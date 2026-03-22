package com.hhs.service.alert;

import com.hhs.entity.HealthMetric;
import com.hhs.entity.UserThreshold;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.UserThresholdMapper;
import com.hhs.service.alert.TrendResult.MetricDataPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Trend prediction service using linear regression
 * Analyzes health metric trends and predicts future values
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendPredictor {

    private final HealthMetricMapper healthMetricMapper;
    private final UserThresholdMapper userThresholdMapper;

    /**
     * Minimum number of data points required for prediction
     */
    private static final int MIN_DATA_POINTS = 3;

    /**
     * Default number of days to look back for trend analysis
     */
    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    /**
     * Threshold distance percentage for early warning (10%)
     */
    private static final double EARLY_WARNING_THRESHOLD = 0.10;

    /**
     * Predict trend for a specific metric
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @return TrendResult containing prediction details
     */
    public TrendResult predict(Long userId, String metricKey) {
        return predict(userId, metricKey, DEFAULT_LOOKBACK_DAYS);
    }

    /**
     * Predict trend for a specific metric with custom lookback period
     *
     * @param userId       the user ID
     * @param metricKey    the metric key
     * @param lookbackDays number of days to look back
     * @return TrendResult containing prediction details
     */
    public TrendResult predict(Long userId, String metricKey, int lookbackDays) {
        log.debug("Predicting trend for user={}, metric={}, lookback={} days", userId, metricKey, lookbackDays);

        // 1. Fetch recent metric data
        List<HealthMetric> metrics = fetchRecentMetrics(userId, metricKey, lookbackDays);

        if (metrics.size() < MIN_DATA_POINTS) {
            log.info("Insufficient data points ({}) for trend prediction, minimum required: {}",
                    metrics.size(), MIN_DATA_POINTS);
            return TrendResult.insufficient(
                    String.format("需要至少%d个数据点，当前仅有%d个", MIN_DATA_POINTS, metrics.size()));
        }

        // 2. Convert to data points and sort by date
        List<MetricDataPoint> dataPoints = convertToDataPoints(metrics);

        // 3. Perform linear regression
        double[] x = createXValues(dataPoints.size());
        double[] y = createYValues(dataPoints);

        LinearRegressionResult lrResult = performLinearRegression(x, y);

        // 4. Predict next value
        double predictedValue = lrResult.slope() * dataPoints.size() + lrResult.intercept();

        // 5. Determine trend direction
        TrendDirection trend = TrendDirection.fromSlope(lrResult.slope());

        // 6. Get threshold and calculate distance
        BigDecimal threshold = getThresholdValue(userId, metricKey, trend);

        if (threshold == null) {
            log.debug("No threshold configured for metric: {}", metricKey);
            return TrendResult.withoutThreshold(trend, predictedValue, lrResult.slope(), dataPoints);
        }

        // 7. Calculate distance to threshold
        double distanceToThreshold = calculateDistanceToThreshold(predictedValue, threshold, trend);
        boolean earlyWarningNeeded = distanceToThreshold < EARLY_WARNING_THRESHOLD;

        log.info("Trend prediction: metric={}, trend={}, predictedValue={}, distanceToThreshold={}%, earlyWarning={}",
                metricKey, trend, predictedValue, distanceToThreshold * 100, earlyWarningNeeded);

        return TrendResult.withThreshold(
                trend,
                predictedValue,
                lrResult.slope(),
                distanceToThreshold,
                earlyWarningNeeded,
                dataPoints,
                threshold
        );
    }

    /**
     * Fetch recent metrics for a user
     */
    private List<HealthMetric> fetchRecentMetrics(Long userId, String metricKey, int lookbackDays) {
        LocalDateTime since = LocalDateTime.now().minusDays(lookbackDays);

        return healthMetricMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthMetric>()
                        .eq(HealthMetric::getUserId, userId)
                        .eq(HealthMetric::getMetricKey, metricKey)
                        .ge(HealthMetric::getCreateTime, since)
                        .orderByAsc(HealthMetric::getRecordDate)
        );
    }

    /**
     * Convert HealthMetric entities to MetricDataPoints
     */
    private List<MetricDataPoint> convertToDataPoints(List<HealthMetric> metrics) {
        List<MetricDataPoint> dataPoints = new ArrayList<>();
        for (HealthMetric metric : metrics) {
            if (metric.getValue() != null && metric.getRecordDate() != null) {
                dataPoints.add(new MetricDataPoint(metric.getRecordDate(), metric.getValue()));
            }
        }
        // Sort by date ascending
        dataPoints.sort(Comparator.comparing(MetricDataPoint::date));
        return dataPoints;
    }

    /**
     * Create X values (time indices) for regression
     */
    private double[] createXValues(int size) {
        double[] x = new double[size];
        for (int i = 0; i < size; i++) {
            x[i] = i;
        }
        return x;
    }

    /**
     * Create Y values (metric values) for regression
     */
    private double[] createYValues(List<MetricDataPoint> dataPoints) {
        double[] y = new double[dataPoints.size()];
        for (int i = 0; i < dataPoints.size(); i++) {
            y[i] = dataPoints.get(i).value().doubleValue();
        }
        return y;
    }

    /**
     * Perform simple linear regression
     */
    private LinearRegressionResult performLinearRegression(double[] x, double[] y) {
        int n = x.length;

        // Calculate means
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
        }
        double meanX = sumX / n;
        double meanY = sumY / n;

        // Calculate slope and intercept
        double numerator = 0, denominator = 0;
        for (int i = 0; i < n; i++) {
            numerator += (x[i] - meanX) * (y[i] - meanY);
            denominator += (x[i] - meanX) * (x[i] - meanX);
        }

        double slope = denominator != 0 ? numerator / denominator : 0;
        double intercept = meanY - slope * meanX;

        // Calculate R-squared (coefficient of determination)
        double ssTotal = 0, ssResidual = 0;
        for (int i = 0; i < n; i++) {
            double predicted = slope * x[i] + intercept;
            ssTotal += (y[i] - meanY) * (y[i] - meanY);
            ssResidual += (y[i] - predicted) * (y[i] - predicted);
        }
        double rSquared = ssTotal != 0 ? 1 - (ssResidual / ssTotal) : 0;

        log.debug("Linear regression: slope={}, intercept={}, rSquared={}", slope, intercept, rSquared);

        return new LinearRegressionResult(slope, intercept, rSquared);
    }

    /**
     * Get threshold value for early warning calculation
     * Returns the relevant threshold based on trend direction
     */
    private BigDecimal getThresholdValue(Long userId, String metricKey, TrendDirection trend) {
        UserThreshold threshold = userThresholdMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserThreshold>()
                        .eq(UserThreshold::getUserId, userId)
                        .eq(UserThreshold::getMetricKey, metricKey)
        );

        if (threshold == null) {
            return null;
        }

        // For rising trend, check high threshold; for falling, check low threshold
        if (trend == TrendDirection.RISING) {
            return threshold.getWarningHigh() != null
                    ? threshold.getWarningHigh()
                    : threshold.getCriticalHigh();
        } else if (trend == TrendDirection.FALLING) {
            return threshold.getWarningLow() != null
                    ? threshold.getWarningLow()
                    : threshold.getCriticalLow();
        }

        // For stable trend, use high threshold as reference
        return threshold.getWarningHigh();
    }

    /**
     * Calculate distance to threshold as a percentage
     *
     * @param predictedValue the predicted value
     * @param threshold      the threshold value
     * @param trend          the trend direction
     * @return distance as a percentage (0.0-1.0+), where lower means closer to threshold
     */
    private double calculateDistanceToThreshold(double predictedValue, BigDecimal threshold, TrendDirection trend) {
        if (threshold == null) {
            return 1.0; // Maximum distance if no threshold
        }

        double thresholdValue = threshold.doubleValue();

        // Calculate distance based on trend direction
        if (trend == TrendDirection.RISING) {
            // Distance to high threshold: how far below the threshold
            if (predictedValue >= thresholdValue) {
                return 0.0; // Already at or above threshold
            }
            return (thresholdValue - predictedValue) / thresholdValue;
        } else if (trend == TrendDirection.FALLING) {
            // Distance to low threshold: how far above the threshold
            if (predictedValue <= thresholdValue) {
                return 0.0; // Already at or below threshold
            }
            return (predictedValue - thresholdValue) / thresholdValue;
        } else {
            // For stable trend, calculate distance to nearest threshold
            return Math.abs(thresholdValue - predictedValue) / thresholdValue;
        }
    }

    /**
     * Check if early warning should be generated for a metric
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @return true if early warning should be generated
     */
    public boolean shouldGenerateEarlyWarning(Long userId, String metricKey) {
        TrendResult result = predict(userId, metricKey);
        return result.hasSufficientData()
                && Boolean.TRUE.equals(result.earlyWarningNeeded());
    }

    /**
     * Result of linear regression calculation
     */
    private record LinearRegressionResult(
            double slope,
            double intercept,
            double rSquared
    ) {}
}