package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * Conflict Resolution Service Unit Tests
 *
 * Tests the ConflictResolutionService which handles conflict resolution
 * during health metric synchronization from multiple devices or sources.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("冲突解决服务测试")
class ConflictResolutionServiceTest {

    @Mock
    private HealthMetricMapper healthMetricMapper;

    @InjectMocks
    private ConflictResolutionServiceImpl conflictResolutionService;

    private Long testUserId;
    private HealthMetric existingMetric;
    private HealthMetricRequest incomingRequest;
    private HealthMetricRequest existingRequest;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testDate = LocalDate.now();

        // Setup existing metric entity
        existingMetric = new HealthMetric();
        existingMetric.setId(100L);
        existingMetric.setUserId(testUserId);
        existingMetric.setMetricKey("glucose");
        existingMetric.setValue(new BigDecimal("5.5"));
        existingMetric.setUnit("mmol/L");
        existingMetric.setRecordDate(testDate);
        existingMetric.setTrend("normal");
        existingMetric.setCreateTime(LocalDateTime.now().minusHours(1));

        // Setup incoming request
        incomingRequest = new HealthMetricRequest();
        incomingRequest.setUserId(testUserId);
        incomingRequest.setMetricKey("glucose");
        incomingRequest.setValue(new BigDecimal("6.0"));
        incomingRequest.setUnit("mmol/L");
        incomingRequest.setRecordDate(testDate);
        incomingRequest.setTrend("up");

        // Setup existing request (for direct resolution tests)
        existingRequest = new HealthMetricRequest();
        existingRequest.setUserId(testUserId);
        existingRequest.setMetricKey("glucose");
        existingRequest.setValue(new BigDecimal("5.5"));
        existingRequest.setUnit("mmol/L");
        existingRequest.setRecordDate(testDate);
        existingRequest.setTrend("normal");
    }

    @Nested
    @DisplayName("查找已存在指标测试")
    class FindExistingTests {

        @Test
        @DisplayName("测试1.1：查找已存在指标 - 存在")
        void testFindExisting_Found() {
            // Given: Mock mapper returns existing metric
            when(healthMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingMetric);

            // When: Find existing
            HealthMetric result = conflictResolutionService.findExisting(testUserId, "glucose", testDate);

            // Then: Verify result
            assertNotNull(result);
            assertEquals("glucose", result.getMetricKey());
            assertEquals(new BigDecimal("5.5"), result.getValue());
            verify(healthMetricMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("测试1.2：查找已存在指标 - 不存在")
        void testFindExisting_NotFound() {
            // Given: Mock mapper returns null
            when(healthMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // When: Find existing
            HealthMetric result = conflictResolutionService.findExisting(testUserId, "glucose", testDate);

            // Then: Verify null result
            assertNull(result);
            verify(healthMetricMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("测试1.3：查找已存在指标 - 无效参数")
        void testFindExisting_InvalidParams() {
            // When & Then: Invalid parameters should return null
            assertNull(conflictResolutionService.findExisting(null, "glucose", testDate));
            assertNull(conflictResolutionService.findExisting(testUserId, null, testDate));
            assertNull(conflictResolutionService.findExisting(testUserId, "glucose", null));
        }
    }

    @Nested
    @DisplayName("直接解决冲突测试")
    class ResolveTests {

        @Test
        @DisplayName("测试2.1：解决冲突 - KEEP_NEWEST策略")
        void testResolve_KeepNewest() {
            // When: Resolve with KEEP_NEWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_NEWEST);

            // Then: Should return incoming (newer)
            assertNotNull(result);
            assertEquals(new BigDecimal("6.0"), result.getValue());
        }

        @Test
        @DisplayName("测试2.2：解决冲突 - KEEP_HIGHEST策略")
        void testResolve_KeepHighest() {
            // When: Resolve with KEEP_HIGHEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_HIGHEST);

            // Then: Should return higher value (6.0 > 5.5)
            assertNotNull(result);
            assertEquals(new BigDecimal("6.0"), result.getValue());
        }

        @Test
        @DisplayName("测试2.3：解决冲突 - KEEP_LOWEST策略")
        void testResolve_KeepLowest() {
            // When: Resolve with KEEP_LOWEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_LOWEST);

            // Then: Should return lower value (5.5 < 6.0)
            assertNotNull(result);
            assertEquals(new BigDecimal("5.5"), result.getValue());
        }

        @Test
        @DisplayName("测试2.4：解决冲突 - KEEP_EXISTING策略")
        void testResolve_KeepExisting() {
            // When: Resolve with KEEP_EXISTING
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_EXISTING);

            // Then: Should return existing
            assertNotNull(result);
            assertEquals(new BigDecimal("5.5"), result.getValue());
        }

        @Test
        @DisplayName("测试2.5：解决冲突 - KEEP_INCOMING策略")
        void testResolve_KeepIncoming() {
            // When: Resolve with KEEP_INCOMING
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_INCOMING);

            // Then: Should return incoming
            assertNotNull(result);
            assertEquals(new BigDecimal("6.0"), result.getValue());
        }

        @Test
        @DisplayName("测试2.6：解决冲突 - 空值处理")
        void testResolve_NullHandling() {
            // When & Then: Null existing returns incoming
            HealthMetricRequest result1 = conflictResolutionService.resolve(
                    null, incomingRequest, ResolutionStrategy.KEEP_NEWEST);
            assertEquals(incomingRequest, result1);

            // When & Then: Null incoming returns existing
            HealthMetricRequest result2 = conflictResolutionService.resolve(
                    existingRequest, null, ResolutionStrategy.KEEP_NEWEST);
            assertEquals(existingRequest, result2);
        }

        @Test
        @DisplayName("测试2.7：解决冲突 - 空策略默认")
        void testResolve_NullStrategy() {
            // When: Resolve with null strategy (should default to KEEP_NEWEST)
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, null);

            // Then: Should apply default strategy
            assertNotNull(result);
            assertEquals(new BigDecimal("6.0"), result.getValue());
        }
    }

    @Nested
    @DisplayName("完整冲突解决流程测试")
    class ResolveConflictTests {

        @Test
        @DisplayName("测试3.1：完整解决 - 无冲突")
        void testResolveConflict_NoConflict() {
            // Given: No existing metric
            when(healthMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // When: Resolve conflict
            ConflictResult result = conflictResolutionService.resolveConflict(
                    testUserId, incomingRequest, null);

            // Then: No conflict detected
            assertFalse(result.isHadConflict());
            assertEquals(incomingRequest, result.getResolved());
            assertNotNull(result.getAppliedStrategy());
        }

        @Test
        @DisplayName("测试3.2：完整解决 - 有冲突")
        void testResolveConflict_WithConflict() {
            // Given: Existing metric found
            when(healthMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingMetric);

            // When: Resolve conflict
            ConflictResult result = conflictResolutionService.resolveConflict(
                    testUserId, incomingRequest, ResolutionStrategy.KEEP_HIGHEST);

            // Then: Conflict detected and resolved
            assertTrue(result.isHadConflict());
            assertNotNull(result.getResolved());
            assertEquals(ResolutionStrategy.KEEP_HIGHEST, result.getAppliedStrategy());
            assertEquals(100L, result.getExistingMetricId());
            assertEquals(new BigDecimal("5.5"), result.getExistingValue());
            // KEEP_HIGHEST should return the higher value (6.0)
            assertEquals(new BigDecimal("6.0"), result.getResolved().getValue());
        }

        @Test
        @DisplayName("测试3.3：完整解决 - 空请求处理")
        void testResolveConflict_NullRequest() {
            // When: Resolve with null request
            ConflictResult result = conflictResolutionService.resolveConflict(
                    testUserId, null, ResolutionStrategy.KEEP_NEWEST);

            // Then: Should handle gracefully
            assertFalse(result.isHadConflict());
            assertNull(result.getResolved());
        }

        @Test
        @DisplayName("测试3.4：完整解决 - 使用默认策略")
        void testResolveConflict_DefaultStrategy() {
            // Given: No existing metric, metricKey is "steps"
            incomingRequest.setMetricKey("steps");
            when(healthMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // When: Resolve conflict with null strategy
            ConflictResult result = conflictResolutionService.resolveConflict(
                    testUserId, incomingRequest, null);

            // Then: Should use default strategy for "steps" (KEEP_HIGHEST)
            assertEquals(ResolutionStrategy.KEEP_HIGHEST, result.getAppliedStrategy());
        }
    }

    @Nested
    @DisplayName("默认策略测试")
    class DefaultStrategyTests {

        @Test
        @DisplayName("测试4.1：默认策略 - 活动指标（KEEP_HIGHEST）")
        void testDefaultStrategy_ActivityMetrics() {
            assertEquals(ResolutionStrategy.KEEP_HIGHEST,
                    conflictResolutionService.getDefaultStrategy("steps"));
            assertEquals(ResolutionStrategy.KEEP_HIGHEST,
                    conflictResolutionService.getDefaultStrategy("activeMinutes"));
            assertEquals(ResolutionStrategy.KEEP_HIGHEST,
                    conflictResolutionService.getDefaultStrategy("calories"));
        }

        @Test
        @DisplayName("测试4.2：默认策略 - 医疗指标（KEEP_NEWEST）")
        void testDefaultStrategy_MedicalMetrics() {
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("glucose"));
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("systolicBP"));
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("heartRate"));
        }

        @Test
        @DisplayName("测试4.3：默认策略 - 睡眠指标（KEEP_NEWEST）")
        void testDefaultStrategy_SleepMetrics() {
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("sleepDuration"));
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("sleepQuality"));
        }

        @Test
        @DisplayName("测试4.4：默认策略 - 低值优先指标（KEEP_LOWEST）")
        void testDefaultStrategy_LowestPreferredMetrics() {
            assertEquals(ResolutionStrategy.KEEP_LOWEST,
                    conflictResolutionService.getDefaultStrategy("restingHeartRate"));
        }

        @Test
        @DisplayName("测试4.5：默认策略 - 未知指标默认值")
        void testDefaultStrategy_UnknownMetric() {
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy("unknownMetric"));
            assertEquals(ResolutionStrategy.KEEP_NEWEST,
                    conflictResolutionService.getDefaultStrategy(null));
        }

        @Test
        @DisplayName("测试4.6：自定义策略设置")
        void testSetDefaultStrategy() {
            // When: Set custom strategy
            conflictResolutionService.setDefaultStrategy("customMetric", ResolutionStrategy.KEEP_LOWEST);

            // Then: Should return custom strategy
            assertEquals(ResolutionStrategy.KEEP_LOWEST,
                    conflictResolutionService.getDefaultStrategy("customMetric"));
        }

        @Test
        @DisplayName("测试4.7：自定义策略覆盖内置默认")
        void testSetDefaultStrategy_Override() {
            // Given: "steps" has default KEEP_HIGHEST
            assertEquals(ResolutionStrategy.KEEP_HIGHEST,
                    conflictResolutionService.getDefaultStrategy("steps"));

            // When: Override with custom strategy
            conflictResolutionService.setDefaultStrategy("steps", ResolutionStrategy.KEEP_LOWEST);

            // Then: Should use custom strategy
            assertEquals(ResolutionStrategy.KEEP_LOWEST,
                    conflictResolutionService.getDefaultStrategy("steps"));
        }

        @Test
        @DisplayName("测试4.8：自定义策略 - 无效参数处理")
        void testSetDefaultStrategy_InvalidParams() {
            // When: Set with null params (should not throw)
            assertDoesNotThrow(() ->
                    conflictResolutionService.setDefaultStrategy(null, ResolutionStrategy.KEEP_NEWEST));
            assertDoesNotThrow(() ->
                    conflictResolutionService.setDefaultStrategy("testMetric", null));
        }
    }

    @Nested
    @DisplayName("ConflictResult工厂方法测试")
    class ConflictResultFactoryTests {

        @Test
        @DisplayName("测试5.1：noConflict工厂方法")
        void testNoConflictFactory() {
            // When: Create no-conflict result
            ConflictResult result = ConflictResult.noConflict(
                    incomingRequest, ResolutionStrategy.KEEP_NEWEST);

            // Then: Verify result
            assertFalse(result.isHadConflict());
            assertEquals(incomingRequest, result.getResolved());
            assertEquals(ResolutionStrategy.KEEP_NEWEST, result.getAppliedStrategy());
            assertNull(result.getExistingMetricId());
            assertNull(result.getExistingValue());
        }

        @Test
        @DisplayName("测试5.2：withConflict工厂方法")
        void testWithConflictFactory() {
            // When: Create conflict result
            ConflictResult result = ConflictResult.withConflict(
                    incomingRequest, ResolutionStrategy.KEEP_HIGHEST, 100L, new BigDecimal("5.5"));

            // Then: Verify result
            assertTrue(result.isHadConflict());
            assertEquals(incomingRequest, result.getResolved());
            assertEquals(ResolutionStrategy.KEEP_HIGHEST, result.getAppliedStrategy());
            assertEquals(100L, result.getExistingMetricId());
            assertEquals(new BigDecimal("5.5"), result.getExistingValue());
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("测试6.1：相同值比较")
        void testSameValueComparison() {
            // Given: Same values
            existingRequest.setValue(new BigDecimal("100"));
            incomingRequest.setValue(new BigDecimal("100"));

            // When: Resolve with KEEP_HIGHEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_HIGHEST);

            // Then: Should return existing (equal values, existing wins)
            assertEquals(new BigDecimal("100"), result.getValue());
        }

        @Test
        @DisplayName("测试6.2：空值在比较中")
        void testNullValueInComparison() {
            // Given: Null value in existing
            existingRequest.setValue(null);
            incomingRequest.setValue(new BigDecimal("100"));

            // When: Resolve with KEEP_HIGHEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_HIGHEST);

            // Then: Should return incoming (existing has null)
            assertEquals(new BigDecimal("100"), result.getValue());
        }

        @Test
        @DisplayName("测试6.3：负值处理")
        void testNegativeValueHandling() {
            // Given: Negative values (for metrics like weight change)
            existingRequest.setValue(new BigDecimal("-2.5"));
            incomingRequest.setValue(new BigDecimal("-1.0"));

            // When: Resolve with KEEP_HIGHEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_HIGHEST);

            // Then: Should return higher (-1.0 > -2.5)
            assertEquals(new BigDecimal("-1.0"), result.getValue());
        }

        @Test
        @DisplayName("测试6.4：高精度数值比较")
        void testHighPrecisionComparison() {
            // Given: High precision values
            existingRequest.setValue(new BigDecimal("5.55555"));
            incomingRequest.setValue(new BigDecimal("5.55556"));

            // When: Resolve with KEEP_HIGHEST
            HealthMetricRequest result = conflictResolutionService.resolve(
                    existingRequest, incomingRequest, ResolutionStrategy.KEEP_HIGHEST);

            // Then: Should correctly compare
            assertEquals(new BigDecimal("5.55556"), result.getValue());
        }
    }
}