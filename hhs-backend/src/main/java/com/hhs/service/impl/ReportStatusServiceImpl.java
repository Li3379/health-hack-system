package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.ExaminationReport;
import com.hhs.mapper.ExaminationReportMapper;
import com.hhs.service.ReportStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of ReportStatusService to update OCR status without circular dependencies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportStatusServiceImpl implements ReportStatusService {

    private final ExaminationReportMapper examinationReportMapper;

    /**
     * Update OCR status for a report with optional error message or abnormal summary
     *
     * @param reportId The report ID to update
     * @param ocrStatus The new status (PENDING, PROCESSING, SUCCESS, FAILED)
     * @param errorMessage Optional error message for FAILED status, or abnormal summary for SUCCESS
     */
    @Override
    @Transactional
    public void updateOcrStatus(Long reportId, String ocrStatus, String errorMessage) {
        log.info(">>> DIAGNOSTIC: updateOcrStatus() called: reportId={}, ocrStatus={}, errorMessage={}", reportId, ocrStatus, errorMessage);
        ExaminationReport report = examinationReportMapper.selectById(reportId);
        if (report != null) {
            report.setOcrStatus(ocrStatus);
            // For SUCCESS status, errorMessage contains abnormalSummary
            // For FAILED status, errorMessage contains error message
            if (errorMessage != null) {
                report.setAbnormalSummary(errorMessage);
            }
            examinationReportMapper.updateById(report);
            log.info(">>> DIAGNOSTIC: Updated OCR status for report {} to {} (abnormalSummary: {})", reportId, ocrStatus,
                errorMessage != null ? errorMessage.substring(0, Math.min(50, errorMessage.length())) : "null");
        } else {
            log.warn("Cannot update OCR status - report {} not found", reportId);
        }
    }
}