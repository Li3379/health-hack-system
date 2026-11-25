package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for alert_rule table
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {
}
