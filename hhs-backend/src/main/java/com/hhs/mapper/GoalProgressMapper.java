package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.GoalProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for goal_progress table
 */
@Mapper
public interface GoalProgressMapper extends BaseMapper<GoalProgress> {
}
