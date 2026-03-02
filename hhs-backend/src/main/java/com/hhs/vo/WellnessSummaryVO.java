package com.hhs.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Wellness Summary VO
 * Aggregated wellness metrics for dashboard display
 */
public record WellnessSummaryVO(
    LocalDate summaryDate,
    BigDecimal avgSleepDuration,
    Integer avgSleepQuality,
    Long totalSteps,
    Integer totalExerciseMinutes,
    Integer totalWaterIntake,
    Integer avgMood,
    Integer avgEnergy,
    List<WellnessMetricSummary> metrics
) {}

/**
 * Individual wellness metric summary for the dashboard
 */
public record WellnessMetricSummary(
    String metricKey,
    String displayName,
    BigDecimal latestValue,
    String unit,
    BigDecimal avgValue,
    BigDecimal trend
) {}