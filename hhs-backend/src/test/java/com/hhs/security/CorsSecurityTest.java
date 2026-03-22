package com.hhs.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS Security Tests - SEC-004
 * Verifies that CORS configuration properly restricts cross-origin requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class CorsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Test that localhost:5173 (development frontend) is allowed in dev profile.
     */
    @Test
    public void testCorsAllowsLocalhostInDevProfile() throws Exception {
        mockMvc.perform(options("/api/user/profile")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"))
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().exists("Access-Control-Allow-Credentials"));
    }

    /**
     * Test that localhost:3000 is allowed in dev profile (all localhost ports allowed).
     */
    @Test
    public void testCorsAllowsLocalhost3000InDevProfile() throws Exception {
        mockMvc.perform(options("/api/user/profile")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    /**
     * Test that requests from unauthorized domain are rejected in dev profile.
     */
    @Test
    public void testCorsRejectsUnauthorizedDomainInDevProfile() throws Exception {
        mockMvc.perform(options("/api/user/profile")
                .header("Origin", "http://evil.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    /**
     * Test that HTTPS origins are rejected in dev profile (dev uses HTTP).
     */
    @Test
    public void testCorsRejectsHttpsInDevProfile() throws Exception {
        mockMvc.perform(options("/api/user/profile")
                .header("Origin", "https://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    /**
     * Test CORS headers configuration for preflight requests.
     */
    @Test
    public void testCorsHeadersConfiguration() throws Exception {
        mockMvc.perform(options("/api/user/profile")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Headers"))
            .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    /**
     * Test that POST requests from allowed origin are permitted.
     */
    @Test
    public void testCorsAllowsPostFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    /**
     * Test that wildcard origin is NOT allowed (security requirement).
     */
    @Test
    public void testCorsRejectsWildcardOrigin() throws Exception {
        mockMvc.perform(options("/api/user/profile")
                .header("Origin", "http://attacker.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }
}
