package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthScoreHistory;
import com.hhs.mapper.HealthScoreHistoryMapper;
import com.hhs.service.ScoreHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Score History Service Implementation
 * Persists daily health score snapshots and provides history retrieval
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreHistoryServiceImpl implements ScoreHistoryService {

    private final HealthScoreHistoryMapper healthScoreHistoryMapper;

    private static final int DEFAULT_RETENTION_DAYS = 365;

    @Override
    public void saveScore(Long userId, HealthScoreHistory score) {
        try {
            int rows = healthScoreHistoryMapper.insertOrUpdate(score);
            log.debug("Saved score history for user {} on date {}, rows affected: {}", userId, score.getScoreDate(), rows);
        } catch (Exception e) {
            log.error("Failed to save score history for user {}", userId, e);
        }
    }

    @Override
    public List<HealthScoreHistory> getHistory(Long userId, int days) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        return healthScoreHistoryMapper.selectList(
                new LambdaQueryWrapper<HealthScoreHistory>()
                        .eq(HealthScoreHistory::getUserId, userId)
                        .ge(HealthScoreHistory::getScoreDate, fromDate)
                        .orderByAsc(HealthScoreHistory::getScoreDate)
        );
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldRecords() {
        cleanupOldRecords(DEFAULT_RETENTION_DAYS);
    }

    @Override
    public void cleanupOldRecords(int retentionDays) {
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
            int deleted = healthScoreHistoryMapper.delete(
                    new LambdaQueryWrapper<HealthScoreHistory>()
                            .lt(HealthScoreHistory::getScoreDate, cutoffDate)
            );
            log.info("Score history cleanup completed: {} records older than {} days deleted", deleted, retentionDays);
        } catch (Exception e) {
            log.error("Failed to cleanup old score history records", e);
        }
    }
}
