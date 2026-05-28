package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户连续记录天数实体
 */
@Data
@TableName("user_streaks")
public class UserStreak {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer currentStreak;

    private Integer longestStreak;

    @TableField("last_activity_date")
    private LocalDate lastActivityDate;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
