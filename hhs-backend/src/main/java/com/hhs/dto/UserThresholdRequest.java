package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * User Threshold Request DTO
 * Used for creating and updating user threshold settings
 */
@Data
public class UserThresholdRequest {

    @NotNull(message = "用户标识不能为空")
    private Long userId;

    @NotNull(message = "指标类型不能为空")
    @Size(min = 1, max = 64, message = "指标类型长度需在 1 至 64 个字符之间")
    private String metricKey;

    @DecimalMin(value = "0.0", message = "警告高值不能为负数")
    private BigDecimal warningHigh;

    @DecimalMin(value = "0.0", message = "严重高值不能为负数")
    private BigDecimal criticalHigh;

    @DecimalMin(value = "0.0", message = "警告低值不能为负数")
    private BigDecimal warningLow;

    @DecimalMin(value = "0.0", message = "严重低值不能为负数")
    private BigDecimal criticalLow;
}
