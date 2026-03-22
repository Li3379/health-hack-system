package com.hhs.domain.event;

import com.hhs.dto.AlertVO;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Domain event published when an alert is generated.
 * Can trigger notifications, logging, or webhook calls.
 */
@Getter
public class AlertGeneratedEvent {
    private final Long userId;
    private final AlertVO alert;
    private final LocalDateTime timestamp;

    public AlertGeneratedEvent(Long userId, AlertVO alert) {
        this.userId = userId;
        this.alert = alert;
        this.timestamp = LocalDateTime.now();
    }
}
