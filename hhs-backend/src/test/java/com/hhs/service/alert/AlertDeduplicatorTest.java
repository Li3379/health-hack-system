package com.hhs.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthAlert;
import com.hhs.mapper.HealthAlertMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Alert Deduplicator Unit Tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预警去重服务测试")
class AlertDeduplicatorTest {

    @Mock
    private HealthAlertMapper healthAlertMapper;

    @InjectMocks
    private AlertDeduplicator alertDeduplicator;

    private HealthAlert testAlert;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        testAlert = new HealthAlert();
        testAlert.setId(1L);
        testAlert.setUserId(testUserId);
        testAlert.setMetricKey("heartRate");
        testAlert.setAlertLevel("HIGH");
        testAlert.setCurrentValue(new BigDecimal("120"));
        testAlert.setOccurrenceCount(1);
        testAlert.setCreatedAt(LocalDateTime.now().minusMinutes(30));
    }

    @Test
    @DisplayName("测试1.1：应该合并 - 相同级别、时间窗口内、值偏差<10%")
    void testShouldMerge_AllConditionsMet() {
        // Given: Value within 10% deviation
        BigDecimal newValue = new BigDecimal("125"); // 125 vs 120 = 4.2% deviation

        // When
        boolean result = alertDeduplicator.shouldMerge(testAlert, newValue, "HIGH");

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("测试1.2：不应合并 - 不同级别")
    void testShouldNotMerge_DifferentLevel() {
        // Given: Different alert level
        BigDecimal newValue = new BigDecimal("125");

        // When
        boolean result = alertDeduplicator.shouldMerge(testAlert, newValue, "MEDIUM");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("测试1.3：不应合并 - 超过时间窗口")
    void testShouldNotMerge_TimeWindowExceeded() {
        // Given: Alert created more than 1 hour ago
        testAlert.setCreatedAt(LocalDateTime.now().minusHours(2));
        BigDecimal newValue = new BigDecimal("125");

        // When
        boolean result = alertDeduplicator.shouldMerge(testAlert, newValue, "HIGH");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("测试1.4：不应合并 - 值偏差过大")
    void testShouldNotMerge_ValueDeviationTooLarge() {
        // Given: Value with >10% deviation
        BigDecimal newValue = new BigDecimal("140"); // 140 vs 120 = 16.7% deviation

        // When
        boolean result = alertDeduplicator.shouldMerge(testAlert, newValue, "HIGH");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("测试1.5：合并成功 - 更新计数和值")
    void testMerge_Success() {
        // Given
        BigDecimal newValue = new BigDecimal("125");
        when(healthAlertMapper.updateById(any(HealthAlert.class))).thenReturn(1);

        // When
        HealthAlert result = alertDeduplicator.merge(testAlert, newValue);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getOccurrenceCount());
        assertEquals(newValue, result.getCurrentValue());
        assertNotNull(result.getLastOccurrenceAt());
        verify(healthAlertMapper, times(1)).updateById(testAlert);
    }

    @Test
    @DisplayName("测试1.6：合并失败 - 空预警")
    void testMerge_NullAlert() {
        // When
        HealthAlert result = alertDeduplicator.merge(null, new BigDecimal("125"));

        // Then
        assertNull(result);
        verify(healthAlertMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("测试1.7：边界值测试 - 偏差正好10%")
    void testShouldMerge_ExactlyTenPercent() {
        // Given: Exactly 10% deviation
        BigDecimal newValue = new BigDecimal("132"); // 132 vs 120 = 10% deviation

        // When
        boolean result = alertDeduplicator.shouldMerge(testAlert, newValue, "HIGH");

        // Then: Should NOT merge (deviation must be < 10%)
        assertFalse(result);
    }

    @Test
    @DisplayName("测试1.8：边界值测试 - 偏差略低于10%")
    void testShouldMerge_SlightlyBelowTenPercent() {
        // Given: Slightly below 10% deviation
        BigDecimal newValue = new BigDecimal("131"); // 131 vs 120 = 9.17% deviation

        // When
        boolean result = alertDeduplicator.shouldMerge(testAlert, newValue, "HIGH");

        // Then: Should merge
        assertTrue(result);
    }

    @Test
    @DisplayName("测试1.9：相似性检查 - 相似预警")
    void testAreSimilar_Similar() {
        // Given
        HealthAlert alert1 = new HealthAlert();
        alert1.setUserId(1L);
        alert1.setMetricKey("heartRate");
        alert1.setAlertLevel("HIGH");
        alert1.setCurrentValue(new BigDecimal("120"));
        alert1.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        HealthAlert alert2 = new HealthAlert();
        alert2.setUserId(1L);
        alert2.setMetricKey("heartRate");
        alert2.setAlertLevel("HIGH");
        alert2.setCurrentValue(new BigDecimal("125"));
        alert2.setCreatedAt(LocalDateTime.now());

        // When
        boolean result = alertDeduplicator.areSimilar(alert1, alert2);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("测试1.10：相似性检查 - 不同用户")
    void testAreSimilar_DifferentUser() {
        // Given
        HealthAlert alert1 = new HealthAlert();
        alert1.setUserId(1L);
        alert1.setMetricKey("heartRate");
        alert1.setAlertLevel("HIGH");
        alert1.setCurrentValue(new BigDecimal("120"));
        alert1.setCreatedAt(LocalDateTime.now());

        HealthAlert alert2 = new HealthAlert();
        alert2.setUserId(2L);
        alert2.setMetricKey("heartRate");
        alert2.setAlertLevel("HIGH");
        alert2.setCurrentValue(new BigDecimal("120"));
        alert2.setCreatedAt(LocalDateTime.now());

        // When
        boolean result = alertDeduplicator.areSimilar(alert1, alert2);

        // Then
        assertFalse(result);
    }
}