package com.hhs.service.domain;

import com.hhs.common.enums.MetricCategory;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative single source of truth (SSOT) registry for health and wellness metric metadata.
 * Provides immutable definitions of standard keys, clinical boundaries, and display properties.
 */
@Component
public class HealthMetricMetaRegistry {

    private static final String VERSION_TAG = "v1.1-metric-meta";

    private final Map<String, MetricDefinition> definitions = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        registerCoreMetrics();
    }

    private void registerCoreMetrics() {
        register(new MetricDefinition(
                "heartRate",
                MetricKeyNormalizer.getAliases("heartRate"),
                "心率",
                MetricCategory.HEALTH,
                "次/分",
                0,
                new BigDecimal("1"),
                new BigDecimal("20"),
                new BigDecimal("300"),
                new BigDecimal("60"),
                new BigDecimal("100"),
                "例如: 75",
                "正常静息心率 60 ~ 100 次/分"
        ));

        register(new MetricDefinition(
                "systolicBP",
                MetricKeyNormalizer.getAliases("systolicBP"),
                "收缩压",
                MetricCategory.HEALTH,
                "mmHg",
                0,
                new BigDecimal("1"),
                new BigDecimal("40"),
                new BigDecimal("300"),
                new BigDecimal("90"),
                new BigDecimal("139"),
                "例如: 120",
                "正常收缩压 90 ~ 139 mmHg"
        ));

        register(new MetricDefinition(
                "diastolicBP",
                MetricKeyNormalizer.getAliases("diastolicBP"),
                "舒张压",
                MetricCategory.HEALTH,
                "mmHg",
                0,
                new BigDecimal("1"),
                new BigDecimal("20"),
                new BigDecimal("200"),
                new BigDecimal("60"),
                new BigDecimal("89"),
                "例如: 80",
                "正常舒张压 60 ~ 89 mmHg"
        ));

        register(new MetricDefinition(
                "bloodGlucose",
                MetricKeyNormalizer.getAliases("bloodGlucose"),
                "血糖",
                MetricCategory.HEALTH,
                "mmol/L",
                1,
                new BigDecimal("0.1"),
                new BigDecimal("1.0"),
                new BigDecimal("33.3"),
                new BigDecimal("3.9"),
                new BigDecimal("6.1"),
                "例如: 5.6",
                "正常空腹参考值 3.9 ~ 6.1 mmol/L"
        ));

        register(new MetricDefinition(
                "bodyTemperature",
                MetricKeyNormalizer.getAliases("bodyTemperature"),
                "体温",
                MetricCategory.HEALTH,
                "°C",
                1,
                new BigDecimal("0.1"),
                new BigDecimal("34.0"),
                new BigDecimal("42.5"),
                new BigDecimal("36.0"),
                new BigDecimal("37.2"),
                "例如: 36.5",
                "正常腋温参考值 36.0 ~ 37.2 °C"
        ));
    }

    /**
     * Register a metric definition into the registry.
     *
     * @param definition metric definition
     */
    public synchronized void register(MetricDefinition definition) {
        definitions.put(definition.canonicalKey(), definition);
    }

    /**
     * Get all registered metric definitions mapped by canonicalKey.
     *
     * @return unmodifiable map of canonical key to MetricDefinition
     */
    public Map<String, MetricDefinition> getAllDefinitions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    /**
     * Look up a metric definition by canonical key or any recognized alias.
     *
     * @param key metric key or alias
     * @return Optional containing MetricDefinition if found
     */
    public Optional<MetricDefinition> getDefinition(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(key);
        return Optional.ofNullable(definitions.get(canonicalKey));
    }

    /**
     * Check if a metric key or alias is recognized in the registry.
     *
     * @param key metric key or alias
     * @return true if registered
     */
    public boolean contains(String key) {
        return getDefinition(key).isPresent();
    }

    /**
     * Get the version tag representing the current metadata dictionary state.
     * Used for HTTP ETag negotiation and cache invalidation.
     *
     * @return version tag string
     */
    public String getVersionTag() {
        return VERSION_TAG;
    }
}
