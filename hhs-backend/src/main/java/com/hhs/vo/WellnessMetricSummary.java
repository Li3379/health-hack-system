package com.hhs.vo;

import java.math.BigDecimal;

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