package com.hhs.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;

/**
 * Validator for profile additional data.
 * Validates:
 * - Maximum 50 keys
 * - Keys must be alphanumeric with underscores and hyphens
 * - Keys must not exceed 50 characters
 * - String values must not exceed 1000 characters
 */
public class ProfileDataValidator implements ConstraintValidator<ValidProfileData, Map<String, Object>> {

    private static final int MAX_KEYS = 50;
    private static final int MAX_KEY_LENGTH = 50;
    private static final int MAX_VALUE_LENGTH = 1000;
    private static final String KEY_PATTERN = "^[a-zA-Z0-9_\\-]+$";

    @Override
    public boolean isValid(Map<String, Object> data, ConstraintValidatorContext context) {
        if (data == null || data.isEmpty()) {
            return true; // Additional data is optional
        }

        // Validate data size
        if (data.size() > MAX_KEYS) {
            return false;
        }

        // Validate keys and values
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();

            // Key validation
            if (key == null || key.length() > MAX_KEY_LENGTH) {
                return false;
            }

            if (!key.matches(KEY_PATTERN)) {
                return false;
            }

            // Value validation (string values)
            Object value = entry.getValue();
            if (value instanceof String strValue) {
                if (strValue.length() > MAX_VALUE_LENGTH) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void initialize(ValidProfileData constraintAnnotation) {
        // No initialization needed
    }
}
