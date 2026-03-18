package com.hhs.service;

import com.hhs.vo.SyncResultVO;

/**
 * Huawei Health OAuth Service.
 * Handles OAuth 2.0 authorization flow for Huawei Health platform integration.
 *
 * <p>This service manages:
 * <ul>
 *   <li>OAuth authorization URL generation with state parameter</li>
 *   <li>OAuth callback handling with token exchange</li>
 *   <li>Token refresh for expired access tokens</li>
 *   <li>Secure token storage with encryption</li>
 *   <li>Health data synchronization from Huawei Health API</li>
 * </ul>
 */
public interface HuaweiHealthService {

    /**
     * Generate OAuth authorization URL for user to grant access.
     *
     * <p>The URL includes:
     * <ul>
     *   <li>Client ID from configuration</li>
     *   <li>Redirect URI for callback</li>
     *   <li>Required scopes for health data access</li>
     *   <li>State parameter with userId for CSRF protection</li>
     * </ul>
     *
     * @param userId the user ID requesting authorization
     * @return the complete authorization URL, or null if OAuth is not configured
     */
    String getAuthorizationUrl(Long userId);

    /**
     * Handle OAuth callback after user authorization.
     *
     * <p>This method:
     * <ul>
     *   <li>Validates the state parameter</li>
     *   <li>Exchanges authorization code for tokens</li>
     *   <li>Encrypts and stores tokens in database</li>
     *   <li>Creates or updates device connection record</li>
     * </ul>
     *
     * @param code the authorization code from OAuth provider
     * @param state the state parameter containing userId
     * @throws com.hhs.exception.BusinessException if state validation fails
     * @throws com.hhs.exception.SystemException if token exchange fails
     */
    void handleCallback(String code, String state);

    /**
     * Refresh access token using refresh token.
     *
     * <p>Called when access token is expired. Updates stored tokens
     * with new access token from refresh token exchange.
     *
     * @param userId the user ID to refresh tokens for
     * @return true if refresh was successful, false otherwise
     */
    boolean refreshTokens(Long userId);

    /**
     * Check if Huawei Health OAuth is properly configured.
     *
     * @return true if client ID and secret are configured
     */
    boolean isConfigured();

    /**
     * Synchronize health data from Huawei Health for a user.
     *
     * <p>This method:
     * <ul>
     *   <li>Checks device connection status</li>
     *   <li>Refreshes access token if expired</li>
     *   <li>Fetches health data from Huawei Health API (or mock data)</li>
     *   <li>Maps data to internal HealthMetricRequest format</li>
     *   <li>Saves metrics via HealthMetricService</li>
     *   <li>Records sync history</li>
     *   <li>Updates lastSyncAt timestamp</li>
     * </ul>
     *
     * @param userId the user ID to sync data for
     * @return SyncResultVO containing sync results
     */
    SyncResultVO syncData(Long userId);
}