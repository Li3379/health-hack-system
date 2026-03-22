package com.hhs.service;

import com.hhs.vo.SyncResultVO;

/**
 * Device Platform Service Interface.
 * Defines the contract for wearable device platform integrations.
 *
 * <p>Each platform (Huawei, Xiaomi, etc.) implements this interface to provide
 * OAuth authorization and data synchronization capabilities.
 *
 * <p>Implementations should be Spring beans with {@code @Service} annotation.
 * The platform identifier is used for automatic registration in the orchestration service.
 */
public interface DevicePlatformService {

    /**
     * Get the platform identifier.
     * Used for routing and registration in the orchestration layer.
     *
     * @return platform identifier (e.g., "huawei", "xiaomi")
     */
    String getPlatform();

    /**
     * Generate OAuth authorization URL for user to grant access.
     *
     * <p>The URL should include all required OAuth parameters including
     * a secure state parameter for CSRF protection.
     *
     * @param userId the user ID requesting authorization
     * @return the complete authorization URL, or null if OAuth is not configured
     */
    String getAuthorizationUrl(Long userId);

    /**
     * Handle OAuth callback after user authorization.
     *
     * <p>This method should:
     * <ul>
     *   <li>Validate the state parameter</li>
     *   <li>Exchange authorization code for tokens</li>
     *   <li>Store tokens securely</li>
     *   <li>Create or update device connection record</li>
     * </ul>
     *
     * @param code the authorization code from OAuth provider
     * @param state the state parameter containing userId
     * @throws com.hhs.exception.BusinessException if state validation fails
     * @throws com.hhs.exception.SystemException if token exchange fails
     */
    void handleCallback(String code, String state);

    /**
     * Synchronize health data from the platform for a user.
     *
     * <p>This method should:
     * <ul>
     *   <li>Check device connection status</li>
     *   <li>Refresh access token if expired</li>
     *   <li>Fetch health data from platform API</li>
     *   <li>Save metrics to local storage</li>
     *   <li>Record sync history</li>
     * </ul>
     *
     * @param userId the user ID to sync data for
     * @return SyncResultVO containing sync results
     */
    SyncResultVO syncData(Long userId);

    /**
     * Refresh access token using refresh token.
     *
     * <p>Called when access token is expired or about to expire.
     *
     * @param userId the user ID to refresh tokens for
     * @return true if refresh was successful, false otherwise
     */
    boolean refreshTokens(Long userId);

    /**
     * Check if the platform OAuth is properly configured.
     *
     * <p>This should verify that required credentials (client ID, secret, etc.)
     * are available and the platform is ready for use.
     *
     * @return true if the platform is configured and ready
     */
    boolean isConfigured();
}