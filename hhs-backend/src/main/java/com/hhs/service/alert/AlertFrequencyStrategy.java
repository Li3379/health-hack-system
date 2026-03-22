package com.hhs.service.alert;

import com.hhs.entity.HealthAlert;
import com.hhs.mapper.HealthAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Alert Frequency Strategy
 * Controls push frequency to prevent notification fatigue
 *
 * Strategy:
 * - First 3 alerts: Normal push
 * - 4th and beyond: Daily summary push only
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertFrequencyStrategy {

    private final HealthAlertMapper healthAlertMapper;

    /**
     * Maximum number of normal pushes per day per metric
     */
    private static final int MAX_DAILY_NORMAL_PUSHES = 3;

    /**
     * Determine if an alert should be pushed based on frequency rules
     *
     * @param alert the alert to check
     * @return true if the alert should be pushed
     */
    public boolean shouldPush(HealthAlert alert) {
        if (alert == null || alert.getUserId() == null || alert.getMetricKey() == null) {
            return false;
        }

        int dailyCount = countTodayAlerts(alert.getUserId(), alert.getMetricKey());

        // First 3 alerts: normal push
        if (dailyCount < MAX_DAILY_NORMAL_PUSHES) {
            log.debug("Normal push allowed: dailyCount={}, max={}", dailyCount, MAX_DAILY_NORMAL_PUSHES);
            return true;
        }

        // 4th and beyond: Check if daily summary already sent
        LocalDateTime lastSummaryTime = getLastSummaryTime(alert.getUserId(), alert.getMetricKey());

        boolean shouldSendSummary = lastSummaryTime == null ||
                lastSummaryTime.toLocalDate().isBefore(LocalDate.now());

        if (shouldSendSummary) {
            log.info("Summary push allowed for user={}, metric={}", alert.getUserId(), alert.getMetricKey());
        } else {
            log.debug("Summary already sent today, skipping push");
        }

        return shouldSendSummary;
    }

    /**
     * Count today's alerts for a user and metric
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @return count of today's alerts
     */
    public int countTodayAlerts(Long userId, String metricKey) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Long count = healthAlertMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getUserId, userId)
                        .eq(HealthAlert::getMetricKey, metricKey)
                        .ge(HealthAlert::getCreatedAt, startOfDay)
        );

        return count != null ? count.intValue() : 0;
    }

    /**
     * Get the last summary notification time
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @return the last summary time, or null if never
     */
    public LocalDateTime getLastSummaryTime(Long userId, String metricKey) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        HealthAlert summaryAlert = healthAlertMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getUserId, userId)
                        .eq(HealthAlert::getMetricKey, metricKey)
                        .eq(HealthAlert::getAlertType, "DAILY_SUMMARY")
                        .ge(HealthAlert::getCreatedAt, startOfDay)
                        .orderByDesc(HealthAlert::getCreatedAt)
                        .last("LIMIT 1")
        );

        return summaryAlert != null ? summaryAlert.getCreatedAt() : null;
    }

    /**
     * Check if a daily summary should be generated
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @return true if summary should be generated
     */
    public boolean shouldGenerateDailySummary(Long userId, String metricKey) {
        int dailyCount = countTodayAlerts(userId, metricKey);

        // Only generate summary if there are more than 3 alerts
        if (dailyCount <= MAX_DAILY_NORMAL_PUSHES) {
            return false;
        }

        // Check if summary already sent today
        LocalDateTime lastSummaryTime = getLastSummaryTime(userId, metricKey);
        return lastSummaryTime == null || lastSummaryTime.toLocalDate().isBefore(LocalDate.now());
    }

    /**
     * Get push frequency status for a user and metric
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @return frequency status information
     */
    public FrequencyStatus getFrequencyStatus(Long userId, String metricKey) {
        int dailyCount = countTodayAlerts(userId, metricKey);
        int remainingNormalPushes = Math.max(0, MAX_DAILY_NORMAL_PUSHES - dailyCount);
        boolean inSummaryMode = dailyCount >= MAX_DAILY_NORMAL_PUSHES;

        return new FrequencyStatus(
                dailyCount,
                remainingNormalPushes,
                inSummaryMode,
                MAX_DAILY_NORMAL_PUSHES
        );
    }

    /**
     * Frequency status record
     */
    public record FrequencyStatus(
            int dailyAlertCount,
            int remainingNormalPushes,
            boolean inSummaryMode,
            int maxDailyNormalPushes
    ) {}
}