package com.hhs.vo;

import java.time.LocalDateTime;
import java.util.List;

public record RiskAssessmentVO(
        Long id,
        Long userId,
        Long profileId,
        String diseaseName,
        String riskLevel,
        Integer riskScore,
        String suggestion,
        LocalDateTime createTime
) {
}
