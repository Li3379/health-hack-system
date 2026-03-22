package com.hhs.service;

import com.hhs.vo.SyncResultVO;

import java.util.List;

/**
 * Device Sync Orchestration Service Interface.
 * Manages synchronization across multiple wearable device platforms.
 *
 * <p>This service provides a unified API for device synchronization,
 * abstracting away platform-specific details. It coordinates between
 * multiple {@link DevicePlatformService} implementations.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Platform registration and discovery</li>
 *   <li>Unified sync operations (single platform or all platforms)</li>
 *   <li>Error aggregation (one failure doesn't stop others)</li>
 *   <li>OAuth flow coordination</li>
 * </ul>
 */
public interface DeviceSyncOrchestrationService {

    /**
     * Synchronize health data from a specific platform.
     *
     * <p>Delegates to the appropriate platform service based on the platform identifier.
     *
     * @param userId the user ID to sync data for
     * @param platform the platform identifier (e.g., "huawei", "xiaomi")
     * @return SyncResultVO containing sync results, or failed result if platform not found
     */
    SyncResultVO syncPlatform(Long userId, String platform);

    /**
     * Synchronize health data from all connected platforms for a user.
     *
     * <p>Iterates through all registered platforms, checks connectivity,
     * and syncs data from each. Continues on individual failures to ensure
     * all platforms are attempted.
     *
     * <p>Error aggregation: collects errors but continues syncing other platforms.
     *
     * @param userId the user ID to sync data for
     * @return list of SyncResultVO for each platform (successful and failed)
     */
    List<SyncResultVO> syncAllPlatforms(Long userId);

    /**
     * Generate OAuth authorization URL for a specific platform.
     *
     * @param userId the user ID requesting authorization
     * @param platform the platform identifier
     * @return the authorization URL, or null if platform not found or not configured
     */
    String getAuthorizationUrl(Long userId, String platform);

    /**
     * Handle OAuth callback for a specific platform.
     *
     * <p>Routes the callback to the appropriate platform service based on
     * the platform identifier in the URL path.
     *
     * @param platform the platform identifier
     * @param code the authorization code from OAuth provider
     * @param state the state parameter containing userId
     * @throws com.hhs.exception.BusinessException if platform not found or state validation fails
     * @throws com.hhs.exception.SystemException if token exchange fails
     */
    void handleCallback(String platform, String code, String state);

    /**
     * Get list of registered platform identifiers.
     *
     * @return list of platform identifiers that are registered
     */
    List<String> getRegisteredPlatforms();

    /**
     * Check if a specific platform is registered and available.
     *
     * @param platform the platform identifier
     * @return true if platform is registered, false otherwise
     */
    boolean isPlatformRegistered(String platform);

    /**
     * Check if a specific platform is configured and ready for use.
     *
     * @param platform the platform identifier
     * @return true if platform is registered and configured, false otherwise
     */
    boolean isPlatformConfigured(String platform);
}