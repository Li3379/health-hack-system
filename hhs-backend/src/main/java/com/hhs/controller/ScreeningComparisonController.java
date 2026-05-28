package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.ScreeningCompareRequest;
import com.hhs.security.SecurityUtils;
import com.hhs.service.ScreeningComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@Tag(name = "筛查对比模块", description = "对比两次体检报告的检验指标")
@RestController
@RequestMapping("/api/screening")
public class ScreeningComparisonController {

    private final ScreeningComparisonService screeningComparisonService;

    public ScreeningComparisonController(ScreeningComparisonService screeningComparisonService) {
        this.screeningComparisonService = screeningComparisonService;
    }

    @Operation(summary = "对比筛查记录", description = "对比两次体检报告的检验指标变化")
    @PostMapping("/compare")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> compareScreenings(
            @Valid @RequestBody ScreeningCompareRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Compare screenings request: userId={}, screeningIdA={}, screeningIdB={}",
                userId, request.screeningIdA(), request.screeningIdB());
        Map<String, Object> comparison = screeningComparisonService.compareScreenings(
                userId, request.screeningIdA(), request.screeningIdB());
        return Result.success(comparison);
    }
}
