package com.hhs.service;

import com.hhs.entity.ExportJob;

import java.time.LocalDate;

/**
 * Export service interface.
 * Manages async data export jobs for metrics, scores, and screenings.
 */
public interface ExportService {

    /**
     * Create a new export job and start async processing.
     *
     * @param userId      the authenticated user ID
     * @param exportType  "csv" or "excel"
     * @param contentType "metrics", "scores", or "screenings"
     * @param startDate   optional date range start (inclusive)
     * @param endDate     optional date range end (inclusive)
     * @return the created ExportJob with status "pending"
     */
    ExportJob createExport(Long userId, String exportType, String contentType,
                           LocalDate startDate, LocalDate endDate);

    /**
     * Get the current status of an export job.
     *
     * @param jobId  the export job ID
     * @param userId the authenticated user ID (for authorization)
     * @return the ExportJob
     */
    ExportJob getExportStatus(Long jobId, Long userId);

    /**
     * Get the exported file bytes for download.
     *
     * @param jobId  the export job ID
     * @param userId the authenticated user ID (for authorization)
     * @return byte array of the file content
     */
    byte[] getExportFile(Long jobId, Long userId);
}
