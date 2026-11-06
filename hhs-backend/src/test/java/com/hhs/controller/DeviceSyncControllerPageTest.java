package com.hhs.controller;

import com.hhs.component.DeviceSyncRateLimiter;
import com.hhs.config.DeviceMockProperties;
import com.hhs.service.DeviceConnectionService;
import com.hhs.service.DeviceSyncOrchestrationService;
import com.hhs.service.SyncHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Device Sync Controller OAuth callback page tests.
 *
 * <p>Verifies that the HTML error pages returned by the OAuth callback endpoint
 * never echo raw provider or platform text back to the browser. These tests use
 * plain Mockito mocks and call the controller's public methods directly, so they
 * run deterministically without Docker or a Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备同步控制器回调页测试")
class DeviceSyncControllerPageTest {

    @Mock
    private DeviceConnectionService deviceConnectionService;

    @Mock
    private SyncHistoryService syncHistoryService;

    @Mock
    private DeviceSyncOrchestrationService orchestrationService;

    @Mock
    private DeviceSyncRateLimiter rateLimiter;

    @Mock
    private DeviceMockProperties deviceMockProperties;

    private DeviceSyncController controller;

    @BeforeEach
    void setUp() {
        controller = new DeviceSyncController(
                deviceConnectionService,
                syncHistoryService,
                orchestrationService,
                rateLimiter,
                deviceMockProperties);
    }

    @Test
    @DisplayName("用例A：未知平台回调页只显示中文说明，不回显原始平台标识")
    void unknownPlatform_ShowsChineseOnly() {
        when(orchestrationService.isPlatformRegistered("unknown")).thenReturn(false);

        String page = controller.handleCallback("unknown", "code", "state", null, null);

        assertTrue(page.contains("授权失败"), "页面标题应为授权失败");
        assertTrue(page.contains("暂不支持该设备平台，请返回应用重新选择"), "应显示固定中文说明");
        assertFalse(page.contains("unknown"), "不得回显原始 platform 字符串");
    }

    @Test
    @DisplayName("用例B：授权成功页仍正常返回")
    void successfulCallback_ReturnsSuccessPage() {
        when(orchestrationService.isPlatformRegistered("huawei")).thenReturn(true);

        String page = controller.handleCallback("huawei", "code", "state", null, null);

        assertTrue(page.contains("授权成功"), "页面应显示授权成功");
    }

    @Test
    @DisplayName("用例C：缺少授权码时显示固定中文说明")
    void missingCode_ShowsChineseOnly() {
        String page = controller.handleCallback("huawei", null, "state", null, null);

        assertTrue(page.contains("授权失败"), "页面标题应为授权失败");
        assertTrue(page.contains("授权回调缺少必要参数，请重新发起授权"), "应显示固定中文说明");
    }

    @Test
    @DisplayName("用例D：缺少state时显示固定中文说明")
    void missingState_ShowsChineseOnly() {
        String page = controller.handleCallback("huawei", "code", null, null, null);

        assertTrue(page.contains("授权失败"), "页面标题应为授权失败");
        assertTrue(page.contains("授权回调缺少校验信息，请重新发起授权"), "应显示固定中文说明");
    }

    @Test
    @DisplayName("用例E：provider 报错时不回显英文 error_description")
    void providerError_DoesNotEchoRawDetail() {
        String page = controller.handleCallback("huawei", null, null, "access_denied", "User denied access");

        assertTrue(page.contains("授权失败"), "页面标题应为授权失败");
        assertTrue(page.contains("授权未完成，请返回应用重新发起授权"), "应显示固定中文说明");
        assertFalse(page.contains("User denied access"), "不得回显 provider 原始错误描述");
    }

    @Test
    @DisplayName("用例F：服务异常时不回显底层异常原文")
    void serviceException_DoesNotEchoRawDetail() {
        when(orchestrationService.isPlatformRegistered("huawei")).thenReturn(true);
        doThrow(new RuntimeException("RAW_PROVIDER_DETAIL"))
                .when(orchestrationService)
                .handleCallback(anyString(), anyString(), anyString());

        String page = controller.handleCallback("huawei", "code", "state", null, null);

        assertTrue(page.contains("授权失败"), "页面标题应为授权失败");
        assertTrue(page.contains("授权过程中出现问题，请稍后重试或重新发起授权"), "应显示固定中文说明");
        assertFalse(page.contains("RAW_PROVIDER_DETAIL"), "不得回显底层异常原文");
    }
}