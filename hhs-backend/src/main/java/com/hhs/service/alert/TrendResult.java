package com.hhs.service.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of trend prediction analysis
 * Contains predicted trend direction, predicted value, and distance to threshold
 */
public record TrendResult(
        /**
         * The direction of the trend (RISING, FALLING, STABLE)
         */
        TrendDirection trend,

        /**
         * The predicted value for the next time point
         */
        Double predictedValue,

        /**
         * The slope of the linear regression (rate of change per day)
         */
        Double slope,

        /**
         * Distance to threshold as a percentage (0.0-1.0)
         * null if threshold is not available
         */
        Double distanceToThreshold,

        /**
         * Whether an early warning should be generated
         */
        Boolean earlyWarningNeeded,

        /**
         * Historical data points used for prediction
         */
        List<MetricDataPoint> historicalData,

        /**
         * Whether sufficient data was available for prediction
         */
        boolean hasSufficientData,

        /**
         * The threshold value used for distance calculation
         */
        BigDecimal thresholdValue,

        /**
         * Message describing the prediction result
         */
        String message
) {
    /**
     * Create a result indicating insufficient data for prediction
     */
    public static TrendResult insufficient(String message) {
        return new TrendResult(
                null,
                null,
                null,
                null,
                false,
                List.of(),
                false,
                null,
                message != null ? message : "数据点不足，无法进行趋势预测"
        );
    }

    /**
     * Create a result for trend without threshold
     */
    public static TrendResult withoutThreshold(
            TrendDirection trend,
            Double predictedValue,
            Double slope,
            List<MetricDataPoint> historicalData) {
        return new TrendResult(
                trend,
                predictedValue,
                slope,
                null,
                false,
                historicalData,
                true,
                null,
                buildTrendMessage(trend, predictedValue)
        );
    }

    /**
     * Create a complete trend result with threshold
     */
    public static TrendResult withThreshold(
            TrendDirection trend,
            Double predictedValue,
            Double slope,
            Double distanceToThreshold,
            Boolean earlyWarningNeeded,
            List<MetricDataPoint> historicalData,
            BigDecimal thresholdValue) {
        return new TrendResult(
                trend,
                predictedValue,
                slope,
                distanceToThreshold,
                earlyWarningNeeded,
                historicalData,
                true,
                thresholdValue,
                buildWarningMessage(trend, predictedValue, distanceToThreshold, earlyWarningNeeded)
        );
    }

    private static String buildTrendMessage(TrendDirection trend, Double predictedValue) {
        if (trend == null || predictedValue == null) {
            return "无法确定趋势";
        }
        return String.format("指标呈%s趋势，预测值: %.1f", trend.getLabel(), predictedValue);
    }

    private static String buildWarningMessage(TrendDirection trend, Double predictedValue,
                                              Double distanceToThreshold, Boolean earlyWarningNeeded) {
        if (earlyWarningNeeded != null && earlyWarningNeeded) {
            return String.format("指标呈%s趋势，已接近预警线(%.0f%%)，建议提前关注",
                    trend != null ? trend.getLabel() : "未知",
                    distanceToThreshold != null ? distanceToThreshold * 100 : 0);
        }
        return buildTrendMessage(trend, predictedValue);
    }

    /**
     * Data point for historical metric values
     */
    public record MetricDataPoint(
            LocalDate date,
            BigDecimal value
    ) {}
}