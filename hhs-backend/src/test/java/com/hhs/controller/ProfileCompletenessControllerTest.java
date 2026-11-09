package com.hhs.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.config.TestSecurityConfig;
import com.hhs.security.SecurityUtils;
import com.hhs.service.HealthMetricService;
import com.hhs.service.HealthProfileService;
import com.hhs.service.RiskAssessmentService;
import com.hhs.service.domain.ProfileCompletenessCalculator;
import com.hhs.service.domain.ProfileFieldCatalog;
import com.hhs.vo.ProfileCompletenessVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wire-shape tests for {@code GET /api/prevention/profile/completeness}.
 *
 * <p>The stub is produced by the real {@link ProfileCompletenessCalculator} rather than a
 * hand-built record, so the fixture cannot drift from production semantics: if a field is
 * renamed, reordered or re-weighted, these assertions fail instead of silently pinning a
 * stale shape. The no-profile case is the important one — it is the state the guidance
 * surfaces exist to show, so it must be 200 with a 0% payload rather than 404.</p>
 */
@WebMvcTest(PreventionController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class ProfileCompletenessControllerTest {

    private static final Long TEST_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HealthProfileService healthProfileService;

    @MockBean
    private HealthMetricService healthMetricService;

    @MockBean
    private RiskAssessmentService riskAssessmentService;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;

    @BeforeEach
    void setUp() {
        mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(TEST_USER_ID);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("未创建档案时返回 200 与 0 分，而不是 404")
    @WithMockUser(username = "testuser")
    void noProfileReturnsZeroScoreAndNineFields() throws Exception {
        ProfileCompletenessVO expected =
                new ProfileCompletenessCalculator(new ProfileFieldCatalog()).calculate(null);
        when(healthProfileService.getCompleteness(TEST_USER_ID)).thenReturn(expected);

        mockMvc.perform(get("/api/prevention/profile/completeness")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.level").value("基础档案"))
                .andExpect(jsonPath("$.data.filledCount").value(0))
                .andExpect(jsonPath("$.data.hasProfile").value(false))
                .andExpect(jsonPath("$.data.assumptions.length()").value(1))
                .andExpect(jsonPath("$.data.assumptions[0]").value(
                        "未填写健康档案，风险得分按通用参考值估算"))
                .andExpect(jsonPath("$.data.fields.length()").value(9))
                .andExpect(jsonPath("$.data.fields[0].key").value("gender"))
                .andExpect(jsonPath("$.data.fields[0].label").value("性别"))
                .andExpect(jsonPath("$.data.fields[0].importance").value("required"))
                .andExpect(jsonPath("$.data.fields[0].filled").value(false))
                .andExpect(jsonPath("$.data.fields[0].reason").isNotEmpty());
    }

    @Test
    @DisplayName("totalCount 恒等于 fields 长度，前端无需硬编码")
    @WithMockUser(username = "testuser")
    void totalCountEqualsFieldListLength() throws Exception {
        ProfileCompletenessVO expected =
                new ProfileCompletenessCalculator(new ProfileFieldCatalog()).calculate(null);
        when(healthProfileService.getCompleteness(TEST_USER_ID)).thenReturn(expected);

        MvcResult result = mockMvc.perform(get("/api/prevention/profile/completeness")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(9))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertEquals(data.path("fields").size(), data.path("totalCount").asInt(),
                "totalCount 必须等于序列化后的 fields 长度");
        assertEquals(9, data.path("fields").size(), "目录固定为 9 个字段");
    }
}