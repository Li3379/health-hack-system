package com.hhs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.entity.SyncHistory;
import com.hhs.vo.SyncHistoryVO;
import com.hhs.vo.SyncResultVO;

/**
 * 设备同步服务接口
 */
public interface SyncHistoryService {

    /**
     * 记录同步历史
     *
     * @param syncHistory 同步历史
     * @return 保存后的历史记录
     */
    SyncHistory record(SyncHistory syncHistory);

    /**
     * 获取用户同步历史
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页历史记录
     */
    Page<SyncHistoryVO> getHistory(Long userId, Integer page, Integer size);

    /**
     * 获取最近一次同步记录
     *
     * @param userId   用户ID
     * @param platform 平台标识
     * @return 同步历史
     */
    SyncHistory getLastSync(Long userId, String platform);

    /**
     * 创建成功同步记录
     *
     * @param userId       用户ID
     * @param platform     平台标识
     * @param syncType     同步类型
     * @param metricsCount 指标数量
     * @param durationMs   耗时
     * @return 同步结果
     */
    SyncResultVO createSuccessRecord(Long userId, String platform, String syncType,
                                     int metricsCount, int durationMs);

    /**
     * 创建失败同步记录
     *
     * @param userId       用户ID
     * @param platform     平台标识
     * @param syncType     同步类型
     * @param errorMessage 错误信息
     * @param durationMs   耗时
     * @return 同步结果
     */
    SyncResultVO createFailedRecord(Long userId, String platform, String syncType,
                                    String errorMessage, int durationMs);
}