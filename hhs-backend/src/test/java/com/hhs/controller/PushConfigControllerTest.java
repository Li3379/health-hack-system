package com.hhs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.config.TestSecurityConfig;
import com.hhs.dto.PushConfigRequest;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.security.JwtUtil;
import com.hhs.security.SecurityUtils;
import com.hhs.service.PushHistoryService;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannelManager;
import com.hhs.service.push.PushResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PushConfigController
 */
@WebMvcTest(PushConfigController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class PushConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserPushConfigMapper userPushConfigMapper;

    @MockBean
    private PushChannelManager pushChannelManager;

    @MockBean
    private PushHistoryService pushHistoryService;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    private UserPushConfig testConfig;

    @BeforeEach
    void setUp() {
        // Mock static SecurityUtils
        mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

        testConfig = new UserPushConfig();
        testConfig.setId(1L);
        testConfig.setUserId(1L);
        testConfig.setChannelType("EMAIL");
        testConfig.setConfigKey("email");
        testConfig.setConfigValue("test@example.com");
        testConfig.setEnabled(1);
        testConfig.setCreatedAt(LocalDateTime.now());
        testConfig.setUpdatedAt(LocalDateTime.now());

        when(pushChannelManager.hasChannel(any())).thenReturn(true);
        when(pushChannelManager.push(any(), any(), any()))
                .thenReturn(PushResult.success(ChannelType.EMAIL));
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("Should get all configs")
    @WithMockUser(username = "admin")
    void testGetAllConfigs() throws Exception {
        // Given
        List<UserPushConfig> configs = new ArrayList<>();
        configs.add(testConfig);
        when(userPushConfigMapper.selectList(any())).thenReturn(configs);

        // When & Then
        mockMvc.perform(get("/api/push/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============================================================
    // PUSH-6：availability 只代表「渠道 bean 已注册」，不代表投递能力
    // ============================================================

    @Test
    @DisplayName("最近一次投递失败时，邮件渠道带 deliveryStatus=FAILED（页面据此不再声称支持离线推送）")
    void testGetAllConfigsExposesFailedDeliveryOutcome() throws Exception {
        testConfig.setChannelType("EMAIL");
        testConfig.setConfigValue("someone@example.com");
        when(userPushConfigMapper.selectList(any())).thenReturn(List.of(testConfig));
        when(pushChannelManager.hasChannel(ChannelType.EMAIL)).thenReturn(true);

        com.hhs.entity.PushHistory failed = new com.hhs.entity.PushHistory();
        failed.setId(9L);
        failed.setUserId(1L);
        failed.setChannelType("EMAIL");
        failed.setStatus("FAILED");
        failed.setPushedAt(LocalDateTime.now().minusMinutes(5));
        when(pushHistoryService.getLatestPerChannelSince(eq(1L), any()))
                .thenReturn(List.of(failed));

        mockMvc.perform(get("/api/push/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.channelType=='EMAIL')].availability").value(
                        org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.channelType=='EMAIL')].deliveryStatus").value(
                        org.hamcrest.Matchers.contains("FAILED")));
    }

    @Test
    @DisplayName("最近一次投递成功时 deliveryStatus=SUCCESS（失败标记随之消失）")
    void testGetAllConfigsExposesSuccessfulDeliveryOutcome() throws Exception {
        testConfig.setChannelType("EMAIL");
        when(userPushConfigMapper.selectList(any())).thenReturn(List.of(testConfig));
        when(pushChannelManager.hasChannel(ChannelType.EMAIL)).thenReturn(true);

        com.hhs.entity.PushHistory ok = new com.hhs.entity.PushHistory();
        ok.setId(10L);
        ok.setUserId(1L);
        ok.setChannelType("EMAIL");
        ok.setStatus("SUCCESS");
        ok.setPushedAt(LocalDateTime.now());
        when(pushHistoryService.getLatestPerChannelSince(eq(1L), any())).thenReturn(List.of(ok));

        mockMvc.perform(get("/api/push/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.channelType=='EMAIL')].deliveryStatus").value(
                        org.hamcrest.Matchers.contains("SUCCESS")));
    }

    @Test
    @DisplayName("从未尝试过的渠道 deliveryStatus 为 null —— 不冒充「已验证」也不冒充「失败」")
    void testGetAllConfigsWithoutHistoryLeavesDeliveryStatusNull() throws Exception {
        testConfig.setChannelType("EMAIL");
        when(userPushConfigMapper.selectList(any())).thenReturn(List.of(testConfig));
        when(pushHistoryService.getLatestPerChannelSince(eq(1L), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/push/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.channelType=='EMAIL')].deliveryStatus").value(
                        org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    @DisplayName("Should mask the stored config value on read (WR-10)")
    @WithMockUser(username = "admin")
    void testGetAllConfigsMasksValue() throws Exception {
        testConfig.setChannelType("FEISHU");
        testConfig.setConfigValue("https://open.feishu.cn/open-apis/bot/v2/hook/secret-token-1234");
        when(userPushConfigMapper.selectList(any())).thenReturn(List.of(testConfig));

        mockMvc.perform(get("/api/push/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.channelType=='FEISHU')].configValue").value(
                        org.hamcrest.Matchers.contains(
                                org.hamcrest.Matchers.containsString("****"))))
                .andExpect(jsonPath("$.data[?(@.channelType=='FEISHU')].configValue").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.contains("secret-token-1234"))));
    }

    @Test
    @DisplayName("Should get specific channel config")
    @WithMockUser(username = "admin")
    void testGetSpecificConfig() throws Exception {
        // Given
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);

        // When & Then
        mockMvc.perform(get("/api/push/config/EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.channelType").value("EMAIL"));
    }

    @Test
    @DisplayName("Should return default config for unconfigured channel")
    @WithMockUser(username = "admin")
    void testGetUnconfiguredChannel() throws Exception {
        // Given
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "WECOM")).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/push/config/WECOM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("Should return error for invalid channel type")
    @WithMockUser(username = "admin")
    void testInvalidChannelType() throws Exception {
        mockMvc.perform(get("/api/push/config/INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("Should create new config")
    @WithMockUser(username = "admin")
    void testCreateConfig() throws Exception {
        // Given
        PushConfigRequest request = new PushConfigRequest();
        request.setChannelType("EMAIL");
        request.setConfigValue("new@example.com");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(null);
        when(userPushConfigMapper.insert(any())).thenAnswer(inv -> {
            UserPushConfig config = inv.getArgument(0);
            config.setId(2L);
            return 1;
        });

        // When & Then
        mockMvc.perform(put("/api/push/config/EMAIL")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userPushConfigMapper).insert(any());
    }

    @Test
    @DisplayName("Should update existing config")
    @WithMockUser(username = "admin")
    void testUpdateConfig() throws Exception {
        // Given
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("updated@example.com");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);
        when(userPushConfigMapper.updateById(any())).thenReturn(1);

        // When & Then
        mockMvc.perform(put("/api/push/config/EMAIL")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userPushConfigMapper).updateById(any());
    }

    // ============================================================
    // PUSH-11：EMAIL 的 configValue 会被直接当成收件人，写入时必须校验格式
    // ============================================================

    @Test
    @DisplayName("非法邮箱地址被拒（清单里的 foo@qq），不入库")
    @WithMockUser(username = "admin")
    void testUpdateConfigRejectsMalformedEmail() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("foo@qq");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);

        mockMvc.perform(put("/api/push/config/EMAIL")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.code").value(400));

        // 关键：格式不对就不能落库，否则页面会显示「已配置」
        verify(userPushConfigMapper, never()).updateById(any());
        verify(userPushConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("掩码值（含 ****）不当作真实地址校验，否则编辑其它字段会被卡住")
    @WithMockUser(username = "admin")
    void testUpdateConfigSkipsMaskedValue() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        // 客户端读回配置后把掩码值原样提交，这是「不改地址」的合法操作
        request.setConfigValue("alice@exam****.com");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);
        when(userPushConfigMapper.updateById(any())).thenReturn(1);

        mockMvc.perform(put("/api/push/config/EMAIL")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userPushConfigMapper).updateById(any());
    }

    @Test
    @DisplayName("邮箱校验只作用于 EMAIL，不误伤 webhook 渠道")
    @WithMockUser(username = "admin")
    void testUpdateConfigDoesNotEmailValidateWebhooks() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "WECOM")).thenReturn(null);
        when(userPushConfigMapper.insert(any())).thenReturn(1);

        mockMvc.perform(put("/api/push/config/WECOM")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("Should require config value for offline channels")
    @WithMockUser(username = "admin")
    void testRequireConfigValue() throws Exception {
        // Given
        PushConfigRequest request = new PushConfigRequest();
        request.setEnabled(true);
        // No configValue set

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(null);

        // When & Then
        mockMvc.perform(put("/api/push/config/EMAIL")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("Should reject a webhook URL pointing at a private network (SSRF)")
    @WithMockUser(username = "admin")
    void testRejectPrivateWebhookUrl() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("https://169.254.169.254/latest/meta-data/");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "FEISHU")).thenReturn(null);

        mockMvc.perform(put("/api/push/config/FEISHU")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // A rejected URL is a client error: the envelope must mirror VALIDATION_INVALID_PARAMETER
                // (400), not the historical parseCodeToInt fallback of 500.
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value("validation.invalid_parameter"));

        verify(userPushConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should reject a non-https webhook URL (SSRF)")
    @WithMockUser(username = "admin")
    void testRejectPlainHttpWebhookUrl() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("http://example.com/hook");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "WECOM")).thenReturn(null);

        mockMvc.perform(put("/api/push/config/WECOM")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value("validation.invalid_parameter"));

        verify(userPushConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should accept a public https webhook URL")
    @WithMockUser(username = "admin")
    void testAcceptPublicWebhookUrl() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("https://8.8.8.8/open-apis/bot/v2/hook/abc");
        request.setEnabled(true);

        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "FEISHU")).thenReturn(null);
        when(userPushConfigMapper.insert(any())).thenAnswer(inv -> {
            UserPushConfig config = inv.getArgument(0);
            config.setId(2L);
            return 1;
        });

        mockMvc.perform(put("/api/push/config/FEISHU")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userPushConfigMapper).insert(any());
    }

    @Test
    @DisplayName("Should reject an over-long config value before it reaches the mapper")
    @WithMockUser(username = "admin")
    void testRejectOverlongConfigValue() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("a".repeat(501));
        request.setEnabled(true);

        mockMvc.perform(put("/api/push/config/FEISHU")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(userPushConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should delete config")
    @WithMockUser(username = "admin")
    void testDeleteConfig() throws Exception {
        // Given
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);
        when(userPushConfigMapper.deleteById(1L)).thenReturn(1);

        // When & Then
        mockMvc.perform(delete("/api/push/config/EMAIL")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userPushConfigMapper).deleteById(1L);
    }

    @Test
    @DisplayName("Should test push channel successfully")
    @WithMockUser(username = "admin")
    void testTestChannelSuccess() throws Exception {
        // Given
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);
        when(pushChannelManager.push(eq(1L), any(), eq(ChannelType.EMAIL)))
                .thenReturn(PushResult.success(ChannelType.EMAIL));

        // When & Then
        mockMvc.perform(post("/api/push/config/EMAIL/test")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    @DisplayName("测试推送用的 alertType 必须落在真实值域内（PUSH-7）")
    @WithMockUser(username = "admin")
    void testTestChannelBuildsValueDomainCompliantAlert() throws Exception {
        // 这个 alert 是手工 new 的，不走 Bean Validation —— 写 INFO 也不会被拦。
        // 而 AlertVO.alertType 的 @Pattern 是 ^(CRITICAL|WARNING|NORMAL)$，
        // 真实告警也只可能由 ThresholdRangeChecker 产出这三个值。
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);
        when(pushChannelManager.push(eq(1L), any(), eq(ChannelType.EMAIL)))
                .thenReturn(PushResult.success(ChannelType.EMAIL));

        mockMvc.perform(post("/api/push/config/EMAIL/test").with(csrf()))
                .andExpect(status().isOk());

        ArgumentCaptor<com.hhs.dto.AlertVO> captor =
                ArgumentCaptor.forClass(com.hhs.dto.AlertVO.class);
        verify(pushChannelManager).push(eq(1L), captor.capture(), eq(ChannelType.EMAIL));

        String alertType = captor.getValue().getAlertType();
        assertTrue(java.util.Set.of("CRITICAL", "WARNING", "NORMAL").contains(alertType),
                "测试推送的 alertType 必须是值域内的值，实际: " + alertType);
        assertEquals("NORMAL", alertType);
    }

    @Test
    @DisplayName("Should fail test when channel not enabled")
    @WithMockUser(username = "admin")
    void testTestChannelNotEnabled() throws Exception {
        // Given
        testConfig.setEnabled(0);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);

        // When & Then
        mockMvc.perform(post("/api/push/config/EMAIL/test")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("Should get push history")
    @WithMockUser(username = "admin")
    void testGetHistory() throws Exception {
        // Given
        when(pushHistoryService.getRecentHistory(1L, 20)).thenReturn(new ArrayList<>());

        // When & Then
        mockMvc.perform(get("/api/push/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Should get push stats")
    @WithMockUser(username = "admin")
    void testGetStats() throws Exception {
        // Given
        when(pushHistoryService.getChannelStats(eq(1L), any())).thenReturn(new ArrayList<>());

        // When & Then
        mockMvc.perform(get("/api/push/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== Server availability ====================

    @Test
    @DisplayName("Should expose availability from hasChannel on the list endpoint")
    @WithMockUser(username = "admin")
    void testGetAllConfigsExposesAvailability() throws Exception {
        when(userPushConfigMapper.selectList(any())).thenReturn(List.of(testConfig));
        when(pushChannelManager.hasChannel(ChannelType.EMAIL)).thenReturn(false);

        mockMvc.perform(get("/api/push/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.channelType=='EMAIL')].availability").value(false))
                .andExpect(jsonPath("$.data[?(@.channelType=='WECOM')].availability").value(true));
    }

    @Test
    @DisplayName("Should expose availability on the single-channel endpoint")
    @WithMockUser(username = "admin")
    void testGetSpecificConfigExposesAvailability() throws Exception {
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);
        when(pushChannelManager.hasChannel(ChannelType.EMAIL)).thenReturn(false);

        mockMvc.perform(get("/api/push/config/EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value(false));
    }

    @Test
    @DisplayName("Should refuse to enable a channel the server never registered")
    @WithMockUser(username = "admin")
    void testRejectEnablingUnregisteredChannel() throws Exception {
        PushConfigRequest request = new PushConfigRequest();
        request.setConfigValue("new@example.com");
        request.setEnabled(true);

        when(pushChannelManager.hasChannel(ChannelType.EMAIL)).thenReturn(false);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(null);

        mockMvc.perform(put("/api/push/config/EMAIL")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(userPushConfigMapper, never()).insert(any());
        verify(userPushConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should still allow disabling a channel the server no longer registers")
    @WithMockUser(username = "admin")
    void testAllowDisablingUnregisteredChannel() throws Exception {
        // An existing enabled=1 row on an environment that lost the capability must stay
        // closable, otherwise the switch is stuck on forever.
        PushConfigRequest request = new PushConfigRequest();
        request.setEnabled(false);

        when(pushChannelManager.hasChannel(ChannelType.EMAIL)).thenReturn(false);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(testConfig);
        when(userPushConfigMapper.updateById(any())).thenReturn(1);

        mockMvc.perform(put("/api/push/config/EMAIL")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userPushConfigMapper).updateById(any());
    }
}