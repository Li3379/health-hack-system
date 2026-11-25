package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.AlertTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for the alert_template table.
 *
 * <p>The three hand-written queries that used to live here ({@code findAllEnabled},
 * {@code findByMetricKeyAndSeverity}, {@code findByMetricKey}) had no callers and no callers of
 * their own — the whole template-driven alert path was retired when alerts moved to
 * {@code ThresholdEvaluator} + {@code AlertRule}. The table and its seed rows stay: they are
 * still part of {@code schema.sql}, and removing data is a separate decision from removing
 * unreachable code.</p>
 *
 * <p>BaseMapper is kept so the table remains mapped; if template-driven alerts return, the
 * queries belong here again.</p>
 */
@Mapper
public interface AlertTemplateMapper extends BaseMapper<AlertTemplate> {
}
