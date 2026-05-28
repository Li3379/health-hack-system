package com.hhs.dto;

import jakarta.validation.constraints.NotNull;

public record ScreeningCompareRequest(
        @NotNull(message = "筛查记录A的ID不能为空") Long screeningIdA,
        @NotNull(message = "筛查记录B的ID不能为空") Long screeningIdB
) {
}
