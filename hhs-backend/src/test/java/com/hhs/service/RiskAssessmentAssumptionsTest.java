package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthProfile;
import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.mapper.RiskAssessmentMapper;
import com.hhs.service.domain.ProfileCompletenessCalculator;
import com.hhs.service.domain.ProfileFieldCatalog;
import com.hhs.service.impl.RiskAssessmentServiceImpl;
import com.hhs.vo.RiskAssessmentVO;
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
                healthMetricMapper, new ProfileCompletenessCalculator(new ProfileFieldCatalog()));
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

    private void stubProfile(HealthProfile profile) {
        when(healthProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
    }

    private void stubAssessmentRows(RiskAssessment... rows) {
        when(riskAssessmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rows));
    }
}