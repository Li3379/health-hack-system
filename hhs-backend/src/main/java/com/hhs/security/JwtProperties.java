package com.hhs.security;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.jwt")
@Data
public class JwtProperties {

    private String secret;

    private long expireDays = 7;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET is required but not set.\n" +
                "Expected: 256-bit (32+ character) random string.\n" +
                "Fix: Set environment variable before starting the application.\n" +
                "Example: export JWT_SECRET=\"your-256-bit-secret-key-here\""
            );
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 256 bits (32 characters). Current length: " + secret.length()
            );
        }
    }
}
