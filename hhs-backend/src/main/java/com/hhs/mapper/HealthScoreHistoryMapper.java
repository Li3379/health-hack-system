package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.HealthScoreHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Mapper for health_score_history table
 */
@Mapper
public interface HealthScoreHistoryMapper extends BaseMapper<HealthScoreHistory> {

    /**
     * Insert or update score history for a given user and date.
     * If a record with the same (user_id, score_date) exists, update all score fields.
     */
    @Update("INSERT INTO health_score_history " +
            "(user_id, score_date, overall_score, cardiovascular_score, metabolic_score, weight_score, lifestyle_score, factors_snapshot) " +
            "VALUES (#{record.userId}, #{record.scoreDate}, #{record.overallScore}, #{record.cardiovascularScore}, " +
            "#{record.metabolicScore}, #{record.weightScore}, #{record.lifestyleScore}, #{record.factorsSnapshot}) " +
            "ON DUPLICATE KEY UPDATE " +
            "overall_score = VALUES(overall_score), " +
            "cardiovascular_score = VALUES(cardiovascular_score), " +
            "metabolic_score = VALUES(metabolic_score), " +
            "weight_score = VALUES(weight_score), " +
            "lifestyle_score = VALUES(lifestyle_score), " +
            "factors_snapshot = VALUES(factors_snapshot)")
    int insertOrUpdate(@Param("record") HealthScoreHistory record);
}
