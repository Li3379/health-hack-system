package com.hhs.vo;

import com.hhs.common.enums.MetricCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HealthMetricVO(
        Long id,
        Long userId,
        Long profileId,
        String metricKey,
        String metricName,
        BigDecimal value,
        String unit,
        LocalDate recordDate,
        String trend,
        MetricCategory category,
        LocalDateTime createTime,
        String status,
        String statusText,
        String alertLevel
) {
    public HealthMetricVO(
            Long id,
            Long userId,
            Long profileId,
            String metricKey,
            String metricName,
            BigDecimal value,
            String unit,
            LocalDate recordDate,
            String trend,
            MetricCategory category,
            LocalDateTime createTime
    ) {
        this(id, userId, profileId, metricKey, metricName, value, unit, recordDate, trend, category, createTime, null, null, null);
    }
}
