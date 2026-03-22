package com.hhs.service.domain;

import com.hhs.entity.HealthAlert;
import com.hhs.mapper.HealthAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Alert rate limiting logic
 * Responsible for checking and enforcing alert rate limits
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRateLimiter {

    private final HealthAlertMapper healthAlertMapper;

    private static final int MAX_ALERTS_PER_HOUR = 10;

    /**
     * Check if user is within rate limit for creating new alerts
     *
     * @param userId the user ID
     * @return true if user can create alert, false if rate limited
     */
    public boolean canCreateAlert(Long userId) {
        return isWithinRateLimit(userId);
    }

    /**
     * Check if user is within rate limit
     *
     * @param userId the user ID
     * @return true if within rate limit, false otherwise
     */
    public boolean isWithinRateLimit(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        Long count = healthAlertMapper.countAlertsSince(userId, since);
        return count < MAX_ALERTS_PER_HOUR;
    }

    /**
     * Get remaining alert count for user
     *
     * @param userId the user ID
     * @return number of alerts remaining in current hour window
     */
    public int getRemainingAlertCount(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        Long count = healthAlertMapper.countAlertsSince(userId, since);
        return Math.max(0, MAX_ALERTS_PER_HOUR - count.intValue());
    }
}
