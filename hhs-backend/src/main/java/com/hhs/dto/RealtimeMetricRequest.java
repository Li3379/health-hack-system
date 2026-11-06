package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating a realtime metric
 */
@Data
public class RealtimeMetricRequest {

    @NotBlank(message = "指标类型不能为空")
    @Size(min = 1, max = 32, message = "指标类型长度需在 1 至 32 个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "指标类型只能包含字母、数字、连字符与下划线")
    private String metricKey;

    /**
     * Alias for metricKey - for frontend compatibility
     */
    private String metricType;

    @NotNull(message = "指标数值不能为空")
    @DecimalMin(value = "0.0", message = "指标数值不能为负数")
    @DecimalMax(value = "1000.0", message = "指标数值不能超过 1000")
    private BigDecimal value;

    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    /**
     * Data source: manual, device, api
     * Default is manual
     */
    @Size(max = 32, message = "数据来源长度不能超过 32 个字符")
    @Pattern(regexp = "^(manual|device|api)?$", message = "数据来源只能是手动、设备、API 或留空")
    private String source = "manual";

    /**
     * Quality score for device-entered metrics (0-1)
     */
    @DecimalMin(value = "0.0", message = "数据质量评分不能小于 0")
    @DecimalMax(value = "1.0", message = "数据质量评分不能大于 1")
    private BigDecimal qualityScore;
}
