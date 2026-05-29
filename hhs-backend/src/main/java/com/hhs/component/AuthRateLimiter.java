package com.hhs.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * 登录限流器
 * 防止暴力破解和账户枚举攻击
 * - 每IP: 10次/5分钟
 * - 每用户名: 5次/5分钟
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {

    private final RedisTemplate<String, Integer> redisTemplate;

    private static final int IP_LIMIT = 10;
    private static final int USERNAME_LIMIT = 5;
    private static final int WINDOW_SECONDS = 300; // 5 minutes

    /**
     * 检查是否超过限流
     *
     * @param username 用户名
     * @param clientIp 客户端IP（null时跳过IP检查）
     * @return true-允许，false-超过限制
     */
    public boolean checkLimit(String username, String clientIp) {
        if (clientIp != null && isRateLimited(clientIp, IP_LIMIT)) {
            log.warn("Auth rate limit exceeded for IP: {}", clientIp);
            return false;
        }
        if (username != null && isRateLimited(username, USERNAME_LIMIT)) {
            log.warn("Auth rate limit exceeded for username: {}", username);
            return false;
        }
        return true;
    }

    /**
     * 记录失败尝试（登录/注册失败时调用）
     */
    public void recordFailure(String username, String clientIp) {
        if (clientIp != null) {
            incrementCounter(clientIp);
        }
        if (username != null) {
            incrementCounter(username);
        }
    }

    private boolean isRateLimited(String key, int limit) {
        String rateKey = "auth:rate:" + key;
        Integer count = redisTemplate.opsForValue().get(rateKey);
        if (count == null) {
            return false;
        }
        return count >= limit;
    }

    private void incrementCounter(String key) {
        String rateKey = "auth:rate:" + key;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(rateKey, 1, WINDOW_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(isNew)) {
            log.debug("Auth rate counter created: key={}", key);
        } else {
            redisTemplate.opsForValue().increment(rateKey);
            redisTemplate.expire(rateKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 获取剩余尝试次数
     */
    public int getRemainingAttempts(String username, String clientIp) {
        int ipRemaining = clientIp != null ? getRemaining(clientIp, IP_LIMIT) : IP_LIMIT;
        int userRemaining = username != null ? getRemaining(username, USERNAME_LIMIT) : USERNAME_LIMIT;
        return Math.min(ipRemaining, userRemaining);
    }

    private int getRemaining(String key, int limit) {
        String rateKey = "auth:rate:" + key;
        Integer count = redisTemplate.opsForValue().get(rateKey);
        return Math.max(0, limit - (count != null ? count : 0));
    }
}
