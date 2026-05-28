package com.hhs.service;

import java.util.Map;

/**
 * Screening Comparison Service
 * Compares two examination reports and their lab results
 */
public interface ScreeningComparisonService {

    /**
     * Compare two screening (examination report) records for a user.
     *
     * @param userId       User ID (for authorization)
     * @param screeningIdA First screening (examination report) ID
     * @param screeningIdB Second screening (examination report) ID
     * @return Comparison data including screening summaries, metric-level comparisons, and a summary
     * @throws BusinessException if either report is not found or user not authorized
     */
    Map<String, Object> compareScreenings(Long userId, Long screeningIdA, Long screeningIdB);
}
