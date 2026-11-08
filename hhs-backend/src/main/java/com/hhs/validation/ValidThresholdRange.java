package com.hhs.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom class-level validation annotation for user threshold requests (THR-05 / D-09).
 * Intercepts invalid boundary ordering (criticalLow &lt; warningLow &lt; warningHigh &lt; criticalHigh)
 * and physiologically impossible boundaries (metadata hardMin/hardMax, D-11) so that a direct
 * API call cannot bypass the frontend wizard.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ThresholdRangeValidator.class)
@Documented
public @interface ValidThresholdRange {

    String message() default "阈值次序或生理极限不合法";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
