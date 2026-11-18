package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthProfile;
import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.mapper.RiskAssessmentMapper;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricCoverageAssembler;
import com.hhs.service.domain.ProfileCompletenessCalculator;
import com.hhs.service.domain.ProfileFieldCatalog;
import com.hhs.service.domain.RiskThresholdRegistry;
import com.hhs.service.impl.RiskAssessmentServiceImpl;
import com.hhs.vo.MetricCoverageVO;
import com.hhs.vo.RiskAssessmentVO;
import com.hhs.vo.RiskContributorVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Assumption-disclosure tests for the risk-assessment path.
 *
 * <p>The subject is the real service with its real rule engine, so the mappers are mocked and
 * nothing between the substitution branch and the response is stubbed. Every expected sentence
 * is read from {@link ProfileFieldCatalog} rather than restated here: a reworded catalog entry
 * must fail these tests instead of silently shipping a second copy of the sentence.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("风险评估路径假设披露测试")
class RiskAssessmentAssumptionsTest {

    @Mock
    private RiskAssessmentMapper riskAssessmentMapper;

    @Mock
    private HealthProfileMapper healthProfileMapper;

    @Mock
    private HealthMetricMapper healthMetricMapper;

    private RiskAssessmentServiceImpl service;

    /** 服务与覆盖面板必须共享同一份阈值声明 —— 各 new 一个会让同源断言在后续计划里漂移。 */
    private RiskThresholdRegistry riskThresholds;

    /** Held as a field so the panel can be asserted against the same engine run. */
    private MetricCoverageAssembler assembler;

    @BeforeEach
    void setUp() {
        riskThresholds = new RiskThresholdRegistry();
        assembler = new MetricCoverageAssembler(new HealthMetricMetaRegistry(), riskThresholds);
        service = new RiskAssessmentServiceImpl(riskAssessmentMapper, healthProfileMapper,
                healthMetricMapper, new ProfileCompletenessCalculator(new ProfileFieldCatalog()),
                assembler, riskThresholds);
    }

    @Test
    @DisplayName("无档案：固定 30/25/25 且只披露一条通用假设，完整度 0")
    void nullProfileReturnsFixedScoresAndOneGenericAssumption() {
        stubMissingProfile();
        stubNoMetrics();

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        assertEquals(3, vos.size());
        assertEquals(List.of("糖尿病", "高血压", "心血管疾病"),
                vos.stream().map(RiskAssessmentVO::diseaseName).toList());
        assertEquals(List.of(30, 25, 25), vos.stream().map(RiskAssessmentVO::riskScore).toList());
        for (RiskAssessmentVO vo : vos) {
            assertEquals(0, vo.profileCompleteness());
            assertEquals(List.of(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION), vo.assumptions());
        }
    }

    @Test
    @DisplayName("无档案：披露列表与档案目录同源，逐字相等")
    void nullProfileAssumptionsMatchCatalogSource() {
        stubMissingProfile();
        stubNoMetrics();

        List<String> disclosed = service.createAssessment(1L).get(0).assumptions();

        assertEquals(ProfileFieldCatalog.assumptionsFor(null), disclosed,
                "the create path must disclose the catalog's own list, not a second literal");
    }

    @Test
    @DisplayName("无档案：假设不提血糖或血压")
    void nullProfileAssumptionNeverClaimsLabData() {
        stubMissingProfile();
        stubNoMetrics();

        for (RiskAssessmentVO vo : service.createAssessment(1L)) {
            List<String> assumptions = vo.assumptions();
            assertNotNull(assumptions);
            for (String assumption : assumptions) {
                assertFalse(assumption.contains("血糖") || assumption.contains("血压"),
                        "an assumption must never claim lab data was used: " + assumption);
            }
        }
    }

    @Test
    @DisplayName("无档案：三条评估共享同一份非空披露列表")
    void nullProfileSharesSingleDisclosureOnEveryRow() {
        stubMissingProfile();
        stubNoMetrics();

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        assertNotNull(vos.get(0).assumptions());
        assertEquals(vos.get(0).assumptions(), vos.get(1).assumptions());
        assertEquals(vos.get(0).assumptions(), vos.get(2).assumptions());
    }

    @Test
    @DisplayName("BMI 为空：披露 22.0 代入，糖尿病得分按 bmi 22 计算")
    void missingBmiDisclosesReferenceValueAndScoreUsesIt() {
        stubProfile(profileWithoutBmi());
        stubNoMetrics();

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        assertEquals(List.of(missingBmiSentence()), vos.get(0).assumptions(),
                "only the BMI sentence applies when birth date and family history are usable");
        // age 36 -> 10, bmi defaulted to 22 -> 10, family diabetes -> 25
        assertEquals(45, vos.get(0).riskScore(),
                "the diabetes score must be computed with the disclosed bmi of 22");
    }

    @Test
    @DisplayName("遗留行出生日期为空：同时披露年龄与 BMI 两句，年龄按 35 岁")
    void legacyRowWithoutBirthDateDisclosesAgeAndBmiSentences() {
        HealthProfile profile = profileWithoutBmi();
        profile.setBirthDate(null);
        stubProfile(profile);
        stubNoMetrics();

        List<String> assumptions = service.createAssessment(1L).get(0).assumptions();

        assertEquals(List.of(missingBirthDateSentence(), missingBmiSentence()), assumptions);
        // age defaulted to 35 -> 10, bmi defaulted to 22 -> 10, family diabetes -> 25
        assertEquals(45, service.createAssessment(1L).stream()
                .filter(vo -> "糖尿病".equals(vo.diseaseName()))
                .findFirst().orElseThrow().riskScore());
    }

    @Test
    @DisplayName("家族病史为空：披露「缺少家族病史」")
    void nullFamilyHistoryDisclosesAbsentSentence() {
        HealthProfile profile = completeProfile();
        profile.setFamilyHistory(null);
        stubProfile(profile);
        stubNoMetrics();

        assertEquals(List.of(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT),
                service.createAssessment(1L).get(0).assumptions());
    }

    @Test
    @DisplayName("家族病史为空白：与为空同等处理")
    void blankFamilyHistoryDisclosesAbsentSentence() {
        HealthProfile profile = completeProfile();
        profile.setFamilyHistory("   ");
        stubProfile(profile);
        stubNoMetrics();

        assertEquals(List.of(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT),
                service.createAssessment(1L).get(0).assumptions());
    }

    @Test
    @DisplayName("已填但未提及糖尿病：披露关键词缺失句，绝不声称字段缺失")
    void suppliedNonDiabeticFamilyHistoryDisclosesKeywordlessSentence() {
        HealthProfile profile = completeProfile();
        profile.setFamilyHistory("父亲有高血压");
        stubProfile(profile);
        stubNoMetrics();

        List<String> assumptions = service.createAssessment(1L).get(0).assumptions();

        assertEquals(List.of(ProfileFieldCatalog.FAMILY_HISTORY_SUPPLIED_NO_DIABETES), assumptions);
        assertFalse(assumptions.contains(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT),
                "a filled family history must never be reported as missing: " + assumptions);
    }

    @Test
    @DisplayName("家族病史提及糖尿病：不做任何家族假设")
    void diabeticFamilyHistoryDisclosesNothing() {
        stubProfile(completeProfile());
        stubNoMetrics();

        assertTrue(service.createAssessment(1L).get(0).assumptions().isEmpty(),
                "a usable family history causes no substitution");
    }

    @Test
    @DisplayName("过敏史为空：引擎不代入该字段，因此不产生假设")
    void blankAllergyHistoryDisclosesNothing() {
        HealthProfile profile = completeProfile();
        profile.setAllergyHistory("  ");
        stubProfile(profile);
        stubNoMetrics();

        assertTrue(service.createAssessment(1L).get(0).assumptions().isEmpty(),
                "allergy history has no substitution branch in the risk engine");
    }

    @Test
    @DisplayName("每种档案形态的披露列表都与档案目录逐字相等")
    void everyProfileShapeMatchesCatalogSource() {
        for (HealthProfile profile : allProfileShapes()) {
            stubProfile(profile);
            stubNoMetrics();

            assertEquals(ProfileFieldCatalog.assumptionsFor(profile),
                    service.createAssessment(1L).get(0).assumptions(),
                    "the create path must disclose the catalog's own list for " + profile);
        }
    }

    private List<HealthProfile> allProfileShapes() {
        HealthProfile nullTwoFields = profileWithoutBmi();
        nullTwoFields.setBirthDate(null);

        HealthProfile noFamily = completeProfile();
        noFamily.setFamilyHistory(null);

        HealthProfile nonDiabeticFamily = completeProfile();
        nonDiabeticFamily.setFamilyHistory("父亲有高血压");

        return List.of(nullTwoFields, noFamily, nonDiabeticFamily, completeProfile());
    }

    private String missingBirthDateSentence() {
        return assumptionOf("birthDate");
    }

    private String missingBmiSentence() {
        return assumptionOf("bmi");
    }

    private String assumptionOf(String key) {
        return new ProfileFieldCatalog().fields().stream()
                .filter(spec -> spec.key().equals(key))
                .map(ProfileFieldCatalog.FieldSpec::assumption)
                .findFirst()
                .orElseThrow();
    }

    private HealthProfile profileWithoutBmi() {
        HealthProfile profile = new HealthProfile();
        profile.setGender("FEMALE");
        profile.setBirthDate(LocalDate.now().minusYears(36));
        profile.setHeightCm(new BigDecimal("165"));
        profile.setWeightKg(new BigDecimal("55"));
        profile.setFamilyHistory("父亲有糖尿病");
        return profile;
    }

    private HealthProfile completeProfile() {
        HealthProfile profile = new HealthProfile();
        profile.setGender("FEMALE");
        profile.setBirthDate(LocalDate.now().minusYears(36));
        profile.setHeightCm(new BigDecimal("165"));
        profile.setWeightKg(new BigDecimal("55"));
        profile.setBmi(new BigDecimal("20.2"));
        profile.setBloodType("A");
        profile.setAllergyHistory("青霉素");
        profile.setFamilyHistory("父亲有糖尿病");
        profile.setLifestyleHabits("每周跑步三次");
        return profile;
    }

    /**
     * A profile with a decisive age and BMI and no family-history noise.
     *
     * <p>Cardiovascular cases fix these two because they carry points of their own (age 15/5,
     * BMI 20/0) — leaving them nil would make the expected totals depend on the age fallback.</p>
     *
     * @param ageYears completed years of age
     * @param bmi      BMI as a decimal string
     * @return the profile
     */
    private HealthProfile profileWithAgeAndBmi(int ageYears, String bmi) {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(ageYears));
        profile.setBmi(new BigDecimal(bmi));
        profile.setFamilyHistory("无");
        return profile;
    }

    @Test
    @DisplayName("覆盖面板的 riskNote 与引擎读的阈值同源（同一份声明）")
    void coverageRiskNoteComesFromTheSameRegistryTheEngineReads() {
        stubNoMetrics();

        MetricCoverageVO sugar = service.getMetricCoverage(1L).stream()
                .filter(row -> row.key().equals("bloodGlucose"))
                .findFirst().orElseThrow();

        String threshold = riskThresholds.ruleFor("bloodGlucose", "糖尿病").orElseThrow()
                .threshold().toPlainString();
        assertNotNull(sugar.riskNote(), "参与评估的指标必须带 riskNote");
        assertTrue(sugar.riskNote().contains(threshold),
                "面板文案必须由注册表规则渲染，实际为: " + sugar.riskNote());
    }

    @Test
    @DisplayName("历史列表：按当前档案重算 BMI 假设，不读存储快照")
    void listRecomputesAssumptionsFromCurrentProfile() {
        stubAssessmentRows(assessmentRow(7L, 1L));
        stubProfile(profileWithoutBmi());

        List<RiskAssessmentVO> vos = service.listByUserId(1L);

        assertEquals(1, vos.size());
        assertEquals(List.of(missingBmiSentence()), vos.get(0).assumptions(),
                "the read path must re-derive the sentence from the current profile");
        assertTrue(vos.get(0).profileCompleteness() < 100);
    }

    @Test
    @DisplayName("历史列表：当前档案无任何代入时披露为空")
    void listDisclosesNothingForCompleteProfile() {
        stubAssessmentRows(assessmentRow(7L, 1L));
        stubProfile(completeProfile());

        List<RiskAssessmentVO> vos = service.listByUserId(1L);

        assertNotNull(vos.get(0).assumptions());
        assertTrue(vos.get(0).assumptions().isEmpty(),
                "a complete current profile needs no disclosure: " + vos.get(0).assumptions());
        assertEquals(100, vos.get(0).profileCompleteness());
    }

    @Test
    @DisplayName("历史列表：当前档案已删除时下发通用句，与创建路径一致")
    void listWithDeletedProfileDisclosesGenericSentence() {
        stubAssessmentRows(assessmentRow(7L, 1L));
        stubMissingProfile();

        List<RiskAssessmentVO> vos = service.listByUserId(1L);

        assertEquals(List.of(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION), vos.get(0).assumptions(),
                "the read path and the create path must agree for a deleted profile");
        assertEquals(0, vos.get(0).profileCompleteness());
    }

    @Test
    @DisplayName("详情：本人记录按当前档案重算假设")
    void detailRecomputesAssumptionsForOwner() {
        when(riskAssessmentMapper.selectById(7L)).thenReturn(assessmentRow(7L, 1L));
        stubProfile(profileWithoutBmi());

        RiskAssessmentVO vo = service.getById(7L, 1L);

        assertNotNull(vo);
        assertEquals(List.of(missingBmiSentence()), vo.assumptions());
    }

    @Test
    @DisplayName("详情：他人记录仍然返回 null（归属校验不回退）")
    void detailKeepsOwnershipCheck() {
        when(riskAssessmentMapper.selectById(7L)).thenReturn(assessmentRow(7L, 2L));

        assertNull(service.getById(7L, 1L),
                "another user's assessment must not be readable");
    }

    private RiskAssessment assessmentRow(Long id, Long userId) {
        RiskAssessment row = new RiskAssessment();
        row.setId(id);
        row.setUserId(userId);
        row.setDiseaseName("糖尿病");
        row.setRiskLevel("LOW");
        row.setRiskScore(45);
        row.setSuggestion("当前风险较低，保持健康生活习惯即可。");
        row.setCreateTime(LocalDateTime.now());
        return row;
    }

    private void stubMissingProfile() {
        when(healthProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
    }

    private void stubNoMetrics() {
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.<HealthMetric>of());
    }

    /**
     * Stub recent metrics with canonical keys, newest first (the mapper's own ordering).
     *
     * <p>Values are converted via {@code String.valueOf} rather than a cast to {@code Integer}:
     * an integer-cast overload silently truncates {@code 0.90} to {@code 0}, which would let a
     * test built on a distinguishing decimal pass against an implementation that never sees it.
     * One conversion for both shapes — a second overload would keep the truncating call sites
     * alive.</p>
     *
     * @param keysAndValues alternating metric key / value pairs (integers or decimals)
     */
    private void stubMetrics(Object... keysAndValues) {
        List<HealthMetric> rows = new java.util.ArrayList<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            HealthMetric m = new HealthMetric();
            m.setMetricKey((String) keysAndValues[i]);
            m.setValue(new BigDecimal(String.valueOf(keysAndValues[i + 1])));
            m.setRecordDate(LocalDate.now());
            rows.add(m);
        }
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(rows);
    }

    private void stubProfile(HealthProfile profile) {
        when(healthProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
    }

    private void stubAssessmentRows(RiskAssessment... rows) {
        when(riskAssessmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rows));
    }

    // ------------------------------------------------------------------
    // Canonical-key metric reads (regression guard)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("规范 key 的血压/血糖被计入评分（中文 displayName 匹配会全部落空）")
    void canonicalMetricKeysContributeToScores() {
        // The engine used to match on Chinese display names ("收缩压"), but metric_key is
        // stored canonical (systolicBP) since the v1.1 SSOT work, so every lookup returned
        // null and each score silently lost its metric contribution — a user with a
        // hypertensive reading was told they were low risk. These exact numbers are the
        // hand-computed expectation for the values below with a 40-year-old, BMI 22,
        // no family history profile.
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(40));
        profile.setBmi(BigDecimal.valueOf(22));
        profile.setFamilyHistory("无");
        stubProfile(profile);
        stubMetrics("systolicBP", 168, "diastolicBP", 80, "bloodGlucose", 14);

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        int diabetes = scoreOf(vos, "糖尿病");
        int hypertension = scoreOf(vos, "高血压");

        // bloodGlucose 14 > 6.1 contributes 30 on top of the 10 baseline.
        assertEquals(50, diabetes, "bloodGlucose must reach the diabetes band");
        // systolicBP 168 > 130 contributes 35 on top of the 5 baseline; diastolic 80 is under 85.
        assertEquals(40, hypertension, "systolicBP must reach the hypertension band");
        assertEquals("MEDIUM", vos.stream()
                .filter(v -> v.diseaseName().equals("高血压")).findFirst().orElseThrow().riskLevel(),
                "a 168 mmHg reading must not read as LOW risk");
    }

    @Test
    @DisplayName("非规范别名（如 glucose）仍被计入评分")
    void legacyAliasKeysStillContribute() {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(40));
        profile.setBmi(BigDecimal.valueOf(22));
        profile.setFamilyHistory("无");
        stubProfile(profile);
        stubMetrics("glucose", 14);

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        assertEquals(50, scoreOf(vos, "糖尿病"),
                "rows written before canonicalization must keep contributing");
    }

    @Test
    @DisplayName("年龄按周年计，生日未到不多算一岁")
    void ageUsesCompletedYears() {
        // A birthday that has not yet arrived this year. Built relative to today so the
        // precondition holds on any run date: take 46 years ago, then move to Dec 31 of that
        // year. From any date before Dec 31, year-subtraction reports 46 while completed
        // years is still 45 — which one crosses the >45 baseline, and only the correct
        // algorithm keeps the score at the 10-branch.
        LocalDate fortySixYearsAgo = LocalDate.now().minusYears(46);
        LocalDate birthDate = LocalDate.of(fortySixYearsAgo.getYear(), 12, 31);
        if (!birthDate.isBefore(LocalDate.now())) {
            // Running on Dec 31: pick the same calendar day one year later instead, so the
            // birthday is still in the future and the year gap is unchanged.
            birthDate = birthDate.plusYears(1);
        }
        assertEquals(45, java.time.Period.between(birthDate, LocalDate.now()).getYears(),
                "precondition: completed years must be 45 while the year gap is 46");
        assertEquals(46, LocalDate.now().getYear() - birthDate.getYear(),
                "precondition: year subtraction must be one higher than completed years");

        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(birthDate);
        profile.setBmi(BigDecimal.valueOf(22));
        profile.setFamilyHistory("无");
        stubProfile(profile);
        stubNoMetrics();

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        // age > 45 ? 20 : 10 — at 45 completed years the baseline is 10, total 10 + 10 = 20.
        // Year subtraction would report 46 and take the 20 branch, giving 30.
        assertEquals(20, scoreOf(vos, "糖尿病"),
                "a birthday that has not arrived yet must not trigger the >45 baseline");
    }

    // ------------------------------------------------------------------
    // Score attribution (contributors)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("归因逐项相加等于分数（三种病都必须对得上）")
    void contributorsSumToScore() {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(60));
        profile.setBmi(BigDecimal.valueOf(30));
        profile.setFamilyHistory("父亲有糖尿病");
        stubProfile(profile);
        stubMetrics("systolicBP", 168, "diastolicBP", 95, "bloodGlucose", 14);

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        for (RiskAssessmentVO vo : vos) {
            int sum = vo.contributors().stream().mapToInt(RiskContributorVO::points).sum();
            assertEquals(vo.riskScore(), sum,
                    vo.diseaseName() + " 的归因合计必须等于分数，否则用户看到的明细解释不了总分");
            assertFalse(vo.contributors().isEmpty(),
                    vo.diseaseName() + " 必须带归因，不能只给一个数字");
        }
    }

    @Test
    @DisplayName("无血压数据标 MISSING，绝不与「血压正常」混为一谈")
    void missingMetricIsMarkedMissingNotNormal() {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(40));
        profile.setBmi(BigDecimal.valueOf(22));
        profile.setFamilyHistory("无");
        stubProfile(profile);
        // 完全没有血压记录
        stubNoMetrics();

        List<RiskAssessmentVO> bp = service.createAssessment(1L).stream()
                .filter(v -> v.diseaseName().equals("高血压")).toList();
        assertEquals(1, bp.size());

        RiskContributorVO systolic = bp.get(0).contributors().stream()
                .filter(c -> c.key().equals("systolicBP")).findFirst().orElseThrow();
        RiskContributorVO diastolic = bp.get(0).contributors().stream()
                .filter(c -> c.key().equals("diastolicBP")).findFirst().orElseThrow();

        assertEquals(RiskContributorVO.STATE_MISSING, systolic.state(),
                "没有血压数据时必须标 MISSING —— 否则「没测」会被读成「正常」");
        assertEquals(0, systolic.points(), "缺失项不得加分");
        assertNull(systolic.value(), "缺失项不得伪造取值");
        assertEquals(RiskContributorVO.STATE_MISSING, diastolic.state());
        assertTrue(systolic.explanation().contains("缺少"),
                "文案必须明确说明缺少数据，实际为: " + systolic.explanation());
    }

    @Test
    @DisplayName("有血压且未超阈值标 NORMAL，与 MISSING 状态不同")
    void normalMetricIsMarkedNormal() {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(40));
        profile.setBmi(BigDecimal.valueOf(22));
        profile.setFamilyHistory("无");
        stubProfile(profile);
        stubMetrics("systolicBP", 120, "diastolicBP", 78);

        RiskContributorVO systolic = service.createAssessment(1L).stream()
                .filter(v -> v.diseaseName().equals("高血压")).findFirst().orElseThrow()
                .contributors().stream()
                .filter(c -> c.key().equals("systolicBP")).findFirst().orElseThrow();

        assertEquals(RiskContributorVO.STATE_NORMAL, systolic.state(),
                "有数据且未超标必须标 NORMAL，而不是 MISSING");
        assertNotNull(systolic.value(), "NORMAL 必须带上实际取值，用户要看到自己的读数");
        assertTrue(systolic.value().contains("120"), "取值应可见，实际为: " + systolic.value());
    }

    @Test
    @DisplayName("默认值代入标 ASSUMED，可被用户识别为「可补填」")
    void assumedValueIsMarkedAssumed() {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(40));
        profile.setBmi(null); // BMI 缺失 → 按 22.0 估算
        profile.setFamilyHistory("无");
        stubProfile(profile);
        stubNoMetrics();

        RiskContributorVO bmi = service.createAssessment(1L).stream()
                .filter(v -> v.diseaseName().equals("糖尿病")).findFirst().orElseThrow()
                .contributors().stream()
                .filter(c -> c.key().equals("bmi")).findFirst().orElseThrow();

        assertEquals(RiskContributorVO.STATE_ASSUMED, bmi.state(),
                "估算值必须标 ASSUMED —— 既不是用户真实数据，也不是「完全没计入」");
        assertTrue(bmi.explanation().contains("估算"),
                "文案必须说明这是估算，实际为: " + bmi.explanation());
    }

    @Test
    @DisplayName("超阈值的项标 CONTRIBUTED 并说明超出多少")
    void crossingThresholdIsExplained() {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(40));
        profile.setBmi(BigDecimal.valueOf(22));
        profile.setFamilyHistory("无");
        stubProfile(profile);
        stubMetrics("systolicBP", 168);

        RiskContributorVO systolic = service.createAssessment(1L).stream()
                .filter(v -> v.diseaseName().equals("高血压")).findFirst().orElseThrow()
                .contributors().stream()
                .filter(c -> c.key().equals("systolicBP")).findFirst().orElseThrow();

        assertEquals(RiskContributorVO.STATE_CONTRIBUTED, systolic.state());
        assertEquals(35, systolic.points());
        assertTrue(systolic.explanation().contains("168"), "必须点名实际读数");
        assertTrue(systolic.explanation().contains("130"), "必须点名参考值");
    }

    @Test
    @DisplayName("缺血糖时归因标 MISSING 且不加分，绝不伪造 0 值当正常")
    void missingBloodGlucoseIsMissingNotZero() {
        HealthProfile profile = new HealthProfile();
        profile.setId(9L);
        profile.setBirthDate(LocalDate.now().minusYears(40));
        profile.setBmi(BigDecimal.valueOf(22));
        profile.setFamilyHistory("无");
        stubProfile(profile);
        // 只有血压，没有血糖
        stubMetrics("systolicBP", 120);

        RiskContributorVO sugar = service.createAssessment(1L).stream()
                .filter(v -> v.diseaseName().equals("糖尿病")).findFirst().orElseThrow()
                .contributors().stream()
                .filter(c -> c.key().equals("bloodGlucose")).findFirst().orElseThrow();

        assertEquals(RiskContributorVO.STATE_MISSING, sugar.state(),
                "缺血糖必须标 MISSING —— 伪造一个 0 值会让「没测」变成「正常」");
        assertNull(sugar.value(), "缺失项的值必须为 null，不能填 0");
        assertEquals(0, sugar.points(), "缺失项不得加分");
    }

    // ------------------------------------------------------------------
    // 方向感知的措辞与缺失别名（D-10 / D-11）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("HDL 偏低时措辞方向正确：说「低于」，绝不说「超过」")
    void lowerBoundMetricIsWordedAsBelowNotAbove() {
        // 0.90 而不是「全缺」：全缺时正确实现与错误实现都给 MISSING，断言没有牙齿。
        stubProfile(profileWithAgeAndBmi(60, "26"));
        stubMetrics("hdlCholesterol", 0.90);

        RiskContributorVO hdl = contributorsOf(service.createAssessment(1L), "心血管疾病").stream()
                .filter(c -> c.key().equals("hdlCholesterol")).findFirst().orElseThrow();

        assertEquals(RiskContributorVO.STATE_CONTRIBUTED, hdl.state());
        assertEquals(15, hdl.points(), "0.90 < 1.00 应计 15 分");
        assertTrue(hdl.explanation().contains("低于"),
                "下限型指标必须说「低于」，实际为: " + hdl.explanation());
        assertFalse(hdl.explanation().contains("超过"),
                "绝不能说「超过」—— 0.90 的人实际低于阈值，那句话把事实说反了: " + hdl.explanation());
        assertTrue(hdl.explanation().contains("评估阈值"),
                "阈值是风险阈值而非参考值，措辞必须如此: " + hdl.explanation());
    }

    @Test
    @DisplayName("上限型指标仍说「超过」，且同样以「评估阈值」称阈值")
    void upperBoundMetricIsWordedAsAboveWithAssessmentThreshold() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("systolicBP", 168);

        RiskContributorVO systolic = contributorsOf(service.createAssessment(1L), "高血压").stream()
                .filter(c -> c.key().equals("systolicBP")).findFirst().orElseThrow();

        assertTrue(systolic.explanation().contains("超过"), "实际为: " + systolic.explanation());
        assertTrue(systolic.explanation().contains("评估阈值"),
                "同屏的参考范围列显示 90–139，这一句若把 130 也叫「参考值」就自相矛盾: "
                        + systolic.explanation());
        assertFalse(systolic.explanation().contains("参考值"),
                "风险阈值不得被称作参考值: " + systolic.explanation());
    }

    @Test
    @DisplayName("库中 metric_key='systolicBloodPressure' 的行开始参与评估")
    void systolicBloodPressureAliasRowStartsScoring() {
        // 用 135 而不是 118：118 在「有别名」与「没别名」两种实现下都低于 130，
        // 断言同样没牙齿。135 落在风险阈值 130 与参考上限 139 之间，只有正确实现计分。
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("systolicBloodPressure", 135);

        List<RiskContributorVO> bp = contributorsOf(service.createAssessment(1L), "高血压");
        RiskContributorVO systolic = bp.stream()
                .filter(c -> c.key().equals("systolicBP")).findFirst().orElseThrow();

        assertEquals(RiskContributorVO.STATE_CONTRIBUTED, systolic.state(),
                "不补别名时 getLatestMetric 返回 null，这一行会永远是 MISSING");
        assertEquals(35, systolic.points(), "135 > 130 应在高血压计 35 分");
        assertNotNull(systolic.value(), "别名行必须带出实际取值");
        assertTrue(systolic.value().contains("135"), "实际为: " + systolic.value());
    }

    // ------------------------------------------------------------------
    // 同源格式化（A(i) / §5.3.1）：同一个数字在两个表面必须是同一个字符串
    // ------------------------------------------------------------------

    /**
     * A(i)：面板与归因对同一个 bmi 渲染出同一个字符串。
     *
     * <p><strong>适用边界（D-06 的必然结果）：</strong>这条断言只对「本次改动之后创建的
     * 评估」成立。归因快照在创建时冻结、读时不重算，所以改动前入库的历史记录里 {@code value}
     * 仍是 {@code 22.86}，而覆盖面板是实时按 precision 1 渲成 {@code 22.9} —— 拿历史记录跑
     * 这条会失败，而实现是完全正确的。取数口径因此固定为「先跑一次 createAssessment，
     * 再对该次返回值断言」，绝不读取库里已有的批次。</p>
     *
     * <p>{@code bmi} 是唯一两侧都不带单位的引擎输入，因此这里用整串相等。血压、血糖、
     * 血脂四项的归因值带 {@code " mmHg"} / {@code " mmol/L"}，与面板值只可能构成数值前缀
     * 关系（下面两条断言即此）—— 为「统一写法」把它们也改成整串相等，会让断言在任何正确
     * 实现下恒红。</p>
     */
    @Test
    @DisplayName("A(i)：bmi 在面板与归因两侧是同一个字符串（22.9，不是 22.86）")
    void bmiRendersIdenticallyOnBothSurfacesForThisRun() {
        // 夹具刻意让两个数据源取同一个值：这条断言考察的是**格式化**是否同源，
        // 而不是取值源选得对不对 —— 取值源由 16-05 Task 2 的「档案无 bmi 但指标表
        // 有一行」夹具单独钉住（那里断言 state 为 MISSING 且 value 为 null）。
        // 只给材料表会漏掉档案、只给档案会让面板在该任务之前是 MISSING，两种写法都
        // 会让这条断言在它本该能执行的时候执行不了。
        HealthProfile profile = profileWithAgeAndBmi(40, "22.86");
        stubProfile(profile);
        stubMetrics("systolicBP", 120, "bmi", 22.86);

        List<RiskAssessmentVO> created = service.createAssessment(1L);
        RiskContributorVO cardioBmi = contributorOf(created, "心血管疾病", "bmi");
        MetricCoverageVO coverageBmi = coverageRowByKey("bmi");

        assertEquals("22.9", coverageBmi.value(),
                "面板按注册表 precision 1 渲染，实际为: " + coverageBmi.value());
        assertEquals("22.9", cardioBmi.value(),
                "归因必须走同一段 formatter，实际为: " + cardioBmi.value());
        assertEquals(coverageBmi.value(), cardioBmi.value(),
                "两侧必须字符串相等 —— formatter 被摘掉时这里会先红，上一行给出人可读的原因");
        assertNotEquals("22.86", cardioBmi.value(),
                "退回 stripTrailingZeros 会把未舍入的 22.86 直接呈现给用户");
        assertNotEquals("22.86", coverageBmi.value());
    }

    /**
     * 血糖 5.55：面板 {@code 5.6}、归因 {@code 5.6 mmol/L}。
     *
     * <p>5.55 是能同时区分两种实现的最小夹具 —— 整数输入在两种实现下都给同一个串。两侧
     * 整体字符串不相等是**既有不对称**（面板没有单位列），故归因侧用数值前缀比较，不得为此
     * 给面板加单位列（那会撑破 Contract 2 的五列栅格）。</p>
     */
    @Test
    @DisplayName("血糖 5.55：面板 5.6、归因以 5.6 起头，两侧都不含 5.55")
    void bloodGlucosePrecisionIsSharedAcrossSurfaces() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("bloodGlucose", 5.55);

        List<RiskAssessmentVO> created = service.createAssessment(1L);
        RiskContributorVO cardioSugar = contributorOf(created, "心血管疾病", "bloodGlucose");
        MetricCoverageVO coverageSugar = coverageRowByKey("bloodGlucose");

        assertEquals("5.6", coverageSugar.value(), "面板按 precision 1 渲染");
        assertTrue(cardioSugar.value().startsWith("5.6"),
                "归因的数值部分必须由同一段 formatter 产出，实际为: " + cardioSugar.value());
        assertFalse(cardioSugar.value().contains("5.55"),
                "未舍入形态出现即说明 formatter 没被调用: " + cardioSugar.value());
        assertFalse(coverageSugar.value().contains("5.55"));
    }

    /**
     * 血脂四项的 A(i) 前缀探针。
     *
     * <p>这是「引擎全部替换点是否覆盖了 D-02 新增的血脂四项」的唯一探针 —— 只断言 bmi 与
     * 血糖会让漏改血脂站点仍然全绿。探针的牙齿不因改前缀而变钝：漏改一处会留下
     * {@code "5.8 mmol/L"}，而正确实现给 {@code "5.80 mmol/L"}，{@code startsWith("5.80")}
     * 当场为假。</p>
     */
    @Test
    @DisplayName("血脂四项：归因值以同名覆盖行值为前缀（数值部分同一段 formatter）")
    void lipidsShareTheNumericPrefixWithTheirCoverageRows() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics(
                "totalCholesterol", 5.80,
                "triglycerides", 2.10,
                "hdlCholesterol", 0.90,
                "ldlCholesterol", 4.00);

        List<RiskAssessmentVO> created = service.createAssessment(1L);

        List<String> lipids = List.of("totalCholesterol", "triglycerides",
                "hdlCholesterol", "ldlCholesterol");
        for (String key : lipids) {
            RiskContributorVO contributor = contributorOf(created, "心血管疾病", key);
            MetricCoverageVO coverageRow = coverageRowByKey(key);
            assertNotNull(contributor.value(), key + " 有数据必须带出取值");
            // 面板无单位列，归因带 " mmol/L" —— 只可能构成前缀关系，整串相等永不成立。
            assertTrue(contributor.value().startsWith(coverageRow.value()),
                    key + " 的两侧数值必须来自同一段 formatter: 面板 "
                            + coverageRow.value() + " / 归因 " + contributor.value());
            assertFalse(contributor.value().equals(coverageRow.value()),
                    key + " 归因带单位后缀，整串相等说明有人为了「统一写法」删掉了单位");
        }

        // 未舍入形态的否定守卫：precision 2 下 5.8 必须渲染成 5.80。
        RiskContributorVO cholesterol = contributorOf(created, "心血管疾病", "totalCholesterol");
        assertNotEquals("5.8 mmol/L", cholesterol.value(),
                "stripTrailingZeros 会把 5.80 压成 5.8，与面板的 5.80 分叉");
    }

    /**
     * 同一规则在两个表面渲染出同一个阈值串（UI-SPEC §5.2 的 {@code {阈值} = 与 threshold 字段同值}）。
     *
     * <p>两个字段本就不是同类值：{@code riskNote} 是整句
     * {@code {疾病}评估：{方向动词} {阈值} {单位} 计入 {分数} 分}，而 {@code threshold} 只是
     * {@code {阈值} {单位}} 那一段，整串相等在任何正确实现下都不成立。钉住「同值」的正确形态
     * 是把 {@code threshold} 当锚点、在 {@code riskNote} 里找出它 —— 包含关系对字节敏感，
     * 阈值与单位之间那个空格由唯一模板提供，任一表面缺失即红。</p>
     *
     * <p>只对血脂四项做，不纳入 bmi：bmi 归因的 {@code threshold} 是不带单位的
     * {@code "24.0"}，而 {@code riskNote} 里带 {@code kg/m²}，两者连包含关系都不成立
     * （既有的单位不对称，本阶段不改）。</p>
     */
    @Test
    @DisplayName("同值：血脂四项的 threshold 字段是同一行 riskNote 的子串（5.20 / 1.70 / 1.00 / 3.40）")
    void lipidThresholdFieldAppearsVerbatimInsideItsRiskNote() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics(
                "totalCholesterol", 5.80,
                "triglycerides", 2.10,
                "hdlCholesterol", 0.90,
                "ldlCholesterol", 4.00);

        List<RiskAssessmentVO> created = service.createAssessment(1L);

        for (String key : List.of("totalCholesterol", "triglycerides",
                "hdlCholesterol", "ldlCholesterol")) {
            String thresholdField = contributorOf(created, "心血管疾病", key).threshold();
            String riskNote = coverageRowByKey(key).riskNote();
            assertNotNull(thresholdField, key + " 必须带阈值串");
            assertNotNull(riskNote, key + " 参与评估必须带 riskNote");
            assertTrue(riskNote.contains(thresholdField),
                    key + " 的阈值在两处漂移了：riskNote「" + riskNote
                            + "」不含 threshold「" + thresholdField + "」");
        }
    }


    /**
     * 阈值等值点在两个表面同时成立：bmi 24.00 与收缩压 130 面板 NORMAL **且**引擎 0 分。
     *
     * <p>必须单列，且两侧必须在同一用例内一起断言：只断言面板会放行「面板与引擎一起把
     * 比较符写成 {@code >=}」，只断言引擎会放行「面板单独写成 {@code >=}」。D-03 要求同源，
     * 同源的正确形态就是两个表面在同一个输入上给出同一个结论。</p>
     */
    @Test
    @DisplayName("阈值等值点两侧一致：bmi 24.00 与收缩压 130 面板 NORMAL 且引擎 0 分")
    void thresholdEqualityPointAgreesOnBothSurfaces() {
        stubProfile(profileWithAgeAndBmi(40, "24.00"));
        stubMetrics("systolicBP", 130);

        List<RiskAssessmentVO> created = service.createAssessment(1L);
        RiskContributorVO cardioBmi = contributorOf(created, "心血管疾病", "bmi");
        RiskContributorVO cardioSystolic = contributorOf(created, "心血管疾病", "systolicBP");

        assertEquals(0, cardioBmi.points(), "24.00 恰好等于阈值，引擎不得计分");
        assertEquals(0, cardioSystolic.points(), "130 恰好等于阈值，引擎不得计分");

        assertEquals(MetricCoverageVO.STATE_NORMAL, coverageRowByKey("bmi").state(),
                "面板必须与引擎同判 NORMAL —— classify 写成 >= 会让这一行变成「偏高」");
        assertEquals(MetricCoverageVO.STATE_NORMAL, coverageRowByKey("systolicBP").state());
    }

    private int scoreOf(List<RiskAssessmentVO> vos, String disease) {
        return vos.stream().filter(v -> v.diseaseName().equals(disease))
                .findFirst().orElseThrow().riskScore();
    }

    /** One coverage row by canonical key, read from the same service the engine runs in. */
    private MetricCoverageVO coverageRowByKey(String key) {        return service.getMetricCoverage(1L).stream()
                .filter(row -> row.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("覆盖面板未渲染: " + key));
    }

    private RiskContributorVO contributorOf(List<RiskAssessmentVO> vos, String disease, String key) {
        return contributorsOf(vos, disease).stream()
                .filter(c -> c.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("未找到归因: " + disease + "/" + key));
    }

    // ------------------------------------------------------------------
    // 封顶折算 / 阈值严于参考上限 / 阈值本身不计分（T-8 / T-1 / T-2 / T-28）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("封顶折算：手算 150 → riskScore 100、Σpoints 100、说明句含按比例折算")
    void cappingScalesTheBreakdownToTheCappedScore() {
        // 夹具必须是「全部异常」：合计 150 = 年龄 15 + BMI 20 + SBP 30 + DBP 15 + 血糖 10
        // + TC 20 + TG 10 + HDL 15 + LDL 15。改成「全部正常」时合计 5、scaleTo 不执行，
        // 断言恒绿且毫无意义。
        stubProfile(profileWithAgeAndBmi(60, "26"));
        stubMetrics(
                "systolicBP", 168,
                "diastolicBP", 95,
                "bloodGlucose", 8,
                "totalCholesterol", 5.8,
                "triglycerides", 2.1,
                "hdlCholesterol", 0.90,
                "ldlCholesterol", 4.0);

        List<RiskAssessmentVO> vos = service.createAssessment(1L);
        RiskAssessmentVO cardio = vos.stream()
                .filter(v -> v.diseaseName().equals("心血管疾病")).findFirst().orElseThrow();

        int rawSum = cardio.contributors().stream().mapToInt(RiskContributorVO::points).sum();
        assertEquals(100, cardio.riskScore(), "分数封顶 100");
        // 核心断言：若 scaleTo 被跳过，总分仍是 100（Math.min）而归因合计是 150 ——
        // 用户把 9 行加起来就会发现两个数字对不上。
        assertEquals(100, rawSum, "归因合计必须跟着封顶一起折算，否则明细解释不了总分");
        assertEquals(cardio.riskScore(), rawSum, "两个数字必须相等（AC-5 的观测点）");
        assertTrue(cardio.contributors().stream()
                        .anyMatch(c -> c.explanation().contains("按比例折算")),
                "至少一条说明必须告诉用户封顶/折算发生了，实际为: "
                        + cardio.contributors().stream().map(RiskContributorVO::explanation).toList());
    }

    @Test
    @DisplayName("收缩压 135：落在 130 与参考上限 139 之间，高血压 35、心血管 30")
    void systolicBetweenRiskThresholdAndReferenceMaxIsScoredInBothDiseases() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("systolicBP", 135);

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        RiskContributorVO bpSystolic = contributorOf(vos, "高血压", "systolicBP");
        RiskContributorVO cardioSystolic = contributorOf(vos, "心血管疾病", "systolicBP");

        // 误读参考上限（139）时两者都变成 0 分、state NORMAL；这条用例同时钉住
        // 「阈值搬家没改成参考范围」与「(指标, 疾病) 二元键没退化成单指标阈值」。
        assertEquals(35, bpSystolic.points(), "135 > 130 在高血压计 35 分");
        assertEquals(30, cardioSystolic.points(), "同一读数在心血管计 30 分 —— 分值因疾病而异");
        assertEquals(RiskContributorVO.STATE_CONTRIBUTED, bpSystolic.state());
        assertEquals(RiskContributorVO.STATE_CONTRIBUTED, cardioSystolic.state());
    }

    @Test
    @DisplayName("舒张压 87：落在 85 与参考上限 89 之间，高血压 30、心血管 15")
    void diastolicBetweenRiskThresholdAndReferenceMaxIsScoredInBothDiseases() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("diastolicBP", 87);

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        RiskContributorVO bpDiastolic = contributorOf(vos, "高血压", "diastolicBP");
        RiskContributorVO cardioDiastolic = contributorOf(vos, "心血管疾病", "diastolicBP");

        assertEquals(30, bpDiastolic.points(), "87 > 85 在高血压计 30 分");
        assertEquals(15, cardioDiastolic.points(), "同一读数在心血管计 15 分");
        assertEquals(RiskContributorVO.STATE_CONTRIBUTED, bpDiastolic.state());
        assertEquals(RiskContributorVO.STATE_CONTRIBUTED, cardioDiastolic.state());
    }

    @Test
    @DisplayName("阈值本身不计分：bmi 恰好 24.00 → 心血管 0 分 / NORMAL，糖尿病仍走 10 分基线")
    void bmiExactlyAtThresholdScoresNothing() {
        // 开区间夹具（135 / 87）只能证明阈值取值正确，证明不了比较符是严格 > 还是 ≥。
        // 若 compareTo(...) > 0 被写成 >= 0，上面全部夹具仍全绿，而阈值上的人凭空多出 20 分。
        stubProfile(profileWithAgeAndBmi(40, "24.00"));
        stubNoMetrics();

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        RiskContributorVO cardioBmi = contributorOf(vos, "心血管疾病", "bmi");
        assertEquals(0, cardioBmi.points(), "24.00 恰好等于阈值，不得计分");
        assertEquals(RiskContributorVO.STATE_NORMAL, cardioBmi.state());

        RiskContributorVO diabetesBmi = contributorOf(vos, "糖尿病", "bmi");
        assertEquals(10, diabetesBmi.points(),
                "糖尿病未命中时仍给 10 分无条件基线（不是阈值奖励）");
    }

    @Test
    @DisplayName("阈值本身不计分：收缩压恰好 130 → 高血压 0 分 / NORMAL")
    void systolicExactlyAtThresholdScoresNothing() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("systolicBP", 130);

        RiskContributorVO systolic = contributorOf(service.createAssessment(1L), "高血压",
                "systolicBP");

        assertEquals(0, systolic.points(), "130 恰好等于阈值，不得计分（比较符是严格 >）");
        assertEquals(RiskContributorVO.STATE_NORMAL, systolic.state());
    }

    @Test
    @DisplayName("阈值本身不计分：舒张压恰好 85 → 高血压 0 分 / NORMAL")
    void diastolicExactlyAtThresholdScoresNothing() {
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("diastolicBP", 85);

        RiskContributorVO diastolic = contributorOf(service.createAssessment(1L), "高血压",
                "diastolicBP");

        assertEquals(0, diastolic.points(), "85 恰好等于阈值，不得计分");
        assertEquals(RiskContributorVO.STATE_NORMAL, diastolic.state());
    }

    @Test
    @DisplayName("阈值本身不计分：HDL 恰好 1.00 → 0 分 / NORMAL（BELOW 用的是严格 <）")
    void hdlExactlyAtThresholdScoresNothing() {
        // 这一条钉住下限型的严格性：把 < 写成 <= 会让恰好 1.00 的人被计 15 分。
        stubProfile(profileWithAgeAndBmi(40, "22"));
        stubMetrics("hdlCholesterol", 1.00);

        RiskContributorVO hdl = contributorOf(service.createAssessment(1L), "心血管疾病",
                "hdlCholesterol");

        assertEquals(0, hdl.points(), "1.00 恰好等于下限阈值，不得计分（比较符是严格 <）");
        assertEquals(RiskContributorVO.STATE_NORMAL, hdl.state());
    }

    private List<RiskContributorVO> contributorsOf(List<RiskAssessmentVO> vos, String disease) {
        return vos.stream().filter(v -> v.diseaseName().equals(disease))
                .findFirst().orElseThrow().contributors();
    }

    // ------------------------------------------------------------------
    // 心血管独立评估（D-01 / D-02 / D-08）
    // ------------------------------------------------------------------

    /** 心血管 9 条归因的固定顺序 —— 即 UI-SPEC Contract 3.1 的枚举，也是快照的数组顺序。 */
    private static final List<String> CARDIO_KEYS = List.of(
            "age", "bmi", "systolicBP", "diastolicBP", "bloodGlucose",
            "totalCholesterol", "triglycerides", "hdlCholesterol", "ldlCholesterol");

    @Test
    @DisplayName("心血管归因恰 9 条，键与顺序精确")
    void cardiovascularHasExactlyNineInputsInDeclaredOrder() {
        HealthProfile profile = profileWithAgeAndBmi(60, "26");
        stubProfile(profile);
        stubMetrics("systolicBP", 168, "diastolicBP", 95, "bloodGlucose", 8);

        List<RiskAssessmentVO> vos = service.createAssessment(1L);
        List<RiskContributorVO> cardio = contributorsOf(vos, "心血管疾病");

        assertEquals(9, cardio.size(),
                "D-08：心血管恰好 9 项输入（含血脂四项，不含 heartRate）");
        assertEquals(CARDIO_KEYS, cardio.stream().map(RiskContributorVO::key).toList(),
                "顺序错误会让前端 9 行错位；这里用逐项 equals 而不是集合比较");

        // 手算：年龄 60>55 → 15，BMI 26>24 → 20，SBP 168>130 → 30，DBP 95>85 → 15，
        // 血糖 8>6.1 → 10，血脂四项缺失 → 0。合计 90，未触及封顶。
        assertEquals(List.of(15, 20, 30, 15, 10, 0, 0, 0, 0),
                cardio.stream().map(RiskContributorVO::points).toList());
        assertEquals(90, scoreOf(vos, "心血管疾病"));
    }

    @Test
    @DisplayName("心血管归因不含来源前缀或折半措辞")
    void cardiovascularAttributionCarriesNoSourcePrefixAndNoHalving() {
        stubProfile(profileWithAgeAndBmi(60, "26"));
        stubMetrics("systolicBP", 168, "diastolicBP", 95, "bloodGlucose", 8);

        List<RiskContributorVO> cardio = contributorsOf(service.createAssessment(1L), "心血管疾病");

        // 唯一的否定守卫：旧实现把 12 条归因写成「来自糖尿病：…（折半计入 N 分）」，
        // 正则可以放行任何一种新措辞，只有这两个否定检查能证伪「halve 没删干净」。
        for (RiskContributorVO part : cardio) {
            String explanation = part.explanation();
            assertFalse(explanation.contains("来自"),
                    "心血管归因不得残留来源前缀（复合评分时代的产物）: " + explanation);
            assertFalse(explanation.contains("折半"),
                    "心血管归因不得残留折半措辞: " + explanation);
            assertFalse(explanation.matches(".*[（(]折半计入.*"),
                    "全角括号包裹的折半分值同样是旧形态: " + explanation);
            // 自足性：有值时以指标名或「年龄」开头；缺失行走共享的 missing() 形态
            // （「缺少{label}数据，此项未计入评分」），它同样点名了指标，只是以「缺少」起句 ——
            // 该形态是 Phase 15 三个疾病共用的，本计划不改动它。
            if (RiskContributorVO.STATE_MISSING.equals(part.state())) {
                assertTrue(explanation.contains(part.label()),
                        "缺失项的说明必须点名是哪一项: " + explanation);
            } else {
                assertTrue(explanation.startsWith(part.label()) || explanation.startsWith("年龄"),
                        "每条说明必须自足、以指标名或「年龄」开头，实际为: " + explanation);
            }
        }
    }

    @Test
    @DisplayName("血脂四项全缺 → 4 条 MISSING、0 分、无任何 ASSUMED")
    void lipidsAbsentAreMissingAndNeverSubstituted() {
        // 年龄与 BMI 有档案值，因此这两项不会触发 ASSUMED 兜底 —— 整份列表中任何一条
        // ASSUMED 都只能来自「血脂被代入了人群默认值」，正是 D-02 要防的半实现。
        stubProfile(profileWithAgeAndBmi(60, "26"));
        stubNoMetrics();

        List<RiskContributorVO> cardio = contributorsOf(service.createAssessment(1L), "心血管疾病");

        List<String> lipids = List.of("totalCholesterol", "triglycerides",
                "hdlCholesterol", "ldlCholesterol");
        for (String key : lipids) {
            RiskContributorVO part = cardio.stream()
                    .filter(c -> c.key().equals(key)).findFirst().orElseThrow();
            assertEquals(RiskContributorVO.STATE_MISSING, part.state(),
                    key + " 无数据时必须标 MISSING");
            assertEquals(0, part.points(), key + " 缺失项不得加分");
            assertNull(part.value(), key + " 缺失项不得伪造取值");
        }

        assertTrue(cardio.stream().noneMatch(c -> RiskContributorVO.STATE_ASSUMED.equals(c.state())),
                "血脂缺失绝不代入人群默认值；ASSUMED 只对年龄/BMI 的档案兜底成立");
    }

    @Test
    @DisplayName("存量快照读时原样返回，不按当前逻辑重算")
    void storedSnapshotIsReturnedVerbatimNotRecomputed() {
        // 一段真实形态的存量快照：单条、带「来自」与「折半」—— 那是复合评分时代的原文。
        // 若实现为了让新断言全绿而回去重算（或批量 UPDATE 历史行），这条会红。
        String legacyJson = "[{\"key\":\"age\",\"label\":\"年龄\",\"state\":\"CONTRIBUTED\","
                + "\"value\":\"52 岁\",\"threshold\":\"50 岁\",\"points\":5,"
                + "\"explanation\":\"来自高血压：年龄 52 岁，超过 50 岁，计入 15 分（折半计入 5 分）\"}]";
        RiskAssessment row = assessmentRow(7L, 1L);
        row.setContributors(legacyJson);
        stubAssessmentRows(row);
        stubProfile(profileWithAgeAndBmi(52, "22"));
        stubMetrics("systolicBP", 168, "diastolicBP", 95, "bloodGlucose", 8);

        List<RiskAssessmentVO> listed = service.listByUserId(1L);
        List<RiskContributorVO> stored = listed.get(0).contributors();

        assertEquals(1, stored.size(), "快照有几条就返回几条，不补不删");
        assertEquals("age", stored.get(0).key());
        assertEquals(5, stored.get(0).points());
        assertEquals(legacyJson.contains("折半"), stored.get(0).explanation().contains("折半"),
                "原文的措辞必须原样保留 —— 历史记录是当时的真实依据");

        // 反向：当前引擎对同一份输入会产出 9 条、且不含折半 —— 两者必须不同。
        List<RiskContributorVO> recomputed = contributorsOf(service.createAssessment(1L), "心血管疾病");
        assertEquals(9, recomputed.size());
        assertNotEquals(recomputed, stored,
                "读路径若拿当前输入重算，就会得到与旧分数对不上的明细 —— 这正是禁止的形态");
    }
}