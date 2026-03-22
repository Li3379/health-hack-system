package com.hhs.service;

import com.hhs.vo.SyncResultVO;

/**
 * Xiaomi Health Service Interface.
 * Defines operations for Xiaomi Sport/Health platform integration.
 */
public interface XiaomiHealthService {

    /**
     * Generate OAuth authorization URL for Xiaomi account authorization.
     *
     * @param userId the user ID requesting authorization
     * @return the complete authorization URL, or null if OAuth is not configured
     */
    String getAuthorizationUrl(Long userId);

    /**
     * Handle OAuth callback after user authorization.
     *
     * @param code the authorization code from Xiaomi OAuth
     * @param state the state parameter containing userId
     */
    void handleCallback(String code, String state);

    /**
     * Synchronize health data from Xiaomi Health.
     *
     * @param userId the user ID to sync data for
     * @return SyncResultVO containing sync results
     */
    SyncResultVO syncData(Long userId);

    /**
     * Refresh access token using refresh token.
     *
     * @param userId the user ID to refresh tokens for
     * @return true if refresh was successful
     */
    boolean refreshTokens(Long userId);

    /**
     * Check if Xiaomi OAuth is properly configured.
     *
     * @return true if the platform is configured and ready
     */
    boolean isConfigured();
}