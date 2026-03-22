package com.hhs.vo;

import java.time.LocalDateTime;

public record OcrStatusVO(String status, String errorMessage, LocalDateTime completedTime) {
    // Constructor for backward compatibility - only status
    public OcrStatusVO(String status) {
        this(status, null, null);
    }

    // Constructor for status with error message
    public OcrStatusVO(String status, String errorMessage) {
        this(status, errorMessage, null);
    }
}
