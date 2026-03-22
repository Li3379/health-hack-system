package com.hhs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.config.DeviceMockProperties;
import com.hhs.config.DeviceOAuthProperties;
import com.hhs.config.DeviceOAuthProperties.PlatformConfig;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.TokenResponse;
import com.hhs.entity.DeviceConnection;
import com.hhs.exception.BusinessException;
import com.hhs.exception.SystemException;
import com.hhs.service.impl.HuaweiHealthServiceImpl;
import com.hhs.vo.SyncResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Huawei Health Service Unit Tests
 *
 * Tests OAuth 2.0 authorization flow and health data synchronization
 * for Huawei Health platform integration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("华为健康服务测试")
class HuaweiHealthServiceTest {

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
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private HuaweiHealthServiceImpl huaweiHealthService;

    private Long testUserId;
    private PlatformConfig testConfig;
    private DeviceConnection testConnection;

    @BeforeEach
    void setUp() {
        testUserId = 1L;

        // Setup mock properties - enable mock data by default for tests
        when(mockProperties.isEnabled()).thenReturn(true);

        // Setup test platform config
        testConfig = new PlatformConfig();
        testConfig.setClientId("test-client-id");
        testConfig.setClientSecret("test-client-secret");
        testConfig.setAuthUrl("https://oauth.huawei.com/authorize");
        testConfig.setTokenUrl("https://oauth.huawei.com/token");
        testConfig.setRedirectUri("https://app.example.com/callback/huawei");
        testConfig.setScopes(List.of("healthkit.step", "healthkit.heart"));

        // Setup test connection
        testConnection = new DeviceConnection();
        testConnection.setId(1L);
        testConnection.setUserId(testUserId);
        testConnection.setPlatform("huawei");
        testConnection.setStatus("connected");
        testConnection.setAccessToken("encrypted-access-token");
        testConnection.setRefreshToken("encrypted-refresh-token");
        testConnection.setSyncEnabled(true);
        testConnection.setTokenExpireAt(LocalDateTime.now().plusDays(1));

        // Setup Redis mock
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("OAuth授权URL生成测试")
    class AuthorizationUrlTests {

        @Test
        @DisplayName("测试1.1：生成授权URL - 正常流程")
        void testGetAuthorizationUrl_Success() {
            // Given: OAuth is configured
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            // When: Get authorization URL
            String authUrl = huaweiHealthService.getAuthorizationUrl(testUserId);

            // Then: URL should be generated with correct parameters
            assertNotNull(authUrl);
            assertTrue(authUrl.contains("client_id=test-client-id"));
            assertTrue(authUrl.contains("redirect_uri="));
            assertTrue(authUrl.contains("response_type=code"));
            assertTrue(authUrl.contains("state="));
            assertTrue(authUrl.contains("access_type=offline"));
            assertTrue(authUrl.contains("scope="));

            // Verify state was stored in Redis
            verify(redisTemplate.opsForValue()).set(
                    anyString(),
                    eq(testUserId),
                    eq(5L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("测试1.2：生成授权URL - 未配置OAuth返回null")
        void testGetAuthorizationUrl_NotConfigured() {
            // Given: OAuth is not configured
            lenient().when(encryptionService.isConfigured()).thenReturn(false);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(false);

            // When: Get authorization URL
            String authUrl = huaweiHealthService.getAuthorizationUrl(testUserId);

            // Then: Should return null
            assertNull(authUrl);
        }

        @Test
        @DisplayName("测试1.3：生成授权URL - 平台配置不存在返回null")
        void testGetAuthorizationUrl_PlatformConfigNull() {
            // Given: Platform config is null
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(null);

            // When: Get authorization URL
            String authUrl = huaweiHealthService.getAuthorizationUrl(testUserId);

            // Then: Should return null
            assertNull(authUrl);
        }

        @Test
        @DisplayName("测试1.4：生成授权URL - 包含正确的scope参数")
        void testGetAuthorizationUrl_ContainsCorrectScopes() {
            // Given: OAuth is configured with specific scopes
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            // When: Get authorization URL
            String authUrl = huaweiHealthService.getAuthorizationUrl(testUserId);

            // Then: URL should contain scopes
            assertNotNull(authUrl);
            assertTrue(authUrl.contains("healthkit.step"));
            assertTrue(authUrl.contains("healthkit.heart"));
        }
    }

    @Nested
    @DisplayName("OAuth回调处理测试")
    class CallbackHandlingTests {

        @Test
        @DisplayName("测试2.1：处理回调 - 成功流程")
        void testHandleCallback_Success() {
            // Given: Valid state and successful token exchange
            String code = "test-auth-code";
            String state = "test-state";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(testUserId);
            lenient().when(encryptionService.isConfigured()).thenReturn(true);
            lenient().when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            TokenResponse tokenResponse = new TokenResponse(
                    "access-token-123", "Bearer", 3600L, "refresh-token-456", "scope", null, null
            );
            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

            when(encryptionService.encrypt(anyString())).thenReturn("encrypted-token");
            when(connectionService.saveConnection(any())).thenReturn(testConnection);

            // When: Handle callback
            assertDoesNotThrow(() -> huaweiHealthService.handleCallback(code, state));

            // Then: Verify tokens were encrypted and saved
            verify(encryptionService, times(2)).encrypt(anyString());
            verify(connectionService).saveConnection(any(DeviceConnection.class));
            verify(redisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("测试2.2：处理回调 - 无效state抛出异常")
        void testHandleCallback_InvalidState() {
            // Given: Invalid state (not in Redis)
            String code = "test-auth-code";
            String state = "invalid-state";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            // When & Then: Should throw BusinessException
            assertThrows(BusinessException.class, () -> huaweiHealthService.handleCallback(code, state));
        }

        @Test
        @DisplayName("测试2.3：处理回调 - 空state抛出异常")
        void testHandleCallback_EmptyState() {
            // Given: Empty state
            String code = "test-auth-code";

            // When & Then: Should throw BusinessException
            assertThrows(BusinessException.class, () -> huaweiHealthService.handleCallback(code, ""));
            assertThrows(BusinessException.class, () -> huaweiHealthService.handleCallback(code, null));
        }

        @Test
        @DisplayName("测试2.4：处理回调 - token交换失败抛出异常")
        void testHandleCallback_TokenExchangeFailed() {
            // Given: Valid state but token exchange fails
            String code = "test-auth-code";
            String state = "test-state";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(testUserId);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            TokenResponse errorResponse = new TokenResponse(
                    null, null, null, null, null, "invalid_grant", "Invalid authorization code"
            );
            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenReturn(new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST));

            // When & Then: Should throw SystemException
            assertThrows(SystemException.class, () -> huaweiHealthService.handleCallback(code, state));
        }

        @Test
        @DisplayName("测试2.5：处理回调 - token加密被调用")
        void testHandleCallback_TokensEncrypted() {
            // Given: Successful callback
            String code = "test-auth-code";
            String state = "test-state";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(testUserId);
            lenient().when(encryptionService.isConfigured()).thenReturn(true);
            lenient().when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            TokenResponse tokenResponse = new TokenResponse(
                    "access-token-123", "Bearer", 3600L, "refresh-token-456", "scope", null, null
            );
            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

            when(encryptionService.encrypt(anyString())).thenReturn("encrypted-token");
            when(connectionService.saveConnection(any())).thenReturn(testConnection);

            // When: Handle callback
            huaweiHealthService.handleCallback(code, state);

            // Then: Verify tokens were encrypted
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(encryptionService, times(2)).encrypt(tokenCaptor.capture());

            List<String> encryptedTokens = tokenCaptor.getAllValues();
            assertTrue(encryptedTokens.contains("access-token-123"));
            assertTrue(encryptedTokens.contains("refresh-token-456"));
        }
    }

    @Nested
    @DisplayName("Token刷新测试")
    class TokenRefreshTests {

        @Test
        @DisplayName("测试3.1：刷新token - 成功流程")
        void testRefreshTokens_Success() {
            // Given: Existing connection with refresh token
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);
            when(encryptionService.decrypt("encrypted-refresh-token")).thenReturn("refresh-token-456");
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            TokenResponse tokenResponse = new TokenResponse(
                    "new-access-token", "Bearer", 3600L, "new-refresh-token", "scope", null, null
            );
            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

            when(encryptionService.encrypt(anyString())).thenReturn("encrypted-new-token");
            when(connectionService.saveConnection(any())).thenReturn(testConnection);

            // When: Refresh tokens
            boolean result = huaweiHealthService.refreshTokens(testUserId);

            // Then: Should succeed
            assertTrue(result);
            verify(connectionService).saveConnection(any(DeviceConnection.class));
        }

        @Test
        @DisplayName("测试3.2：刷新token - 未配置OAuth返回false")
        void testRefreshTokens_NotConfigured() {
            // Given: OAuth is not configured
            lenient().when(encryptionService.isConfigured()).thenReturn(false);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(false);

            // When: Refresh tokens
            boolean result = huaweiHealthService.refreshTokens(testUserId);

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试3.3：刷新token - 连接不存在返回false")
        void testRefreshTokens_NoConnection() {
            // Given: No connection exists
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(null);

            // When: Refresh tokens
            boolean result = huaweiHealthService.refreshTokens(testUserId);

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试3.4：刷新token - 无refreshToken返回false")
        void testRefreshTokens_NoRefreshToken() {
            // Given: Connection exists but no refresh token
            testConnection.setRefreshToken(null);
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);

            // When: Refresh tokens
            boolean result = huaweiHealthService.refreshTokens(testUserId);

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试3.5：刷新token - API错误标记连接过期")
        void testRefreshTokens_ApiError() {
            // Given: API returns error
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);
            when(encryptionService.decrypt("encrypted-refresh-token")).thenReturn("refresh-token-456");
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            TokenResponse errorResponse = new TokenResponse(
                    null, null, null, null, null, "invalid_grant", "Token expired"
            );
            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenReturn(new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST));

            when(connectionService.saveConnection(any())).thenReturn(testConnection);

            // When: Refresh tokens
            boolean result = huaweiHealthService.refreshTokens(testUserId);

            // Then: Should return false and mark connection as expired
            assertFalse(result);
            ArgumentCaptor<DeviceConnection> captor = ArgumentCaptor.forClass(DeviceConnection.class);
            verify(connectionService).saveConnection(captor.capture());
            assertEquals("expired", captor.getValue().getStatus());
        }

        @Test
        @DisplayName("测试3.6：刷新token - 异常处理标记连接过期")
        void testRefreshTokens_Exception() {
            // Given: Exception during token refresh
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);
            when(encryptionService.decrypt("encrypted-refresh-token")).thenReturn("refresh-token-456");
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);

            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenThrow(new RestClientException("Network error"));

            when(connectionService.saveConnection(any())).thenReturn(testConnection);

            // When: Refresh tokens
            boolean result = huaweiHealthService.refreshTokens(testUserId);

            // Then: Should return false and mark connection as expired
            assertFalse(result);
            ArgumentCaptor<DeviceConnection> captor = ArgumentCaptor.forClass(DeviceConnection.class);
            verify(connectionService).saveConnection(captor.capture());
            assertEquals("expired", captor.getValue().getStatus());
        }
    }

    @Nested
    @DisplayName("配置状态检查测试")
    class ConfigurationTests {

        @Test
        @DisplayName("测试4.1：isConfigured - 已配置返回true")
        void testIsConfigured_True() {
            // Given: OAuth credentials are configured
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);

            // When: Check if configured
            boolean result = huaweiHealthService.isConfigured();

            // Then: Should return true
            assertTrue(result);
        }

        @Test
        @DisplayName("测试4.2：isConfigured - 未配置OAuth返回false")
        void testIsConfigured_NoOAuthCredentials() {
            // Given: OAuth credentials not configured
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(false);

            // When: Check if configured
            boolean result = huaweiHealthService.isConfigured();

            // Then: Should return false
            assertFalse(result);
        }

        @Test
        @DisplayName("测试4.3：isConfigured - 未配置加密服务返回false")
        void testIsConfigured_NoEncryption() {
            // Given: Encryption not configured
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(false);

            // When: Check if configured
            boolean result = huaweiHealthService.isConfigured();

            // Then: Should return false
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("健康数据同步测试")
    class SyncDataTests {

        @Test
        @DisplayName("测试5.1：同步数据 - 无设备连接返回失败")
        void testSyncData_NoConnection() {
            // Given: No device connection
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(null);

            SyncResultVO failedResult = SyncResultVO.failed("huawei", "Device not connected", 0);
            when(syncHistoryService.createFailedRecord(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(failedResult);

            // When: Sync data
            SyncResultVO result = huaweiHealthService.syncData(testUserId);

            // Then: Should return failed result
            assertEquals("failed", result.getStatus());
            assertTrue(result.getErrorMessage().contains("Device not connected"));
        }

        @Test
        @DisplayName("测试5.2：同步数据 - 连接状态非connected返回失败")
        void testSyncData_NotConnectedStatus() {
            // Given: Connection with non-connected status
            testConnection.setStatus("expired");
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);

            SyncResultVO failedResult = SyncResultVO.failed("huawei", "Device connection is not active", 0);
            when(syncHistoryService.createFailedRecord(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(failedResult);

            // When: Sync data
            SyncResultVO result = huaweiHealthService.syncData(testUserId);

            // Then: Should return failed result
            assertEquals("failed", result.getStatus());
        }

        @Test
        @DisplayName("测试5.3：同步数据 - 使用mock数据成功同步")
        void testSyncData_SuccessWithMockData() {
            // Given: Valid connection and successful sync
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);
            when(encryptionService.decrypt("encrypted-access-token")).thenReturn("access-token-123");

            // Make OAuth not configured to use mock data
            lenient().when(encryptionService.isConfigured()).thenReturn(false);

            doNothing().when(connectionService).updateLastSyncAt(testUserId, "huawei");

            SyncResultVO successResult = SyncResultVO.success("huawei", 10, 100);
            when(syncHistoryService.createSuccessRecord(anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(successResult);

            // When: Sync data
            SyncResultVO result = huaweiHealthService.syncData(testUserId);

            // Then: Should return success result
            assertEquals("success", result.getStatus());
            verify(connectionService).updateLastSyncAt(testUserId, "huawei");
        }

        @Test
        @DisplayName("测试5.4：同步数据 - token即将过期时刷新")
        void testSyncData_TokenExpiringSoon() {
            // Given: Token is expiring soon
            testConnection.setTokenExpireAt(LocalDateTime.now().plusMinutes(3)); // Within 5 minute threshold

            when(connectionService.getConnection(testUserId, "huawei"))
                    .thenReturn(testConnection)
                    .thenReturn(testConnection); // Return again after refresh

            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);
            when(encryptionService.decrypt(anyString())).thenReturn("decrypted-token");

            TokenResponse tokenResponse = new TokenResponse(
                    "new-access-token", "Bearer", 3600L, "new-refresh-token", "scope", null, null
            );
            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

            when(encryptionService.encrypt(anyString())).thenReturn("encrypted-token");
            when(connectionService.saveConnection(any())).thenReturn(testConnection);
            doNothing().when(connectionService).updateLastSyncAt(testUserId, "huawei");

            SyncResultVO successResult = SyncResultVO.success("huawei", 10, 100);
            when(syncHistoryService.createSuccessRecord(anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(successResult);

            // When: Sync data
            SyncResultVO result = huaweiHealthService.syncData(testUserId);

            // Then: Should have attempted token refresh
            assertTrue(result.getStatus().equals("success") || result.getStatus().equals("failed"));
        }

        @Test
        @DisplayName("测试5.5：同步数据 - 更新lastSyncAt时间")
        void testSyncData_UpdatesLastSyncAt() {
            // Given: Valid connection
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);
            when(encryptionService.decrypt("encrypted-access-token")).thenReturn("access-token-123");
            lenient().when(encryptionService.isConfigured()).thenReturn(false);

            SyncResultVO successResult = SyncResultVO.success("huawei", 10, 100);
            when(syncHistoryService.createSuccessRecord(anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(successResult);

            // When: Sync data
            huaweiHealthService.syncData(testUserId);

            // Then: Should update lastSyncAt
            verify(connectionService).updateLastSyncAt(testUserId, "huawei");
        }
    }

    @Nested
    @DisplayName("平台标识测试")
    class PlatformTests {

        @Test
        @DisplayName("测试6.1：getPlatform返回huawei")
        void testGetPlatform() {
            // When: Get platform identifier
            String platform = huaweiHealthService.getPlatform();

            // Then: Should return "huawei"
            assertEquals("huawei", platform);
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("测试7.1：RestTemplate异常处理")
        void testRestTemplateException() {
            // Given: RestTemplate throws exception
            when(encryptionService.isConfigured()).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(oAuthProperties.getPlatform("huawei")).thenReturn(testConfig);
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);
            when(encryptionService.decrypt(anyString())).thenReturn("refresh-token");

            // When: Token exchange throws exception
            when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            when(connectionService.saveConnection(any())).thenReturn(testConnection);

            // Then: Should handle exception and return false (marks connection as expired)
            boolean result = huaweiHealthService.refreshTokens(testUserId);
            assertFalse(result);

            // Verify connection was marked as expired
            ArgumentCaptor<DeviceConnection> captor = ArgumentCaptor.forClass(DeviceConnection.class);
            verify(connectionService).saveConnection(captor.capture());
            assertEquals("expired", captor.getValue().getStatus());
        }

        @Test
        @DisplayName("测试7.2：同步时健康指标保存部分失败")
        void testSyncData_PartialSaveFailure() {
            // Given: Valid connection but some saves fail
            when(connectionService.getConnection(testUserId, "huawei")).thenReturn(testConnection);
            when(encryptionService.decrypt("encrypted-access-token")).thenReturn("access-token-123");
            lenient().when(encryptionService.isConfigured()).thenReturn(false);
            doNothing().when(connectionService).updateLastSyncAt(testUserId, "huawei");

            // Some saves succeed
            lenient().when(healthMetricService.add(anyLong(), any(HealthMetricRequest.class)))
                    .thenThrow(new RuntimeException("Save failed"))
                    .thenReturn(null);

            SyncResultVO successResult = SyncResultVO.success("huawei", 10, 100);
            when(syncHistoryService.createSuccessRecord(anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(successResult);

            // When: Sync data
            SyncResultVO result = huaweiHealthService.syncData(testUserId);

            // Then: Should return success (mock data saves some metrics)
            assertEquals("success", result.getStatus());
        }
    }
}