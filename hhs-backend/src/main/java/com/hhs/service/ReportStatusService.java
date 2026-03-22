package com.hhs.service;

/**
 * Service for updating examination report OCR status.
 * This separate service prevents circular dependencies between
 * ScreeningService and OcrServiceImpl.
 */
public interface ReportStatusService {

    /**
     * Update OCR status for a report with optional error message or abnormal summary.
     *
     * @param reportId The report ID to update
     * @param ocrStatus The new status (PENDING, PROCESSING, SUCCESS, FAILED)
     * @param errorMessage Optional error message for FAILED status, or abnormal summary for SUCCESS
     */
    void updateOcrStatus(Long reportId, String ocrStatus, String errorMessage);
}