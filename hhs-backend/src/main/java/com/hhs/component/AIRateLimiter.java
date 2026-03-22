package com.hhs.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * AI功能限流器
 * - 访客：3次/天
 * - 登录用户：20次/天
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIRateLimiter {

    private final RedisTemplate<String, Integer> redisTemplate;
    
    private static final int GUEST_LIMIT = 3;
    private static final int USER_LIMIT = 20;
    
    /**
     * 检查是否超过限流
     * @param userId 用户ID，null表示访客
     * @return true-允许访问，false-超过限制
     */
    public boolean checkLimit(Long userId) {
        String key = getLimitKey(userId);
        Integer count = redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            // 首次访问，设置为1，过期时间到明天0点
            long secondsUntilMidnight = getSecondsUntilMidnight();
            redisTemplate.opsForValue().set(key, 1, secondsUntilMidnight, TimeUnit.SECONDS);
            log.debug("AI限流 - 首次访问: key={}, ttl={}", key, secondsUntilMidnight);
            return true;
        }
        
        int limit = userId != null ? USER_LIMIT : GUEST_LIMIT;
        if (count >= limit) {
            log.warn("AI限流 - 超过限制: key={}, count={}, limit={}", key, count, limit);
            return false;
        }
        
        redisTemplate.opsForValue().increment(key);
        log.debug("AI限流 - 通过: key={}, count={}, limit={}", key, count + 1, limit);
        return true;
    }
    
    /**
     * 获取剩余次数
     */
    public int getRemainingCount(Long userId) {
        String key = getLimitKey(userId);
        Integer count = redisTemplate.opsForValue().get(key);
        int limit = userId != null ? USER_LIMIT : GUEST_LIMIT;
        return Math.max(0, limit - (count != null ? count : 0));
    }
    
    /**
     * 重置限流（管理员功能）
     */
    public void resetLimit(Long userId) {
        String key = getLimitKey(userId);
        redisTemplate.delete(key);
        log.info("AI限流已重置: key={}", key);
    }
    
    private String getLimitKey(Long userId) {
        return "ai:limit:" + (userId != null ? "user:" + userId : "guest");
    }
    
    /**
     * 计算到明天0点的秒数
     */
    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.plusDays(1).with(LocalTime.MIN);
        return Duration.between(now, midnight).getSeconds();
    }
}
