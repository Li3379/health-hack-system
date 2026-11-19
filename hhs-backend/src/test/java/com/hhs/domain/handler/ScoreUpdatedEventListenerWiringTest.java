package com.hhs.domain.handler;

import com.hhs.domain.event.ScoreUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wiring contract of {@link ScoreUpdatedEventListener}.
 *
 * <p>{@code ScoreUpdatedEventListenerTest} calls the handler directly, which proves
 * the body is right but says nothing about whether Spring ever invokes it. That gap
 * is how a live defect survived: the handler was
 * {@code @TransactionalEventListener(AFTER_COMMIT)} while its publisher,
 * {@code HealthScoreServiceImpl.getScore()}, runs with no transaction. Spring skips
 * an AFTER_COMMIT listener when no transaction synchronization is active unless
 * {@code fallbackExecution} is set, so {@code health_score_history} stayed empty
 * however often the score endpoint was called — and the direct-call tests stayed
 * green throughout.</p>
 *
 * <p><b>Why this asserts the annotation instead of firing an event:</b> I built the
 * event-firing version first, in a {@code @SpringJUnitConfig} context, and it passed
 * with {@code fallbackExecution} removed — a context without a transaction manager
 * does not reproduce Spring's skip, so that test pinned nothing. The annotation
 * attribute is the actual precondition for the skip, so it is what gets asserted
 * here; the runtime consequence lives in the message.</p>
 */
@DisplayName("Score Updated Event Listener — 事件接线契约")
class ScoreUpdatedEventListenerWiringTest {

    private static Method handler() throws NoSuchMethodException {
        return ScoreUpdatedEventListener.class.getMethod(
                "onScoreUpdated", ScoreUpdatedEvent.class);
    }

    @Test
    @DisplayName("fallbackExecution 必须为 true —— 否则无事务的发布者会让监听器被静默跳过")
    void handlerDeclaresFallbackExecution() throws Exception {
        TransactionalEventListener annotation =
                handler().getAnnotation(TransactionalEventListener.class);

        assertNotNull(annotation,
                "handler 必须保留 @TransactionalEventListener；改成普通 @EventListener 会丢掉"
                        + "「等事务提交后再写」的语义");
        assertTrue(annotation.fallbackExecution(),
                "fallbackExecution 必须为 true：HealthScoreServiceImpl.getScore() 是无事务的纯读，"
                        + "Spring 在无事务同步时默认跳过 AFTER_COMMIT 监听器，"
                        + "health_score_history 会永远为空");
    }

    @Test
    @DisplayName("phase 仍是 AFTER_COMMIT —— 事务性调用方保留「提交后再写」的保证")
    void handlerKeepsAfterCommitPhase() throws Exception {
        TransactionalEventListener annotation =
                handler().getAnnotation(TransactionalEventListener.class);

        assertNotNull(annotation);
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase(),
                "phase 不应退化为 BEFORE_COMMIT 或普通监听");
    }
}
