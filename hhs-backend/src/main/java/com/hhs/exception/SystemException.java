package com.hhs.exception;

import com.hhs.common.constant.ErrorCode;
import lombok.Getter;

/**
 * System exception that should NOT trigger transaction rollback.
 * Use for system errors that are transient or external (e.g., API timeouts, network issues).
 * Unlike BusinessException, SystemException indicates the operation might succeed on retry.
 */
@Getter
public class SystemException extends RuntimeException {

    private final ErrorCode errorCode;

    public SystemException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public SystemException(ErrorCode errorCode, String detail) {
        super(detail != null && !detail.isEmpty() ? detail : errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public SystemException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public SystemException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail != null && !detail.isEmpty() ? detail : errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * Get HTTP status code.
     * @return HTTP status from error code
     */
    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
