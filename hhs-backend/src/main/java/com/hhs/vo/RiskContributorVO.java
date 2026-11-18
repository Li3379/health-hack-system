package com.hhs.vo;

/**
 * One scored input behind a risk assessment, exposed so the result can be explained.
 *
 * <p>Every factor the rule engine reads produces exactly one contributor, including the ones
 * whose value was missing. That is the point: before this record existed a missing metric
 * contributed zero points and was indistinguishable from a metric that was present and
 * healthy — "your blood pressure is normal" and "we have no blood pressure for you" rendered
 * as the same low risk score.</p>
 *
 * @param key         stable identifier of the factor, e.g. {@code systolicBP}
 * @param label       user-facing factor name, e.g. 收缩压
 * @param state       {@code CONTRIBUTED} (value present), {@code NORMAL} (value present, under
 *                    the threshold) or {@code MISSING} (no value; the factor was not evaluated)
 * @param value       the value that was used, formatted for display; {@code null} when missing
 * @param threshold   the threshold it was compared against, formatted for display; may be
 *                    {@code null} for factors that are not threshold-based
 * @param points      points this factor added to the disease score
 * @param explanation one sentence stating why these points were awarded, in Chinese
 */
public record RiskContributorVO(
        String key,
        String label,
        String state,
        String value,
        String threshold,
        int points,
        String explanation
) {

    /** The factor had a value and crossed its threshold. */
    public static final String STATE_CONTRIBUTED = "CONTRIBUTED";

    /** The factor had a value but stayed under its threshold. */
    public static final String STATE_NORMAL = "NORMAL";

    /** The factor had no value; it contributed nothing and the score is less reliable. */
    public static final String STATE_MISSING = "MISSING";

    /**
     * The factor had no value but a documented default was substituted and scored.
     *
     * <p>Distinct from both {@link #STATE_CONTRIBUTED} (the user's own reading) and
     * {@link #STATE_MISSING} (nothing was scored at all): the points are real, but they rest
     * on an assumption the user can correct by filling the field in.</p>
     */
    public static final String STATE_ASSUMED = "ASSUMED";

    /**
     * Build a contributor for a factor that was evaluated against an upper-bound threshold.
     *
     * <p>The sentence calls the number an assessment threshold, not a reference value. Once risk
     * thresholds stopped being the clinical reference ranges (D-03), 130 / 85 / 24.0 are
     * preventive thresholds sitting <em>below</em> the 139 / 89 / 23.9 reference maxima — and the
     * same screen shows the clinical reference <em>range</em> column beside this sentence.
     * Calling both "reference" asks the user to reconcile two contradictory descriptions of one
     * row.</p>
     *
     * @param key       stable factor key
     * @param label     user-facing name
     * @param value     display-formatted value, or {@code null} when absent
     * @param threshold display-formatted threshold
     * @param hit       whether the threshold was crossed
     * @param points    points awarded
     * @return the contributor
     */
    public static RiskContributorVO threshold(String key, String label, String value,
                                              String threshold, boolean hit, int points) {
        if (value == null) {
            return missing(key, label);
        }
        String state = hit ? STATE_CONTRIBUTED : STATE_NORMAL;
        String explanation = hit
                ? label + " " + value + "，超过评估阈值 " + threshold + "，因此计入 " + points + " 分"
                : label + " " + value + "，未超过评估阈值 " + threshold + "，未加分";
        return new RiskContributorVO(key, label, state, value, threshold, points, explanation);
    }

    /**
     * Build a contributor for a factor whose risky side is <em>below</em> its threshold.
     *
     * <p>Some metrics are protective: HDL cholesterol is normal when high and risky when low
     * (the reference says {@code >= 1.00 mmol/L}). Rendering those through {@link #threshold}
     * would tell a person at 0.90 that they "exceeded" the very bound they undershot — the
     * sentence would state the fact backwards. This factory is the mirror image, and it uses the
     * same assessment-threshold wording for the same reason: the number is a risk threshold, not
     * a reference value.</p>
     *
     * <p>It deliberately performs <em>no</em> comparison. Which side is risky is declared once,
     * as {@code Direction} on the registry rule; duplicating the comparison here would create a
     * third threshold implementation that could disagree with the other two.</p>
     *
     * @param key       stable factor key
     * @param label     user-facing name
     * @param value     display-formatted value, or {@code null} when absent
     * @param threshold display-formatted lower-bound threshold
     * @param breached  whether the value is below the threshold
     * @param points    points awarded
     * @return the contributor
     */
    public static RiskContributorVO below(String key, String label, String value,
                                          String threshold, boolean breached, int points) {
        if (value == null) {
            return missing(key, label);
        }
        String state = breached ? STATE_CONTRIBUTED : STATE_NORMAL;
        String explanation = breached
                ? label + " " + value + "，低于评估阈值 " + threshold + "，因此计入 " + points + " 分"
                : label + " " + value + "，未低于评估阈值 " + threshold + "，未加分";
        return new RiskContributorVO(key, label, state, value, threshold, points, explanation);
    }

    /**
     * Build a contributor for a factor that added points without a threshold comparison
     * (an age bracket, or a baseline that is awarded unconditionally).
     *
     * @param key         stable factor key
     * @param label       user-facing name
     * @param value       display-formatted value
     * @param points      points awarded
     * @param explanation why these points were awarded
     * @return the contributor
     */
    public static RiskContributorVO awarded(String key, String label, String value, int points,
                                            String explanation) {
        return new RiskContributorVO(key, label, STATE_CONTRIBUTED, value, null, points, explanation);
    }

    /**
     * Build a contributor for a factor whose value was unavailable.
     *
     * <p>{@code points} is always 0 and the state is {@link #STATE_MISSING}, so a caller can
     * count missing factors and tell the user the score rests on incomplete data.</p>
     *
     * @param key   stable factor key
     * @param label user-facing name
     * @return the contributor
     */
    public static RiskContributorVO missing(String key, String label) {
        return new RiskContributorVO(key, label, STATE_MISSING, null, null, 0,
                "缺少" + label + "数据，此项未计入评分");
    }

    /**
     * Build a contributor for a factor whose value was substituted by a documented default.
     *
     * @param key         stable factor key
     * @param label       user-facing name
     * @param value       the substituted value, formatted for display
     * @param points      points awarded on the substituted value
     * @param explanation one sentence naming the substitution
     * @return the contributor
     */
    public static RiskContributorVO assumed(String key, String label, String value, int points,
                                            String explanation) {
        return new RiskContributorVO(key, label, STATE_ASSUMED, value, null, points, explanation);
    }
}
