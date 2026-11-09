package com.hhs.service.domain;

import com.hhs.entity.RealtimeMetric;
import com.hhs.mapper.RealtimeMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Calculates dimension-level health scores for score history persistence.
 * <p>
 * Derives four dimension scores from realtime metrics:
 * <ul>
 *   <li>cardiovascularScore — heart rate, systolic/diastolic blood pressure</li>
 *   <li>metabolicScore — blood glucose</li>
 *   <li>weightScore — BMI</li>
 *   <li>lifestyleScore — delegates to WellnessScorer</li>
 * </ul>
 * <p>
 * Uses the same penalty thresholds as {@link MetricsScorer} for consistency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DimensionScoreCalculator {

    private final RealtimeMetricMapper realtimeMetricMapper;
    private final WellnessScorer wellnessScorer;

    /**
     * Calculate dimension scores for a user.
     *
     * @param userId the user ID
     * @return map of dimension name to score (0-100)
     */
    public Map<String, Integer> calculate(Long userId) {
        return calculate(userId, AssumptionCollector.noop());
    }

    /**
     * Calculate dimension scores for a user, disclosing the missing-BMI substitution.
     *
     * <p>The weight dimension reads the realtime {@code bmi} metric, not the health profile.
     * When the metric set is non-empty but carries no {@code bmi} key, the weight penalty stays
     * zero and the dimension silently reports a full score, so that absence is disclosed here.
     * An entirely empty metric set is already reported by {@link MetricsScorer}, and emitting
     * both sentences would double-report the same absence.</p>
     *
     * @param userId    the user ID
     * @param collector receives the substitution sentence, ignored when {@code null}
     * @return map of dimension name to score (0-100)
     */
    public Map<String, Integer> calculate(Long userId, AssumptionCollector collector) {
        List<RealtimeMetric> metrics = realtimeMetricMapper.getLatestMetricsByUser(userId);

        int cardiovascularPenalty = 0;
        int metabolicPenalty = 0;
        int weightPenalty = 0;
        boolean sawBmi = false;

        for (RealtimeMetric metric : metrics) {
            String key = metric.getMetricKey();
            BigDecimal value = metric.getValue();

            switch (key) {
                case "heartRate" -> cardiovascularPenalty += getPenalty(value, 60, 100, 10);
                case "systolicBP" -> cardiovascularPenalty += getPenalty(value, 90, 140, 15);
                case "diastolicBP" -> cardiovascularPenalty += getPenalty(value, 60, 90, 15);
                case "glucose" -> metabolicPenalty += getPenalty(value, 3.9, 7.0, 20);
                case "bmi" -> {
                    weightPenalty += getPenalty(value, 18.5, 28, 10);
                    sawBmi = true;
                }
                default -> { /* not mapped to any dimension */ }
            }
        }

        if (!metrics.isEmpty() && !sawBmi && collector != null) {
            collector.add("缺少 BMI 指标，体重维度按参考值估算");
        }

        int cardiovascularScore = clamp(100 - cardiovascularPenalty);
        int metabolicScore = clamp(100 - metabolicPenalty);
        int weightScore = clamp(100 - weightPenalty);
        int lifestyleScore = wellnessScorer.calculate(userId);

        log.debug("Dimension scores for user {}: cardiovascular={}, metabolic={}, weight={}, lifestyle={}",
                userId, cardiovascularScore, metabolicScore, weightScore, lifestyleScore);

        return Map.of(
                "cardiovascular", cardiovascularScore,
                "metabolic", metabolicScore,
                "weight", weightScore,
                "lifestyle", lifestyleScore
        );
    }

    /**
     * Calculate penalty if value is outside [low, high] range.
     *
     * @param value     the metric value
     * @param low       lower bound (inclusive)
     * @param high      upper bound (inclusive)
     * @param penalty   penalty points if out of range
     * @return penalty points (0 if in range)
     */
    private int getPenalty(BigDecimal value, double low, double high, int penalty) {
        if (value == null) {
            return 0;
        }
        if (value.compareTo(BigDecimal.valueOf(low)) < 0
                || value.compareTo(BigDecimal.valueOf(high)) > 0) {
            return penalty;
        }
        return 0;
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}