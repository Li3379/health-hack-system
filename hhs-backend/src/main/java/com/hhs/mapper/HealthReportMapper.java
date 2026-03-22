package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.HealthReport;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for health_report table
 */
@Mapper
public interface HealthReportMapper extends BaseMapper<HealthReport> {

    /**
     * Get the latest report for a user
     *
     * @param userId User ID
     * @return Latest health report or null if not found
     */
    @Select("SELECT * FROM health_report WHERE user_id = #{userId} ORDER BY generated_at DESC LIMIT 1")
    HealthReport getLatestByUserId(@Param("userId") Long userId);

    /**
     * List reports for a user with limit
     *
     * @param userId User ID
     * @param limit Maximum number of records
     * @return List of health reports ordered by generated_at DESC
     */
    @Select("SELECT * FROM health_report WHERE user_id = #{userId} ORDER BY generated_at DESC LIMIT #{limit}")
    List<HealthReport> listByUserIdWithLimit(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * Get report by report ID
     *
     * @param reportId Report unique identifier
     * @return Health report or null if not found
     */
    @Select("SELECT * FROM health_report WHERE report_id = #{reportId}")
    HealthReport getByReportId(@Param("reportId") String reportId);

    /**
     * Delete all reports for a user (called when user is deleted)
     *
     * @param userId User ID
     * @return Number of deleted records
     */
    @Delete("DELETE FROM health_report WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * Count reports for a user
     *
     * @param userId User ID
     * @return Number of reports
     */
    @Select("SELECT COUNT(*) FROM health_report WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}