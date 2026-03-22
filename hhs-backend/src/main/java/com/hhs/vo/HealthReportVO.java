package com.hhs.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Generated Health Report VO
 */
public record HealthReportVO(
    String reportId,
    LocalDateTime generatedAt,

    // 基本信息
    UserInfo userInfo,

    // 健康评分
    int overallScore,
    String scoreLevel,

    // 各维度分析
    List<DimensionAnalysis> dimensions,

    // 风险提示
    List<RiskAlert> riskAlerts,

    // 改善建议
    List<ImprovementSuggestion> suggestions,

    // 总结
    String summary
) {
    public record UserInfo(
        String gender,
        int age,
        double heightCm,
        double weightKg,
        double bmi
    ) {}

    public record DimensionAnalysis(
        String name,
        int score,
        String status,
        String description
    ) {}

    public record RiskAlert(
        String level,
        String category,
        String description,
        String recommendation
    ) {}

    public record ImprovementSuggestion(
        String category,
        String priority,
        String suggestion,
        String action
    ) {}
}