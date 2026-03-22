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
        List<RealtimeMetric> metrics = realtimeMetricMapper.getLatestMetricsByUser(userId);
        if (metrics.isEmpty()) {
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
     * Get penalty for metric value outside normal range
     */
    private int getMetricPenalty(String key, BigDecimal value) {
        return switch (key) {
            case "heartRate" -> (value.compareTo(new BigDecimal("60")) < 0 || value.compareTo(new BigDecimal("100")) > 0) ? 10 : 0;
            case "systolicBP" -> (value.compareTo(new BigDecimal("90")) < 0 || value.compareTo(new BigDecimal("140")) > 0) ? 15 : 0;
            case "diastolicBP" -> (value.compareTo(new BigDecimal("60")) < 0 || value.compareTo(new BigDecimal("90")) > 0) ? 15 : 0;
            case "glucose" -> (value.compareTo(new BigDecimal("3.9")) < 0 || value.compareTo(new BigDecimal("7.0")) > 0) ? 20 : 0;
            case "bmi" -> (value.compareTo(new BigDecimal("18.5")) < 0 || value.compareTo(new BigDecimal("28")) > 0) ? 10 : 0;
            default -> 0;
        };
    }
}
