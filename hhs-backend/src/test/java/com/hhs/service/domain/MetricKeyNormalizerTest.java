package com.hhs.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MetricKeyNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "heart_rate, heartRate",
            "HEART_RATE, heartRate",
            "heartrate, heartRate",
            "pulse, heartRate",
            "PULSE, heartRate",

            "systolic_bp, systolicBP",
            "SYSTOLIC_BP, systolicBP",
            "systolicBp, systolicBP",
            "systolicbp, systolicBP",
            "sbp, systolicBP",
            "SBP, systolicBP",
            // 库中现存的写法（user 8 那一行 metric_key='systolicBloodPressure'）。
            // 不在别名组里时它会走 lenient passthrough 原样返回，引擎的 getLatestMetric
            // 恒落空且不报错 —— 已复发两次的静默陷阱。
            "systolicBloodPressure, systolicBP",

            "diastolic_bp, diastolicBP",
            "DIASTOLIC_BP, diastolicBP",
            "diastolicBp, diastolicBP",
            "diastolicbp, diastolicBP",
            "dbp, diastolicBP",
            "DBP, diastolicBP",
            "diastolicBloodPressure, diastolicBP",

            "blood_glucose, bloodGlucose",
            "BLOOD_GLUCOSE, bloodGlucose",
            "bloodglucose, bloodGlucose",
            "glucose, bloodGlucose",
            "GLUCOSE, bloodGlucose",
            "glu, bloodGlucose",
            "GLU, bloodGlucose",

            "fasting_glucose, fastingGlucose",
            "FASTING_GLUCOSE, fastingGlucose",
            "fastingglucose, fastingGlucose",

            "postprandial_glucose, postprandialGlucose",
            "POSTPRANDIAL_GLUCOSE, postprandialGlucose",
            "postprandialglucose, postprandialGlucose",

            "body_temperature, bodyTemperature",
            "BODY_TEMPERATURE, bodyTemperature",
            "bodytemperature, bodyTemperature",
            "temperature, bodyTemperature",
            "TEMPERATURE, bodyTemperature",
            "temp, bodyTemperature",
            "TEMP, bodyTemperature",

            "blood_oxygen, bloodOxygen",
            "BLOOD_OXYGEN, bloodOxygen",
            "bloodoxygen, bloodOxygen",
            "spo2, bloodOxygen",
            "SPO2, bloodOxygen",

            "total_cholesterol, totalCholesterol",
            "TOTAL_CHOLESTEROL, totalCholesterol",
            "totalcholesterol, totalCholesterol",
            "tc, totalCholesterol",
            "TC, totalCholesterol",

            "triglycerides, triglycerides",
            "TRIGLYCERIDES, triglycerides",
            "tg, triglycerides",
            "TG, triglycerides",

            "hdl_cholesterol, hdlCholesterol",
            "HDL_CHOLESTEROL, hdlCholesterol",
            "hdlcholesterol, hdlCholesterol",
            "hdl, hdlCholesterol",
            "HDL, hdlCholesterol",

            "ldl_cholesterol, ldlCholesterol",
            "LDL_CHOLESTEROL, ldlCholesterol",
            "ldlcholesterol, ldlCholesterol",
            "ldl, ldlCholesterol",
            "LDL, ldlCholesterol",

            "uric_acid, uricAcid",
            "URIC_ACID, uricAcid",
            "uricacid, uricAcid",
            "ua, uricAcid",
            "UA, uricAcid",

            "creatinine, creatinine",
            "CREATININE, creatinine",
            "cr, creatinine",
            "CR, creatinine",

            "sleep_duration, sleepDuration",
            "SLEEP_DURATION, sleepDuration",
            "sleepduration, sleepDuration",

            "sleep_quality, sleepQuality",
            "SLEEP_QUALITY, sleepQuality",
            "sleepquality, sleepQuality",

            "steps, steps",
            "STEPS, steps",
            "step, steps",
            "STEP, steps",

            "exercise_minutes, exerciseMinutes",
            "EXERCISE_MINUTES, exerciseMinutes",
            "exerciseminutes, exerciseMinutes",

            "water_intake, waterIntake",
            "WATER_INTAKE, waterIntake",
            "waterintake, waterIntake",

            "mood, mood",
            "MOOD, mood",

            "energy, energy",
            "ENERGY, energy",

            "weight, weight",
            "WEIGHT, weight",

            "bmi, bmi",
            "BMI, bmi"
    })
    @DisplayName("Normalizes known variants, acronyms, and aliases to standard canonical keys")
    void testToCanonicalKeyKnownVariants(String input, String expected) {
        assertEquals(expected, MetricKeyNormalizer.toCanonicalKey(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "heartRate", "systolicBP", "diastolicBP", "bloodGlucose", "fastingGlucose",
            "postprandialGlucose", "bodyTemperature", "weight", "bmi", "bloodOxygen",
            "totalCholesterol", "triglycerides", "hdlCholesterol", "ldlCholesterol",
            "uricAcid", "creatinine", "sleepDuration", "sleepQuality", "steps",
            "exerciseMinutes", "waterIntake", "mood", "energy"
    })
    @DisplayName("Canonical keys are preserved unchanged")
    void testCanonicalKeysPreserved(String canonicalKey) {
        assertEquals(canonicalKey, MetricKeyNormalizer.toCanonicalKey(canonicalKey));
        assertTrue(MetricKeyNormalizer.isCanonicalKey(canonicalKey));
    }

    @Test
    @DisplayName("Unrecognized or custom keys are passed through leniently without alteration")
    void testLenientPassthrough() {
        assertEquals("custom_step_index", MetricKeyNormalizer.toCanonicalKey("custom_step_index"));
        assertEquals("user_defined_vital", MetricKeyNormalizer.toCanonicalKey("user_defined_vital"));
        assertEquals("randomKey123", MetricKeyNormalizer.toCanonicalKey("randomKey123"));

        assertNull(MetricKeyNormalizer.toCanonicalKey(null));
        assertEquals("", MetricKeyNormalizer.toCanonicalKey(""));
        assertEquals("   ", MetricKeyNormalizer.toCanonicalKey("   "));
    }

    @Test
    @DisplayName("getAliases returns comprehensive alias set including canonical key")
    void testGetAliases() {
        Set<String> sbpAliases = MetricKeyNormalizer.getAliases("systolicBP");
        assertTrue(sbpAliases.contains("systolicBP"));
        assertTrue(sbpAliases.contains("systolic_bp"));
        assertTrue(sbpAliases.contains("sbp"));

        // 库中现存的写法必须在别名组内 —— 否则那一行数据永远不参与评估，且失败是静默的。
        assertTrue(sbpAliases.contains("systolicBloodPressure"),
                "systolicBloodPressure 必须归到 systolicBP，否则库里现存那一行被静默忽略");

        // Reverse lookup from alias returns the same canonical set
        Set<String> sbpFromAlias = MetricKeyNormalizer.getAliases("systolic_bp");
        assertEquals(sbpAliases, sbpFromAlias);
        assertEquals(sbpAliases, MetricKeyNormalizer.getAliases("systolicBloodPressure"),
                "从新别名反查必须得到同一组，否则 getLatestMetric 与面板会用两套键");

        Set<String> dbpAliases = MetricKeyNormalizer.getAliases("diastolicBP");
        assertTrue(dbpAliases.contains("diastolicBloodPressure"));
        assertEquals(dbpAliases, MetricKeyNormalizer.getAliases("diastolicBloodPressure"));

        Set<String> hrAliases = MetricKeyNormalizer.getAliases("heart_rate");
        assertTrue(hrAliases.contains("heartRate"));
        assertTrue(hrAliases.contains("pulse"));

        // Unknown key returns singleton set
        Set<String> unknown = MetricKeyNormalizer.getAliases("unknown_foo");
        assertEquals(Set.of("unknown_foo"), unknown);

        // Null / blank returns empty set
        assertTrue(MetricKeyNormalizer.getAliases(null).isEmpty());
        assertTrue(MetricKeyNormalizer.getAliases("").isEmpty());
    }

    @Test
    @DisplayName("isCanonicalKey accurately differentiates canonical from aliases and unknown")
    void testIsCanonicalKey() {
        assertTrue(MetricKeyNormalizer.isCanonicalKey("heartRate"));
        assertTrue(MetricKeyNormalizer.isCanonicalKey("systolicBP"));
        assertFalse(MetricKeyNormalizer.isCanonicalKey("heart_rate"));
        assertFalse(MetricKeyNormalizer.isCanonicalKey("systolic_bp"));
        assertFalse(MetricKeyNormalizer.isCanonicalKey("unknown_key"));
        assertFalse(MetricKeyNormalizer.isCanonicalKey(null));
    }
}
