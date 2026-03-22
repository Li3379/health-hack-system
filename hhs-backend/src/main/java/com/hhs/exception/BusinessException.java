package com.hhs.exception;

import com.hhs.common.constant.ErrorCode;
import lombok.Getter;

import java.io.Serializable;

/**
 * Business exception with ErrorCode support.
 * Provides consistent error handling across the application.
 */
@Getter
public class BusinessException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String detail;

    /**
     * Constructor with ErrorCode - recommended for new code.
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * Constructor with ErrorCode and detail message.
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * Constructor with ErrorCode and cause.
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * Constructor with ErrorCode, detail message, and cause.
     */
    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #BusinessException(ErrorCode)} instead
     */
    @Deprecated
    public BusinessException(int code, String message) {
        super(message);
        this.errorCode = inferErrorCode(code);
        this.detail = null;
    }

    /**
     * Get the HTTP status code for this exception.
     */
    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    /**
     * Get the error code string (e.g., "user.not_found").
     */
    public String getCode() {
        return errorCode.getCode();
    }

    /**
     * Get the error message with optional detail.
     */
    @Override
    public String getMessage() {
        if (detail != null) {
            return errorCode.getMessage() + ": " + detail;
        }
        return errorCode.getMessage();
    }

    /**
     * Infer ErrorCode from legacy integer code.
     * This maintains backward compatibility with existing code.
     */
    private ErrorCode inferErrorCode(int code) {
        return switch (code) {
            case 400 -> ErrorCode.VALIDATION_INVALID_PARAMETER;
            case 401 -> ErrorCode.AUTH_FAILED;
            case 403 -> ErrorCode.AUTH_FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 409 -> ErrorCode.RESOURCE_ALREADY_EXISTS;
            case 500 -> ErrorCode.SYSTEM_ERROR;
            default -> ErrorCode.BUSINESS_ERROR;
        };
    }
}
