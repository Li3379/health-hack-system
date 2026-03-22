package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI解析历史记录实体
 * 用于记录用户通过AI智能录入的解析历史
 */
@Data
@TableName("ai_parse_history")
public class AiParseHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户输入文本
     */
    private String inputText;

    /**
     * 输入类型: text(文本), voice(语音)
     */
    private String inputType;

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
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 输入类型枚举
     */
    public enum InputType {
        TEXT("文本输入"),
        VOICE("语音输入");

        private final String displayName;

        InputType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}