package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * AI Classify Request DTO
 * Used for content classification requests
 */
@Data
public class AIClassifyRequest {

    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 10000, message = "Content must be between 1 and 10000 characters")
    private String content;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    @Size(max = 500, message = "Context must not exceed 500 characters")
    private String context;
}
