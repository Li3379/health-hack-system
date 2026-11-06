package com.hhs.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hhs.common.constant.ErrorCode;
import com.hhs.vo.FieldErrorVO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Unified API response envelope.
 *
 * <p>{@code code}, {@code message} and {@code data} keep their historical semantics:
 * success is {@code 200}, and non-numeric error codes fall back to {@code 500} through
 * {@link #parseCodeToInt(String)}. Three optional diagnostic fields were added so that the
 * failure payload no longer needs a free-form {@code Map}:
 * <ul>
 *   <li>{@code errorCode} - the stable string code (e.g. {@code resource.exists})</li>
 *   <li>{@code fieldErrors} - structured field-level violations</li>
 *   <li>{@code errors} - legacy {@code "field: message"} strings, kept for one release</li>
 * </ul>
 *
 * <p>Each optional field is annotated individually with {@link JsonInclude.Include#NON_EMPTY}.
 * A class-level annotation is deliberately avoided: it would also drop {@code data} from
 * successful responses that legitimately return {@code null}.
 *
 * @param <T> the success payload type
 */
@Data
@NoArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String errorCode;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<FieldErrorVO> fieldErrors;

    /**
     * Legacy {@code "field: message"} list.
     *
     * @deprecated use {@link #fieldErrors} instead; scheduled for removal after one release.
     */
    @Deprecated
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> errors;

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public Result(int code, String message, T data, String errorCode,
                  List<FieldErrorVO> fieldErrors, List<String> errors) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
        this.errors = errors;
    }

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

    /**
     * Build a failure envelope where the numeric {@code code} and the string
     * {@code errorCode} are supplied independently.
     *
     * @param code      numeric code carried by the envelope
     * @param errorCode stable string code exposed to the client
     * @param message   user-facing message
     * @param <T>       payload type
     * @return failure envelope
     */
    public static <T> Result<T> failure(int code, String errorCode, String message) {
        Result<T> result = new Result<>(code, message, null);
        result.setErrorCode(errorCode);
        return result;
    }

    /**
     * Build the canonical validation-failure envelope.
     *
     * @param message     user-facing summary message
     * @param fieldErrors structured field-level violations
     * @param <T>         payload type
     * @return HTTP-400-shaped validation failure envelope
     */
    public static <T> Result<T> invalidInput(String message, List<FieldErrorVO> fieldErrors) {
        Result<T> result = new Result<>(400, message, null);
        result.setErrorCode(ErrorCode.VALIDATION_INVALID_PARAMETER.getCode());
        result.setFieldErrors(fieldErrors);
        return result;
    }

    public Result<T> withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public Result<T> withFieldErrors(List<FieldErrorVO> fieldErrors) {
        this.fieldErrors = fieldErrors;
        return this;
    }

    public Result<T> withErrors(List<String> errors) {
        this.errors = errors;
        return this;
    }

    private static int parseCodeToInt(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return 500; // Default to system error for non-numeric codes
        }
    }
}
