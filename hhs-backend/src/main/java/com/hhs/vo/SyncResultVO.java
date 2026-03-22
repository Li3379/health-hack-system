package com.hhs.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步结果VO
 */
@Data
public class SyncResultVO {

    /**
     * 平台标识
     */
    private String platform;

    /**
     * 平台名称
     */
    private String platformName;

    /**
     * 同步状态
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 同步的指标数量
     */
    private Integer metricsCount;

    /**
     * 同步时间
     */
    private LocalDateTime syncTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 耗时（毫秒）
     */
    private Integer durationMs;

    /**
     * 创建成功结果
     */
    public static SyncResultVO success(String platform, int metricsCount, int durationMs) {
        SyncResultVO result = new SyncResultVO();
        result.setPlatform(platform);
        result.setPlatformName(getPlatformName(platform));
        result.setStatus("success");
        result.setStatusName("成功");
        result.setMetricsCount(metricsCount);
        result.setSyncTime(LocalDateTime.now());
        result.setDurationMs(durationMs);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static SyncResultVO failed(String platform, String errorMessage, int durationMs) {
        SyncResultVO result = new SyncResultVO();
        result.setPlatform(platform);
        result.setPlatformName(getPlatformName(platform));
        result.setStatus("failed");
        result.setStatusName("失败");
        result.setSyncTime(LocalDateTime.now());
        result.setErrorMessage(errorMessage);
        result.setDurationMs(durationMs);
        return result;
    }

    private static String getPlatformName(String platform) {
        return switch (platform) {
            case "huawei" -> "华为运动健康";
            case "xiaomi" -> "小米运动";
            case "wechat" -> "微信运动";
            case "apple" -> "Apple Health";
            default -> platform;
        };
    }
}