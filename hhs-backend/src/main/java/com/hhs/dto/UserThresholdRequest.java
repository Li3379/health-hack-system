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

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Metric key is required")
    @Size(min = 1, max = 64, message = "Metric key must be between 1 and 64 characters")
    private String metricKey;

    @DecimalMin(value = "0.0", message = "Warning high must be non-negative")
    private BigDecimal warningHigh;

    @DecimalMin(value = "0.0", message = "Critical high must be non-negative")
    private BigDecimal criticalHigh;

    @DecimalMin(value = "0.0", message = "Warning low must be non-negative")
    private BigDecimal warningLow;

    @DecimalMin(value = "0.0", message = "Critical low must be non-negative")
    private BigDecimal criticalLow;
}
