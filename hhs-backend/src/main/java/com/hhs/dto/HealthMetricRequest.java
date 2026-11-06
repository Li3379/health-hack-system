package com.hhs.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.hhs.service.domain.MetricKeyNormalizer;
import com.hhs.service.domain.MetricValueProvider;
import com.hhs.validation.ValidMetricRecord;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Health Metric Request DTO
 * Used for creating and updating health metrics
 */
@Data
@ValidMetricRecord
public class HealthMetricRequest implements MetricValueProvider {

    // userId is obtained from SecurityUtils.getCurrentUserId(), not from request body
    private Long userId;

    @NotNull(message = "指标类型不能为空")
    @Size(min = 1, max = 32, message = "指标类型长度需在 1 至 32 个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "指标类型只能包含字母、数字、连字符与下划线")
    private String metricKey;

    @NotNull(message = "指标数值不能为空")
    @DecimalMin(value = "0.0", message = "指标数值不能为负数")
    private BigDecimal value;

    @NotNull(message = "记录日期不能为空")
    @PastOrPresent(message = "记录日期不能晚于今天")
    private LocalDate recordDate;

    /**
     * Precise timestamp for the measurement.
     * Used for conflict resolution when multiple records have the same recordDate.
     * If null, recordDate at start of day is assumed.
     */
    private LocalDateTime recordTime;

    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    @Size(max = 16, message = "趋势长度不能超过 16 个字符")
    @Pattern(regexp = "^(up|down|stable|normal)?$", message = "趋势只能是上升、下降、平稳、正常或留空")
    private String trend;

    /**
     * Set the metric key, automatically normalizing aliases and snake_case to canonical camelCase.
     *
     * @param metricKey raw or alias metric key
     */
    @JsonSetter
    public void setMetricKey(String metricKey) {
        this.metricKey = MetricKeyNormalizer.toCanonicalKey(metricKey);
    }
}
