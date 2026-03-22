package com.hhs.vo;

import java.util.List;

/**
 * 一次评估产生的多条疾病风险记录
 */
public record RiskAssessmentListVO(
        List<RiskAssessmentVO> items
) {
}
