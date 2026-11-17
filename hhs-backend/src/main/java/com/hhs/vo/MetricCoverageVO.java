package com.hhs.vo;

/**
 * One metric's status in the coverage view — "which of my parameters did the system look at,
 * and what did each of them say".
 *
 * <p>This exists because a risk score alone hides two very different situations: a metric that
 * was measured and came back normal, and a metric that was never measured. The four states make
 * that difference visible per metric, so a user can see which of their parameters are driving
 * risk, which are healthy, and which are simply missing.</p>
 *
 * @param key          canonical metric key
 * @param label        user-facing name, e.g. 收缩压
 * @param unit         display unit, e.g. mmHg
 * @param category     {@code HEALTH} or {@code WELLNESS}
 * @param state        {@code HIGH}, {@code LOW}, {@code NORMAL} or {@code MISSING}
 * @param value        the latest value, formatted for display; {@code null} when missing
 * @param normalRange  the reference range it was judged against, formatted; {@code null} when
 *                     the registry defines no range for this metric
 * @param riskNote     which risk assessments consume this metric, and how much it can add;
 *                     {@code null} when no assessment reads it
 */
public record MetricCoverageVO(
        String key,
        String label,
        String unit,
        String category,
        String state,
        String value,
        String normalRange,
        String riskNote
) {

    /** The value is above the reference range. */
    public static final String STATE_HIGH = "HIGH";

    /** The value is below the reference range. */
    public static final String STATE_LOW = "LOW";

    /** The value is inside the reference range. */
    public static final String STATE_NORMAL = "NORMAL";

    /** No value recorded; nothing was judged. */
    public static final String STATE_MISSING = "MISSING";
}
