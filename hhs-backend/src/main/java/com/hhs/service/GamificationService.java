package com.hhs.service;

import com.hhs.entity.Achievement;
import com.hhs.entity.UserStreak;

import java.util.List;

/**
 * 游戏化服务
 * 管理用户连续记录、成就解锁和积分系统
 */
public interface GamificationService {

    /**
     * 更新用户连续记录天数。
     * 若 lastActivityDate 为昨天则连续天数+1，为今天则无操作，更早则重置为1。
     *
     * @param userId 用户ID
     */
    void updateStreak(Long userId);

    /**
     * 获取用户连续记录信息
     *
     * @param userId 用户ID
     * @return 用户连续记录，不存在则返回空对象
     */
    UserStreak getStreak(Long userId);

    /**
     * 检查并解锁用户成就
     *
     * @param userId 用户ID
     */
    void checkAndUnlockAchievements(Long userId);

    /**
     * 获取所有成就定义
     *
     * @return 成就列表
     */
    List<Achievement> getAllAchievements();

    /**
     * 获取用户已解锁的成就
     *
     * @param userId 用户ID
     * @return 已解锁成就列表
     */
    List<Achievement> getUserAchievements(Long userId);

    /**
     * 为用户增加积分
     *
     * @param userId      用户ID
     * @param points      积分值
     * @param source      积分来源（achievement|streak|health_record|ai_chat）
     * @param referenceId 关联业务ID
     */
    void addPoints(Long userId, int points, String source, Long referenceId);

    /**
     * 获取用户总积分
     *
     * @param userId 用户ID
     * @return 总积分
     */
    int getUserPoints(Long userId);
}
