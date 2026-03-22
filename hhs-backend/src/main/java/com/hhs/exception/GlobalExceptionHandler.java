package com.hhs.exception;

import com.hhs.common.Result;
import com.hhs.common.constant.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global exception handler for the application.
 * Provides consistent error responses and aggregates validation errors.
 * Supports internationalization via Accept-Language header.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle business exceptions with ErrorCode support.
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode ec = ex.getErrorCode();
        String lang = resolveLanguage(request);
        String message = ec.getMessage(lang);

        // Include detail if present
        if (ex.getDetail() != null) {
            message = message + ": " + ex.getDetail();
        }

        log.warn("Business exception: {} - {} - {}", ec.getCode(), message, ex.getMessage());
        return Result.failure(ec.getCode(), message);
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
        return Result.failure(errorCode.getHttpStatus(), ex.getMessage());
    }

    /**
     * Handle validation exceptions from @Valid annotated request bodies.
     * Returns a flat list of all validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> {
                String field = error.getField();
                String message = error.getDefaultMessage();
                // For nested DTOs, use flattened path format
                if (field.contains(".")) {
                    return field + ": " + message;
                }
                return field + ": " + message;
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("code", ErrorCode.VALIDATION_INVALID_PARAMETER.getCode());
        response.put("message", getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request));
        response.put("errors", errors);

        log.warn("Validation failed: {}", errors);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle constraint violation exceptions (e.g., from @Validated method parameters).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        List<String> errors = violations.stream()
            .map(violation -> {
                String propertyPath = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                return propertyPath + ": " + message;
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("code", ErrorCode.VALIDATION_INVALID_PARAMETER.getCode());
        response.put("message", getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request));
        response.put("errors", errors);

        log.warn("Constraint validation failed: {}", errors);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle general validation exceptions.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", ErrorCode.VALIDATION_INVALID_PARAMETER.getCode());
        response.put("message", getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request));
        response.put("errors", List.of(ex.getMessage()));

        log.warn("Validation exception: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle authentication credentials not found exceptions.
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationCredentialsNotFound(
            AuthenticationCredentialsNotFoundException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("code", "AUTHENTICATION_REQUIRED");
        response.put("message", "Authentication required. Please login.");

        log.warn("Authentication attempt without credentials: {}", ex.getMessage());

        return ResponseEntity.status(401).body(response);
    }

    /**
     * Handle security exceptions.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "SECURITY_VIOLATION");
        response.put("message", "Invalid request");

        log.error("Security violation: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle HTTP message not readable exceptions (e.g., malformed JSON).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Request body parsing failed: {}", ex.getMessage());
        return Result.failure(400, "Request body format error");
    }

    /**
     * Handle resource not found exceptions.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getResourcePath());
        // For static resources (like avatars) that don't exist, return 404 but don't log as error
        return Result.failure(404, "Resource not found");
    }

    /**
     * Handle all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("System exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return Result.failure(500, "System error, please try again later");
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
