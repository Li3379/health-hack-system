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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Alert Frequency Strategy Unit Tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预警频率策略测试")
class AlertFrequencyStrategyTest {

    @Mock
    private HealthAlertMapper healthAlertMapper;

    @InjectMocks
    private AlertFrequencyStrategy alertFrequencyStrategy;

    private HealthAlert testAlert;
    private Long testUserId = 1L;
    private String testMetricKey = "heartRate";

    @BeforeEach
    void setUp() {
        testAlert = new HealthAlert();
        testAlert.setId(1L);
        testAlert.setUserId(testUserId);
        testAlert.setMetricKey(testMetricKey);
        testAlert.setAlertLevel("HIGH");
        testAlert.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("测试1.1：应该推送 - 当日首次预警")
    void testShouldPush_FirstAlertOfDay() {
        // Given: No alerts today
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // When
        boolean result = alertFrequencyStrategy.shouldPush(testAlert);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("测试1.2：应该推送 - 当日第2次预警")
    void testShouldPush_SecondAlertOfDay() {
        // Given: 1 alert today
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // When
        boolean result = alertFrequencyStrategy.shouldPush(testAlert);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("测试1.3：应该推送 - 当日第3次预警")
    void testShouldPush_ThirdAlertOfDay() {
        // Given: 2 alerts today
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        // When
        boolean result = alertFrequencyStrategy.shouldPush(testAlert);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("测试1.4：不应推送 - 当日第4次预警且已发送摘要")
    void testShouldNotPush_FourthAlertAndSummarySent() {
        // Given: 3 alerts today and summary already sent
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        HealthAlert summaryAlert = new HealthAlert();
        summaryAlert.setAlertType("DAILY_SUMMARY");
        summaryAlert.setCreatedAt(LocalDateTime.now());
        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(summaryAlert);

        // When
        boolean result = alertFrequencyStrategy.shouldPush(testAlert);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("测试1.5：应该推送摘要 - 当日第4次预警且未发送摘要")
    void testShouldPushSummary_FourthAlertNoSummary() {
        // Given: 3 alerts today and no summary sent
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When
        boolean result = alertFrequencyStrategy.shouldPush(testAlert);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("测试1.6：统计当日预警数量")
    void testCountTodayAlerts() {
        // Given
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        // When
        int count = alertFrequencyStrategy.countTodayAlerts(testUserId, testMetricKey);

        // Then
        assertEquals(5, count);
    }

    @Test
    @DisplayName("测试1.7：获取频率状态 - 正常模式")
    void testGetFrequencyStatus_NormalMode() {
        // Given: 2 alerts today
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        // When
        AlertFrequencyStrategy.FrequencyStatus status = alertFrequencyStrategy.getFrequencyStatus(testUserId, testMetricKey);

        // Then
        assertEquals(2, status.dailyAlertCount());
        assertEquals(1, status.remainingNormalPushes());
        assertFalse(status.inSummaryMode());
        assertEquals(3, status.maxDailyNormalPushes());
    }

    @Test
    @DisplayName("测试1.8：获取频率状态 - 摘要模式")
    void testGetFrequencyStatus_SummaryMode() {
        // Given: 5 alerts today
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        // When
        AlertFrequencyStrategy.FrequencyStatus status = alertFrequencyStrategy.getFrequencyStatus(testUserId, testMetricKey);

        // Then
        assertEquals(5, status.dailyAlertCount());
        assertEquals(0, status.remainingNormalPushes());
        assertTrue(status.inSummaryMode());
    }

    @Test
    @DisplayName("测试1.9：不应生成摘要 - 预警数量不足")
    void testShouldNotGenerateSummary_InsufficientAlerts() {
        // Given: Only 2 alerts today
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        // When
        boolean result = alertFrequencyStrategy.shouldGenerateDailySummary(testUserId, testMetricKey);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("测试1.10：应该生成摘要 - 预警数量足够且未发送")
    void testShouldGenerateSummary_SufficientAlertsNoSummary() {
        // Given: 5 alerts today, no summary yet
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When
        boolean result = alertFrequencyStrategy.shouldGenerateDailySummary(testUserId, testMetricKey);

        // Then
        assertTrue(result);
    }
}