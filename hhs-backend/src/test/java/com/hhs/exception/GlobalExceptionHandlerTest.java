package com.hhs.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.common.Result;
import com.hhs.common.constant.ErrorCode;
import com.hhs.vo.FieldErrorVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}. The handler is instantiated directly so no
 * Spring context, database, or Docker is required.
 */
class GlobalExceptionHandlerTest {

    private static final String SYSTEM_FALLBACK = "服务暂时不可用，请稍后重试";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // Validation channels
    // ------------------------------------------------------------------

    @Test
    @DisplayName("validation failure projects both field and global errors into fieldErrors")
    void methodArgumentNotValid_projectsFieldAndGlobalErrors() throws Exception {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "userThresholdRequest");
        bindingResult.addError(new FieldError(
            "userThresholdRequest", "metricKey", "指标类型不能为空"));
        bindingResult.addError(new ObjectError(
            "userThresholdRequest", "健康指标数据校验未通过"));

        MethodArgumentNotValidException ex =
            new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<Result<Void>> response =
            handler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Result<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(400);
        assertThat(body.getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_INVALID_PARAMETER.getCode());
        assertThat(body.getFieldErrors()).isNotNull().hasSize(2);
        assertThat(body.getFieldErrors())
            .extracting(FieldErrorVO::field)
            .containsExactly("metricKey", "_global");
        assertThat(body.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("field errors are capped at 20 per response")
    void methodArgumentNotValid_capsFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "bulkRequest");
        for (int i = 0; i < 21; i++) {
            bindingResult.addError(new FieldError(
                "bulkRequest", "field" + i, "第" + i + "项校验失败"));
        }

        MethodArgumentNotValidException ex =
            new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<Result<Void>> response =
            handler.handleMethodArgumentNotValid(ex, request);

        List<FieldErrorVO> fieldErrors = response.getBody().getFieldErrors();
        assertThat(fieldErrors).hasSize(20);
    }

    // ------------------------------------------------------------------
    // Business channel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("business failure keeps the legacy 500 numeric code and exposes errorCode")
    void handleBusinessException_keepsNumericCodeAndExposesErrorCode() {
        BusinessException ex = new BusinessException(
            ErrorCode.RESOURCE_ALREADY_EXISTS, "该指标已存在阈值，请直接编辑");

        Result<Void> body = handler.handleBusinessException(ex, request);

        assertThat(body.getCode()).isEqualTo(500);
        assertThat(body.getErrorCode()).isEqualTo("resource.exists");
        assertThat(body.getMessage()).contains("该指标已存在阈值，请直接编辑");
    }

    @Test
    @DisplayName("business failures carrying a SYSTEM_* code use the fixed friendly message")
    void handleBusinessException_replacesSystemCodes() {
        for (ErrorCode code : systemCodes()) {
            BusinessException ex = new BusinessException(code, "所有指标保存失败");

            Result<Void> body = handler.handleBusinessException(ex, request);

            assertThat(body.getCode()).isEqualTo(500);
            assertThat(body.getErrorCode()).isEqualTo(code.getCode());
            assertThat(body.getMessage())
                .isEqualTo(SYSTEM_FALLBACK + ": 所有指标保存失败");
            assertThat(body.getMessage())
                .doesNotContain("系统错误", "数据库错误", "内部错误");
        }
    }

    // ------------------------------------------------------------------
    // System channel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("system failure hides the internal English detail from the response body")
    void handleSystemException_hidesInternalDetail() throws Exception {
        SystemException ex =
            new SystemException(ErrorCode.SYSTEM_ERROR, "Token encryption failed");

        Result<Void> body = handler.handleSystemException(ex);

        assertThat(body.getCode()).isEqualTo(500);
        assertThat(body.getErrorCode()).isEqualTo("system.error");
        assertThat(body.getMessage()).isEqualTo(SYSTEM_FALLBACK);
        assertThat(objectMapper.writeValueAsString(body))
            .doesNotContain("Token encryption failed");
    }

    @Test
    @DisplayName("all three SYSTEM_* codes resolve to the fixed friendly message")
    void handleSystemException_coversEverySystemCode() {
        for (ErrorCode code : systemCodes()) {
            Result<Void> body = handler.handleSystemException(
                new SystemException(code, "outer internal detail"));

            assertThat(body.getCode()).isEqualTo(500);
            assertThat(body.getErrorCode()).isEqualTo(code.getCode());
            assertThat(body.getMessage()).isEqualTo(SYSTEM_FALLBACK);
            assertThat(body.getMessage())
                .doesNotContain("系统错误", "数据库错误", "内部错误");
        }
    }

    @Test
    @DisplayName("non-SYSTEM codes keep their enum message")
    void handleSystemException_keepsEnumMessageForNonSystemCodes() {
        Result<Void> body =
            handler.handleSystemException(new SystemException(ErrorCode.AUTH_FAILED, "outer failure"));

        assertThat(body.getMessage()).isEqualTo("认证失败");
        assertThat(body.getErrorCode()).isEqualTo("auth.failed");
    }

    // ------------------------------------------------------------------
    // Fallback channels
    // ------------------------------------------------------------------

    @Test
    @DisplayName("uncaught exception returns a friendly Chinese message without the internal detail")
    void handleException_hidesInternalDetail() throws Exception {
        Result<Void> body = handler.handleException(new RuntimeException("db down"));

        assertThat(body.getCode()).isEqualTo(500);
        assertThat(body.getMessage()).isEqualTo(SYSTEM_FALLBACK);
        assertThat(objectMapper.writeValueAsString(body)).doesNotContain("db down");
    }

    @Test
    @DisplayName("authentication, security, parse, 404 and 500 channels are all Chinese")
    void fallbackChannels_areAllChinese() {
        List<String> messages = List.of(
            handler.handleAuthenticationCredentialsNotFound(
                new org.springframework.security.authentication
                    .AuthenticationCredentialsNotFoundException("no creds"))
                .getBody().getMessage(),
            handler.handleSecurityException(new SecurityException("bad request")).getBody().getMessage(),
            handler.handleHttpMessageNotReadableException(
                new org.springframework.http.converter.HttpMessageNotReadableException(
                    "body", (org.springframework.http.HttpInputMessage) null)).getMessage(),
            handler.handleNoResourceFoundException(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "/missing")).getMessage(),
            handler.handleException(new RuntimeException("boom")).getMessage());

        for (String message : messages) {
            assertThat(message)
                .doesNotContain(
                    "System error, please try again later",
                    "Request body format error",
                    "Resource not found",
                    "Authentication required",
                    "Invalid request");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<ErrorCode> systemCodes() {
        return List.of(ErrorCode.SYSTEM_ERROR,
            ErrorCode.SYSTEM_DATABASE_ERROR,
            ErrorCode.SYSTEM_INTERNAL_ERROR);
    }

    /**
     * Provides a {@link MethodParameter} pointing at the first argument of a private method, as
     * required by the {@link MethodArgumentNotValidException} constructor.
     */
    private MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
        return new MethodParameter(method, 0);
    }

    private void dummy(String value) {
        // Intentionally empty: only the method parameter signature is used.
    }
}