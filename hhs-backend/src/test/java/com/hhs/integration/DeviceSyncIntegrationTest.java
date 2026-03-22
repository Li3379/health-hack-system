package com.hhs.integration;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.config.TestSecurityConfig;
import com.hhs.entity.DeviceConnection;
import com.hhs.service.*;
import com.hhs.vo.DeviceConnectionVO;
import com.hhs.vo.SyncHistoryVO;
import com.hhs.vo.SyncResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for device sync input flow.
 *
 * Tests the complete OAuth flow and data synchronization
 * from wearable devices (Huawei Health, etc.).
 *
 * @see com.hhs.controller.DeviceSyncController
 * @see com.hhs.service.DeviceSyncOrchestrationService
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@DisplayName("设备同步集成测试")
public class DeviceSyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceSyncOrchestrationService orchestrationService;

    @MockBean
    private DeviceConnectionService deviceConnectionService;

    @MockBean
    private SyncHistoryService syncHistoryService;

    @MockBean
    private HuaweiHealthService huaweiHealthService;

    @MockBean
    private TokenEncryptionService tokenEncryptionService;

    private static final String API_BASE = "/api/device";
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // Default mock behaviors
        when(orchestrationService.isPlatformRegistered("huawei")).thenReturn(true);
        when(orchestrationService.isPlatformRegistered("xiaomi")).thenReturn(true);
        when(orchestrationService.isPlatformRegistered("unknown")).thenReturn(false);

        when(orchestrationService.isPlatformConfigured("huawei")).thenReturn(true);
        when(orchestrationService.isPlatformConfigured("xiaomi")).thenReturn(false);

        when(orchestrationService.getRegisteredPlatforms())
                .thenReturn(Arrays.asList("huawei", "xiaomi", "wechat", "apple"));
    }

    @Nested
    @DisplayName("设备连接列表测试")
    class ConnectionListTests {

        @Test
        @DisplayName("DEV-INT-001: 获取设备连接列表 - 成功场景")
        void testGetConnections_Success() throws Exception {
            // Given: User has connected devices
            when(deviceConnectionService.getConnections(anyLong()))
                    .thenReturn(Collections.emptyList());

            // When: Request connection list
            // Then: Should return the list
            mockMvc.perform(get(API_BASE + "/connections"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());

            verify(deviceConnectionService).getConnections(anyLong());
        }

        @Test
        @DisplayName("DEV-INT-002: 获取设备连接列表 - 包含华为设备")
        void testGetConnections_WithHuaweiDevice() throws Exception {
            // Given: User has a connected Huawei device
            DeviceConnectionVO connection = new DeviceConnectionVO();
            connection.setPlatform("huawei");
            connection.setPlatformName("华为运动健康");
            connection.setStatus("connected");
            connection.setSyncEnabled(true);
            when(deviceConnectionService.getConnections(anyLong()))
                    .thenReturn(List.of(connection));

            // When: Request connection list
            // Then: Should include the Huawei device
            mockMvc.perform(get(API_BASE + "/connections"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].platform").value("huawei"))
                    .andExpect(jsonPath("$.data[0].status").value("connected"));
        }
    }

    @Nested
    @DisplayName("平台列表测试")
    class PlatformListTests {

        @Test
        @DisplayName("DEV-INT-003: 获取支持的平台列表 - 成功场景")
        void testGetPlatforms_Success() throws Exception {
            // When: Request platform list
            // Then: Should return all supported platforms
            mockMvc.perform(get(API_BASE + "/platforms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[*].platform").value(hasItems("huawei", "xiaomi", "wechat", "apple")));

            verify(orchestrationService).getRegisteredPlatforms();
        }

        @Test
        @DisplayName("DEV-INT-004: 平台配置状态 - 已配置返回true")
        void testPlatformConfiguredStatus_True() throws Exception {
            // When: Request platform list
            // Then: Configured platforms should show configured=true
            mockMvc.perform(get(API_BASE + "/platforms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            // Verify isPlatformConfigured was called
            verify(orchestrationService).isPlatformConfigured("huawei");
        }
    }

    @Nested
    @DisplayName("设备连接测试")
    class ConnectDeviceTests {

        @Test
        @DisplayName("DEV-INT-005: 连接设备 - 成功返回授权URL")
        void testConnectDevice_Success() throws Exception {
            // Given: Platform is registered and configured
            String authUrl = "https://oauth.huawei.com/authorize?client_id=test";
            when(orchestrationService.getAuthorizationUrl(anyLong(), eq("huawei")))
                    .thenReturn(authUrl);

            // When: Request to connect device
            // Then: Should return OAuth authorization URL
            mockMvc.perform(post(API_BASE + "/connect/huawei"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(authUrl));

            verify(orchestrationService).getAuthorizationUrl(anyLong(), eq("huawei"));
        }

        @Test
        @DisplayName("DEV-INT-006: 连接设备 - 未知平台返回错误")
        void testConnectDevice_UnknownPlatform() throws Exception {
            // Given: Unknown platform
            // When: Request to connect unknown platform
            // Then: Should return error
            mockMvc.perform(post(API_BASE + "/connect/unknown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("DEV-INT-007: 连接设备 - 未配置平台返回错误")
        void testConnectDevice_UnconfiguredPlatform() throws Exception {
            // Given: Platform is registered but not configured
            // When: Request to connect unconfigured platform
            // Then: Should return error
            mockMvc.perform(post(API_BASE + "/connect/xiaomi"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("DEV-INT-008: 连接设备 - 服务返回null授权URL")
        void testConnectDevice_NullAuthUrl() throws Exception {
            // Given: Service returns null auth URL
            when(orchestrationService.getAuthorizationUrl(anyLong(), eq("huawei")))
                    .thenReturn(null);

            // When: Request to connect device
            // Then: Should return error
            mockMvc.perform(post(API_BASE + "/connect/huawei"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Nested
    @DisplayName("OAuth回调测试")
    class OAuthCallbackTests {

        @Test
        @DisplayName("DEV-INT-009: OAuth回调 - 成功场景")
        void testHandleCallback_Success() throws Exception {
            // Given: Valid callback with code and state
            doNothing().when(orchestrationService)
                    .handleCallback(eq("huawei"), anyString(), anyString());

            // When: OAuth provider calls back
            // Then: Should return success page
            mockMvc.perform(get(API_BASE + "/callback/huawei")
                            .param("code", "test-auth-code")
                            .param("state", "test-state"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("授权成功")));

            verify(orchestrationService).handleCallback(eq("huawei"), eq("test-auth-code"), eq("test-state"));
        }

        @Test
        @DisplayName("DEV-INT-010: OAuth回调 - 缺少授权码返回错误页面")
        void testHandleCallback_MissingCode() throws Exception {
            // Given: Callback without authorization code
            // When: OAuth callback without code
            // Then: Should return error page
            mockMvc.perform(get(API_BASE + "/callback/huawei")
                            .param("state", "test-state"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("授权失败")));
        }

        @Test
        @DisplayName("DEV-INT-011: OAuth回调 - 缺少state返回错误页面")
        void testHandleCallback_MissingState() throws Exception {
            // Given: Callback without state
            // When: OAuth callback without state
            // Then: Should return error page
            mockMvc.perform(get(API_BASE + "/callback/huawei")
                            .param("code", "test-auth-code"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("授权失败")));
        }

        @Test
        @DisplayName("DEV-INT-012: OAuth回调 - OAuth错误返回错误页面")
        void testHandleCallback_OAuthError() throws Exception {
            // Given: OAuth provider returns error
            // When: OAuth callback with error
            // Then: Should return error page
            mockMvc.perform(get(API_BASE + "/callback/huawei")
                            .param("error", "access_denied")
                            .param("error_description", "User denied access"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("授权失败")));
        }

        @Test
        @DisplayName("DEV-INT-013: OAuth回调 - 未知平台返回错误页面")
        void testHandleCallback_UnknownPlatform() throws Exception {
            // Given: Callback for unknown platform
            // When: OAuth callback for unregistered platform
            // Then: Should return error page
            mockMvc.perform(get(API_BASE + "/callback/unknown")
                            .param("code", "test-auth-code")
                            .param("state", "test-state"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("授权失败")));
        }

        @Test
        @DisplayName("DEV-INT-014: OAuth回调 - 服务异常返回错误页面")
        void testHandleCallback_ServiceException() throws Exception {
            // Given: Service throws exception
            doThrow(new RuntimeException("Token exchange failed"))
                    .when(orchestrationService)
                    .handleCallback(anyString(), anyString(), anyString());

            // When: OAuth callback causes exception
            // Then: Should return error page
            mockMvc.perform(get(API_BASE + "/callback/huawei")
                            .param("code", "test-auth-code")
                            .param("state", "test-state"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("授权失败")));
        }
    }

    @Nested
    @DisplayName("断开设备测试")
    class DisconnectDeviceTests {

        @Test
        @DisplayName("DEV-INT-015: 断开设备 - 成功场景")
        void testDisconnect_Success() throws Exception {
            // Given: Device is connected
            when(deviceConnectionService.disconnect(anyLong(), eq("huawei")))
                    .thenReturn(true);

            // When: Request to disconnect device
            // Then: Should return success
            mockMvc.perform(delete(API_BASE + "/disconnect/huawei"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(deviceConnectionService).disconnect(anyLong(), eq("huawei"));
        }

        @Test
        @DisplayName("DEV-INT-016: 断开设备 - 失败返回错误")
        void testDisconnect_Failure() throws Exception {
            // Given: Disconnect fails
            when(deviceConnectionService.disconnect(anyLong(), eq("huawei")))
                    .thenReturn(false);

            // When: Request to disconnect device
            // Then: Should return error
            mockMvc.perform(delete(API_BASE + "/disconnect/huawei"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500));
        }
    }

    @Nested
    @DisplayName("设备同步测试")
    class SyncDeviceTests {

        @Test
        @DisplayName("DEV-INT-017: 同步单个设备 - 成功场景")
        void testSyncSingleDevice_Success() throws Exception {
            // Given: Device sync succeeds
            SyncResultVO successResult = SyncResultVO.success("huawei", 10, 100);
            when(orchestrationService.syncPlatform(anyLong(), eq("huawei")))
                    .thenReturn(successResult);

            // When: Request to sync device
            // Then: Should return sync result
            mockMvc.perform(post(API_BASE + "/sync/huawei"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("success"))
                    .andExpect(jsonPath("$.data.platform").value("huawei"))
                    .andExpect(jsonPath("$.data.metricsCount").value(10));

            verify(orchestrationService).syncPlatform(anyLong(), eq("huawei"));
        }

        @Test
        @DisplayName("DEV-INT-018: 同步单个设备 - 未知平台返回错误")
        void testSyncSingleDevice_UnknownPlatform() throws Exception {
            // Given: Unknown platform
            // When: Request to sync unknown platform
            // Then: Should return error
            mockMvc.perform(post(API_BASE + "/sync/unknown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("DEV-INT-019: 同步单个设备 - 同步失败返回失败状态")
        void testSyncSingleDevice_SyncFailed() throws Exception {
            // Given: Device sync fails
            SyncResultVO failedResult = SyncResultVO.failed("huawei", "Device not connected", 0);
            when(orchestrationService.syncPlatform(anyLong(), eq("huawei")))
                    .thenReturn(failedResult);

            // When: Request to sync device
            // Then: Should return failed status
            mockMvc.perform(post(API_BASE + "/sync/huawei"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("failed"));
        }

        @Test
        @DisplayName("DEV-INT-020: 批量同步所有设备 - 成功场景")
        void testSyncAllDevices_Success() throws Exception {
            // Given: Multiple devices to sync
            SyncResultVO result1 = SyncResultVO.success("huawei", 10, 100);
            SyncResultVO result2 = SyncResultVO.success("xiaomi", 5, 50);
            when(orchestrationService.syncAllPlatforms(anyLong()))
                    .thenReturn(Arrays.asList(result1, result2));

            // When: Request to sync all devices
            // Then: Should return all sync results
            mockMvc.perform(post(API_BASE + "/sync/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));

            verify(orchestrationService).syncAllPlatforms(anyLong());
        }
    }

    @Nested
    @DisplayName("同步历史测试")
    class SyncHistoryTests {

        @Test
        @DisplayName("DEV-INT-021: 获取同步历史 - 成功场景")
        void testGetSyncHistory_Success() throws Exception {
            // Given: Sync history exists
            Page<SyncHistoryVO> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0);
            when(syncHistoryService.getHistory(anyLong(), eq(1), eq(10)))
                    .thenReturn(mockPage);

            // When: Request sync history
            // Then: Should return paginated history
            mockMvc.perform(get(API_BASE + "/sync/history")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").exists());

            verify(syncHistoryService).getHistory(anyLong(), eq(1), eq(10));
        }
    }
}