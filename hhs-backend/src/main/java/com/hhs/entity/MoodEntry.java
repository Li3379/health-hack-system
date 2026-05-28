package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mood entry entity
 * Tracks daily mood, energy, stress, and sleep quality
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mood_entries")
public class MoodEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * Mood score on a 1-10 scale
     */
    private Integer moodScore;

    /**
     * Energy level on a 1-10 scale
     */
    private Integer energyLevel;

    /**
     * Stress level on a 1-10 scale
     */
    private Integer stressLevel;

    /**
     * Sleep quality on a 1-10 scale
     */
    private Integer sleepQuality;

    private String notes;

    private LocalDate entryDate;

    private LocalDateTime createdAt;
}
