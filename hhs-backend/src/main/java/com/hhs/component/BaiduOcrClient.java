package com.hhs.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * 百度 OCR 通用文字识别客户端。
 * 未配置 apiKey/secretKey 时 recognize 返回 Optional.empty()。
 */
@Component
public class BaiduOcrClient {

    private static final Logger log = LoggerFactory.getLogger(BaiduOcrClient.class);
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";

    @Value("${baidu.ocr.api-key:}")
    private String apiKey;

    @Value("${baidu.ocr.secret-key:}")
    private String secretKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String cachedToken;
    private long tokenExpireAt;

    /**
     * 识别图片中的文字，返回拼接后的整段文本。
     * 若未配置密钥或调用失败，返回 empty。
     */
    public Optional<String> recognize(byte[] imageBytes) {
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.debug("百度 OCR 未配置 api-key/secret-key，跳过识别");
            return Optional.empty();
        }
        try {
            String token = getAccessToken();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("image", base64Image);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OCR_URL + "?access_token=" + token, request, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error_code")) {
                log.warn("百度 OCR 返回错误: {}", root);
                return Optional.empty();
            }
            StringBuilder text = new StringBuilder();
            JsonNode wordsResult = root.path("words_result");
            if (wordsResult.isArray()) {
                for (JsonNode item : wordsResult) {
                    JsonNode words = item.path("words");
                    if (!words.isMissingNode()) {
                        text.append(words.asText()).append("\n");
                    }
                }
            }
            return Optional.of(text.toString().trim());
        } catch (Exception e) {
            log.error("百度 OCR 调用异常", e);
            return Optional.empty();
        }
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }
        String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            cachedToken = root.path("access_token").asText();
            int expiresIn = root.path("expires_in").asInt(2592000);
            tokenExpireAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
            return cachedToken;
        } catch (Exception e) {
            throw new RuntimeException("获取百度 access_token 失败", e);
        }
    }
}
