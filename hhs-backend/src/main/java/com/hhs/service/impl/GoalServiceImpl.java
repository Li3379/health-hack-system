package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.entity.GoalProgress;
import com.hhs.entity.HealthGoal;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.GoalProgressMapper;
import com.hhs.mapper.HealthGoalMapper;
import com.hhs.service.GoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Goal Service Implementation
 * Manages health goals with progress tracking and auto-completion
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalServiceImpl implements GoalService {

    private final HealthGoalMapper healthGoalMapper;
    private final GoalProgressMapper goalProgressMapper;

    @Override
    @Transactional(timeout = 30)
    public HealthGoal createGoal(Long userId, String title, String description,
                                 String metricKey, double targetValue, String unit,
                                 LocalDate startDate, LocalDate endDate) {
        log.info("Creating health goal for user: {}, title: {}", userId, title);

        HealthGoal goal = HealthGoal.builder()
                .userId(userId)
                .title(title)
                .description(description)
                .metricKey(metricKey)
                .targetValue(targetValue)
                .currentValue(0.0)
                .unit(unit)
                .status("active")
                .startDate(startDate)
                .endDate(endDate)
                .build();

        healthGoalMapper.insert(goal);

        log.info("Health goal created: id={}, userId={}", goal.getId(), userId);
        return goal;
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<HealthGoal> getUserGoals(Long userId) {
        LambdaQueryWrapper<HealthGoal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthGoal::getUserId, userId);
        wrapper.orderByDesc(HealthGoal::getCreatedAt);
        return healthGoalMapper.selectList(wrapper);
    }

    @Override
    @Transactional(timeout = 30)
    public HealthGoal updateGoal(Long id, Long userId, String title, String description, LocalDate endDate) {
        log.info("Updating health goal: id={}, userId={}", id, userId);

        HealthGoal goal = healthGoalMapper.selectById(id);
        if (goal == null) {
            throw new BusinessException(ErrorCode.GOAL_NOT_FOUND);
        }
        if (!goal.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.GOAL_FORBIDDEN, "无权修改他人目标");
        }

        if (title != null) {
            goal.setTitle(title);
        }
        if (description != null) {
            goal.setDescription(description);
        }
        if (endDate != null) {
            goal.setEndDate(endDate);
        }

        healthGoalMapper.updateById(goal);

        log.info("Health goal updated: id={}", id);
        return goal;
    }

    @Override
    @Transactional(timeout = 30)
    public GoalProgress addProgress(Long goalId, Long userId, double value, String note) {
        log.info("Adding progress to goal: goalId={}, userId={}, value={}", goalId, userId, value);

        HealthGoal goal = healthGoalMapper.selectById(goalId);
        if (goal == null) {
            throw new BusinessException(ErrorCode.GOAL_NOT_FOUND);
        }
        if (!goal.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.GOAL_FORBIDDEN, "无权操作他人目标");
        }

        // Create progress entry
        GoalProgress progress = GoalProgress.builder()
                .goalId(goalId)
                .value(value)
                .note(note)
                .recordedAt(LocalDateTime.now())
                .build();
        goalProgressMapper.insert(progress);

        // Update current value on the goal
        goal.setCurrentValue(goal.getCurrentValue() + value);
        healthGoalMapper.updateById(goal);

        // Check for auto-completion
        checkGoalCompletion(goal);

        log.info("Progress added: progressId={}, goalId={}, newCurrentValue={}",
                progress.getId(), goalId, goal.getCurrentValue());
        return progress;
    }

    @Override
    @Transactional(timeout = 30)
    public void checkGoalCompletion(HealthGoal goal) {
        if ("active".equals(goal.getStatus())
                && goal.getCurrentValue() >= goal.getTargetValue()) {
            goal.setStatus("completed");
            goal.setCompletedAt(LocalDateTime.now());
            healthGoalMapper.updateById(goal);
            log.info("Goal auto-completed: id={}, currentValue={}, targetValue={}",
                    goal.getId(), goal.getCurrentValue(), goal.getTargetValue());
        }
    }
}
