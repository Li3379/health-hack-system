package com.hhs.service.alert;

import com.hhs.entity.HealthAlert;
import com.hhs.mapper.HealthAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Alert Deduplication Service
 * Determines if alerts should be merged based on similarity rules
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDeduplicator {

    private final HealthAlertMapper healthAlertMapper;

    /**
     * Time window for alert merging (in hours)
     */
    private static final int MERGE_TIME_WINDOW_HOURS = 1;

    /**
     * Maximum value deviation for merging (10%)
     */
    private static final double MAX_VALUE_DEVIATION = 0.10;

    /**
     * Determine if a new alert should be merged with an existing one
     *
     * @param existingAlert the existing alert
     * @param newValue      the new metric value
     * @param alertLevel    the new alert level
     * @return true if the alerts should be merged
     */
    public boolean shouldMerge(HealthAlert existingAlert, BigDecimal newValue, String alertLevel) {
        if (existingAlert == null || newValue == null) {
            return false;
        }

        // 1. Same alert level required
        if (!existingAlert.getAlertLevel().equals(alertLevel)) {
            log.debug("Alert levels differ: existing={}, new={}", existingAlert.getAlertLevel(), alertLevel);
            return false;
        }

        // 2. Within time window (1 hour)
        Duration duration = Duration.between(existingAlert.getCreatedAt(), LocalDateTime.now());
        if (duration.toHours() >= MERGE_TIME_WINDOW_HOURS) {
            log.debug("Time window exceeded: {} hours", duration.toHours());
            return false;
        }

        // 3. Value deviation check (< 10%)
        BigDecimal existingValue = existingAlert.getCurrentValue();
        if (existingValue == null || existingValue.compareTo(BigDecimal.ZERO) == 0) {
            return true; // Can't calculate deviation, merge anyway
        }

        BigDecimal diff = newValue.subtract(existingValue).abs();
        BigDecimal deviation = diff.divide(existingValue.abs(), 4, BigDecimal.ROUND_HALF_UP);

        boolean withinDeviation = deviation.compareTo(BigDecimal.valueOf(MAX_VALUE_DEVIATION)) < 0;
        log.debug("Value deviation: {}%, merge={}", deviation.multiply(BigDecimal.valueOf(100)), withinDeviation);

        return withinDeviation;
    }

    /**
     * Merge a new alert into an existing one
     * Updates occurrence count and last occurrence time, persists to database
     *
     * @param existingAlert the existing alert to update
     * @param newValue      the new metric value
     * @return the updated alert
     */
    public HealthAlert merge(HealthAlert existingAlert, BigDecimal newValue) {
        if (existingAlert == null) {
            return null;
        }

        existingAlert.setOccurrenceCount(existingAlert.getOccurrenceCount() + 1);
        existingAlert.setLastOccurrenceAt(LocalDateTime.now());
        existingAlert.setCurrentValue(newValue); // Update to latest value

        // Persist to database
        healthAlertMapper.updateById(existingAlert);

        log.info("Merged alert: id={}, occurrenceCount={}", existingAlert.getId(), existingAlert.getOccurrenceCount());

        return existingAlert;
    }

    /**
     * Check if an alert is similar to another (for deduplication purposes)
     *
     * @param alert1 first alert
     * @param alert2 second alert
     * @return true if alerts are similar
     */
    public boolean areSimilar(HealthAlert alert1, HealthAlert alert2) {
        if (alert1 == null || alert2 == null) {
            return false;
        }

        // Same user
        if (!alert1.getUserId().equals(alert2.getUserId())) {
            return false;
        }

        // Same metric
        if (!alert1.getMetricKey().equals(alert2.getMetricKey())) {
            return false;
        }

        // Same alert level
        if (!alert1.getAlertLevel().equals(alert2.getAlertLevel())) {
            return false;
        }

        // Within time window
        LocalDateTime earlier = alert1.getCreatedAt().isBefore(alert2.getCreatedAt())
                ? alert1.getCreatedAt() : alert2.getCreatedAt();
        LocalDateTime later = alert1.getCreatedAt().isAfter(alert2.getCreatedAt())
                ? alert1.getCreatedAt() : alert2.getCreatedAt();

        if (Duration.between(earlier, later).toHours() >= MERGE_TIME_WINDOW_HOURS) {
            return false;
        }

        // Value deviation check
        BigDecimal value1 = alert1.getCurrentValue();
        BigDecimal value2 = alert2.getCurrentValue();

        if (value1 != null && value2 != null && value1.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal diff = value1.subtract(value2).abs();
            BigDecimal deviation = diff.divide(value1.abs(), 4, BigDecimal.ROUND_HALF_UP);
            return deviation.compareTo(BigDecimal.valueOf(MAX_VALUE_DEVIATION)) < 0;
        }

        return true;
    }
}