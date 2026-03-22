package com.hhs.domain.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Domain event published when a health score is calculated or updated.
 * Triggers cache invalidation for user score.
 */
@Getter
public class ScoreUpdatedEvent {
    private final Long userId;
    private final Integer newScore;
    private final LocalDateTime timestamp;

    public ScoreUpdatedEvent(Long userId, Integer newScore) {
        this.userId = userId;
        this.newScore = newScore;
        this.timestamp = LocalDateTime.now();
    }
}
