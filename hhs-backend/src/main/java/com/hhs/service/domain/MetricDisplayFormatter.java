package com.hhs.service.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Metric display name and unit formatter
 * Responsible for providing human-readable metric names and units
 */
@Slf4j
@Component
public class MetricDisplayFormatter {

    /**
     * Get metric display name in Chinese
     *
     * @param metricKey the metric key
     * @return Chinese display name
     */
    public String getDisplayName(String metricKey) {
        return switch (metricKey) {
            // Health metrics - 健康指标
            case "heartRate" -> "心率";
            case "systolicBP" -> "收缩压";
            case "diastolicBP" -> "舒张压";
            case "glucose" -> "血糖";
            case "fastingGlucose" -> "空腹血糖";
            case "postprandialGlucose" -> "餐后血糖";
            case "bmi" -> "BMI";
            case "temperature" -> "体温";
            case "weight" -> "体重";
            case "bloodOxygen" -> "血氧";
            case "totalCholesterol" -> "总胆固醇";
            case "hdlCholesterol" -> "高密度脂蛋白";
            case "ldlCholesterol" -> "低密度脂蛋白";
            case "creatinine" -> "肌酐";
            case "uricAcid" -> "尿酸";
            case "hemoglobin" -> "血红蛋白";
            case "wbc" -> "白细胞";
            case "rbc" -> "红细胞";
            case "platelet" -> "血小板";
            // Wellness metrics - 保健指标
            case "sleepDuration" -> "睡眠时长";
            case "sleepQuality" -> "睡眠质量";
            case "steps" -> "步数";
            case "exerciseMinutes" -> "运动时长";
            case "waterIntake" -> "饮水量";
            case "mood" -> "心情";
            case "energy" -> "精力";
            default -> metricKey;
        };
    }

    /**
     * Get metric unit
     *
     * @param metricKey the metric key
     * @return unit string
     */
    public String getUnit(String metricKey) {
        return switch (metricKey) {
            // Health metrics - 健康指标
            case "heartRate" -> "次/分";
            case "systolicBP", "diastolicBP" -> "mmHg";
            case "glucose", "fastingGlucose", "postprandialGlucose" -> "mmol/L";
            case "temperature" -> "°C";
            case "weight" -> "kg";
            case "bmi" -> "";
            case "bloodOxygen" -> "%";
            case "totalCholesterol", "hdlCholesterol", "ldlCholesterol" -> "mmol/L";
            case "creatinine", "uricAcid" -> "μmol/L";
            case "hemoglobin" -> "g/L";
            case "wbc" -> "10^9/L";
            case "rbc" -> "10^12/L";
            case "platelet" -> "10^9/L";
            // Wellness metrics - 保健指标
            case "sleepDuration" -> "小时";
            case "sleepQuality" -> "级";
            case "steps" -> "步";
            case "exerciseMinutes" -> "分钟";
            case "waterIntake" -> "ml";
            case "mood" -> "级";
            case "energy" -> "级";
            default -> "";
        };
    }

    /**
     * Format alert message for exceeded threshold
     *
     * @param metricKey the metric key
     * @param value the current value
     * @param thresholdValue the threshold value
     * @param alertType the alert type (CRITICAL, WARNING)
     * @param direction the direction (HIGH, LOW)
     * @return formatted message
     */
    public String formatAlertMessage(String metricKey, java.math.BigDecimal value,
                                     java.math.BigDecimal thresholdValue,
                                     String alertType, String direction) {
        String displayName = getDisplayName(metricKey);
        String unit = getUnit(metricKey);
        String severity = "CRITICAL".equals(alertType) ? "严重警告" : "警告";
        String dirText = "HIGH".equals(direction) ? "过高" : "过低";

        if ("CRITICAL".equals(alertType)) {
            return String.format("您的%s数值为%.2f%s，%s严重警戒值%.2f%s，请立即关注！",
                    displayName, value, unit,
                    "HIGH".equals(direction) ? "超过" : "低于",
                    thresholdValue, unit);
        } else {
            return String.format("您的%s数值为%.2f%s，%s警戒值%.2f%s，请注意。",
                    displayName, value, unit,
                    "HIGH".equals(direction) ? "超过" : "低于",
                    thresholdValue, unit);
        }
    }
}
