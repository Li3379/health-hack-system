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

    @BeforeEach
    void setUp() {
        service = new RiskAssessmentServiceImpl(riskAssessmentMapper, healthProfileMapper,
                healthMetricMapper, new ProfileCompletenessCalculator(new ProfileFieldCatalog()),
                new MetricCoverageAssembler(new HealthMetricMetaRegistry()),
                new RiskThresholdRegistry());
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
     * @param keysAndValues alternating canonical key / value pairs
     */
    private void stubMetrics(Object... keysAndValues) {
        List<HealthMetric> rows = new java.util.ArrayList<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            HealthMetric m = new HealthMetric();
            m.setMetricKey((String) keysAndValues[i]);
            m.setValue(new BigDecimal((Integer) keysAndValues[i + 1]));
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

    private int scoreOf(List<RiskAssessmentVO> vos, String disease) {
        return vos.stream().filter(v -> v.diseaseName().equals(disease))
                .findFirst().orElseThrow().riskScore();
    }
}