package com.hhs.service;

import com.hhs.common.PageResult;
import com.hhs.dto.MetricParseRequest;
import com.hhs.entity.AiParseHistory;
import com.hhs.vo.AiParseHistoryVO;
import com.hhs.vo.ConfirmSaveResult;
import com.hhs.vo.MetricParseResultVO;

import java.util.List;

/**
 * AI解析服务接口
 */
public interface AiParseService {

    /**
     * 解析用户输入的健康指标
     *
     * @param userId  用户ID
     * @param request 解析请求
     * @return 解析结果
     */
    MetricParseResultVO parseMetrics(Long userId, MetricParseRequest request);

    /**
     * 确认并保存解析结果
     *
     * <p>返回保存的 ID 与失败的指标名，而不是只返回 ID：只返回 ID 时，部分失败与全部成功在
     * 调用方看来完全一样（都会提示「成功录入 N 个」），而记录已被置为已确认，失败的指标既
     * 不会出现在统计里，也无法再补录。
     *
     * @param userId        用户ID
     * @param parseHistoryId 解析历史ID
     * @param metrics       确认的指标列表
     * @return 保存的指标 ID 与未能保存的指标名
     */
    ConfirmSaveResult confirmMetrics(Long userId, Long parseHistoryId, List<MetricParseResultVO.ParsedMetric> metrics);

    /**
     * 获取解析历史（简化版）
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 解析历史列表
     */
    List<AiParseHistory> getParseHistory(Long userId, int limit);

    /**
     * 获取解析历史（分页）
     *
     * @param userId 用户ID
     * @param page   页码（从1开始）
     * @param size   每页数量
     * @return 分页解析历史
     */
    PageResult<AiParseHistoryVO> getHistoryPage(Long userId, int page, int size);

    /**
     * 获取解析记录详情
     *
     * @param userId 用户ID
     * @param id     解析历史ID
     * @return 解析结果
     */
    MetricParseResultVO getHistoryDetail(Long userId, Long id);

    /**
     * 删除解析历史记录
     *
     * @param userId 用户ID
     * @param id     解析历史ID
     * @return 是否删除成功
     */
    boolean deleteHistory(Long userId, Long id);

    /**
     * 保存解析历史
     *
     * @param parseHistory 解析历史
     * @return 保存后的历史
     */
    AiParseHistory saveParseHistory(AiParseHistory parseHistory);

    /**
     * 标记解析历史为已确认
     *
     * @param id        解析历史ID
     * @param metricIds 保存的指标ID列表
     */
    void markAsConfirmed(Long id, List<Long> metricIds);
}