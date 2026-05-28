package com.hhs.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request DTO for updating a health goal
 */
@Data
public class UpdateGoalRequest {

    @Size(max = 100, message = "目标标题不能超过100个字符")
    private String title;

    @Size(max = 500, message = "目标描述不能超过500个字符")
    private String description;

    private LocalDate endDate;
}
