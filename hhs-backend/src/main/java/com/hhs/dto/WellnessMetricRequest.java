package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Wellness Metric Request DTO
 * Used for creating and updating wellness metrics with wellness-specific validation
 */
@Data
public class WellnessMetricRequest {

    @NotNull(message = "指标类型不能为空")
    @Pattern(regexp = "^(sleepDuration|sleepQuality|steps|exerciseMinutes|waterIntake|mood|energy)$",
             message = "无效的保健指标类型")
    private String metricKey;

    @NotNull(message = "数值不能为空")
    @DecimalMin(value = "0.0", message = "数值必须为非负数")
    private BigDecimal value;

    @NotNull(message = "记录日期不能为空")
    @PastOrPresent(message = "记录日期不能是未来日期")
    private LocalDate recordDate;

    @Size(max = 32, message = "单位不能超过32个字符")
    private String unit;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String notes;
}