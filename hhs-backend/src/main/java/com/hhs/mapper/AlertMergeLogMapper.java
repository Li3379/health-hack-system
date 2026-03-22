package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.AlertMergeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Alert merge log mapper
 */
@Mapper
public interface AlertMergeLogMapper extends BaseMapper<AlertMergeLog> {

    @Select("SELECT * FROM alert_merge_log WHERE user_id = #{userId} AND metric_key = #{metricKey} " +
            "AND first_occurrence_at >= #{since} ORDER BY first_occurrence_at DESC LIMIT 1")
    Optional<AlertMergeLog> findRecentByUserAndMetric(@Param("userId") Long userId,
                                                       @Param("metricKey") String metricKey,
                                                       @Param("since") LocalDateTime since);

    @Update("UPDATE alert_merge_log SET merge_count = merge_count + 1, " +
            "last_occurrence_at = #{lastOccurrenceAt}, " +
            "merged_alert_ids = JSON_ARRAY_APPEND(COALESCE(merged_alert_ids, '[]'), '$', #{alertId}) " +
            "WHERE id = #{id}")
    int incrementMergeCount(@Param("id") Long id,
                           @Param("alertId") Long alertId,
                           @Param("lastOccurrenceAt") LocalDateTime lastOccurrenceAt);
}