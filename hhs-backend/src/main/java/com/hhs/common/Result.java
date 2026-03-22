package com.hhs.common;

import com.hhs.common.constant.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> failure(String code, String message) {
        return new Result<>(parseCodeToInt(code), message, null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.getHttpStatus(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String detail) {
        String message = detail != null ? errorCode.getMessage() + ": " + detail : errorCode.getMessage();
        return new Result<>(errorCode.getHttpStatus(), message, null);
    }

    private static int parseCodeToInt(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return 500; // Default to system error for non-numeric codes
        }
    }
}
