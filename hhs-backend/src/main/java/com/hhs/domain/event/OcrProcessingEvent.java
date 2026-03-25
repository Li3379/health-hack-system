package com.hhs.domain.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Domain event published when an OCR processing task should be triggered.
 * Uses TransactionalEventListener to ensure processing happens AFTER
 * the database transaction commits, avoiding the race condition where
 * async tasks can't see uncommitted data.
 */
@Getter
public class OcrProcessingEvent {
    private final Long reportId;
    private final LocalDateTime timestamp;

    public OcrProcessingEvent(Long reportId) {
        this.reportId = reportId;
        this.timestamp = LocalDateTime.now();
    }
}