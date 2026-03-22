package com.hhs.common.constant;

import lombok.Getter;

/**
 * Consolidated error code enumeration with internationalization support.
 * Provides consistent error codes and messages in Chinese and English.
 *
 * <p>Error codes are organized with module prefixes:
 * <ul>
 *   <li>AUTH_* - Authentication and authorization errors</li>
 *   <li>USER_* - User-related errors</li>
 *   <li>HEALTH_* - Health data errors</li>
 *   <li>AI_* - AI service errors</li>
 *   <li>SYSTEM_* - System and infrastructure errors</li>
 * </ul>
 *
 * <p>This enum consolidates all previous ErrorCode definitions:
 * - com.hhs.exception.ErrorCode
 * - com.hhs.common.ErrorCode
 */
@Getter
public enum ErrorCode {
    // ========================================
    // Authentication & Authorization (AUTH_*)
    // ========================================
    AUTH_FAILED(401, "auth.failed", "认证失败", "Authentication failed"),
    AUTH_FORBIDDEN(403, "auth.forbidden", "权限不足", "Authorization failed"),
    AUTH_UNAUTHORIZED(401, "auth.unauthorized", "未授权", "Unauthorized"),

    // ========================================
    // User errors (USER_*)
    // ========================================
    USER_NOT_FOUND(404, "user.not_found", "用户不存在", "User not found"),
    USER_ALREADY_EXISTS(409, "user.exists", "用户已存在", "User already exists"),
    USER_CONFLICT(409, "user.conflict", "用户信息冲突", "User conflict"),
    USER_PASSWORD_INVALID(400, "user.password_invalid", "密码不正确", "Invalid password"),

    // ========================================
    // Health data errors (HEALTH_*)
    // ========================================
    HEALTH_METRIC_NOT_FOUND(404, "health.metric_not_found", "健康数据不存在", "Health metric not found"),
    HEALTH_PROFILE_NOT_FOUND(404, "health.profile_not_found", "健康档案不存在", "Health profile not found"),
    HEALTH_ALERT_NOT_FOUND(404, "health.alert_not_found", "健康提醒不存在", "Health alert not found"),
    HEALTH_THRESHOLD_INVALID(400, "health.threshold_invalid", "健康阈值设置无效", "Invalid health threshold"),
    HEALTH_DATA_CONFLICT(409, "health.data_conflict", "健康数据冲突", "Health data conflict"),

    // ========================================
    // Resource errors (RESOURCE_*)
    // ========================================
    RESOURCE_NOT_FOUND(404, "resource.not_found", "资源不存在", "Resource not found"),
    RESOURCE_ALREADY_EXISTS(409, "resource.exists", "资源已存在", "Resource already exists"),
    RESOURCE_FILE_NOT_FOUND(404, "resource.file_not_found", "文件不存在", "File not found"),
    RESOURCE_FILE_PARSE_ERROR(400, "resource.file_parse_error", "文件解析失败", "File parsing failed"),

    // ========================================
    // Validation errors (VALIDATION_*)
    // ========================================
    VALIDATION_INVALID_PARAMETER(400, "validation.invalid_parameter", "参数校验失败", "Invalid parameter"),
    VALIDATION_FILE_EMPTY(400, "validation.file_empty", "文件不能为空", "File cannot be empty"),
    VALIDATION_FILE_SIZE_EXCEEDED(400, "validation.file_size_exceeded", "文件大小超过限制", "File size exceeded"),
    VALIDATION_FILE_TYPE_INVALID(400, "validation.file_type_invalid", "文件类型不支持", "Invalid file type"),

    // ========================================
    // AI service errors (AI_*)
    // ========================================
    AI_ERROR(500, "ai.error", "AI服务暂时不可用", "AI service unavailable"),
    AI_PARSE_ERROR(500, "ai.parse_error", "AI响应解析失败", "AI response parsing failed"),
    AI_RESPONSE_EMPTY(500, "ai.response_empty", "AI响应为空", "AI response is empty"),
    AI_RATE_LIMIT_EXCEEDED(429, "ai.rate_limit_exceeded", "请求过于频繁", "Rate limit exceeded"),
    OCR_ERROR(500, "ocr.error", "OCR识别失败", "OCR recognition failed"),

    // ========================================
    // Device sync errors (DEVICE_*)
    // ========================================
    DEVICE_NOT_CONNECTED(400, "device.not_connected", "设备未连接", "Device not connected"),
    DEVICE_SYNC_FAILED(500, "device.sync_failed", "设备同步失败", "Device sync failed"),
    DEVICE_RATE_LIMIT_EXCEEDED(429, "device.rate_limit_exceeded", "同步请求过于频繁，请稍后再试", "Sync rate limit exceeded, please try again later"),

    // ========================================
    // System errors (SYSTEM_*)
    // ========================================
    SYSTEM_ERROR(500, "system.error", "系统错误", "System error"),
    SYSTEM_DATABASE_ERROR(500, "system.database_error", "数据库错误", "Database error"),
    SYSTEM_INTERNAL_ERROR(500, "system.internal_error", "内部错误", "Internal error"),
    BUSINESS_ERROR(400, "business.error", "业务处理失败", "Business operation failed"),

    // ========================================
    // Legacy code mappings (for backward compatibility)
    // ========================================
    // These are kept for backward compatibility with legacy integer-based error codes
    // Use the prefixed versions above in new code
    @Deprecated
    BAD_REQUEST(400, "bad_request", "请求错误", "Bad request"),
    @Deprecated
    NOT_FOUND(404, "not_found", "未找到", "Not found"),
    @Deprecated
    CONFLICT(409, "conflict", "冲突", "Conflict"),
    @Deprecated
    INTERNAL_ERROR(500, "internal_error", "内部错误", "Internal error");

    private final int httpStatus;
    private final String code;
    private final String messageZh;
    private final String messageEn;

    ErrorCode(int httpStatus, String code, String messageZh, String messageEn) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.messageZh = messageZh;
        this.messageEn = messageEn;
    }

    /**
     * Get the error message. Returns Chinese by default.
     * For full locale resolution, use getMessage(String lang).
     *
     * @return Chinese error message by default
     */
    public String getMessage() {
        return messageZh;
    }

    /**
     * Get the error message for a specific language.
     *
     * @param lang Language code ("en" for English, any other value for Chinese)
     * @return Localized error message
     */
    public String getMessage(String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            return messageEn;
        }
        return messageZh;
    }
}
