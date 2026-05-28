package com.hhs.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for adding progress to a health goal
 */
@Data
public class AddProgressRequest {

    @NotNull(message = "进度值不能为空")
    @DecimalMin(value = "0.0", message = "进度值不能为负数")
    private Double value;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String note;
}
