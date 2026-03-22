package com.hhs.schedule;

import com.hhs.mapper.RealtimeMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled tasks for data cleanup and maintenance
 * Replaces foreign key CASCADE operations for partitioned tables
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {

    private final RealtimeMetricMapper realtimeMetricMapper;

    /**
     * Clean up orphaned metrics (references to non-existent users)
     * Runs daily at 2:00 AM
     *
     * Note: This replaces the missing ON DELETE CASCADE foreign key behavior
     * for the realtime_metric partitioned table.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOrphanedMetrics() {
        log.info("Starting orphaned metrics cleanup...");
        try {
            int deleted = realtimeMetricMapper.deleteOrphanedMetrics();
            log.info("Orphaned metrics cleanup completed: {} records deleted", deleted);
        } catch (Exception e) {
            log.error("Failed to cleanup orphaned metrics", e);
        }
    }

    /**
     * Clean up old metrics based on retention policy
     * Different metric types have different retention periods
     * Runs weekly on Sunday at 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void cleanupOldMetrics() {
        log.info("Starting old metrics cleanup based on retention policy...");
        try {
            LocalDateTime now = LocalDateTime.now();

            // Heart rate: 30 days
            int heartRateDeleted = realtimeMetricMapper.deleteOldMetrics(
                now.minusDays(30).withHour(0).withMinute(0));
            log.info("Heart rate metrics cleanup: {} records deleted", heartRateDeleted);

            // Blood pressure: 90 days
            int bpDeleted = realtimeMetricMapper.deleteOldMetrics(
                now.minusDays(90).withHour(0).withMinute(0));
            log.info("Blood pressure metrics cleanup: {} records deleted", bpDeleted);

            // Glucose: 180 days (6 months)
            int glucoseDeleted = realtimeMetricMapper.deleteOldMetrics(
                now.minusDays(180).withHour(0).withMinute(0));
            log.info("Glucose metrics cleanup: {} records deleted", glucoseDeleted);

            // Weight: 365 days (1 year)
            int weightDeleted = realtimeMetricMapper.deleteOldMetrics(
                now.minusDays(365).withHour(0).withMinute(0));
            log.info("Weight metrics cleanup: {} records deleted", weightDeleted);

            // Other metrics: 30 days
            int otherDeleted = realtimeMetricMapper.deleteOldMetrics(
                now.minusDays(30).withHour(0).withMinute(0));
            log.info("Other metrics cleanup: {} records deleted", otherDeleted);

            log.info("Old metrics cleanup completed");
        } catch (Exception e) {
            log.error("Failed to cleanup old metrics", e);
        }
    }

    /**
     * Log partition statistics for monitoring
     * Runs monthly on the 1st at 4:00 AM
     */
    @Scheduled(cron = "0 0 4 1 * *")
    public void logPartitionStatistics() {
        log.info("Collecting partition statistics...");
        try {
            Long totalMetrics = realtimeMetricMapper.selectCount(null);
            Long oldMetrics = realtimeMetricMapper.countOldMetrics(
                LocalDateTime.now().minusDays(90));
            log.info("Partition statistics - Total: {}, Older than 90 days: {}", totalMetrics, oldMetrics);
        } catch (Exception e) {
            log.error("Failed to collect partition statistics", e);
        }
    }
}
