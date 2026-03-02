package com.hhs.common.enums;

import lombok.Getter;

/**
 * Metric category enumeration for distinguishing medical (health) metrics from wellness metrics.
 *
 * <p>Categories are used to:
 * <ul>
 *   <li>Separate medical metrics (blood pressure, glucose) from wellness metrics (sleep, steps)</li>
 *   <li>Enable category-specific UI views and filtering</li>
 *   <li>Support different analysis and alerting rules per category</li>
 * </ul>
 */
@Getter
public enum MetricCategory {

    /**
     * Medical/health metrics - clinical measurements requiring medical attention
     * Examples: blood pressure, heart rate, glucose, BMI, temperature, weight
     */
    HEALTH("健康指标", "Health Metric"),

    /**
     * Wellness metrics - lifestyle and preventive health measurements
     * Examples: sleep duration, sleep quality, steps, exercise minutes, water intake, mood, energy
     */
    WELLNESS("保健指标", "Wellness Metric");

    private final String displayNameZh;
    private final String displayNameEn;

    MetricCategory(String displayNameZh, String displayNameEn) {
        this.displayNameZh = displayNameZh;
        this.displayNameEn = displayNameEn;
    }

    /**
     * Get the display name. Returns Chinese by default.
     *
     * @return Chinese display name
     */
    public String getDisplayName() {
        return displayNameZh;
    }

    /**
     * Get the display name for a specific language.
     *
     * @param lang Language code ("en" for English, any other value for Chinese)
     * @return Localized display name
     */
    public String getDisplayName(String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            return displayNameEn;
        }
        return displayNameZh;
    }

    /**
     * Check if this category is WELLNESS.
     *
     * @return true if this is WELLNESS category, false otherwise
     */
    public boolean isWellnessCategory() {
        return this == WELLNESS;
    }

    /**
     * Check if this category is HEALTH.
     *
     * @return true if this is HEALTH category, false otherwise
     */
    public boolean isHealthCategory() {
        return this == HEALTH;
    }
}