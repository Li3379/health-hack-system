package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.dto.UserThresholdRequest;
import com.hhs.entity.UserThreshold;
import com.hhs.exception.BusinessException;
import com.hhs.common.constant.ErrorCode;
import com.hhs.mapper.UserThresholdMapper;
import com.hhs.service.UserThresholdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Threshold Service Implementation
 * Direct Mapper injection pattern (no Repository layer)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserThresholdServiceImpl implements UserThresholdService {

    private final UserThresholdMapper userThresholdMapper; // Direct Mapper injection

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Page<UserThreshold> list(Integer page, Integer size, Long userId) {
        Page<UserThreshold> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<UserThreshold> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(UserThreshold::getUserId, userId);
        }
        wrapper.orderByDesc(UserThreshold::getUpdatedAt);
        return userThresholdMapper.selectPage(pageParam, wrapper);
    }

    @Override
    @Transactional(timeout = 30)
    public UserThreshold create(UserThresholdRequest request) {
        log.info("Creating threshold for user: {}, metricKey: {}", request.getUserId(), request.getMetricKey());

        // Check if threshold already exists for this user and metric key
        UserThreshold existing = getByUserAndMetricKey(request.getUserId(), request.getMetricKey());
        if (existing != null) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "Threshold already exists for this metric");
        }

        UserThreshold threshold = new UserThreshold();
        BeanUtils.copyProperties(request, threshold);
        threshold.setCreatedAt(LocalDateTime.now());
        threshold.setUpdatedAt(LocalDateTime.now());

        userThresholdMapper.insert(threshold);

        log.info("Threshold created: id={}, userId={}, metricKey={}",
                threshold.getId(), request.getUserId(), request.getMetricKey());
        return threshold;
    }

    @Override
    @Transactional(timeout = 30)
    public UserThreshold update(Long id, UserThresholdRequest request) {
        log.info("Updating threshold id: {}", id);

        UserThreshold threshold = userThresholdMapper.selectById(id);
        if (threshold == null) {
            throw new BusinessException(ErrorCode.HEALTH_THRESHOLD_INVALID, "Threshold not found");
        }

        // Check if updating to a different metric key that already exists
        if (!threshold.getMetricKey().equals(request.getMetricKey())) {
            UserThreshold existing = getByUserAndMetricKey(request.getUserId(), request.getMetricKey());
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "Threshold already exists for this metric");
            }
        }

        BeanUtils.copyProperties(request, threshold, "id", "createdAt");
        threshold.setUpdatedAt(LocalDateTime.now());

        userThresholdMapper.updateById(threshold);

        log.info("Threshold updated: id={}, userId={}, metricKey={}",
                id, request.getUserId(), request.getMetricKey());
        return threshold;
    }

    @Override
    @Transactional(timeout = 30)
    public void delete(Long id) {
        log.info("Deleting threshold id: {}", id);

        UserThreshold threshold = userThresholdMapper.selectById(id);
        if (threshold == null) {
            log.warn("Threshold not found for deletion: id={}", id);
            return;
        }

        userThresholdMapper.deleteById(id);
        log.info("Threshold deleted: id={}, userId={}", id, threshold.getUserId());
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<UserThreshold> getByUserId(Long userId) {
        return userThresholdMapper.getByUserId(userId);
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public UserThreshold getByUserAndMetricKey(Long userId, String metricKey) {
        LambdaQueryWrapper<UserThreshold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserThreshold::getUserId, userId);
        wrapper.eq(UserThreshold::getMetricKey, metricKey);
        return userThresholdMapper.selectOne(wrapper);
    }
}
