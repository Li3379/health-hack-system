package com.hhs.domain.handler;

import com.hhs.domain.event.AlertCreatedEvent;
import com.hhs.dto.AlertVO;
import com.hhs.service.PushHistoryService;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannel;
import com.hhs.service.push.PushResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Runtime wiring test for {@link AlertPushListener}.
 *
 * <p>Same shape as {@link ScoreUpdatedEventListenerLifecycleTest}: real event
 * publication through a real {@code ApplicationEventPublisher}, with
 * {@code @EnableTransactionManagement} so {@code TransactionalEventListenerFactory}
 * is registered. Without that registration a missing {@code AFTER_COMMIT} or
 * {@code fallbackExecution} silently passes — that is how the score-history bug
 * survived.</p>
 *
 * <p>The manager is the <em>real</em> {@code PushChannelManager} fed by a recording
 * channel. Mocking the manager itself does not work here: Mockito's subclass inherits
 * {@code @Autowired setChannels(List<PushChannel>)}, and Spring tries to inject it.</p>
 */
@SpringJUnitConfig(classes = {
        AlertPushListener.class,
        AlertPushListenerLifecycleTest.Config.class
})
@DisplayName("Alert Push Listener — 事务接线（真实事件发布）")
class AlertPushListenerLifecycleTest {

    /** 记录每次 push 的 userId，用于断言「推送是否发生」与「发生几次」。 */
    static class RecordingChannel implements PushChannel {
        final List<Long> pushedFor = new ArrayList<>();

        @Override
        public PushResult push(Long userId, AlertVO alert) {
            pushedFor.add(userId);
            return PushResult.success(ChannelType.WECOM);
        }

        @Override
        public ChannelType getChannelType() {
            return ChannelType.WECOM;
        }

        @Override
        public boolean isAvailable(Long userId) {
            return true;
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        RecordingChannel recordingChannel() {
            return new RecordingChannel();
        }

        /**
         * 只注册一个 PushChannel（RecordingChannel），让 pushAll 恰好推一次。
         * 不扫 {@code com.hhs.service.push.channel} 包 —— 那会拉进真实的
         * Feishu/WeCom/Email 实现，它们各自还要 UserPushConfigMapper 等依赖。
         */
        @Bean
        com.hhs.service.push.PushChannelManager pushChannelManager() {
            return new com.hhs.service.push.PushChannelManager();
        }

        @Bean
        PushHistoryService pushHistoryService() {
            return mock(PushHistoryService.class);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new StubTransactionManager();
        }
    }

    /**
     * Minimal manager whose commit can be told to fail.
     *
     * <p>That switch is what separates {@code BEFORE_COMMIT} from {@code AFTER_COMMIT}:
     * on a failing commit, {@code AbstractPlatformTransactionManager.processCommit}
     * runs {@code triggerBeforeCommit} (BEFORE_COMMIT listeners fire) before calling
     * {@code doCommit}, and only then rolls back. So a BEFORE_COMMIT listener would be
     * observed, an AFTER_COMMIT one would not. A no-op commit cannot tell them apart.</p>
     */
    static class StubTransactionManager extends AbstractPlatformTransactionManager {
        volatile boolean failCommit = false;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) {
                throw new IllegalStateException("simulated commit failure");
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private RecordingChannel recordingChannel;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StubTransactionManager stubTransactionManager;

    @BeforeEach
    void clear() {
        recordingChannel.pushedFor.clear();
    }

    private AlertCreatedEvent event() {
        AlertVO alert = new AlertVO();
        alert.setId(11L);
        alert.setAlertType("CRITICAL");
        alert.setAlertLevel("HIGH");
        alert.setTitle("t");
        alert.setMessage("m");
        return new AlertCreatedEvent(5L, alert);
    }

    @Test
    @DisplayName("无事务时监听器仍然执行 —— fallbackExecution=true 的语义")
    void publishesWithoutTransaction_pushesImmediately() {
        publisher.publishEvent(event());

        assertEquals(List.of(5L), recordingChannel.pushedFor);
    }

    @Test
    @DisplayName("事务回滚时监听器根本不触发 —— 回滚的告警不能发出去")
    void rolledBackTransaction_neverPushes() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(event());
            // 模拟业务失败：mark rollback-only 后 AFTER_COMMIT 不得执行
            status.setRollbackOnly();
        });

        assertTrue(recordingChannel.pushedFor.isEmpty(),
                "回滚的告警不得推送，实际推送了: " + recordingChannel.pushedFor);
    }

    @Test
    @DisplayName("事务提交后才推送 —— 提交前不得外呼")
    void committedTransaction_pushesAfterCommit() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<Long> duringTransaction = new ArrayList<>();

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(event());
            // 此刻仍在事务内，AFTER_COMMIT 尚未触发
            duringTransaction.addAll(recordingChannel.pushedFor);
        });

        assertTrue(duringTransaction.isEmpty(),
                "提交前不得推送，实际推送了: " + duringTransaction);
        assertEquals(List.of(5L), recordingChannel.pushedFor);
    }

    @Test
    @DisplayName("提交失败时也不得推送 —— 这一条把 AFTER_COMMIT 与 BEFORE_COMMIT 分开")
    void failedCommit_neverPushes() {
        stubTransactionManager.failCommit = true;
        try {
            TransactionTemplate tx = new TransactionTemplate(stubTransactionManager);

            assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                publisher.publishEvent(event());
            }));

            assertTrue(recordingChannel.pushedFor.isEmpty(),
                    "提交失败等同于没落库，不得推送，实际推送了: " + recordingChannel.pushedFor);
        } finally {
            stubTransactionManager.failCommit = false;
        }
    }
}
