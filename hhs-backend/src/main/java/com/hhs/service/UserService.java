package com.hhs.service;

import com.hhs.dto.ChangePasswordRequest;
import com.hhs.dto.LoginRequest;
import com.hhs.dto.RegisterRequest;
import com.hhs.dto.UpdateProfileRequest;
import com.hhs.vo.AuthResponse;
import com.hhs.vo.UserProfileVO;
import com.hhs.vo.UserPublicProfileVO;

/**
 * User Service
 * Handles user registration, authentication, profile management, and password changes
 */
public interface UserService {

    /**
     * Register a new user account
     *
     * @param request Registration request containing username, password, nickname, and email
     * @throws BusinessException if username already exists or email is already registered
     */
    void register(RegisterRequest request);

    /**
     * Authenticate user and generate JWT token
     *
     * @param request Login request containing username and password
     * @return AuthResponse containing JWT token and user information
     * @throws BusinessException if credentials are invalid or user is disabled
     */
    AuthResponse login(LoginRequest request);

    /**
     * Get current user's full profile including sensitive information
     *
     * @param userId User ID
     * @return UserProfileVO containing complete user profile
     * @throws BusinessException if user not found
     */
    UserProfileVO getUserProfile(Long userId);

    /**
     * Get user's public profile with limited information
     *
     * @param userId User ID
     * @return UserPublicProfileVO containing public profile (id, nickname, avatar)
     * @throws BusinessException if user not found or disabled
     */
    UserPublicProfileVO getPublicProfile(Long userId);

    /**
     * Update user's profile information
     *
     * @param userId User ID
     * @param request Update request containing email, nickname, avatar, phone
     * @throws BusinessException if user not found or email already in use
     */
    void updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * Change user's password
     *
     * @param userId User ID
     * @param request Change password request containing old and new passwords
     * @throws BusinessException if user not found or old password is incorrect
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
