package com.hhs.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for device OAuth integrations (Huawei, Xiaomi).
 * Supports platform-specific OAuth settings loaded from application YAML.
 */
@Component
@ConfigurationProperties(prefix = "device.oauth")
@Data
public class DeviceOAuthProperties {

    private static final Logger log = LoggerFactory.getLogger(DeviceOAuthProperties.class);

    /**
     * Map of platform name to its OAuth configuration.
     * Supported platforms: huawei, xiaomi
     */
    private Map<String, PlatformConfig> platforms = new HashMap<>();

    /**
     * OAuth configuration for a specific device platform.
     */
    @Data
    public static class PlatformConfig {
        private String clientId;
        private String clientSecret;
        private String authUrl;
        private String tokenUrl;
        private String redirectUri;
        private List<String> scopes;
    }

    /**
     * Validates OAuth configuration on startup.
     * Logs warnings for any platforms with missing required credentials.
     */
    @PostConstruct
    public void validate() {
        log.info("=== Device OAuth Configuration ===");

        if (platforms.isEmpty()) {
            log.warn("No device OAuth platforms configured. Device integration features will be unavailable.");
            return;
        }

        for (Map.Entry<String, PlatformConfig> entry : platforms.entrySet()) {
            String platform = entry.getKey();
            PlatformConfig config = entry.getValue();
            validatePlatform(platform, config);
        }
    }

    /**
     * Validates a single platform configuration.
     */
    private void validatePlatform(String platform, PlatformConfig config) {
        if (config == null) {
            log.warn("[{}] Platform configuration is null", platform);
            return;
        }

        boolean hasClientId = config.getClientId() != null && !config.getClientId().isBlank();
        boolean hasClientSecret = config.getClientSecret() != null && !config.getClientSecret().isBlank();

        if (!hasClientId || !hasClientSecret) {
            log.warn("[{}] OAuth credentials not configured. " +
                    "Set {}_CLIENT_ID and {}_CLIENT_SECRET environment variables to enable this platform.",
                    platform.toUpperCase(), platform.toUpperCase(), platform.toUpperCase());
            log.debug("[{}] clientId present: {}, clientSecret present: {}",
                    platform, hasClientId, hasClientSecret);
        } else {
            log.info("[{}] OAuth configured - authUrl: {}, redirectUri: {}",
                    platform.toUpperCase(), config.getAuthUrl(), config.getRedirectUri());
        }

        // Validate required URLs are present
        if (config.getAuthUrl() == null || config.getAuthUrl().isBlank()) {
            log.warn("[{}] auth-url is not configured", platform);
        }
        if (config.getTokenUrl() == null || config.getTokenUrl().isBlank()) {
            log.warn("[{}] token-url is not configured", platform);
        }
        if (config.getRedirectUri() == null || config.getRedirectUri().isBlank()) {
            log.warn("[{}] redirect-uri is not configured", platform);
        }
    }

    /**
     * Gets the OAuth configuration for a specific platform.
     *
     * @param platform the platform name (e.g., "huawei", "xiaomi")
     * @return the platform configuration, or null if not configured
     */
    public PlatformConfig getPlatform(String platform) {
        return platforms.get(platform);
    }

    /**
     * Checks if a platform has valid OAuth credentials configured.
     *
     * @param platform the platform name
     * @return true if credentials are present and non-empty
     */
    public boolean hasValidCredentials(String platform) {
        PlatformConfig config = platforms.get(platform);
        if (config == null) {
            return false;
        }
        return config.getClientId() != null && !config.getClientId().isBlank()
                && config.getClientSecret() != null && !config.getClientSecret().isBlank();
    }

    /**
     * Checks if any platform is fully configured with valid credentials.
     *
     * @return true if at least one platform has valid credentials
     */
    public boolean hasAnyPlatformConfigured() {
        return platforms.entrySet().stream()
                .anyMatch(entry -> hasValidCredentials(entry.getKey()));
    }
}