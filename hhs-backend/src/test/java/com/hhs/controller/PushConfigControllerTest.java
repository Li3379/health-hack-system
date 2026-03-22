package com.hhs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.dto.PushConfigRequest;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.security.SecurityUtils;
import com.hhs.service.PushHistoryService;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannelManager;
import com.hhs.service.push.PushResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PushConfigController
 */
@WebMvcTest(PushConfigController.class)
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
}