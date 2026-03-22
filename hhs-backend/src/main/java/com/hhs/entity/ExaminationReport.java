package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("examination_report")
public class ExaminationReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String reportName;
    private String reportType;
    private String institution;
    private LocalDate reportDate;
    private String fileUrl;

    @TableField("ocr_status")
    private String ocrStatus;

    private String structuredData;
    private String abnormalSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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
