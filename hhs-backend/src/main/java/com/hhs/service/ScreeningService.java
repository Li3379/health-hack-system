package com.hhs.service;

import com.hhs.vo.ExaminationReportVO;
import com.hhs.vo.LabResultVO;
import com.hhs.vo.OcrStatusVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Screening Service
 * Manages examination reports including upload, listing, retrieval, deletion, and OCR processing
 */
public interface ScreeningService {

    /**
     * Upload a new examination report
     *
     * @param userId User ID
     * @param file Report file (PDF, JPG, PNG)
     * @param reportName Optional report name
     * @param reportType Optional report type
     * @param institution Optional institution name
     * @param reportDate Optional report date (yyyy-MM-dd format)
     * @return ExaminationReportVO containing the uploaded report
     * @throws BusinessException if file validation fails
     */
    ExaminationReportVO uploadReport(Long userId, MultipartFile file, String reportName, String reportType, String institution, String reportDate);

    /**
     * List all examination reports for a user
     *
     * @param userId User ID
     * @return List of ExaminationReportVO sorted by creation time (descending)
     */
    List<ExaminationReportVO> listReports(Long userId);

    /**
     * Get a specific examination report
     *
     * @param id Report ID
     * @param userId User ID (for authorization)
     * @return ExaminationReportVO
     * @throws BusinessException if report not found or user not authorized
     */
    ExaminationReportVO getReport(Long id, Long userId);

    /**
     * Delete an examination report and its associated lab results
     *
     * @param id Report ID
     * @param userId User ID (for authorization)
     * @throws BusinessException if report not found or user not authorized
     */
    void deleteReport(Long id, Long userId);

    /**
     * Manually trigger OCR processing for a report
     *
     * @param reportId Report ID
     * @param userId User ID (for authorization)
     * @throws BusinessException if report not found or user not authorized
     */
    void triggerOcr(Long reportId, Long userId);

    /**
     * Get OCR processing status for a report
     *
     * @param reportId Report ID
     * @param userId User ID (for authorization)
     * @return OcrStatusVO containing status and optional error message
     * @throws BusinessException if report not found or user not authorized
     */
    OcrStatusVO getOcrStatus(Long reportId, Long userId);

    /**
     * Get lab results extracted from a report
     *
     * @param reportId Report ID
     * @param userId User ID (for authorization)
     * @return List of LabResultVO sorted by sort order
     * @throws BusinessException if report not found or user not authorized
     */
    List<LabResultVO> getLabResults(Long reportId, Long userId);
}
