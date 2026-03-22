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
     * @param userId the user ID
     * @return true if available
     */
    boolean isAvailable(Long userId);

    /**
     * Check if this channel supports offline delivery
     *
     * @return true if supports offline
     */
    default boolean supportsOffline() {
        return false;
    }
}