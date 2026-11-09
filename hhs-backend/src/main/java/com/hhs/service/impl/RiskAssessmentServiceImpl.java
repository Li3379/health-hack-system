package com.hhs.service.impl;

import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthProfile;
import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.mapper.RiskAssessmentMapper;
import com.hhs.service.RiskAssessmentService;
import com.hhs.service.domain.AssumptionCollector;
import com.hhs.service.domain.ProfileCompletenessCalculator;
import com.hhs.service.domain.ProfileFieldCatalog;
import com.hhs.vo.RiskAssessmentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    private final RiskAssessmentMapper riskAssessmentMapper;
    private final HealthProfileMapper healthProfileMapper;
    private final HealthMetricMapper healthMetricMapper;
    private final ProfileCompletenessCalculator profileCompletenessCalculator;

    /**
     * Construct the service with its mappers and the completeness calculator.
     *
     * @param riskAssessmentMapper          risk-assessment persistence
     * @param healthProfileMapper           health-profile lookup
     * @param healthMetricMapper            recent-metric lookup
     * @param profileCompletenessCalculator authoritative completeness source
     */
    public RiskAssessmentServiceImpl(RiskAssessmentMapper riskAssessmentMapper,
                                     HealthProfileMapper healthProfileMapper,
                                     HealthMetricMapper healthMetricMapper,
                                     ProfileCompletenessCalculator profileCompletenessCalculator) {
        this.riskAssessmentMapper = riskAssessmentMapper;
        this.healthProfileMapper = healthProfileMapper;
        this.healthMetricMapper = healthMetricMapper;
        this.profileCompletenessCalculator = profileCompletenessCalculator;
    }

    /**
     * Execute risk assessment and persist results, returning multiple assessment records.
     * This method will delete existing assessments for the user before creating new ones (update mode).
     *
     * @param userId User ID
     * @return List of risk assessment results (diabetes, hypertension, cardiovascular)
     * @throws BusinessException if user data is insufficient for assessment
     */
    @Override
    @Transactional
    public List<RiskAssessmentVO> createAssessment(Long userId) {
        // Delete existing assessments for this user (update mode)
        riskAssessmentMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RiskAssessment>()
                        .eq(RiskAssessment::getUserId, userId));

        HealthProfile profile = getProfileByUserId(userId);
        AssumptionCollector collector = new AssumptionCollector();
        List<RiskAssessment> items = RiskRuleEngine.evaluate(userId, profile, getRecentMetrics(userId),
                collector);
        for (RiskAssessment r : items) {
            riskAssessmentMapper.insert(r);
        }
        int completeness = profileCompletenessCalculator.calculate(profile).score();
        List<String> assumptions = collector.toList();
        return items.stream().map(r -> toVO(r, completeness, assumptions)).toList();
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
        return toVO(r);
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
        return assessments.stream().map(this::toVO).toList();
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

    private RiskAssessmentVO toVO(RiskAssessment r) {
        return toVO(r, null, List.of());
    }

    private RiskAssessmentVO toVO(RiskAssessment r, Integer profileCompleteness, List<String> assumptions) {
        return new RiskAssessmentVO(r.getId(), r.getUserId(), r.getProfileId(), r.getDiseaseName(),
                r.getRiskLevel(), r.getRiskScore(), r.getSuggestion(), profileCompleteness,
                r.getCreateTime(), assumptions);
    }

    /**
     * 基于规则的简易风险评估引擎（糖尿病/高血压/心血管）
     */
    private static final class RiskRuleEngine {
        static List<RiskAssessment> evaluate(Long userId, HealthProfile profile, List<HealthMetric> recentMetrics) {
            return evaluate(userId, profile, recentMetrics, AssumptionCollector.noop());
        }

        static List<RiskAssessment> evaluate(Long userId, HealthProfile profile, List<HealthMetric> recentMetrics,
                                             AssumptionCollector collector) {
            List<RiskAssessment> result = new ArrayList<>();
            Long profileId = profile != null ? profile.getId() : null;

            if (profile != null) {
                int age = profile.getBirthDate() != null
                        ? LocalDate.now().getYear() - profile.getBirthDate().getYear() : 35;
                BigDecimal bmi = profile.getBmi() != null ? profile.getBmi() : BigDecimal.valueOf(22);
                boolean familyDiabetes = ProfileFieldCatalog.mentionsDiabetes(profile.getFamilyHistory());
                BigDecimal latestBloodSugar = getLatestMetric(recentMetrics, "血糖");
                int diabetesScore = (age > 45 ? 20 : 10) + (bmi.compareTo(BigDecimal.valueOf(24)) > 0 ? 25 : 10)
                        + (familyDiabetes ? 25 : 0) + (latestBloodSugar != null && latestBloodSugar.compareTo(BigDecimal.valueOf(6.1)) > 0 ? 30 : 0);
                result.add(create(userId, profileId, "糖尿病", diabetesScore));

                BigDecimal systolic = getLatestMetric(recentMetrics, "收缩压");
                BigDecimal diastolic = getLatestMetric(recentMetrics, "舒张压");
                int bpScore = (age > 50 ? 15 : 5) + (systolic != null && systolic.compareTo(BigDecimal.valueOf(130)) > 0 ? 35 : 0)
                        + (diastolic != null && diastolic.compareTo(BigDecimal.valueOf(85)) > 0 ? 30 : 0);
                result.add(create(userId, profileId, "高血压", bpScore));

                int cardioScore = Math.min(100, diabetesScore / 2 + bpScore / 2 + (bmi.compareTo(BigDecimal.valueOf(28)) > 0 ? 20 : 0));
                result.add(create(userId, profileId, "心血管疾病", cardioScore));
            } else {
                collector.add(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION);
                result.add(create(userId, null, "糖尿病", 30));
                result.add(create(userId, null, "高血压", 25));
                result.add(create(userId, null, "心血管疾病", 25));
            }
            return result;
        }

        private static BigDecimal getLatestMetric(List<HealthMetric> metrics, String type) {
            return metrics.stream().filter(m -> type.equals(m.getMetricKey())).findFirst().map(HealthMetric::getValue).orElse(null);
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
}