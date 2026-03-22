package com.hhs.service;

import com.hhs.common.enums.ResolutionStrategy;
import com.hhs.dto.ConflictResult;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.service.impl.ConflictResolutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 4 Validation Tests for Device Sync
 *
 * These tests validate the identified gaps in the device sync implementation,
 * specifically focusing on the conflict resolution timestamp issue.
 *
 * GAP-003: KEEP_NEWEST strategy should compare actual timestamps
 * STATUS: FIXED - Added recordTime field to HealthMetricRequest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 4 设备同步验证测试")
class DeviceSyncValidationTest {

    @Mock
    private HealthMetricMapper healthMetricMapper;

    @InjectMocks
    private ConflictResolutionServiceImpl conflictResolutionService;

    private Long testUserId;
    private LocalDate testDate;
    private HealthMetric existingMetric;
    private HealthMetricRequest incomingRequest;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testDate = LocalDate.now();

        // Create existing metric with older recordTime
        existingMetric = new HealthMetric();
        existingMetric.setId(100L);
        existingMetric.setUserId(testUserId);
        existingMetric.setMetricKey("heartRate");
        existingMetric.setValue(new BigDecimal("72"));
        existingMetric.setUnit("bpm");
        existingMetric.setRecordDate(testDate);
        existingMetric.setRecordTime(LocalDateTime.now().minusHours(2)); // 2 hours ago
        existingMetric.setCreateTime(LocalDateTime.now().minusHours(2));

        // Create incoming request (simulating newer data from device)
        incomingRequest = new HealthMetricRequest();
        incomingRequest.setUserId(testUserId);
        incomingRequest.setMetricKey("heartRate");
        incomingRequest.setValue(new BigDecimal("75"));
        incomingRequest.setUnit("bpm");
        incomingRequest.setRecordDate(testDate);
        incomingRequest.setRecordTime(LocalDateTime.now()); // Now
    }

    @Nested
    @DisplayName("GAP-003修复验证: KEEP_NEWEST时间戳比较")
    class TimestampComparisonFixedTests {

        /**
         * Test that KEEP_NEWEST now properly compares recordTime values.
         */
        @Test
        @DisplayName("验证-001: KEEP_NEWEST - incoming更新时返回incoming")
        void testKeepNewest_IncomingNewer_ReturnsIncoming() {
            // Given: Incoming has later recordTime
            HealthMetricRequest existing = createRequestWithTime("heartRate", new BigDecimal("72"), testDate,
                    LocalDateTime.now().minusHours(2));
            HealthMetricRequest incoming = createRequestWithTime("heartRate", new BigDecimal("75"), testDate,
                    LocalDateTime.now());

            // When: Resolve with KEEP_NEWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existing, incoming, ResolutionStrategy.KEEP_NEWEST);

            // Then: Returns incoming (newer)
            assertEquals(new BigDecimal("75"), result.getValue(), "Should return incoming when it's newer");
        }

        /**
         * Test that KEEP_NEWEST returns existing when existing is newer.
         */
        @Test
        @DisplayName("验证-002: KEEP_NEWEST - existing更新时返回existing")
        void testKeepNewest_ExistingNewer_ReturnsExisting() {
            // Given: Existing has later recordTime
            HealthMetricRequest existing = createRequestWithTime("heartRate", new BigDecimal("72"), testDate,
                    LocalDateTime.now());
            HealthMetricRequest incoming = createRequestWithTime("heartRate", new BigDecimal("75"), testDate,
                    LocalDateTime.now().minusHours(2));

            // When: Resolve with KEEP_NEWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existing, incoming, ResolutionStrategy.KEEP_NEWEST);

            // Then: Returns existing (newer)
            assertEquals(new BigDecimal("72"), result.getValue(), "Should return existing when it's newer");
        }

        /**
         * Test KEEP_NEWEST with null recordTime on both sides.
         */
        @Test
        @DisplayName("验证-003: KEEP_NEWEST - 双方无recordTime时返回incoming")
        void testKeepNewest_BothNullRecordTime_ReturnsIncoming() {
            // Given: Both have null recordTime
            HealthMetricRequest existing = createRequest("heartRate", new BigDecimal("72"), testDate);
            HealthMetricRequest incoming = createRequest("heartRate", new BigDecimal("75"), testDate);

            // When: Resolve with KEEP_NEWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existing, incoming, ResolutionStrategy.KEEP_NEWEST);

            // Then: Returns incoming (default fallback)
            assertEquals(new BigDecimal("75"), result.getValue());
        }

        /**
         * Test KEEP_NEWEST with only incoming having recordTime.
         */
        @Test
        @DisplayName("验证-004: KEEP_NEWEST - 仅incoming有recordTime时返回incoming")
        void testKeepNewest_OnlyIncomingHasRecordTime_ReturnsIncoming() {
            // Given: Only incoming has recordTime
            HealthMetricRequest existing = createRequest("heartRate", new BigDecimal("72"), testDate);
            HealthMetricRequest incoming = createRequestWithTime("heartRate", new BigDecimal("75"), testDate,
                    LocalDateTime.now());

            // When: Resolve with KEEP_NEWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existing, incoming, ResolutionStrategy.KEEP_NEWEST);

            // Then: Returns incoming (has precise timestamp)
            assertEquals(new BigDecimal("75"), result.getValue());
        }

        /**
         * Test KEEP_NEWEST with only existing having recordTime.
         */
        @Test
        @DisplayName("验证-005: KEEP_NEWEST - 仅existing有recordTime时返回existing")
        void testKeepNewest_OnlyExistingHasRecordTime_ReturnsExisting() {
            // Given: Only existing has recordTime
            HealthMetricRequest existing = createRequestWithTime("heartRate", new BigDecimal("72"), testDate,
                    LocalDateTime.now());
            HealthMetricRequest incoming = createRequest("heartRate", new BigDecimal("75"), testDate);

            // When: Resolve with KEEP_NEWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existing, incoming, ResolutionStrategy.KEEP_NEWEST);

            // Then: Returns existing (has precise timestamp)
            assertEquals(new BigDecimal("72"), result.getValue());
        }

        /**
         * Test KEEP_NEWEST with different recordDates.
         */
        @Test
        @DisplayName("验证-006: KEEP_NEWEST - 不同日期时比较日期")
        void testKeepNewest_DifferentDates() {
            // Given: Different recordDates
            HealthMetricRequest existing = createRequest("heartRate", new BigDecimal("72"), testDate.minusDays(1));
            HealthMetricRequest incoming = createRequest("heartRate", new BigDecimal("75"), testDate);

            // When: Resolve with KEEP_NEWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existing, incoming, ResolutionStrategy.KEEP_NEWEST);

            // Then: Returns incoming (later date)
            assertEquals(new BigDecimal("75"), result.getValue());
        }

        /**
         * Test complete conflict resolution flow with recordTime.
         */
        @Test
        @DisplayName("验证-007: 完整冲突解决流程 - 基于recordTime")
        void testResolveConflict_WithRecordTime() {
            // Given: Existing metric with older recordTime
            when(healthMetricMapper.selectOne(any())).thenReturn(existingMetric);

            // When: Resolve conflict
            ConflictResult result = conflictResolutionService.resolveConflict(
                    testUserId, incomingRequest, ResolutionStrategy.KEEP_NEWEST);

            // Then: Conflict resolved with incoming (newer)
            assertTrue(result.isHadConflict());
            assertEquals(new BigDecimal("75"), result.getResolved().getValue());
        }
    }

    @Nested
    @DisplayName("默认策略配置验证")
    class DefaultStrategyValidationTests {

        @Test
        @DisplayName("验证-008: 心率使用KEEP_NEWEST默认策略")
        void testDefaultStrategy_HeartRate() {
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("heartRate"));
        }

        @Test
        @DisplayName("验证-009: 步数使用KEEP_HIGHEST默认策略")
        void testDefaultStrategy_Steps() {
            assertEquals(ResolutionStrategy.KEEP_HIGHEST,
                    conflictResolutionService.getDefaultStrategy("steps"));
        }

        @Test
        @DisplayName("验证-010: 血压使用KEEP_NEWEST默认策略")
        void testDefaultStrategy_BloodPressure() {
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("systolicBP"));
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("diastolicBP"));
        }

        @Test
        @DisplayName("验证-011: 静息心率使用KEEP_LOWEST默认策略")
        void testDefaultStrategy_RestingHeartRate() {
            assertEquals(ResolutionStrategy.KEEP_LOWEST,
                    conflictResolutionService.getDefaultStrategy("restingHeartRate"));
        }
    }

    // Helper methods
    private HealthMetricRequest createRequest(String metricKey, BigDecimal value, LocalDate recordDate) {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey(metricKey);
        request.setValue(value);
        request.setRecordDate(recordDate);
        return request;
    }

    private HealthMetricRequest createRequestWithTime(String metricKey, BigDecimal value, LocalDate recordDate,
                                                       LocalDateTime recordTime) {
        HealthMetricRequest request = createRequest(metricKey, value, recordDate);
        request.setRecordTime(recordTime);
        return request;
    }
}