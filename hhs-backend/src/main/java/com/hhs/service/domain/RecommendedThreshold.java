package com.hhs.service.domain;

import java.math.BigDecimal;

/**
 * Immutable recommended threshold boundaries for a single metric (D-06).
 * Derived server-side from enabled {@code alert_rule} seed rows; the frontend never
 * recomputes recommendations so both sides cannot drift.
 *
 * @param warningLow   recommended warning low boundary (null = unbounded)
 * @param warningHigh  recommended warning high boundary (null = unbounded)
 * @param criticalLow  recommended critical low boundary (null = unbounded)
 * @param criticalHigh recommended critical high boundary (null = unbounded)
 */
public record RecommendedThreshold(
        BigDecimal warningLow,
        BigDecimal warningHigh,
        BigDecimal criticalLow,
        BigDecimal criticalHigh
) {

    /**
     * D-06 fallback derivation: when a metric has no usable alert_rule seed, the warning
     * band is derived from the metadata normal range and both critical boundaries stay
     * unbounded (null).
     *
     * @param normalMin metadata normal range lower bound
     * @param normalMax metadata normal range upper bound
     * @return recommendation carrying warningLow = normalMin and warningHigh = normalMax
     */
    public static RecommendedThreshold fallback(BigDecimal normalMin, BigDecimal normalMax) {
        return new RecommendedThreshold(normalMin, normalMax, null, null);
    }
}
