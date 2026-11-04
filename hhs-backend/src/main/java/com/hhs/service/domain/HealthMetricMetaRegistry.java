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

    public HealthMetricMetaRegistry() {
        registerAllMetrics();
    }

    @PostConstruct
    public void init() {
        if (definitions.isEmpty()) {
            registerAllMetrics();
        }
    }

    private void registerAllMetrics() {
        // HEALTH METRICS (16 items)
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
                "fastingGlucose",
                MetricKeyNormalizer.getAliases("fastingGlucose"),
                "空腹血糖",
                MetricCategory.HEALTH,
                "mmol/L",
                1,
                new BigDecimal("0.1"),
                new BigDecimal("1.0"),
                new BigDecimal("33.3"),
                new BigDecimal("3.9"),
                new BigDecimal("6.1"),
                "例如: 5.2",
                "正常空腹参考值 3.9 ~ 6.1 mmol/L"
        ));

        register(new MetricDefinition(
                "postprandialGlucose",
                MetricKeyNormalizer.getAliases("postprandialGlucose"),
                "餐后血糖",
                MetricCategory.HEALTH,
                "mmol/L",
                1,
                new BigDecimal("0.1"),
                new BigDecimal("1.0"),
                new BigDecimal("33.3"),
                new BigDecimal("3.9"),
                new BigDecimal("7.8"),
                "例如: 6.8",
                "餐后2小时参考值 3.9 ~ 7.8 mmol/L"
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

        register(new MetricDefinition(
                "weight",
                MetricKeyNormalizer.getAliases("weight"),
                "体重",
                MetricCategory.HEALTH,
                "kg",
                1,
                new BigDecimal("0.1"),
                new BigDecimal("1.0"),
                new BigDecimal("500.0"),
                new BigDecimal("40.0"),
                new BigDecimal("120.0"),
                "例如: 65.0",
                "健康体重通常与身高相适应 (BMI 18.5 ~ 23.9)"
        ));

        register(new MetricDefinition(
                "bmi",
                MetricKeyNormalizer.getAliases("bmi"),
                "BMI",
                MetricCategory.HEALTH,
                "kg/m²",
                1,
                new BigDecimal("0.1"),
                new BigDecimal("5.0"),
                new BigDecimal("70.0"),
                new BigDecimal("18.5"),
                new BigDecimal("23.9"),
                "例如: 22.0",
                "正常 BMI 参考值 18.5 ~ 23.9"
        ));

        register(new MetricDefinition(
                "bloodOxygen",
                MetricKeyNormalizer.getAliases("bloodOxygen"),
                "血氧",
                MetricCategory.HEALTH,
                "%",
                0,
                new BigDecimal("1"),
                new BigDecimal("50"),
                new BigDecimal("100"),
                new BigDecimal("95"),
                new BigDecimal("100"),
                "例如: 98",
                "正常静息血氧 95 ~ 100%"
        ));

        register(new MetricDefinition(
                "totalCholesterol",
                MetricKeyNormalizer.getAliases("totalCholesterol"),
                "总胆固醇",
                MetricCategory.HEALTH,
                "mmol/L",
                2,
                new BigDecimal("0.01"),
                new BigDecimal("0.5"),
                new BigDecimal("30.0"),
                new BigDecimal("2.8"),
                new BigDecimal("5.2"),
                "例如: 4.50",
                "理想参考值 < 5.20 mmol/L"
        ));

        register(new MetricDefinition(
                "triglycerides",
                MetricKeyNormalizer.getAliases("triglycerides"),
                "甘油三酯",
                MetricCategory.HEALTH,
                "mmol/L",
                2,
                new BigDecimal("0.01"),
                new BigDecimal("0.1"),
                new BigDecimal("30.0"),
                new BigDecimal("0.4"),
                new BigDecimal("1.7"),
                "例如: 1.20",
                "理想参考值 < 1.70 mmol/L"
        ));

        register(new MetricDefinition(
                "hdlCholesterol",
                MetricKeyNormalizer.getAliases("hdlCholesterol"),
                "高密度脂蛋白",
                MetricCategory.HEALTH,
                "mmol/L",
                2,
                new BigDecimal("0.01"),
                new BigDecimal("0.1"),
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("2.2"),
                "例如: 1.30",
                "正常参考值 >= 1.00 mmol/L"
        ));

        register(new MetricDefinition(
                "ldlCholesterol",
                MetricKeyNormalizer.getAliases("ldlCholesterol"),
                "低密度脂蛋白",
                MetricCategory.HEALTH,
                "mmol/L",
                2,
                new BigDecimal("0.01"),
                new BigDecimal("0.1"),
                new BigDecimal("15.0"),
                new BigDecimal("1.3"),
                new BigDecimal("3.4"),
                "例如: 2.60",
                "理想参考值 < 3.40 mmol/L"
        ));

        register(new MetricDefinition(
                "uricAcid",
                MetricKeyNormalizer.getAliases("uricAcid"),
                "尿酸",
                MetricCategory.HEALTH,
                "μmol/L",
                0,
                new BigDecimal("1"),
                new BigDecimal("20"),
                new BigDecimal("1500"),
                new BigDecimal("150"),
                new BigDecimal("420"),
                "例如: 320",
                "正常参考值 150 ~ 420 μmol/L"
        ));

        register(new MetricDefinition(
                "creatinine",
                MetricKeyNormalizer.getAliases("creatinine"),
                "肌酐",
                MetricCategory.HEALTH,
                "μmol/L",
                0,
                new BigDecimal("1"),
                new BigDecimal("10"),
                new BigDecimal("2000"),
                new BigDecimal("44"),
                new BigDecimal("133"),
                "例如: 85",
                "正常参考值 44 ~ 133 μmol/L"
        ));

        // WELLNESS METRICS (7 items)
        register(new MetricDefinition(
                "sleepDuration",
                MetricKeyNormalizer.getAliases("sleepDuration"),
                "睡眠时长",
                MetricCategory.WELLNESS,
                "小时",
                1,
                new BigDecimal("0.5"),
                new BigDecimal("0.0"),
                new BigDecimal("24.0"),
                new BigDecimal("6.0"),
                new BigDecimal("9.0"),
                "例如: 7.5",
                "推荐每晚睡眠 6.0 ~ 9.0 小时"
        ));

        register(new MetricDefinition(
                "sleepQuality",
                MetricKeyNormalizer.getAliases("sleepQuality"),
                "睡眠质量",
                MetricCategory.WELLNESS,
                "级",
                0,
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("5"),
                new BigDecimal("3"),
                new BigDecimal("5"),
                "1~5",
                "1: 很差, 3: 一般, 5: 很好"
        ));

        register(new MetricDefinition(
                "steps",
                MetricKeyNormalizer.getAliases("steps"),
                "步数",
                MetricCategory.WELLNESS,
                "步",
                0,
                new BigDecimal("100"),
                new BigDecimal("0"),
                new BigDecimal("150000"),
                new BigDecimal("6000"),
                new BigDecimal("10000"),
                "例如: 8000",
                "推荐每日活动 6000 ~ 10000 步"
        ));

        register(new MetricDefinition(
                "exerciseMinutes",
                MetricKeyNormalizer.getAliases("exerciseMinutes"),
                "运动时长",
                MetricCategory.WELLNESS,
                "分钟",
                0,
                new BigDecimal("5"),
                new BigDecimal("0"),
                new BigDecimal("1440"),
                new BigDecimal("30"),
                new BigDecimal("120"),
                "例如: 45",
                "推荐每日适度运动 30 ~ 60 分钟"
        ));

        register(new MetricDefinition(
                "waterIntake",
                MetricKeyNormalizer.getAliases("waterIntake"),
                "饮水量",
                MetricCategory.WELLNESS,
                "ml",
                0,
                new BigDecimal("50"),
                new BigDecimal("0"),
                new BigDecimal("20000"),
                new BigDecimal("1500"),
                new BigDecimal("2500"),
                "例如: 2000",
                "推荐每日饮水 1500 ~ 2500 ml"
        ));

        register(new MetricDefinition(
                "mood",
                MetricKeyNormalizer.getAliases("mood"),
                "心情",
                MetricCategory.WELLNESS,
                "级",
                0,
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("5"),
                new BigDecimal("3"),
                new BigDecimal("5"),
                "1~5",
                "1: 极差, 3: 平静, 5: 极佳"
        ));

        register(new MetricDefinition(
                "energy",
                MetricKeyNormalizer.getAliases("energy"),
                "精力",
                MetricCategory.WELLNESS,
                "级",
                0,
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("5"),
                new BigDecimal("3"),
                new BigDecimal("5"),
                "1~5",
                "1: 疲惫, 3: 良好, 5: 充沛"
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
