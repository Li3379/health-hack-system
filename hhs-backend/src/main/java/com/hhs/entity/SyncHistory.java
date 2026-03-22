package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备同步历史记录实体
 * 用于记录每次设备数据同步的结果
 */
@Data
@TableName("sync_history")
public class SyncHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 平台标识: huawei, xiaomi, wechat, apple
     */
    private String platform;

    /**
     * 同步类型: manual(手动), scheduled(定时)
     */
    private String syncType;

    /**
     * 同步的指标数量
     */
    private Integer metricsCount;

    /**
     * 同步状态: success, partial, failed
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 同步开始时间
     */
    private LocalDateTime startTime;

    /**
     * 同步结束时间
     */
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    private Integer durationMs;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 同步类型枚举
     */
    public enum SyncType {
        MANUAL("手动同步"),
        SCHEDULED("定时同步");

        private final String displayName;

        SyncType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 同步状态枚举
     */
    public enum SyncStatus {
        SUCCESS("成功"),
        PARTIAL("部分成功"),
        FAILED("失败");

        private final String displayName;

        SyncStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}