package com.hhs.service;

import com.hhs.service.impl.DeviceSyncOrchestrationServiceImpl;
import com.hhs.vo.DeviceConnectionVO;
import com.hhs.vo.SyncResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Device Sync Orchestration Service Unit Tests
 *
 * Tests the orchestration layer that coordinates synchronization
 * across multiple wearable device platforms.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备同步编排服务测试")
class DeviceSyncOrchestrationServiceTest {

    @Mock
    private DeviceConnectionService connectionService;

    @Mock
    private DevicePlatformService huaweiService;

    @Mock
    private DevicePlatformService xiaomiService;

    // Service is created manually in each test via createService()
    // because the constructor requires a List of platform services

    private Long testUserId;
    private DeviceConnectionVO huaweiConnection;
    private DeviceConnectionVO xiaomiConnection;

    @BeforeEach
    void setUp() {
        testUserId = 1L;

        // Setup Huawei mock service
        lenient().when(huaweiService.getPlatform()).thenReturn("huawei");

        // Setup Xiaomi mock service
        lenient().when(xiaomiService.getPlatform()).thenReturn("xiaomi");

        // Create test connections
        huaweiConnection = createConnectionVO("huawei", "connected", true);
        xiaomiConnection = createConnectionVO("xiaomi", "connected", true);
    }

    private DeviceConnectionVO createConnectionVO(String platform, String status, Boolean syncEnabled) {
        DeviceConnectionVO vo = new DeviceConnectionVO();
        vo.setPlatform(platform);
        vo.setStatus(status);
        vo.setSyncEnabled(syncEnabled);
        return vo;
    }

    /**
     * Create orchestration service with specific platform services.
     */
    private DeviceSyncOrchestrationServiceImpl createService(List<DevicePlatformService> services) {
        return new DeviceSyncOrchestrationServiceImpl(services, connectionService);
    }

    @Nested
    @DisplayName("平台注册测试")
    class PlatformRegistrationTests {

        @Test
        @DisplayName("测试1.1：构造函数注册多个平台")
        void testConstructor_RegistersMultiplePlatforms() {
            // Given: Multiple platform services
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);

            // When: Create orchestration service
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // Then: Both platforms should be registered
            assertTrue(service.isPlatformRegistered("huawei"));
            assertTrue(service.isPlatformRegistered("xiaomi"));
            assertEquals(2, service.getRegisteredPlatforms().size());
        }

        @Test
        @DisplayName("测试1.2：构造函数处理空服务列表")
        void testConstructor_EmptyServiceList() {
            // Given: Empty service list
            List<DevicePlatformService> services = new ArrayList<>();

            // When: Create orchestration service
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // Then: No platforms should be registered
            assertTrue(service.getRegisteredPlatforms().isEmpty());
        }

        @Test
        @DisplayName("测试1.3：getRegisteredPlatforms返回所有已注册平台")
        void testGetRegisteredPlatforms_ReturnsAllPlatforms() {
            // Given: Service with multiple platforms
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Get registered platforms
            List<String> platforms = service.getRegisteredPlatforms();

            // Then: Should contain all platforms
            assertEquals(2, platforms.size());
            assertTrue(platforms.contains("huawei"));
            assertTrue(platforms.contains("xiaomi"));
        }

        @Test
        @DisplayName("测试1.4：平台标识不区分大小写")
        void testPlatformIdentifier_CaseInsensitive() {
            // Given: Service with Huawei platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Check with different cases
            boolean result1 = service.isPlatformRegistered("HUAWEI");
            boolean result2 = service.isPlatformRegistered("Huawei");
            boolean result3 = service.isPlatformRegistered("huawei");

            // Then: All should return true
            assertTrue(result1);
            assertTrue(result2);
            assertTrue(result3);
        }
    }

    @Nested
    @DisplayName("平台查找测试")
    class PlatformLookupTests {

        @Test
        @DisplayName("测试2.1：isPlatformRegistered - 已注册返回true")
        void testIsPlatformRegistered_Registered() {
            // Given: Service with Huawei platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Check if registered
            boolean result = service.isPlatformRegistered("huawei");

            // Then: Should return true
            assertTrue(result);
        }

        @Test
        @DisplayName("测试2.2：isPlatformRegistered - 未注册返回false")
        void testIsPlatformRegistered_NotRegistered() {
            // Given: Service without the platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Check if registered
            boolean result = service.isPlatformRegistered("apple");

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试2.3：isPlatformRegistered - null返回false")
        void testIsPlatformRegistered_Null() {
            // Given: Service with platforms
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Check with null
            boolean result = service.isPlatformRegistered(null);

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试2.4：isPlatformRegistered - 空字符串返回false")
        void testIsPlatformRegistered_Empty() {
            // Given: Service with platforms
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Check with empty string
            boolean result = service.isPlatformRegistered("");

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试2.5：isPlatformConfigured - 已配置返回true")
        void testIsPlatformConfigured_Configured() {
            // Given: Service with configured platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(true);

            // When: Check if configured
            boolean result = service.isPlatformConfigured("huawei");

            // Then: Should return true
            assertTrue(result);
        }

        @Test
        @DisplayName("测试2.6：isPlatformConfigured - 未配置返回false")
        void testIsPlatformConfigured_NotConfigured() {
            // Given: Service with unconfigured platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(false);

            // When: Check if configured
            boolean result = service.isPlatformConfigured("huawei");

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试2.7：isPlatformConfigured - 未注册返回false")
        void testIsPlatformConfigured_NotRegistered() {
            // Given: Service without the platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Check if configured
            boolean result = service.isPlatformConfigured("apple");

            // Then: Should return false
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("单平台同步测试")
    class SyncPlatformTests {

        @Test
        @DisplayName("测试3.1：syncPlatform - 成功同步")
        void testSyncPlatform_Success() {
            // Given: Registered and configured platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(true);

            SyncResultVO successResult = SyncResultVO.success("huawei", 10, 100);
            when(huaweiService.syncData(testUserId)).thenReturn(successResult);

            // When: Sync platform
            SyncResultVO result = service.syncPlatform(testUserId, "huawei");

            // Then: Should return success result
            assertEquals("success", result.getStatus());
            assertEquals("huawei", result.getPlatform());
            verify(huaweiService).syncData(testUserId);
        }

        @Test
        @DisplayName("测试3.2：syncPlatform - 平台未注册返回失败")
        void testSyncPlatform_NotRegistered() {
            // Given: Service without the platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Sync unknown platform
            SyncResultVO result = service.syncPlatform(testUserId, "unknown");

            // Then: Should return failed result
            assertEquals("failed", result.getStatus());
            assertTrue(result.getErrorMessage().contains("Unknown platform"));
        }

        @Test
        @DisplayName("测试3.3：syncPlatform - 平台未配置返回失败")
        void testSyncPlatform_NotConfigured() {
            // Given: Registered but unconfigured platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(false);

            // When: Sync platform
            SyncResultVO result = service.syncPlatform(testUserId, "huawei");

            // Then: Should return failed result
            assertEquals("failed", result.getStatus());
            assertTrue(result.getErrorMessage().contains("not configured"));
        }

        @Test
        @DisplayName("测试3.4：syncPlatform - 同步异常返回失败")
        void testSyncPlatform_Exception() {
            // Given: Platform that throws exception
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(true);
            when(huaweiService.syncData(testUserId)).thenThrow(new RuntimeException("Sync error"));

            // When: Sync platform
            SyncResultVO result = service.syncPlatform(testUserId, "huawei");

            // Then: Should return failed result
            assertEquals("failed", result.getStatus());
            assertTrue(result.getErrorMessage().contains("Sync error"));
        }

        @Test
        @DisplayName("测试3.5：syncPlatform - 委托到正确的平台服务")
        void testSyncPlatform_DelegatesToCorrectService() {
            // Given: Multiple platforms
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(huaweiService.isConfigured()).thenReturn(true);
            when(xiaomiService.isConfigured()).thenReturn(true);

            SyncResultVO huaweiResult = SyncResultVO.success("huawei", 5, 50);
            SyncResultVO xiaomiResult = SyncResultVO.success("xiaomi", 8, 80);

            when(huaweiService.syncData(testUserId)).thenReturn(huaweiResult);
            when(xiaomiService.syncData(testUserId)).thenReturn(xiaomiResult);

            // When: Sync each platform
            SyncResultVO result1 = service.syncPlatform(testUserId, "huawei");
            SyncResultVO result2 = service.syncPlatform(testUserId, "xiaomi");

            // Then: Should delegate to correct service
            assertEquals(5, result1.getMetricsCount());
            assertEquals(8, result2.getMetricsCount());
            verify(huaweiService).syncData(testUserId);
            verify(xiaomiService).syncData(testUserId);
        }
    }

    @Nested
    @DisplayName("全平台同步测试")
    class SyncAllPlatformsTests {

        @Test
        @DisplayName("测试4.1：syncAllPlatforms - 同步所有已连接平台")
        void testSyncAllPlatforms_AllConnected() {
            // Given: Multiple connected platforms
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(huaweiConnection, xiaomiConnection));

            when(huaweiService.isConfigured()).thenReturn(true);
            when(xiaomiService.isConfigured()).thenReturn(true);

            when(huaweiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.success("huawei", 5, 50));
            when(xiaomiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.success("xiaomi", 8, 80));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Should sync all platforms
            assertEquals(2, results.size());
            verify(huaweiService).syncData(testUserId);
            verify(xiaomiService).syncData(testUserId);
        }

        @Test
        @DisplayName("测试4.2：syncAllPlatforms - 一个失败不影响其他")
        void testSyncAllPlatforms_ContinueOnError() {
            // Given: One platform fails
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(huaweiConnection, xiaomiConnection));

            when(huaweiService.isConfigured()).thenReturn(true);
            when(xiaomiService.isConfigured()).thenReturn(true);

            when(huaweiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.failed("huawei", "Error", 0));
            when(xiaomiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.success("xiaomi", 8, 80));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Both should be attempted
            assertEquals(2, results.size());
            verify(huaweiService).syncData(testUserId);
            verify(xiaomiService).syncData(testUserId);
        }

        @Test
        @DisplayName("测试4.3：syncAllPlatforms - 跳过同步禁用的平台")
        void testSyncAllPlatforms_SkipDisabledSync() {
            // Given: One platform with sync disabled
            DeviceConnectionVO disabledConnection = createConnectionVO("huawei", "connected", false);

            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(disabledConnection, xiaomiConnection));

            when(xiaomiService.isConfigured()).thenReturn(true);
            when(xiaomiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.success("xiaomi", 8, 80));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Only enabled platform should be synced
            assertEquals(1, results.size());
            assertEquals("xiaomi", results.get(0).getPlatform());
            verify(huaweiService, never()).syncData(any());
            verify(xiaomiService).syncData(testUserId);
        }

        @Test
        @DisplayName("测试4.4：syncAllPlatforms - 跳过未配置的平台")
        void testSyncAllPlatforms_SkipUnconfigured() {
            // Given: One platform not configured
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(huaweiConnection, xiaomiConnection));

            when(huaweiService.isConfigured()).thenReturn(false);
            when(xiaomiService.isConfigured()).thenReturn(true);
            when(xiaomiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.success("xiaomi", 8, 80));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Unconfigured should return failed result
            assertEquals(2, results.size());
            verify(huaweiService, never()).syncData(any());
            verify(xiaomiService).syncData(testUserId);
        }

        @Test
        @DisplayName("测试4.5：syncAllPlatforms - 处理无连接情况")
        void testSyncAllPlatforms_NoConnections() {
            // Given: No connections
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of());

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Should return empty list
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("测试4.6：syncAllPlatforms - 处理重复连接")
        void testSyncAllPlatforms_DuplicateConnections() {
            // Given: Duplicate connections for same platform
            DeviceConnectionVO dupeConnection = createConnectionVO("huawei", "connected", true);

            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(huaweiConnection, dupeConnection));

            when(huaweiService.isConfigured()).thenReturn(true);
            when(huaweiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.success("huawei", 5, 50));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Should only sync once
            assertEquals(1, results.size());
            verify(huaweiService, times(1)).syncData(testUserId);
        }

        @Test
        @DisplayName("测试4.7：syncAllPlatforms - 处理服务未注册情况")
        void testSyncAllPlatforms_ServiceNotRegistered() {
            // Given: Connection for platform without registered service
            DeviceConnectionVO unknownConnection = createConnectionVO("apple", "connected", true);

            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(unknownConnection));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Should return failed result
            assertEquals(1, results.size());
            assertEquals("failed", results.get(0).getStatus());
            assertTrue(results.get(0).getErrorMessage().contains("not available"));
        }

        @Test
        @DisplayName("测试4.8：syncAllPlatforms - 异常聚合")
        void testSyncAllPlatforms_ExceptionAggregation() {
            // Given: One platform throws exception
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(huaweiConnection, xiaomiConnection));

            when(huaweiService.isConfigured()).thenReturn(true);
            when(xiaomiService.isConfigured()).thenReturn(true);

            when(huaweiService.syncData(testUserId))
                    .thenThrow(new RuntimeException("Network error"));
            when(xiaomiService.syncData(testUserId))
                    .thenReturn(SyncResultVO.success("xiaomi", 8, 80));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Should still sync other platforms
            assertEquals(2, results.size());
            verify(huaweiService).syncData(testUserId);
            verify(xiaomiService).syncData(testUserId);
        }
    }

    @Nested
    @DisplayName("OAuth授权URL测试")
    class AuthorizationUrlTests {

        @Test
        @DisplayName("测试5.1：getAuthorizationUrl - 成功获取")
        void testGetAuthorizationUrl_Success() {
            // Given: Configured platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(true);
            when(huaweiService.getAuthorizationUrl(testUserId))
                    .thenReturn("https://oauth.huawei.com/authorize?...");

            // When: Get authorization URL
            String url = service.getAuthorizationUrl(testUserId, "huawei");

            // Then: Should return URL
            assertNotNull(url);
            assertTrue(url.startsWith("https://"));
        }

        @Test
        @DisplayName("测试5.2：getAuthorizationUrl - 平台未注册返回null")
        void testGetAuthorizationUrl_NotRegistered() {
            // Given: Platform not registered
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Get authorization URL
            String url = service.getAuthorizationUrl(testUserId, "unknown");

            // Then: Should return null
            assertNull(url);
        }

        @Test
        @DisplayName("测试5.3：getAuthorizationUrl - 平台未配置返回null")
        void testGetAuthorizationUrl_NotConfigured() {
            // Given: Platform not configured
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(false);

            // When: Get authorization URL
            String url = service.getAuthorizationUrl(testUserId, "huawei");

            // Then: Should return null
            assertNull(url);
        }
    }

    @Nested
    @DisplayName("OAuth回调处理测试")
    class CallbackHandlingTests {

        @Test
        @DisplayName("测试6.1：handleCallback - 成功处理")
        void testHandleCallback_Success() {
            // Given: Registered platform
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Handle callback
            service.handleCallback("huawei", "test-code", "test-state");

            // Then: Should delegate to platform service
            verify(huaweiService).handleCallback("test-code", "test-state");
        }

        @Test
        @DisplayName("测试6.2：handleCallback - 平台未注册抛出异常")
        void testHandleCallback_NotRegistered() {
            // Given: Platform not registered
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When & Then: Should throw exception
            assertThrows(IllegalArgumentException.class,
                    () -> service.handleCallback("unknown", "test-code", "test-state"));
        }

        @Test
        @DisplayName("测试6.3：handleCallback - 委托到正确平台")
        void testHandleCallback_DelegatesToCorrectPlatform() {
            // Given: Multiple platforms
            List<DevicePlatformService> services = List.of(huaweiService, xiaomiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Handle callback for each platform
            service.handleCallback("huawei", "code1", "state1");
            service.handleCallback("xiaomi", "code2", "state2");

            // Then: Should delegate to correct service
            verify(huaweiService).handleCallback("code1", "state1");
            verify(xiaomiService).handleCallback("code2", "state2");
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("测试7.1：平台标识包含空格")
        void testPlatformIdentifier_WithSpaces() {
            // Given: Service with platforms
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            // When: Check with space
            boolean result = service.isPlatformRegistered(" huawei ");

            // Then: Should return false (no trim)
            assertFalse(result);
        }

        @Test
        @DisplayName("测试7.2：null用户ID处理")
        void testNullUserId() {
            // Given: Service with platforms
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(huaweiService.isConfigured()).thenReturn(true);
            when(huaweiService.syncData(null)).thenReturn(SyncResultVO.success("huawei", 0, 0));

            // When: Sync with null userId
            SyncResultVO result = service.syncPlatform(null, "huawei");

            // Then: Should handle gracefully (delegates to platform)
            assertNotNull(result);
        }

        @Test
        @DisplayName("测试7.3：空连接列表处理")
        void testEmptyConnectionList() {
            // Given: Empty connection list
            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);
            when(connectionService.getConnections(testUserId)).thenReturn(null);

            // When: Sync all platforms
            assertThrows(NullPointerException.class, () -> service.syncAllPlatforms(testUserId));
        }

        @Test
        @DisplayName("测试7.4：syncEnabled为null时跳过")
        void testSyncEnabledNull() {
            // Given: Connection with null syncEnabled
            DeviceConnectionVO nullSyncConnection = createConnectionVO("huawei", "connected", null);

            List<DevicePlatformService> services = List.of(huaweiService);
            DeviceSyncOrchestrationServiceImpl service = createService(services);

            when(connectionService.getConnections(testUserId))
                    .thenReturn(List.of(nullSyncConnection));

            // When: Sync all platforms
            List<SyncResultVO> results = service.syncAllPlatforms(testUserId);

            // Then: Should skip (null syncEnabled is treated as false)
            assertTrue(results.isEmpty());
        }
    }
}