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
            // Two different situations hide behind "not available", and they deserve different
            // treatment in the history:
            //
            //  - never configured (no email address, no webhook, WS switch off): record SKIPPED.
            //    This is the fact the user needs — the alert happened, but this channel could
            //    never have delivered it.
            //  - configured but unreachable right now (WebSocket tab closed): do NOT record.
            //    pushAll reaches every channel for every alert, so writing here would add one
            //    SKIPPED row per alert per offline user and bury the history under noise.
            //
            // Return the result either way so callers still see "skipped" rather than a success.
            boolean configured = channel.isConfigured(userId);
            log.debug("Channel {} not available for user {} (configured={})", channelType, userId, configured);
            PushResult result = configured
                    ? PushResult.skipped(channelType, "Channel temporarily unreachable")
                    : PushResult.skipped(channelType, "Channel not configured");
            if (!configured) {
                recordHistory(userId, alert.getId(), result);
            }
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
     * Push alert through every registered channel.
     *
     * <p>This used to pre-filter with {@code isAvailable()} and hand only the survivors to
     * {@link #pushMulti}. A channel the user never configured therefore never reached
     * {@link #push}, so it wrote no history row at all — "no email configured" and "no alert was
     * ever produced" looked identical in the UI, and the user concluded the system had raised
     * nothing. A single-channel {@code push()} has always recorded SKIPPED in that situation
     * (see its {@code !channel.isAvailable} branch), so the two entry points disagreed about the
     * same fact.
     *
     * <p>Every registered channel now goes through {@link #push}, which records the two cases
     * differently: <em>not configured</em> writes a SKIPPED row (the absence is real information),
     * while <em>configured but unreachable right now</em> stays silent — otherwise every alert
     * would add one SKIPPED WEBSOCKET row per offline user and bury the history.
     */
    public List<PushResult> pushAll(Long userId, AlertVO alert) {
        // Every registered channel participates; push() decides what deserves a history row.
        List<ChannelType> registered = new ArrayList<>(channels.keySet());
        return pushMulti(userId, alert, registered);
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