package com.hhs.service.domain;

import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthProfile;
import com.hhs.vo.MetricCoverageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the "which of my parameters were actually used" view.
 *
 * <p>A user looking at a score has no way to tell "everything you measured is fine" from "we only
 * had one of the inputs". This component answers the per-parameter question directly, deriving
 * both the reference range and the risk note from the registries rather than keeping a second
 * copy of either.</p>
 *
 * <p>The risk note is derived from {@link RiskThresholdRegistry} — the same declaration the rule
 * engine reads. It used to be a hard-coded map here, which meant the panel could claim a metric
 * mattered while the engine had stopped reading it (D-05).</p>
 */
@Component
@RequiredArgsConstructor
public class MetricCoverageAssembler {

    /** The one metric the engine reads from {@code health_profile} rather than {@code health_metric}. */
    private static final String BMI_KEY = "bmi";

    private final HealthMetricMetaRegistry metaRegistry;
    private final RiskThresholdRegistry riskThresholds;

    /**
     * Build the coverage rows for a user's latest values.
     *
     * @param profile       the stored health profile, or {@code null} when the user has none;
     *                      used only for dual-source factors (currently {@code bmi})
     * @param recentMetrics metrics ordered newest first; aliases are accepted
     * @return one row per registry metric, ordered HEALTH first then WELLNESS
     */
    public List<MetricCoverageVO> assemble(HealthProfile profile, List<HealthMetric> recentMetrics) {
        Map<String, HealthMetric> latest = latestByCanonicalKey(recentMetrics);

        List<MetricCoverageVO> rows = new ArrayList<>();
        for (MetricDefinition def : metaRegistry.getAllDefinitions().values()) {
            rows.add(toRow(def, profile, latest.get(def.canonicalKey())));
        }
        // HEALTH 在前，同类内按显示名排序，让"体检指标"聚在一起，便于扫读。
        rows.sort(Comparator
                .comparing((MetricCoverageVO r) -> "HEALTH".equals(r.category()) ? 0 : 1)
                .thenComparing(MetricCoverageVO::label));
        return List.copyOf(rows);
    }

    /**
     * Index the newest value per canonical key, accepting every alias.
     *
     * @param metrics metrics ordered newest first
     * @return canonical key to newest row
     */
    private Map<String, HealthMetric> latestByCanonicalKey(List<HealthMetric> metrics) {
        Map<String, HealthMetric> latest = new LinkedHashMap<>();
        for (HealthMetric metric : metrics) {
            if (metric.getMetricKey() == null) {
                continue;
            }
            String canonical = MetricKeyNormalizer.toCanonicalKey(metric.getMetricKey());
            latest.putIfAbsent(canonical, metric);
        }
        return latest;
    }

    /**
     * Turn one definition plus its latest reading into a coverage row.
     *
     * <p>The engine reads {@code bmi} from the profile, never from {@code health_metric}. So when
     * the engine has a dual-source factor available from the profile, the row is derived from
     * that same source and the metric row is ignored; when the profile has no value the row
     * reports MISSING instead of falling back to the metric table. Falling back would show a
     * number the engine never used ({@code 31.0 / 偏高} beside an engine {@code 22.0（估算）}) —
     * the same lie this panel exists to remove, pointing the other way. This decision lives here
     * and is deliberately not delegated to the frontend.</p>
     *
     * @param def     registry definition
     * @param profile the stored profile, or {@code null}
     * @param hit     the latest reading, or {@code null} when the user has none
     * @return the row
     */
    private MetricCoverageVO toRow(MetricDefinition def, HealthProfile profile, HealthMetric hit) {
        String range = formatRange(def);
        String riskNote = riskNoteFor(def);

        BigDecimal value = valueFor(def, profile, hit);
        if (value == null) {
            return new MetricCoverageVO(def.canonicalKey(), def.displayName(), def.unit(),
                    def.category().name(), MetricCoverageVO.STATE_MISSING, null, range, riskNote);
        }

        String state = classify(def, value);
        return new MetricCoverageVO(def.canonicalKey(), def.displayName(), def.unit(),
                def.category().name(), state, formatValue(value, def), range, riskNote);
    }

    /**
     * Pick the value the engine would actually see for this metric.
     *
     * <p>{@code bmi} is the only dual-source factor today: the engine reads the profile's BMI, so
     * this is where the panel is held to the same source. Absent on the profile means absent —
     * never "absent there, present here".</p>
     *
     * @param def     registry definition
     * @param profile the stored profile, or {@code null}
     * @param hit     the latest metric row, or {@code null}
     * @return the value to display, or {@code null} when the engine source has none
     */
    private BigDecimal valueFor(MetricDefinition def, HealthProfile profile, HealthMetric hit) {
        if (BMI_KEY.equals(def.canonicalKey())) {
            return profile == null ? null : profile.getBmi();
        }
        return hit == null ? null : hit.getValue();
    }

    /**
     * Judge a value against the risk threshold the engine grades it by.
     *
     * <p>When the registry declares a rule for this metric the rule's direction and threshold
     * decide — the same declaration the engine reads, so the pill cannot disagree with the score.
     * Metrics without a rule keep the clinical reference range: the system has a value but no
     * assessment basis, and inventing a threshold would be a second source of truth.</p>
     *
     * <p>Only the first rule is consulted, exactly as {@link #riskNoteFor} describes only the
     * first: otherwise the pill would describe one thing while its own note describes another.</p>
     *
     * @param def   registry definition
     * @param value the reading
     * @return HIGH, LOW or NORMAL
     */
    private String classify(MetricDefinition def, BigDecimal value) {
        List<RiskThresholdRegistry.RiskThresholdRule> rules =
                riskThresholds.rulesFor(def.canonicalKey());
        if (!rules.isEmpty()) {
            RiskThresholdRegistry.RiskThresholdRule rule = rules.get(0);
            if (rule.direction() == RiskThresholdRegistry.Direction.BELOW) {
                return value.compareTo(rule.threshold()) < 0
                        ? MetricCoverageVO.STATE_LOW : MetricCoverageVO.STATE_NORMAL;
            }
            // Strictly greater: a value sitting exactly on the threshold scores nothing, and the
            // pill must say the same thing. `>=` here would show「偏高」to a BMI of exactly 24.00
            // while the engine awards it 0 points.
            return value.compareTo(rule.threshold()) > 0
                    ? MetricCoverageVO.STATE_HIGH : MetricCoverageVO.STATE_NORMAL;
        }
        if (def.normalMax() != null && value.compareTo(def.normalMax()) > 0) {
            return MetricCoverageVO.STATE_HIGH;
        }
        if (def.normalMin() != null && value.compareTo(def.normalMin()) < 0) {
            return MetricCoverageVO.STATE_LOW;
        }
        return MetricCoverageVO.STATE_NORMAL;
    }

    /**
     * Render the risk note for a metric, or {@code null} when no assessment reads it.
     *
     * <p>Only the first declared rule is described. A metric can be read by more than one
     * assessment, but joining the diseases with a separator would immediately exceed the 24
     * character ceiling and fold the cell onto three lines — the UI spec explicitly forbids it
     * (Contract 5.2).</p>
     *
     * @param def registry definition
     * @return the note, or {@code null} when the metric carries no rule
     */
    private String riskNoteFor(MetricDefinition def) {
        List<RiskThresholdRegistry.RiskThresholdRule> rules =
                riskThresholds.rulesFor(def.canonicalKey());
        if (rules.isEmpty()) {
            return null;
        }
        return formatRiskNote(rules.get(0), def);
    }

    /**
     * Render one rule as the single permitted sentence shape.
     *
     * <p>The template is {@code {疾病}评估：{方向动词} {阈值} {单位} 计入 {分数} 分}. Both spaces
     * are written by this template, including the one between the threshold and the unit: the
     * registry's unit literals carry no leading space ({@code "mmol/L"}, not {@code " mmol/L"}),
     * so this is the only place that knows a separator is needed. Dropping it would silently
     * change three sentences users already read ({@code 6.1 mmol/L} → {@code 6.1mmol/L}) and
     * would break the later assertion that a contributor's {@code threshold} field is a substring
     * of this note.</p>
     *
     * <p>The threshold is rendered with the metric's own precision rather than a literal number
     * of decimals: blood glucose (precision 1) gives {@code 6.1}, BMI gives {@code 24.0}, and a
     * lipid at precision 2 gives {@code 5.20}. Hard-coding a digit count would put two different
     * decimal shapes in one sentence. {@link #formatValue} is reused rather than re-deriving the
     * scaling so the class keeps a single formatting formula.</p>
     *
     * @param rule the rule to describe
     * @param def  the metric's definition, supplying unit and precision
     * @return the note
     */
    private String formatRiskNote(RiskThresholdRegistry.RiskThresholdRule rule,
                                  MetricDefinition def) {
        String verb = rule.direction() == RiskThresholdRegistry.Direction.BELOW ? "低于" : "超过";
        return rule.disease() + "评估：" + verb + " " + formatValue(rule.threshold(), def)
                + " " + def.unit() + " 计入 " + rule.points() + " 分";
    }

    /**
     * Format a value using the definition's precision.
     *
     * <p>This is the only formatting formula in the class, and it is {@code public static} so
     * the rule engine can call it too. That is the whole point: the panel renders {@code bmi}
     * at precision 1 ({@code 22.9}) while the engine used to render the same stored value with
     * {@code stripTrailingZeros()} ({@code 22.86}), so one number appeared as two strings on two
     * surfaces of one screen. Delegating here makes "both surfaces show the same string" a
     * structural fact instead of something an assertion has to simulate.</p>
     *
     * <p>{@code public} rather than package-private is required, not cosmetic:
     * {@code RiskRuleEngine} is a private nested class of
     * {@code com.hhs.service.impl.RiskAssessmentServiceImpl}, and a package-private method in
     * {@code com.hhs.service.domain} is invisible there. The resulting compile error would be
     * raised far from the defect this method prevents.</p>
     *
     * @param value raw value
     * @param def   registry definition
     * @return display string
     */
    public static String format(BigDecimal value, MetricDefinition def) {
        int precision = def.precision() != null ? def.precision() : 0;
        return value.setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Format a value using the definition's precision.
     *
     * @param value raw value
     * @param def   registry definition
     * @return display string
     */
    private String formatValue(BigDecimal value, MetricDefinition def) {
        return format(value, def);
    }

    /**
     * Format the reference range for display.
     *
     * @param def registry definition
     * @return e.g. {@code 3.9–6.1}, or {@code null} when the registry defines no range
     */
    private String formatRange(MetricDefinition def) {
        int precision = def.precision() != null ? def.precision() : 0;
        String min = def.normalMin() == null ? null
                : def.normalMin().setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString();
        String max = def.normalMax() == null ? null
                : def.normalMax().setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString();
        if (min == null && max == null) {
            return null;
        }
        if (min == null) {
            return "≤ " + max;
        }
        if (max == null) {
            return "≥ " + min;
        }
        return min + "–" + max;
    }
}
