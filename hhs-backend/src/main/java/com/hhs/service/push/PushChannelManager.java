package com.hhs.service.push;

import com.hhs.dto.AlertVO;
import com.hhs.service.PushHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push channel manager for coordinating multi-channel alert delivery
 */
@Slf4j
@Service
public class PushChannelManager {

    private Map<ChannelType, PushChannel> channels = new ConcurrentHashMap<>();
    private PushHistoryService pushHistoryService;

    @Autowired
    public void setChannels(List<PushChannel> channelList) {
        Map<ChannelType, PushChannel> tempMap = new ConcurrentHashMap<>();
        for (PushChannel channel : channelList) {
            tempMap.put(channel.getChannelType(), channel);
            log.info("Registered push channel: {}", channel.getChannelType());
        }
        // Make the map unmodifiable after initialization for thread safety
        this.channels = Collections.unmodifiableMap(tempMap);
    }

    @Autowired
    public void setPushHistoryService(PushHistoryService pushHistoryService) {
        this.pushHistoryService = pushHistoryService;
    }

    /**
     * Push alert through specified channel
     */
    public PushResult push(Long userId, AlertVO alert, ChannelType channelType) {
        if (alert == null) {
            PushResult result = PushResult.failed(channelType, "Alert is null");
            recordHistory(userId, null, result);
            return result;
        }

        PushChannel channel = channels.get(channelType);
        if (channel == null) {
            log.warn("Channel not found: {}", channelType);
            PushResult result = PushResult.failed(channelType, "Channel not registered");
            recordHistory(userId, alert.getId(), result);
            return result;
        }

        // Check rate limit before pushing
        if (pushHistoryService != null && pushHistoryService.isRateLimitExceeded(userId, channelType)) {
            log.info("Rate limit exceeded for user {} on channel {}", userId, channelType);
            PushResult result = PushResult.skipped(channelType, "Rate limit exceeded");
            recordHistory(userId, alert.getId(), result);
            return result;
        }

        if (!channel.isAvailable(userId)) {
            log.debug("Channel {} not available for user {}", channelType, userId);
            PushResult result = PushResult.skipped(channelType, "Channel not available");
            recordHistory(userId, alert.getId(), result);
            return result;
        }

        try {
            PushResult result = channel.push(userId, alert);
            recordHistory(userId, alert.getId(), result);
            return result;
        } catch (Exception e) {
            log.error("Push failed via {}: {}", channelType, e.getMessage(), e);
            PushResult result = PushResult.failed(channelType, e.getMessage());
            recordHistory(userId, alert.getId(), result);
            return result;
        }
    }

    /**
     * Record push history if service is available
     */
    private void recordHistory(Long userId, Long alertId, PushResult result) {
        if (pushHistoryService != null) {
            pushHistoryService.recordPush(userId, alertId, result);
        }
    }

    /**
     * Push alert through multiple channels
     *
     * @param userId       target user ID
     * @param alert        alert to push
     * @param channelTypes channels to use (in priority order)
     * @return list of push results
     */
    public List<PushResult> pushMulti(Long userId, AlertVO alert, List<ChannelType> channelTypes) {
        List<PushResult> results = new ArrayList<>();

        for (ChannelType channelType : channelTypes) {
            PushResult result = push(userId, alert, channelType);
            results.add(result);

            // Log result
            if (result.isSuccess()) {
                log.info("Alert {} pushed successfully via {} to user {}",
                        alert.getId(), channelType, userId);
            } else {
                log.debug("Alert {} push skipped/failed via {} to user {}: {}",
                        alert.getId(), channelType, userId, result.getMessage());
            }
        }

        return results;
    }

    /**
     * Push alert through all available channels
     */
    public List<PushResult> pushAll(Long userId, AlertVO alert) {
        List<ChannelType> availableChannels = new ArrayList<>();

        for (Map.Entry<ChannelType, PushChannel> entry : channels.entrySet()) {
            if (entry.getValue().isAvailable(userId)) {
                availableChannels.add(entry.getKey());
            }
        }

        return pushMulti(userId, alert, availableChannels);
    }

    /**
     * Get channel by type
     */
    public PushChannel getChannel(ChannelType channelType) {
        return channels.get(channelType);
    }

    /**
     * Check if channel is registered
     */
    public boolean hasChannel(ChannelType channelType) {
        return channels.containsKey(channelType);
    }
}