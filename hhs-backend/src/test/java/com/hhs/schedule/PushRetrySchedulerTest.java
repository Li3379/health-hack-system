package com.hhs.schedule;

import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.PushHistory;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.service.PushHistoryService;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannelManager;
import com.hhs.service.push.PushResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推送失败补偿任务（PUSH-10）。
 *
 * <p>修复前失败行无人扫描：{@code AlertPushListener} 吞掉异常只打日志，push_history 里的
 * FAILED 行永远躺着。本类钉住补偿任务的关键性质：
 * <ul>
 *   <li>失败的真实告警会被重新投递，且成功后备注为「不再调度」；</li>
 *   <li>重试次数到上限后停止，不再继续打服务商；</li>
 *   <li>告警已被删除时停止，而不是反复重试一条发不出去的行；</li>
 *   <li>单行失败不中断整批；</li>
 *   <li>退避是递增的。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("推送失败补偿任务")
class PushRetrySchedulerTest {

    @Mock
    private PushHistoryService pushHistoryService;

    @Mock
    private PushChannelManager pushChannelManager;

    @Mock
    private HealthAlertMapper healthAlertMapper;

    @InjectMocks
    private PushRetryScheduler scheduler;

    private static final Long USER_ID = 7L;
    private static final Long ALERT_ID = 42L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 3);
        ReflectionTestUtils.setField(scheduler, "baseDelayMinutes", 2L);
    }

    private PushHistory failedRow(long id, int retryCount) {
        PushHistory history = new PushHistory();
        history.setId(id);
        history.setUserId(USER_ID);
        history.setAlertId(ALERT_ID);
        history.setChannelType("EMAIL");
        history.setStatus("FAILED");
        history.setRetryCount(retryCount);
        history.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        return history;
    }

    private HealthAlert existingAlert() {
        HealthAlert alert = new HealthAlert();
        alert.setId(ALERT_ID);
        alert.setUserId(USER_ID);
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("心率异常");
        alert.setMessage("心率超过阈值");
        alert.setMetricKey("heartRate");
        return alert;
    }

    @Test
    @DisplayName("重试成功：重新投递该告警，并把这一行标记为不再调度")
    void retrySucceeds() {
        when(pushHistoryService.findDueForRetry(3, 50)).thenReturn(List.of(failedRow(1L, 0)));
        when(healthAlertMapper.selectById(ALERT_ID)).thenReturn(existingAlert());
        when(pushChannelManager.push(eq(USER_ID), any(AlertVO.class), eq(ChannelType.EMAIL)))
                .thenReturn(PushResult.success(ChannelType.EMAIL));

        scheduler.retryFailedPushes();

        ArgumentCaptor<AlertVO> alert = ArgumentCaptor.forClass(AlertVO.class);
        verify(pushChannelManager).push(eq(USER_ID), alert.capture(), eq(ChannelType.EMAIL));
        // 重试的是告警内容本身，不是历史行
        assertEquals("心率异常", alert.getValue().getTitle());
        assertEquals(ALERT_ID, alert.getValue().getId());

        // 成功后不再排期（nextRetryAt = null 即「停」）
        verify(pushHistoryService).scheduleRetry(eq(1L), eq(1), eq(null));
    }

    @Test
    @DisplayName("重试失败且未到上限：安排下一次，延迟按退避递增")
    void retryFailureSchedulesNextAttempt() {
        when(pushHistoryService.findDueForRetry(3, 50)).thenReturn(List.of(failedRow(2L, 1)));
        when(healthAlertMapper.selectById(ALERT_ID)).thenReturn(existingAlert());
        when(pushChannelManager.push(any(), any(), any()))
                .thenReturn(PushResult.failed(ChannelType.EMAIL, "still broken"));

        scheduler.retryFailedPushes();

        ArgumentCaptor<LocalDateTime> next = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(pushHistoryService).scheduleRetry(eq(2L), eq(2), next.capture());
        assertTrue(next.getValue().isAfter(LocalDateTime.now().plusMinutes(3)),
                "第 2 次重试应至少等约 4 分钟（base*2^1）: " + next.getValue());
    }

    @Test
    @DisplayName("达到上限：停止调度，不再排下一次")
    void stopsAtMaxAttempts() {
        // retryCount 已是 2，本次是第 3 次 = maxAttempts
        when(pushHistoryService.findDueForRetry(3, 50)).thenReturn(List.of(failedRow(3L, 2)));
        when(healthAlertMapper.selectById(ALERT_ID)).thenReturn(existingAlert());
        when(pushChannelManager.push(any(), any(), any()))
                .thenReturn(PushResult.failed(ChannelType.EMAIL, "still broken"));

        scheduler.retryFailedPushes();

        ArgumentCaptor<LocalDateTime> next = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(pushHistoryService).scheduleRetry(eq(3L), eq(3), next.capture());
        assertNull(next.getValue(), "达到上限后 nextRetryAt 必须为 null");
    }

    @Test
    @DisplayName("告警已被删除：停止调度，不发出任何请求")
    void stopsWhenAlertGone() {
        when(pushHistoryService.findDueForRetry(3, 50)).thenReturn(List.of(failedRow(4L, 0)));
        when(healthAlertMapper.selectById(ALERT_ID)).thenReturn(null);

        scheduler.retryFailedPushes();

        verify(pushChannelManager, never()).push(any(), any(), any());
        verify(pushHistoryService).scheduleRetry(eq(4L), eq(3), eq(null));
    }

    @Test
    @DisplayName("未知渠道类型：停止调度，不尝试投递")
    void stopsOnUnknownChannel() {
        PushHistory broken = failedRow(5L, 0);
        broken.setChannelType("PIGEON");
        when(pushHistoryService.findDueForRetry(3, 50)).thenReturn(List.of(broken));

        scheduler.retryFailedPushes();

        verify(pushChannelManager, never()).push(any(), any(), any());
        verify(pushHistoryService).scheduleRetry(eq(5L), eq(3), eq(null));
    }

    @Test
    @DisplayName("单行抛异常不中断整批：后面的行仍被处理")
    void oneBadRowDoesNotStopTheBatch() {
        PushHistory bad = failedRow(6L, 0);
        PushHistory good = failedRow(7L, 0);
        when(pushHistoryService.findDueForRetry(3, 50)).thenReturn(List.of(bad, good));
        when(healthAlertMapper.selectById(ALERT_ID)).thenReturn(existingAlert());
        when(pushChannelManager.push(any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(PushResult.success(ChannelType.EMAIL));

        scheduler.retryFailedPushes();

        // 两行都被尝试过：第一行抛异常被吞，第二行仍走成功分支
        verify(pushChannelManager, times(2)).push(any(), any(), any());
        verify(pushHistoryService).scheduleRetry(eq(7L), eq(1), eq(null));
    }

    @Test
    @DisplayName("没有到期的行时不发起任何投递")
    void noDueRowsIsNoop() {
        when(pushHistoryService.findDueForRetry(anyInt(), anyInt())).thenReturn(List.of());

        scheduler.retryFailedPushes();

        verify(pushChannelManager, never()).push(any(), any(), any());
    }

    @Test
    @DisplayName("退避随尝试次数递增（2 → 4 → 8 分钟）")
    void backoffGrows() {
        LocalDateTime first = scheduler.nextAttemptAt(1);
        LocalDateTime second = scheduler.nextAttemptAt(2);
        LocalDateTime third = scheduler.nextAttemptAt(3);

        assertTrue(second.isAfter(first), "第二次应晚于第一次");
        assertTrue(third.isAfter(second), "第三次应晚于第二次");
        // 与 now 比较时留出执行耗时余量
        assertTrue(first.isBefore(LocalDateTime.now().plusMinutes(2).plusSeconds(5)));
        assertTrue(third.isAfter(LocalDateTime.now().plusMinutes(7)));
    }
}