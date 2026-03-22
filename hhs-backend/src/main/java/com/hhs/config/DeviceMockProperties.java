package com.hhs.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for device data mock settings.
 *
 * <p>Controls whether mock data is returned when real API calls fail
 * or when API credentials are not configured.
 *
 * <p>Default behavior:
 * <ul>
 *   <li>Development (dev profile): Mock data enabled for testing</li>
 *   <li>Production (prod profile): Mock data disabled for data integrity</li>
 * </ul>
 *
 * <p>Configuration example:
 * <pre>
 * device:
 *   mock:
 *     enabled: false  # Set to false in production
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "device.mock")
@Data
public class DeviceMockProperties {

    private static final Logger log = LoggerFactory.getLogger(DeviceMockProperties.class);

    /**
     * Whether mock data generation is enabled.
     * When enabled, the system will return simulated health data when real API calls fail.
     * When disabled, sync operations will fail instead of returning mock data.
     *
     * Default: true (for development convenience)
     */
    private boolean enabled = true;

    /**
     * Validates mock configuration on startup.
     */
    @PostConstruct
    public void validate() {
        if (enabled) {
            log.info("=== Device Mock Configuration ===");
            log.warn("Device mock data is ENABLED. This should be DISABLED in production!");
            log.info("Set 'device.mock.enabled=false' in production configuration.");
        } else {
            log.info("=== Device Mock Configuration ===");
            log.info("Device mock data is DISABLED. Sync will fail if real API is unavailable.");
        }
    }

    /**
     * Check if mock data generation is enabled.
     *
     * @return true if mock data should be returned when API fails
     */
    public boolean isEnabled() {
        return enabled;
    }
}