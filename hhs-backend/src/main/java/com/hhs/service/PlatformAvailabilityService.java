package com.hhs.service;

import com.hhs.dto.PlatformMetadata;

import java.util.List;

/**
 * Platform Availability Service Interface.
 * Provides platform metadata and availability status for device integrations.
 *
 * <p>This service centralizes platform capability information and determines
 * availability based on OAuth configuration, service implementation status,
 * and technical constraints.
 */
public interface PlatformAvailabilityService {

    /**
     * Get metadata for all supported platforms.
     * Includes availability status, capabilities, and supported data types.
     *
     * @return list of all platform metadata
     */
    List<PlatformMetadata> getAllPlatformMetadata();

    /**
     * Get metadata for a specific platform.
     *
     * @param platform the platform identifier (e.g., "huawei", "xiaomi")
     * @return platform metadata, or null if platform is unknown
     */
    PlatformMetadata getPlatformMetadata(String platform);

    /**
     * Check if a platform is available for use.
     * A platform is available if:
     * <ul>
     *   <li>Service implementation exists</li>
     *   <li>OAuth is configured (for web OAuth platforms)</li>
     *   <li>Encryption key is configured</li>
     * </ul>
     *
     * @param platform the platform identifier
     * @return true if the platform can be used for device sync
     */
    boolean isPlatformAvailable(String platform);

    /**
     * Get the reason why a platform is unavailable.
     *
     * @param platform the platform identifier
     * @return human-readable reason, or null if platform is available
     */
    String getUnavailableReason(String platform);

    /**
     * Get list of platform identifiers that are currently available.
     *
     * @return list of available platform identifiers
     */
    List<String> getAvailablePlatforms();

    /**
     * Get list of platform identifiers that require user action.
     * These platforms need mini-program or native app.
     *
     * @return list of platforms requiring user action
     */
    List<String> getPlatformsRequiringAction();
}