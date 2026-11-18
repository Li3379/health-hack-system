package com.hhs.service.domain;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative declaration of the thresholds the risk engine judges readings against.
 *
 * <p>Risk thresholds are deliberately <em>not</em> the clinical reference ranges held by
 * {@link HealthMetricMetaRegistry}: a preventive assessment is stricter than "inside the
 * reference range". Systolic pressure 130–139 mmHg is inside the reference range but is exactly
 * the band the hypertension rule must score, so reading {@code normalMax} (139) instead of the
 * risk threshold (130) would silently loosen every score.</p>
 *
 * <p>The class exists because the same knowledge used to live in two places — four
 * {@code BigDecimal.valueOf(...)} literals inside the engine and a hard-coded
 * {@code RISK_CONSUMERS} map inside the coverage panel. Either copy drifting on its own made
 * the panel lie about which parameters are used, or made scoring quietly lenient. Both consumers
 * now derive from this one declaration.</p>
 *
 * <p>Kept dependency-free (no reference to {@link HealthMetricMetaRegistry}) so tests can simply
 * {@code new} it; spelling is guarded by {@link MetricKeyNormalizer#isCanonicalKey} at
 * construction time, and "every consumed key is a registered metric" is guarded by the tests.</p>
 */
@Component
public class RiskThresholdRegistry {

    /** Which side of the threshold is the risky one. */
    public enum Direction {

        /** The reading is risky when it is strictly greater than the threshold. */
        ABOVE,

        /** The reading is risky when it is strictly less than the threshold. */
        BELOW
    }

    /**
     * One threshold rule: which metric, read by which assessment, in which direction.
     *
     * <p>Carries no display name, unit or precision on purpose — those already have a single
     * source of truth in {@link MetricDefinition}, and copying them here would let the two
     * drift. A metric may legitimately carry one rule per disease, so the key is the
     * {@code (metricKey, disease)} pair rather than the metric alone.</p>
     *
     * @param metricKey canonical metric key, e.g. {@code bloodGlucose}
     * @param disease   the assessment that reads it, e.g. 糖尿病
     * @param direction which side of the threshold is risky
     * @param threshold the risk threshold value
     * @param points    points awarded when the reading crosses the threshold
     */
    public record RiskThresholdRule(String metricKey, String disease, Direction direction,
                                    BigDecimal threshold, int points) {
    }

    /** Rules per canonical key, in declaration order (the panel describes the first one). */
    private final Map<String, List<RiskThresholdRule>> rulesByKey = new LinkedHashMap<>();

    /**
     * Build the registry with its rules already loaded, so tests need no Spring context.
     */
    public RiskThresholdRegistry() {
        registerAllRules();
    }

    /**
     * Re-register when Spring constructs the bean, mirroring {@link HealthMetricMetaRegistry}.
     */
    @PostConstruct
    public void init() {
        if (rulesByKey.isEmpty()) {
            registerAllRules();
        }
    }

    /**
     * Declare every rule, in a hand-picked order that must not be "tidied" into another one.
     *
     * <p>The coverage panel describes <em>only the first</em> rule of a metric (joining diseases
     * would blow the 24-character cell), so the order inside one key is user-visible copy, not
     * incidental. Three of the four shared keys therefore list the long-standing assessment
     * first and the cardiovascular one second:</p>
     *
     * <ul>
     *   <li>{@code bloodGlucose} — 糖尿病 first: the panel reads
     *       {@code 糖尿病评估：超过 6.1 mmol/L 计入 30 分}.</li>
     *   <li>{@code systolicBP} / {@code diastolicBP} — 高血压 first: the panel reads
     *       {@code 高血压评估：超过 130 mmHg 计入 35 分}.</li>
     *   <li>{@code bmi} — 心血管 first (the one key where the older rule is second): the panel
     *       reads {@code 心血管评估：超过 24.0 kg/m² 计入 20 分}. Declaring the diabetes rule
     *       first here would silently rewrite that sentence to 25 分.</li>
     * </ul>
     *
     * <p>The four lipids carry exactly one rule each (心血管) and are kept adjacent so the block
     * can be reviewed as a unit.</p>
     */
    private void registerAllRules() {
        rule("bloodGlucose", "糖尿病", Direction.ABOVE, "6.1", 30);
        rule("bloodGlucose", "心血管", Direction.ABOVE, "6.1", 10);

        rule("systolicBP", "高血压", Direction.ABOVE, "130", 35);
        rule("systolicBP", "心血管", Direction.ABOVE, "130", 30);

        rule("diastolicBP", "高血压", Direction.ABOVE, "85", 30);
        rule("diastolicBP", "心血管", Direction.ABOVE, "85", 15);

        // 24.0 是既有行为，不是「严于参考上限」的例子：元数据参考上限是 23.9，这条更松 0.1。
        // 改成 23.9 会让 BMI 23.9–24.0 区间的人新增计分，属未授权行为变化，故保留 24.0 并
        // 明确不宣称严于参考上限（AC-3 的可执行断言只对收缩压/舒张压成立）。
        // 心血管那条排在糖尿病之前，理由见本方法 javadoc（面板只描述第一条）。
        rule("bmi", "心血管", Direction.ABOVE, "24.0", 20);
        rule("bmi", "糖尿病", Direction.ABOVE, "24.0", 25);

        // ---- 心血管自有输入（D-01）：血脂四项 + 上表的血压/血糖/BMI ----
        // 权重合计 150 > 100，因此「全部异常」的用户会真实触发封顶折算（不变量 A）。
        // 本表每项都是产品判断，不是临床指南的引用；改动任何一条都要先看
        // RiskAssessmentAssumptionsTest 的封顶用例（手算 150）是否仍成立。
        //
        // 年龄基线 5 分写在引擎里（age > 55 → 15，否则 5），不在本表 —— 年龄不是
        // health_metric，没有 canonical key。基线 5 让「单项严重血压」能跨 MEDIUM
        // （5 + 30 = 35），孤立肥胖（5+20=25）、孤立高胆固醇（5+20=25）、孤立低 HDL
        // （5+15=20）仍是 LOW —— 这是有意设计：单项血脂异常不构成与 2 级高血压同等的近期风险。
        rule("totalCholesterol", "心血管", Direction.ABOVE, "5.2", 20);
        rule("triglycerides", "心血管", Direction.ABOVE, "1.7", 10);
        // 唯一的 BELOW：HDL 是「越高越好」，低于阈值才是风险。
        rule("hdlCholesterol", "心血管", Direction.BELOW, "1.0", 15);
        rule("ldlCholesterol", "心血管", Direction.ABOVE, "3.4", 15);
    }

    /**
     * Register one rule after proving the key is canonical.
     *
     * <p>A misspelt key would make {@link #rulesFor} return nothing forever: the engine would
     * award zero points and the panel would render no note, both silently. This constructor-time
     * check turns that into a startup failure — the same defect class has shipped twice in this
     * project, and silence is the part that made it survive review.</p>
     *
     * @param metricKey canonical metric key
     * @param disease   the assessment that reads it
     * @param direction which side of the threshold is risky
     * @param threshold the threshold value, as declared
     * @param points    points awarded when crossed
     */
    private void rule(String metricKey, String disease, Direction direction, String threshold,
                      int points) {
        requireCanonicalKey(metricKey);
        rulesByKey.computeIfAbsent(metricKey, key -> new ArrayList<>())
                .add(new RiskThresholdRule(metricKey, disease, direction,
                        new BigDecimal(threshold), points));
    }

    /**
     * Reject a non-canonical metric key, naming the reason.
     *
     * <p>Package-private rather than private so a test can prove the guard fires. Keeping the
     * check itself is what matters — a registry that accepts {@code SystolicBP} would build
     * cleanly and then match nothing at runtime, which is precisely the silent failure the
     * class is meant to make impossible.</p>
     *
     * @param metricKey the key to validate
     * @throws IllegalStateException when the key is not a canonical metric key
     */
    static void requireCanonicalKey(String metricKey) {
        if (!MetricKeyNormalizer.isCanonicalKey(metricKey)) {
            throw new IllegalStateException("风险阈值必须使用 canonical key: " + metricKey);
        }
    }

    /**
     * Rules declared for one metric, in declaration order.
     *
     * @param canonicalKey canonical metric key
     * @return immutable list; empty when the metric carries no rule
     */
    public List<RiskThresholdRule> rulesFor(String canonicalKey) {
        List<RiskThresholdRule> rules = rulesByKey.get(canonicalKey);
        return rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * The rule a given assessment reads for a given metric.
     *
     * @param canonicalKey canonical metric key
     * @param disease      the assessment name, e.g. 高血压
     * @return the rule, or empty when this pair carries none
     */
    public Optional<RiskThresholdRule> ruleFor(String canonicalKey, String disease) {
        return rulesFor(canonicalKey).stream()
                .filter(rule -> rule.disease().equals(disease))
                .findFirst();
    }

    /**
     * Every metric key that carries at least one rule, in declaration order.
     *
     * <p>This is the set the coverage panel renders a note for, and the set the engine must
     * actually read. The two being derived from one declaration is the point of the class.</p>
     *
     * @return immutable set of canonical keys
     */
    public Set<String> consumedKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(rulesByKey.keySet()));
    }

    /**
     * Whether any assessment reads this metric.
     *
     * @param canonicalKey canonical metric key
     * @return true when at least one rule exists
     */
    public boolean consumes(String canonicalKey) {
        return rulesByKey.containsKey(canonicalKey);
    }

    /**
     * Every rule, flattened, for tests and audits.
     *
     * @return immutable list of all rules in declaration order
     */
    public List<RiskThresholdRule> allRules() {
        List<RiskThresholdRule> all = new ArrayList<>();
        rulesByKey.values().forEach(all::addAll);
        return List.copyOf(all);
    }
}
