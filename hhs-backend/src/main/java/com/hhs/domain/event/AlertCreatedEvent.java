package com.hhs.domain.event;

import com.hhs.dto.AlertVO;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Domain event published when a health alert row has been inserted.
 * Triggers multi-channel push (email / WeCom / Feishu / WebSocket) after commit.
 */
@Getter
public class AlertCreatedEvent {
    private final Long userId;
    private final AlertVO alert;
    private final LocalDateTime timestamp;

    public AlertCreatedEvent(Long userId, AlertVO alert) {
        this.userId = userId;
        this.alert = alert;
        this.timestamp = LocalDateTime.now();
    }
}
