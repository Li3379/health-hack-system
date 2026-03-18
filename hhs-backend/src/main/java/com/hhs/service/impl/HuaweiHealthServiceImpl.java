package com.hhs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.config.DeviceOAuthProperties;
import com.hhs.config.DeviceOAuthProperties.PlatformConfig;
import com.hhs.dto.TokenResponse;
import com.hhs.entity.DeviceConnection;
import com.hhs.exception.BusinessException;
import com.hhs.exception.SystemException;
import com.hhs.service.DeviceConnectionService;
import com.hhs.service.HuaweiHealthService;
import com.hhs.service.TokenEncryptionService;
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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
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

    private final DeviceOAuthProperties oAuthProperties;
    private final TokenEncryptionService encryptionService;
    private final DeviceConnectionService connectionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public HuaweiHealthServiceImpl(
            DeviceOAuthProperties oAuthProperties,
            TokenEncryptionService encryptionService,
            DeviceConnectionService connectionService,
            RedisTemplate<String, Object> redisTemplate,
            RestTemplate restTemplate) {
        this.oAuthProperties = oAuthProperties;
        this.encryptionService = encryptionService;
        this.connectionService = connectionService;
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