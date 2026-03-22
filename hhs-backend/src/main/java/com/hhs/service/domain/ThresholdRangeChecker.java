package com.hhs.service.domain;

import com.hhs.entity.UserThreshold;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Threshold range and boundary checking component.
 * Determines if values are within normal, warning, or critical ranges.
 */
@Slf4j
@Component
public class ThresholdRangeChecker {

    /**
     * Check if value is within normal range (no thresholds exceeded).
     */
    public boolean isNormalRange(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return true;
        BigDecimal metricValue = BigDecimal.valueOf(value);

        boolean belowLow = isBelowThreshold(metricValue, threshold.getWarningLow());
        boolean aboveHigh = isAboveThreshold(metricValue, threshold.getWarningHigh());

        return !belowLow && !aboveHigh;
    }

    /**
     * Check if value is in warning range (exceeds warning but not critical).
     */
    public boolean isWarningRange(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return false;
        BigDecimal metricValue = BigDecimal.valueOf(value);

        boolean belowCriticalLow = isBelowThreshold(metricValue, threshold.getCriticalLow());
        boolean aboveWarningLow = isAboveThreshold(metricValue, threshold.getWarningLow());

        boolean belowWarningHigh = isBelowThreshold(metricValue, threshold.getWarningHigh());
        boolean aboveCriticalHigh = isAboveThreshold(metricValue, threshold.getCriticalHigh());

        // Warning range: exceeds warning thresholds but not critical
        return (belowCriticalLow && aboveWarningLow) ||
               (belowWarningHigh && aboveCriticalHigh);
    }

    /**
     * Check if value is in critical range (exceeds critical thresholds).
     */
    public boolean isCriticalRange(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return false;
        BigDecimal metricValue = BigDecimal.valueOf(value);

        return isBelowThreshold(metricValue, threshold.getCriticalLow()) ||
               isAboveThreshold(metricValue, threshold.getCriticalHigh());
    }

    /**
     * Determine alert severity level based on value position.
     */
    public String getSeverityLevel(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) {
            return "NORMAL";
        }

        if (isCriticalRange(value, threshold)) {
            return "CRITICAL";
        } else if (isWarningRange(value, threshold)) {
            return "WARNING";
        } else {
            return "NORMAL";
        }
    }

    /**
     * Get alert level priority (for sorting/filtering).
     */
    public String getAlertLevel(String severityLevel) {
        return "CRITICAL".equals(severityLevel) ? "HIGH" :
               "WARNING".equals(severityLevel) ? "MEDIUM" :
               "LOW";
    }

    // Helper methods
    private boolean isBelowThreshold(BigDecimal value, BigDecimal threshold) {
        return threshold != null && value.compareTo(threshold) <= 0;
    }

    private boolean isAboveThreshold(BigDecimal value, BigDecimal threshold) {
        return threshold != null && value.compareTo(threshold) >= 0;
    }
}
