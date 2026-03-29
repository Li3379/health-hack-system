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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feishu (Lark) push channel implementation.
 * Always registered; skips push if no webhook URL is configured for the user.
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class FeishuPushChannel implements PushChannel {

    private final UserPushConfigMapper userPushConfigMapper;
    private final RestTemplate pushRestTemplate;

    @Value("${push.feishu.webhook-url:}")
    private String defaultWebhookUrl;

    @Override
    public PushResult push(Long userId, AlertVO alert) {
        String webhookUrl = getWebhookUrl(userId);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return PushResult.skipped(getChannelType(), "Webhook not configured");
        }

        try {
            Map<String, Object> message = buildCardMessage(alert);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
            String response = pushRestTemplate.postForObject(webhookUrl, request, String.class);

            // Validate response - Feishu returns {"StatusCode":0} on success
            if (response != null && response.contains("\"StatusCode\":0")) {
                log.info("Feishu push successful for user {}", userId);
                return PushResult.success(getChannelType());
            } else {
                log.warn("Feishu push returned non-success response for user {}: {}", userId, response);
                return PushResult.failed(getChannelType(), "Feishu API returned error: " + response);
            }
        } catch (Exception e) {
            log.error("Feishu push failed for user {}: {}", userId, e.getMessage());
            return PushResult.failed(getChannelType(), e.getMessage());
        }
    }

    private String getWebhookUrl(Long userId) {
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, "FEISHU");
        if (config != null && config.getEnabled() == 1 && config.getConfigValue() != null) {
            return config.getConfigValue();
        }
        return defaultWebhookUrl;
    }

    private Map<String, Object> buildCardMessage(AlertVO alert) {
        Map<String, Object> card = new HashMap<>();

        // Header
        Map<String, Object> header = new HashMap<>();
        header.put("title", Map.of("content", "🏥 健康预警", "tag", "plain_text"));
        header.put("template", getCardColor(alert.getAlertLevel()));
        card.put("header", header);

        // Elements
        Map<String, Object> titleElement = new HashMap<>();
        titleElement.put("tag", "div");
        titleElement.put("text", Map.of(
                "content", String.format("**%s**\n%s", escapeText(alert.getTitle()), escapeText(alert.getMessage())),
                "tag", "lark_md"
        ));

        Map<String, Object> fieldsElement = new HashMap<>();
        fieldsElement.put("tag", "div");
        fieldsElement.put("fields", List.of(
                Map.of("is_short", true, "text", Map.of(
                        "content", String.format("**当前值**\n%s",
                                alert.getCurrentValue() != null ? alert.getCurrentValue().toString() : "-"),
                        "tag", "lark_md"
                )),
                Map.of("is_short", true, "text", Map.of(
                        "content", String.format("**阈值范围**\n%s",
                                alert.getThresholdValue() != null ? alert.getThresholdValue().toString() : "正常范围"),
                        "tag", "lark_md"
                ))
        ));

        Map<String, Object> suggestionElement = new HashMap<>();
        suggestionElement.put("tag", "note");
        suggestionElement.put("elements", List.of(
                Map.of("content", alert.getSuggestion() != null ? escapeText(alert.getSuggestion()) : "请关注您的健康状况",
                        "tag", "plain_text")
        ));

        card.put("elements", List.of(titleElement, fieldsElement, suggestionElement));

        Map<String, Object> message = new HashMap<>();
        message.put("msg_type", "interactive");
        message.put("card", card);

        return message;
    }

    /**
     * Escape special characters for Feishu lark_md format
     */
    private String escapeText(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("&", "&amp;");
    }

    private String getCardColor(String alertLevel) {
        return switch (alertLevel) {
            case "HIGH" -> "red";
            case "MEDIUM" -> "orange";
            default -> "blue";
        };
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.FEISHU;
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