package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ocr_recognize_log")
public class OcrRecognizeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    private String status;
    private String rawText;
    private BigDecimal confidence;
    private Integer durationMs;
    private String errorMessage;
    private LocalDateTime createTime;

    /**
     * Original filename provided by user (for display purposes only)
     */
    @TableField("original_filename")
    private String originalFilename;

    /**
     * Safe UUID-based filename used for disk storage
     */
    @TableField("stored_filename")
    private String storedFilename;
}
