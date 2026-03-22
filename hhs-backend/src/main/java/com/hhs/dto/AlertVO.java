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
     * Alert type: CRITICAL, WARNING, INFO, TREND, RECOVERY
     */
    @NotBlank(message = "Alert type is required")
    @Size(min = 1, max = 50, message = "Alert type must be between 1 and 50 characters")
    @Pattern(regexp = "^(CRITICAL|WARNING|INFO|TREND|RECOVERY)$", message = "Alert type must be CRITICAL, WARNING, INFO, TREND, or RECOVERY")
    private String alertType;

    /**
     * Alert level: HIGH, MEDIUM, LOW
     */
    @NotBlank(message = "Alert level is required")
    @Size(min = 1, max = 16, message = "Alert level must be between 1 and 16 characters")
    @Pattern(regexp = "^(HIGH|MEDIUM|LOW)$", message = "Alert level must be HIGH, MEDIUM, or LOW")
    private String alertLevel;

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;

    @NotBlank(message = "Message is required")
    @Size(min = 1, max = 500, message = "Message must be between 1 and 500 characters")
    private String message;

    @Size(max = 32, message = "Metric key must not exceed 32 characters")
    private String metricKey;

    @DecimalMin(value = "0.0", message = "Current value must be non-negative")
    @DecimalMax(value = "1000.0", message = "Current value must not exceed 1000")
    private BigDecimal currentValue;

    @DecimalMin(value = "0.0", message = "Threshold value must be non-negative")
    @DecimalMax(value = "1000.0", message = "Threshold value must not exceed 1000")
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
