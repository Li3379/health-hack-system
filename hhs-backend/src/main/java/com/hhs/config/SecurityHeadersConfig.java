package com.hhs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Security headers configuration for HTTP responses.
 * Provides protection against common web vulnerabilities:
 * - XSS (Cross-Site Scripting)
 * - Clickjacking
 * - Content injection
 * - MIME type sniffing
 */
@Configuration
public class SecurityHeadersConfig {

    /**
     * Configures security headers for all profiles.
     * These headers are applied to all HTTP responses.
     */
    public void configureHeaders(HeadersConfigurer<?> headers) throws Exception {
        headers
            // Prevent clickjacking - deny all iframe embedding
            .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)

            // Prevent MIME type sniffing
            .contentTypeOptions(contentTypeOptions -> {})

            // Enable XSS protection with block mode
            .xssProtection(xss -> xss
                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
            )

            // HTTP Strict Transport Security (HSTS)
            // Enable for production to force HTTPS
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000) // 1 year
            )

            // Content-Security-Policy
            // Tuned for Vue 3 + Element Plus compatibility
            .contentSecurityPolicy(csp -> csp
                .policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self' ws: wss:; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'"
                )
            )

            // Referrer-Policy - limit referrer information
            .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))

            // Permissions-Policy - disable unnecessary browser features
            .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                "geolocation=(), " +
                "microphone=(), " +
                "camera=(), " +
                "payment=(), " +
                "usb=()"
            ));
    }
}