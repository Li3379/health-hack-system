package com.hhs.service.domain;

import com.hhs.common.enums.MetricCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

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
}
