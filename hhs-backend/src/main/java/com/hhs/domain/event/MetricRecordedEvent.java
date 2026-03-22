package com.hhs.domain.event;

import com.hhs.entity.RealtimeMetric;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Domain event published when a health metric is recorded.
 * Triggers alert evaluation and cache invalidation.
 */
@Getter
public class MetricRecordedEvent {
    private final Long userId;
    private final RealtimeMetric metric;
    private final LocalDateTime timestamp;

    public MetricRecordedEvent(Long userId, RealtimeMetric metric) {
        this.userId = userId;
        this.metric = metric;
        this.timestamp = LocalDateTime.now();
    }
}
