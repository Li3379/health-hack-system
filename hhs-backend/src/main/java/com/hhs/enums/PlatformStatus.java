package com.hhs.enums;

/**
 * Platform availability status enum.
 * Represents the operational status of a device platform.
 */
public enum PlatformStatus {
    /**
     * Platform is fully configured and ready to use.
     */
    AVAILABLE("可用", "success"),

    /**
     * Platform requires WeChat mini-program for OAuth flow.
     * Web OAuth is not supported.
     */
    REQUIRES_MINI_PROGRAM("需要小程序", "warning"),

    /**
     * Platform requires native iOS/Android application.
     * Web OAuth is not supported.
     */
    REQUIRES_APP("需要应用", "warning"),

    /**
     * Platform implementation is planned but not yet available.
     */
    COMING_SOON("即将支持", "info"),

    /**
     * Platform OAuth is not configured.
     * Contact administrator to configure credentials.
     */
    NOT_CONFIGURED("未配置", "danger"),

    /**
     * Platform service is implemented but not available.
     * May be due to missing dependencies or maintenance.
     */
    UNAVAILABLE("暂不可用", "danger");

    private final String displayName;
    private final String tagType;

    PlatformStatus(String displayName, String tagType) {
        this.displayName = displayName;
        this.tagType = tagType;
    }

    /**
     * Get the Chinese display name for UI.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the Element Plus tag type for styling.
     */
    public String getTagType() {
        return tagType;
    }

    /**
     * Check if the platform can be connected via web OAuth.
     */
    public boolean isWebConnectable() {
        return this == AVAILABLE;
    }

    /**
     * Check if the platform status requires user action.
     */
    public boolean requiresUserAction() {
        return this == REQUIRES_MINI_PROGRAM || this == REQUIRES_APP;
    }
}