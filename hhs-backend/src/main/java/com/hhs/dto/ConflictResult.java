package com.hhs.dto;

import com.hhs.common.enums.ResolutionStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a conflict resolution operation for health metrics.
 *
 * <p>When syncing health metrics from multiple devices or sources, this class
 * captures the outcome of the conflict resolution process, including:
 * <ul>
 *   <li>Whether a conflict was detected</li>
 *   <li>The resolved metric request (after applying the strategy)</li>
 *   <li>The strategy that was applied</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictResult {

    /**
     * Whether a conflict was detected between existing and incoming metrics.
     * True if an existing metric was found for the same user, metricKey, and recordDate.
     */
    private boolean hadConflict;

    /**
     * The resolved HealthMetricRequest after applying the resolution strategy.
     * If no conflict existed, this equals the incoming request.
     * If a conflict existed, this reflects the result of applying the strategy.
     */
    private HealthMetricRequest resolved;

    /**
     * The resolution strategy that was applied.
     * If no conflict existed, this reflects the strategy that would have been used.
     */
    private ResolutionStrategy appliedStrategy;

    /**
     * The ID of the existing metric if a conflict was detected.
     * Null if no conflict existed.
     */
    private Long existingMetricId;

    /**
     * The value of the existing metric if a conflict was detected.
     * Null if no conflict existed.
     */
    private java.math.BigDecimal existingValue;

    /**
     * Create a result indicating no conflict was found.
     *
     * @param request The incoming request
     * @param strategy The default strategy
     * @return ConflictResult with no conflict
     */
    public static ConflictResult noConflict(HealthMetricRequest request, ResolutionStrategy strategy) {
        return ConflictResult.builder()
                .hadConflict(false)
                .resolved(request)
                .appliedStrategy(strategy)
                .build();
    }

    /**
     * Create a result indicating a conflict was resolved.
     *
     * @param resolved The resolved request
     * @param strategy The applied strategy
     * @param existingId The ID of the existing metric
     * @param existingValue The value of the existing metric
     * @return ConflictResult with conflict details
     */
    public static ConflictResult withConflict(HealthMetricRequest resolved,
                                               ResolutionStrategy strategy,
                                               Long existingId,
                                               java.math.BigDecimal existingValue) {
        return ConflictResult.builder()
                .hadConflict(true)
                .resolved(resolved)
                .appliedStrategy(strategy)
                .existingMetricId(existingId)
                .existingValue(existingValue)
                .build();
    }
}