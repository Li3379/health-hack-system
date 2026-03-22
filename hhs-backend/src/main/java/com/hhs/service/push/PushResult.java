package com.hhs.service.push;

/**
 * Push result object
 */
public class PushResult {

    private final boolean success;
    private final String message;
    private final ChannelType channelType;
    private final long timestamp;

    private PushResult(boolean success, String message, ChannelType channelType) {
        this.success = success;
        this.message = message;
        this.channelType = channelType;
        this.timestamp = System.currentTimeMillis();
    }

    public static PushResult success(ChannelType channelType) {
        return new PushResult(true, "Push successful", channelType);
    }

    public static PushResult success(ChannelType channelType, String message) {
        return new PushResult(true, message, channelType);
    }

    public static PushResult failed(ChannelType channelType, String reason) {
        return new PushResult(false, reason, channelType);
    }

    public static PushResult skipped(ChannelType channelType, String reason) {
        return new PushResult(false, "Skipped: " + reason, channelType);
    }

    public static PushResult offline(ChannelType channelType) {
        return new PushResult(false, "User offline", channelType);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("PushResult{success=%s, channel=%s, message='%s'}",
                success, channelType, message);
    }
}