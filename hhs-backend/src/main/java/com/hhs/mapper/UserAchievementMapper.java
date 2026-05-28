package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.UserAchievement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户已解锁成就 Mapper
 */
@Mapper
public interface UserAchievementMapper extends BaseMapper<UserAchievement> {
}
