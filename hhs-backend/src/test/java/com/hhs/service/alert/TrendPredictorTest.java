package com.hhs.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.UserThreshold;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.UserThresholdMapper;
import com.hhs.service.alert.TrendResult.MetricDataPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Trend Predictor Service Unit Tests
 *
 * Tests the TrendPredictor service which analyzes health metric trends
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("趋势预测服务测试")
class TrendPredictorTest {

    @Mock
    private HealthMetricMapper healthMetricMapper;

    @Mock
    private UserThresholdMapper userThresholdMapper;

    @InjectMocks
    private TrendPredictor trendPredictor;

    private Long testUserId = 1L;
    private String testMetricKey = "heartRate";
    private UserThreshold testThreshold;

    @BeforeEach
    void setUp() {
        testThreshold = new UserThreshold();
        testThreshold.setUserId(testUserId);
        testThreshold.setMetricKey(testMetricKey);
        testThreshold.setWarningHigh(new BigDecimal("100"));
        testThreshold.setWarningLow(new BigDecimal("50"));
    }

    @Test
    @DisplayName("测试1.1：趋势预测 - 数据点不足")
    void testPredict_InsufficientData() {
        // Given: Only 2 data points (less than minimum 3)
        List<HealthMetric> metrics = createMetrics(2, 70.0, 1.0);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should return insufficient result
        assertFalse(result.hasSufficientData());
        assertNull(result.trend());
        assertNull(result.predictedValue());
        assertTrue(result.message().contains("数据点"));
    }

    @Test
    @DisplayName("测试1.2：趋势预测 - 无数据")
    void testPredict_NoData() {
        // Given: No data points
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should return insufficient result
        assertFalse(result.hasSufficientData());
        assertNull(result.trend());
    }

    @Test
    @DisplayName("测试1.3：趋势预测 - 上升趋势（无阈值）")
    void testPredict_RisingTrend_NoThreshold() {
        // Given: Rising trend data points
        List<HealthMetric> metrics = createMetrics(7, 70.0, 3.0); // Each day +3
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should detect rising trend
        assertTrue(result.hasSufficientData());
        assertEquals(TrendDirection.RISING, result.trend());
        assertNotNull(result.predictedValue());
        assertNull(result.distanceToThreshold()); // No threshold configured
        assertFalse(result.earlyWarningNeeded());
        assertEquals(7, result.historicalData().size());
    }

    @Test
    @DisplayName("测试1.4：趋势预测 - 下降趋势（无阈值）")
    void testPredict_FallingTrend_NoThreshold() {
        // Given: Falling trend data points
        List<HealthMetric> metrics = createMetrics(7, 100.0, -3.0); // Each day -3
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should detect falling trend
        assertTrue(result.hasSufficientData());
        assertEquals(TrendDirection.FALLING, result.trend());
        assertNotNull(result.predictedValue());
        assertFalse(result.earlyWarningNeeded());
    }

    @Test
    @DisplayName("测试1.5：趋势预测 - 稳定趋势")
    void testPredict_StableTrend() {
        // Given: Stable data points (minimal variation)
        List<HealthMetric> metrics = createMetrics(7, 75.0, 0.001); // Very small change
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should detect stable trend
        assertTrue(result.hasSufficientData());
        assertEquals(TrendDirection.STABLE, result.trend());
    }

    @Test
    @DisplayName("测试1.6：趋势预测 - 上升趋势接近阈值（需要早期预警）")
    void testPredict_RisingTrend_NearThreshold() {
        // Given: Rising trend approaching high threshold
        // Start at 90, threshold is 100, slope +3/day
        List<HealthMetric> metrics = createMetrics(7, 90.0, 3.0);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should detect rising trend near threshold
        assertTrue(result.hasSufficientData());
        assertEquals(TrendDirection.RISING, result.trend());
        assertNotNull(result.distanceToThreshold());
        // Predicted value should be around 90 + 6*3 = 108 (at day 7, next prediction)
        assertTrue(result.predictedValue() > 100); // Above threshold
        assertTrue(result.distanceToThreshold() < 0.1); // Close to threshold
    }

    @Test
    @DisplayName("测试1.7：趋势预测 - 下降趋势接近阈值（需要早期预警）")
    void testPredict_FallingTrend_NearThreshold() {
        // Given: Falling trend approaching low threshold
        // Start at 60, threshold is 50, slope -3/day
        List<HealthMetric> metrics = createMetrics(7, 60.0, -3.0);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should detect falling trend
        assertTrue(result.hasSufficientData());
        assertEquals(TrendDirection.FALLING, result.trend());
        assertNotNull(result.distanceToThreshold());
    }

    @Test
    @DisplayName("测试1.8：趋势预测 - 远离阈值（不需要早期预警）")
    void testPredict_FarFromThreshold() {
        // Given: Stable trend far from threshold
        // Start at 75, threshold is 100, very small slope
        List<HealthMetric> metrics = createMetrics(7, 75.0, 0.5);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should not trigger early warning
        assertTrue(result.hasSufficientData());
        assertNotNull(result.distanceToThreshold());
        assertTrue(result.distanceToThreshold() > 0.1); // Far from threshold
    }

    @Test
    @DisplayName("测试1.9：趋势预测 - 自定义回溯天数")
    void testPredict_CustomLookbackDays() {
        // Given: 14 days of data
        List<HealthMetric> metrics = createMetrics(14, 80.0, 1.0);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: Predict with 14-day lookback
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey, 14);

        // Then: Should use all data points
        assertTrue(result.hasSufficientData());
        assertEquals(14, result.historicalData().size());
    }

    @Test
    @DisplayName("测试1.10：检查是否需要早期预警 - 需要")
    void testShouldGenerateEarlyWarning_Needed() {
        // Given: Trend approaching threshold
        List<HealthMetric> metrics = createMetrics(7, 95.0, 2.0); // Rising toward 100 threshold
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When: Check early warning
        boolean needed = trendPredictor.shouldGenerateEarlyWarning(testUserId, testMetricKey);

        // Then: Should return true
        assertTrue(needed);
    }

    @Test
    @DisplayName("测试1.11：检查是否需要早期预警 - 不需要")
    void testShouldGenerateEarlyWarning_NotNeeded() {
        // Given: Stable trend far from threshold
        List<HealthMetric> metrics = createMetrics(7, 70.0, 0.1);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When: Check early warning
        boolean needed = trendPredictor.shouldGenerateEarlyWarning(testUserId, testMetricKey);

        // Then: Should return false
        assertFalse(needed);
    }

    @Test
    @DisplayName("测试1.12：检查是否需要早期预警 - 数据不足")
    void testShouldGenerateEarlyWarning_InsufficientData() {
        // Given: Only 2 data points
        List<HealthMetric> metrics = createMetrics(2, 70.0, 1.0);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);

        // When: Check early warning
        boolean needed = trendPredictor.shouldGenerateEarlyWarning(testUserId, testMetricKey);

        // Then: Should return false
        assertFalse(needed);
    }

    @Test
    @DisplayName("测试1.13：趋势结果包含历史数据")
    void testPredict_ContainsHistoricalData() {
        // Given: Multiple data points
        List<HealthMetric> metrics = createMetrics(5, 75.0, 1.0);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Historical data should be included
        assertTrue(result.hasSufficientData());
        List<MetricDataPoint> historicalData = result.historicalData();
        assertEquals(5, historicalData.size());

        // Verify data is sorted by date ascending
        for (int i = 1; i < historicalData.size(); i++) {
            assertTrue(historicalData.get(i).date().isAfter(historicalData.get(i - 1).date()));
        }
    }

    @Test
    @DisplayName("测试1.14：趋势预测 - 忽略空值数据点")
    void testPredict_SkipsNullValues() {
        // Given: Some metrics with null values
        List<HealthMetric> metrics = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            HealthMetric metric = new HealthMetric();
            metric.setId((long) i);
            metric.setUserId(testUserId);
            metric.setMetricKey(testMetricKey);
            metric.setRecordDate(LocalDate.now().minusDays(5 - i));
            if (i % 2 == 0) {
                metric.setValue(new BigDecimal(75 + i));
            } else {
                metric.setValue(null); // Null value should be skipped
            }
            metric.setCreateTime(LocalDateTime.now());
            metrics.add(metric);
        }
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: Predict trend
        TrendResult result = trendPredictor.predict(testUserId, testMetricKey);

        // Then: Should only use non-null values
        // 3 valid points (0, 2, 4) - enough for prediction
        assertTrue(result.hasSufficientData());
        assertEquals(3, result.historicalData().size());
    }

    /**
     * Helper method to create test metrics with specified trend
     *
     * @param count     Number of metrics to create
     * @param startValue Starting value
     * @param slope     Value change per day
     * @return List of HealthMetric entities
     */
    private List<HealthMetric> createMetrics(int count, double startValue, double slope) {
        List<HealthMetric> metrics = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            HealthMetric metric = new HealthMetric();
            metric.setId((long) i);
            metric.setUserId(testUserId);
            metric.setMetricKey(testMetricKey);
            metric.setValue(new BigDecimal(startValue + (slope * i)));
            metric.setRecordDate(LocalDate.now().minusDays(count - 1 - i));
            // Set createTime within lookback period (last 7 days)
            metric.setCreateTime(now.minusDays(count - 1 - i));
            metrics.add(metric);
        }
        return metrics;
    }
}