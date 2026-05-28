package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.HealthGoal;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for health_goals table
 */
@Mapper
public interface HealthGoalMapper extends BaseMapper<HealthGoal> {
}
