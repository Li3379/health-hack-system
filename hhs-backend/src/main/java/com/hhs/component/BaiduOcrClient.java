package com.hhs.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
    private boolean configured = false;

    @PostConstruct
    public void init() {
        configured = apiKey != null && !apiKey.isBlank() && secretKey != null && !secretKey.isBlank();
        if (configured) {
            log.info("=== 百度 OCR 配置检查 ===");
            log.info("API Key: {}****{}", apiKey.substring(0, Math.min(4, apiKey.length())), 
                     apiKey.length() > 8 ? apiKey.substring(apiKey.length() - 4) : "");
            log.info("Secret Key: {}****{}", secretKey.substring(0, Math.min(4, secretKey.length())),
                     secretKey.length() > 8 ? secretKey.substring(secretKey.length() - 4) : "");
            log.info("百度 OCR 服务已配置，将使用真实识别功能");
        } else {
            log.warn("=== 百度 OCR 配置检查 ===");
            log.warn("API Key 或 Secret Key 未配置，OCR 功能将返回模拟数据");
            log.warn("请在环境变量中配置: BAIDU_OCR_API_KEY 和 BAIDU_OCR_SECRET_KEY");
        }
    }

    /**
     * 检查 OCR 服务是否已配置
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * 识别图片中的文字，返回拼接后的整段文本。
     * 若未配置密钥或调用失败，返回 empty。
     */
    public Optional<String> recognize(byte[] imageBytes) {
        if (!configured) {
            log.warn("百度 OCR 未配置，跳过识别，将使用模拟数据");
            return Optional.empty();
        }

        log.info("开始百度 OCR 识别，图片大小: {} bytes", imageBytes.length);

        try {
            String token = getAccessToken();
            log.info("获取 access_token 成功");

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("image", base64Image);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            String url = OCR_URL + "?access_token=" + token;
            log.info("调用百度 OCR API: {}", OCR_URL);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("百度 OCR API 响应状态: {}", response.getStatusCode());

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("百度 OCR API 返回非200状态: {}", response.getStatusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            
            // 检查错误
            if (root.has("error_code")) {
                String errorCode = root.path("error_code").asText();
                String errorMsg = root.path("error_msg").asText();
                log.error("百度 OCR 返回错误 - 错误码: {}, 错误信息: {}", errorCode, errorMsg);
                log.error("完整响应: {}", response.getBody());
                return Optional.empty();
            }

            // 解析识别结果
            StringBuilder text = new StringBuilder();
            JsonNode wordsResult = root.path("words_result");
            if (wordsResult.isArray()) {
                int wordCount = 0;
                for (JsonNode item : wordsResult) {
                    JsonNode words = item.path("words");
                    if (!words.isMissingNode()) {
                        text.append(words.asText()).append("\n");
                        wordCount++;
                    }
                }
                log.info("百度 OCR 识别成功，识别到 {} 行文字", wordCount);
            } else {
                log.warn("百度 OCR 返回数据格式异常: {}", response.getBody());
            }

            String result = text.toString().trim();
            if (result.isEmpty()) {
                log.warn("百度 OCR 未识别到任何文字");
                return Optional.empty();
            }

            return Optional.of(result);
        } catch (Exception e) {
            log.error("百度 OCR 调用异常: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            log.debug("使用缓存的 access_token");
            return cachedToken;
        }

        log.info("请求新的百度 access_token");
        String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            // 检查 token 请求错误
            if (root.has("error")) {
                String error = root.path("error").asText();
                String errorDesc = root.path("error_description").asText();
                log.error("获取百度 access_token 失败 - 错误: {}, 描述: {}", error, errorDesc);
                throw new RuntimeException("获取百度 access_token 失败: " + error + " - " + errorDesc);
            }

            cachedToken = root.path("access_token").asText();
            if (cachedToken == null || cachedToken.isEmpty()) {
                log.error("百度 access_token 响应为空: {}", response.getBody());
                throw new RuntimeException("百度 access_token 响应为空");
            }

            int expiresIn = root.path("expires_in").asInt(2592000);
            tokenExpireAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
            log.info("百度 access_token 获取成功，有效期: {} 秒", expiresIn);
            return cachedToken;
        } catch (Exception e) {
            log.error("获取百度 access_token 异常: {}", e.getMessage(), e);
            throw new RuntimeException("获取百度 access_token 失败", e);
        }
    }
}