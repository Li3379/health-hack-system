package com.hhs.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 内容安全过滤器
 * 过滤敏感词和不当内容
 */
@Slf4j
@Component
public class ContentFilter {
    
    /**
     * 敏感词列表（完全屏蔽）
     */
    private static final Set<String> SENSITIVE_WORDS = Set.of(
        "自杀", "割腕", "轻生",
        "抑郁症", "精神病",
        "癌症", "肿瘤", "白血病",
        "药品", "处方药", "注射",
        "赌博", "毒品", "违法"
    );
    
    /**
     * 警告词列表（需要添加提示）
     */
    private static final Set<String> WARNING_WORDS = Set.of(
        "头痛", "胸痛", "呼吸困难", 
        "心慌", "失眠", "发烧",
        "血压", "糖尿病", "高血脂"
    );
    
    /**
     * 检查是否包含敏感内容
     */
    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        boolean hasSensitive = SENSITIVE_WORDS.stream().anyMatch(text::contains);
        if (hasSensitive) {
            log.warn("检测到敏感内容: text={}", text.substring(0, Math.min(50, text.length())));
        }
        return hasSensitive;
    }
    
    /**
     * 过滤AI响应内容
     * - 检测敏感词，直接返回提示语
     * - 检测警告词，添加医疗免责声明
     */
    public String filterResponse(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        
        // 检查敏感词
        for (String word : SENSITIVE_WORDS) {
            if (answer.contains(word)) {
                log.warn("AI回答包含敏感词: word={}", word);
                return "抱歉，您的问题涉及专业医疗领域，建议咨询专业医生获得帮助。🏥";
            }
        }
        
        // 检查警告词，添加免责声明
        boolean hasWarning = WARNING_WORDS.stream().anyMatch(answer::contains);
        if (hasWarning) {
            answer += "\n\n⚠️ 温馨提示：以上建议仅供参考，如症状持续或加重，请及时就医。";
        }
        
        return answer;
    }
    
    /**
     * 清理用户输入（去除特殊字符）
     */
    public String cleanInput(String input) {
        if (input == null) {
            return "";
        }
        
        return input.trim()
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "") // 控制字符
            .replaceAll("\\s+", " "); // 多个空格合并
    }
}
