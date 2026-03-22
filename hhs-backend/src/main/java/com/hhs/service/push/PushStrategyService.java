package com.hhs.service.push;

import com.hhs.dto.AlertVO;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Push strategy service for determining which channels to use based on alert level
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushStrategyService {

    private final UserPushConfigMapper userPushConfigMapper;
    private final PushChannelManager pushChannelManager;

    /**
     * Determine push channels based on alert level
     *
     * @param alertLevel the alert level (HIGH, MEDIUM, LOW)
     * @return list of channels to use
     */
    public List<ChannelType> getChannelsForAlertLevel(String alertLevel) {
        List<ChannelType> channels = new ArrayList<>();

        // Always try WebSocket first
        channels.add(ChannelType.WEBSOCKET);

        switch (alertLevel) {
            case "HIGH" -> {
                // Critical alerts: use all available channels
                channels.add(ChannelType.EMAIL);
                channels.add(ChannelType.WECOM);
                channels.add(ChannelType.FEISHU);
            }
            case "MEDIUM" -> {
                // Warning alerts: WebSocket + Email
                channels.add(ChannelType.EMAIL);
            }
            case "LOW" -> {
                // Info alerts: WebSocket only (already added)
            }
            default -> log.warn("Unknown alert level: {}", alertLevel);
        }

        return channels;
    }

    /**
     * Get enabled channels for a specific user
     *
     * @param userId     the user ID
     * @param alertLevel the alert level
     * @return list of enabled channels for this user
     */
    public List<ChannelType> getEnabledChannelsForUser(Long userId, String alertLevel) {
        List<ChannelType> desiredChannels = getChannelsForAlertLevel(alertLevel);
        List<ChannelType> enabledChannels = new ArrayList<>();

        // Get user's enabled push configs
        List<UserPushConfig> userConfigs = userPushConfigMapper.findEnabledByUserId(userId);

        for (ChannelType channel : desiredChannels) {
            // Check if this channel is available for the user
            if (pushChannelManager.hasChannel(channel)) {
                PushChannel pushChannel = pushChannelManager.getChannel(channel);
                if (pushChannel.isAvailable(userId)) {
                    enabledChannels.add(channel);
                }
            }
        }

        log.debug("Enabled channels for user {} with alert level {}: {}",
                userId, alertLevel, enabledChannels);

        return enabledChannels;
    }

    /**
     * Push alert with appropriate strategy
     *
     * @param userId the target user ID
     * @param alert  the alert to push
     * @return list of push results
     */
    public List<PushResult> pushWithStrategy(Long userId, AlertVO alert) {
        List<ChannelType> channels = getEnabledChannelsForUser(userId, alert.getAlertLevel());

        if (channels.isEmpty()) {
            log.warn("No available push channels for user {}", userId);
            return List.of(PushResult.skipped(ChannelType.WEBSOCKET, "No available channels"));
        }

        return pushChannelManager.pushMulti(userId, alert, channels);
    }
}