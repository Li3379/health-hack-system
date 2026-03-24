package com.hhs.config;

import com.hhs.websocket.WebSocketAuthInterceptor;
import com.hhs.websocket.HealthWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * WebSocket Configuration
 * Enables WebSocket for real-time alert notifications
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final HealthWebSocketHandler healthWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${app.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Decorate the handler with authentication
        WebSocketHandlerDecorator decoratedHandler = (WebSocketHandlerDecorator)
                webSocketAuthInterceptor.decorate(healthWebSocketHandler);

        // Split allowed origins by comma
        String[] origins = allowedOrigins.split(",");

        // Register both endpoints for backward compatibility
        registry.addHandler(decoratedHandler, "/ws/alerts", "/ws/realtime")
                .setAllowedOrigins(origins);
    }
}