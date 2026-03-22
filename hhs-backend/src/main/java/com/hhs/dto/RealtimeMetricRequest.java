package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating a realtime metric
 */
@Data
public class RealtimeMetricRequest {

    @NotBlank(message = "Metric key is required")
    @Size(min = 1, max = 32, message = "Metric key must be between 1 and 32 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "Metric key must contain only letters, numbers, hyphens, and underscores")
    private String metricKey;

    /**
     * Alias for metricKey - for frontend compatibility
     */
    private String metricType;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.0", message = "Value must be non-negative")
    @DecimalMax(value = "1000.0", message = "Value must not exceed 1000")
    private BigDecimal value;

    @Size(max = 32, message = "Unit must not exceed 32 characters")
    private String unit;

    /**
     * Data source: manual, device, api
     * Default is manual
     */
    @Size(max = 32, message = "Source must not exceed 32 characters")
    @Pattern(regexp = "^(manual|device|api)?$", message = "Source must be manual, device, api, or empty")
    private String source = "manual";

    /**
     * Quality score for device-entered metrics (0-1)
     */
    @DecimalMin(value = "0.0", message = "Quality score must be at least 0")
    @DecimalMax(value = "1.0", message = "Quality score must not exceed 1")
    private BigDecimal qualityScore;
}
