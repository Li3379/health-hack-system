package com.hhs.service.impl;

import com.hhs.entity.RealtimeMetric;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.MetricDeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Metric Deduplication Service Implementation
 * Uses Redis for distributed deduplication tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricDeduplicationServiceImpl implements MetricDeduplicationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RealtimeMetricMapper realtimeMetricMapper;

    // Deduplication time windows per metric type (in seconds)
    private static final int DEDUP_HEART_RATE = 300; // 5 minutes
    private static final int DEDUP_BLOOD_PRESSURE = 43200; // 12 hours
    private static final int DEDUP_WEIGHT = 86400; // 24 hours
    private static final int DEDUP_GLUCOSE = 21600; // 6 hours
    private static final int DEDUP_DEFAULT = 300; // 5 minutes

    /**
     * Check if a metric should be accepted (not a duplicate)
     *
     * @param metric The metric to check
     * @return true if metric should be accepted, false if duplicate
     */
    @Override
    public boolean shouldAcceptMetric(RealtimeMetric metric) {
        try {
            String key = buildDedupKey(metric);
            Boolean exists = redisTemplate.hasKey(key);

            if (Boolean.TRUE.equals(exists)) {
                log.debug("Duplicate metric detected: user={}, metricKey={}",
                        metric.getUserId(), metric.getMetricKey());
                return false;
            }
            return true;
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, accepting metric by default: {}", e.getMessage());
            return true; // Accept metric when Redis is unavailable
        } catch (Exception e) {
            log.warn("Redis operation failed, accepting metric by default: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Record a metric for deduplication tracking
     *
     * @param metric The metric that was accepted
     */
    @Override
    public void recordMetric(RealtimeMetric metric) {
        try {
            String key = buildDedupKey(metric);
            int ttl = getDedupWindowSeconds(metric.getMetricKey());

            redisTemplate.opsForValue().set(
                    key,
                    metric.getValue().toString(),
                    ttl,
                    TimeUnit.SECONDS
            );
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, skipping metric recording: {}", e.getMessage());
            // If Redis is down, skip deduplication recording
        } catch (Exception e) {
            log.warn("Redis operation failed, skipping metric recording: {}", e.getMessage());
        }
    }

    private String buildDedupKey(RealtimeMetric metric) {
        // Format: metric:dedup:{userId}:{metricKey}:{date}
        String date = metric.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.format("metric:dedup:%d:%s:%s",
                metric.getUserId(),
                metric.getMetricKey(),
                date);
    }

    private int getDedupWindowSeconds(String metricKey) {
        return switch (metricKey) {
            case "heartRate" -> DEDUP_HEART_RATE;
            case "systolicBP", "diastolicBP" -> DEDUP_BLOOD_PRESSURE;
            case "weight" -> DEDUP_WEIGHT;
            case "glucose" -> DEDUP_GLUCOSE;
            default -> DEDUP_DEFAULT;
        };
    }
}
