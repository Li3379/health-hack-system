package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request DTO for creating a health goal
 */
@Data
public class CreateGoalRequest {

    @NotBlank(message = "目标标题不能为空")
    @Size(max = 100, message = "目标标题不能超过100个字符")
    private String title;

    @Size(max = 500, message = "目标描述不能超过500个字符")
    private String description;

    @NotBlank(message = "指标类型不能为空")
    @Size(max = 50, message = "指标类型不能超过50个字符")
    private String metricKey;

    @NotNull(message = "目标值不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "目标值必须大于0")
    private Double targetValue;

    @Size(max = 20, message = "单位不能超过20个字符")
    private String unit;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    private LocalDate endDate;
}
