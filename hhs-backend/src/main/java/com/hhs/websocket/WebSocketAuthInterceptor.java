package com.hhs.websocket;

import com.hhs.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * WebSocket Authentication Interceptor
 * Validates JWT token from Authorization header during handshake
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements WebSocketHandlerDecoratorFactory {

    private final JwtUtil jwtUtil;

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // Extract token from query parameter (WebSocket doesn't support custom headers)
                String query = session.getUri().getQuery();
                String token = null;

                if (query != null) {
                    // Parse query parameter: "token=xxx"
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2 && "token".equals(pair[0])) {
                            // URL decode the token (frontend uses encodeURIComponent)
                            token = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                            break;
                        }
                    }
                }

                if (token == null || token.isEmpty()) {
                    log.warn("WebSocket connection rejected: Missing token parameter");
                    session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing authorization token"));
                    return;
                }

                String jwt = token; // Token is already extracted, no "Bearer " prefix to remove

                try {
                    // Validate token
                    Long userId = jwtUtil.getUserId(jwt);
                    session.getAttributes().put("userId", userId);
                    log.info("WebSocket connection established for user: {}", userId);

                    // Proceed with connection
                    super.afterConnectionEstablished(session);
                } catch (Exception e) {
                    log.error("WebSocket authentication failed: {}", e.getMessage());
                    session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
                }
            }
        };
    }
}
