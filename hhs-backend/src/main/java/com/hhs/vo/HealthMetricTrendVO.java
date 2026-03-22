package com.hhs.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 指标趋势数据，供 ECharts 折线图使用
 */
public record HealthMetricTrendVO(
        String metricKey,
        List<LocalDate> dates,
        List<BigDecimal> values
) {
}
