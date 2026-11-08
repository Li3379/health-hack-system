package com.hhs.service.domain;

import com.hhs.entity.UserThreshold;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Threshold range and boundary checking component.
 * Determines if values are within normal, warning, or critical ranges.
 *
 * <p>All four public range predicates share a single classification source,
 * {@link #classify(BigDecimal, UserThreshold)}, so the band semantics can
 * never fork between them (D-16 single-source classification).</p>
 */
@Slf4j
@Component
public class ThresholdRangeChecker {

    /**
     * The five bands a value can fall into relative to a threshold.
     * A null boundary is treated as unbounded: it never produces a band on its own.
     */
    public enum Band {
        /** Value at or below criticalLow. */
        CRITICAL_LOW,
        /** Value above criticalLow and at or below warningLow. */
        WARNING_LOW,
        /** Value above warningLow and below warningHigh. */
        NORMAL,
        /** Value at or above warningHigh and below criticalHigh. */
        WARNING_HIGH,
        /** Value at or above criticalHigh. */
        CRITICAL_HIGH
    }

    /**
     * Check if value is within normal range (no thresholds exceeded).
     */
    public boolean isNormalRange(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return true;
        return classify(BigDecimal.valueOf(value), threshold) == Band.NORMAL;
    }

    /**
     * Check if value is in warning range (exceeds warning but not critical).
     */
    public boolean isWarningRange(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return false;
        Band band = classify(BigDecimal.valueOf(value), threshold);
        return band == Band.WARNING_LOW || band == Band.WARNING_HIGH;
    }

    /**
     * Check if value is in critical range (exceeds critical thresholds).
     */
    public boolean isCriticalRange(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) return false;
        Band band = classify(BigDecimal.valueOf(value), threshold);
        return band == Band.CRITICAL_LOW || band == Band.CRITICAL_HIGH;
    }

    /**
     * Determine alert severity level based on value position.
     */
    public String getSeverityLevel(Double value, UserThreshold threshold) {
        if (value == null || threshold == null) {
            return "NORMAL";
        }
        Band band = classify(BigDecimal.valueOf(value), threshold);
        if (band == Band.CRITICAL_LOW || band == Band.CRITICAL_HIGH) {
            return "CRITICAL";
        }
        if (band == Band.WARNING_LOW || band == Band.WARNING_HIGH) {
            return "WARNING";
        }
        return "NORMAL";
    }

    /**
     * Get alert level priority (for sorting/filtering).
     */
    public String getAlertLevel(String severityLevel) {
        return "CRITICAL".equals(severityLevel) ? "HIGH" :
               "WARNING".equals(severityLevel) ? "MEDIUM" :
               "LOW";
    }

    /**
     * Single-source classification of a value into one of five bands.
     *
     * <p>Every band is derived from the two existing inclusive helpers only
     * ({@code isBelowThreshold} means value &lt;= boundary, {@code isAboveThreshold}
     * means value &gt;= boundary). Bands are evaluated critical-side first, so each
     * later match implicitly excludes the earlier band — no explicit two-sided
     * inequalities are written, which would introduce a second comparison base
     * (RESEARCH Pattern 1, the root cause of the original inverted-band defect).</p>
     */
    private Band classify(BigDecimal value, UserThreshold threshold) {
        boolean belowCriticalLow = isBelowThreshold(value, threshold.getCriticalLow());
        boolean belowWarningLow = isBelowThreshold(value, threshold.getWarningLow());
        boolean aboveCriticalHigh = isAboveThreshold(value, threshold.getCriticalHigh());
        boolean aboveWarningHigh = isAboveThreshold(value, threshold.getWarningHigh());

        if (belowCriticalLow) return Band.CRITICAL_LOW;
        if (belowWarningLow) return Band.WARNING_LOW;      // criticalLow already excluded
        if (aboveCriticalHigh) return Band.CRITICAL_HIGH;
        if (aboveWarningHigh) return Band.WARNING_HIGH;    // criticalHigh already excluded
        return Band.NORMAL;
    }

    // Helper methods
    private boolean isBelowThreshold(BigDecimal value, BigDecimal threshold) {
        return threshold != null && value.compareTo(threshold) <= 0;
    }

    private boolean isAboveThreshold(BigDecimal value, BigDecimal threshold) {
        return threshold != null && value.compareTo(threshold) >= 0;
    }
}
