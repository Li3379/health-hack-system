package com.hhs.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mapper for alert_rule table
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    /**
     * Get all enabled alert rules
     * Uses BaseMapper methods for basic CRUD
     */
    default List<AlertRule> getEnabledRules() {
        return this.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getEnabled, true));
    }
}
