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

    @NotNull(message = "Metric key is required")
    @Size(min = 1, max = 32, message = "Metric key must be between 1 and 32 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "Metric key must contain only letters, numbers, hyphens, and underscores")
    private String metricKey;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.0", message = "Value must be non-negative")
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
