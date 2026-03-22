package com.hhs.service.impl;

import com.hhs.entity.PushHistory;
import com.hhs.mapper.PushHistoryMapper;
import com.hhs.service.PushHistoryService;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Push history service implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushHistoryServiceImpl implements PushHistoryService {

    private final PushHistoryMapper pushHistoryMapper;

    /**
     * Default rate limits per channel (per hour)
     */
    @Value("${push.rate-limit.websocket:100}")
    private int webSocketRateLimit;

    @Value("${push.rate-limit.email:20}")
    private int emailRateLimit;

    @Value("${push.rate-limit.wecom:30}")
    private int wecomRateLimit;

    @Value("${push.rate-limit.feishu:30}")
    private int feishuRateLimit;

    @Override
    @Async("pushExecutor")
    public void recordPush(Long userId, Long alertId, PushResult result) {
        if (result == null) {
            return;
        }

        String status = result.isSuccess() ? "SUCCESS" : "FAILED";
        if (result.getMessage() != null && result.getMessage().startsWith("Skipped:")) {
            status = "SKIPPED";
        }

        recordPush(userId, alertId, result.getChannelType(), status, result.getMessage());
    }

    @Override
    @Async("pushExecutor")
    public void recordPush(Long userId, Long alertId, ChannelType channelType,
                           String status, String message) {
        try {
            PushHistory history = new PushHistory();
            history.setUserId(userId);
            history.setAlertId(alertId);
            history.setChannelType(channelType.name());
            history.setStatus(status);
            history.setMessage(truncateMessage(message, 500));
            history.setPushedAt(LocalDateTime.now());

            pushHistoryMapper.insert(history);
            log.debug("Recorded push history: userId={}, channel={}, status={}",
                    userId, channelType, status);
        } catch (Exception e) {
            log.error("Failed to record push history: {}", e.getMessage());
        }
    }

    @Override
    public List<PushHistory> getRecentHistory(Long userId, int limit) {
        return pushHistoryMapper.findRecentByUserId(userId, Math.min(limit, 100));
    }

    @Override
    public int getPushCountSince(Long userId, ChannelType channelType, LocalDateTime since) {
        return pushHistoryMapper.countByUserAndChannelSince(userId, channelType.name(), since);
    }

    @Override
    public List<Map<String, Object>> getChannelStats(Long userId, LocalDateTime since) {
        return pushHistoryMapper.getChannelStatsByUserSince(userId, since);
    }

    @Override
    public boolean isRateLimitExceeded(Long userId, ChannelType channelType) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        int count = getPushCountSince(userId, channelType, oneHourAgo);
        int limit = getRateLimit(channelType);

        boolean exceeded = count >= limit;
        if (exceeded) {
            log.info("Rate limit exceeded for user {} on channel {}: {}/{}",
                    userId, channelType, count, limit);
        }
        return exceeded;
    }

    /**
     * Get rate limit for a channel
     */
    private int getRateLimit(ChannelType channelType) {
        return switch (channelType) {
            case WEBSOCKET -> webSocketRateLimit;
            case EMAIL -> emailRateLimit;
            case WECOM -> wecomRateLimit;
            case FEISHU -> feishuRateLimit;
        };
    }

    /**
     * Truncate message to fit database column
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() > maxLength
                ? message.substring(0, maxLength - 3) + "..."
                : message;
    }
}