package com.hhs.service.domain;

import com.hhs.entity.UserThreshold;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Threshold comparison component.
 * Handles comparison of metric values against threshold values.
 */
@Slf4j
@Component
public class ThresholdComparator {

    /**
     * Compare a metric value to its threshold.
     *
     * @param value the metric value
     * @param threshold the user threshold to compare against
     * @return comparison result: 1 if above, 0 if within, -1 if below
     */
    public int compare(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) {
            log.debug("Cannot compare: value or threshold is null");
            return 0;
        }

        BigDecimal metricValue = BigDecimal.valueOf(value);

        // Check critical high
        if (threshold.getCriticalHigh() != null &&
            metricValue.compareTo(threshold.getCriticalHigh()) >= 0) {
            return 1;
        }

        // Check warning high
        if (threshold.getWarningHigh() != null &&
            metricValue.compareTo(threshold.getWarningHigh()) >= 0) {
            return 1;
        }

        // Check critical low
        if (threshold.getCriticalLow() != null &&
            metricValue.compareTo(threshold.getCriticalLow()) <= 0) {
            return -1;
        }

        // Check warning low
        if (threshold.getWarningLow() != null &&
            metricValue.compareTo(threshold.getWarningLow()) <= 0) {
            return -1;
        }

        log.debug("Value {} within normal range for threshold", metricValue);
        return 0;
    }

    /**
     * Check if value exceeds high threshold.
     */
    public boolean isHigh(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return false;
        BigDecimal metricValue = BigDecimal.valueOf(value);

        return (threshold.getCriticalHigh() != null && metricValue.compareTo(threshold.getCriticalHigh()) >= 0) ||
               (threshold.getWarningHigh() != null && metricValue.compareTo(threshold.getWarningHigh()) >= 0);
    }

    /**
     * Check if value is below low threshold.
     */
    public boolean isLow(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return false;
        BigDecimal metricValue = BigDecimal.valueOf(value);

        return (threshold.getCriticalLow() != null && metricValue.compareTo(threshold.getCriticalLow()) <= 0) ||
               (threshold.getWarningLow() != null && metricValue.compareTo(threshold.getWarningLow()) <= 0);
    }
}
