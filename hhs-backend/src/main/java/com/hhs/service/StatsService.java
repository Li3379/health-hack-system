package com.hhs.service;

import com.hhs.vo.DashboardSummaryVO;
import com.hhs.vo.MonthlyStatsVO;
import com.hhs.vo.TodayStatsVO;
import com.hhs.vo.TrendDataVO;
import com.hhs.vo.WeeklyStatsVO;

import java.util.List;

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

    /**
     * 获取本周统计数据（含周同比）
     *
     * @param userId 用户ID
     * @return 本周统计数据
     */
    WeeklyStatsVO getWeeklyStats(Long userId);

    /**
     * 获取本月统计数据（含月同比）
     *
     * @param userId 用户ID
     * @return 本月统计数据
     */
    MonthlyStatsVO getMonthlyStats(Long userId);

    /**
     * 获取指标趋势数据（折线图/迷你图）
     *
     * @param userId   用户ID
     * @param metricKey 指标类型
     * @param days     天数
     * @return 趋势数据列表
     */
    List<TrendDataVO> getTrends(Long userId, String metricKey, int days);

    /**
     * 获取仪表盘汇总数据
     *
     * @param userId 用户ID
     * @return 仪表盘汇总
     */
    DashboardSummaryVO getDashboardSummary(Long userId);
}