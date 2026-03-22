package com.hhs.service.alert;

/**
 * Trend direction enumeration for health metric prediction
 */
public enum TrendDirection {
    /**
     * Metric value is increasing over time
     */
    RISING("上升"),

    /**
     * Metric value is decreasing over time
     */
    FALLING("下降"),

    /**
     * Metric value remains relatively stable
     */
    STABLE("稳定");

    private final String label;

    TrendDirection(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Determine trend direction from slope value
     *
     * @param slope the slope from linear regression
     * @return the corresponding TrendDirection
     */
    public static TrendDirection fromSlope(double slope) {
        // Use a small threshold to determine if slope is significant
        double threshold = 0.01;
        if (slope > threshold) {
            return RISING;
        } else if (slope < -threshold) {
            return FALLING;
        } else {
            return STABLE;
        }
    }
}