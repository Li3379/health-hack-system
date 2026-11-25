package com.hhs.service.push.channel;

import com.hhs.dto.AlertVO;
import com.hhs.entity.User;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserMapper;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannel;
import com.hhs.service.push.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Email push channel implementation.
 * Only activated when spring.mail.host is configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${spring.mail.host:}'.isEmpty()")
public class EmailPushChannel implements PushChannel {

    private final JavaMailSender mailSender;
    private final UserMapper userMapper;
    private final UserPushConfigMapper userPushConfigMapper;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    /**
     * Ceiling for a failure message: {@code push_history.message} is {@code VARCHAR(500)}, and a
     * 535 reply plus a socket description reaches most of it.
     */
    private static final int MAX_FAILURE_MESSAGE_CHARS = 480;

    /** Server text is capped below the overall ceiling so an appended hint still fits. */
    private static final int MAX_RAW_CHARS = 300;

    /** Bounded walk so a self-referencing or very deep cause chain cannot spin. */
    private static final int MAX_CAUSE_DEPTH = 5;

    @Override
    public PushResult push(Long userId, AlertVO alert) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return PushResult.failed(getChannelType(), "User not found");
        }

        // Check user's email configuration
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, "EMAIL");
        String email = config != null && config.getConfigValue() != null
                ? config.getConfigValue()
                : user.getEmail();

        if (email == null || email.isEmpty()) {
            log.debug("No email configured for user {}", userId);
            return PushResult.skipped(getChannelType(), "No email configured");
        }

        try {
            sendEmail(email, alert, user);
            return PushResult.success(getChannelType());
        } catch (Exception e) {
            String detail = describeFailure(e);
            log.error("Failed to send email to {}: {}", email, detail);
            return PushResult.failed(getChannelType(), detail);
        }
    }

    /**
     * Build a failure message that keeps the SMTP server's own wording.
     *
     * <p>Spring wraps a failed SMTP AUTH in {@code MailAuthenticationException(Throwable)}, whose
     * message is the hardcoded literal {@code "Authentication failed"} — the server's actual reply
     * ({@code 535 Login fail. Account is abnormal, ...}) lives only on the cause. Logging just
     * {@code getMessage()} therefore discarded the one piece of text saying <em>why</em> the login
     * failed, leaving "Authentication failed" in {@code push_history} and in the "test push"
     * response, indistinguishable between a wrong authorization code, a disabled SMTP service and
     * a server-side frequency block.
     *
     * <p>This does not fix a misconfigured mailbox — that is an ops problem — and it deliberately
     * does not retry: QQ answers repeated failed AUTH with a frequency limit, so a retry loop makes
     * the error worse. It only makes the existing failure legible.
     *
     * <p>Distinct cause messages are appended once, and the whole string is capped because
     * {@code push_history.message} is {@code VARCHAR(500)}.
     */
    private String describeFailure(Throwable error) {
        StringBuilder described = new StringBuilder(
                error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());

        // MailSendException(jakarta.util.Map) 传入的 cause 是 null，子异常挂在 messageExceptions
        // 里、由 getMessage() 以 "Failed messages: ..." 内嵌。只沿 getCause() 走会把
        // 「消息被拒收」这一类错误的信息丢光，所以两个来源都要看。
        List<Throwable> nested = new ArrayList<>();
        if (error instanceof MailSendException sendException) {
            Exception[] failed = sendException.getMessageExceptions();
            if (failed != null) {
                nested.addAll(Arrays.asList(failed));
            }
        }

        List<Throwable> chain = new ArrayList<>(nested);
        Throwable cause = error.getCause();
        int guard = 0;
        while (cause != null && guard++ < MAX_CAUSE_DEPTH) {
            chain.add(cause);
            cause = cause.getCause();
        }

        for (Throwable link : chain) {
            String message = link.getMessage();
            if (message == null || message.isBlank() || described.toString().contains(message)) {
                continue;
            }
            described.append(" (").append(message).append(')');
            // 原文封顶低于整体上限，给后面的处置后缀留位置
            if (described.length() >= MAX_RAW_CHARS) {
                break;
            }
        }

        String raw = described.length() <= MAX_RAW_CHARS
                ? described.toString()
                : described.substring(0, MAX_RAW_CHARS - 3) + "...";

        // 分类后缀：服务器原文已经带在上面，这里只补「该动什么」。
        String hint = actionableHint(error, nested);
        if (hint.isEmpty()) {
            return raw;
        }

        String combined = raw + " → " + hint;
        // 后缀也要进封顶：认证提示本身就有几十字，raw 已接近上限时会一起超列宽。
        return combined.length() <= MAX_FAILURE_MESSAGE_CHARS
                ? combined
                : combined.substring(0, MAX_FAILURE_MESSAGE_CHARS - 3) + "...";
    }

    /**
     * Which knob to turn, decided from the exception type rather than the message text.
     *
     * <p>Spring gives no mapping from mail exceptions to anything a user can act on; without this
     * the response is the English {@code Authentication failed} and both the user and the operator
     * are left guessing. Classification is by type so it cannot be fooled by a hostname that
     * happens to contain "auth" (the trap {@code PushSettings.getFailureHint} documents).
     *
     * <p>Only {@link MailAuthenticationException} gets an actionable sentence, because only that
     * one has a known, correct remedy: QQ (and most providers) reject the account password and
     * require an app-specific authorization code. Other failures keep the server text alone —
     * inventing advice for them would be speculation.
     */
    private String actionableHint(Throwable error, List<Throwable> nested) {
        boolean authFailed = error instanceof MailAuthenticationException
                || nested.stream().anyMatch(n -> n instanceof jakarta.mail.AuthenticationFailedException)
                || hasAuthFailureInCauseChain(error.getCause());
        if (!authFailed) {
            return "";
        }
        return "SMTP 认证失败：请检查 MAIL_USERNAME 是否为完整邮箱地址、"
                + "MAIL_PASSWORD 是否为邮箱服务商生成的授权码（QQ 邮箱需在设置中开启 SMTP 服务，"
                + "并使用授权码而非登录密码），修改后重启后端";
    }

    private boolean hasAuthFailureInCauseChain(Throwable cause) {
        int guard = 0;
        while (cause != null && guard++ < MAX_CAUSE_DEPTH) {
            if (cause instanceof jakarta.mail.AuthenticationFailedException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void sendEmail(String to, AlertVO alert, User user) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(buildSubject(alert));
        helper.setText(buildHtmlContent(alert, user), true);

        mailSender.send(message);
        log.info("Alert email sent to {} for user {}", to, user.getId());
    }

    private String buildSubject(AlertVO alert) {
        return String.format("[HHS健康预警] %s - %s", alert.getAlertLevel(), escapeHtml(alert.getTitle()));
    }

    private String buildHtmlContent(AlertVO alert, User user) {
        String color = getColorForLevel(alert.getAlertLevel());
        String nickname = escapeHtml(user.getNickname() != null ? user.getNickname() : user.getUsername());
        String thresholdDisplay = alert.getThresholdValue() != null
                ? alert.getThresholdValue().toString()
                : "正常范围";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset='UTF-8'></head><body>");
        html.append("<div style='max-width:600px;margin:0 auto;font-family:Arial,sans-serif;'>");

        // Header
        html.append(String.format("<h2 style='color:%s;'>健康预警通知</h2>", color));
        html.append(String.format("<p>尊敬的 %s，您好！</p>", nickname));

        // Alert content
        html.append("<div style='background:#f5f5f5;padding:15px;border-radius:5px;margin:15px 0;'>");
        html.append(String.format("<h3 style='margin-top:0;color:%s;'>%s</h3>", color, escapeHtml(alert.getTitle())));
        html.append(String.format("<p>%s</p>", escapeHtml(alert.getMessage())));

        if (alert.getCurrentValue() != null) {
            html.append(String.format("<p><strong>当前值：</strong>%s</p>", alert.getCurrentValue()));
        }
        html.append(String.format("<p><strong>阈值范围：</strong>%s</p>", thresholdDisplay));
        html.append("</div>");

        // Suggestion
        if (alert.getSuggestion() != null && !alert.getSuggestion().isEmpty()) {
            html.append("<div style='background:#e8f5e9;padding:15px;border-radius:5px;margin:15px 0;'>");
            html.append("<h4 style='margin-top:0;'>健康建议</h4>");
            html.append(String.format("<p>%s</p>", escapeHtml(alert.getSuggestion())));
            html.append("</div>");
        }

        // AI Analysis block intentionally absent: nothing in the codebase ever calls
        // setAiAnalysis on an AlertVO, so the branch could never render. Re-add it together
        // with a writer (convertToVO / ThresholdEvaluator), not on its own.

        // Footer
        html.append("<p style='color:#999;font-size:12px;margin-top:20px;'>");
        html.append("此邮件由HHS健康管理系统自动发送，请勿直接回复。");
        html.append("</p>");

        html.append("</div></body></html>");

        return html.toString();
    }

    /**
     * Escape HTML special characters to prevent XSS attacks
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String getColorForLevel(String alertLevel) {
        return switch (alertLevel) {
            case "HIGH" -> "#d32f2f";
            case "MEDIUM" -> "#f57c00";
            default -> "#1976d2";
        };
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public boolean isAvailable(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return false;

        // Check if user has email configured
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, "EMAIL");
        if (config != null && config.getEnabled() == 1 && config.getConfigValue() != null) {
            return true;
        }

        // Fall back to user's profile email
        return user.getEmail() != null && !user.getEmail().isEmpty();
    }

    @Override
    public boolean supportsOffline() {
        return true;
    }
}