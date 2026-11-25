package com.hhs.service;

import com.hhs.entity.PushHistory;
import com.hhs.mapper.PushHistoryMapper;
import com.hhs.service.impl.PushHistoryServiceImpl;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PushHistoryService
 */
@ExtendWith(MockitoExtension.class)
class PushHistoryServiceTest {

    @Mock
    private PushHistoryMapper pushHistoryMapper;

    @InjectMocks
    private PushHistoryServiceImpl pushHistoryService;

    @BeforeEach
    void setUp() {
        // Set rate limit values
        ReflectionTestUtils.setField(pushHistoryService, "webSocketRateLimit", 100);
        ReflectionTestUtils.setField(pushHistoryService, "emailRateLimit", 20);
        ReflectionTestUtils.setField(pushHistoryService, "wecomRateLimit", 30);
        ReflectionTestUtils.setField(pushHistoryService, "feishuRateLimit", 30);
    }

    @Test
    @DisplayName("Should record successful push")
    void testRecordSuccessfulPush() {
        // Given
        Long userId = 1L;
        Long alertId = 100L;
        PushResult result = PushResult.success(ChannelType.EMAIL);

        // When
        pushHistoryService.recordPush(userId, alertId, result);

        // Then
        ArgumentCaptor<PushHistory> captor = ArgumentCaptor.forClass(PushHistory.class);
        verify(pushHistoryMapper).insert(captor.capture());

        PushHistory history = captor.getValue();
        assertEquals(userId, history.getUserId());
        assertEquals(alertId, history.getAlertId());
        assertEquals("EMAIL", history.getChannelType());
        assertEquals("SUCCESS", history.getStatus());
    }

    @Test
    @DisplayName("Should record failed push")
    void testRecordFailedPush() {
        // Given
        Long userId = 1L;
        Long alertId = 100L;
        PushResult result = PushResult.failed(ChannelType.WECOM, "Webhook timeout");

        // When
        pushHistoryService.recordPush(userId, alertId, result);

        // Then
        ArgumentCaptor<PushHistory> captor = ArgumentCaptor.forClass(PushHistory.class);
        verify(pushHistoryMapper).insert(captor.capture());

        PushHistory history = captor.getValue();
        assertEquals("FAILED", history.getStatus());
        assertEquals("Webhook timeout", history.getMessage());
        // PUSH-10：真实告警的失败要进入补偿队列
        assertEquals(0, history.getRetryCount());
        assertNotNull(history.getNextRetryAt(), "失败的真实告警应被排入重试");
    }

    @Test
    @DisplayName("测试推送（alertId 为空）失败不排重试")
    void testFailedTestPushIsNotRetried() {
        // 手动「测试推送」是诊断动作，重试它只会在用户不知情时反复打服务商
        pushHistoryService.recordPush(1L, null, PushResult.failed(ChannelType.EMAIL, "auth failed"));

        ArgumentCaptor<PushHistory> captor = ArgumentCaptor.forClass(PushHistory.class);
        verify(pushHistoryMapper).insert(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertNull(captor.getValue().getNextRetryAt(), "测试推送不该排期");
    }

    @Test
    @DisplayName("SKIPPED 不排重试（没配置/被限流，重试无意义）")
    void testSkippedPushIsNotRetried() {
        pushHistoryService.recordPush(1L, 100L,
                PushResult.skipped(ChannelType.EMAIL, "Channel not configured"));

        ArgumentCaptor<PushHistory> captor = ArgumentCaptor.forClass(PushHistory.class);
        verify(pushHistoryMapper).insert(captor.capture());
        assertEquals("SKIPPED", captor.getValue().getStatus());
        assertNull(captor.getValue().getNextRetryAt());
    }

    @Test
    @DisplayName("scheduleRetry 走显式 UPDATE（nextRetryAt=null 必须真的清空列）")
    void testScheduleRetryUsesExplicitUpdate() {
        pushHistoryService.scheduleRetry(9L, 3, null);

        // updateById 会跳过 null 字段，「停止调度」就永远写不进去 —— 所以必须用显式 SQL
        verify(pushHistoryMapper).updateRetrySchedule(9L, 3, null);
        verify(pushHistoryMapper, never()).updateById(any(PushHistory.class));
    }

    @Test
    @DisplayName("findDueForRetry 把上限与批量交给 mapper")
    void testFindDueForRetryDelegates() {
        when(pushHistoryMapper.findDueForRetry(any(LocalDateTime.class), eq(3), eq(50)))
                .thenReturn(List.of());

        assertTrue(pushHistoryService.findDueForRetry(3, 50).isEmpty());

        verify(pushHistoryMapper).findDueForRetry(any(LocalDateTime.class), eq(3), eq(50));
    }

    @Test
    @DisplayName("Should record skipped push")
    void testRecordSkippedPush() {
        // Given
        Long userId = 1L;
        Long alertId = 100L;
        PushResult result = PushResult.skipped(ChannelType.FEISHU, "Channel not available");

        // When
        pushHistoryService.recordPush(userId, alertId, result);

        // Then
        ArgumentCaptor<PushHistory> captor = ArgumentCaptor.forClass(PushHistory.class);
        verify(pushHistoryMapper).insert(captor.capture());

        PushHistory history = captor.getValue();
        assertEquals("SKIPPED", history.getStatus());
    }

    @Test
    @DisplayName("Should get recent history")
    void testGetRecentHistory() {
        // Given
        Long userId = 1L;
        List<PushHistory> mockHistory = List.of(new PushHistory(), new PushHistory());
        when(pushHistoryMapper.findRecentByUserId(userId, 20)).thenReturn(mockHistory);

        // When
        List<PushHistory> result = pushHistoryService.getRecentHistory(userId, 20);

        // Then
        assertEquals(2, result.size());
        verify(pushHistoryMapper).findRecentByUserId(userId, 20);
    }

    @Test
    @DisplayName("Should limit history query to 100")
    void testHistoryLimit() {
        // Given
        Long userId = 1L;

        // When
        pushHistoryService.getRecentHistory(userId, 200);

        // Then
        verify(pushHistoryMapper).findRecentByUserId(userId, 100);
    }

    @Test
    @DisplayName("Should get push count since")
    void testGetPushCountSince() {
        // Given
        Long userId = 1L;
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        when(pushHistoryMapper.countByUserAndChannelSince(userId, "EMAIL", since)).thenReturn(5);

        // When
        int count = pushHistoryService.getPushCountSince(userId, ChannelType.EMAIL, since);

        // Then
        assertEquals(5, count);
    }

    @Test
    @DisplayName("Should delegate latest-per-channel lookup to the mapper (PUSH-6)")
    void testGetLatestPerChannelSinceDelegates() {
        Long userId = 1L;
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        PushHistory failed = new PushHistory();
        failed.setChannelType("EMAIL");
        failed.setStatus("FAILED");
        when(pushHistoryMapper.findLatestPerChannelSince(userId, since)).thenReturn(List.of(failed));

        List<PushHistory> result = pushHistoryService.getLatestPerChannelSince(userId, since);

        assertEquals(1, result.size());
        assertEquals("FAILED", result.get(0).getStatus());
        verify(pushHistoryMapper).findLatestPerChannelSince(userId, since);
    }

    @Test
    @DisplayName("Should detect rate limit exceeded")
    void testRateLimitExceeded() {
        // Given: 20 条**已送达的真实告警** —— 额度按 countDeliveredAlertsSince 计
        Long userId = 1L;
        when(pushHistoryMapper.countDeliveredAlertsSince(eq(userId), eq("EMAIL"), any()))
                .thenReturn(20); // At limit

        // When
        boolean exceeded = pushHistoryService.isRateLimitExceeded(userId, ChannelType.EMAIL);

        // Then
        assertTrue(exceeded);
    }

    @Test
    @DisplayName("Rate limit counts delivered real alerts only — never the attempt counter")
    void testRateLimitUsesDeliveredAlertsQuery() {
        // PUSH-5：旧的 countByUserAndChannelSince 数的是所有行（含 FAILED 与测试推送），
        // 于是「凭据坏了反复测试」会把真实告警锁死。这里钉住用的是新查询。
        Long userId = 1L;
        when(pushHistoryMapper.countDeliveredAlertsSince(eq(userId), eq("EMAIL"), any()))
                .thenReturn(0);

        assertFalse(pushHistoryService.isRateLimitExceeded(userId, ChannelType.EMAIL));

        verify(pushHistoryMapper).countDeliveredAlertsSince(eq(userId), eq("EMAIL"), any());
        verify(pushHistoryMapper, never()).countByUserAndChannelSince(any(), any(), any());
    }

    @Test
    @DisplayName("Should not exceed rate limit when under threshold")
    void testRateLimitNotExceeded() {
        // Given
        Long userId = 1L;
        when(pushHistoryMapper.countDeliveredAlertsSince(eq(userId), eq("EMAIL"), any()))
                .thenReturn(10); // Under limit of 20

        // When
        boolean exceeded = pushHistoryService.isRateLimitExceeded(userId, ChannelType.EMAIL);

        // Then
        assertFalse(exceeded);
    }

    @Test
    @DisplayName("Should use different rate limits per channel")
    void testDifferentRateLimits() {
        // Given
        Long userId = 1L;

        // WebSocket has limit of 100
        when(pushHistoryMapper.countDeliveredAlertsSince(eq(userId), eq("WEBSOCKET"), any()))
                .thenReturn(50);
        assertFalse(pushHistoryService.isRateLimitExceeded(userId, ChannelType.WEBSOCKET));

        // Email has limit of 20
        when(pushHistoryMapper.countDeliveredAlertsSince(eq(userId), eq("EMAIL"), any()))
                .thenReturn(50);
        assertTrue(pushHistoryService.isRateLimitExceeded(userId, ChannelType.EMAIL));
    }

    @Test
    @DisplayName("Should truncate long messages")
    void testMessageTruncation() {
        // Given
        Long userId = 1L;
        Long alertId = 100L;
        String longMessage = "a".repeat(600); // 600 characters
        PushResult result = PushResult.failed(ChannelType.EMAIL, longMessage);

        // When
        pushHistoryService.recordPush(userId, alertId, result);

        // Then
        ArgumentCaptor<PushHistory> captor = ArgumentCaptor.forClass(PushHistory.class);
        verify(pushHistoryMapper).insert(captor.capture());

        PushHistory history = captor.getValue();
        assertTrue(history.getMessage().length() <= 500);
        assertTrue(history.getMessage().endsWith("..."));
    }

    @Test
    @DisplayName("Should handle null result gracefully")
    void testNullResult() {
        // Given
        Long userId = 1L;
        Long alertId = 100L;

        // When
        pushHistoryService.recordPush(userId, alertId, null);

        // Then
        verify(pushHistoryMapper, never()).insert(any());
    }
}