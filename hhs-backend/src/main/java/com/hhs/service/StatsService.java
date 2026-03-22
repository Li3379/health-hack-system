package com.hhs.service;

import com.hhs.vo.TodayStatsVO;

/**
 * 统计服务接口
 */
public interface StatsService {

    /**
     * 获取今日统计数据
     *
     * @param userId 用户ID
     * @return 今日统计数据
     */
    TodayStatsVO getTodayStats(Long userId);
}