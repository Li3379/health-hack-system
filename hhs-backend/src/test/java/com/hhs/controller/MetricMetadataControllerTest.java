package com.hhs.controller;

import com.hhs.config.SecurityConfig;
import com.hhs.config.SecurityHeadersConfig;
import com.hhs.security.JwtAuthenticationFilter;
import com.hhs.security.RestAccessDeniedHandler;
import com.hhs.security.RestAuthenticationEntryPoint;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.RecommendedThreshold;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricMetadataController.class)
@Import({SecurityConfig.class, SecurityHeadersConfig.class, HealthMetricMetaRegistry.class})
@ActiveProfiles("test")
class MetricMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthMetricMetaRegistry metricRegistry;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @MockBean
    private RestAccessDeniedHandler restAccessDeniedHandler;

    @MockBean(name = "corsConfigurationSource")
    private CorsConfigurationSource corsConfigurationSource;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ServletRequest req = invocation.getArgument(0);
            ServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        // The WebMvcTest slice has no database: seed the registry with the alert_rule seed
        // values (warningLow/warningHigh/criticalLow/criticalHigh) so the recommended
        // assertion is deterministic (heartRate seed: warning_high=100, critical_high=120,
        // warning_low=50, critical_low=40)
        metricRegistry.applyRecommendations(Map.of(
                "heartRate", new RecommendedThreshold(
                        new BigDecimal("50"), new BigDecimal("100"),
                        new BigDecimal("40"), new BigDecimal("120")),
                "bloodGlucose", new RecommendedThreshold(
                        new BigDecimal("3.9"), new BigDecimal("7.0"),
                        new BigDecimal("3.0"), new BigDecimal("11.1"))));
    }

    @Test
    @DisplayName("GET /api/metrics/metadata allows anonymous access and returns 200 with cache headers and core metrics")
    void testGetMetadataAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/metrics/metadata")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"))
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("ETag", containsString("v1.2-metric-meta")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.heartRate.canonicalKey").value("heartRate"))
                .andExpect(jsonPath("$.data.heartRate.displayName").value("心率"))
                .andExpect(jsonPath("$.data.heartRate.unit").value("次/分"))
                .andExpect(jsonPath("$.data.systolicBP.canonicalKey").value("systolicBP"))
                .andExpect(jsonPath("$.data.systolicBP.unit").value("mmHg"))
                .andExpect(jsonPath("$.data.diastolicBP.canonicalKey").value("diastolicBP"))
                .andExpect(jsonPath("$.data.bloodGlucose.canonicalKey").value("bloodGlucose"))
                .andExpect(jsonPath("$.data.bodyTemperature.canonicalKey").value("bodyTemperature"))
                // alertable crosses the wire: seeded metrics true, unseeded weight false (D-19)
                .andExpect(jsonPath("$.data.heartRate.alertable").value(true))
                .andExpect(jsonPath("$.data.bloodGlucose.alertable").value(true))
                .andExpect(jsonPath("$.data.weight.alertable").value(false))
                // recommended boundaries cross the wire (seeded heartRate warning band)
                .andExpect(jsonPath("$.data.heartRate.recommended.warningHigh").value(100));
    }
}
