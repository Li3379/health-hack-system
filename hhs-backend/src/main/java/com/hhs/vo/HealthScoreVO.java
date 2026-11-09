package com.hhs.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
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
     * Dimension-level scores for history persistence
     * Keys: cardiovascular, metabolic, weight, lifestyle; Values: 0-100
     */
    private Map<String, Integer> dimensionScores;

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

    /**
     * Weighted health-profile completeness (0-100) from the authoritative calculator.
     *
     * <p>May be {@code null} when the user has no health data at all: that path returns the
     * NO_DATA default before any scorer runs, and null means "unknown" so the client renders no
     * completeness badge rather than a fabricated 0%. Consumers that need an authoritative value
     * for every user read the dedicated completeness endpoint instead.</p>
     */
    private Integer profileCompleteness;

    /**
     * Human-readable Chinese sentences naming each default value this score substituted.
     *
     * <p>Captured inside the branch that applied the default, so the disclosure cannot drift
     * from the behaviour it describes. Never {@code null} on the calculated path; an empty list
     * means nothing was substituted.</p>
     */
    private List<String> assumptions;
}
