package com.hhs.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动期接线探针：{@link ExceptionHandlerMethodResolver} 的构造等价于
 * {@code RequestMappingHandlerAdapter} 初始化 advice 时的行为。
 *
 * <p>复审指出同一异常若被两个 {@code @ExceptionHandler} 声明，构造会抛
 * {@code IllegalStateException: Ambiguous @ExceptionHandler method mapped for [...]}。
 * 这条用例把「当前类可被 Spring 正常装配」变成一个可执行断言，而不只是靠人工核对。</p>
 */
@DisplayName("GlobalExceptionHandler — Spring 启动期接线")
class GlobalExceptionHandlerWiringTest {

    @Test
    @DisplayName("构造 ExceptionHandlerMethodResolver 不抛 Ambiguous —— 无重复 handler")
    void resolverInitialisesWithoutAmbiguousHandler() {
        assertThatCode(() -> new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class))
            .as("重复的 @ExceptionHandler 会让 Spring 启动失败")
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("对照：人为造一个重复类，resolver 确实会抛 —— 证明上一条断言不是空转")
    void duplicateHandlerIsActuallyRejected() {
        // 若上一条用例无论代码如何都通过，它就什么也没证明。这个对照组证明
        // resolver 对重复声明确实会抛，所以上一条的「不抛」是有信息量的。
        assertThatThrownBy(() -> new ExceptionHandlerMethodResolver(DuplicateAdvice.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Ambiguous");
    }

    /** 故意重复声明同一个异常，仅用于证明探针有效。 */
    static class DuplicateAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.web.HttpRequestMethodNotSupportedException.class)
        public String first(Exception ex) {
            return "a";
        }

        @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.web.HttpRequestMethodNotSupportedException.class)
        public String second(Exception ex) {
            return "b";
        }
    }
}
