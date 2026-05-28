package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request DTO for recording a mood entry
 */
@Data
public class RecordMoodRequest {

    @NotNull(message = "心情评分不能为空")
    @Min(value = 1, message = "心情评分最小为1")
    @Max(value = 10, message = "心情评分最大为10")
    private Integer moodScore;

    @Min(value = 1, message = "精力值最小为1")
    @Max(value = 10, message = "精力值最大为10")
    private Integer energyLevel;

    @Min(value = 1, message = "压力值最小为1")
    @Max(value = 10, message = "压力值最大为10")
    private Integer stressLevel;

    @Min(value = 1, message = "睡眠质量最小为1")
    @Max(value = 10, message = "睡眠质量最大为10")
    private Integer sleepQuality;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String notes;

    @NotNull(message = "记录日期不能为空")
    @PastOrPresent(message = "记录日期不能是未来日期")
    private LocalDate entryDate;
}
