package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.entity.ExaminationReport;
import com.hhs.entity.LabResult;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.ExaminationReportMapper;
import com.hhs.mapper.LabResultMapper;
import com.hhs.service.ScreeningComparisonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of ScreeningComparisonService.
 * Compares two examination reports and their associated lab results.
 */
@Slf4j
@Service
public class ScreeningComparisonServiceImpl implements ScreeningComparisonService {

    private final ExaminationReportMapper examinationReportMapper;
    private final LabResultMapper labResultMapper;

    public ScreeningComparisonServiceImpl(ExaminationReportMapper examinationReportMapper,
                                          LabResultMapper labResultMapper) {
        this.examinationReportMapper = examinationReportMapper;
        this.labResultMapper = labResultMapper;
    }

    @Override
    public Map<String, Object> compareScreenings(Long userId, Long screeningIdA, Long screeningIdB) {
        // 1. Fetch both reports and verify user ownership
        ExaminationReport reportA = fetchReportAndVerifyOwnership(screeningIdA, userId);
        ExaminationReport reportB = fetchReportAndVerifyOwnership(screeningIdB, userId);

        // 2. Fetch all lab results for both reports
        List<LabResult> resultsA = labResultMapper.selectList(
                new LambdaQueryWrapper<LabResult>()
                        .eq(LabResult::getReportId, screeningIdA)
                        .orderByAsc(LabResult::getSortOrder));
        List<LabResult> resultsB = labResultMapper.selectList(
                new LambdaQueryWrapper<LabResult>()
                        .eq(LabResult::getReportId, screeningIdB)
                        .orderByAsc(LabResult::getSortOrder));

        // 3. Index results by name for comparison
        Map<String, LabResult> resultMapA = resultsA.stream()
                .collect(Collectors.toMap(LabResult::getName, Function.identity(), (a, b) -> a));
        Map<String, LabResult> resultMapB = resultsB.stream()
                .collect(Collectors.toMap(LabResult::getName, Function.identity(), (a, b) -> a));

        // 4. Collect all unique metric names (preserving order from A then B)
        List<String> allMetricNames = new ArrayList<>(resultMapA.keySet());
        for (String name : resultMapB.keySet()) {
            if (!allMetricNames.contains(name)) {
                allMetricNames.add(name);
            }
        }

        // 5. Build comparison entries
        List<Map<String, Object>> comparisons = new ArrayList<>();
        int improvedCount = 0;
        int worsenedCount = 0;
        int stableCount = 0;

        for (String metricName : allMetricNames) {
            LabResult lrA = resultMapA.get(metricName);
            LabResult lrB = resultMapB.get(metricName);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("metricKey", metricName);
            entry.put("metricDisplayName", metricName);

            Double valueA = parseDouble(lrA != null ? lrA.getValue() : null);
            Double valueB = parseDouble(lrB != null ? lrB.getValue() : null);

            entry.put("valueA", valueA);
            entry.put("valueB", valueB);

            if (lrA != null) {
                entry.put("unitA", lrA.getUnit());
                entry.put("referenceRangeA", lrA.getReferenceRange());
            }
            if (lrB != null) {
                entry.put("unitB", lrB.getUnit());
                entry.put("referenceRangeB", lrB.getReferenceRange());
            }

            if (valueA != null && valueB != null && valueA != 0) {
                double changePercent = ((valueB - valueA) / Math.abs(valueA)) * 100;
                // Round to 2 decimal places
                changePercent = Math.round(changePercent * 100.0) / 100.0;
                entry.put("changePercent", changePercent);

                String status;
                if (Math.abs(changePercent) < 5) {
                    status = "stable";
                    stableCount++;
                } else if (valueB < valueA) {
                    // Default assumption: lower is better for most metrics
                    status = "improved";
                    improvedCount++;
                } else {
                    status = "worsened";
                    worsenedCount++;
                }
                entry.put("status", status);
            } else {
                entry.put("changePercent", null);
                entry.put("status", "stable");
                stableCount++;
            }

            comparisons.add(entry);
        }

        // 6. Build response
        Map<String, Object> screeningA = new LinkedHashMap<>();
        screeningA.put("id", reportA.getId());
        screeningA.put("reportName", reportA.getReportName());
        screeningA.put("reportDate", reportA.getReportDate());
        screeningA.put("institution", reportA.getInstitution());

        Map<String, Object> screeningB = new LinkedHashMap<>();
        screeningB.put("id", reportB.getId());
        screeningB.put("reportName", reportB.getReportName());
        screeningB.put("reportDate", reportB.getReportDate());
        screeningB.put("institution", reportB.getInstitution());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("improvedCount", improvedCount);
        summary.put("worsenedCount", worsenedCount);
        summary.put("stableCount", stableCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("screeningA", screeningA);
        result.put("screeningB", screeningB);
        result.put("comparisons", comparisons);
        result.put("summary", summary);

        log.info("Compared screenings {} and {} for user {}: {} metrics compared",
                screeningIdA, screeningIdB, userId, comparisons.size());

        return result;
    }

    /**
     * Fetch an examination report by ID and verify it belongs to the given user.
     */
    private ExaminationReport fetchReportAndVerifyOwnership(Long reportId, Long userId) {
        ExaminationReport report = examinationReportMapper.selectById(reportId);
        if (report == null || !report.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        return report;
    }

    /**
     * Safely parse a string value to Double. Returns null if the value is null, blank, or not numeric.
     */
    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Remove common non-numeric characters (e.g., "%" or units appended to value)
            String cleaned = value.replaceAll("[^0-9.\\-+eE]", "");
            if (cleaned.isEmpty()) {
                return null;
            }
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.debug("Could not parse lab result value as numeric: {}", value);
            return null;
        }
    }
}
