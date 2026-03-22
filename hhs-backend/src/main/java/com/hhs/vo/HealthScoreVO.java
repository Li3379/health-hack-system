package com.hhs.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * View Object for health score
 */
@Data
public class HealthScoreVO {

    /**
     * Overall health score (0-100)
     */
    private Integer score;

    /**
     * Health level: EXCELLENT, GOOD, FAIR, POOR
     */
    private String level;

    /**
     * Score breakdown by category
     */
    private Map<String, Object> factors;

    /**
     * Calculation method used: RULE_BASED, AI_BASED
     */
    private String calculationMethod;

    /**
     * When the score was calculated
     */
    private LocalDateTime calculatedAt;

    /**
     * When the cached score expires
     */
    private LocalDateTime expiresAt;

    /**
     * Whether this is a cached value
     */
    private Boolean isCached;

    /**
     * Optional message (e.g., when no data available)
     */
    private String message;
}
