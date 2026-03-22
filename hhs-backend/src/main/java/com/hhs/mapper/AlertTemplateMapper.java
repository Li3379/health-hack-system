package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.AlertTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * Alert template mapper
 */
@Mapper
public interface AlertTemplateMapper extends BaseMapper<AlertTemplate> {

    @Select("SELECT * FROM alert_template WHERE enabled = 1 ORDER BY priority DESC")
    List<AlertTemplate> findAllEnabled();

    @Select("SELECT * FROM alert_template WHERE metric_key = #{metricKey} AND severity_level = #{severityLevel} AND enabled = 1 LIMIT 1")
    Optional<AlertTemplate> findByMetricKeyAndSeverity(String metricKey, String severityLevel);

    @Select("SELECT * FROM alert_template WHERE metric_key = #{metricKey} AND enabled = 1 ORDER BY priority DESC")
    List<AlertTemplate> findByMetricKey(String metricKey);
}