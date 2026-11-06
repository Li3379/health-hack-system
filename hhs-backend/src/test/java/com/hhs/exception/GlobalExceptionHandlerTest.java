package com.hhs.exception;

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

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

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