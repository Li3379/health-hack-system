package com.hhs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthProfile;
import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.mapper.RiskAssessmentMapper;
import com.hhs.service.RiskAssessmentService;
import com.hhs.service.domain.AssumptionCollector;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricCoverageAssembler;
import com.hhs.service.domain.MetricDefinition;
import com.hhs.service.domain.MetricKeyNormalizer;
import com.hhs.service.domain.ProfileCompletenessCalculator;
import com.hhs.service.domain.ProfileFieldCatalog;
import com.hhs.service.domain.RiskThresholdRegistry;
import com.hhs.vo.MetricCoverageVO;
import com.hhs.vo.RiskAssessmentVO;
import com.hhs.vo.RiskContributorVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    /** 归因快照的 JSON 编解码器；只处理本类自己的 record 列表，无共享可变状态。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 批次号的时间前缀格式；秒级精度足以排序，随机后缀消解同秒碰撞。 */
    private static final DateTimeFormatter BATCH_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Age-default sentence, recorded in the branch that assigns the default (D-03).
     *
     * <p>The catalog exposes the same sentence through its {@code birthDate} {@code FieldSpec}
     * and via {@link ProfileFieldCatalog#assumptionsFor(HealthProfile)}; the disclosure tests
     * assert this collected list deep-equals that function's output, so a rewording cannot
     * silently ship two different sentences.</p>
     */
    private static final String MISSING_BIRTH_DATE_ASSUMPTION = "缺少出生日期，年龄按 35 岁估算";

    /** BMI-default sentence; see {@link #MISSING_BIRTH_DATE_ASSUMPTION} for the drift guard. */
    private static final String MISSING_BMI_ASSUMPTION = "缺少 BMI，按 22.0 估算";

    private final RiskAssessmentMapper riskAssessmentMapper;
    private final HealthProfileMapper healthProfileMapper;
    private final HealthMetricMapper healthMetricMapper;
    private final ProfileCompletenessCalculator profileCompletenessCalculator;
    private final MetricCoverageAssembler metricCoverageAssembler;
    private final RiskThresholdRegistry riskThresholds;

    /**
     * Construct the service with its mappers and the completeness calculator.
     *
     * @param riskAssessmentMapper          risk-assessment persistence
     * @param healthProfileMapper           health-profile lookup
     * @param healthMetricMapper            recent-metric lookup
     * @param profileCompletenessCalculator authoritative completeness source
     * @param metricCoverageAssembler       coverage-panel assembler
     * @param riskThresholds                authoritative risk thresholds, shared with the panel
     */
    public RiskAssessmentServiceImpl(RiskAssessmentMapper riskAssessmentMapper,
                                     HealthProfileMapper healthProfileMapper,
                                     HealthMetricMapper healthMetricMapper,
                                     ProfileCompletenessCalculator profileCompletenessCalculator,
                                     MetricCoverageAssembler metricCoverageAssembler,
                                     RiskThresholdRegistry riskThresholds) {
        this.riskAssessmentMapper = riskAssessmentMapper;
        this.healthProfileMapper = healthProfileMapper;
        this.healthMetricMapper = healthMetricMapper;
        this.profileCompletenessCalculator = profileCompletenessCalculator;
        this.metricCoverageAssembler = metricCoverageAssembler;
        this.riskThresholds = riskThresholds;
    }

    /**
     * Execute risk assessment and persist results, returning multiple assessment records.
     *
     * <p>Rows are appended, never overwritten: the previous implementation deleted the user's
     * entire history on every run, which made the risk trend — the single most useful thing an
     * assessment produces — impossible to see. Rows from one run share a {@code batchId}.</p>
     *
     * @param userId User ID
     * @return List of risk assessment results (diabetes, hypertension, cardiovascular)
     * @throws BusinessException if user data is insufficient for assessment
     */
    @Override
    @Transactional
    public List<RiskAssessmentVO> createAssessment(Long userId) {
        HealthProfile profile = getProfileByUserId(userId);
        AssumptionCollector collector = new AssumptionCollector();
        List<ScoredAssessment> scored = RiskRuleEngine.evaluateDetailed(userId, profile,
                getRecentMetrics(userId), collector, riskThresholds);

        // 追加而不是覆盖：删除会让风险趋势无法追踪，而趋势恰恰是风险评估最有价值的部分。
        // 同一次运行的三条记录共享一个 batchId，列表据此折叠成「一次评估」。
        String batchId = newBatchId();
        for (ScoredAssessment item : scored) {
            RiskAssessment row = item.assessment();
            row.setBatchId(batchId);
            // 冻结当时的归因：读取时原样返回，不重算，历史记录才永远是「当时的真实依据」。
            row.setContributors(serializeContributors(item.contributors()));
            riskAssessmentMapper.insert(row);
        }

        int completeness = profileCompletenessCalculator.calculate(profile).score();
        List<String> assumptions = collector.toList();
        return scored.stream()
                .map(item -> toVO(item.assessment(), completeness, assumptions, item.contributors()))
                .toList();
    }

    /**
     * 生成批次号。
     *
     * <p>时间前缀让批次天然有序（列表按批次倒序即时间倒序），随机后缀避免同毫秒并发碰撞。</p>
     *
     * @return 32 字符以内的批次标识
     */
    private static String newBatchId() {
        return "b" + LocalDateTime.now().format(BATCH_TIME_FORMAT) + "-" + ThreadLocalRandom.current().nextLong(0x7FFFFFFF);
    }

    /**
     * 把归因序列化为 JSON 快照。
     *
     * @param contributors 归因列表
     * @return JSON 字符串；序列化失败时返回 {@code null}（宁可没有明细，也不写坏数据）
     */
    private String serializeContributors(List<RiskContributorVO> contributors) {
        try {
            return OBJECT_MAPPER.writeValueAsString(contributors);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize risk contributors, storing null: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JSON 快照反序列化归因。
     *
     * @param json 快照，可能为 {@code null}（存量行或序列化失败）
     * @return 归因列表；无法解析时返回空列表，由前端说明「该记录未保存明细」
     */
    private List<RiskContributorVO> deserializeContributors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<RiskContributorVO>>() { });
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stored risk contributors: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get a specific risk assessment by ID
     *
     * @param id Assessment ID
     * @param userId User ID (for authorization)
     * @return Risk assessment result or null if not found
     */
    @Override
    public RiskAssessmentVO getById(Long id, Long userId) {
        RiskAssessment r = riskAssessmentMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) return null;
        HealthProfile profile = getProfileByUserId(r.getUserId());
        return toVO(r, profileCompletenessCalculator.calculate(profile).score(),
                disclosedAssumptions(profile), deserializeContributors(r.getContributors()));
    }

    /**
     * Get all risk assessment records for a user (history)
     *
     * @param userId User ID
     * @return List of risk assessments sorted by creation time (descending)
     */
    @Override
    public List<RiskAssessmentVO> listByUserId(Long userId) {
        List<RiskAssessment> assessments = riskAssessmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RiskAssessment>()
                        .eq(RiskAssessment::getUserId, userId)
                        .orderByDesc(RiskAssessment::getCreateTime));
        if (assessments.isEmpty()) {
            return List.of();
        }
        HealthProfile profile = getProfileByUserId(userId);
        int completeness = profileCompletenessCalculator.calculate(profile).score();
        List<String> assumptions = disclosedAssumptions(profile);

        // 归因直接读快照：每行都带着它创建时的真实依据，与分数必然一致。
        // 存量行的快照为 null，返回空列表 —— 前端据此说明「该记录创建时未保存明细」，
        // 而不是拿当前数据重算出一份与旧分数对不上的明细。
        return assessments.stream()
                .map(r -> toVO(r, completeness, assumptions,
                        deserializeContributors(r.getContributors())))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the same three-month window the rule engine uses, so the coverage view describes
     * exactly the data the assessment was allowed to see.</p>
     */
    @Override
    public List<MetricCoverageVO> getMetricCoverage(Long userId) {
        return metricCoverageAssembler.assemble(getRecentMetrics(userId));
    }


    private HealthProfile getProfileByUserId(Long userId) {
        return healthProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthProfile>()
                        .eq(HealthProfile::getUserId, userId).last("LIMIT 1"));
    }

    private List<HealthMetric> getRecentMetrics(Long userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(3);
        return healthMetricMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthMetric>()
                        .eq(HealthMetric::getUserId, userId)
                        .ge(HealthMetric::getRecordDate, start)
                        .le(HealthMetric::getRecordDate, end)
                        .orderByDesc(HealthMetric::getRecordDate));
    }

    /**
     * Disclose the substitution sentences for a profile on the read path.
     *
     * <p>This is deliberately a delegation and not a second derivation: the create path records
     * sentences at each substitution site and this read path recomputes them from the current
     * profile, and both must resolve to the identical expression
     * ({@link ProfileFieldCatalog#assumptionsFor}). Re-forking it here would reintroduce the
     * drift between the backfill panel's pre-create promise and this response.</p>
     *
     * @param profile the stored profile, or {@code null} when the user has none
     * @return the disclosed sentences; never {@code null}
     */
    static List<String> disclosedAssumptions(HealthProfile profile) {
        return ProfileFieldCatalog.assumptionsFor(profile);
    }

    private RiskAssessmentVO toVO(RiskAssessment r, int profileCompleteness, List<String> assumptions,
                                  List<RiskContributorVO> contributors) {
        return new RiskAssessmentVO(r.getId(), r.getUserId(), r.getProfileId(), r.getBatchId(),
                r.getDiseaseName(), r.getRiskLevel(), r.getRiskScore(), r.getSuggestion(),
                profileCompleteness, r.getCreateTime(), assumptions, contributors);
    }

    /**
     * 基于规则的简易风险评估引擎（糖尿病/高血压/心血管）。
     *
     * <p>Every branch records the points it awards into a {@link RiskContributorVO} list, so the
     * returned score can be explained item by item. A factor whose input is absent records
     * {@code MISSING} rather than silently contributing zero — otherwise "your reading is
     * normal" and "we have no reading" would produce the same score and the same copy.</p>
     */
    private static final class RiskRuleEngine {

        /**
         * Metric metadata, held locally because this engine is static.
         *
         * <p>Same-source beats one fewer instance: the constructor has no side effects (it only
         * runs {@code registerAllMetrics()}, and {@code @PostConstruct} is guarded by an
         * {@code isEmpty()} check), and the startup recommendation pass rewrites only
         * {@code alertable}/{@code recommended} — {@code precision} is copied unchanged. A
         * locally built registry therefore yields byte-identical precision to the Spring bean,
         * while the alternative (threading the bean through every static call) would widen a
         * constructor signature that the tests depend on.</p>
         */
        private static final HealthMetricMetaRegistry METRICS = new HealthMetricMetaRegistry();

        static List<RiskAssessment> evaluate(Long userId, HealthProfile profile, List<HealthMetric> recentMetrics,
                                             RiskThresholdRegistry thresholds) {
            return evaluate(userId, profile, recentMetrics, AssumptionCollector.noop(), thresholds);
        }

        static List<RiskAssessment> evaluate(Long userId, HealthProfile profile, List<HealthMetric> recentMetrics,
                                             AssumptionCollector collector,
                                             RiskThresholdRegistry thresholds) {
            return evaluateDetailed(userId, profile, recentMetrics, collector, thresholds).stream()
                    .map(ScoredAssessment::assessment)
                    .toList();
        }

        /**
         * Evaluate and keep each disease's contributor list alongside its row.
         *
         * @param userId        owner
         * @param profile       health profile, or {@code null}
         * @param recentMetrics recent metrics, newest first
         * @param collector     receives the substitution sentences
         * @param thresholds    authoritative risk thresholds, shared with the coverage panel
         * @return one entry per disease, in 糖尿病 / 高血压 / 心血管疾病 order
         */
        static List<ScoredAssessment> evaluateDetailed(Long userId, HealthProfile profile,
                                                       List<HealthMetric> recentMetrics,
                                                       AssumptionCollector collector,
                                                       RiskThresholdRegistry thresholds) {
            List<ScoredAssessment> result = new ArrayList<>();
            Long profileId = profile != null ? profile.getId() : null;

            if (profile == null) {
                collector.add(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION);
                result.add(new ScoredAssessment(create(userId, null, "糖尿病", 30),
                        List.of(RiskContributorVO.missing("profile", "健康档案"))));
                result.add(new ScoredAssessment(create(userId, null, "高血压", 25),
                        List.of(RiskContributorVO.missing("profile", "健康档案"))));
                result.add(new ScoredAssessment(create(userId, null, "心血管疾病", 25),
                        List.of(RiskContributorVO.missing("profile", "健康档案"))));
                return result;
            }

            // ---- 年龄：档案缺失时按 35 岁代入（与假设句同源） ----
            int age;
            String ageValue;
            String ageState;
            if (profile.getBirthDate() == null) {
                age = 35;
                ageValue = "35 岁（估算）";
                ageState = RiskContributorVO.STATE_ASSUMED;
                collector.add(MISSING_BIRTH_DATE_ASSUMPTION);
            } else {
                age = ageAt(profile.getBirthDate(), LocalDate.now());
                ageValue = age + " 岁";
                ageState = RiskContributorVO.STATE_CONTRIBUTED;
            }

            // ---- BMI：档案缺失时按 22.0 代入 ----
            boolean bmiAssumed = profile.getBmi() == null;
            BigDecimal bmi = bmiAssumed ? BigDecimal.valueOf(22) : profile.getBmi();
            // 与覆盖面板共用同一段 formatter（domain 包里的静态 format），否则同一个
            // 22.86 在弹窗显示 22.86、在面板显示 22.9 —— 同屏两个数字说同一件事却说成两样。
            String bmiValue = bmiAssumed ? "22.0（估算）"
                    : MetricCoverageAssembler.format(bmi, definitionOf("bmi"));
            if (bmiAssumed) {
                collector.add(MISSING_BMI_ASSUMPTION);
            }

            // ---- 家族史：缺失与「有但无糖尿病关键词」是两种不同状态 ----
            String familyHistory = profile.getFamilyHistory();
            boolean familyBlank = familyHistory == null || familyHistory.isBlank();
            if (familyBlank) {
                collector.add(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT);
            } else if (!ProfileFieldCatalog.mentionsDiabetes(familyHistory)) {
                collector.add(ProfileFieldCatalog.FAMILY_HISTORY_SUPPLIED_NO_DIABETES);
            }
            boolean familyDiabetes = ProfileFieldCatalog.mentionsDiabetes(familyHistory);

            // ---- 指标：缺失即为 MISSING，绝不按「正常」计 ----
            BigDecimal latestBloodSugar = getLatestMetric(recentMetrics, "bloodGlucose");
            BigDecimal systolic = getLatestMetric(recentMetrics, "systolicBP");
            BigDecimal diastolic = getLatestMetric(recentMetrics, "diastolicBP");

            // 血脂四项：库里当前零条记录。缺失一律 MISSING + 0 分，绝不代入人群默认值 ——
            // 代入等于凭空造临床值，与「区分无数据与正常」的原则直接冲突。
            BigDecimal totalCholesterol = getLatestMetric(recentMetrics, "totalCholesterol");
            BigDecimal triglycerides = getLatestMetric(recentMetrics, "triglycerides");
            BigDecimal hdlCholesterol = getLatestMetric(recentMetrics, "hdlCholesterol");
            BigDecimal ldlCholesterol = getLatestMetric(recentMetrics, "ldlCholesterol");

            // ================= 糖尿病 =================
            List<RiskContributorVO> diabetesParts = new ArrayList<>();

            int ageDiabetesPoints = age > 45 ? 20 : 10;
            diabetesParts.add(new RiskContributorVO("age", "年龄", ageState, ageValue,
                    "45 岁", ageDiabetesPoints,
                    age > 45
                            ? "年龄 " + ageValue + "，超过 45 岁，计入 " + ageDiabetesPoints + " 分"
                            : "年龄 " + ageValue + "，未超过 45 岁，计入 " + ageDiabetesPoints + " 分"));

            RiskThresholdRegistry.RiskThresholdRule bmiRule =
                    requiredRule(thresholds, "bmi", "糖尿病");
            boolean bmiHigh = bmi.compareTo(bmiRule.threshold()) > 0;
            // 未命中时的 10 分是无条件基线，不是阈值奖励 —— 它不属于阈值表，故意留在这里。
            int bmiPoints = bmiHigh ? bmiRule.points() : 10;
            // An assumed BMI is never "the user's own value": label it ASSUMED so the panel can
            // prompt them to fill it in, and so nobody reads the points as measured evidence.
            String bmiState = bmiAssumed
                    ? RiskContributorVO.STATE_ASSUMED
                    : (bmiHigh ? RiskContributorVO.STATE_CONTRIBUTED : RiskContributorVO.STATE_NORMAL);
            diabetesParts.add(new RiskContributorVO("bmi", "BMI", bmiState, bmiValue,
                    "24.0", bmiPoints,
                    bmiHigh
                            ? "BMI " + bmiValue + (bmiAssumed ? "（按 22.0 估算）" : "")
                                    + "，超过 24.0，计入 " + bmiPoints + " 分"
                            : "BMI " + bmiValue + (bmiAssumed ? "，档案缺失按 22.0 估算" : "")
                                    + "，未超过 24.0，计入 " + bmiPoints + " 分"));

            int familyPoints = familyDiabetes ? 25 : 0;
            diabetesParts.add(familyBlank
                    ? RiskContributorVO.missing("familyHistory", "家族病史")
                    : new RiskContributorVO("familyHistory", "家族病史",
                            familyDiabetes ? RiskContributorVO.STATE_CONTRIBUTED : RiskContributorVO.STATE_NORMAL,
                            familyHistory, "有糖尿病家族史", familyPoints,
                            familyDiabetes
                                    ? "家族病史提到糖尿病，计入 " + familyPoints + " 分"
                                    : "家族病史未提到糖尿病，未加分"));

            RiskThresholdRegistry.RiskThresholdRule sugarRule =
                    requiredRule(thresholds, "bloodGlucose", "糖尿病");
            boolean sugarHigh = latestBloodSugar != null
                    && latestBloodSugar.compareTo(sugarRule.threshold()) > 0;
            int sugarPoints = sugarHigh ? sugarRule.points() : 0;
            diabetesParts.add(RiskContributorVO.threshold("bloodGlucose", "血糖",
                    latestBloodSugar == null ? null
                            : MetricCoverageAssembler.format(latestBloodSugar,
                                    definitionOf("bloodGlucose")) + " mmol/L",
                    "6.1 mmol/L", sugarHigh, sugarPoints));

            // Sum the parts rather than recomputing: one code path, so the breakdown a user
            // reads can never disagree with the score above it.
            int diabetesScore = diabetesParts.stream().mapToInt(RiskContributorVO::points).sum();
            result.add(new ScoredAssessment(create(userId, profileId, "糖尿病", diabetesScore), diabetesParts));

            // ================= 高血压 =================
            List<RiskContributorVO> bpParts = new ArrayList<>();

            int ageBpPoints = age > 50 ? 15 : 5;
            bpParts.add(new RiskContributorVO("age", "年龄", ageState, ageValue,
                    "50 岁", ageBpPoints,
                    age > 50
                            ? "年龄 " + ageValue + "，超过 50 岁，计入 " + ageBpPoints + " 分"
                            : "年龄 " + ageValue + "，未超过 50 岁，计入 " + ageBpPoints + " 分"));

            RiskThresholdRegistry.RiskThresholdRule systolicRule =
                    requiredRule(thresholds, "systolicBP", "高血压");
            boolean systolicHigh = systolic != null && systolic.compareTo(systolicRule.threshold()) > 0;
            bpParts.add(RiskContributorVO.threshold("systolicBP", "收缩压",
                    systolic == null ? null
                            : MetricCoverageAssembler.format(systolic,
                                    definitionOf("systolicBP")) + " mmHg",
                    "130 mmHg", systolicHigh, systolicHigh ? systolicRule.points() : 0));

            RiskThresholdRegistry.RiskThresholdRule diastolicRule =
                    requiredRule(thresholds, "diastolicBP", "高血压");
            boolean diastolicHigh = diastolic != null && diastolic.compareTo(diastolicRule.threshold()) > 0;
            bpParts.add(RiskContributorVO.threshold("diastolicBP", "舒张压",
                    diastolic == null ? null
                            : MetricCoverageAssembler.format(diastolic,
                                    definitionOf("diastolicBP")) + " mmHg",
                    "85 mmHg", diastolicHigh, diastolicHigh ? diastolicRule.points() : 0));

            int bpScore = bpParts.stream().mapToInt(RiskContributorVO::points).sum();
            result.add(new ScoredAssessment(create(userId, profileId, "高血压", bpScore), bpParts));

            // ================= 心血管 =================
            // 这是一次独立评估（D-01）：9 项自有输入，没有任何一项取自糖尿病分或高血压分。
            // 此前这里是「两种病的分数各取一半，再加一个 BMI 超额奖励」，于是用户点开
            // 「心血管疾病」看到的全是别的疾病的依据 —— 没有一条是心血管自己的，而血脂、
            // BMI、年龄这些核心因子从未参与。
            //
            // 键顺序即 UI-SPEC Contract 3.1 的 9 行枚举，也是 contributors 快照的数组顺序。
            List<RiskContributorVO> cardioParts = new ArrayList<>();

            // ---- 1. 年龄：基线 5 分，越过 55 岁计 15 分 ----
            // 基线不是「奖励」而是分级设计的一部分：5 + 30（严重收缩压）= 35，恰好跨 MEDIUM，
            // 因此「血压 168 的年轻用户」在心血管卡上不会读成 LOW，与高血压卡不再自相矛盾。
            int ageCardioPoints = age > 55 ? 15 : 5;
            cardioParts.add(new RiskContributorVO("age", "年龄", ageState, ageValue,
                    "55 岁", ageCardioPoints,
                    age > 55
                            ? "年龄 " + ageValue + "，超过 55 岁，计入 " + ageCardioPoints + " 分"
                            : "年龄 " + ageValue + "，未超过 55 岁，计入 " + ageCardioPoints + " 分"));

            // ---- 2. BMI：读注册表的心血管规则（24.0 / 20），复用已渲染好的 bmiValue ----
            RiskThresholdRegistry.RiskThresholdRule cardioBmiRule =
                    requiredRule(thresholds, "bmi", "心血管");
            boolean cardioBmiHigh = bmi.compareTo(cardioBmiRule.threshold()) > 0;
            int cardioBmiPoints = cardioBmiHigh ? cardioBmiRule.points() : 0;
            // 档案缺失时的 BMI 既不是用户真实数据，也不是「完全没计入」：标 ASSUMED，
            // 让面板能提示补填，也避免把估算值当成实测证据。
            String cardioBmiState = bmiAssumed
                    ? RiskContributorVO.STATE_ASSUMED
                    : (cardioBmiHigh ? RiskContributorVO.STATE_CONTRIBUTED
                            : RiskContributorVO.STATE_NORMAL);
            cardioParts.add(new RiskContributorVO("bmi", "BMI", cardioBmiState, bmiValue,
                    "24.0", cardioBmiPoints,
                    cardioBmiHigh
                            ? "BMI " + bmiValue + "，超过 24.0，计入 " + cardioBmiPoints + " 分"
                            : "BMI " + bmiValue + (bmiAssumed ? "，档案缺失按 22.0 估算" : "")
                                    + "，未超过 24.0，未加分"));

            // ---- 3. 收缩压：与高血压共用阈值 130，但分值不同（30 vs 35） ----
            RiskThresholdRegistry.RiskThresholdRule cardioSystolicRule =
                    requiredRule(thresholds, "systolicBP", "心血管");
            boolean cardioSystolicHigh = systolic != null
                    && systolic.compareTo(cardioSystolicRule.threshold()) > 0;
            cardioParts.add(RiskContributorVO.threshold("systolicBP", "收缩压",
                    systolic == null ? null
                            : MetricCoverageAssembler.format(systolic,
                                    definitionOf("systolicBP")) + " mmHg",
                    "130 mmHg", cardioSystolicHigh,
                    cardioSystolicHigh ? cardioSystolicRule.points() : 0));

            // ---- 4. 舒张压 ----
            RiskThresholdRegistry.RiskThresholdRule cardioDiastolicRule =
                    requiredRule(thresholds, "diastolicBP", "心血管");
            boolean cardioDiastolicHigh = diastolic != null
                    && diastolic.compareTo(cardioDiastolicRule.threshold()) > 0;
            cardioParts.add(RiskContributorVO.threshold("diastolicBP", "舒张压",
                    diastolic == null ? null
                            : MetricCoverageAssembler.format(diastolic,
                                    definitionOf("diastolicBP")) + " mmHg",
                    "85 mmHg", cardioDiastolicHigh,
                    cardioDiastolicHigh ? cardioDiastolicRule.points() : 0));

            // ---- 5. 血糖 ----
            RiskThresholdRegistry.RiskThresholdRule cardioSugarRule =
                    requiredRule(thresholds, "bloodGlucose", "心血管");
            boolean cardioSugarHigh = latestBloodSugar != null
                    && latestBloodSugar.compareTo(cardioSugarRule.threshold()) > 0;
            cardioParts.add(RiskContributorVO.threshold("bloodGlucose", "血糖",
                    latestBloodSugar == null ? null
                            : MetricCoverageAssembler.format(latestBloodSugar,
                                    definitionOf("bloodGlucose")) + " mmol/L",
                    "6.1 mmol/L", cardioSugarHigh,
                    cardioSugarHigh ? cardioSugarRule.points() : 0));

            // ---- 6-9. 血脂四项：缺失一律 MISSING + 0 分，绝不代入人群默认值（D-02） ----
            // 阈值按注册表精度渲染（四项 precision 均为 2）：写成 "5.2" 会与面板 riskNote 的
            // "5.20" 在同一个值的两种小数位之间分叉。
            RiskThresholdRegistry.RiskThresholdRule totalCholesterolRule =
                    requiredRule(thresholds, "totalCholesterol", "心血管");
            boolean totalCholesterolHigh = totalCholesterol != null
                    && totalCholesterol.compareTo(totalCholesterolRule.threshold()) > 0;
            cardioParts.add(RiskContributorVO.threshold("totalCholesterol", "总胆固醇",
                    totalCholesterol == null ? null
                            : MetricCoverageAssembler.format(totalCholesterol,
                                    definitionOf("totalCholesterol")) + " mmol/L",
                    "5.20 mmol/L", totalCholesterolHigh,
                    totalCholesterolHigh ? totalCholesterolRule.points() : 0));

            RiskThresholdRegistry.RiskThresholdRule triglyceridesRule =
                    requiredRule(thresholds, "triglycerides", "心血管");
            boolean triglyceridesHigh = triglycerides != null
                    && triglycerides.compareTo(triglyceridesRule.threshold()) > 0;
            cardioParts.add(RiskContributorVO.threshold("triglycerides", "甘油三酯",
                    triglycerides == null ? null
                            : MetricCoverageAssembler.format(triglycerides,
                                    definitionOf("triglycerides")) + " mmol/L",
                    "1.70 mmol/L", triglyceridesHigh,
                    triglyceridesHigh ? triglyceridesRule.points() : 0));

            // HDL 是下限型：低于阈值才是风险，因此走 below(...) 而不是 threshold(...)，
            // 否则一个 0.90 的人会被告知「超过」了它实际低于的阈值。
            RiskThresholdRegistry.RiskThresholdRule hdlRule =
                    requiredRule(thresholds, "hdlCholesterol", "心血管");
            boolean hdlLow = hdlCholesterol != null
                    && hdlCholesterol.compareTo(hdlRule.threshold()) < 0;
            cardioParts.add(RiskContributorVO.below("hdlCholesterol", "高密度脂蛋白",
                    hdlCholesterol == null ? null
                            : MetricCoverageAssembler.format(hdlCholesterol,
                                    definitionOf("hdlCholesterol")) + " mmol/L",
                    "1.00 mmol/L", hdlLow, hdlLow ? hdlRule.points() : 0));

            RiskThresholdRegistry.RiskThresholdRule ldlRule =
                    requiredRule(thresholds, "ldlCholesterol", "心血管");
            boolean ldlHigh = ldlCholesterol != null
                    && ldlCholesterol.compareTo(ldlRule.threshold()) > 0;
            cardioParts.add(RiskContributorVO.threshold("ldlCholesterol", "低密度脂蛋白",
                    ldlCholesterol == null ? null
                            : MetricCoverageAssembler.format(ldlCholesterol,
                                    definitionOf("ldlCholesterol")) + " mmol/L",
                    "3.40 mmol/L", ldlHigh, ldlHigh ? ldlRule.points() : 0));

            int cardioRaw = cardioParts.stream().mapToInt(RiskContributorVO::points).sum();
            // 9 项上限合计 150 > 100，因此「全部异常」的用户真实走到这里。分数封顶 100，
            // 归因也必须一起折算 —— 否则用户把 9 行加起来得到 150，却看到总分 100。
            if (cardioRaw > 100) {
                cardioParts = scaleTo(cardioParts, cardioRaw, 100);
            }
            int cardioScore = Math.min(100, cardioRaw);
            result.add(new ScoredAssessment(create(userId, profileId, "心血管疾病", cardioScore), cardioParts));

            return result;
        }

        /**
         * Look up the rule one assessment reads for one metric, failing loudly when absent.
         *
         * <p>Returning a default instead would silently award zero points — the exact failure
         * mode this registry exists to remove, where a misspelt or missing rule lowers a score
         * without anyone noticing. The lookup goes through {@code ruleFor} once, so the
         * "what happens when the rule is missing" decision lives in one place rather than being
         * re-derived at each of the four call sites.</p>
         *
         * @param thresholds   the authoritative registry
         * @param canonicalKey canonical metric key
         * @param disease      the assessment reading it
         * @return the matching rule
         * @throws IllegalStateException when the registry declares no such rule
         */
        private static RiskThresholdRegistry.RiskThresholdRule requiredRule(
                RiskThresholdRegistry thresholds, String canonicalKey, String disease) {
            return thresholds.ruleFor(canonicalKey, disease)
                    .orElseThrow(() -> new IllegalStateException(
                            "缺少风险阈值规则: " + canonicalKey + "/" + disease));
        }

        /**
         * Scale a contributor list down so its points sum to exactly {@code target}.
         *
         * <p>Proportional scaling alone leaves a rounding residue; the largest-remainder pass
         * hands the leftover points to the items with the biggest fractional part, so the total
         * lands on {@code target} rather than near it. The explanation states the cap, because
         * a user comparing an item against its pre-cap value would otherwise see a silent
         * discrepancy.</p>
         *
         * @param parts  contributors whose points sum to {@code currentTotal}
         * @param currentTotal sum before scaling, greater than {@code target}
         * @param target desired sum, typically 100
         * @return contributors summing to exactly {@code target}
         */
        private static List<RiskContributorVO> scaleTo(List<RiskContributorVO> parts, int currentTotal,
                                                       int target) {
            int[] scaled = new int[parts.size()];
            double[] remainders = new double[parts.size()];
            int assigned = 0;
            for (int i = 0; i < parts.size(); i++) {
                double exact = (double) parts.get(i).points() * target / currentTotal;
                scaled[i] = (int) Math.floor(exact);
                remainders[i] = exact - scaled[i];
                assigned += scaled[i];
            }
            // Distribute the leftover to the largest fractional parts, highest first.
            int leftover = target - assigned;
            while (leftover > 0) {
                int best = -1;
                double bestRemainder = -1;
                for (int i = 0; i < scaled.length; i++) {
                    if (remainders[i] > bestRemainder) {
                        bestRemainder = remainders[i];
                        best = i;
                    }
                }
                scaled[best]++;
                remainders[best] = -1;
                leftover--;
            }

            List<RiskContributorVO> scaledParts = new ArrayList<>(parts.size());
            for (int i = 0; i < parts.size(); i++) {
                RiskContributorVO p = parts.get(i);
                scaledParts.add(new RiskContributorVO(p.key(), p.label(), p.state(), p.value(),
                        p.threshold(), scaled[i],
                        p.explanation() + "（总分上限 100，已按比例折算为 " + scaled[i] + " 分）"));
            }
            return scaledParts;
        }

        /**
         * Look up a metric's definition from the locally held metadata registry.
         *
         * <p>Failing loudly mirrors {@link #requiredRule}: rendering a value with an invented
         * precision would re-create the very split this formatter exists to remove, and it would
         * do so silently.</p>
         *
         * @param canonicalKey canonical metric key
         * @return the definition carrying unit and precision
         * @throws IllegalStateException when the registry declares no such metric
         */
        private static MetricDefinition definitionOf(String canonicalKey) {
            return METRICS.getDefinition(canonicalKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "缺少指标元数据: " + canonicalKey));
        }

        /**
         * Read the most recent value for a canonical metric key, accepting every alias.
         *
         * <p>{@code metric_key} is stored canonical (camelCase English) since the v1.1 SSOT
         * work, so matching on a Chinese display name — as this method used to — matched
         * nothing and every risk score silently lost its metric contribution. The input list
         * is already ordered {@code record_date DESC}, so the first alias match is the latest.
         *
         * <p>Aliases are still accepted so rows written before the FIX-02 canonicalization
         * (e.g. {@code glucose}) keep contributing.
         *
         * @param metrics    recent metrics, newest first
         * @param canonicalKey canonical key such as {@code bloodGlucose}
         * @return latest value, or {@code null} when the user has none
         */
        private static BigDecimal getLatestMetric(List<HealthMetric> metrics, String canonicalKey) {
            Set<String> aliases = MetricKeyNormalizer.getAliases(canonicalKey);
            return metrics.stream()
                    .filter(m -> m.getMetricKey() != null && aliases.contains(m.getMetricKey()))
                    .findFirst()
                    .map(HealthMetric::getValue)
                    .orElse(null);
        }

        /**
         * Whole years between a birth date and a reference date.
         *
         * <p>Year subtraction (the previous implementation) counts a birthday as passed as
         * soon as the year turns over, so anyone born late in the year is one year older than
         * they are — enough to cross the 45- and 50-year thresholds this engine branches on.
         *
         * @param birthDate date of birth
         * @param on        reference date, normally today
         * @return completed years of age
         */
        private static int ageAt(LocalDate birthDate, LocalDate on) {
            return Period.between(birthDate, on).getYears();
        }

        private static RiskAssessment create(Long userId, Long profileId, String diseaseName, int score) {
            RiskAssessment r = new RiskAssessment();
            r.setUserId(userId);
            r.setProfileId(profileId);
            r.setDiseaseName(diseaseName);
            r.setRiskScore(score);
            if (score >= 60) {
                r.setRiskLevel("HIGH");
                r.setSuggestion("建议尽快就医进行专项检查，并改善生活方式。");
            } else if (score >= 35) {
                r.setRiskLevel("MEDIUM");
                r.setSuggestion("建议定期体检，注意饮食与运动，必要时咨询医生。");
            } else {
                r.setRiskLevel("LOW");
                r.setSuggestion("当前风险较低，保持健康生活习惯即可。");
            }
            return r;
        }
    }

    /**
     * A scored disease row together with the factors that produced its score.
     *
     * <p>The contributor list travels with the row rather than being recomputed later, so the
     * numbers a user is shown always add up to the score they are shown.</p>
     *
     * @param assessment   the row as persisted
     * @param contributors the scored inputs, in evaluation order
     */
    private record ScoredAssessment(RiskAssessment assessment,
                                    List<RiskContributorVO> contributors) {
    }
}