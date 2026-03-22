package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Health Metric Request DTO
 * Used for creating and updating health metrics
 */
@Data
public class HealthMetricRequest {

    // userId is obtained from SecurityUtils.getCurrentUserId(), not from request body
    private Long userId;

    @NotNull(message = "Metric key is required")
    @Size(min = 1, max = 32, message = "Metric key must be between 1 and 32 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "Metric key must contain only letters, numbers, hyphens, and underscores")
    private String metricKey;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.0", message = "Value must be non-negative")
    @DecimalMax(value = "1000.0", message = "Value must not exceed 1000")
    private BigDecimal value;

    @NotNull(message = "Record date is required")
    @PastOrPresent(message = "Record date cannot be in the future")
    private LocalDate recordDate;

    /**
     * Precise timestamp for the measurement.
     * Used for conflict resolution when multiple records have the same recordDate.
     * If null, recordDate at start of day is assumed.
     */
    private LocalDateTime recordTime;

    @Size(max = 32, message = "Unit must not exceed 32 characters")
    private String unit;

    @Size(max = 16, message = "Trend must not exceed 16 characters")
    @Pattern(regexp = "^(up|down|stable|normal)?$", message = "Trend must be up, down, stable, normal, or empty")
    private String trend;
}
