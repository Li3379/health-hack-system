package com.hhs.security;

import com.hhs.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 邮箱地址写入校验（PUSH-11）。
 *
 * <p>修复前 EMAIL 的 {@code configValue} 只有 {@code @Size(max=500)}，没有格式校验；
 * {@code EmailPushChannel} 直接把它当成收件人。于是 {@code foo@qq} 能保存成功、页面显示
 * 「已配置」，错误推迟到真实告警/测试推送时由 SMTP 以英文报出。
 */
class EmailAddressValidatorTest {

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {
            "user@example.com",
            "first.last@example.com",
            "user+tag@sub.example.co.uk",
            "a@b.co",
            "ops@corp.local",
            "user_name@example.com",
            "USER@EXAMPLE.COM"
    })
    @DisplayName("合法地址被接受")
    void acceptsValidAddresses(String email) {
        assertThatCode(() -> EmailAddressValidator.validate(email)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {
            "foo@qq",                 // 缺顶级域 —— 清单点名的那个例子
            "foo",                    // 没有 @
            "@example.com",           // 没有本地部分
            "foo@",                   // 没有域名
            "foo bar@example.com",    // 空白
            "a@b.com c@d.com",        // 两段粘贴
            "Name <a@b.com>",         // 显示名形式
            "foo@@example.com",       // 两个 @
            "foo@example..com",       // 空标签
            "foo@-example.com",       // 标签以连字符开头
            ".foo@example.com",       // 本地部分以点开头
            "foo.@example.com"        // 本地部分以点结尾
    })
    @DisplayName("非法地址被拒绝")
    void rejectsInvalidAddresses(String email) {
        assertThatCode(() -> EmailAddressValidator.validate(email))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱地址");
    }

    @Test
    @DisplayName("空值与超长值给出各自的提示")
    void rejectsBlankAndOverlong() {
        assertThatCode(() -> EmailAddressValidator.validate(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
        assertThatCode(() -> EmailAddressValidator.validate("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");

        String overlong = "a".repeat(EmailAddressValidator.MAX_LENGTH) + "@example.com";
        assertThatCode(() -> EmailAddressValidator.validate(overlong))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过长");
    }

    @Test
    @DisplayName("错误详情是中文且指明该怎么填，而不是一句「格式错误」")
    void detailIsActionable() {
        try {
            EmailAddressValidator.validate("foo@qq");
        } catch (BusinessException e) {
            assertThat(e.getDetail())
                    .contains("格式不正确")
                    .contains("name@example.com");
            return;
        }
        throw new AssertionError("expected BusinessException");
    }
}