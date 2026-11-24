package com.hhs.domain.handler;

import com.hhs.domain.event.AlertCreatedEvent;
import com.hhs.dto.AlertVO;
import com.hhs.service.push.PushChannelManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertPushListener}.
 *
 * <p>Covers two of the four guards the review asked for that a plain unit test
 * <em>can</em> express: (c) only one pushAll per alert, and (d) a throwing
 * pushAll must not propagate. Rollback / no-transaction semantics are covered in
 * {@link AlertPushListenerLifecycleTest} because they need the real
 * {@code TransactionalEventListenerFactory}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Alert Push Listener — 单元行为")
class AlertPushListenerTest {

    @Mock
    private PushChannelManager pushChannelManager;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AlertPushListener listener() {
        return new AlertPushListener(pushChannelManager, meterRegistry);
    }

    private AlertCreatedEvent event() {
        AlertVO alert = new AlertVO();
        alert.setId(9L);
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("t");
        alert.setMessage("m");
        return new AlertCreatedEvent(3L, alert);
    }

    @Test
    @DisplayName("每条告警只调用一次 pushAll（防止直接 WS + pushAll 双发）")
    void pushesExactlyOnce() {
        listener().onAlertCreated(event());

        verify(pushChannelManager, times(1)).pushAll(eq(3L), any(AlertVO.class));
        verifyNoMoreInteractions(pushChannelManager);
    }

    @Test
    @DisplayName("pushAll 抛异常时监听器吞掉，不污染调用方")
    void swallowsPushFailure() {
        doThrow(new IllegalStateException("webhook down"))
                .when(pushChannelManager).pushAll(any(), any());

        // must not throw
        listener().onAlertCreated(event());

        verify(pushChannelManager, times(1)).pushAll(eq(3L), any(AlertVO.class));
    }

    @Test
    @DisplayName("推送耗时进入 hhs.alert.push timer，失败打 outcome=error")
    void recordsTimerWithOutcome() {
        doThrow(new IllegalStateException("boom")).when(pushChannelManager).pushAll(any(), any());

        listener().onAlertCreated(event());

        var timer = meterRegistry.find("hhs.alert.push").tag("outcome", "error").timer();
        org.junit.jupiter.api.Assertions.assertNotNull(timer, "失败也必须留下计时样本");
        org.junit.jupiter.api.Assertions.assertEquals(1L, timer.count());
    }
}
