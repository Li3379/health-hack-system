package com.hhs.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Device Sync Rate Limiter
 *
 * <p>Implements rate limiting for device synchronization endpoints.
 * Limits the number of sync operations per user per minute to prevent
 * API abuse and protect external service quotas.
 *
 * <p>Rate limits:
 * <ul>
 *   <li>Per-user limit: 10 syncs per minute</li>
 *   <li>Window: Rolling 60-second window</li>
 * </ul>
 *
 * <p>Uses Redis for distributed rate limiting across multiple instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceSyncRateLimiter {

    private final RedisTemplate<String, Integer> redisTemplate;

    /**
     * Maximum sync operations per user per minute.
     */
    private static final int SYNC_LIMIT_PER_MINUTE = 10;

    /**
     * Rate limit window in seconds.
     */
    private static final int WINDOW_SECONDS = 60;

    /**
     * Key prefix for rate limit counters.
     */
    private static final String KEY_PREFIX = "device:sync:limit:";

    /**
     * Check if a sync operation is allowed for the given user.
     *
     * @param userId the user ID
     * @return true if the operation is allowed, false if rate limit exceeded
     */
    public boolean checkLimit(Long userId) {
        if (userId == null) {
            log.warn("Rate limit check called with null userId");
            return false;
        }

        String key = getKey(userId);
        Integer count = redisTemplate.opsForValue().get(key);

        if (count == null) {
            // First request in window, set to 1 with expiration
            redisTemplate.opsForValue().set(key, 1, WINDOW_SECONDS, TimeUnit.SECONDS);
            log.debug("Device sync rate limit - First request: userId={}", userId);
            return true;
        }

        if (count >= SYNC_LIMIT_PER_MINUTE) {
            log.warn("Device sync rate limit exceeded: userId={}, count={}, limit={}",
                    userId, count, SYNC_LIMIT_PER_MINUTE);
            return false;
        }

        // Increment counter
        redisTemplate.opsForValue().increment(key);
        log.debug("Device sync rate limit - Allowed: userId={}, count={}", userId, count + 1);
        return true;
    }

    /**
     * Get the remaining sync count for the user.
     *
     * @param userId the user ID
     * @return remaining sync operations in current window
     */
    public int getRemainingCount(Long userId) {
        if (userId == null) {
            return 0;
        }

        String key = getKey(userId);
        Integer count = redisTemplate.opsForValue().get(key);
        return Math.max(0, SYNC_LIMIT_PER_MINUTE - (count != null ? count : 0));
    }

    /**
     * Get the current sync count for the user in the current window.
     *
     * @param userId the user ID
     * @return current sync count
     */
    public int getCurrentCount(Long userId) {
        if (userId == null) {
            return 0;
        }

        String key = getKey(userId);
        Integer count = redisTemplate.opsForValue().get(key);
        return count != null ? count : 0;
    }

    /**
     * Reset the rate limit for a user (admin function).
     *
     * @param userId the user ID
     */
    public void resetLimit(Long userId) {
        if (userId == null) {
            return;
        }

        String key = getKey(userId);
        redisTemplate.delete(key);
        log.info("Device sync rate limit reset: userId={}", userId);
    }

    /**
     * Get the rate limit per minute.
     *
     * @return the limit
     */
    public int getLimit() {
        return SYNC_LIMIT_PER_MINUTE;
    }

    /**
     * Get the window duration in seconds.
     *
     * @return the window duration
     */
    public int getWindowSeconds() {
        return WINDOW_SECONDS;
    }

    private String getKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}