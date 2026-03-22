package com.hhs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.config.DeviceOAuthProperties;
import com.hhs.config.DeviceOAuthProperties.PlatformConfig;
import com.hhs.entity.DevicePlatformConfig;
import com.hhs.mapper.DevicePlatformConfigMapper;
import com.hhs.service.DevicePlatformConfigService;
import com.hhs.service.TokenEncryptionService;
import com.hhs.vo.DevicePlatformConfigRequest;
import com.hhs.vo.DevicePlatformConfigStatusVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 设备平台配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePlatformConfigServiceImpl implements DevicePlatformConfigService {

    private final DevicePlatformConfigMapper configMapper;
    private final DeviceOAuthProperties oAuthProperties;
    private final TokenEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    private static final Map<String, List<String>> SUPPORTED_DATA_TYPES = Map.of(
            "huawei", List.of("heart_rate", "step_count", "sleep", "blood_pressure", "blood_glucose", "spo2"),
            "xiaomi", List.of("heart_rate", "step_count", "sleep", "blood_pressure", "blood_glucose"),
            "wechat", List.of("step_count"),
            "apple", List.of("heart_rate", "step_count", "sleep", "blood_pressure", "blood_glucose", "spo2")
    );

    @PostConstruct
    public void init() {
        initializeDefaultConfigs();
    }

    @Override
    public void initializeDefaultConfigs() {
        // Initialize default configs from YAML if database is empty
        for (DevicePlatformConfig.Platform platform : DevicePlatformConfig.Platform.values()) {
            String platformCode = platform.getCode();

            // Check if config exists in database
            DevicePlatformConfig existing = configMapper.selectById(platformCode);
            if (existing == null) {
                // Try to load from YAML configuration
                PlatformConfig yamlConfig = oAuthProperties.getPlatform(platformCode);
                if (yamlConfig != null && yamlConfig.getClientId() != null && !yamlConfig.getClientId().isBlank()) {
                    DevicePlatformConfig config = createFromYaml(platformCode, yamlConfig);
                    configMapper.insert(config);
                    log.info("Initialized device platform config from YAML: {}", platformCode);
                } else {
                    // Create empty config placeholder
                    DevicePlatformConfig emptyConfig = new DevicePlatformConfig();
                    emptyConfig.setPlatform(platformCode);
                    emptyConfig.setConfigured(false);
                    emptyConfig.setEnabled(false);
                    emptyConfig.setCreateTime(LocalDateTime.now());
                    emptyConfig.setUpdateTime(LocalDateTime.now());
                    configMapper.insert(emptyConfig);
                    log.info("Created empty config placeholder for platform: {}", platformCode);
                }
            }
        }
    }

    private DevicePlatformConfig createFromYaml(String platform, PlatformConfig yamlConfig) {
        DevicePlatformConfig config = new DevicePlatformConfig();
        config.setPlatform(platform);

        // Encrypt sensitive fields
        if (encryptionService.isConfigured()) {
            config.setClientId(encryptionService.encrypt(yamlConfig.getClientId()));
            config.setClientSecret(encryptionService.encrypt(yamlConfig.getClientSecret()));
        } else {
            log.warn("Encryption service not configured, storing credentials in plain text (not recommended for production)");
            config.setClientId(yamlConfig.getClientId());
            config.setClientSecret(yamlConfig.getClientSecret());
        }

        config.setAuthUrl(yamlConfig.getAuthUrl());
        config.setTokenUrl(yamlConfig.getTokenUrl());
        config.setRedirectUri(yamlConfig.getRedirectUri());

        if (yamlConfig.getScopes() != null) {
            try {
                config.setScopes(objectMapper.writeValueAsString(yamlConfig.getScopes()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize scopes for platform: {}", platform, e);
            }
        }

        config.setConfigured(true);
        config.setEnabled(true);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());

        return config;
    }

    @Override
    public List<DevicePlatformConfigStatusVO> getAllPlatformStatus() {
        List<DevicePlatformConfigStatusVO> result = new ArrayList<>();

        for (DevicePlatformConfig.Platform platform : DevicePlatformConfig.Platform.values()) {
            result.add(getPlatformStatus(platform.getCode()));
        }

        return result;
    }

    @Override
    public DevicePlatformConfigStatusVO getPlatformStatus(String platform) {
        DevicePlatformConfig config = configMapper.selectById(platform);
        DevicePlatformConfig.Platform platformEnum = DevicePlatformConfig.Platform.fromCode(platform);

        DevicePlatformConfigStatusVO.DevicePlatformConfigStatusVOBuilder builder = DevicePlatformConfigStatusVO.builder()
                .platform(platform)
                .platformName(platformEnum != null ? platformEnum.getDisplayName() : platform)
                .supportedDataTypes(SUPPORTED_DATA_TYPES.getOrDefault(platform, List.of()))
                .guideUrl("/docs/device-sync-configuration-guide.md");

        if (config == null) {
            return builder
                    .configured(false)
                    .oauthReady(false)
                    .enabled(false)
                    .missingConfig(List.of("client_id", "client_secret", "encryption_key"))
                    .build();
        }

        // Check if all required configs are present
        List<String> missing = new ArrayList<>();
        if (config.getClientId() == null || config.getClientId().isBlank()) {
            missing.add("client_id");
        }
        if (config.getClientSecret() == null || config.getClientSecret().isBlank()) {
            missing.add("client_secret");
        }
        if (!encryptionService.isConfigured()) {
            missing.add("encryption_key");
        }

        boolean configured = missing.isEmpty() && Boolean.TRUE.equals(config.getConfigured());
        boolean oauthReady = configured && config.getAuthUrl() != null && config.getTokenUrl() != null;

        return builder
                .configured(configured)
                .oauthReady(oauthReady)
                .lastTestTime(config.getLastTestTime())
                .testResult(config.getTestResult())
                .missingConfig(missing)
                .enabled(config.getEnabled())
                .build();
    }

    @Override
    public boolean isEncryptionKeyConfigured() {
        return encryptionService.isConfigured();
    }

    @Override
    public DevicePlatformConfig getPlatformConfig(String platform) {
        DevicePlatformConfig config = configMapper.selectById(platform);
        if (config == null) {
            return null;
        }

        // Decrypt sensitive fields
        if (config.getClientId() != null && encryptionService.isConfigured()) {
            try {
                config.setClientId(encryptionService.decrypt(config.getClientId()));
            } catch (Exception e) {
                log.error("Failed to decrypt clientId for platform: {}", platform, e);
            }
        }
        if (config.getClientSecret() != null && encryptionService.isConfigured()) {
            try {
                config.setClientSecret(encryptionService.decrypt(config.getClientSecret()));
            } catch (Exception e) {
                log.error("Failed to decrypt clientSecret for platform: {}", platform, e);
            }
        }

        return config;
    }

    @Override
    @Transactional
    public DevicePlatformConfig saveConfig(DevicePlatformConfigRequest request) {
        String platform = request.getPlatform().toLowerCase();

        // Validate platform
        if (DevicePlatformConfig.Platform.fromCode(platform) == null) {
            throw new IllegalArgumentException("Unknown platform: " + platform);
        }

        DevicePlatformConfig config = configMapper.selectById(platform);
        if (config == null) {
            config = new DevicePlatformConfig();
            config.setPlatform(platform);
            config.setCreateTime(LocalDateTime.now());
        }

        // Encrypt sensitive fields
        if (encryptionService.isConfigured()) {
            config.setClientId(encryptionService.encrypt(request.getClientId()));
            config.setClientSecret(encryptionService.encrypt(request.getClientSecret()));
        } else {
            config.setClientId(request.getClientId());
            config.setClientSecret(request.getClientSecret());
        }

        // Set optional fields
        if (request.getAuthUrl() != null) {
            config.setAuthUrl(request.getAuthUrl());
        }
        if (request.getTokenUrl() != null) {
            config.setTokenUrl(request.getTokenUrl());
        }
        if (request.getRedirectUri() != null) {
            config.setRedirectUri(request.getRedirectUri());
        }
        if (request.getScopes() != null) {
            try {
                config.setScopes(objectMapper.writeValueAsString(request.getScopes()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize scopes", e);
            }
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }

        config.setConfigured(true);
        config.setUpdateTime(LocalDateTime.now());

        // Upsert
        configMapper.insert(config);

        log.info("Saved device platform config for: {}", platform);
        return config;
    }

    @Override
    public DevicePlatformConfigStatusVO testConfig(String platform) {
        DevicePlatformConfig config = configMapper.selectById(platform);
        if (config == null) {
            return getPlatformStatus(platform);
        }

        // Decrypt credentials for testing
        String clientId = config.getClientId();
        String clientSecret = config.getClientSecret();

        if (encryptionService.isConfigured()) {
            try {
                clientId = encryptionService.decrypt(config.getClientId());
                clientSecret = encryptionService.decrypt(config.getClientSecret());
            } catch (Exception e) {
                log.error("Failed to decrypt credentials for testing", e);
                config.setTestResult("failed");
                config.setTestErrorMessage("Failed to decrypt credentials");
                config.setLastTestTime(LocalDateTime.now());
                configMapper.updateById(config);
                return getPlatformStatus(platform);
            }
        }

        // Perform configuration test
        boolean testSuccess = performConfigTest(platform, clientId, clientSecret, config.getAuthUrl(), config.getTokenUrl());

        config.setLastTestTime(LocalDateTime.now());
        config.setTestResult(testSuccess ? "success" : "failed");
        config.setTestErrorMessage(testSuccess ? null : "OAuth endpoint test failed");
        configMapper.updateById(config);

        return getPlatformStatus(platform);
    }

    private boolean performConfigTest(String platform, String clientId, String clientSecret, String authUrl, String tokenUrl) {
        // Simple test: check if URLs are accessible and credentials are non-empty
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            return false;
        }
        if (authUrl == null || authUrl.isBlank()) {
            return false;
        }
        if (tokenUrl == null || tokenUrl.isBlank()) {
            return false;
        }

        // For more robust testing, we could make actual HTTP requests
        // For now, just validate the configuration is complete
        log.info("Config test passed for platform: {}", platform);
        return true;
    }

    @Override
    @Transactional
    public void deleteConfig(String platform) {
        configMapper.deleteById(platform);
        log.info("Deleted device platform config for: {}", platform);
    }
}