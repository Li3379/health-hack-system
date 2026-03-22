package com.hhs.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for profile additional data.
 * Validates that profile data keys and values meet security requirements.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProfileDataValidator.class)
@Documented
public @interface ValidProfileData {
    String message() default "Invalid profile data";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
