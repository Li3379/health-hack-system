package com.hhs.integration;

import com.hhs.common.constant.ErrorCode;
import com.hhs.config.TestSecurityConfig;
import com.hhs.util.TestDataUtil;
import com.hhs.dto.RealtimeMetricRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Realtime Controller
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class RealtimeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testSubmitRealtimeMetric() throws Exception {
        String jsonRequest = """
            {
                "metricKey": "heartRate",
                "value": 75.0,
                "unit": "次/分",
                "source": "device"
            }
            """;

        mockMvc.perform(post("/api/realtime/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    public void testGetLatestMetrics() throws Exception {
        mockMvc.perform(get("/api/realtime/metrics/latest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    public void testGetMetricHistory() throws Exception {
        mockMvc.perform(get("/api/realtime/metrics/history")
                .param("metricKey", "heartRate")
                .param("hours", "24")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    public void testValidateMetricValue() throws Exception {
        // Test with invalid value - validation returns 400 with error details
        String jsonRequest = """
            {
                "metricKey": "heart_rate",
                "value": -10,
                "unit": "bpm"
            }
            """;

        mockMvc.perform(post("/api/realtime/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_INVALID_PARAMETER.getCode()))
                .andExpect(jsonPath("$.errors").exists());
    }
}
