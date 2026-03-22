package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 删除操作日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delete_log")
public class DeleteLog {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 操作人昵称
     */
    private String operatorName;
    
    /**
     * 删除目标类型：TIP/COMMENT
     */
    private String targetType;
    
    /**
     * 删除目标ID
     */
    private Long targetId;
    
    /**
     * 目标标题/内容摘要
     */
    private String targetTitle;
    
    /**
     * 删除时间
     */
    private LocalDateTime deleteTime;
    
    /**
     * 操作IP地址
     */
    private String ipAddress;
    
    /**
     * 备注信息
     */
    private String remark;
}
