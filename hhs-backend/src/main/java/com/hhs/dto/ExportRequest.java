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

    @NotBlank(message = "exportType is required")
    @Pattern(regexp = "^(csv|excel)$", message = "exportType must be csv or excel")
    private String exportType;

    @NotBlank(message = "contentType is required")
    @Pattern(regexp = "^(metrics|scores|screenings)$",
            message = "contentType must be metrics, scores, or screenings")
    private String contentType;

    /** Optional start date filter (yyyy-MM-dd). */
    private String startDate;

    /** Optional end date filter (yyyy-MM-dd). */
    private String endDate;
}
