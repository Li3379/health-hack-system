package com.hhs.service.domain;

import com.hhs.vo.HealthScoreVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure health score calculation logic
 * Responsible for computing health scores based on various health factors
 *
 * Weight distribution (Phase 4 - includes wellness):
 * - Health Profile: 25%
 * - Latest Metrics: 35%
 * - Risk Assessment: 20%
 * - Screening Report: 10%
 * - Wellness: 10%
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private final ProfileScorer profileScorer;
    private final MetricsScorer metricsScorer;
    private final RiskScorer riskScorer;
    private final ScreeningScorer screeningScorer;
    private final WellnessScorer wellnessScorer;

    /**
     * Calculate health score for a user
     * Orchestrates the calculation workflow across all health factors
     *
     * Weight distribution:
     * - Health Profile: 25%
     * - Latest Metrics: 35%
     * - Risk Assessment: 20%
     * - Screening Report: 10%
     * - Wellness: 10%
     *
     * @param userId the user ID
     * @return calculated health score VO
     */
    public HealthScoreVO calculate(Long userId) {
        Map<String, Object> factors = new HashMap<>();
        int totalScore = 100;

        // 1. Health Profile Score (25%)
        int profileScore = profileScorer.calculate(userId);
        factors.put("healthProfile", Map.of(
                "score", profileScore,
                "weight", 0.25,
                "contribution", (int) (profileScore * 0.25)
        ));
        totalScore -= (int) ((100 - profileScore) * 0.25);

        // 2. Latest Metrics Score (35%)
        int metricsScore = metricsScorer.calculate(userId);
        factors.put("latestMetrics", Map.of(
                "score", metricsScore,
                "weight", 0.35,
                "contribution", (int) (metricsScore * 0.35)
        ));
        totalScore -= (int) ((100 - metricsScore) * 0.35);

        // 3. Risk Assessment Score (20%)
        int riskScore = riskScorer.calculate(userId);
        factors.put("riskAssessment", Map.of(
                "score", riskScore,
                "weight", 0.20,
                "contribution", (int) (riskScore * 0.20)
        ));
        totalScore -= (int) ((100 - riskScore) * 0.20);

        // 4. Screening Report Score (10%)
        int screeningScore = screeningScorer.calculate(userId);
        factors.put("screeningReport", Map.of(
                "score", screeningScore,
                "weight", 0.10,
                "contribution", (int) (screeningScore * 0.10)
        ));
        totalScore -= (int) ((100 - screeningScore) * 0.10);

        // 5. Wellness Score (10%) - Phase 4
        int wellnessScore = wellnessScorer.calculate(userId);
        factors.put("wellness", Map.of(
                "score", wellnessScore,
                "weight", 0.10,
                "contribution", (int) (wellnessScore * 0.10)
        ));
        totalScore -= (int) ((100 - wellnessScore) * 0.10);

        // Round to integer
        totalScore = Math.max(0, Math.min(100, totalScore));

        log.debug("Health score calculated for user {}: {} (profile={}, metrics={}, risk={}, screening={}, wellness={})",
                userId, totalScore, profileScore, metricsScore, riskScore, screeningScore, wellnessScore);

        HealthScoreVO vo = new HealthScoreVO();
        vo.setScore(totalScore);
        vo.setLevel(getHealthLevel(totalScore));
        vo.setFactors(factors);
        vo.setCalculatedAt(LocalDateTime.now());

        return vo;
    }

    /**
     * Get health level description from score
     */
    private String getHealthLevel(int score) {
        if (score >= 85) return "EXCELLENT";
        if (score >= 70) return "GOOD";
        if (score >= 50) return "FAIR";
        return "POOR";
    }
}
