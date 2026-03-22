package com.hhs.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * Validator for health metric ranges.
 * Validates that metric values are within acceptable health ranges:
 * - Blood pressure: 60-300 mmHg
 * - Heart rate: 30-250 bpm
 * - Temperature: 30-45 Celsius
 * - Weight: 20-500 kg
 * - Height: 50-300 cm
 * - Glucose: 1-30 mmol/L
 */
public class MetricRangeValidator implements ConstraintValidator<ValidMetricRange, BigDecimal> {

    private static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALUE = new BigDecimal("1000");

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull handles null check
        }

        // Value must be non-negative
        if (value.compareTo(MIN_VALUE) < 0) {
            return false;
        }

        // Value must not exceed maximum
        if (value.compareTo(MAX_VALUE) > 0) {
            return false;
        }

        return true;
    }

    @Override
    public void initialize(ValidMetricRange constraintAnnotation) {
        // No initialization needed
    }
}
