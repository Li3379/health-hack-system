package com.hhs.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.dto.AlertVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Handler for Health Alerts
 * Manages user connections and pushes real-time alerts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // Store active WebSocket sessions by user ID
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("WebSocket session added for user: {}, total sessions: {}", userId, userSessions.size());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
            log.info("WebSocket session removed for user: {}, total sessions: {}", userId, userSessions.size());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Handle incoming messages if needed (e.g., ping/pong)
        String payload = message.getPayload();
        log.debug("Received WebSocket message: {}", payload);

        // Echo back for testing
        session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: {}", exception.getMessage());
        if (session.isOpen()) {
            session.close();
        }
    }

    /**
     * Send an alert to a specific user using atomic operation
     *
     * @param userId User ID
     * @param alert Alert to send
     */
    public void sendAlertToUser(Long userId, AlertVO alert) {
        userSessions.computeIfPresent(userId, (id, session) -> {
            try {
                if (session.isOpen()) {
                    String message = objectMapper.writeValueAsString(Map.of(
                            "type", "alert",
                            "data", alert
                    ));
                    session.sendMessage(new TextMessage(message));
                    log.info("Alert sent to user: {}, type: {}", userId, alert.getAlertType());
                } else {
                    // Session is closed, remove it from the map
                    log.debug("Session closed for user: {}, removing from map", userId);
                    return null;
                }
            } catch (IOException | IllegalStateException e) {
                log.warn("Failed to send alert to user {}: {}", userId, e.getMessage());
                // Return null to remove the session from the map
                return null;
            }
            return session;
        });
    }

    /**
     * Check if user has an active WebSocket connection
     *
     * @param userId User ID
     * @return true if connected
     */
    public boolean isUserConnected(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * Get count of active connections
     *
     * @return Number of active sessions
     */
    public int getActiveConnectionCount() {
        return userSessions.size();
    }
}
