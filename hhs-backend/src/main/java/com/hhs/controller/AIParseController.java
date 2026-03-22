package com.hhs.controller;

import com.hhs.common.PageResult;
import com.hhs.common.Result;
import com.hhs.common.constant.ErrorCode;
import com.hhs.component.AIRateLimiter;
import com.hhs.dto.ConfirmMetricsRequest;
import com.hhs.dto.MetricParseRequest;
import com.hhs.exception.BusinessException;
import com.hhs.security.SecurityUtils;
import com.hhs.service.AiParseService;
import com.hhs.vo.AiParseHistoryVO;
import com.hhs.vo.MetricParseResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI智能录入控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI智能录入", description = "AI解析健康指标接口")
public class AIParseController {

    private final AiParseService aiParseService;
    private final AIRateLimiter rateLimiter;

    /**
     * AI智能解析健康指标
     */
    @PostMapping("/parse-metrics")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "AI解析健康指标", description = "从用户输入的文本或语音中解析健康指标（每天限20次）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "解析成功"),
        @ApiResponse(responseCode = "400", description = "输入内容无效"),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "429", description = "请求过于频繁")
    })
    public Result<MetricParseResultVO> parseMetrics(@RequestBody @Valid MetricParseRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 检查速率限制
        if (!rateLimiter.checkLimit(userId)) {
            int remaining = rateLimiter.getRemainingCount(userId);
            log.warn("AI解析请求超过限制: userId={}, remaining={}", userId, remaining);
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED,
                    "今日AI解析次数已用完，请明天再试");
        }

        MetricParseResultVO result = aiParseService.parseMetrics(userId, request);

        // 返回剩余次数
        int remaining = rateLimiter.getRemainingCount(userId);
        result.setRemainingCount(remaining);

        return Result.success(result);
    }

    /**
     * 确认并保存解析结果
     */
    @PostMapping("/confirm-metrics")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "确认录入", description = "确认AI解析结果并保存到数据库")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "保存成功"),
        @ApiResponse(responseCode = "400", description = "请求参数无效或已确认"),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "403", description = "无权操作此记录"),
        @ApiResponse(responseCode = "404", description = "解析记录不存在")
    })
    public Result<List<Long>> confirmMetrics(@RequestBody @Valid ConfirmMetricsRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 转换为VO对象
        List<MetricParseResultVO.ParsedMetric> metrics = request.getMetrics().stream()
                .map(this::mapToParsedMetric)
                .collect(Collectors.toList());

        List<Long> metricIds = aiParseService.confirmMetrics(
                userId,
                request.getParseHistoryId(),
                metrics
        );

        return Result.success(metricIds);
    }

    /**
     * 获取AI解析剩余次数
     */
    @GetMapping("/parse-metrics/remaining")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取剩余次数", description = "获取今日AI解析剩余次数")
    public Result<Integer> getRemainingCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        int remaining = rateLimiter.getRemainingCount(userId);
        return Result.success(remaining);
    }

    /**
     * 获取AI解析历史
     */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取AI解析历史", description = "获取当前用户的AI解析历史记录列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<PageResult<AiParseHistoryVO>> getHistory(
            @Parameter(description = "页码，从1开始")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量")
            @RequestParam(defaultValue = "10") int size) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("获取AI解析历史: userId={}, page={}, size={}", userId, page, size);

        PageResult<AiParseHistoryVO> history = aiParseService.getHistoryPage(userId, page, size);
        return Result.success(history);
    }

    /**
     * 获取AI解析记录详情
     */
    @GetMapping("/history/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取AI解析详情", description = "获取指定AI解析记录的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "403", description = "无权访问此记录"),
        @ApiResponse(responseCode = "404", description = "记录不存在")
    })
    public Result<MetricParseResultVO> getHistoryDetail(
            @Parameter(description = "解析记录ID", required = true)
            @PathVariable Long id) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("获取AI解析详情: userId={}, id={}", userId, id);

        MetricParseResultVO result = aiParseService.getHistoryDetail(userId, id);
        return Result.success(result);
    }

    /**
     * 删除AI解析记录
     */
    @DeleteMapping("/history/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "删除AI解析记录", description = "删除指定的AI解析历史记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "403", description = "无权删除此记录"),
        @ApiResponse(responseCode = "404", description = "记录不存在")
    })
    public Result<Boolean> deleteHistory(
            @Parameter(description = "解析记录ID", required = true)
            @PathVariable Long id) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("删除AI解析记录: userId={}, id={}", userId, id);

        boolean deleted = aiParseService.deleteHistory(userId, id);
        return Result.success(deleted);
    }

    /**
     * 将DTO转换为VO
     */
    private MetricParseResultVO.ParsedMetric mapToParsedMetric(ConfirmMetricsRequest.MetricItem item) {
        MetricParseResultVO.ParsedMetric metric = new MetricParseResultVO.ParsedMetric();
        metric.setMetricKey(item.getMetricKey());
        metric.setMetricName(item.getMetricName());
        metric.setValue(item.getValue());
        metric.setUnit(item.getUnit());
        metric.setCategory(item.getCategory());
        metric.setRecordDate(item.getRecordDate());
        metric.setSelected(item.getSelected() != null ? item.getSelected() : true);
        return metric;
    }
}