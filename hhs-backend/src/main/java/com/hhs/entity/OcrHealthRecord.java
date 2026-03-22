package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCR健康图片识别记录实体
 * 用于智能录入模块的OCR识别历史
 */
@Data
@TableName("ocr_health_record")
public class OcrHealthRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 识别类型: report(体检报告), medicine(药品标签), nutrition(营养标签)
     */
    private String ocrType;

    /**
     * 原始识别文本
     */
    private String rawText;

    /**
     * 解析结果（JSON格式）
     */
    private String parseResult;

    /**
     * 是否已确认录入
     */
    private Boolean confirmed;

    /**
     * 录入的指标ID列表（JSON数组）
     */
    private String metricIds;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 存储的文件名（UUID）
     */
    private String storedFilename;

    /**
     * 识别状态: success, failed
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 识别耗时(毫秒)
     */
    private Integer durationMs;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * OCR类型枚举
     */
    public enum OcrType {
        REPORT("体检报告"),
        MEDICINE("药品标签"),
        NUTRITION("营养标签");

        private final String displayName;

        OcrType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}