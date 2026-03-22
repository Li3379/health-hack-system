package com.hhs.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExaminationReportVO(
        Long id,
        Long userId,
        String reportName,
        String reportType,
        String institution,
        LocalDate reportDate,
        String fileUrl,
        String ocrStatus,
        String abnormalSummary,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
