package com.hhs.service.domain;

import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Risk assessment analysis
 * Responsible for analyzing and interpreting risk assessment data
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAnalyzer {

    private final RiskAssessmentMapper riskAssessmentMapper;

    /**
     * Get the latest risk assessment for a user
     *
     * @param userId the user ID
     * @return the latest risk assessment, or null if none exists
     */
    public RiskAssessment getLatestAssessment(Long userId) {
        List<RiskAssessment> risks = riskAssessmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RiskAssessment>()
                        .eq(RiskAssessment::getUserId, userId)
                        .orderByDesc(RiskAssessment::getCreateTime)
                        .last("LIMIT 1")
        );

        return risks.isEmpty() ? null : risks.get(0);
    }

    /**
     * Calculate score contribution from risk assessment
     *
     * @param userId the user ID
     * @return score based on risk level (100 for LOW, 70 for MEDIUM, 40 for HIGH, 70 default)
     */
    public int calculateRiskScore(Long userId) {
        RiskAssessment risk = getLatestAssessment(userId);
        if (risk == null) {
            return 70; // Neutral score
        }

        return switch (risk.getRiskLevel()) {
            case "LOW" -> 100;
            case "MEDIUM" -> 70;
            case "HIGH" -> 40;
            default -> 70;
        };
    }

    /**
     * Get risk level as a descriptive string
     *
     * @param userId the user ID
     * @return risk level description or "UNKNOWN" if no assessment exists
     */
    public String getRiskLevelDescription(Long userId) {
        RiskAssessment risk = getLatestAssessment(userId);
        if (risk == null) {
            return "UNKNOWN";
        }

        return switch (risk.getRiskLevel()) {
            case "LOW" -> "低风险";
            case "MEDIUM" -> "中风险";
            case "HIGH" -> "高风险";
            default -> "未知";
        };
    }

    /**
     * Check if user has any high risk factors
     *
     * @param userId the user ID
     * @return true if user has HIGH risk level assessment
     */
    public boolean hasHighRisk(Long userId) {
        RiskAssessment risk = getLatestAssessment(userId);
        return risk != null && "HIGH".equals(risk.getRiskLevel());
    }
}
