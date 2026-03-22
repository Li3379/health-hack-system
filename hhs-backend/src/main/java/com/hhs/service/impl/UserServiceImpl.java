package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.dto.ChangePasswordRequest;
import com.hhs.dto.LoginRequest;
import com.hhs.dto.RegisterRequest;
import com.hhs.dto.UpdateProfileRequest;
import com.hhs.entity.User;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.UserMapper;
import com.hhs.security.JwtUtil;
import com.hhs.service.UserService;
import com.hhs.vo.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Register a new user account
     *
     * @param request Registration request containing username, password, nickname, and email
     * @throws BusinessException if username already exists or email is already registered
     */
    @Override
    @Transactional(timeout = 30)
    public void register(RegisterRequest request) {
        log.debug("Checking if user exists by username: username={}", request.username());
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, request.username()).last("LIMIT 1");
        if (userMapper.selectOne(usernameWrapper) != null) {
            throw new BusinessException(ErrorCode.USER_CONFLICT, "用户名已存在");
        }
        if (StringUtils.hasText(request.email())) {
            log.debug("Counting users by email: email={}", request.email());
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, request.email());
            long count = userMapper.selectCount(emailWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.USER_CONFLICT, "邮箱已被注册");
            }
        }
        User user = new User();
        user.setUsername(request.username());
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setStatus(1);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAvatar("https://api.dicebear.com/7.x/identicon/svg?seed=" + request.username());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    /**
     * Authenticate user and generate JWT token
     *
     * @param request Login request containing username and password
     * @return AuthResponse containing JWT token and user information
     * @throws BusinessException if credentials are invalid or user is disabled
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.debug("Finding user by username: username={}", request.username());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.username()).last("LIMIT 1");
        User user = userMapper.selectOne(wrapper);
        if (user == null || user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_INVALID, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_INVALID, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return AuthResponse.builder()
                .token(token)
                .user(toVO(user))
                .build();
    }

    /**
     * Get current user's full profile including sensitive information
     *
     * @param userId User ID
     * @return UserProfileVO containing complete user profile
     * @throws BusinessException if user not found
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    @Cacheable(value = "users", key = "#userId")
    public UserProfileVO getUserProfile(Long userId) {
        log.debug("Finding user by id: id={}", userId);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return UserProfileVO.builder()
                .profile(toVO(user))
                .build();
    }

    /**
     * Get user's public profile with limited information
     *
     * @param userId User ID
     * @return UserPublicProfileVO containing public profile (id, nickname, avatar)
     * @throws BusinessException if user not found or disabled
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    @Cacheable(value = "users", key = "#userId")
    public UserPublicProfileVO getPublicProfile(Long userId) {
        log.debug("Finding user by id: id={}", userId);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return UserPublicProfileVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    /**
     * Update user's profile information
     *
     * @param userId User ID
     * @param request Update request containing email, nickname, avatar, phone
     * @throws BusinessException if user not found or email already in use
     */
    @Override
    @Transactional(timeout = 30)
    @CacheEvict(value = "users", allEntries = true)
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        log.debug("Finding user by id for update: id={}", userId);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (StringUtils.hasText(request.email()) && !Objects.equals(request.email(), user.getEmail())) {
            log.debug("Counting users by email excluding id: email={}, excludeId={}", request.email(), userId);
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, request.email()).ne(User::getId, userId);
            long count = userMapper.selectCount(emailWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.USER_CONFLICT, "邮箱已被使用");
            }
            user.setEmail(request.email());
        }
        if (StringUtils.hasText(request.nickname())) {
            user.setNickname(request.nickname());
        }
        if (StringUtils.hasText(request.avatar())) {
            user.setAvatar(request.avatar());
        }
        if (StringUtils.hasText(request.phone())) {
            user.setPhone(request.phone());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * Change user's password
     *
     * @param userId User ID
     * @param request Change password request containing old and new passwords
     * @throws BusinessException if user not found or old password is incorrect
     */
    @Override
    @Transactional(timeout = 30)
    @CacheEvict(value = "users", allEntries = true)
    public void changePassword(Long userId, ChangePasswordRequest request) {
        log.debug("Finding user by id for password change: id={}", userId);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private UserVO toVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }
}
