package com.hhs.vo;

import java.util.List;

/**
 * Outcome of confirming a parsed / OCR-recognised metric batch.
 *
 * <p>Both confirm endpoints used to return only the saved ids, so a partially failed batch was
 * indistinguishable from a complete one: the caller saw "N saved", the record was marked
 * confirmed anyway, and the metrics that failed were lost for good (a confirmed record rejects
 * resubmission). Returning the failures alongside the ids lets the caller report "N saved / M
 * failed", keep the panel open, and retry only what is missing.
 *
 * @param savedIds     ids of the metrics that were written
 * @param failedMetrics display names (or keys) of the metrics that were not written
 */
public record ConfirmSaveResult(
    List<Long> savedIds,
    List<String> failedMetrics
) {
    public boolean hasFailures() {
        return failedMetrics != null && !failedMetrics.isEmpty();
    }

    public boolean isComplete() {
        return !hasFailures();
    }
}