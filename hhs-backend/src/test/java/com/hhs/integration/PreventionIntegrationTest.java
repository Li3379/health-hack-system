package com.hhs.integration;

import com.hhs.config.TestSecurityConfig;
import com.hhs.util.TestDataUtil;
import com.hhs.dto.HealthProfileRequest;
import com.hhs.entity.HealthProfile;
import com.hhs.mapper.HealthProfileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Prevention Controller
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class PreventionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthProfileMapper healthProfileMapper;

    private Long testUserId = 1L;

    @BeforeEach
    public void setUp() {
        // Clean up any existing test data
        healthProfileMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, testUserId)
        );
    }

    @AfterEach
    public void tearDown() {
        // Cleanup test data
        healthProfileMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, testUserId)
        );
    }

    @Test
    public void testGetHealthProfile() throws Exception {
        // First create a profile so the get endpoint can find it
        String createRequest = """
            {
                "gender": "male",
                "birthDate": "1994-01-01",
                "heightCm": 175.0,
                "weightKg": 70.0,
                "bloodType": "A+",
                "allergyHistory": "None",
                "familyHistory": "None",
                "lifestyleHabits": "None"
            }
            """;

        // Create profile first
        mockMvc.perform(post("/api/prevention/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
                .andExpect(status().isOk());

        // Now get the profile
        mockMvc.perform(get("/api/prevention/profile")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    public void testCreateHealthProfile() throws Exception {
        HealthProfileRequest request = TestDataUtil.createTestProfileRequest();

        String jsonRequest = """
            {
                "gender": "male",
                "birthDate": "1994-01-01",
                "heightCm": 175.0,
                "weightKg": 70.0,
                "bloodType": "A+",
                "allergyHistory": "None",
                "familyHistory": "None",
                "lifestyleHabits": "None"
            }
            """;

        mockMvc.perform(post("/api/prevention/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testGetHealthMetrics() throws Exception {
        mockMvc.perform(get("/api/prevention/metrics")
                .param("metricKey", "heartRate")
                .param("days", "7")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    public void testGetRiskAssessment() throws Exception {
        mockMvc.perform(get("/api/prevention/risk-assessment")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }
}
