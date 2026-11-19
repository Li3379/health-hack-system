package com.hhs.domain.handler;

import com.hhs.domain.event.ScoreUpdatedEvent;
import com.hhs.service.ScoreHistoryService;
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
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

/**
 * Runtime wiring test for {@link ScoreUpdatedEventListener}.
 *
 * <p>This fires a real event through {@link ApplicationEventPublisher} and checks the
 * handler actually ran, which is the property the direct-call tests in
 * {@code ScoreUpdatedEventListenerTest} cannot see.</p>
 *
 * <p><b>{@code @EnableTransactionManagement} is the part that makes this test real.</b>
 * Without it Spring does not register {@code TransactionalEventListenerFactory}, so
 * {@code @TransactionalEventListener} degrades to a plain listener and a missing
 * {@code fallbackExecution} never shows up. My first attempt omitted it and passed
 * against the buggy annotation — it pinned nothing. With it registered, the
 * AFTER_COMMIT phase is honoured, no transaction synchronization is active in this
 * test, and the skip only happens when {@code fallbackExecution} is false.</p>
 */
@SpringJUnitConfig(classes = {
        ScoreUpdatedEventListener.class,
        ScoreUpdatedEventListenerLifecycleTest.Config.class
})
@DisplayName("Score Updated Event Listener — 运行时接线（真实事件发布）")
class ScoreUpdatedEventListenerLifecycleTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {
        /**
         * A minimal manager is enough: the listener only needs to know whether
         * transaction synchronization is active, and no @Transactional method runs here.
         */
        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }

    @MockBean
    private ScoreHistoryService scoreHistoryService;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Test
    @DisplayName("无事务时监听器仍然执行 —— 曾导致 health_score_history 永远为空的那条路径")
    void publishesWithoutActiveTransaction_handlerRuns() {
        // No transaction is active here, mirroring HealthScoreServiceImpl.getScore().
        publisher.publishEvent(new ScoreUpdatedEvent(
                42L, 82, "GOOD",
                Map.of("healthProfile", Map.of("score", 90)),
                Map.of("cardiovascular", 90)));

        verify(scoreHistoryService).saveScore(anyLong(), any());
    }
}