package com.hhs.exception;

import com.hhs.common.Result;
import com.hhs.common.constant.ErrorCode;
import com.hhs.vo.FieldErrorVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Global exception handler for the application.
 * Provides consistent error responses and aggregates validation errors.
 * Supports internationalization via Accept-Language header.
 *
 * <p>Every user-facing fallback message is Chinese. Server-side exception details are written to
 * the log only and never returned in the response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Upper bound on the number of field errors returned in one response. */
    private static final int MAX_FIELD_ERRORS = 20;

    /** Fixed message used for every {@code SYSTEM_*} error code reaching a user. */
    private static final String SYSTEM_FALLBACK_MESSAGE = "服务暂时不可用，请稍后重试";

    /**
     * Handle business exceptions with ErrorCode support.
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode ec = ex.getErrorCode();
        String lang = resolveLanguage(request);
        String message = systemFallbackMessage(ec, lang);

        // Include detail if present
        if (ex.getDetail() != null) {
            message = message + ": " + ex.getDetail();
        }

        log.warn("Business exception: {} - {} - {}", ec.getCode(), message, ex.getMessage());
        Result<Void> result = Result.failure(ec.getCode(), message);
        return result.withErrorCode(ec.getCode());
    }

    /**
     * Handle system exceptions - errors that don't trigger rollback
     * SystemException indicates transient/external errors (API timeouts, network issues)
     */
    @ExceptionHandler(SystemException.class)
    public Result<Void> handleSystemException(SystemException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.error("System exception: code={}, message={}", errorCode.getCode(), ex.getMessage(), ex);

        // System exceptions don't trigger rollback, might succeed on retry
        String message = systemFallbackMessage(errorCode, "zh");
        return Result.<Void>failure(errorCode.getHttpStatus(), message)
            .withErrorCode(errorCode.getCode());
    }

    /**
     * Handle validation exceptions from @Valid annotated request bodies.
     * Returns structured field-level errors.
     *
     * <p>Both {@code getFieldErrors()} and {@code getGlobalErrors()} are consumed. The former
     * carries property-bound violations, including class-level constraints that call
     * {@code addPropertyNode(...)} (Spring's {@code SpringValidatorAdapter} then exposes them as
     * field errors). The latter carries class-level violations with no property path, which are
     * reported under the {@code "_global"} pseudo-field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorVO> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
            fieldErrors.add(new FieldErrorVO(fe.getField(), fe.getDefaultMessage(), null)));
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
            fieldErrors.add(new FieldErrorVO(extractField(ge), ge.getDefaultMessage(), null)));

        List<FieldErrorVO> capped = fieldErrors.stream().limit(MAX_FIELD_ERRORS).toList();
        List<String> legacyErrors = capped.stream()
            .map(fe -> fe.field() + ": " + fe.message())
            .toList();
        String message = getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request);

        log.warn("Validation failed: {}", legacyErrors);

        return ResponseEntity.badRequest()
            .body(Result.<Void>invalidInput(message, capped).withErrors(legacyErrors));
    }

    /**
     * Handle constraint violation exceptions (e.g., from @Validated method parameters).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        List<FieldErrorVO> fieldErrors = violations.stream()
            .map(violation -> new FieldErrorVO(
                violation.getPropertyPath().toString(), violation.getMessage(), null))
            .limit(MAX_FIELD_ERRORS)
            .toList();
        List<String> legacyErrors = fieldErrors.stream()
            .map(fe -> fe.field() + ": " + fe.message())
            .toList();
        String message = getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request);

        log.warn("Constraint validation failed: {}", legacyErrors);

        return ResponseEntity.badRequest()
            .body(Result.<Void>invalidInput(message, fieldErrors).withErrors(legacyErrors));
    }

    /**
     * Handle general validation exceptions. No property path is available here, so the single
     * violation is reported under the {@code "_global"} pseudo-field.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Void>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        List<FieldErrorVO> fieldErrors =
            List.of(new FieldErrorVO("_global", ex.getMessage(), null));
        String message = getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request);

        log.warn("Validation exception: {}", ex.getMessage());

        return ResponseEntity.badRequest()
            .body(Result.<Void>invalidInput(message, fieldErrors).withErrors(List.of(ex.getMessage())));
    }

    /**
     * Handle authentication credentials not found exceptions.
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationCredentialsNotFound(
            AuthenticationCredentialsNotFoundException ex) {
        log.warn("Authentication attempt without credentials: {}", ex.getMessage());

        Result<Void> body = Result.failure(
            401, ErrorCode.AUTH_UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        return ResponseEntity.status(401).body(body);
    }

    /**
     * Handle security exceptions.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Result<Void>> handleSecurityException(SecurityException ex) {
        log.error("Security violation: {}", ex.getMessage());

        Result<Void> body = Result.failure(400, "security.violation", "请求无效，请刷新页面后重试");
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handle HTTP message not readable exceptions (e.g., malformed JSON).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Request body parsing failed: {}", ex.getMessage());
        return Result.failure(400, "validation.request_body_invalid", "请求内容格式有误，请检查后重试");
    }

    /**
     * Handle resource not found exceptions.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("静态资源缺失: {}", ex.getResourcePath());
        // For static resources (like avatars) that don't exist, return 404 but don't log as error
        return Result.failure(404, ErrorCode.RESOURCE_NOT_FOUND.getCode(), "请求的资源不存在");
    }

    /**
     * Handle all other exceptions. The exception detail goes to the log only.
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("System exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return Result.failure(500, ErrorCode.SYSTEM_ERROR.getCode(), SYSTEM_FALLBACK_MESSAGE);
    }

    /**
     * Resolve the user-facing message for an error code.
     *
     * <p>Every {@code SYSTEM_*} code is unconditionally replaced by a fixed friendly message.
     * The enum's own Chinese wording for those codes is banned by the UI specification and would
     * otherwise reach the user, because these failures travel back as a business envelope that the
     * client renders verbatim.
     *
     * @param ec   the error code being reported
     * @param lang requested language for non-system codes
     * @return the user-facing message
     */
    private String systemFallbackMessage(ErrorCode ec, String lang) {
        if (ec.name().startsWith("SYSTEM_")) {
            return SYSTEM_FALLBACK_MESSAGE;
        }
        return ec.getMessage(lang);
    }

    /**
     * Resolve the pseudo-field name for a class-level violation.
     *
     * @param ge a global error produced by Bean Validation
     * @return the first property-path node name, or {@code "_global"} when absent
     */
    private String extractField(ObjectError ge) {
        ConstraintViolation<?> violation;
        try {
            violation = ge.unwrap(ConstraintViolation.class);
        } catch (IllegalArgumentException ex) {
            // getGlobalErrors() entries are not backed by a ConstraintViolation in the common case
            return "_global";
        }
        if (violation != null) {
            Iterator<Path.Node> nodes = violation.getPropertyPath().iterator();
            if (nodes.hasNext()) {
                return nodes.next().getName();
            }
        }
        return "_global";
    }

    /**
     * Resolve language from Accept-Language header.
     * Defaults to Chinese (zh) if no Accept-Language header is present.
     */
    private String resolveLanguage(HttpServletRequest request) {
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && acceptLanguage.startsWith("en")) {
            return "en";
        }
        return "zh"; // Default to Chinese
    }

    /**
     * Get localized message for ErrorCode based on Accept-Language header.
     */
    private String getLocalizedMessage(ErrorCode errorCode, HttpServletRequest request) {
        String lang = resolveLanguage(request);
        return errorCode.getMessage(lang);
    }
}