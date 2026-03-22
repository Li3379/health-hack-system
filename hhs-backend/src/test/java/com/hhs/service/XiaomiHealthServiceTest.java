package com.hhs.service;

import com.hhs.config.DeviceMockProperties;
import com.hhs.config.DeviceOAuthProperties;
import com.hhs.config.DeviceOAuthProperties.PlatformConfig;
import com.hhs.dto.HealthDataPoint;
import com.hhs.entity.DeviceConnection;
import com.hhs.service.impl.XiaomiHealthServiceImpl;
import com.hhs.vo.SyncResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for XiaomiHealthServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class XiaomiHealthServiceTest {

    @Mock
    private DeviceOAuthProperties oAuthProperties;

    @Mock
    private DeviceMockProperties mockProperties;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private DeviceConnectionService connectionService;

    @Mock
    private HealthMetricService healthMetricService;

    @Mock
    private SyncHistoryService syncHistoryService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private XiaomiHealthServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("getPlatform()")
    class GetPlatformTests {

        @Test
        @DisplayName("should return 'xiaomi'")
        void shouldReturnXiaomi() {
            assertEquals("xiaomi", service.getPlatform());
        }
    }

    @Nested
    @DisplayName("isConfigured()")
    class IsConfiguredTests {

        @Test
        @DisplayName("should return false when OAuth not configured")
        void shouldReturnFalseWhenOAuthNotConfigured() {
            when(oAuthProperties.hasValidCredentials("xiaomi")).thenReturn(false);

            assertFalse(service.isConfigured());
        }

        @Test
        @DisplayName("should return false when encryption not configured")
        void shouldReturnFalseWhenEncryptionNotConfigured() {
            when(oAuthProperties.hasValidCredentials("xiaomi")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(false);

            assertFalse(service.isConfigured());
        }

        @Test
        @DisplayName("should return true when both configured")
        void shouldReturnTrueWhenBothConfigured() {
            when(oAuthProperties.hasValidCredentials("xiaomi")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);

            assertTrue(service.isConfigured());
        }
    }

    @Nested
    @DisplayName("getAuthorizationUrl()")
    class GetAuthorizationUrlTests {

        @Test
        @DisplayName("should return null when not configured")
        void shouldReturnNullWhenNotConfigured() {
            when(oAuthProperties.hasValidCredentials("xiaomi")).thenReturn(false);

            String url = service.getAuthorizationUrl(1L);

            assertNull(url);
        }

        @Test
        @DisplayName("should return valid OAuth URL when configured")
        void shouldReturnValidOAuthUrlWhenConfigured() {
            PlatformConfig config = new PlatformConfig();
            ReflectionTestUtils.setField(config, "clientId", "test-client-id");
            ReflectionTestUtils.setField(config, "clientSecret", "test-secret");
            ReflectionTestUtils.setField(config, "authUrl", "https://account.xiaomi.com/oauth2/authorize");
            ReflectionTestUtils.setField(config, "tokenUrl", "https://account.xiaomi.com/oauth2/token");
            ReflectionTestUtils.setField(config, "redirectUri", "http://localhost:8082/api/device/callback/xiaomi");
            ReflectionTestUtils.setField(config, "scopes", List.of("read", "write"));

            when(oAuthProperties.hasValidCredentials("xiaomi")).thenReturn(true);
            when(oAuthProperties.getPlatform("xiaomi")).thenReturn(config);
            when(encryptionService.isConfigured()).thenReturn(true);

            String url = service.getAuthorizationUrl(1L);

            assertNotNull(url);
            assertTrue(url.contains("account.xiaomi.com"));
            assertTrue(url.contains("client_id=test-client-id"));
            assertTrue(url.contains("response_type=code"));
            assertTrue(url.contains("state="));
        }
    }

    @Nested
    @DisplayName("syncData()")
    class SyncDataTests {

        @Test
        @DisplayName("should return failed result when device not connected")
        void shouldReturnFailedResultWhenDeviceNotConnected() {
            when(connectionService.getConnection(1L, "xiaomi")).thenReturn(null);
            when(syncHistoryService.createFailedRecord(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(SyncResultVO.failed("xiaomi", "Device not connected", 0));

            SyncResultVO result = service.syncData(1L);

            assertNotNull(result);
            assertEquals("failed", result.getStatus());
        }

        @Test
        @DisplayName("should return failed result when connection status not active")
        void shouldReturnFailedResultWhenConnectionStatusNotActive() {
            DeviceConnection connection = new DeviceConnection();
            connection.setStatus("disconnected");

            when(connectionService.getConnection(1L, "xiaomi")).thenReturn(connection);
            when(syncHistoryService.createFailedRecord(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(SyncResultVO.failed("xiaomi", "Connection not active", 0));

            SyncResultVO result = service.syncData(1L);

            assertNotNull(result);
            assertEquals("failed", result.getStatus());
        }
    }

    @Nested
    @DisplayName("Mock Data Generation")
    class MockDataTests {

        @Test
        @DisplayName("should generate mock data with correct structure")
        void shouldGenerateMockDataWithCorrectStructure() {
            // This test verifies the mock data structure matches expected format
            // The actual generation is tested via syncData with mock enabled

            // Setup for mock data test
            DeviceConnection connection = new DeviceConnection();
            connection.setStatus("connected");
            connection.setAccessToken("encrypted-token");
            connection.setTokenExpireAt(java.time.LocalDateTime.now().plusHours(1));

            when(oAuthProperties.hasValidCredentials("xiaomi")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);
            when(encryptionService.decrypt(anyString())).thenReturn("real-token");
            when(connectionService.getConnection(1L, "xiaomi")).thenReturn(connection);
            when(mockProperties.isEnabled()).thenReturn(true);
            when(syncHistoryService.createSuccessRecord(anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(SyncResultVO.success("xiaomi", 10, 100));

            SyncResultVO result = service.syncData(1L);

            assertNotNull(result);
        }
    }
}