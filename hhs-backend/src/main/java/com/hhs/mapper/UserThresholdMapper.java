package com.hhs.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.UserThreshold;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mapper for user_threshold table
 */
@Mapper
public interface UserThresholdMapper extends BaseMapper<UserThreshold> {

    /**
     * Get all thresholds for a user
     * Uses BaseMapper methods for basic CRUD
     */
    default List<UserThreshold> getByUserId(Long userId) {
        return this.selectList(new LambdaQueryWrapper<UserThreshold>()
                .eq(UserThreshold::getUserId, userId));
    }
}
