package com.hhs.service.push;

/**
 * Channel type enumeration
 */
public enum ChannelType {

    WEBSOCKET("WebSocket", "实时推送", false),
    EMAIL("Email", "邮件通知", true),
    WECOM("WeCom", "企业微信", true),
    FEISHU("Feishu", "飞书", true);

    private final String code;
    private final String label;
    private final boolean offlineSupported;

    ChannelType(String code, String label, boolean offlineSupported) {
        this.code = code;
        this.label = label;
        this.offlineSupported = offlineSupported;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOfflineSupported() {
        return offlineSupported;
    }

    public static ChannelType fromCode(String code) {
        for (ChannelType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown channel type: " + code);
    }
}