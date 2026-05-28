package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.ExportRequest;
import com.hhs.entity.ExportJob;
import com.hhs.security.SecurityUtils;
import com.hhs.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Data export controller.
 * Provides endpoints for creating export jobs, checking status, and downloading files.
 */
@Slf4j
@Tag(name = "数据导出", description = "健康数据CSV/Excel导出")
@RestController
@RequestMapping("/api/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "创建导出任务")
    public Result<ExportJob> createExport(@RequestBody @Valid ExportRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDate startDate = parseDate(request.getStartDate());
        LocalDate endDate = parseDate(request.getEndDate());
        log.info("Create export request: userId={}, type={}, content={}, range=[{}, {}]",
                userId, request.getExportType(), request.getContentType(), startDate, endDate);
        ExportJob job = exportService.createExport(
                userId, request.getExportType(), request.getContentType(), startDate, endDate);
        return Result.success(job);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询导出状态")
    public Result<ExportJob> getExportStatus(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get export status: jobId={}, userId={}", id, userId);
        ExportJob job = exportService.getExportStatus(id, userId);
        return Result.success(job);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "下载导出文件")
    public ResponseEntity<byte[]> downloadExport(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Download export: jobId={}, userId={}", id, userId);
        ExportJob job = exportService.getExportStatus(id, userId);
        byte[] fileBytes = exportService.getExportFile(id, userId);

        String encodedName = URLEncoder.encode(job.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentType = "excel".equals(job.getExportType())
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(fileBytes.length)
                .body(fileBytes);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        return LocalDate.parse(dateStr);
    }
}
