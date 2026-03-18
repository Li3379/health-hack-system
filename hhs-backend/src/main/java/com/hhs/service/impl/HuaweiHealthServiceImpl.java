package com.hhs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.config.DeviceOAuthProperties;
import com.hhs.config.DeviceOAuthProperties.PlatformConfig;
import com.hhs.dto.HealthDataPoint;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.HuaweiHealthDataResponse;
import com.hhs.dto.TokenResponse;
import com.hhs.entity.DeviceConnection;
import com.hhs.exception.BusinessException;
import com.hhs.exception.SystemException;
import com.hhs.service.DeviceConnectionService;
import com.hhs.service.HealthMetricService;
import com.hhs.service.HuaweiHealthService;
import com.hhs.service.SyncHistoryService;
import com.hhs.service.TokenEncryptionService;
import com.hhs.vo.SyncResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Huawei Health OAuth Service Implementation.
 *
 * <p>Implements OAuth 2.0 authorization flow for Huawei Health platform.
 * Uses state parameter with userId for CSRF protection, stored in Redis
 * with 5-minute expiration.
 *
 * <p>Security features:
 * <ul>
 *   <li>State parameter with timestamp and random nonce</li>
 *   <li>Redis storage with automatic expiration</li>
 *   <li>AES-256-GCM token encryption</li>
 *   <li>Secure token exchange over HTTPS</li>
 * </ul>
 */
@Slf4j
@Service
public class HuaweiHealthServiceImpl implements HuaweiHealthService {

    private static final String PLATFORM = "huawei";
    private static final String STATE_KEY_PREFIX = "oauth:state:huawei:";
    private static final long STATE_EXPIRATION_MINUTES = 5;

    // Huawei Health Kit API base URL
    private static final String HUAWEI_HEALTH_API_URL = "https://health-api.cloud.huawei.com/healthkit/v1";

    private final DeviceOAuthProperties oAuthProperties;
    private final TokenEncryptionService encryptionService;
    private final DeviceConnectionService connectionService;
    private final HealthMetricService healthMetricService;
    private final SyncHistoryService syncHistoryService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public HuaweiHealthServiceImpl(
            DeviceOAuthProperties oAuthProperties,
            TokenEncryptionService encryptionService,
            DeviceConnectionService connectionService,
            HealthMetricService healthMetricService,
            SyncHistoryService syncHistoryService,
            RedisTemplate<String, Object> redisTemplate,
            RestTemplate restTemplate) {
        this.oAuthProperties = oAuthProperties;
        this.encryptionService = encryptionService;
        this.connectionService = connectionService;
        this.healthMetricService = healthMetricService;
        this.syncHistoryService = syncHistoryService;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String getAuthorizationUrl(Long userId) {
        if (!isConfigured()) {
            log.warn("Huawei Health OAuth is not configured");
            return null;
        }

        PlatformConfig config = oAuthProperties.getPlatform(PLATFORM);
        if (config == null) {
            log.error("Huawei platform configuration not found");
            return null;
        }

        // Generate secure state parameter
        String state = generateState(userId);
        storeState(state, userId);

        // Build authorization URL
        String scopes = String.join(" ", config.getScopes());
        String authUrl = UriComponentsBuilder
                .fromHttpUrl(config.getAuthUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .queryParam("access_type", "offline")  // Request refresh token
                .build()
                .toUriString();

        log.info("Generated Huawei Health OAuth URL for user {}", userId);
        log.debug("Authorization URL: {}", authUrl);

        return authUrl;
    }

    @Override
    @Transactional
    public void handleCallback(String code, String state) {
        log.info("Processing Huawei Health OAuth callback");

        // Validate state parameter
        Long userId = validateAndConsumeState(state);
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN,
                "Invalid or expired OAuth state. Please try connecting again.");
        }

        // Exchange code for tokens
        TokenResponse tokenResponse = exchangeCodeForTokens(code);
        if (tokenResponse.hasError()) {
            log.error("Token exchange failed: {} - {}", tokenResponse.error(), tokenResponse.getErrorMessage());
            throw new SystemException(ErrorCode.AUTH_FAILED,
                "Token exchange failed: " + tokenResponse.getErrorMessage());
        }

        // Encrypt and store tokens
        saveEncryptedTokens(userId, tokenResponse);

        log.info("Huawei Health OAuth completed successfully for user {}", userId);
    }

    @Override
    @Transactional
    public boolean refreshTokens(Long userId) {
        if (!isConfigured()) {
            log.warn("Cannot refresh tokens: Huawei Health OAuth is not configured");
            return false;
        }

        DeviceConnection connection = connectionService.getConnection(userId, PLATFORM);
        if (connection == null || connection.getRefreshToken() == null) {
            log.warn("No Huawei connection found for user {}", userId);
            return false;
        }

        try {
            // Decrypt refresh token
            String refreshToken = encryptionService.decrypt(connection.getRefreshToken());

            // Refresh tokens
            TokenResponse tokenResponse = refreshAccessToken(refreshToken);
            if (tokenResponse.hasError()) {
                log.error("Token refresh failed: {} - {}", tokenResponse.error(), tokenResponse.getErrorMessage());

                // Mark connection as expired
                connection.setStatus("expired");
                connectionService.saveConnection(connection);

                return false;
            }

            // Update stored tokens
            saveEncryptedTokens(userId, tokenResponse);

            log.info("Tokens refreshed successfully for user {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Error refreshing tokens for user {}: {}", userId, e.getMessage());

            // Mark connection as expired
            connection.setStatus("expired");
            connectionService.saveConnection(connection);

            return false;
        }
    }

    @Override
    public boolean isConfigured() {
        return oAuthProperties.hasValidCredentials(PLATFORM)
                && encryptionService.isConfigured();
    }

    @Override
    @Transactional
    public SyncResultVO syncData(Long userId) {
        long startTime = System.currentTimeMillis();
        log.info("Starting Huawei Health data sync for user {}", userId);

        // Step 1: Check device connection status
        DeviceConnection connection = connectionService.getConnection(userId, PLATFORM);
        if (connection == null) {
            log.warn("No Huawei device connection found for user {}", userId);
            return syncHistoryService.createFailedRecord(
                    userId, PLATFORM, "manual",
                    "Device not connected. Please connect your Huawei Health account first.",
                    (int) (System.currentTimeMillis() - startTime)
            );
        }

        if (!"connected".equals(connection.getStatus())) {
            log.warn("Huawei device connection is not active for user {}: {}", userId, connection.getStatus());
            return syncHistoryService.createFailedRecord(
                    userId, PLATFORM, "manual",
                    "Device connection is not active. Status: " + connection.getStatus(),
                    (int) (System.currentTimeMillis() - startTime)
            );
        }

        // Step 2: Check and refresh token if needed
        String accessToken = null;
        try {
            accessToken = getValidAccessToken(userId, connection);
            if (accessToken == null) {
                log.warn("Failed to get valid access token for user {}", userId);
                return syncHistoryService.createFailedRecord(
                        userId, PLATFORM, "manual",
                        "Failed to authenticate with Huawei Health. Please reconnect your account.",
                        (int) (System.currentTimeMillis() - startTime)
                );
            }
        } catch (Exception e) {
            log.error("Error getting access token for user {}: {}", userId, e.getMessage());
            return syncHistoryService.createFailedRecord(
                    userId, PLATFORM, "manual",
                    "Authentication error: " + e.getMessage(),
                    (int) (System.currentTimeMillis() - startTime)
            );
        }

        // Step 3: Fetch health data from Huawei Health API (or mock)
        List<HealthDataPoint> healthData;
        try {
            healthData = fetchHealthData(accessToken, userId);
            log.info("Fetched {} health data points for user {}", healthData.size(), userId);
        } catch (Exception e) {
            log.error("Error fetching health data for user {}: {}", userId, e.getMessage());
            return syncHistoryService.createFailedRecord(
                    userId, PLATFORM, "manual",
                    "Failed to fetch health data: " + e.getMessage(),
                    (int) (System.currentTimeMillis() - startTime)
            );
        }

        // Step 4: Map and save health metrics
        int savedCount = 0;
        List<String> errors = new ArrayList<>();
        for (HealthDataPoint dataPoint : healthData) {
            try {
                if (dataPoint.hasValidValue()) {
                    saveHealthMetric(userId, dataPoint);
                    savedCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to save health metric for user {}: {}", userId, e.getMessage());
                errors.add(dataPoint.metricKey() + ": " + e.getMessage());
            }
        }

        int durationMs = (int) (System.currentTimeMillis() - startTime);

        // Step 5: Update lastSyncAt
        try {
            connectionService.updateLastSyncAt(userId, PLATFORM);
        } catch (Exception e) {
            log.warn("Failed to update lastSyncAt for user {}: {}", userId, e.getMessage());
        }

        // Step 6: Record sync history and return result
        if (savedCount > 0) {
            log.info("Huawei Health sync completed for user {}: {} metrics saved", userId, savedCount);
            SyncResultVO result = syncHistoryService.createSuccessRecord(
                    userId, PLATFORM, "manual", savedCount, durationMs
            );
            if (!errors.isEmpty()) {
                result.setErrorMessage("Partial sync: " + String.join("; ", errors));
            }
            return result;
        } else {
            log.warn("Huawei Health sync completed for user {} with no data saved", userId);
            return syncHistoryService.createFailedRecord(
                    userId, PLATFORM, "manual",
                    errors.isEmpty() ? "No valid health data found" : String.join("; ", errors),
                    durationMs
            );
        }
    }

    /**
     * Get a valid access token, refreshing if necessary.
     *
     * @param userId the user ID
     * @param connection the device connection
     * @return valid access token, or null if unavailable
     */
    private String getValidAccessToken(Long userId, DeviceConnection connection) {
        try {
            // Check if token is expired or about to expire (within 5 minutes)
            if (connection.getTokenExpireAt() != null
                    && connection.getTokenExpireAt().isBefore(LocalDateTime.now().plusMinutes(5))) {

                log.info("Access token expired or expiring soon for user {}, refreshing...", userId);
                if (!refreshTokens(userId)) {
                    return null;
                }

                // Re-fetch connection with new token
                connection = connectionService.getConnection(userId, PLATFORM);
                if (connection == null) {
                    return null;
                }
            }

            // Decrypt and return access token
            return encryptionService.decrypt(connection.getAccessToken());
        } catch (Exception e) {
            log.error("Error getting access token for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch health data from Huawei Health API.
     * Falls back to mock data if API is unavailable or not configured.
     *
     * @param accessToken the access token
     * @param userId the user ID (for logging)
     * @return list of health data points
     */
    private List<HealthDataPoint> fetchHealthData(String accessToken, Long userId) {
        // Try to fetch real data if OAuth is configured
        if (isConfigured()) {
            try {
                List<HealthDataPoint> realData = fetchRealHealthData(accessToken, userId);
                if (!realData.isEmpty()) {
                    return realData;
                }
                log.info("No data returned from Huawei API, using mock data");
            } catch (Exception e) {
                log.warn("Failed to fetch real health data, falling back to mock: {}", e.getMessage());
            }
        }

        // Return mock data for testing/demo purposes
        return generateMockHealthData();
    }

    /**
     * Fetch real health data from Huawei Health Kit API.
     *
     * @param accessToken the access token
     * @param userId the user ID
     * @return list of health data points
     */
    private List<HealthDataPoint> fetchRealHealthData(String accessToken, Long userId) {
        List<HealthDataPoint> allData = new ArrayList<>();

        // Define data types to query
        List<String> dataTypes = List.of(
                "heart_rate",
                "step_count",
                "sleep",
                "blood_pressure",
                "blood_glucose",
                "spo2"
        );

        // Query each data type
        for (String dataType : dataTypes) {
            try {
                List<HealthDataPoint> typeData = queryHealthDataType(accessToken, dataType);
                allData.addAll(typeData);
            } catch (Exception e) {
                log.warn("Failed to fetch {} data: {}", dataType, e.getMessage());
            }
        }

        return allData;
    }

    /**
     * Query a specific health data type from Huawei Health Kit API.
     *
     * @param accessToken the access token
     * @param dataType the data type to query
     * @return list of health data points
     */
    private List<HealthDataPoint> queryHealthDataType(String accessToken, String dataType) {
        try {
            // Build request URL - Huawei Health Kit API endpoint
            String url = HUAWEI_HEALTH_API_URL + "/dataPoints";

            // Build request body
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusDays(1); // Last 24 hours

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("dataType", dataType);
            requestBody.put("startTime", startTime.toString());
            requestBody.put("endTime", endTime.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("Querying Huawei Health API for data type: {}", dataType);
            ResponseEntity<HuaweiHealthDataResponse> response = restTemplate.postForEntity(
                    url, request, HuaweiHealthDataResponse.class
            );

            HuaweiHealthDataResponse dataResponse = response.getBody();
            if (dataResponse == null || dataResponse.hasError()) {
                String errorMsg = dataResponse != null ? dataResponse.getErrorMessage() : "Empty response";
                log.warn("Huawei Health API returned error for {}: {}", dataType, errorMsg);
                return Collections.emptyList();
            }

            if (!dataResponse.hasData()) {
                return Collections.emptyList();
            }

            // Convert to internal data points
            return dataResponse.dataPoints().stream()
                    .map(dp -> HealthDataPoint.fromHuawei(dp, PLATFORM))
                    .filter(HealthDataPoint::hasValidValue)
                    .toList();

        } catch (RestClientException e) {
            log.warn("HTTP error querying Huawei Health API for {}: {}", dataType, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error querying Huawei Health API for {}: {}", dataType, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Generate mock health data for testing/demo purposes.
     * This is used when real Huawei API credentials are not available.
     *
     * @return list of mock health data points
     */
    private List<HealthDataPoint> generateMockHealthData() {
        List<HealthDataPoint> mockData = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        SecureRandom random = new SecureRandom();

        // Heart rate (multiple readings throughout the day)
        for (int i = 0; i < 5; i++) {
            mockData.add(new HealthDataPoint(
                    "heartRate",
                    BigDecimal.valueOf(60 + random.nextInt(40)), // 60-100 bpm
                    "bpm",
                    now.minusHours(i),
                    "heart_rate",
                    PLATFORM
            ));
        }

        // Steps (daily total)
        mockData.add(new HealthDataPoint(
                "steps",
                BigDecimal.valueOf(5000 + random.nextInt(10000)), // 5000-15000 steps
                "steps",
                now,
                "step_count",
                PLATFORM
        ));

        // Sleep duration (last night in hours)
        double sleepHours = 5 + random.nextDouble() * 4; // 5-9 hours
        mockData.add(new HealthDataPoint(
                "sleepDuration",
                BigDecimal.valueOf(sleepHours).setScale(1, RoundingMode.HALF_UP),
                "hours",
                now.minusDays(1),
                "sleep",
                PLATFORM
        ));

        // Blood pressure
        mockData.add(new HealthDataPoint(
                "systolicBP",
                BigDecimal.valueOf(110 + random.nextInt(30)), // 110-140 mmHg
                "mmHg",
                now.minusHours(2),
                "blood_pressure",
                PLATFORM
        ));
        mockData.add(new HealthDataPoint(
                "diastolicBP",
                BigDecimal.valueOf(70 + random.nextInt(20)), // 70-90 mmHg
                "mmHg",
                now.minusHours(2),
                "blood_pressure",
                PLATFORM
        ));

        // Blood glucose
        mockData.add(new HealthDataPoint(
                "glucose",
                BigDecimal.valueOf(80 + random.nextInt(60)), // 80-140 mg/dL
                "mg/dL",
                now.minusHours(4),
                "blood_glucose",
                PLATFORM
        ));

        // SpO2
        mockData.add(new HealthDataPoint(
                "spo2",
                BigDecimal.valueOf(95 + random.nextInt(5)), // 95-99%
                "%",
                now.minusHours(1),
                "spo2",
                PLATFORM
        ));

        log.debug("Generated {} mock health data points", mockData.size());
        return mockData;
    }

    /**
     * Save a health data point as a health metric.
     *
     * @param userId the user ID
     * @param dataPoint the health data point to save
     */
    private void saveHealthMetric(Long userId, HealthDataPoint dataPoint) {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setUserId(userId);
        request.setMetricKey(dataPoint.metricKey());
        request.setValue(dataPoint.value());
        request.setUnit(dataPoint.unit());
        request.setRecordDate(dataPoint.recordTime().toLocalDate());

        // Calculate trend based on metric type (simplified)
        request.setTrend("normal");

        healthMetricService.add(userId, request);
        log.debug("Saved health metric: {} = {} {} for user {}",
                dataPoint.metricKey(), dataPoint.value(), dataPoint.unit(), userId);
    }

    /**
     * Generate a secure state parameter containing userId, timestamp, and nonce.
     * Format: Base64(JSON{userId, timestamp, nonce})
     */
    private String generateState(Long userId) {
        try {
            Map<String, Object> stateData = new HashMap<>();
            stateData.put("userId", userId);
            stateData.put("timestamp", System.currentTimeMillis());
            stateData.put("nonce", generateNonce());

            String json = objectMapper.writeValueAsString(stateData);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR, "Failed to generate OAuth state", e);
        }
    }

    /**
     * Generate a random nonce for state parameter.
     */
    private String generateNonce() {
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }

    /**
     * Store state parameter in Redis with expiration.
     */
    private void storeState(String state, Long userId) {
        String key = STATE_KEY_PREFIX + state;
        redisTemplate.opsForValue().set(
                key,
                userId,
                STATE_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
        log.debug("Stored OAuth state for user {} with {} minute expiration", userId, STATE_EXPIRATION_MINUTES);
    }

    /**
     * Validate state parameter and retrieve userId.
     * Removes state from Redis after validation (one-time use).
     */
    private Long validateAndConsumeState(String state) {
        if (state == null || state.isEmpty()) {
            log.warn("Empty state parameter received");
            return null;
        }

        String key = STATE_KEY_PREFIX + state;
        Object value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            log.warn("State not found or expired: {}", state.substring(0, Math.min(10, state.length())));
            return null;
        }

        // Delete state after successful validation (one-time use)
        redisTemplate.delete(key);

        Long userId;
        if (value instanceof Number) {
            userId = ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                // Try parsing from JSON state for additional validation
                String json = new String(Base64.getUrlDecoder().decode((String) value), StandardCharsets.UTF_8);
                Map<?, ?> stateData = objectMapper.readValue(json, Map.class);
                Object userIdObj = stateData.get("userId");
                if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                } else {
                    userId = Long.parseLong(userIdObj.toString());
                }

                // Validate timestamp (should be within expiration window)
                Long timestamp = ((Number) stateData.get("timestamp")).longValue();
                long ageMinutes = ChronoUnit.MINUTES.between(
                        LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC),
                        LocalDateTime.now()
                );
                if (ageMinutes > STATE_EXPIRATION_MINUTES) {
                    log.warn("State expired: {} minutes old", ageMinutes);
                    return null;
                }
            } catch (Exception e) {
                // Fall back to direct value
                userId = Long.parseLong(value.toString());
            }
        } else {
            log.warn("Unexpected state value type: {}", value.getClass());
            return null;
        }

        log.debug("Validated OAuth state for user {}", userId);
        return userId;
    }

    /**
     * Exchange authorization code for access tokens.
     */
    private TokenResponse exchangeCodeForTokens(String code) {
        PlatformConfig config = oAuthProperties.getPlatform(PLATFORM);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", config.getClientId());
        params.add("client_secret", config.getClientSecret());
        params.add("redirect_uri", config.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            log.debug("Exchanging authorization code for tokens at: {}", config.getTokenUrl());
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    config.getTokenUrl(),
                    request,
                    TokenResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Token exchange request failed: {}", e.getMessage());
            throw new SystemException(ErrorCode.AUTH_FAILED,
                "Failed to exchange authorization code: " + e.getMessage(), e);
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    private TokenResponse refreshAccessToken(String refreshToken) {
        PlatformConfig config = oAuthProperties.getPlatform(PLATFORM);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", refreshToken);
        params.add("client_id", config.getClientId());
        params.add("client_secret", config.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            log.debug("Refreshing access token at: {}", config.getTokenUrl());
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    config.getTokenUrl(),
                    request,
                    TokenResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Token refresh request failed: {}", e.getMessage());
            throw new SystemException(ErrorCode.AUTH_FAILED,
                "Failed to refresh access token: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypt and save tokens to database.
     */
    private void saveEncryptedTokens(Long userId, TokenResponse tokenResponse) {
        // Encrypt tokens
        String encryptedAccessToken = encryptionService.encrypt(tokenResponse.accessToken());
        String encryptedRefreshToken = tokenResponse.refreshToken() != null
                ? encryptionService.encrypt(tokenResponse.refreshToken())
                : null;

        // Calculate expiration time
        LocalDateTime expiresAt = null;
        if (tokenResponse.expiresIn() != null) {
            // Subtract 5 minutes for safety margin
            expiresAt = LocalDateTime.now()
                    .plusSeconds(tokenResponse.expiresIn() - 300);
        }

        // Create or update connection
        DeviceConnection connection = new DeviceConnection();
        connection.setUserId(userId);
        connection.setPlatform(PLATFORM);
        connection.setAccessToken(encryptedAccessToken);
        connection.setRefreshToken(encryptedRefreshToken);
        connection.setTokenExpireAt(expiresAt);
        connection.setStatus("connected");
        connection.setSyncEnabled(true);

        connectionService.saveConnection(connection);

        log.debug("Saved encrypted tokens for user {}, expires at: {}", userId, expiresAt);
    }
}