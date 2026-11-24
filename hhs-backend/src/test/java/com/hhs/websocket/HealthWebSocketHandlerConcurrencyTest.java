package com.hhs.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.dto.AlertVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 同一用户并发投递的串行性证明。
 *
 * <p>S3 把告警推送从请求线程搬到 {@code push-} 线程池后，同一用户的并发告警会并发进入
 * {@link HealthWebSocketHandler#sendAlertToUser}。复审认为那里「没有任何同步」。
 * 该 handler 用的是 {@code ConcurrentHashMap.computeIfPresent}，按 key 持有 bin 锁，
 * 因此同一 userId 的调用应当串行。这条断言把该性质钉住：任何人把它改回
 * {@code get()} + {@code put()} 或把手写锁去掉，用例都会红。</p>
 */
@DisplayName("WebSocket 同一用户并发投递")
class HealthWebSocketHandlerConcurrencyTest {

    private static WebSocketSession openSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("userId", 7L));
        return session;
    }

    @Test
    @DisplayName("同一用户的并发 sendAlertToUser 不会交叠执行 sendMessage")
    void concurrentSendsForSameUserDoNotOverlap() throws Exception {
        HealthWebSocketHandler handler = new HealthWebSocketHandler(new ObjectMapper());
        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicBoolean overlapped = new AtomicBoolean(false);
        AtomicInteger delivered = new AtomicInteger();

        // sendMessage 里插入一个短窗口，若两次调用交叠就会观察到 inFlight > 1
        org.mockito.Mockito.doAnswer(invocation -> {
            if (inFlight.incrementAndGet() > 1) {
                overlapped.set(true);
            }
            Thread.sleep(20);
            inFlight.decrementAndGet();
            delivered.incrementAndGet();
            return null;
        }).when(session).sendMessage(any(TextMessage.class));

        AlertVO alert = new AlertVO();
        alert.setId(1L);
        alert.setAlertType("WARNING");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                handler.sendAlertToUser(7L, alert);
            });
        }
        tasks.forEach(pool::submit);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "workers did not finish");

        assertEquals(threads, delivered.get(), "每条告警都应投递一次");
        assertFalse(overlapped.get(),
                "computeIfPresent 应按 userId 串行；观察到 sendMessage 交叠说明串行性已丢失");
    }

    @Test
    @DisplayName("不同用户的调用不应被互相阻塞")
    void differentUsersAreIndependent() throws Exception {
        HealthWebSocketHandler handler = new HealthWebSocketHandler(new ObjectMapper());

        Map<Long, WebSocketSession> sessions = new HashMap<>();
        for (long uid = 1; uid <= 2; uid++) {
            WebSocketSession s = mock(WebSocketSession.class);
            when(s.isOpen()).thenReturn(true);
            when(s.getAttributes()).thenReturn(Map.of("userId", uid));
            sessions.put(uid, s);
            handler.afterConnectionEstablished(s);
        }

        AlertVO alert = new AlertVO();
        alert.setId(1L);
        alert.setAlertType("WARNING");

        for (long uid = 1; uid <= 2; uid++) {
            handler.sendAlertToUser(uid, alert);
        }

        for (WebSocketSession s : sessions.values()) {
            org.mockito.Mockito.verify(s).sendMessage(any(TextMessage.class));
        }
    }
}
