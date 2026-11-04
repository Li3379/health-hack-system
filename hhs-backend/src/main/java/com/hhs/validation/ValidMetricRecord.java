package com.hhs.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom class-level validation annotation for health metric requests.
 * Intercepts physically impossible biological values at the request level.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MetricRecordValidator.class)
@Documented
public @interface ValidMetricRecord {

    String message() default "健康指标数据校验未通过";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
