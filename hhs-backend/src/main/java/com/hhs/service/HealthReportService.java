package com.hhs.service;

import com.hhs.vo.HealthReportVO;

import java.util.List;

/**
 * Health Report Service
 * Generates AI-powered health reports with caching support
 */
public interface HealthReportService {

    /**
     * Get or generate health report for a user
     * Returns cached report if available, otherwise generates a new one
     *
     * @param userId User ID
     * @return Health report (cached or newly generated)
     */
    HealthReportVO getOrGenerateReport(Long userId);

    /**
     * Force regenerate health report for a user
     * Clears cache and generates a fresh report
     *
     * @param userId User ID
     * @return Newly generated health report
     */
    HealthReportVO regenerateReport(Long userId);

    /**
     * Get health report history for a user
     *
     * @param userId User ID
     * @param limit Maximum number of reports to return
     * @return List of health reports ordered by generated_at DESC
     */
    List<HealthReportVO> getReportHistory(Long userId, int limit);

    /**
     * Get a specific health report by report ID
     *
     * @param userId User ID (for authorization)
     * @param reportId Report unique identifier
     * @return Health report or null if not found
     */
    HealthReportVO getReportById(Long userId, String reportId);

    /**
     * Generate a comprehensive health report for a user
     * (Legacy method, calls getOrGenerateReport internally)
     *
     * @param userId User ID
     * @return Generated health report
     * @deprecated Use {@link #getOrGenerateReport(Long)} instead
     */
    @Deprecated
    HealthReportVO generateReport(Long userId);
}