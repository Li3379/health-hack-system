package com.hhs.service.domain;

import com.hhs.common.enums.MetricCategory;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

/**
 * Immutable definition of a health or wellness metric.
 * Acts as the authoritative single source of truth for metric attributes.
 *
 * @param canonicalKey standard camelCase metric key (e.g., heartRate, systolicBP)
 * @param aliases known historical or alternative key aliases
 * @param displayName Chinese display name
 * @param category HEALTH or WELLNESS
 * @param unit measurement unit string
 * @param precision decimal places to retain
 * @param step suggested UI input step size
 * @param hardMin physiological minimum value for hard validation
 * @param hardMax physiological maximum value for hard validation
 * @param normalMin clinical reference normal range lower bound
 * @param normalMax clinical reference normal range upper bound
 * @param placeholder UI input placeholder text
 * @param guideHint clinical guide / reference explanation hint
 * @param alertable whether the metric can be evaluated by the alert engine, i.e. a usable
 *                   alert_rule seed exists (D-03/D-19); populated by
 *                   {@link HealthMetricMetaRegistry#applyRecommendations(Map)} at startup
 * @param recommended backend-derived recommended boundaries ({@link RecommendedThreshold}, D-06);
 *                    non-null only when {@code alertable} is true
 */
public record MetricDefinition(
        String canonicalKey,
        Set<String> aliases,
        String displayName,
        MetricCategory category,
        String unit,
        Integer precision,
        BigDecimal step,
        BigDecimal hardMin,
        BigDecimal hardMax,
        BigDecimal normalMin,
        BigDecimal normalMax,
        String placeholder,
        String guideHint,
        boolean alertable,
        RecommendedThreshold recommended
) {

    public MetricDefinition {
        aliases = aliases != null ? Collections.unmodifiableSet(Set.copyOf(aliases)) : Set.of();
        if (alertable && recommended == null) {
            throw new IllegalArgumentException(
                    "alertable metric must carry a recommended threshold: " + canonicalKey);
        }
    }
}
