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
        LocalDateTime createTime
) {
}
