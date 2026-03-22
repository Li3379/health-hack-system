package com.hhs.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * View Object for metric trend data (charts)
 */
@Data
public class MetricTrendVO {

    private String metricKey;

    private String metricDisplayName;

    private String unit;

    private List<TrendPoint> dataPoints;

    @Data
    public static class TrendPoint {
        private LocalDateTime timestamp;
        private BigDecimal value;
    }
}
