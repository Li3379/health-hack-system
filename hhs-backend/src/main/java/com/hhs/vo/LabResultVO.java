package com.hhs.vo;

import java.time.LocalDateTime;

public record LabResultVO(
        Long id,
        Long reportId,
        String name,
        String category,
        String value,
        String unit,
        String referenceRange,
        Boolean isAbnormal,
        String trend,
        Integer sortOrder,
        LocalDateTime createTime
) {
}
