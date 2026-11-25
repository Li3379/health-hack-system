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
     * First retry delay. Each subsequent attempt backs off exponentially:
     * {@code base * 2^(retryCount-1)} — see {@link com.hhs.schedule.PushRetryScheduler}.
     */
    private static final java.time.Duration RETRY_BASE_DELAY = java.time.Duration.ofMinutes(2);

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
            LocalDateTime now = LocalDateTime.now();
            PushHistory history = new PushHistory();
            history.setUserId(userId);
            history.setAlertId(alertId);
            history.setChannelType(channelType.name());
            history.setStatus(status);
            history.setMessage(truncateMessage(message, 500));
            history.setPushedAt(now);
            history.setRetryCount(0);
            // 真实告警的失败从下一次扫描起进入补偿队列；测试推送（alertId 为空）与 SKIPPED
            // 一律不排期 —— 前者是诊断动作，后者是「没配置/被限流」，重试都无意义。
            if ("FAILED".equals(status) && alertId != null) {
                history.setNextRetryAt(now.plus(RETRY_BASE_DELAY));
            }

            pushHistoryMapper.insert(history);
            log.debug("Recorded push history: userId={}, channel={}, status={}, retryScheduled={}",
                    userId, channelType, status, history.getNextRetryAt() != null);
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
    public List<PushHistory> getLatestPerChannelSince(Long userId, LocalDateTime since) {
        return pushHistoryMapper.findLatestPerChannelSince(userId, since);
    }

    @Override
    public void scheduleRetry(Long historyId, int retryCount, LocalDateTime nextRetryAt) {
        // 显式 UPDATE 而不是 updateById：nextRetryAt=null 必须真的把列清空才算「不再调度」，
        // 而 MyBatis-Plus 默认的更新策略会跳过 null 字段。
        pushHistoryMapper.updateRetrySchedule(historyId, retryCount, nextRetryAt);
        log.debug("Scheduled retry for push history {}: count={}, next={}",
                historyId, retryCount, nextRetryAt);
    }

    @Override
    public List<PushHistory> findDueForRetry(int maxRetries, int limit) {
        return pushHistoryMapper.findDueForRetry(LocalDateTime.now(), maxRetries, limit);
    }

    @Override
    public boolean isRateLimitExceeded(Long userId, ChannelType channelType) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        // 额度按「真正送达的真实告警」计，不按「写过多少行」计。
        //
        // 旧实现数的是全部历史行，于是两类本该无代价的事件吃掉了真实告警的配额：
        //  - FAILED：凭据坏了的时候每点一次「测试推送」都在逼近上限，攒满 20 次后真实告警
        //    直接被跳过，用户看到「Skipped: Rate limit exceeded」而不是真正的认证失败 ——
        //    一个诊断动作反而把自己的故障掩盖掉了。
        //  - 测试推送（alert_id 为 NULL）：手动诊断不该占用生产投递的预算。
        // 失败量本身已由 AlertRateLimiter（每用户每小时最多 10 条告警）在上游兜住，
        // 因此排除它们不会放开任何洪泛。
        int count = pushHistoryMapper.countDeliveredAlertsSince(userId, channelType.name(), oneHourAgo);
        int limit = getRateLimit(channelType);

        boolean exceeded = count >= limit;
        if (exceeded) {
            log.info("Rate limit exceeded for user {} on channel {}: {}/{} delivered",
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