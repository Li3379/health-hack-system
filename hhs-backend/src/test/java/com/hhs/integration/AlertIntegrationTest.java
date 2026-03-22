package com.hhs.integration;

import com.hhs.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Alert Controller
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class AlertIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetAlerts() throws Exception {
        mockMvc.perform(get("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").exists());
    }

    @Test
    public void testGetAlertsWithFilters() throws Exception {
        mockMvc.perform(get("/api/alerts")
                .param("status", "PENDING")
                .param("severity", "WARNING")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    public void testAcknowledgeAlert() throws Exception {
        // This would need a valid alert ID in test database
        // Controller uses PUT method for acknowledge endpoint
        mockMvc.perform(put("/api/alerts/1/acknowledge")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testGetAlertStatistics() throws Exception {
        mockMvc.perform(get("/api/alerts/statistics")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.pending").exists());
    }
}
