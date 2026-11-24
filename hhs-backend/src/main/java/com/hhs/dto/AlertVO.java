package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * View Object for health alerts
 * Used for alert creation and updates
 */
@Data
public class AlertVO {

    private Long id;

    /**
     * Alert type: CRITICAL, WARNING, NORMAL
     */
    @NotBlank(message = "提醒类型不能为空")
    @Size(min = 1, max = 50, message = "提醒类型长度需在 1 至 50 个字符之间")
    @Pattern(regexp = "^(CRITICAL|WARNING|NORMAL)$", message = "提醒类型只能是危急、警告或提示")
    private String alertType;

    /**
     * Alert level: HIGH, MEDIUM, LOW
     */
    @NotBlank(message = "提醒级别不能为空")
    @Size(min = 1, max = 16, message = "提醒级别长度需在 1 至 16 个字符之间")
    @Pattern(regexp = "^(HIGH|MEDIUM|LOW)$", message = "提醒级别只能是高、中、低")
    private String alertLevel;

    @NotBlank(message = "标题不能为空")
    @Size(min = 1, max = 200, message = "标题长度需在 1 至 200 个字符之间")
    private String title;

    @NotBlank(message = "提醒内容不能为空")
    @Size(min = 1, max = 500, message = "提醒内容长度需在 1 至 500 个字符之间")
    private String message;

    @Size(max = 32, message = "指标类型长度不能超过 32 个字符")
    private String metricKey;

    @DecimalMin(value = "0.0", message = "当前值不能为负数")
    @DecimalMax(value = "1000.0", message = "当前值不能超过 1000")
    private BigDecimal currentValue;

    @DecimalMin(value = "0.0", message = "阈值不能为负数")
    @DecimalMax(value = "1000.0", message = "阈值不能超过 1000")
    private BigDecimal thresholdValue;

    /**
     * Occurrence count for merged alerts
     */
    private Integer occurrenceCount;

    /**
     * Last occurrence time for merged alerts
     */
    private LocalDateTime lastOccurrenceAt;

    /**
     * AI analysis result
     */
    private String aiAnalysis;

    /**
     * Health suggestion
     */
    private String suggestion;

    private Boolean isRead;

    private Boolean isAcknowledged;

    private LocalDateTime acknowledgedAt;

    private LocalDateTime createdAt;
}
