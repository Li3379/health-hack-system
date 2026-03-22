package com.hhs.service.push.channel;

import com.hhs.dto.AlertVO;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannel;
import com.hhs.service.push.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * WeCom (Enterprise WeChat) push channel implementation.
 * Only activated when push.wecom.webhook-url is configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "push.wecom.webhook-url")
public class WeComPushChannel implements PushChannel {

    private final UserPushConfigMapper userPushConfigMapper;
    private final RestTemplate pushRestTemplate;

    @Value("${push.wecom.webhook-url:}")
    private String defaultWebhookUrl;

    @Override
    public PushResult push(Long userId, AlertVO alert) {
        String webhookUrl = getWebhookUrl(userId);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return PushResult.skipped(getChannelType(), "Webhook not configured");
        }

        try {
            Map<String, Object> message = buildMarkdownMessage(alert);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
            String response = pushRestTemplate.postForObject(webhookUrl, request, String.class);

            // Validate response
            if (response != null && response.contains("\"errcode\":0")) {
                log.info("WeCom push successful for user {}", userId);
                return PushResult.success(getChannelType());
            } else {
                log.warn("WeCom push returned non-success response for user {}: {}", userId, response);
                return PushResult.failed(getChannelType(), "WeCom API returned error: " + response);
            }
        } catch (Exception e) {
            log.error("WeCom push failed for user {}: {}", userId, e.getMessage());
            return PushResult.failed(getChannelType(), e.getMessage());
        }
    }

    private String getWebhookUrl(Long userId) {
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, "WECOM");
        if (config != null && config.getEnabled() == 1 && config.getConfigValue() != null) {
            return config.getConfigValue();
        }
        return defaultWebhookUrl;
    }

    private Map<String, Object> buildMarkdownMessage(AlertVO alert) {
        String color = getColorMarkdown(alert.getAlertLevel());
        String content = String.format("""
                ## 🏥 健康预警通知

                > 级别：<font color="%s">%s</font>

                **%s**

                - 指标：%s
                - 当前值：%s
                - 阈值范围：%s

                **健康建议：**
                %s

                ---
                *来自 HHS 健康管理系统*
                """,
                color,
                getLevelLabel(alert.getAlertLevel()),
                escapeMarkdown(alert.getTitle()),
                alert.getMetricKey() != null ? alert.getMetricKey() : "-",
                alert.getCurrentValue() != null ? alert.getCurrentValue().toString() : "-",
                alert.getThresholdValue() != null ? alert.getThresholdValue().toString() : "正常范围",
                alert.getSuggestion() != null ? escapeMarkdown(alert.getSuggestion()) : "请关注您的健康状况"
        );

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", content);

        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");
        message.put("markdown", markdown);

        return message;
    }

    /**
     * Escape special characters for WeCom markdown
     */
    private String escapeMarkdown(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("&", "&amp;");
    }

    private String getColorMarkdown(String alertLevel) {
        return switch (alertLevel) {
            case "HIGH" -> "warning";
            case "MEDIUM" -> "comment";
            default -> "info";
        };
    }

    private String getLevelLabel(String alertLevel) {
        return switch (alertLevel) {
            case "HIGH" -> "严重";
            case "MEDIUM" -> "警告";
            default -> "提示";
        };
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WECOM;
    }

    @Override
    public boolean isAvailable(Long userId) {
        String webhookUrl = getWebhookUrl(userId);
        return webhookUrl != null && !webhookUrl.isEmpty();
    }

    @Override
    public boolean supportsOffline() {
        return true;
    }
}