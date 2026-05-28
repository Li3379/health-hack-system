package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.entity.UserReminder;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.UserReminderMapper;
import com.hhs.service.UserReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Reminder Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserReminderServiceImpl implements UserReminderService {

    private final UserReminderMapper userReminderMapper;

    @Override
    @Transactional(timeout = 30)
    public UserReminder createReminder(Long userId, String title, String description,
                                       String reminderType, String cronExpression) {
        UserReminder reminder = new UserReminder();
        reminder.setUserId(userId);
        reminder.setTitle(title);
        reminder.setDescription(description);
        reminder.setReminderType(reminderType);
        reminder.setCronExpression(cronExpression);
        reminder.setIsActive(true);

        userReminderMapper.insert(reminder);
        log.info("Reminder created: id={}, userId={}, type={}", reminder.getId(), userId, reminderType);
        return reminder;
    }

    @Override
    public List<UserReminder> getUserReminders(Long userId) {
        LambdaQueryWrapper<UserReminder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserReminder::getUserId, userId)
               .orderByDesc(UserReminder::getCreatedAt);
        return userReminderMapper.selectList(wrapper);
    }

    @Override
    @Transactional(timeout = 30)
    public UserReminder toggleReminder(Long id, Long userId) {
        UserReminder reminder = userReminderMapper.selectById(id);
        if (reminder == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "提醒不存在");
        }
        if (!reminder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权修改他人提醒");
        }

        reminder.setIsActive(!reminder.getIsActive());
        userReminderMapper.updateById(reminder);
        log.info("Reminder toggled: id={}, isActive={}", id, reminder.getIsActive());
        return reminder;
    }

    @Override
    @Transactional(timeout = 30)
    public void deleteReminder(Long id, Long userId) {
        UserReminder reminder = userReminderMapper.selectById(id);
        if (reminder == null) {
            log.info("Reminder already deleted or not found: id={}", id);
            return;
        }
        if (!reminder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权删除他人提醒");
        }

        userReminderMapper.deleteById(id);
        log.info("Reminder deleted: id={}, userId={}", id, userId);
    }

    @Override
    public List<UserReminder> getActiveRemindersDue() {
        LambdaQueryWrapper<UserReminder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserReminder::getIsActive, true);
        return userReminderMapper.selectList(wrapper);
    }
}
