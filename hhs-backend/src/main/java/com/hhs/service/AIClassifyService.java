package com.hhs.service;

import com.hhs.dto.ai.AIClassifyResponse;

/**
 * AI Classify Service
 * Intelligently classifies health tip content using AI
 */
public interface AIClassifyService {

    /**
     * Intelligently classify health tip content
     *
     * @param title Content title
     * @param content Content body
     * @return Classification result containing category, tags, and summary
     */
    AIClassifyResponse classify(String title, String content);
}
