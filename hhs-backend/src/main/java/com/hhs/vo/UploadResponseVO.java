package com.hhs.vo;

import lombok.Builder;
import lombok.Data;

/**
 * File upload response VO
 * Provides type-safe response for file upload operations
 */
@Data
@Builder
public class UploadResponseVO {

    /**
     * File access URL (relative path)
     */
    private String url;

    /**
     * Saved filename (with UUID prefix)
     */
    private String filename;

    /**
     * File size in bytes
     */
    private Long size;
}
