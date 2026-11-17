package com.hhs.service.domain;

import com.hhs.entity.HealthMetric;
import com.hhs.vo.MetricCoverageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the "which of my parameters were actually used" view.
 *
 * <p>The risk engine reads three metrics out of the twenty-three the registry knows about. A
 * user looking at a score has no way to tell that from the score itself: a normal-looking total
 * can mean "everything you measured is fine" or "we only had one of the three". This component
 * answers the per-parameter question directly, using the registry's own reference ranges rather
 * than a second set of thresholds.</p>
 */
@Component
@RequiredArgsConstructor
public class MetricCoverageAssembler {

    /**
     * Which risk assessments consume which metric, and what the metric can add.
     *
     * <p>Kept next to the ranges rather than derived, because the mapping is a product decision
     * (this is what the rule engine reads), not something inferable from the metric itself.
     * Every entry here must correspond to a real {@code getLatestMetric} call in the engine;
     * a stale entry would tell the user their metric matters when it does not.</p>
     */
    private static final Map<String, String> RISK_CONSUMERS = buildRiskConsumers();

    private final HealthMetricMetaRegistry metaRegistry;

    private static Map<String, String> buildRiskConsumers() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("bloodGlucose", "糖尿病评估：超过 6.1 mmol/L 计入 30 分");
        map.put("systolicBP", "高血压评估：超过 130 mmHg 计入 35 分");
        map.put("diastolicBP", "高血压评估：超过 85 mmHg 计入 30 分");
        return Map.copyOf(map);
    }

    /**
     * Build the coverage rows for a user's latest values.
     *
     * @param recentMetrics metrics ordered newest first; aliases are accepted
     * @return one row per registry metric, ordered HEALTH first then WELLNESS
     */
    public List<MetricCoverageVO> assemble(List<HealthMetric> recentMetrics) {
        Map<String, HealthMetric> latest = latestByCanonicalKey(recentMetrics);

        List<MetricCoverageVO> rows = new ArrayList<>();
        for (MetricDefinition def : metaRegistry.getAllDefinitions().values()) {
            HealthMetric hit = latest.get(def.canonicalKey());
            rows.add(toRow(def, hit));
        }
        // HEALTH 在前，同类内按显示名排序，让"体检指标"聚在一起，便于扫读。
        rows.sort(Comparator
                .comparing((MetricCoverageVO r) -> "HEALTH".equals(r.category()) ? 0 : 1)
                .thenComparing(MetricCoverageVO::label));
        return List.copyOf(rows);
    }

    /**
     * Index the newest value per canonical key, accepting every alias.
     *
     * @param metrics metrics ordered newest first
     * @return canonical key to newest row
     */
    private Map<String, HealthMetric> latestByCanonicalKey(List<HealthMetric> metrics) {
        Map<String, HealthMetric> latest = new LinkedHashMap<>();
        for (HealthMetric metric : metrics) {
            if (metric.getMetricKey() == null) {
                continue;
            }
            String canonical = MetricKeyNormalizer.toCanonicalKey(metric.getMetricKey());
            latest.putIfAbsent(canonical, metric);
        }
        return latest;
    }

    /**
     * Turn one definition plus its latest reading into a coverage row.
     *
     * @param def registry definition
     * @param hit the latest reading, or {@code null} when the user has none
     * @return the row
     */
    private MetricCoverageVO toRow(MetricDefinition def, HealthMetric hit) {
        String range = formatRange(def);
        String riskNote = RISK_CONSUMERS.get(def.canonicalKey());

        if (hit == null || hit.getValue() == null) {
            return new MetricCoverageVO(def.canonicalKey(), def.displayName(), def.unit(),
                    def.category().name(), MetricCoverageVO.STATE_MISSING, null, range, riskNote);
        }

        BigDecimal value = hit.getValue();
        String state = classify(def, value);
        return new MetricCoverageVO(def.canonicalKey(), def.displayName(), def.unit(),
                def.category().name(), state, formatValue(value, def), range, riskNote);
    }

    /**
     * Judge a value against the registry's clinical reference range.
     *
     * <p>Metrics without a range are reported NORMAL: the system has a value but no basis to
     * call it abnormal, and inventing a threshold here would be a second source of truth.</p>
     *
     * @param def   registry definition
     * @param value the reading
     * @return HIGH, LOW or NORMAL
     */
    private String classify(MetricDefinition def, BigDecimal value) {
        if (def.normalMax() != null && value.compareTo(def.normalMax()) > 0) {
            return MetricCoverageVO.STATE_HIGH;
        }
        if (def.normalMin() != null && value.compareTo(def.normalMin()) < 0) {
            return MetricCoverageVO.STATE_LOW;
        }
        return MetricCoverageVO.STATE_NORMAL;
    }

    /**
     * Format a value using the definition's precision.
     *
     * @param value raw value
     * @param def   registry definition
     * @return display string
     */
    private String formatValue(BigDecimal value, MetricDefinition def) {
        int precision = def.precision() != null ? def.precision() : 0;
        return value.setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Format the reference range for display.
     *
     * @param def registry definition
     * @return e.g. {@code 3.9–6.1}, or {@code null} when the registry defines no range
     */
    private String formatRange(MetricDefinition def) {
        int precision = def.precision() != null ? def.precision() : 0;
        String min = def.normalMin() == null ? null
                : def.normalMin().setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString();
        String max = def.normalMax() == null ? null
                : def.normalMax().setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString();
        if (min == null && max == null) {
            return null;
        }
        if (min == null) {
            return "≤ " + max;
        }
        if (max == null) {
            return "≥ " + min;
        }
        return min + "–" + max;
    }
}
