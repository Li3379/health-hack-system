package com.hhs.service.domain;

import com.hhs.common.enums.MetricCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Service for determining metric category based on metric key.
 * Maps metric keys to their default categories (HEALTH or WELLNESS).
 * Prioritizes HealthMetricMetaRegistry authoritative dictionary with fallback to legacy sets.
 */
@Slf4j
@Component
public class MetricCategoryService {

    private final HealthMetricMetaRegistry registry;

    /**
     * Routine blood-examination keys (hemoglobin / wbc / rbc / plt) live in their own registry so
     * the core lifestyle dictionary stays clean, but they are still valid metric keys that OCR
     * writes into {@code health_metric}. Acceptance must consult both registries — checking only
     * the core one would reject every CBC row.
     */
    private final LabRoutineMetricRegistry labRegistry;

    public MetricCategoryService() {
        this(null);
    }

    public MetricCategoryService(@Nullable HealthMetricMetaRegistry registry) {
        this(registry, null);
    }

    @Autowired
    public MetricCategoryService(@Nullable HealthMetricMetaRegistry registry,
                                 @Nullable LabRoutineMetricRegistry labRegistry) {
        this.registry = registry;
        this.labRegistry = labRegistry != null ? labRegistry : new LabRoutineMetricRegistry();
    }

    /**
     * Medical/health metrics requiring clinical attention.
     */
    private static final Set<String> HEALTH_METRICS = Set.of(
            "heartRate",
            "systolicBP",
            "diastolicBP",
            "glucose",
            "bloodGlucose",
            "fastingGlucose",
            "postprandialGlucose",
            "bmi",
            "bodyTemperature",
            "temperature",
            "weight",
            "bloodOxygen",
            "totalCholesterol",
            "triglycerides",
            "hdlCholesterol",
            "ldlCholesterol",
            "uricAcid",
            "creatinine"
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
     * <p>Unknown keys still default to HEALTH for backward compatibility — callers that need to
     * <em>reject</em> unknown keys must gate on {@link #isWritableMetric(String)} first. The
     * default is kept because this method is also used to label rows read back from the database,
     * where an unrecognised legacy key must render as something rather than throw.
     *
     * @param metricKey the metric key to categorize
     * @return the appropriate MetricCategory, defaults to HEALTH for unknown keys
     */
    public MetricCategory determineCategory(String metricKey) {
        if (metricKey == null || metricKey.isBlank()) {
            log.warn("Empty metric key provided, defaulting to HEALTH category");
            return MetricCategory.HEALTH;
        }

        if (registry != null) {
            Optional<MetricDefinition> def = registry.getDefinition(metricKey);
            if (def.isPresent()) {
                return def.get().category();
            }
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
        if (metricKey == null || metricKey.isBlank()) {
            return false;
        }
        if (registry != null) {
            Optional<MetricDefinition> def = registry.getDefinition(metricKey);
            if (def.isPresent()) {
                return def.get().category() == MetricCategory.HEALTH;
            }
        }
        return HEALTH_METRICS.contains(metricKey);
    }

    /**
     * Check if a metric key is a known wellness metric.
     *
     * @param metricKey the metric key to check
     * @return true if it's a recognized wellness metric
     */
    public boolean isWellnessMetric(String metricKey) {
        if (metricKey == null || metricKey.isBlank()) {
            return false;
        }
        if (registry != null) {
            Optional<MetricDefinition> def = registry.getDefinition(metricKey);
            if (def.isPresent()) {
                return def.get().category() == MetricCategory.WELLNESS;
            }
        }
        return WELLNESS_METRICS.contains(metricKey);
    }

    /**
     * Check if a metric key is recognized (either health or wellness, core or routine-lab).
     *
     * @param metricKey the metric key to check
     * @return true if the metric key is recognized
     */
    public boolean isRecognizedMetric(String metricKey) {
        if (metricKey == null || metricKey.isBlank()) {
            return false;
        }
        if (registry != null && registry.contains(metricKey)) {
            return true;
        }
        // CBC keys are real metric keys written by OCR even though they live in their own registry.
        if (labRegistry.contains(metricKey)) {
            return true;
        }
        return HEALTH_METRICS.contains(metricKey) || WELLNESS_METRICS.contains(metricKey);
    }

    /**
     * True when this key may be written to {@code health_metric}.
     *
     * <p>Same set as {@link #isRecognizedMetric(String)}, but the intent at a write site is
     * "is this a key the dictionaries know about", not "which category does it belong to".
     * Kept separate so a future loosening of category lookup cannot accidentally widen the
     * accepted write surface.
     */
    public boolean isWritableMetric(String metricKey) {
        return isRecognizedMetric(MetricKeyNormalizer.toCanonicalKey(metricKey));
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
