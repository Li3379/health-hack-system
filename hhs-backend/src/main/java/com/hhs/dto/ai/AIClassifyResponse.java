package com.hhs.dto.ai;

import java.util.List;

/**
 * AI内容分类响应
 */
public record AIClassifyResponse(
    /**
     * 分类：diet, fitness, sleep, mental
     */
    String category,
    
    /**
     * 标签列表（3-5个）
     */
    List<String> tags,
    
    /**
     * 内容摘要（20字内）
     */
    String summary
) {
}
