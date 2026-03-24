package com.hhs.config;

import com.hhs.security.JwtAuthenticationFilter;
import com.hhs.security.RestAccessDeniedHandler;
import com.hhs.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the HHS application.
 * Provides environment-specific CORS configuration to prevent unauthorized cross-origin requests.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtAuthenticationFilter,
                                                    RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                                                    RestAccessDeniedHandler restAccessDeniedHandler,
                                                    @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
                                                    SecurityHeadersConfig securityHeadersConfig) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Apply security headers
                .headers(headers -> {
                    try {
                        securityHeadersConfig.configureHeaders(headers);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to configure security headers", e);
                    }
                })
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/uploads/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/doc.html",
                                "/actuator/health",
                                "/actuator/info",
                                "/error",
                                "/ws/**" // WebSocket endpoint
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/**").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Development CORS configuration - allows common development origins.
     * Active when profile is "dev" or no profile is specified.
     */
    @Bean(name = "corsConfigurationSource")
    @Profile({"default", "dev"})
    public CorsConfigurationSource devCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        // Allow common development origins
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*:*",
            "http://10.*:*",
            "http://172.16.*:*",
            "http://172.17.*:*",
            "http://172.18.*:*",
            "http://172.19.*:*",
            "http://172.20.*:*",
            "http://172.21.*:*",
            "http://172.22.*:*",
            "http://172.23.*:*",
            "http://172.24.*:*",
            "http://172.25.*:*",
            "http://172.26.*:*",
            "http://172.27.*:*",
            "http://172.28.*:*",
            "http://172.29.*:*",
            "http://172.30.*:*",
            "http://172.31.*:*"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Production CORS configuration - requires explicit origin whitelist.
     * The ALLOWED_ORIGINS environment variable must be configured.
     * Example: export ALLOWED_ORIGINS="https://yourdomain.com"
     * For IP-based deployment: export ALLOWED_ORIGINS="http://47.112.6.180"
     * Multiple origins can be comma-separated: https://app1.com,https://app2.com
     *
     * @throws IllegalStateException if ALLOWED_ORIGINS is not configured
     */
    @Bean(name = "corsConfigurationSource")
    @Profile("prod")
    public CorsConfigurationSource prodCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);

        // Parse comma-separated origins from environment variable
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            List<String> origins = Arrays.asList(allowedOrigins.split(","));
            // Trim whitespace from each origin
            origins = origins.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            
            if (origins.isEmpty()) {
                throw new IllegalStateException(
                        "ALLOWED_ORIGINS must be configured for production profile. " +
                        "Example: export ALLOWED_ORIGINS=\"http://47.112.6.180\" " +
                        "or for domain: export ALLOWED_ORIGINS=\"https://yourdomain.com\""
                );
            }
            
            configuration.setAllowedOriginPatterns(origins);
        } else {
            throw new IllegalStateException(
                    "ALLOWED_ORIGINS must be configured for production profile. " +
                    "Example: export ALLOWED_ORIGINS=\"http://47.112.6.180\" " +
                    "or for domain: export ALLOWED_ORIGINS=\"https://yourdomain.com\""
            );
        }

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Test CORS configuration - allows all origins for testing.
     * Permissive configuration is acceptable for test environments.
     */
    @Bean(name = "corsConfigurationSource")
    @Profile("test")
    public CorsConfigurationSource testCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Fallback CORS configuration - ensures bean is always available.
     * This provides a secure default that matches the development configuration.
     */
    @Bean(name = "corsConfigurationSource")
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    public CorsConfigurationSource fallbackCorsConfigurationSource() {
        // Reuse development configuration as fallback
        return devCorsConfigurationSource();
    }
}