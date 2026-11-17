package com.hhs.service.domain;

import com.hhs.entity.RealtimeMetric;
import com.hhs.mapper.RealtimeMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Metrics scorer - handles real-time health metrics scoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsScorer {

    private final RealtimeMetricMapper realtimeMetricMapper;

    /**
     * Calculate score based on latest health metrics
     *
     * @param userId the user ID
     * @return score from 0-100
     */
    public int calculate(Long userId) {
        return calculate(userId, AssumptionCollector.noop());
    }

    /**
     * Calculate score based on latest health metrics, disclosing the empty-set substitution.
     *
     * <p>The neutral-70 branch is the only substitution this scorer performs. The per-metric
     * penalties below describe score loss, not value substitution, so they add no assumption.</p>
     *
     * @param userId    the user ID
     * @param collector receives the substitution sentence, ignored when {@code null}
     * @return score from 0-100
     */
    public int calculate(Long userId, AssumptionCollector collector) {
        List<RealtimeMetric> metrics = realtimeMetricMapper.getLatestMetricsByUser(userId);
        if (metrics.isEmpty()) {
            if (collector != null) {
                collector.add("缺少健康指标数据，指标维度按中性 70 分计算");
            }
            return 70;
        }

        int score = 100;
        int count = 0;

        for (RealtimeMetric metric : metrics) {
            count++;
            BigDecimal value = metric.getValue();
            String key = metric.getMetricKey();
            score -= getMetricPenalty(key, value);
        }

        // Adjust based on number of metrics tracked
        if (count < 3) {
            score = (score + 70) / 2;
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Get penalty for metric value outside normal range.
     *
     * <p>The key is canonicalized first: rows may be stored under a legacy alias (for example
     * {@code glucose} instead of {@code bloodGlucose}), and a raw {@code switch} on the stored
     * string silently skipped every such row. The blood-glucose case previously matched the
     * alias {@code "glucose"} only, so a canonical {@code bloodGlucose} reading never incurred
     * a penalty at all.</p>
     *
     * @param rawKey metric key as stored, possibly a legacy alias
     * @param value  the reading
     * @return penalty points, 0 when the metric is healthy or unrecognized
     */
    private int getMetricPenalty(String rawKey, BigDecimal value) {
        String key = MetricKeyNormalizer.toCanonicalKey(rawKey);
        return switch (key) {
            case "heartRate" -> (value.compareTo(new BigDecimal("60")) < 0 || value.compareTo(new BigDecimal("100")) > 0) ? 10 : 0;
            case "systolicBP" -> (value.compareTo(new BigDecimal("90")) < 0 || value.compareTo(new BigDecimal("140")) > 0) ? 15 : 0;
            case "diastolicBP" -> (value.compareTo(new BigDecimal("60")) < 0 || value.compareTo(new BigDecimal("90")) > 0) ? 15 : 0;
            case "bloodGlucose" -> (value.compareTo(new BigDecimal("3.9")) < 0 || value.compareTo(new BigDecimal("7.0")) > 0) ? 20 : 0;
            case "bmi" -> (value.compareTo(new BigDecimal("18.5")) < 0 || value.compareTo(new BigDecimal("28")) > 0) ? 10 : 0;
            default -> 0;
        };
    }
}