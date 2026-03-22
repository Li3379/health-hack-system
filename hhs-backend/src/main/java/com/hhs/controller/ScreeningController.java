package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.security.SecurityUtils;
import com.hhs.service.ScreeningService;
import com.hhs.vo.ExaminationReportVO;
import com.hhs.vo.LabResultVO;
import com.hhs.vo.OcrStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Tag(name = "筛查服务模块", description = "体检报告上传、OCR、检验指标")
@RestController
@RequestMapping("/api/screening")
public class ScreeningController {

    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @Operation(summary = "上传体检报告", description = "上传 PDF 或图片，可选报告名称、类型、机构、日期")
    @PostMapping(value = "/reports/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Result<ExaminationReportVO> uploadReport(
            @Parameter(description = "报告文件", required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String reportName,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String reportDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Upload report request: userId={}, fileName={}", userId, file.getOriginalFilename());
        ExaminationReportVO vo = screeningService.uploadReport(userId, file, reportName, reportType, institution, reportDate);
        return Result.success(vo);
    }

    @Operation(summary = "报告列表", description = "获取当前用户的体检报告列表")
    @GetMapping("/reports")
    @PreAuthorize("isAuthenticated()")
    public Result<com.hhs.common.PageResult<ExaminationReportVO>> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("List reports request: userId={}, page={}, size={}", userId, page, size);
        List<ExaminationReportVO> reports = screeningService.listReports(userId);
        
        // Create paginated response using PageResult.of() factory method
        com.hhs.common.PageResult<ExaminationReportVO> pageResult = 
            com.hhs.common.PageResult.of((long) reports.size(), page, size, reports);
        
        return Result.success(pageResult);
    }

    @Operation(summary = "报告详情", description = "获取单份报告详情")
    @GetMapping("/reports/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<ExaminationReportVO> getReport(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get report request: reportId={}, userId={}", id, userId);
        return Result.success(screeningService.getReport(id, userId));
    }

    @Operation(summary = "删除报告", description = "删除指定报告及其检验指标")
    @DeleteMapping("/reports/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteReport(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Delete report request: reportId={}, userId={}", id, userId);
        screeningService.deleteReport(id, userId);
        return Result.success();
    }

    @Operation(summary = "触发OCR", description = "对报告执行 OCR 识别")
    @PostMapping("/reports/{id}/ocr")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> triggerOcr(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Trigger OCR request: reportId={}, userId={}", id, userId);
        screeningService.triggerOcr(id, userId);
        return Result.success();
    }

    @Operation(summary = "OCR状态", description = "查询报告 OCR 识别状态")
    @GetMapping("/reports/{id}/ocr-status")
    @PreAuthorize("isAuthenticated()")
    public Result<OcrStatusVO> getOcrStatus(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get OCR status request: reportId={}, userId={}", id, userId);
        return Result.success(screeningService.getOcrStatus(id, userId));
    }

    @Operation(summary = "检验指标", description = "获取报告解析出的检验指标列表")
    @GetMapping("/reports/{id}/lab-results")
    @PreAuthorize("isAuthenticated()")
    public Result<List<LabResultVO>> getLabResults(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get lab results request: reportId={}, userId={}", id, userId);
        return Result.success(screeningService.getLabResults(id, userId));
    }
}
