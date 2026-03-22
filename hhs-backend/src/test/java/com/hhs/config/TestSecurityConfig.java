package com.hhs.config;

import com.hhs.entity.User;
import com.hhs.security.LoginUser;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.io.IOException;

/**
 * Test security configuration that sets up mock authentication for integration tests.
 * This provides a test user context for SecurityUtils.getCurrentUserId() calls.
 *
 * Note: CORS configuration is provided by SecurityConfig.testCorsConfigurationSource()
 * with @Profile("test"), so we don't define it here to avoid bean definition conflicts.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class TestSecurityConfig {

    /**
     * Creates a test user for integration tests.
     */
    private static LoginUser createTestLoginUser() {
        User testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        return new LoginUser(testUser);
    }

    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {}) // Use default CORS configuration from SecurityConfig
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(testAuthenticationFilter(), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Filter that sets up test authentication in the security context.
     * This ensures that SecurityUtils.getCurrentUserId() returns a valid user ID.
     */
    @Bean
    public Filter testAuthenticationFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                LoginUser testUser = createTestLoginUser();
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                chain.doFilter(request, response);
            }
        };
    }
}