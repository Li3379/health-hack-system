package com.hhs.service;

import com.hhs.entity.PushHistory;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Push history service interface
 */
public interface PushHistoryService {

    /**
     * Record a push attempt
     *
     * @param userId     the user ID
     * @param alertId    the alert ID (can be null)
     * @param result     the push result
     */
    void recordPush(Long userId, Long alertId, PushResult result);

    /**
     * Record a push attempt with custom message
     *
     * @param userId      the user ID
     * @param alertId     the alert ID
     * @param channelType the channel type
     * @param status      the status (SUCCESS, FAILED, SKIPPED)
     * @param message     the message
     */
    void recordPush(Long userId, Long alertId, ChannelType channelType,
                    String status, String message);

    /**
     * Get recent push history for a user
     *
     * @param userId the user ID
     * @param limit  maximum number of records
     * @return list of push history records
     */
    List<PushHistory> getRecentHistory(Long userId, int limit);

    /**
     * Get push count for a user on a specific channel within a time window
     *
     * @param userId     the user ID
     * @param channelType the channel type
     * @param since      the start time
     * @return push count
     */
    int getPushCountSince(Long userId, ChannelType channelType, LocalDateTime since);

    /**
     * Get push statistics for a user
     *
     * @param userId the user ID
     * @param since  the start time
     * @return statistics grouped by channel and status
     */
    List<Map<String, Object>> getChannelStats(Long userId, LocalDateTime since);

    /**
     * Check if rate limit is exceeded for a user on a specific channel
     *
     * @param userId     the user ID
     * @param channelType the channel type
     * @return true if rate limit is exceeded
     */
    boolean isRateLimitExceeded(Long userId, ChannelType channelType);
}