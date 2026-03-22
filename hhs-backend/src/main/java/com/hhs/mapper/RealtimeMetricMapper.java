package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.RealtimeMetric;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for realtime_metric table
 * Note: This is a partitioned table without foreign key constraints.
 * Data consistency must be maintained at the application layer.
 */
@Mapper
public interface RealtimeMetricMapper extends BaseMapper<RealtimeMetric> {

    /**
     * Get latest metric for a user and metric type
     */
    @Select("SELECT * FROM realtime_metric WHERE user_id = #{userId} AND metric_key = #{metricKey} ORDER BY id DESC LIMIT 1")
    RealtimeMetric getLatestMetric(@Param("userId") Long userId, @Param("metricKey") String metricKey);

    /**
     * Get metrics within a time range for trend analysis
     */
    @Select("SELECT * FROM realtime_metric WHERE user_id = #{userId} AND metric_key = #{metricKey} " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime} ORDER BY created_at ASC")
    List<RealtimeMetric> getMetricsInRange(@Param("userId") Long userId,
                                           @Param("metricKey") String metricKey,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * Get all recent metrics for a user (dashboard summary)
     * Returns the most recent entry for each metric type
     */
    @Select("SELECT * FROM realtime_metric r1 WHERE r1.id = (" +
            "SELECT r2.id FROM realtime_metric r2 " +
            "WHERE r2.user_id = #{userId} AND r2.metric_key = r1.metric_key " +
            "ORDER BY r2.id DESC LIMIT 1" +
            ")")
    List<RealtimeMetric> getLatestMetricsByUser(@Param("userId") Long userId);

    /**
     * Count metrics for cleanup (retention policy)
     */
    @Select("SELECT COUNT(*) FROM realtime_metric WHERE created_at < #{cutoffDate}")
    Long countOldMetrics(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete all metrics for a user (called when user is deleted)
     * Application-level cascade delete to replace missing foreign key constraint
     */
    @Delete("DELETE FROM realtime_metric WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * Delete orphaned metrics (user_id references non-existent users)
     * Should be run periodically by scheduled task
     */
    @Delete("DELETE rm FROM realtime_metric r " +
            "LEFT JOIN sys_user u ON r.user_id = u.id " +
            "WHERE u.id IS NULL")
    int deleteOrphanedMetrics();

    /**
     * Delete old metrics by cutoff date (for data retention policy)
     */
    @Delete("DELETE FROM realtime_metric WHERE created_at < #{cutoffDate}")
    int deleteOldMetrics(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete realtime metric matching the health metric (cascade delete on health_metric delete)
     * Matches by userId, metricKey, and createdAt (converted from recordDate)
     */
    @Delete("DELETE FROM realtime_metric WHERE user_id = #{userId} AND metric_key = #{metricKey} AND created_at >= #{startTime} AND created_at < #{endTime}")
    int deleteByHealthMetric(@Param("userId") Long userId,
                            @Param("metricKey") String metricKey,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);
}
