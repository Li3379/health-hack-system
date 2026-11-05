package com.hhs.service.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Domain-isolated extension registry for routine blood examination metrics (CBC).
 * Provides clinical reference ranges and physiological limits for medical laboratory reports
 * without polluting the core daily lifestyle HealthMetricMetaRegistry.
 */
@Component
public class LabRoutineMetricRegistry {

    public record LabDefinition(
            String key,
            String displayName,
            Set<String> aliases,
            String unit,
            BigDecimal normalMin,
            BigDecimal normalMax,
            BigDecimal hardMin,
            BigDecimal hardMax
    ) {
        public LabDefinition {
            aliases = aliases != null ? Collections.unmodifiableSet(Set.copyOf(aliases)) : Set.of();
        }
    }

    private final Map<String, LabDefinition> definitions = new LinkedHashMap<>();

    public LabRoutineMetricRegistry() {
        register(new LabDefinition(
                "hemoglobin",
                "血红蛋白",
                Set.of("血红蛋白", "血红蛋白计数", "HGB", "Hb", "hemoglobin"),
                "g/L",
                new BigDecimal("120"),
                new BigDecimal("160"),
                new BigDecimal("20"),
                new BigDecimal("300")
        ));

        register(new LabDefinition(
                "wbc",
                "白细胞计数",
                Set.of("白细胞", "白细胞计数", "WBC", "wbc"),
                "10^9/L",
                new BigDecimal("4.0"),
                new BigDecimal("10.0"),
                new BigDecimal("0.1"),
                new BigDecimal("200.0")
        ));

        register(new LabDefinition(
                "rbc",
                "红细胞计数",
                Set.of("红细胞", "红细胞计数", "RBC", "rbc"),
                "10^12/L",
                new BigDecimal("4.0"),
                new BigDecimal("5.5"),
                new BigDecimal("0.5"),
                new BigDecimal("15.0")
        ));

        register(new LabDefinition(
                "platelet",
                "血小板计数",
                Set.of("血小板", "血小板计数", "PLT", "platelet"),
                "10^9/L",
                new BigDecimal("100"),
                new BigDecimal("300"),
                new BigDecimal("5"),
                new BigDecimal("2000")
        ));
    }

    private void register(LabDefinition def) {
        definitions.put(def.key(), def);
    }

    /**
     * Look up a routine lab test definition by key or recognized alias.
     *
     * @param keyOrAlias canonical key or alias name
     * @return Optional containing LabDefinition if found
     */
    public Optional<LabDefinition> getDefinition(String keyOrAlias) {
        if (keyOrAlias == null || keyOrAlias.isBlank()) {
            return Optional.empty();
        }
        String trimmed = keyOrAlias.trim();
        if (definitions.containsKey(trimmed)) {
            return Optional.of(definitions.get(trimmed));
        }
        for (LabDefinition def : definitions.values()) {
            if (def.aliases().contains(trimmed) || def.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(trimmed))) {
                return Optional.of(def);
            }
        }
        return Optional.empty();
    }

    /**
     * Check if a key or alias is recognized in the lab routine registry.
     *
     * @param keyOrAlias key or alias to check
     * @return true if recognized
     */
    public boolean contains(String keyOrAlias) {
        return getDefinition(keyOrAlias).isPresent();
    }

    /**
     * Get all registered lab routine metric definitions.
     *
     * @return unmodifiable map of key to definition
     */
    public Map<String, LabDefinition> getAllDefinitions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }
}
