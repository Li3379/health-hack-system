package com.hhs.service.push.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析企微 / 飞书 webhook 的响应体，判断这次推送是否真的成功。
 *
 * <p>两个渠道原来各自用字符串嗅探：企微 {@code response.contains("\"errcode\":0")}、
 * 飞书 {@code contains("\"StatusCode\":0")}。子串匹配对响应的**空白与字段顺序**敏感 ——
 * 服务商返回 {@code {"errcode": 0}}（冒号后有空格）或带缩进的 pretty-print 就会判失败，
 * 用户明明收到了消息却在推送历史里看到 FAILED。改为按 JSON 字段取值。
 *
 * <p>解析失败（非 JSON、空体）一律视为未成功，并把原文带回给调用方 —— 这类响应无法证明成功。
 */
@Slf4j
final class WebhookResponseEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebhookResponseEvaluator() {
    }

    /**
     * Evaluate a webhook response against the field that carries its status code.
     *
     * @param response      raw response body, may be null
     * @param statusField   the JSON field holding the provider's status ({@code errcode} / {@code StatusCode})
     * @param errorField    optional human-readable message field ({@code errmsg} / {@code StatusMessage}),
     *                      reported back so the history row says why the provider refused
     * @return null when the provider reported success, otherwise the failure reason
     */
    static String failureReason(String response, String statusField, String errorField) {
        if (response == null || response.isBlank()) {
            return "empty response";
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(response);
        } catch (Exception e) {
            // 既不是 JSON 就无法证明成功；把原文带回去，至少运维能看到对方返回了什么
            log.warn("Webhook response is not JSON: {}", abbreviate(response));
            return "unparseable response: " + abbreviate(response);
        }

        if (root == null || !root.isObject()) {
            return "unexpected response: " + abbreviate(response);
        }

        JsonNode status = root.get(statusField);
        // 字段缺失与值不是 0 一样都是「没有说成功」——但对飞书要注意 StatusCode 与 code 的差异，
        // 见下面的 fallback：旧版返回 StatusCode，新版返回 code。
        if (status == null) {
            JsonNode fallback = root.get("code");
            if (fallback != null) {
                status = fallback;
            }
        }

        if (status == null) {
            return "missing " + statusField + ": " + abbreviate(response);
        }
        if (!status.isNumber() || status.asInt() != 0) {
            String detail = errorField != null ? textOf(root, errorField) : "";
            return detail.isEmpty()
                    ? statusField + "=" + status.asText()
                    : statusField + "=" + status.asText() + " " + detail;
        }

        return null;
    }

    private static String textOf(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null ? "" : node.asText("");
    }

    /** 响应体可能很长（含 echo 的请求体），截断后再回传/记日志。 */
    private static String abbreviate(String response) {
        String trimmed = response.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }
}