package com.hhs.security;

import com.hhs.common.constant.ErrorCode;
import com.hhs.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * Validates user-supplied recipient email addresses at write time.
 *
 * <p>The address is later handed straight to {@code EmailPushChannel} as the {@code To:} header.
 * Without a check here, a typo like {@code foo@qq} is stored, the settings page reports
 * "已配置", and the mistake only surfaces when an alert fires — as an English SMTP error, far
 * from the field that was actually wrong. Validating on write puts the message next to the input.
 *
 * <p>Sibling of {@link WebhookUrlValidator}: same shape (static, throws
 * {@link BusinessException} with a Chinese detail that {@code GlobalExceptionHandler} projects
 * into the response envelope), so both write paths report failures the same way.
 *
 * <p>Scope is deliberately structural, not deliverability: it rejects values that can never be a
 * single address (missing {@code @}, no dot in the domain, whitespace, a display-name form such as
 * {@code Name <a@b.com>}, multiple addresses). Whether the mailbox exists is only knowable by
 * sending, which this module must not do.
 */
public final class EmailAddressValidator {

    /** Matches {@code push_config.config_value VARCHAR(500)}; RFC allows far shorter in practice. */
    public static final int MAX_LENGTH = 500;

    /**
     * One {@code local@domain.tld}.
     *
     * <p>Deliberately stricter than RFC 5322 (which permits quoted local parts, IP-literal
     * domains and comments): those forms are never typed into a push-settings box, and accepting
     * them would mean accepting strings no mail client here can render back. It is deliberately
     * looser than requiring a TLD of 2+ letters, so {@code user@localhost} style intranet
     * addresses in a private deployment still pass.
     */
    private static final Pattern ADDRESS = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?"
                    + "(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$");

    private EmailAddressValidator() {
    }

    /**
     * Validate a recipient address.
     *
     * @param email the candidate address
     * @throws BusinessException with {@link ErrorCode#VALIDATION_INVALID_PARAMETER} and a Chinese
     *                           detail when the value cannot be a single email address
     */
    public static void validate(String email) {
        if (email == null || email.isBlank()) {
            throw invalid("邮箱地址不能为空");
        }
        String candidate = email.trim();
        if (candidate.length() > MAX_LENGTH) {
            throw invalid("邮箱地址过长");
        }
        // 空白出现在中间多半是粘贴了两段（"a@b.com c@d.com"）；单独给出贴切提示，
        // 比让用户以为是某个字符写错要好定位。
        if (candidate.chars().anyMatch(Character::isWhitespace)) {
            throw invalid("邮箱地址不能包含空白字符");
        }
        if (!ADDRESS.matcher(candidate).matches()) {
            throw invalid("邮箱地址格式不正确，请填写完整地址（例如 name@example.com）");
        }
    }

    private static BusinessException invalid(String detail) {
        return new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, detail);
    }
}