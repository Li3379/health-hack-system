package com.hhs.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Internal Health Data Point.
 * Represents a single health measurement extracted from an external device.
 *
 * <p>This DTO normalizes data from various health platforms (Huawei, Xiaomi, etc.)
 * into a consistent internal format for processing and storage.
 */
public record HealthDataPoint(
    /**
     * The internal metric key (e.g., "heartRate", "steps", "glucose").
     */
    String metricKey,

    /**
     * The measured value.
     */
    BigDecimal value,

    /**
     * The unit of measurement (e.g., "bpm", "steps", "mg/dL").
     */
    String unit,

    /**
     * The timestamp when the measurement was taken.
     */
    LocalDateTime recordTime,

    /**
     * Original data type from the source platform.
     */
    String sourceDataType,

    /**
     * Source platform identifier.
     */
    String sourcePlatform
) {
    /**
     * Create a health data point from a Huawei data point.
     *
     * @param huaweiPoint the Huawei data point
     * @param platform the platform identifier
     * @return normalized health data point
     */
    public static HealthDataPoint fromHuawei(
            HuaweiHealthDataResponse.HuaweiDataPoint huaweiPoint,
            String platform) {

        String metricKey = mapHuaweiDataType(huaweiPoint.dataType());
        BigDecimal value = huaweiPoint.getPrimaryValue() != null
                ? BigDecimal.valueOf(huaweiPoint.getPrimaryValue())
                : null;
        String unit = mapUnit(huaweiPoint.dataType(), huaweiPoint.unit());

        LocalDateTime recordTime = null;
        if (huaweiPoint.startTime() != null) {
            try {
                recordTime = LocalDateTime.ofInstant(
                        Instant.parse(huaweiPoint.startTime()),
                        ZoneId.systemDefault()
                );
            } catch (Exception e) {
                recordTime = LocalDateTime.now();
            }
        }

        return new HealthDataPoint(
                metricKey,
                value,
                unit,
                recordTime != null ? recordTime : LocalDateTime.now(),
                huaweiPoint.dataType(),
                platform
        );
    }

    /**
     * Map Huawei Health data types to internal metric keys.
     *
     * @param huaweiDataType the Huawei data type
     * @return the internal metric key
     */
    public static String mapHuaweiDataType(String huaweiDataType) {
        if (huaweiDataType == null) {
            return "unknown";
        }

        return switch (huaweiDataType.toLowerCase()) {
            case "heart_rate", "heartrate", "com.huawei.health.heart_rate" -> "heartRate";
            case "steps", "step_count", "com.huawei.health.step_count" -> "steps";
            case "sleep", "sleep_duration", "com.huawei.health.sleep" -> "sleepDuration";
            case "blood_pressure", "blood_pressure_systolic", "com.huawei.health.blood_pressure" -> "systolicBP";
            case "blood_pressure_diastolic" -> "diastolicBP";
            case "blood_glucose", "glucose", "com.huawei.health.blood_glucose" -> "glucose";
            case "spo2", "blood_oxygen", "com.huawei.health.spo2" -> "spo2";
            case "calories", "com.huawei.health.calories" -> "calories";
            case "distance", "com.huawei.health.distance" -> "distance";
            case "weight", "com.huawei.health.weight" -> "weight";
            case "height" -> "height";
            case "body_fat", "com.huawei.health.body_fat" -> "bodyFat";
            default -> huaweiDataType.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "_");
        };
    }

    /**
     * Map units for consistency.
     *
     * @param dataType the data type
     * @param originalUnit the original unit
     * @return the normalized unit
     */
    public static String mapUnit(String dataType, String originalUnit) {
        if (originalUnit != null && !originalUnit.isBlank()) {
            return originalUnit;
        }

        // Provide default units based on data type
        if (dataType == null) {
            return "";
        }

        return switch (dataType.toLowerCase()) {
            case "heart_rate", "heartrate" -> "bpm";
            case "steps", "step_count" -> "steps";
            case "sleep", "sleep_duration" -> "hours";
            case "blood_pressure", "blood_pressure_systolic", "blood_pressure_diastolic" -> "mmHg";
            case "blood_glucose", "glucose" -> "mg/dL";
            case "spo2", "blood_oxygen" -> "%";
            case "calories" -> "kcal";
            case "distance" -> "km";
            case "weight" -> "kg";
            case "height" -> "cm";
            case "body_fat" -> "%";
            default -> "";
        };
    }

    /**
     * Check if this data point has a valid value.
     */
    public boolean hasValidValue() {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * Check if this is a blood pressure measurement (requires systolic value).
     */
    public boolean isBloodPressureSystolic() {
        return "systolicBP".equals(metricKey);
    }

    /**
     * Check if this is a blood pressure diastolic measurement.
     */
    public boolean isBloodPressureDiastolic() {
        return "diastolicBP".equals(metricKey);
    }
}