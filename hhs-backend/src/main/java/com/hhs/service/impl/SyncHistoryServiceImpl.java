package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.entity.SyncHistory;
import com.hhs.mapper.SyncHistoryMapper;
import com.hhs.service.SyncHistoryService;
import com.hhs.vo.SyncHistoryVO;
import com.hhs.vo.SyncResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 设备同步历史服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncHistoryServiceImpl implements SyncHistoryService {

    private final SyncHistoryMapper syncHistoryMapper;

    @Override
    @Transactional
    public SyncHistory record(SyncHistory syncHistory) {
        if (syncHistory.getCreateTime() == null) {
            syncHistory.setCreateTime(LocalDateTime.now());
        }
        syncHistoryMapper.insert(syncHistory);
        return syncHistory;
    }

    @Override
    public Page<SyncHistoryVO> getHistory(Long userId, Integer page, Integer size) {
        Page<SyncHistory> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SyncHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncHistory::getUserId, userId);
        queryWrapper.orderByDesc(SyncHistory::getCreateTime);

        Page<SyncHistory> result = syncHistoryMapper.selectPage(pageParam, queryWrapper);

        Page<SyncHistoryVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toVO)
                .toList());

        return voPage;
    }

    @Override
    public SyncHistory getLastSync(Long userId, String platform) {
        LambdaQueryWrapper<SyncHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncHistory::getUserId, userId);
        queryWrapper.eq(SyncHistory::getPlatform, platform);
        queryWrapper.orderByDesc(SyncHistory::getCreateTime);
        queryWrapper.last("LIMIT 1");
        return syncHistoryMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional
    public SyncResultVO createSuccessRecord(Long userId, String platform, String syncType,
                                            int metricsCount, int durationMs) {
        SyncHistory history = new SyncHistory();
        history.setUserId(userId);
        history.setPlatform(platform);
        history.setSyncType(syncType);
        history.setMetricsCount(metricsCount);
        history.setStatus("success");
        history.setStartTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000L));
        history.setEndTime(LocalDateTime.now());
        history.setDurationMs(durationMs);

        syncHistoryMapper.insert(history);

        return SyncResultVO.success(platform, metricsCount, durationMs);
    }

    @Override
    @Transactional
    public SyncResultVO createFailedRecord(Long userId, String platform, String syncType,
                                           String errorMessage, int durationMs) {
        SyncHistory history = new SyncHistory();
        history.setUserId(userId);
        history.setPlatform(platform);
        history.setSyncType(syncType);
        history.setMetricsCount(0);
        history.setStatus("failed");
        history.setErrorMessage(errorMessage);
        history.setStartTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000L));
        history.setEndTime(LocalDateTime.now());
        history.setDurationMs(durationMs);

        syncHistoryMapper.insert(history);

        return SyncResultVO.failed(platform, errorMessage, durationMs);
    }

    private SyncHistoryVO toVO(SyncHistory history) {
        SyncHistoryVO vo = new SyncHistoryVO();
        vo.setId(history.getId());
        vo.setPlatform(history.getPlatform());
        vo.setPlatformName(getPlatformName(history.getPlatform()));
        vo.setSyncType(history.getSyncType());
        vo.setSyncTypeName(getSyncTypeName(history.getSyncType()));
        vo.setMetricsCount(history.getMetricsCount());
        vo.setStatus(history.getStatus());
        vo.setStatusName(getStatusName(history.getStatus()));
        vo.setErrorMessage(history.getErrorMessage());
        vo.setSyncTime(history.getEndTime() != null ? history.getEndTime() : history.getCreateTime());
        vo.setDurationMs(history.getDurationMs());
        return vo;
    }

    private String getPlatformName(String platform) {
        return switch (platform) {
            case "huawei" -> "华为运动健康";
            case "xiaomi" -> "小米运动";
            case "wechat" -> "微信运动";
            case "apple" -> "Apple Health";
            default -> platform;
        };
    }

    private String getSyncTypeName(String syncType) {
        return switch (syncType) {
            case "manual" -> "手动同步";
            case "scheduled" -> "定时同步";
            default -> syncType;
        };
    }

    private String getStatusName(String status) {
        return switch (status) {
            case "success" -> "成功";
            case "partial" -> "部分成功";
            case "failed" -> "失败";
            default -> status;
        };
    }
}