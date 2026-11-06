package com.hhs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Export request DTO.
 * Sent by the client to initiate a data export job.
 */
@Data
public class ExportRequest {

    @NotBlank(message = "导出类型不能为空")
    @Pattern(regexp = "^(csv|excel)$", message = "导出类型只能是 CSV 或 Excel")
    private String exportType;

    @NotBlank(message = "导出内容类型不能为空")
    @Pattern(regexp = "^(metrics|scores|screenings)$",
            message = "导出内容类型只能是指标、评分或筛查记录")
    private String contentType;

    /** Optional start date filter (yyyy-MM-dd). */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式应为 yyyy-MM-dd")
    private String startDate;

    /** Optional end date filter (yyyy-MM-dd). */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式应为 yyyy-MM-dd")
    private String endDate;
}
