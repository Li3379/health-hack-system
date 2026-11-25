package com.hhs.service.push;

import com.hhs.dto.AlertVO;

/**
 * Push channel interface for multi-channel alert delivery
 */
public interface PushChannel {

    /**
     * Push alert to user via this channel
     *
     * @param userId the target user ID
     * @param alert  the alert to push
     * @return push result
     */
    PushResult push(Long userId, AlertVO alert);

    /**
     * Get the channel type
     *
     * @return channel type
     */
    ChannelType getChannelType();

    /**
     * Check if this channel is available for the user
     *
     * <p>Availability is "can deliver right now". It mixes two different things: whether the user
     * configured the channel at all, and whether it happens to be reachable this second
     * (WebSocket: is the user connected).
     *
     * @param userId the user ID
     * @return true if available
     */
    boolean isAvailable(Long userId);

    /**
     * Whether the user has this channel configured, ignoring transient reachability.
     *
     * <p>{@code pushAll} needs the distinction: a channel the user never configured should leave
     * a SKIPPED row so "nothing was delivered" is visible, while a configured channel that is
     * momentarily unreachable (a closed WebSocket tab) must stay silent — one row per alert per
     * offline user would bury the history under WebSocket noise.
     *
     * <p>The default delegates to {@link #isAvailable(Long)} because for the webhook/email
     * channels the two questions coincide: the URL either exists or it does not, and nothing
     * about it is transient. Only {@code WebSocketPushChannel} overrides it.
     *
     * @param userId the user ID
     * @return true if the user has this channel configured
     */
    default boolean isConfigured(Long userId) {
        return isAvailable(userId);
    }

    /**
     * Check if this channel supports offline delivery
     *
     * @return true if supports offline
     */
    default boolean supportsOffline() {
        return false;
    }
}