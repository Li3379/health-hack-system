package com.hhs.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hhs.common.constant.ErrorCode;
import com.hhs.dto.ai.AIClassifyResponse;
import com.hhs.exception.BusinessException;
import com.hhs.service.AIClassifyService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI内容分类服务实现
 */
@Slf4j
@Service
public class AIClassifyServiceImpl implements AIClassifyService {

    private final ChatLanguageModel chatModel;
    private final RedisTemplate<String, String> redisTemplate;

    public AIClassifyServiceImpl(
            ChatLanguageModel chatModel,
            @Qualifier("aiCacheRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.chatModel = chatModel;
        this.redisTemplate = redisTemplate;
    }
    
    private static final String CLASSIFY_PROMPT_TEMPLATE = """
        你是一个专业的健康内容分类专家。请分析以下健康技巧内容，并返回JSON格式的分类结果。
        
        分类规则：
        - category: 必须是以下之一 [diet, fitness, sleep, mental]
          * diet: 饮食、营养、食谱相关
          * fitness: 运动、锻炼、健身相关  
          * sleep: 睡眠、作息、休息相关
          * mental: 心理、情绪、压力相关
        
        - tags: 提取3-5个关键标签，每个标签2-4个字
        
        - summary: 用一句话概括内容要点（20字以内）
        
        返回格式（严格JSON，不要有其他说明文字）：
        {"category": "diet", "tags": ["标签1", "标签2", "标签3"], "summary": "核心内容概括"}
        
        待分析内容：
        标题：%s
        内容：%s
        """;
    
    /**
     * Intelligently classify health tip content
     *
     * @param title Content title
     * @param content Content body
     * @return Classification result containing category, tags, and summary
     * @throws BusinessException if AI service fails and fallback classification also fails
     */
    @Override
    public AIClassifyResponse classify(String title, String content) {
        log.info("AI分类请求: title={}", title);
        
        // 1. 检查缓存（相同内容不重复调用）
        String cacheKey = "ai:classify:" + DigestUtil.md5Hex(title + content);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("AI分类命中缓存: key={}", cacheKey);
            return parseJSON(cached);
        }
        
        // 2. 构建Prompt
        String prompt = String.format(CLASSIFY_PROMPT_TEMPLATE, title, content);
        
        // 3. 调用AI
        try {
            String aiResponse = chatModel.generate(prompt);
            log.info("AI分类响应: {}", aiResponse);
            
            // 4. 提取JSON
            String jsonResult = extractJSON(aiResponse);
            
            // 5. 解析结果
            AIClassifyResponse result = parseJSON(jsonResult);
            
            // 6. 缓存结果（30天）
            redisTemplate.opsForValue().set(
                cacheKey, 
                jsonResult,
                30, 
                TimeUnit.DAYS
            );
            
            log.info("AI分类成功: category={}, tags={}", result.category(), result.tags());
            return result;
            
        } catch (Exception e) {
            log.error("AI分类失败", e);
            // 降级为默认分类
            return getDefaultClassify(title, content);
        }
    }
    
    /**
     * 从AI响应中提取JSON部分
     */
    private String extractJSON(String text) {
        if (text == null || text.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_ERROR, "AI响应为空");
        }
        
        // 查找第一个{和最后一个}
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        // 如果没有找到JSON，尝试直接解析
        return text.trim();
    }
    
    /**
     * 解析JSON为DTO
     */
    private AIClassifyResponse parseJSON(String json) {
        try {
            JSONObject jsonObj = JSONUtil.parseObj(json);
            
            String category = jsonObj.getStr("category", "diet");
            List<String> tags = jsonObj.getBeanList("tags", String.class);
            String summary = jsonObj.getStr("summary", "");
            
            // 验证category
            if (!Arrays.asList("diet", "fitness", "sleep", "mental").contains(category)) {
                log.warn("AI返回了无效的category: {}, 使用默认值diet", category);
                category = "diet";
            }
            
            // 限制tags数量
            if (tags != null && tags.size() > 5) {
                tags = tags.subList(0, 5);
            }
            
            return new AIClassifyResponse(category, tags, summary);
            
        } catch (Exception e) {
            log.error("解析AI响应失败: json={}", json, e);
            throw new BusinessException(ErrorCode.AI_PARSE_ERROR, "AI响应解析失败");
        }
    }
    
    /**
     * 降级方案：基于关键词的简单分类
     */
    private AIClassifyResponse getDefaultClassify(String title, String content) {
        log.warn("使用默认分类逻辑");
        
        String text = (title + content).toLowerCase();
        String category = "diet"; // 默认
        
        if (text.contains("运动") || text.contains("锻炼") || text.contains("健身")) {
            category = "fitness";
        } else if (text.contains("睡眠") || text.contains("失眠") || text.contains("作息")) {
            category = "sleep";
        } else if (text.contains("心理") || text.contains("情绪") || text.contains("压力")) {
            category = "mental";
        }
        
        List<String> tags = List.of("健康", "生活");
        String summary = title.length() > 20 ? title.substring(0, 20) : title;
        
        return new AIClassifyResponse(category, tags, summary);
    }
}
