package com.hhs.service;

import com.hhs.config.DeviceOAuthProperties;
import com.hhs.dto.PlatformMetadata;
import com.hhs.enums.PlatformStatus;
import com.hhs.service.impl.PlatformAvailabilityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlatformAvailabilityService.
 */
@ExtendWith(MockitoExtension.class)
class PlatformAvailabilityServiceTest {

    @Mock
    private DeviceOAuthProperties oAuthProperties;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private DeviceSyncOrchestrationService orchestrationService;

    @InjectMocks
    private PlatformAvailabilityServiceImpl service;

    @Nested
    @DisplayName("getAllPlatformMetadata()")
    class GetAllPlatformMetadataTests {

        @Test
        @DisplayName("should return all four platforms")
        void shouldReturnAllFourPlatforms() {
            when(orchestrationService.isPlatformRegistered(anyString())).thenReturn(true);
            when(oAuthProperties.hasValidCredentials(anyString())).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);

            List<PlatformMetadata> metadata = service.getAllPlatformMetadata();

            assertNotNull(metadata);
            assertEquals(4, metadata.size());

            List<String> platforms = metadata.stream().map(PlatformMetadata::platform).toList();
            assertTrue(platforms.contains("huawei"));
            assertTrue(platforms.contains("xiaomi"));
            assertTrue(platforms.contains("wechat"));
            assertTrue(platforms.contains("apple"));
        }
    }

    @Nested
    @DisplayName("getPlatformMetadata()")
    class GetPlatformMetadataTests {

        @Test
        @DisplayName("should return null for unknown platform")
        void shouldReturnNullForUnknownPlatform() {
            PlatformMetadata metadata = service.getPlatformMetadata("unknown");

            assertNull(metadata);
        }

        @Test
        @DisplayName("should return AVAILABLE for configured Huawei")
        void shouldReturnAvailableForConfiguredHuawei() {
            when(orchestrationService.isPlatformRegistered("huawei")).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);

            PlatformMetadata metadata = service.getPlatformMetadata("huawei");

            assertNotNull(metadata);
            assertEquals("huawei", metadata.platform());
            assertEquals(PlatformStatus.AVAILABLE, metadata.status());
        }

        @Test
        @DisplayName("should return REQUIRES_MINI_PROGRAM for WeChat")
        void shouldReturnRequiresMiniProgramForWeChat() {
            PlatformMetadata metadata = service.getPlatformMetadata("wechat");

            assertNotNull(metadata);
            assertEquals("wechat", metadata.platform());
            assertEquals(PlatformStatus.REQUIRES_MINI_PROGRAM, metadata.status());
        }

        @Test
        @DisplayName("should return REQUIRES_APP for Apple")
        void shouldReturnRequiresAppForApple() {
            PlatformMetadata metadata = service.getPlatformMetadata("apple");

            assertNotNull(metadata);
            assertEquals("apple", metadata.platform());
            assertEquals(PlatformStatus.REQUIRES_APP, metadata.status());
        }
    }

    @Nested
    @DisplayName("isPlatformAvailable()")
    class IsPlatformAvailableTests {

        @Test
        @DisplayName("should return false for null platform")
        void shouldReturnFalseForNullPlatform() {
            assertFalse(service.isPlatformAvailable(null));
        }

        @Test
        @DisplayName("should return false for empty platform")
        void shouldReturnFalseForEmptyPlatform() {
            assertFalse(service.isPlatformAvailable(""));
        }

        @Test
        @DisplayName("should return false for WeChat (requires mini-program)")
        void shouldReturnFalseForWeChat() {
            assertFalse(service.isPlatformAvailable("wechat"));
        }

        @Test
        @DisplayName("should return false for Apple (requires app)")
        void shouldReturnFalseForApple() {
            assertFalse(service.isPlatformAvailable("apple"));
        }

        @Test
        @DisplayName("should return true for configured Huawei")
        void shouldReturnTrueForConfiguredHuawei() {
            when(orchestrationService.isPlatformRegistered("huawei")).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);

            assertTrue(service.isPlatformAvailable("huawei"));
        }
    }

    @Nested
    @DisplayName("getUnavailableReason()")
    class GetUnavailableReasonTests {

        @Test
        @DisplayName("should return reason for null platform")
        void shouldReturnReasonForNullPlatform() {
            String reason = service.getUnavailableReason(null);

            assertEquals("未知平台", reason);
        }

        @Test
        @DisplayName("should return reason for WeChat")
        void shouldReturnReasonForWeChat() {
            String reason = service.getUnavailableReason("wechat");

            assertNotNull(reason);
            assertTrue(reason.contains("小程序"));
        }

        @Test
        @DisplayName("should return null for available platform")
        void shouldReturnNullForAvailablePlatform() {
            when(orchestrationService.isPlatformRegistered("huawei")).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);

            String reason = service.getUnavailableReason("huawei");

            assertNull(reason);
        }
    }

    @Nested
    @DisplayName("getAvailablePlatforms()")
    class GetAvailablePlatformsTests {

        @Test
        @DisplayName("should return empty list when no platforms configured")
        void shouldReturnEmptyListWhenNoPlatformsConfigured() {
            when(orchestrationService.isPlatformRegistered(anyString())).thenReturn(false);

            List<String> available = service.getAvailablePlatforms();

            assertTrue(available.isEmpty());
        }

        @Test
        @DisplayName("should return only configured platforms")
        void shouldReturnOnlyConfiguredPlatforms() {
            when(orchestrationService.isPlatformRegistered("huawei")).thenReturn(true);
            when(oAuthProperties.hasValidCredentials("huawei")).thenReturn(true);
            when(encryptionService.isConfigured()).thenReturn(true);
            when(orchestrationService.isPlatformRegistered("xiaomi")).thenReturn(false);

            List<String> available = service.getAvailablePlatforms();

            assertEquals(1, available.size());
            assertEquals("huawei", available.get(0));
        }
    }
}