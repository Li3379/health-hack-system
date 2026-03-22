package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.HealthAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for health_alert table
 */
@Mapper
public interface HealthAlertMapper extends BaseMapper<HealthAlert> {

    /**
     * Get unread alert count for a user
     */
    @Select("SELECT COUNT(*) FROM health_alert WHERE user_id = #{userId} AND is_read = FALSE")
    Long getUnreadCount(@Param("userId") Long userId);

    /**
     * Get recent alerts for a user
     */
    @Select("SELECT * FROM health_alert WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<HealthAlert> getRecentAlerts(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * Check if similar alert exists in time window (deduplication)
     */
    @Select("SELECT * FROM health_alert WHERE user_id = #{userId} AND metric_key = #{metricKey} " +
            "AND alert_type = #{alertType} AND created_at >= #{since} ORDER BY created_at DESC LIMIT 1")
    HealthAlert findRecentSimilarAlert(@Param("userId") Long userId,
                                        @Param("metricKey") String metricKey,
                                        @Param("alertType") String alertType,
                                        @Param("since") LocalDateTime since);

    /**
     * Count alerts in last hour (rate limiting)
     */
    @Select("SELECT COUNT(*) FROM health_alert WHERE user_id = #{userId} AND created_at >= #{since}")
    Long countAlertsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
