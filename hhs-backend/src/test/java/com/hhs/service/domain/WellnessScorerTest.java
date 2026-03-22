package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.enums.MetricCategory;
import com.hhs.entity.HealthMetric;
import com.hhs.mapper.HealthMetricMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WellnessScorer
 */
@ExtendWith(MockitoExtension.class)
class WellnessScorerTest {

    @Mock
    private HealthMetricMapper healthMetricMapper;

    @InjectMocks
    private WellnessScorer wellnessScorer;

    private Long testUserId;
    private List<HealthMetric> testMetrics;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testMetrics = new ArrayList<>();
    }

    @Test
    @DisplayName("Should return default score 70 when no wellness data exists")
    void calculate_NoData_ReturnsDefaultScore() {
        // Given
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then
        assertEquals(70, score);
        verify(healthMetricMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("Should return 100 when all metrics are in ideal range")
    void calculate_AllIdealMetrics_Returns100() {
        // Given
        testMetrics.add(createMetric("sleepDuration", new BigDecimal("8"))); // 7-9 ideal
        testMetrics.add(createMetric("sleepQuality", new BigDecimal("4"))); // 3-5 ideal
        testMetrics.add(createMetric("steps", new BigDecimal("10000"))); // 8000+ ideal
        testMetrics.add(createMetric("exerciseMinutes", new BigDecimal("45"))); // 30+ ideal
        testMetrics.add(createMetric("waterIntake", new BigDecimal("8"))); // 8+ ideal
        testMetrics.add(createMetric("mood", new BigDecimal("4"))); // 3-5 ideal
        testMetrics.add(createMetric("energy", new BigDecimal("4"))); // 3-5 ideal

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then
        assertEquals(100, score);
    }

    @Test
    @DisplayName("Should apply penalty for low sleep duration")
    void calculate_LowSleepDuration_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("sleepDuration", new BigDecimal("5"))); // Below 7

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 10 = 90, blended with 70: (90 + 70) / 2 = 80
        assertEquals(80, score);
    }

    @Test
    @DisplayName("Should apply penalty for excessive sleep duration")
    void calculate_ExcessiveSleepDuration_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("sleepDuration", new BigDecimal("10"))); // Above 9

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 5 = 95, blended with 70: (95 + 70) / 2 = 82
        assertEquals(82, score);
    }

    @Test
    @DisplayName("Should apply penalty for low sleep quality")
    void calculate_LowSleepQuality_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("sleepQuality", new BigDecimal("2"))); // Below 3

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 10 = 90, blended with 70: (90 + 70) / 2 = 80
        assertEquals(80, score);
    }

    @Test
    @DisplayName("Should apply penalty for low steps")
    void calculate_LowSteps_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("steps", new BigDecimal("5000"))); // Below 8000

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 10 = 90, blended with 70: (90 + 70) / 2 = 80
        assertEquals(80, score);
    }

    @Test
    @DisplayName("Should apply penalty for low exercise minutes")
    void calculate_LowExercise_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("exerciseMinutes", new BigDecimal("15"))); // Below 30

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 10 = 90, blended with 70: (90 + 70) / 2 = 80
        assertEquals(80, score);
    }

    @Test
    @DisplayName("Should apply penalty for low water intake")
    void calculate_LowWaterIntake_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("waterIntake", new BigDecimal("5"))); // Below 8

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 5 = 95, blended with 70: (95 + 70) / 2 = 82
        assertEquals(82, score);
    }

    @Test
    @DisplayName("Should apply penalty for low mood")
    void calculate_LowMood_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("mood", new BigDecimal("2"))); // Below 3

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 5 = 95, blended with 70: (95 + 70) / 2 = 82
        assertEquals(82, score);
    }

    @Test
    @DisplayName("Should apply penalty for low energy")
    void calculate_LowEnergy_AppliesPenalty() {
        // Given
        testMetrics.add(createMetric("energy", new BigDecimal("1"))); // Below 3

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 5 = 95, blended with 70: (95 + 70) / 2 = 82
        assertEquals(82, score);
    }

    @Test
    @DisplayName("Should use latest value when multiple entries exist for same metric")
    void calculate_MultipleEntriesForSameMetric_UsesLatest() {
        // Given
        HealthMetric oldMetric = createMetric("steps", new BigDecimal("5000"));
        oldMetric.setRecordDate(LocalDate.now().minusDays(2));

        HealthMetric newMetric = createMetric("steps", new BigDecimal("10000"));
        newMetric.setRecordDate(LocalDate.now());

        testMetrics.add(oldMetric);
        testMetrics.add(newMetric);

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then - uses latest value (10000 steps, no penalty), blended with 70: (100 + 70) / 2 = 85
        assertEquals(85, score);
    }

    @Test
    @DisplayName("Should not blend with default when 3+ metrics tracked")
    void calculate_ThreeOrMoreMetrics_NoBlending() {
        // Given
        testMetrics.add(createMetric("sleepDuration", new BigDecimal("8"))); // Ideal
        testMetrics.add(createMetric("steps", new BigDecimal("10000"))); // Ideal
        testMetrics.add(createMetric("mood", new BigDecimal("2"))); // Low - penalty

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 5 = 95, no blending since 3+ metrics
        assertEquals(95, score);
    }

    @Test
    @DisplayName("Should accumulate multiple penalties")
    void calculate_MultiplePenalties_Accumulates() {
        // Given
        testMetrics.add(createMetric("sleepDuration", new BigDecimal("5"))); // -10 penalty
        testMetrics.add(createMetric("steps", new BigDecimal("3000"))); // -10 penalty
        testMetrics.add(createMetric("mood", new BigDecimal("2"))); // -5 penalty

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 10 - 10 - 5 = 75, no blending since 3+ metrics
        assertEquals(75, score);
    }

    @Test
    @DisplayName("Should return minimum score 0 for severe penalties")
    void calculate_SeverePenalties_ReturnsMinimum0() {
        // Given - Create scenario that would go below 0
        testMetrics.add(createMetric("sleepDuration", new BigDecimal("4"))); // -10
        testMetrics.add(createMetric("sleepQuality", new BigDecimal("1"))); // -10
        testMetrics.add(createMetric("steps", new BigDecimal("1000"))); // -10
        testMetrics.add(createMetric("exerciseMinutes", new BigDecimal("0"))); // -10
        testMetrics.add(createMetric("waterIntake", new BigDecimal("2"))); // -5
        testMetrics.add(createMetric("mood", new BigDecimal("1"))); // -5
        testMetrics.add(createMetric("energy", new BigDecimal("1"))); // -5
        // Total penalty: 55, score = 45

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then: 100 - 55 = 45
        assertEquals(45, score);
    }

    @Test
    @DisplayName("Should return maximum score 100 even if calculation exceeds")
    void calculate_ScoreExceeds100_CappedAt100() {
        // Given - All ideal values
        testMetrics.add(createMetric("sleepDuration", new BigDecimal("8")));
        testMetrics.add(createMetric("sleepQuality", new BigDecimal("5")));
        testMetrics.add(createMetric("steps", new BigDecimal("15000")));
        testMetrics.add(createMetric("exerciseMinutes", new BigDecimal("60")));
        testMetrics.add(createMetric("waterIntake", new BigDecimal("10")));
        testMetrics.add(createMetric("mood", new BigDecimal("5")));
        testMetrics.add(createMetric("energy", new BigDecimal("5")));

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(testMetrics);

        // When
        int score = wellnessScorer.calculate(testUserId);

        // Then
        assertEquals(100, score); // Capped at 100
    }

    // Helper method to create test metrics
    private HealthMetric createMetric(String key, BigDecimal value) {
        HealthMetric metric = new HealthMetric();
        metric.setId((long) (Math.random() * 10000));
        metric.setUserId(testUserId);
        metric.setMetricKey(key);
        metric.setValue(value);
        metric.setRecordDate(LocalDate.now());
        metric.setCategory(MetricCategory.WELLNESS);
        return metric;
    }
}