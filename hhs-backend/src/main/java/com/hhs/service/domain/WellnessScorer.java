package com.hhs.service.domain;

import com.hhs.entity.HealthMetric;
import com.hhs.mapper.HealthMetricMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.enums.MetricCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Wellness Scorer - handles wellness metrics scoring
 * Uses penalty-based scoring system with ideal ranges for each metric
 *
 * Wellness metrics:
 * - sleepDuration: 7-9 hours ideal
 * - sleepQuality: 3-5 ideal (1-5 scale)
 * - steps: 8000+ ideal
 * - exerciseMinutes: 30+ ideal
 * - waterIntake: 8+ glasses ideal
 * - mood: 3-5 ideal (1-5 scale)
 * - energy: 3-5 ideal (1-5 scale)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WellnessScorer {

    private final HealthMetricMapper healthMetricMapper;

    private static final int DEFAULT_SCORE = 70;

    /**
     * Calculate wellness score for a user
     * Uses penalty-based scoring starting from 100
     *
     * @param userId the user ID
     * @return score from 0-100, or 70 if no wellness data
     */
    public int calculate(Long userId) {
        // Get latest wellness metrics from the last 7 days
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);

        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthMetric::getUserId, userId);
        wrapper.eq(HealthMetric::getCategory, MetricCategory.WELLNESS);
        wrapper.ge(HealthMetric::getRecordDate, startDate);
        wrapper.le(HealthMetric::getRecordDate, endDate);
        wrapper.orderByDesc(HealthMetric::getRecordDate);

        List<HealthMetric> metrics = healthMetricMapper.selectList(wrapper);

        if (metrics.isEmpty()) {
            log.debug("No wellness data for user {}, returning default score {}", userId, DEFAULT_SCORE);
            return DEFAULT_SCORE;
        }

        // Get latest value for each metric type
        Map<String, BigDecimal> latestValues = getLatestValues(metrics);

        if (latestValues.isEmpty()) {
            return DEFAULT_SCORE;
        }

        int score = 100;
        int metricsCount = 0;

        // Apply penalties based on ideal ranges
        for (Map.Entry<String, BigDecimal> entry : latestValues.entrySet()) {
            String key = entry.getKey();
            BigDecimal value = entry.getValue();
            int penalty = calculatePenalty(key, value);

            if (penalty > 0) {
                log.debug("Wellness metric {} penalty: -{} (value: {})", key, penalty, value);
            }

            score -= penalty;
            metricsCount++;
        }

        // Adjust score based on how many metrics are being tracked
        // More metrics = more accurate score
        if (metricsCount < 3) {
            // If tracking fewer than 3 metrics, blend with default score
            score = (score + DEFAULT_SCORE) / 2;
        }

        int finalScore = Math.max(0, Math.min(100, score));
        log.debug("Wellness score for user {}: {} (from {} metrics)", userId, finalScore, metricsCount);

        return finalScore;
    }

    /**
     * Get latest value for each wellness metric type
     */
    private Map<String, BigDecimal> getLatestValues(List<HealthMetric> metrics) {
        // Group by metric key and get the most recent value
        return metrics.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        HealthMetric::getMetricKey,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                list -> list.stream()
                                        .max((a, b) -> a.getRecordDate().compareTo(b.getRecordDate()))
                                        .map(HealthMetric::getValue)
                                        .orElse(BigDecimal.ZERO)
                        )
                ));
    }

    /**
     * Calculate penalty for a metric value outside ideal range
     * Returns 0 if within ideal range, penalty points otherwise
     */
    private int calculatePenalty(String key, BigDecimal value) {
        if (value == null) {
            return 0;
        }

        return switch (key) {
            case "sleepDuration" -> {
                // Ideal: 7-9 hours
                // Penalty if < 7 or > 9
                if (value.compareTo(new BigDecimal("7")) < 0) {
                    yield 10; // Too little sleep
                } else if (value.compareTo(new BigDecimal("9")) > 0) {
                    yield 5; // Too much sleep (less penalty)
                }
                yield 0;
            }
            case "sleepQuality" -> {
                // Ideal: 3-5 (on 1-5 scale)
                // Penalty if < 3
                if (value.compareTo(new BigDecimal("3")) < 0) {
                    yield 10;
                }
                yield 0;
            }
            case "steps" -> {
                // Ideal: 8000+ steps
                // Penalty if < 8000
                if (value.compareTo(new BigDecimal("8000")) < 0) {
                    yield 10;
                }
                yield 0;
            }
            case "exerciseMinutes" -> {
                // Ideal: 30+ minutes
                // Penalty if < 30
                if (value.compareTo(new BigDecimal("30")) < 0) {
                    yield 10;
                }
                yield 0;
            }
            case "waterIntake" -> {
                // Ideal: 8+ glasses
                // Penalty if < 8
                if (value.compareTo(new BigDecimal("8")) < 0) {
                    yield 5;
                }
                yield 0;
            }
            case "mood" -> {
                // Ideal: 3-5 (on 1-5 scale)
                // Penalty if < 3
                if (value.compareTo(new BigDecimal("3")) < 0) {
                    yield 5;
                }
                yield 0;
            }
            case "energy" -> {
                // Ideal: 3-5 (on 1-5 scale)
                // Penalty if < 3
                if (value.compareTo(new BigDecimal("3")) < 0) {
                    yield 5;
                }
                yield 0;
            }
            default -> 0;
        };
    }
}