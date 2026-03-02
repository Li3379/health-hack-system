package com.hhs.service.domain;

import com.hhs.common.enums.MetricCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Service for determining metric category based on metric key.
 * Maps metric keys to their default categories (HEALTH or WELLNESS).
 */
@Slf4j
@Component
public class MetricCategoryService {

    /**
     * Medical/health metrics requiring clinical attention.
     */
    private static final Set<String> HEALTH_METRICS = Set.of(
            "heartRate",
            "systolicBP",
            "diastolicBP",
            "glucose",
            "bmi",
            "temperature",
            "weight"
    );

    /**
     * Wellness/lifestyle metrics for preventive health tracking.
     */
    private static final Set<String> WELLNESS_METRICS = Set.of(
            "sleepDuration",
            "sleepQuality",
            "steps",
            "exerciseMinutes",
            "waterIntake",
            "mood",
            "energy"
    );

    /**
     * Determine the category for a given metric key.
     *
     * @param metricKey the metric key to categorize
     * @return the appropriate MetricCategory, defaults to HEALTH for unknown keys
     */
    public MetricCategory determineCategory(String metricKey) {
        if (metricKey == null || metricKey.isBlank()) {
            log.warn("Empty metric key provided, defaulting to HEALTH category");
            return MetricCategory.HEALTH;
        }

        if (HEALTH_METRICS.contains(metricKey)) {
            return MetricCategory.HEALTH;
        }

        if (WELLNESS_METRICS.contains(metricKey)) {
            return MetricCategory.WELLNESS;
        }

        // Unknown metric keys default to HEALTH for backward compatibility
        log.debug("Unknown metric key '{}' defaulted to HEALTH category", metricKey);
        return MetricCategory.HEALTH;
    }

    /**
     * Check if a metric key is a known health metric.
     *
     * @param metricKey the metric key to check
     * @return true if it's a recognized health metric
     */
    public boolean isHealthMetric(String metricKey) {
        return HEALTH_METRICS.contains(metricKey);
    }

    /**
     * Check if a metric key is a known wellness metric.
     *
     * @param metricKey the metric key to check
     * @return true if it's a recognized wellness metric
     */
    public boolean isWellnessMetric(String metricKey) {
        return WELLNESS_METRICS.contains(metricKey);
    }

    /**
     * Check if a metric key is recognized (either health or wellness).
     *
     * @param metricKey the metric key to check
     * @return true if the metric key is recognized
     */
    public boolean isRecognizedMetric(String metricKey) {
        return HEALTH_METRICS.contains(metricKey) || WELLNESS_METRICS.contains(metricKey);
    }

    /**
     * Get all health metric keys.
     *
     * @return set of health metric keys
     */
    public Set<String> getHealthMetrics() {
        return HEALTH_METRICS;
    }

    /**
     * Get all wellness metric keys.
     *
     * @return set of wellness metric keys
     */
    public Set<String> getWellnessMetrics() {
        return WELLNESS_METRICS;
    }
}