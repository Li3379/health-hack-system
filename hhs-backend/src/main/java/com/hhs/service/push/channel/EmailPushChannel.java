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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

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
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", email, e.getMessage());
            return PushResult.failed(getChannelType(), e.getMessage());
        }
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

        // AI Analysis
        if (alert.getAiAnalysis() != null && !alert.getAiAnalysis().isEmpty()) {
            html.append("<div style='background:#e3f2fd;padding:15px;border-radius:5px;margin:15px 0;'>");
            html.append("<h4 style='margin-top:0;'>AI分析</h4>");
            html.append(String.format("<p>%s</p>", escapeHtml(alert.getAiAnalysis())));
            html.append("</div>");
        }

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