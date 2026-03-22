package com.hhs.controller;

import com.hhs.common.PageResult;
import com.hhs.common.Result;
import com.hhs.dto.OcrConfirmRequest;
import com.hhs.security.SecurityUtils;
import com.hhs.service.OcrService;
import com.hhs.vo.OcrHealthResultVO;
import com.hhs.vo.OcrHistoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * OCR健康图片识别控制器
 * 提供体检报告、药品标签、营养标签等图片识别功能
 */
@Slf4j
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Tag(name = "OCR识别", description = "健康图片OCR识别接口")
public class OcrController {

    private final OcrService ocrService;

    /**
     * 通用健康图片识别
     */
    @PostMapping(value = "/health-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "通用健康图片识别", description = "识别健康相关图片，支持体检报告、药品标签、营养标签等")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "识别成功"),
        @ApiResponse(responseCode = "400", description = "文件无效"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<OcrHealthResultVO> recognizeHealthImage(
            @Parameter(description = "图片文件", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "识别类型: report, medicine, nutrition")
            @RequestParam(value = "ocrType", defaultValue = "report") String ocrType) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("OCR识别请求: userId={}, ocrType={}, filename={}", userId, ocrType, file.getOriginalFilename());

        // Validate file
        if (file.isEmpty()) {
            return Result.failure(400, "文件不能为空");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.failure(400, "只能上传图片文件");
        }

        // Validate file size (max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            return Result.failure(400, "文件大小不能超过10MB");
        }

        // Validate file extension
        String filename = file.getOriginalFilename();
        if (filename != null && filename.contains(".")) {
            String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            if (!java.util.Set.of("jpg", "jpeg", "png", "webp").contains(extension)) {
                return Result.failure(400, "只支持 JPG、PNG、WebP 格式");
            }
        }

        OcrHealthResultVO result = ocrService.recognizeHealthImage(userId, file, ocrType);
        return Result.success(result);
    }

    /**
     * 体检报告识别
     */
    @PostMapping(value = "/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "体检报告识别", description = "识别体检报告图片，提取健康指标")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "识别成功"),
        @ApiResponse(responseCode = "400", description = "文件无效"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<OcrHealthResultVO> recognizeReport(
            @Parameter(description = "体检报告图片", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("体检报告OCR识别: userId={}, filename={}", userId, file.getOriginalFilename());

        if (file.isEmpty()) {
            return Result.failure(400, "文件不能为空");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.failure(400, "只能上传图片文件");
        }

        OcrHealthResultVO result = ocrService.recognizeReport(userId, file);
        return Result.success(result);
    }

    /**
     * 药品标签识别
     */
    @PostMapping(value = "/medicine", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "药品标签识别", description = "识别药品标签图片，提取药品信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "识别成功"),
        @ApiResponse(responseCode = "400", description = "文件无效"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<OcrHealthResultVO> recognizeMedicine(
            @Parameter(description = "药品标签图片", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("药品标签OCR识别: userId={}, filename={}", userId, file.getOriginalFilename());

        if (file.isEmpty()) {
            return Result.failure(400, "文件不能为空");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.failure(400, "只能上传图片文件");
        }

        OcrHealthResultVO result = ocrService.recognizeMedicine(userId, file);
        return Result.success(result);
    }

    /**
     * 营养标签识别
     */
    @PostMapping(value = "/nutrition", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "营养标签识别", description = "识别食品营养标签图片，提取营养成分信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "识别成功"),
        @ApiResponse(responseCode = "400", description = "文件无效"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<OcrHealthResultVO> recognizeNutrition(
            @Parameter(description = "营养标签图片", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("营养标签OCR识别: userId={}, filename={}", userId, file.getOriginalFilename());

        if (file.isEmpty()) {
            return Result.failure(400, "文件不能为空");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.failure(400, "只能上传图片文件");
        }

        OcrHealthResultVO result = ocrService.recognizeNutrition(userId, file);
        return Result.success(result);
    }

    /**
     * 确认并保存识别的指标
     */
    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "确认录入", description = "确认OCR识别结果并保存到数据库")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "保存成功"),
        @ApiResponse(responseCode = "400", description = "请求参数无效"),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "403", description = "无权操作此记录")
    })
    public Result<List<Long>> confirmMetrics(@RequestBody @Valid OcrConfirmRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("确认OCR指标: userId={}, ocrRecordId={}, metricsCount={}",
                userId, request.getOcrRecordId(), request.getMetrics().size());

        List<Long> savedIds = ocrService.confirmMetrics(userId, request);
        return Result.success(savedIds);
    }

    /**
     * 获取OCR识别历史
     */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取OCR历史", description = "获取当前用户的OCR识别历史记录列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<PageResult<OcrHistoryVO>> getHistory(
            @Parameter(description = "页码，从1开始")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量")
            @RequestParam(defaultValue = "10") int size) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("获取OCR历史: userId={}, page={}, size={}", userId, page, size);

        PageResult<OcrHistoryVO> history = ocrService.getHistory(userId, page, size);
        return Result.success(history);
    }

    /**
     * 获取OCR记录详情
     */
    @GetMapping("/history/{recordId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取OCR记录详情", description = "获取指定OCR记录的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "403", description = "无权访问此记录"),
        @ApiResponse(responseCode = "404", description = "记录不存在")
    })
    public Result<OcrHealthResultVO> getRecordDetail(
            @Parameter(description = "OCR记录ID", required = true)
            @PathVariable Long recordId) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("获取OCR记录详情: userId={}, recordId={}", userId, recordId);

        OcrHealthResultVO result = ocrService.getRecordDetail(userId, recordId);
        return Result.success(result);
    }

    /**
     * 删除OCR记录
     */
    @DeleteMapping("/history/{recordId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "删除OCR记录", description = "删除指定的OCR识别记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "403", description = "无权删除此记录"),
        @ApiResponse(responseCode = "404", description = "记录不存在")
    })
    public Result<Boolean> deleteRecord(
            @Parameter(description = "OCR记录ID", required = true)
            @PathVariable Long recordId) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("删除OCR记录: userId={}, recordId={}", userId, recordId);

        boolean deleted = ocrService.deleteRecord(userId, recordId);
        return Result.success(deleted);
    }
}