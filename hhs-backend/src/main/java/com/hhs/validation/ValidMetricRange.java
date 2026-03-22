package com.hhs.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for health metric ranges.
 * Validates that metric values are within acceptable health ranges.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MetricRangeValidator.class)
@Documented
public @interface ValidMetricRange {
    String message() default "Invalid metric value";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
