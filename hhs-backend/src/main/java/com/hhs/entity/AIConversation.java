package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI对话历史实体
 */
@Data
@TableName("ai_conversation")
public class AIConversation {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID（访客为null）
     */
    private Long userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 提问内容
     */
    private String question;
    
    /**
     * AI回答
     */
    private String answer;
    
    /**
     * Token消耗量
     */
    private Integer tokensUsed;
    
    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createTime;
}
