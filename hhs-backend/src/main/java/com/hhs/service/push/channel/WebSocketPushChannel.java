package com.hhs.service.push.channel;

import com.hhs.dto.AlertVO;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannel;
import com.hhs.service.push.PushResult;
import com.hhs.websocket.HealthWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket push channel implementation.
 * Checks both user connection status and push preference settings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPushChannel implements PushChannel {

    private final HealthWebSocketHandler webSocketHandler;
    private final UserPushConfigMapper userPushConfigMapper;

    @Override
    public PushResult push(Long userId, AlertVO alert) {
        if (!webSocketHandler.isUserConnected(userId)) {
            log.debug("User {} is not connected via WebSocket", userId);
            return PushResult.offline(getChannelType());
        }

        try {
            webSocketHandler.sendAlertToUser(userId, alert);
            return PushResult.success(getChannelType());
        } catch (Exception e) {
            log.error("Failed to push alert via WebSocket for user {}: {}", userId, e.getMessage());
            return PushResult.failed(getChannelType(), e.getMessage());
        }
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WEBSOCKET;
    }

    @Override
    public boolean isAvailable(Long userId) {
        // First check if user is connected via WebSocket
        if (!webSocketHandler.isUserConnected(userId)) {
            return false;
        }

        return isConfigured(userId);
    }

    /**
     * Configured-ness ignores the live connection.
     *
     * <p>A closed tab is not a configuration problem: the user still enabled WebSocket push.
     * {@code pushAll} uses this to decide whether "not delivered" deserves a history row —
     * recording one SKIPPED row per alert for every offline user would drown the history, while
     * a user who explicitly disabled (or never configured) the channel should see why nothing
     * arrived.
     */
    @Override
    public boolean isConfigured(Long userId) {
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, "WEBSOCKET");
        // If no config exists, default to enabled (for backward compatibility)
        // If config exists, respect the enabled setting
        if (config == null) {
            return true;  // Default: enabled if not explicitly disabled
        }
        return config.getEnabled() != null && config.getEnabled() == 1;
    }

    @Override
    public boolean supportsOffline() {
        return false;
    }
}