package com.hhs.service.domain;

import com.hhs.common.enums.MetricCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MetricMetadataRegistryTest {

    private HealthMetricMetaRegistry registry;
    private MetricDisplayFormatter displayFormatter;
    private MetricCategoryService categoryService;

    @BeforeEach
    void setUp() {
        registry = new HealthMetricMetaRegistry();
        displayFormatter = new MetricDisplayFormatter(registry);
        categoryService = new MetricCategoryService(registry);
    }

    @Test
    @DisplayName("Registry contains at least 23 metrics across health and wellness categories")
    void testRegistrySizeAndCompleteness() {
        Map<String, MetricDefinition> all = registry.getAllDefinitions();
        assertTrue(all.size() >= 23, "Registry should contain at least 23 metric definitions, found: " + all.size());

        long healthCount = all.values().stream().filter(d -> d.category() == MetricCategory.HEALTH).count();
        long wellnessCount = all.values().stream().filter(d -> d.category() == MetricCategory.WELLNESS).count();

        assertTrue(healthCount >= 16, "Expected at least 16 HEALTH metrics, got: " + healthCount);
        assertTrue(wellnessCount >= 7, "Expected at least 7 WELLNESS metrics, got: " + wellnessCount);
    }

    @Test
    @DisplayName("Every metric definition conforms to completeness and boundary rules (hardMin <= normalMin <= normalMax <= hardMax)")
    void testMetricDefinitionIntegrity() {
        Map<String, MetricDefinition> all = registry.getAllDefinitions();

        for (MetricDefinition def : all.values()) {
            assertNotNull(def.canonicalKey(), "canonicalKey must not be null");
            assertFalse(def.canonicalKey().isBlank(), "canonicalKey must not be blank");

            assertNotNull(def.displayName(), "displayName must not be null for " + def.canonicalKey());
            assertFalse(def.displayName().isBlank(), "displayName must not be blank for " + def.canonicalKey());

            assertNotNull(def.category(), "category must not be null for " + def.canonicalKey());
            assertNotNull(def.unit(), "unit must not be null for " + def.canonicalKey());
            assertNotNull(def.precision(), "precision must not be null for " + def.canonicalKey());
            assertTrue(def.precision() >= 0, "precision must be non-negative for " + def.canonicalKey());

            assertNotNull(def.step(), "step must not be null for " + def.canonicalKey());
            assertTrue(def.step().compareTo(BigDecimal.ZERO) > 0, "step must be > 0 for " + def.canonicalKey());

            assertNotNull(def.hardMin(), "hardMin must not be null for " + def.canonicalKey());
            assertNotNull(def.hardMax(), "hardMax must not be null for " + def.canonicalKey());
            assertNotNull(def.normalMin(), "normalMin must not be null for " + def.canonicalKey());
            assertNotNull(def.normalMax(), "normalMax must not be null for " + def.canonicalKey());

            assertTrue(def.hardMin().compareTo(def.hardMax()) <= 0,
                    "hardMin should be <= hardMax for " + def.canonicalKey());
            assertTrue(def.hardMin().compareTo(def.normalMin()) <= 0,
                    "hardMin should be <= normalMin for " + def.canonicalKey());
            assertTrue(def.normalMin().compareTo(def.normalMax()) <= 0,
                    "normalMin should be <= normalMax for " + def.canonicalKey());
            assertTrue(def.normalMax().compareTo(def.hardMax()) <= 0,
                    "normalMax should be <= hardMax for " + def.canonicalKey());

            assertNotNull(def.placeholder(), "placeholder must not be null for " + def.canonicalKey());
            assertNotNull(def.guideHint(), "guideHint must not be null for " + def.canonicalKey());
            assertTrue(def.aliases().contains(def.canonicalKey()),
                    "aliases must include canonicalKey for " + def.canonicalKey());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "heartRate", "systolicBP", "diastolicBP", "bloodGlucose", "fastingGlucose",
            "postprandialGlucose", "bodyTemperature", "weight", "bmi", "bloodOxygen",
            "totalCholesterol", "triglycerides", "hdlCholesterol", "ldlCholesterol",
            "uricAcid", "creatinine", "sleepDuration", "sleepQuality", "steps",
            "exerciseMinutes", "waterIntake", "mood", "energy"
    })
    @DisplayName("Lookup by canonical key returns expected definition")
    void testCanonicalLookup(String key) {
        Optional<MetricDefinition> def = registry.getDefinition(key);
        assertTrue(def.isPresent(), "Expected definition present for: " + key);
        assertEquals(key, def.get().canonicalKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "heart_rate", "HEART_RATE", "systolic_bp", "diastolic_bp", "glucose",
            "blood_glucose", "temp", "temperature", "spo2", "sleep_duration", "water_intake"
    })
    @DisplayName("Lookup by alias resolves to canonical definition")
    void testAliasLookup(String alias) {
        Optional<MetricDefinition> def = registry.getDefinition(alias);
        assertTrue(def.isPresent(), "Expected definition present for alias: " + alias);
        assertNotNull(def.get().canonicalKey());
    }

    @Test
    @DisplayName("Unknown or blank key lookup returns empty Optional")
    void testUnknownLookup() {
        assertTrue(registry.getDefinition(null).isEmpty());
        assertTrue(registry.getDefinition("").isEmpty());
        assertTrue(registry.getDefinition("   ").isEmpty());
        assertTrue(registry.getDefinition("unknownCustomKey").isEmpty());
    }

    @Test
    @DisplayName("MetricDisplayFormatter delegates to registry eliminating English leakage")
    void testDisplayFormatterDelegation() {
        assertEquals("心率", displayFormatter.getDisplayName("heartRate"));
        assertEquals("心率", displayFormatter.getDisplayName("heart_rate"));
        assertEquals("体温", displayFormatter.getDisplayName("bodyTemperature"));
        assertEquals("体温", displayFormatter.getDisplayName("temperature"));
        assertEquals("体温", displayFormatter.getDisplayName("temp"));
        assertEquals("收缩压", displayFormatter.getDisplayName("systolicBP"));
        assertEquals("收缩压", displayFormatter.getDisplayName("systolic_bp"));

        assertEquals("次/分", displayFormatter.getUnit("heartRate"));
        assertEquals("次/分", displayFormatter.getUnit("heart_rate"));
        assertEquals("mmHg", displayFormatter.getUnit("systolic_bp"));
        assertEquals("°C", displayFormatter.getUnit("bodyTemperature"));
        assertEquals("°C", displayFormatter.getUnit("temperature"));
        assertEquals("mmol/L", displayFormatter.getUnit("bloodGlucose"));
    }

    @Test
    @DisplayName("MetricCategoryService delegates to registry and supports alias resolution")
    void testCategoryServiceDelegation() {
        assertEquals(MetricCategory.HEALTH, categoryService.determineCategory("heartRate"));
        assertEquals(MetricCategory.HEALTH, categoryService.determineCategory("heart_rate"));
        assertEquals(MetricCategory.HEALTH, categoryService.determineCategory("body_temperature"));
        assertEquals(MetricCategory.WELLNESS, categoryService.determineCategory("sleep_duration"));
        assertEquals(MetricCategory.WELLNESS, categoryService.determineCategory("water_intake"));

        assertTrue(categoryService.isHealthMetric("systolic_bp"));
        assertTrue(categoryService.isWellnessMetric("exercise_minutes"));
        assertTrue(categoryService.isRecognizedMetric("blood_oxygen"));
        assertFalse(categoryService.isRecognizedMetric("non_existent_key"));
    }

    @Test
    @DisplayName("Standalone registry reports alertable=false with null recommendations until applyRecommendations runs")
    void testStandaloneRegistryNotAlertable() {
        // The registry is dependency-free: without the startup merge nothing is alertable
        for (MetricDefinition def : registry.getAllDefinitions().values()) {
            assertFalse(def.alertable(), def.canonicalKey() + " must not be alertable before merge");
            assertNull(def.recommended(), def.canonicalKey() + " must have null recommendation before merge");
        }
    }

    @Test
    @DisplayName("Exactly the six D-19 metrics are alertable after seed recommendations are applied")
    void testAlertableMetrics_AreExactlyTheSixSeedMetrics() {
        registry.applyRecommendations(seedRecommendations());

        Set<String> alertableKeys = registry.getAllDefinitions().entrySet().stream()
                .filter(e -> e.getValue().alertable())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Set equality, not a count only (D-19)
        assertEquals(Set.of("heartRate", "systolicBP", "diastolicBP", "bloodGlucose",
                "bodyTemperature", "bmi"), alertableKeys);
    }

    @Test
    @DisplayName("weight, bloodOxygen and every WELLNESS metric are not alertable")
    void testNonAlertableMetrics() {
        registry.applyRecommendations(seedRecommendations());

        Map<String, MetricDefinition> all = registry.getAllDefinitions();
        assertFalse(all.get("weight").alertable(), "weight seed has all four boundaries NULL");
        assertNull(all.get("weight").recommended());
        assertFalse(all.get("bloodOxygen").alertable(), "bloodOxygen has no alert_rule seed");
        assertNull(all.get("bloodOxygen").recommended());

        for (MetricDefinition def : all.values()) {
            if (def.category() == MetricCategory.WELLNESS) {
                assertFalse(def.alertable(), def.canonicalKey() + " (WELLNESS) must not be alertable");
                assertNull(def.recommended());
            }
        }
    }

    @Test
    @DisplayName("Every alertable metric carries a recommendation with at least one non-null boundary within hardMin/hardMax")
    void testRecommendationCompletenessAndBounds() {
        registry.applyRecommendations(seedRecommendations());

        for (MetricDefinition def : registry.getAllDefinitions().values()) {
            if (!def.alertable()) {
                continue;
            }
            RecommendedThreshold rec = def.recommended();
            assertNotNull(rec, "alertable metric must carry a recommendation: " + def.canonicalKey());
            assertTrue(rec.warningLow() != null || rec.warningHigh() != null
                            || rec.criticalLow() != null || rec.criticalHigh() != null,
                    "at least one non-null recommended boundary for " + def.canonicalKey());
            assertWithinHardLimits(def, "warningLow", rec.warningLow());
            assertWithinHardLimits(def, "warningHigh", rec.warningHigh());
            assertWithinHardLimits(def, "criticalLow", rec.criticalLow());
            assertWithinHardLimits(def, "criticalHigh", rec.criticalHigh());
        }
    }

    @Test
    @DisplayName("Legacy alias keys canonicalize before the merge (glucose -> bloodGlucose)")
    void testLegacyAliasKeyCanonicalizesDuringMerge() {
        Map<String, RecommendedThreshold> seeds = new LinkedHashMap<>();
        seeds.put("glucose", new RecommendedThreshold(new BigDecimal("3.9"), new BigDecimal("7.0"),
                new BigDecimal("3.0"), new BigDecimal("11.1")));

        registry.applyRecommendations(seeds);

        MetricDefinition glucose = registry.getAllDefinitions().get("bloodGlucose");
        assertTrue(glucose.alertable(), "legacy glucose row must map onto bloodGlucose");
        assertNotNull(glucose.recommended());
        assertEquals(0, new BigDecimal("7.0").compareTo(glucose.recommended().warningHigh()));
        assertFalse(registry.getAllDefinitions().get("heartRate").alertable(),
                "metrics absent from the map must stay non-alertable");
    }

    @Test
    @DisplayName("RecommendedThreshold.fallback derives the warning band from the normal range (D-06)")
    void testFallbackDerivation() {
        RecommendedThreshold fallback = RecommendedThreshold.fallback(
                new BigDecimal("60"), new BigDecimal("100"));

        assertEquals(new BigDecimal("60"), fallback.warningLow());
        assertEquals(new BigDecimal("100"), fallback.warningHigh());
        assertNull(fallback.criticalLow());
        assertNull(fallback.criticalHigh());
    }

    /**
     * Hand-built recommendation map mirroring the alert_rule seed rows in schema.sql
     * (12-03 canonicalized keys). No database needed: values are the seed four-boundary
     * columns, warningLow/warningHigh/criticalLow/criticalHigh order.
     */
    private Map<String, RecommendedThreshold> seedRecommendations() {
        Map<String, RecommendedThreshold> seeds = new LinkedHashMap<>();
        seeds.put("heartRate", new RecommendedThreshold(
                new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("120")));
        seeds.put("systolicBP", new RecommendedThreshold(
                new BigDecimal("90"), new BigDecimal("140"), new BigDecimal("80"), new BigDecimal("160")));
        seeds.put("diastolicBP", new RecommendedThreshold(
                new BigDecimal("60"), new BigDecimal("90"), new BigDecimal("50"), new BigDecimal("100")));
        seeds.put("bloodGlucose", new RecommendedThreshold(
                new BigDecimal("3.9"), new BigDecimal("7.0"), new BigDecimal("3.0"), new BigDecimal("11.1")));
        seeds.put("bodyTemperature", new RecommendedThreshold(
                new BigDecimal("36.0"), new BigDecimal("37.5"), new BigDecimal("35.5"), new BigDecimal("38.5")));
        seeds.put("bmi", new RecommendedThreshold(
                new BigDecimal("18.5"), new BigDecimal("28"), new BigDecimal("16"), new BigDecimal("35")));
        return seeds;
    }

    private void assertWithinHardLimits(MetricDefinition def, String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        assertTrue(value.compareTo(def.hardMin()) >= 0,
                field + " must be >= hardMin for " + def.canonicalKey());
        assertTrue(value.compareTo(def.hardMax()) <= 0,
                field + " must be <= hardMax for " + def.canonicalKey());
    }
}
