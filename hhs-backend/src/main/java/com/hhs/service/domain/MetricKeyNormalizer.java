package com.hhs.service.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative normalizer for metric keys and alias mappings.
 * Enforces standard camelCase naming while protecting domain-specific acronyms (e.g., systolicBP).
 * Unknown metric keys are passed through leniently without alteration.
 */
public final class MetricKeyNormalizer {

    private static final Set<String> CANONICAL_KEYS;
    private static final Map<String, String> EXACT_ALIAS_MAP;
    private static final Map<String, String> SIMPLIFIED_MAP;
    private static final Map<String, Set<String>> CANONICAL_TO_ALIASES;

    static {
        Set<String> canonical = new LinkedHashSet<>();
        // Health metrics
        canonical.add("heartRate");
        canonical.add("systolicBP");
        canonical.add("diastolicBP");
        canonical.add("bloodGlucose");
        canonical.add("fastingGlucose");
        canonical.add("postprandialGlucose");
        canonical.add("bodyTemperature");
        canonical.add("weight");
        canonical.add("bmi");
        canonical.add("bloodOxygen");
        canonical.add("totalCholesterol");
        canonical.add("triglycerides");
        canonical.add("hdlCholesterol");
        canonical.add("ldlCholesterol");
        canonical.add("uricAcid");
        canonical.add("creatinine");
        // Wellness metrics
        canonical.add("sleepDuration");
        canonical.add("sleepQuality");
        canonical.add("steps");
        canonical.add("exerciseMinutes");
        canonical.add("waterIntake");
        canonical.add("mood");
        canonical.add("energy");

        CANONICAL_KEYS = Collections.unmodifiableSet(canonical);

        // Predefined aliases mapped to canonical keys
        Map<String, Set<String>> aliasGroup = new HashMap<>();
        aliasGroup.put("heartRate", Set.of("heart_rate", "HEART_RATE", "heartrate", "pulse", "PULSE"));
        aliasGroup.put("systolicBP", Set.of("systolic_bp", "systolicBp", "SYSTOLIC_BP", "sbp", "SBP"));
        aliasGroup.put("diastolicBP", Set.of("diastolic_bp", "diastolicBp", "DIASTOLIC_BP", "dbp", "DBP"));
        aliasGroup.put("bloodGlucose", Set.of("blood_glucose", "BLOOD_GLUCOSE", "bloodglucose", "glucose", "GLUCOSE", "glu", "GLU"));
        aliasGroup.put("fastingGlucose", Set.of("fasting_glucose", "FASTING_GLUCOSE", "fastingglucose"));
        aliasGroup.put("postprandialGlucose", Set.of("postprandial_glucose", "POSTPRANDIAL_GLUCOSE", "postprandialglucose"));
        aliasGroup.put("bodyTemperature", Set.of("body_temperature", "BODY_TEMPERATURE", "bodytemperature", "temperature", "TEMPERATURE", "temp", "TEMP"));
        aliasGroup.put("weight", Set.of("WEIGHT"));
        aliasGroup.put("bmi", Set.of("BMI"));
        aliasGroup.put("bloodOxygen", Set.of("blood_oxygen", "BLOOD_OXYGEN", "bloodoxygen", "spo2", "SPO2"));
        aliasGroup.put("totalCholesterol", Set.of("total_cholesterol", "TOTAL_CHOLESTEROL", "totalcholesterol", "tc", "TC"));
        aliasGroup.put("triglycerides", Set.of("TRIGLYCERIDES", "tg", "TG"));
        aliasGroup.put("hdlCholesterol", Set.of("hdl_cholesterol", "HDL_CHOLESTEROL", "hdlcholesterol", "hdl", "HDL"));
        aliasGroup.put("ldlCholesterol", Set.of("ldl_cholesterol", "LDL_CHOLESTEROL", "ldlcholesterol", "ldl", "LDL"));
        aliasGroup.put("uricAcid", Set.of("uric_acid", "URIC_ACID", "uricacid", "ua", "UA"));
        aliasGroup.put("creatinine", Set.of("CREATININE", "cr", "CR"));
        aliasGroup.put("sleepDuration", Set.of("sleep_duration", "SLEEP_DURATION", "sleepduration"));
        aliasGroup.put("sleepQuality", Set.of("sleep_quality", "SLEEP_QUALITY", "sleepquality"));
        aliasGroup.put("steps", Set.of("STEPS", "step", "STEP"));
        aliasGroup.put("exerciseMinutes", Set.of("exercise_minutes", "EXERCISE_MINUTES", "exerciseminutes"));
        aliasGroup.put("waterIntake", Set.of("water_intake", "WATER_INTAKE", "waterintake"));
        aliasGroup.put("mood", Set.of("MOOD"));
        aliasGroup.put("energy", Set.of("ENERGY"));

        Map<String, String> exact = new HashMap<>();
        Map<String, String> simplified = new HashMap<>();
        Map<String, Set<String>> fullAliases = new HashMap<>();

        for (String cKey : CANONICAL_KEYS) {
            Set<String> set = new LinkedHashSet<>();
            set.add(cKey);
            simplified.put(simplify(cKey), cKey);

            Set<String> declaredAliases = aliasGroup.getOrDefault(cKey, Collections.emptySet());
            for (String alias : declaredAliases) {
                set.add(alias);
                exact.put(alias, cKey);
                simplified.put(simplify(alias), cKey);
            }
            fullAliases.put(cKey, Collections.unmodifiableSet(set));
        }

        EXACT_ALIAS_MAP = Collections.unmodifiableMap(exact);
        SIMPLIFIED_MAP = Collections.unmodifiableMap(simplified);
        CANONICAL_TO_ALIASES = Collections.unmodifiableMap(fullAliases);
    }

    private MetricKeyNormalizer() {
        // Utility class
    }

    /**
     * Normalize a raw metric key into its canonical camelCase identifier.
     * Preserves acronym capitalization for known metrics (e.g., systolicBP).
     * If the key is null, blank, or unrecognized, the original string is returned unchanged.
     *
     * @param rawKey the raw metric key string
     * @return the canonical metric key or the original input if unrecognized
     */
    public static String toCanonicalKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return rawKey;
        }

        String trimmed = rawKey.trim();
        if (CANONICAL_KEYS.contains(trimmed)) {
            return trimmed;
        }

        String exactMatch = EXACT_ALIAS_MAP.get(trimmed);
        if (exactMatch != null) {
            return exactMatch;
        }

        String simplifiedKey = simplify(trimmed);
        String simplifiedMatch = SIMPLIFIED_MAP.get(simplifiedKey);
        if (simplifiedMatch != null) {
            return simplifiedMatch;
        }

        // Unknown metric key: lenient passthrough
        return rawKey;
    }

    /**
     * Get all known aliases for a metric key, including the canonical key itself.
     * Unrecognized keys return an immutable set containing only the provided key.
     *
     * @param key any metric key or alias
     * @return unmodifiable set of known aliases and canonical key
     */
    public static Set<String> getAliases(String key) {
        if (key == null || key.isBlank()) {
            return Collections.emptySet();
        }

        String canonicalKey = toCanonicalKey(key);
        Set<String> aliases = CANONICAL_TO_ALIASES.get(canonicalKey);
        if (aliases != null) {
            return aliases;
        }

        return Collections.singleton(key);
    }

    /**
     * Check if the provided key is a known canonical metric key.
     *
     * @param key the key to check
     * @return true if it is a canonical key
     */
    public static boolean isCanonicalKey(String key) {
        return key != null && CANONICAL_KEYS.contains(key.trim());
    }

    /**
     * Get all known canonical metric keys.
     *
     * @return unmodifiable set of canonical keys
     */
    public static Set<String> getCanonicalKeys() {
        return CANONICAL_KEYS;
    }

    private static String simplify(String str) {
        return str.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }
}
