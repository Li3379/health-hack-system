package com.hhs.enums;

/**
 * Platform capability enum.
 * Represents features supported by a device platform.
 */
public enum PlatformCapability {
    /**
     * Platform supports standard OAuth 2.0 web authorization flow.
     */
    WEB_OAUTH("Web OAuth 授权"),

    /**
     * Platform supports real-time data synchronization.
     */
    REALTIME_SYNC("实时数据同步"),

    /**
     * Platform can retrieve historical health data.
     */
    HISTORICAL_DATA("历史数据查询"),

    /**
     * Platform requires mini-program for authorization.
     * Used for WeChat Sport.
     */
    MINI_PROGRAM("小程序授权"),

    /**
     * Platform requires native mobile application.
     * Used for Apple HealthKit.
     */
    NATIVE_APP("原生应用");

    private final String displayName;

    PlatformCapability(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get the Chinese display name for UI.
     */
    public String getDisplayName() {
        return displayName;
    }
}