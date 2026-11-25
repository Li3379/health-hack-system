package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.PushHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Push history mapper
 */
@Mapper
public interface PushHistoryMapper extends BaseMapper<PushHistory> {

    /**
     * Count push attempts for a user on a specific channel since a given time.
     *
     * <p>Not the rate-limit denominator — see {@link #countDeliveredAlertsSince}. Kept because
     * it answers "how many attempts" (a diagnostic question), which is deliberately different
     * from "how much usable delivery happened".
     */
    @Select("SELECT COUNT(*) FROM push_history WHERE user_id = #{userId} " +
            "AND channel_type = #{channelType} AND pushed_at >= #{since}")
    int countByUserAndChannelSince(@Param("userId") Long userId,
                                    @Param("channelType") String channelType,
                                    @Param("since") LocalDateTime since);

    /**
     * Count <em>delivered real alerts</em> for a user on a channel since a given time.
     *
     * <p>This is the rate-limit denominator, and all three conditions are deliberate:
     * <ul>
     *   <li>{@code alert_id IS NOT NULL} — a manual "test push" carries no alert id and must not
     *       consume the budget real alerts need. Diagnostics that lock out delivery is the worst
     *       ordering: the user clicks "test" to find out why mail is broken and ends up blocking
     *       the very alerts they were trying to confirm.</li>
     *   <li>{@code status = 'SUCCESS'} — a FAILED attempt delivered nothing. Counting failures
     *       turned "your authorization code is wrong" into "Skipped: Rate limit exceeded" after
     *       twenty attempts, hiding the real cause behind a rate-limit message. Failure volume is
     *       already bounded upstream by {@code AlertRateLimiter} (≤10 alerts/hour/user), so
     *       excluding them opens no floodgate.</li>
     *   <li>Rate-limit SKIPPED rows are excluded for the same reason: counting skip rows would
     *       extend the lockout every time the lockout fired.</li>
     * </ul>
     */
    @Select("SELECT COUNT(*) FROM push_history WHERE user_id = #{userId} " +
            "AND channel_type = #{channelType} AND pushed_at >= #{since} " +
            "AND alert_id IS NOT NULL AND status = 'SUCCESS'")
    int countDeliveredAlertsSince(@Param("userId") Long userId,
                                   @Param("channelType") String channelType,
                                   @Param("since") LocalDateTime since);

    /**
     * Count push attempts for a user since a given time (all channels)
     */
    @Select("SELECT COUNT(*) FROM push_history WHERE user_id = #{userId} AND pushed_at >= #{since}")
    int countByUserSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Get recent push history for a user
     */
    @Select("SELECT * FROM push_history WHERE user_id = #{userId} " +
            "ORDER BY pushed_at DESC LIMIT #{limit}")
    List<PushHistory> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * Get push statistics grouped by channel for a user
     */
    @Select("SELECT channel_type AS channelType, status, COUNT(*) AS count " +
            "FROM push_history WHERE user_id = #{userId} " +
            "AND pushed_at >= #{since} " +
            "GROUP BY channel_type, status")
    List<Map<String, Object>> getChannelStatsByUserSince(@Param("userId") Long userId,
                                                          @Param("since") LocalDateTime since);

    /**
     * The most recent delivery attempt per channel for a user, since a cut-off.
     *
     * <p>Drives the "server says configured, but delivery is failing" badge on the settings page.
     * Deliberately reads what already happened instead of probing the provider: an SMTP probe is a
     * real AUTH, and QQ answers repeated failed AUTH with a frequency limit — so a page refresh
     * would manufacture the very outage it reports, and would contradict the "never retry a
     * failed AUTH" rule the email channel follows. Reading the last result costs nothing and is
     * evidence rather than inference.
     *
     * <p>Rows with {@code alert_id IS NULL} are the manual "test push" (see
     * {@code PushConfigController.testChannel}); they are included because a failing test push is
     * exactly the diagnostic the user needs to see reflected. Only the newest row per channel
     * matters — one success proves the credentials work right now.
     *
     * @param userId the user ID
     * @param since  ignore attempts older than this
     * @return newest row per channel, ordered newest first
     */
    @Select("SELECT * FROM push_history WHERE user_id = #{userId} AND pushed_at >= #{since} " +
            "AND id IN (SELECT MAX(id) FROM push_history WHERE user_id = #{userId} " +
            "           AND pushed_at >= #{since} GROUP BY channel_type) " +
            "ORDER BY pushed_at DESC")
    List<PushHistory> findLatestPerChannelSince(@Param("userId") Long userId,
                                                @Param("since") LocalDateTime since);

    /**
     * Rows the compensation job may retry now.
     *
     * <p>Selection is deliberately narrow — every condition removes a class of useless retry:
     * <ul>
     *   <li>{@code status = 'FAILED'} — SUCCESS needs nothing, SKIPPED means "not configured"
     *       (or rate-limited), which retrying cannot fix;</li>
     *   <li>{@code alert_id IS NOT NULL} — manual test pushes are diagnostics, not delivery;
     *       retrying them would spam the provider on the user's behalf;</li>
     *   <li>{@code next_retry_at IS NOT NULL AND next_retry_at <= now} — rows written before this
     *       feature existed are NULL and therefore never resurrected;</li>
     *   <li>{@code retry_count < maxRetries} — bounded, so a permanently broken channel stops.</li>
     * </ul>
     *
     * @param now        current time (injected so tests are deterministic)
     * @param maxRetries stop retrying rows that already consumed this many attempts
     * @param limit      cap per run so one backlog cannot monopolise the executor
     * @return rows due for a retry, oldest failure first
     */
    @Select("SELECT * FROM push_history WHERE status = 'FAILED' " +
            "AND alert_id IS NOT NULL " +
            "AND next_retry_at IS NOT NULL AND next_retry_at <= #{now} " +
            "AND retry_count < #{maxRetries} " +
            "ORDER BY next_retry_at ASC LIMIT #{limit}")
    List<PushHistory> findDueForRetry(@Param("now") LocalDateTime now,
                                       @Param("maxRetries") int maxRetries,
                                       @Param("limit") int limit);

    /**
     * Book the next attempt (or stop) for one row.
     *
     * <p>Explicit SQL rather than {@code updateById}: a null {@code nextRetryAt} must actually
     * clear the column to mean "no longer scheduled", and MyBatis-Plus's default update strategy
     * skips nulls.
     *
     * @param id           row to update
     * @param retryCount   attempts made so far, including the one that just failed
     * @param nextRetryAt  next attempt time, or {@code null} to stop retrying
     * @return rows updated
     */
    @Update("UPDATE push_history SET retry_count = #{retryCount}, next_retry_at = #{nextRetryAt} " +
            "WHERE id = #{id}")
    int updateRetrySchedule(@Param("id") Long id,
                            @Param("retryCount") int retryCount,
                            @Param("nextRetryAt") LocalDateTime nextRetryAt);
}