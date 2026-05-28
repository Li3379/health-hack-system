package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User reminder entity for medication, exercise, water, sleep, and custom reminders
 */
@Data
@TableName("user_reminders")
public class UserReminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    /**
     * Reminder type: medication|exercise|water|sleep|custom
     */
    private String reminderType;

    private String cronExpression;

    private Boolean isActive;

    private LocalDateTime lastTriggeredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
