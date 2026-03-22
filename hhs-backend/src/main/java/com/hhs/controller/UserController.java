package com.hhs.controller;

import com.hhs.common.Result;
import lombok.extern.slf4j.Slf4j;
import com.hhs.dto.ChangePasswordRequest;
import com.hhs.dto.LoginRequest;
import com.hhs.dto.RegisterRequest;
import com.hhs.dto.UpdateProfileRequest;
import com.hhs.security.SecurityUtils;
import com.hhs.service.UserService;
import com.hhs.vo.AuthResponse;
import com.hhs.vo.UserProfileVO;
import com.hhs.vo.UserPublicProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "用户模块", description = "用户认证、个人资料管理")
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户注册")
    @PostMapping("/auth/register")
    public Result<Void> register(@RequestBody @Valid RegisterRequest request) {
        log.info("User registration request: username={}", request.username());
        userService.register(request);
        return Result.success();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/auth/login")
    public Result<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        log.info("User login request: username={}", request.username());
        return Result.success(userService.login(request));
    }

    @Operation(summary = "获取个人资料")
    @GetMapping("/user/profile")
    public Result<UserProfileVO> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get profile request: userId={}", userId);
        return Result.success(userService.getUserProfile(userId));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/user/profile")
    public Result<Void> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Update profile request: userId={}", userId);
        userService.updateProfile(userId, request);
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/user/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Change password request: userId={}", userId);
        userService.changePassword(userId, request);
        return Result.success();
    }

    @Operation(summary = "查看用户公开信息")
    @GetMapping("/users/{id}")
    public Result<UserPublicProfileVO> getPublicProfile(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        log.debug("Get public profile request: userId={}", id);
        return Result.success(userService.getPublicProfile(id));
    }
}
