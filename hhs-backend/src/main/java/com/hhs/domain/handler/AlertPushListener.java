package com.hhs.domain.handler;

import com.hhs.domain.event.AlertCreatedEvent;
import com.hhs.service.push.PushChannelManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers a freshly created alert through every configured push channel.
 *
 * <p>Runs after the publishing transaction commits, so a rolled-back alert never
 * reaches email / IM. {@code fallbackExecution = true} keeps non-transactional
 * callers working — see {@link ScoreUpdatedEventListener} for the same reasoning.</p>
 *
 * <p>This listener replaces the direct {@code webSocketHandler.sendAlertToUser}
 * call that used to live in {@code AlertServiceImpl.createAlert}. WebSocket is now
 * one channel among four, so the alert must not also be sent directly or online
 * users would receive the same frame twice.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertPushListener {

    private static final String PUSH_TIMER = "hhs.alert.push";

    private final PushChannelManager pushChannelManager;
    private final MeterRegistry meterRegistry;

    /**
     * Push one alert to all available channels.
     *
     * <p>Failures are swallowed on purpose: an unreachable webhook must not fail
     * the alert write. The timer records per-alert wall time, so a slow channel
     * (pushRestTemplate allows 5s connect / 10s read) shows up as a rising p95
     * instead of an invisible stall.</p>
     */
    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlertCreated(AlertCreatedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            pushChannelManager.pushAll(event.getUserId(), event.getAlert());
            log.debug("Pushed alert {} for user {}", event.getAlert().getId(), event.getUserId());
        } catch (Exception e) {
            outcome = "error";
            log.error("Failed to push alert {} for user {}: {}",
                    event.getAlert().getId(), event.getUserId(), e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder(PUSH_TIMER)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }
}
