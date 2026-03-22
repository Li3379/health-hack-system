package com.hhs.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${spring.ai.langchain4j.openai.chat-model.api-key:}")
    private String apiKey;

    @Value("${spring.ai.langchain4j.openai.chat-model.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${spring.ai.langchain4j.openai.chat-model.model-name:qwen-max}")
    private String modelName;

    @Value("${spring.ai.langchain4j.openai.chat-model.temperature:0.7}")
    private double temperature;

    @Value("${spring.ai.langchain4j.openai.chat-model.max-tokens:1000}")
    private int maxTokens;

    @Value("${spring.ai.langchain4j.openai.chat-model.timeout:30s}")
    private String timeout;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("=== LangChain4j配置检查 ===");
        log.info("API Key已配置: {}", StringUtils.hasText(apiKey));
        log.info("Base URL: {}", baseUrl);
        log.info("Model Name: {}", modelName);
        log.info("Temperature: {}", temperature);
        log.info("Max Tokens: {}", maxTokens);
        log.info("Timeout: {}", timeout);
        
        if (!StringUtils.hasText(apiKey)) {
            log.warn("OpenAI API Key 未配置，AI 功能将使用离线占位逻辑。");
            return new ChatLanguageModel() {
                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages) {
                    AiMessage aiMessage = AiMessage.from("AI服务未配置，请联系管理员设置API Key后再试。");
                    return Response.from(aiMessage);
                }
            };
        }
        log.info("正在初始化 LangChain4j ChatModel...");
        
        try {
            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.parse("PT" + timeout.replace("s", "S")))
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            log.info("LangChain4j ChatModel 初始化成功！");
            return model;
        } catch (Exception e) {
            log.error("LangChain4j ChatModel 初始化失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}
