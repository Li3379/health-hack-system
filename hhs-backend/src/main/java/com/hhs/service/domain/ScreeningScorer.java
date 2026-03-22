package com.hhs.service.domain;

import com.hhs.entity.ExaminationReport;
import com.hhs.mapper.ExaminationReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Screening scorer - handles screening report scoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScreeningScorer {

    private final ExaminationReportMapper examinationReportMapper;

    /**
     * Calculate score based on screening report recency
     *
     * @param userId the user ID
     * @return score from 0-100
     */
    public int calculate(Long userId) {
        List<ExaminationReport> reports = examinationReportMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExaminationReport>()
                        .eq(ExaminationReport::getUserId, userId)
                        .orderByDesc(ExaminationReport::getReportDate)
                        .last("LIMIT 1")
        );

        if (reports.isEmpty()) {
            return 50; // Lower score for no screening
        }

        ExaminationReport report = reports.get(0);

        // Check if report is recent (within 6 months)
        java.time.LocalDate sixMonthsAgo = java.time.LocalDate.now().minusMonths(6);

        // Check if report date exists before comparison (Bug 3 fix: NPE prevention)
        if (report.getReportDate() == null) {
            // Report without date - treat as old report (lower score)
            log.debug("Report {} has null reportDate, treating as old report", report.getId());
            return 50;
        }

        return report.getReportDate().isAfter(sixMonthsAgo) ? 100 : 70;
    }
}
