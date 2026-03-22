package com.hhs.service.domain;

import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Risk scorer - handles risk assessment scoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskScorer {

    private final RiskAssessmentMapper riskAssessmentMapper;

    /**
     * Calculate score based on risk assessment level
     *
     * @param userId the user ID
     * @return score from 0-100
     */
    public int calculate(Long userId) {
        List<RiskAssessment> risks = riskAssessmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RiskAssessment>()
                        .eq(RiskAssessment::getUserId, userId)
                        .orderByDesc(RiskAssessment::getCreateTime)
                        .last("LIMIT 1")
        );

        if (risks.isEmpty()) {
            return 70;
        }

        RiskAssessment risk = risks.get(0);
        return switch (risk.getRiskLevel()) {
            case "LOW" -> 100;
            case "MEDIUM" -> 70;
            case "HIGH" -> 40;
            default -> 70;
        };
    }
}
