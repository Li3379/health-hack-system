package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Goal progress entity
 * Records individual progress entries for a health goal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("goal_progress")
public class GoalProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long goalId;

    private Double value;

    private String note;

    private LocalDateTime recordedAt;
}
