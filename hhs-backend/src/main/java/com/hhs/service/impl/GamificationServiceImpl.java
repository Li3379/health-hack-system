package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.*;
import com.hhs.mapper.*;
import com.hhs.service.GamificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 游戏化服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GamificationServiceImpl implements GamificationService {

    private final UserStreakMapper userStreakMapper;
    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final UserPointMapper userPointMapper;
    private final HealthMetricMapper healthMetricMapper;
    private final AIConversationMapper aiConversationMapper;
    private final HealthScoreHistoryMapper healthScoreHistoryMapper;

    @Override
    @Transactional(timeout = 30)
    public void updateStreak(Long userId) {
        LocalDate today = LocalDate.now();
        UserStreak streak = userStreakMapper.selectOne(
                new LambdaQueryWrapper<UserStreak>()
                        .eq(UserStreak::getUserId, userId)
                        .last("LIMIT 1")
        );

        if (streak == null) {
            // 首次记录
            streak = new UserStreak();
            streak.setUserId(userId);
            streak.setCurrentStreak(1);
            streak.setLongestStreak(1);
            streak.setLastActivityDate(today);
            userStreakMapper.insertOrUpdate(streak);
            log.debug("Created streak for user {}: streak=1", userId);
            return;
        }

        LocalDate lastDate = streak.getLastActivityDate();
        if (today.equals(lastDate)) {
            // 今天已记录，无需操作
            log.debug("Streak already updated today for user {}", userId);
            return;
        }

        if (lastDate != null && lastDate.plusDays(1).equals(today)) {
            // 昨天有记录，连续天数+1
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        } else {
            // 断更，重置为1
            streak.setCurrentStreak(1);
        }

        // 更新最长连续天数
        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
            streak.setLongestStreak(streak.getCurrentStreak());
        }
        streak.setLastActivityDate(today);
        userStreakMapper.insertOrUpdate(streak);
        log.debug("Updated streak for user {}: current={}, longest={}",
                userId, streak.getCurrentStreak(), streak.getLongestStreak());
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public UserStreak getStreak(Long userId) {
        UserStreak streak = userStreakMapper.selectOne(
                new LambdaQueryWrapper<UserStreak>()
                        .eq(UserStreak::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (streak == null) {
            // 返回空对象，避免前端空指针
            streak = new UserStreak();
            streak.setUserId(userId);
            streak.setCurrentStreak(0);
            streak.setLongestStreak(0);
        }
        return streak;
    }

    @Override
    @Transactional(timeout = 30)
    public void checkAndUnlockAchievements(Long userId) {
        List<Achievement> allAchievements = achievementMapper.selectList(null);
        if (allAchievements.isEmpty()) {
            return;
        }

        // 查询用户已解锁的成就ID
        List<UserAchievement> unlocked = userAchievementMapper.selectList(
                new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId)
        );
        java.util.Set<Long> unlockedIds = new java.util.HashSet<>();
        for (UserAchievement ua : unlocked) {
            unlockedIds.add(ua.getAchievementId());
        }

        for (Achievement achievement : allAchievements) {
            if (unlockedIds.contains(achievement.getId())) {
                continue;
            }
            boolean earned = checkAchievementCondition(userId, achievement.getCode());
            if (earned) {
                unlockAchievement(userId, achievement);
            }
        }
    }

    private boolean checkAchievementCondition(Long userId, String code) {
        switch (code) {
            case "first_record":
                return checkFirstRecord(userId);
            case "streak_7":
                return checkStreak(userId, 7);
            case "streak_30":
                return checkStreak(userId, 30);
            case "first_ai_chat":
                return checkFirstAiChat(userId);
            case "score_90":
                return checkScore90(userId);
            default:
                log.warn("Unknown achievement code: {}", code);
                return false;
        }
    }

    private boolean checkFirstRecord(Long userId) {
        long count = healthMetricMapper.selectCount(
                new LambdaQueryWrapper<HealthMetric>()
                        .eq(HealthMetric::getUserId, userId)
                        .last("LIMIT 1")
        );
        return count > 0;
    }

    private boolean checkStreak(Long userId, int days) {
        UserStreak streak = userStreakMapper.selectOne(
                new LambdaQueryWrapper<UserStreak>()
                        .eq(UserStreak::getUserId, userId)
                        .last("LIMIT 1")
        );
        return streak != null && streak.getCurrentStreak() != null && streak.getCurrentStreak() >= days;
    }

    private boolean checkFirstAiChat(Long userId) {
        long count = aiConversationMapper.selectCount(
                new LambdaQueryWrapper<AIConversation>()
                        .eq(AIConversation::getUserId, userId)
                        .last("LIMIT 1")
        );
        return count > 0;
    }

    private boolean checkScore90(Long userId) {
        HealthScoreHistory latest = healthScoreHistoryMapper.selectOne(
                new LambdaQueryWrapper<HealthScoreHistory>()
                        .eq(HealthScoreHistory::getUserId, userId)
                        .orderByDesc(HealthScoreHistory::getScoreDate)
                        .last("LIMIT 1")
        );
        return latest != null && latest.getOverallScore() != null
                && latest.getOverallScore().compareTo(BigDecimal.valueOf(90)) >= 0;
    }

    private void unlockAchievement(Long userId, Achievement achievement) {
        // 检查是否已被其他线程解锁（防并发）
        Long exists = userAchievementMapper.selectCount(
                new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId)
                        .eq(UserAchievement::getAchievementId, achievement.getId())
        );
        if (exists > 0) {
            return;
        }

        UserAchievement ua = new UserAchievement();
        ua.setUserId(userId);
        ua.setAchievementId(achievement.getId());
        ua.setUnlockedAt(LocalDateTime.now());
        userAchievementMapper.insert(ua);

        // 奖励积分
        if (achievement.getPoints() != null && achievement.getPoints() > 0) {
            addPoints(userId, achievement.getPoints(), "achievement", achievement.getId());
        }
        log.info("User {} unlocked achievement: {} (+{} pts)",
                userId, achievement.getCode(), achievement.getPoints());
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<Achievement> getAllAchievements() {
        return achievementMapper.selectList(null);
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<Achievement> getUserAchievements(Long userId) {
        List<UserAchievement> userAchievements = userAchievementMapper.selectList(
                new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId)
        );
        if (userAchievements.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.List<Long> achievementIds = new java.util.ArrayList<>();
        for (UserAchievement ua : userAchievements) {
            achievementIds.add(ua.getAchievementId());
        }
        return achievementMapper.selectBatchIds(achievementIds);
    }

    @Override
    @Transactional(timeout = 30)
    public void addPoints(Long userId, int points, String source, Long referenceId) {
        if (points <= 0) {
            return;
        }
        UserPoint userPoint = new UserPoint();
        userPoint.setUserId(userId);
        userPoint.setPoints(points);
        userPoint.setSource(source);
        userPoint.setReferenceId(referenceId);
        userPoint.setCreatedAt(LocalDateTime.now());
        userPointMapper.insert(userPoint);
        log.debug("Added {} points to user {} from source={} ref={}", points, userId, source, referenceId);
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public int getUserPoints(Long userId) {
        List<UserPoint> records = userPointMapper.selectList(
                new LambdaQueryWrapper<UserPoint>()
                        .eq(UserPoint::getUserId, userId)
        );
        int total = 0;
        for (UserPoint record : records) {
            total += record.getPoints();
        }
        return total;
    }
}
