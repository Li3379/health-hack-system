package com.hhs.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;

/**
 * Validates JWT secret strength at application startup.
 * Ensures the secret meets minimum security requirements:
 * - Minimum 256 bits (32 characters) for HS256 algorithm
 * - Contains sufficient entropy (mix of characters)
 * - Not a known weak/default secret
 *
 * For production profile, validation is strict and will fail startup.
 * For development profile, warnings are logged but startup continues.
 */
@Slf4j
@Component
public class JwtSecretValidator {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // Minimum length for HS256: 256 bits = 32 bytes
    private static final int MINIMUM_SECRET_LENGTH = 32;

    // Pattern to check for character variety
    private static final Pattern HAS_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    // Known weak secrets that should never be used
    private static final String[] WEAK_SECRETS = {
        "secret", "password", "jwt-secret", "my-secret-key",
        "hhs-test-secret-key", "dev-test", "development",
        "changeme", "default", "admin"
    };

    @PostConstruct
    public void validateSecret() {
        log.info("Validating JWT secret strength for profile: {}", activeProfile);

        // Check if secret is configured
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                "JWT secret is not configured! Set the JWT_SECRET environment variable."
            );
        }

        // Check minimum length
        if (jwtSecret.length() < MINIMUM_SECRET_LENGTH) {
            String message = String.format(
                "JWT secret is too short! Minimum %d characters required for HS256, got %d. " +
                "Generate with: openssl rand -base64 32",
                MINIMUM_SECRET_LENGTH, jwtSecret.length()
            );
            handleValidationFailure(message);
            return;
        }

        // Check for known weak secrets
        String lowerSecret = jwtSecret.toLowerCase();
        for (String weak : WEAK_SECRETS) {
            if (lowerSecret.contains(weak)) {
                String message = String.format(
                    "JWT secret contains weak pattern '%s'. Use a cryptographically secure random secret. " +
                    "Generate with: openssl rand -base64 32",
                    weak
                );
                handleValidationFailure(message);
                return;
            }
        }

        // Check for character variety (entropy)
        int varietyScore = 0;
        if (HAS_UPPERCASE.matcher(jwtSecret).find()) varietyScore++;
        if (HAS_LOWERCASE.matcher(jwtSecret).find()) varietyScore++;
        if (HAS_DIGIT.matcher(jwtSecret).find()) varietyScore++;
        if (HAS_SPECIAL.matcher(jwtSecret).find()) varietyScore++;

        if (varietyScore < 3 && isProductionProfile()) {
            log.warn(
                "JWT secret has low entropy (variety score: {}/4). " +
                "Consider using a more complex secret generated with: openssl rand -base64 32",
                varietyScore
            );
        }

        log.info("JWT secret validation passed. Length: {} characters, Entropy score: {}/4",
            jwtSecret.length(), varietyScore);
    }

    private void handleValidationFailure(String message) {
        if (isProductionProfile()) {
            throw new IllegalStateException("SECURITY ERROR: " + message);
        } else {
            log.warn("SECURITY WARNING (development): {}", message);
            log.warn("This warning will cause application failure in production!");
        }
    }

    private boolean isProductionProfile() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }
}