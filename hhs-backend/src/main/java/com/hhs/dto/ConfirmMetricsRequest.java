package com.hhs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 确认AI解析指标请求DTO
 */
@Data
@Schema(description = "确认AI解析指标请求")
public class ConfirmMetricsRequest {

    /**
     * 解析历史ID
     */
    @NotNull(message = "解析历史ID不能为空")
    @Schema(description = "解析历史ID", required = true)
    private Long parseHistoryId;

    /**
     * 待确认的指标列表
     */
    @NotEmpty(message = "指标列表不能为空")
    @Valid
    @Schema(description = "待确认的指标列表", required = true)
    private List<MetricItem> metrics;

    /**
     * 单个指标项
     */
    @Data
    @Schema(description = "指标项")
    public static class MetricItem {

        @NotNull(message = "指标Key不能为空")
        @Schema(description = "指标Key", example = "glucose", required = true)
        private String metricKey;

        @Schema(description = "指标名称", example = "血糖")
        private String metricName;

        @NotNull(message = "指标值不能为空")
        @Schema(description = "指标值", example = "5.6", required = true)
        private BigDecimal value;

        @Schema(description = "单位", example = "mmol/L")
        private String unit;

        @Schema(description = "类别", example = "HEALTH")
        private String category;

        @Schema(description = "记录日期", example = "2026-03-17")
        private String recordDate;

        @Schema(description = "置信度", example = "0.95")
        private BigDecimal confidence;

        @Schema(description = "是否选中", example = "true")
        private Boolean selected = true;
    }
}