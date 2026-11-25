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
     * Newest delivery attempt per channel since a cut-off.
     *
     * <p>Backs the "server says configured, but delivery is failing" badge on the settings page:
     * it reports what actually happened last instead of probing the provider. An SMTP probe is a
     * real AUTH and providers rate-limit repeated failures, so probing on every page read would
     * cause the outage it reports.
     *
     * @param userId the user ID
     * @param since  ignore attempts older than this
     * @return newest row per channel, newest first; empty when nothing was attempted
     */
    List<PushHistory> getLatestPerChannelSince(Long userId, LocalDateTime since);

    /**
     * Schedule a failed row for compensation, or mark it exhausted.
     *
     * <p>Called by the compensation job after each attempt. Kept here (rather than in the job) so
     * the retry bookkeeping lives next to the rest of the history writes.
     *
     * @param historyId  the {@code push_history} row that just failed again
     * @param retryCount attempts already made, including this one
     * @param nextRetryAt when to try again, or {@code null} to stop retrying
     */
    void scheduleRetry(Long historyId, int retryCount, LocalDateTime nextRetryAt);

    /**
     * Failed real-alert rows that are due for a retry now.
     *
     * @param maxRetries stop retrying rows that already consumed this many attempts
     * @param limit       cap per run
     * @return rows due for a retry, oldest failure first
     */
    List<PushHistory> findDueForRetry(int maxRetries, int limit);

    /**
     * Check if rate limit is exceeded for a user on a specific channel
     *
     * @param userId     the user ID
     * @param channelType the channel type
     * @return true if rate limit is exceeded
     */
    boolean isRateLimitExceeded(Long userId, ChannelType channelType);
}