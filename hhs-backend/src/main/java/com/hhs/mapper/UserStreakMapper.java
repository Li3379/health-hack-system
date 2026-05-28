package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.UserStreak;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户连续记录 Mapper
 */
@Mapper
public interface UserStreakMapper extends BaseMapper<UserStreak> {

    /**
     * 插入或更新用户连续记录。
     * 若该用户已存在记录则更新 current_streak、longest_streak、last_activity_date。
     */
    @Update("INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_activity_date, created_at, updated_at) " +
            "VALUES (#{record.userId}, #{record.currentStreak}, #{record.longestStreak}, #{record.lastActivityDate}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "current_streak = VALUES(current_streak), " +
            "longest_streak = VALUES(longest_streak), " +
            "last_activity_date = VALUES(last_activity_date), " +
            "updated_at = NOW()")
    int insertOrUpdate(@Param("record") UserStreak record);
}
