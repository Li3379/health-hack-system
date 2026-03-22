package com.hhs.dto;

import com.hhs.enums.PlatformCapability;
import com.hhs.enums.PlatformStatus;

import java.util.List;

/**
 * Platform metadata DTO.
 * Contains comprehensive information about a device platform's availability and capabilities.
 *
 * @param platform Platform identifier (e.g., "huawei", "xiaomi", "wechat", "apple")
 * @param displayName Chinese display name for UI
 * @param status Current availability status
 * @param capabilities List of supported capabilities
 * @param supportedDataTypes List of supported health data types (e.g., "heart_rate", "steps")
 * @param unavailableReason Human-readable reason if platform is unavailable
 * @param guideUrl URL for user guidance (mini-program link, app download, etc.)
 * @param icon Platform icon identifier or URL
 */
public record PlatformMetadata(
    String platform,
    String displayName,
    PlatformStatus status,
    List<PlatformCapability> capabilities,
    List<String> supportedDataTypes,
    String unavailableReason,
    String guideUrl,
    String icon
) {
    /**
     * Check if this platform can be connected via web OAuth.
     */
    public boolean isConnectable() {
        return status != null && status.isWebConnectable();
    }

    /**
     * Check if this platform requires user action (download app, use mini-program).
     */
    public boolean requiresAction() {
        return status != null && status.requiresUserAction();
    }

    /**
     * Create a builder for constructing PlatformMetadata.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for PlatformMetadata.
     */
    public static class Builder {
        private String platform;
        private String displayName;
        private PlatformStatus status;
        private List<PlatformCapability> capabilities = List.of();
        private List<String> supportedDataTypes = List.of();
        private String unavailableReason;
        private String guideUrl;
        private String icon;

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder status(PlatformStatus status) {
            this.status = status;
            return this;
        }

        public Builder capabilities(List<PlatformCapability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder capabilities(PlatformCapability... capabilities) {
            this.capabilities = List.of(capabilities);
            return this;
        }

        public Builder supportedDataTypes(List<String> supportedDataTypes) {
            this.supportedDataTypes = supportedDataTypes;
            return this;
        }

        public Builder supportedDataTypes(String... supportedDataTypes) {
            this.supportedDataTypes = List.of(supportedDataTypes);
            return this;
        }

        public Builder unavailableReason(String unavailableReason) {
            this.unavailableReason = unavailableReason;
            return this;
        }

        public Builder guideUrl(String guideUrl) {
            this.guideUrl = guideUrl;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public PlatformMetadata build() {
            return new PlatformMetadata(
                platform,
                displayName,
                status,
                capabilities,
                supportedDataTypes,
                unavailableReason,
                guideUrl,
                icon
            );
        }
    }
}