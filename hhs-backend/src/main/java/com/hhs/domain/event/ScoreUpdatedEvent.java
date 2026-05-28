package com.hhs.domain.event;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Domain event published when a health score is calculated or updated.
 * Triggers cache invalidation for user score and score history persistence.
 */
@Getter
public class ScoreUpdatedEvent {
    private final Long userId;
    private final Integer newScore;
    private final String level;
    private final LocalDateTime timestamp;
    private final Map<String, Object> factors;
    private final BigDecimal cardiovascularScore;
    private final BigDecimal metabolicScore;
    private final BigDecimal weightScore;
    private final BigDecimal lifestyleScore;

    public ScoreUpdatedEvent(Long userId, Integer newScore, String level) {
        this(userId, newScore, level, null, null, null, null, null);
    }

    public ScoreUpdatedEvent(Long userId, Integer newScore, String level, Map<String, Object> factors) {
        this(userId, newScore, level, factors, null, null, null, null);
    }

    public ScoreUpdatedEvent(Long userId, Integer newScore, String level, Map<String, Object> factors,
                             Map<String, Integer> dimensionScores) {
        this.userId = userId;
        this.newScore = newScore;
        this.level = level;
        this.timestamp = LocalDateTime.now();
        this.factors = factors;
        if (dimensionScores != null) {
            this.cardiovascularScore = toBigDecimal(dimensionScores.get("cardiovascular"));
            this.metabolicScore = toBigDecimal(dimensionScores.get("metabolic"));
            this.weightScore = toBigDecimal(dimensionScores.get("weight"));
            this.lifestyleScore = toBigDecimal(dimensionScores.get("lifestyle"));
        } else {
            this.cardiovascularScore = null;
            this.metabolicScore = null;
            this.weightScore = null;
            this.lifestyleScore = null;
        }
    }

    private ScoreUpdatedEvent(Long userId, Integer newScore, String level, Map<String, Object> factors,
                              BigDecimal cardiovascularScore, BigDecimal metabolicScore,
                              BigDecimal weightScore, BigDecimal lifestyleScore) {
        this.userId = userId;
        this.newScore = newScore;
        this.level = level;
        this.timestamp = LocalDateTime.now();
        this.factors = factors;
        this.cardiovascularScore = cardiovascularScore;
        this.metabolicScore = metabolicScore;
        this.weightScore = weightScore;
        this.lifestyleScore = lifestyleScore;
    }

    private static BigDecimal toBigDecimal(Integer value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}
