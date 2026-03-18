package com.hhs.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Resolution strategy enumeration for handling conflicts during health metric synchronization.
 *
 * <p>When syncing health metrics from multiple devices or sources, conflicts may arise
 * when duplicate entries exist for the same user, metric type, and date. This enum
 * defines the strategies for resolving such conflicts.
 *
 * <p>Strategy selection is based on:
 * <ul>
 *   <li>Metric type characteristics (medical accuracy vs. activity tracking)</li>
 *   <li>Data source reliability (device vs. manual entry)</li>
 *   <li>Business requirements for each metric category</li>
 * </ul>
 */
@Getter
public enum ResolutionStrategy {

    /**
     * Keep the metric with the most recent timestamp.
     * Used when newer data is more reliable (e.g., blood pressure, glucose).
     * Compares recordDate timestamps and keeps the latest entry.
     */
    KEEP_NEWEST("keep_newest", "最新数据优先", "Keep Newest"),

    /**
     * Keep the metric with the highest value.
     * Used for activity metrics where devices may undercount (e.g., steps, active minutes).
     * The assumption is that a higher count from a device is more accurate.
     */
    KEEP_HIGHEST("keep_highest", "最高值优先", "Keep Highest"),

    /**
     * Keep the metric with the lowest value.
     * Used for medical metrics where lower values indicate better health (e.g., resting heart rate).
     * Also used for conservative estimates in critical health metrics.
     */
    KEEP_LOWEST("keep_lowest", "最低值优先", "Keep Lowest"),

    /**
     * Keep the existing metric and ignore the incoming value.
     * Used when existing data is verified or manually entered.
     * Prevents automatic overwriting of trusted data.
     */
    KEEP_EXISTING("keep_existing", "保留现有数据", "Keep Existing"),

    /**
     * Replace existing metric with the incoming value.
     * Used when incoming data is from a more reliable source.
     * Overwrites any previous value for the same metric and date.
     */
    KEEP_INCOMING("keep_incoming", "新数据覆盖", "Keep Incoming");

    /**
     * Value stored in database for persistence
     */
    @EnumValue
    @JsonValue
    private final String dbValue;

    private final String displayNameZh;
    private final String displayNameEn;

    ResolutionStrategy(String dbValue, String displayNameZh, String displayNameEn) {
        this.dbValue = dbValue;
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
     * Resolve a ResolutionStrategy from its dbValue.
     *
     * @param dbValue The database value to resolve
     * @return The matching ResolutionStrategy, or KEEP_NEWEST as default
     */
    public static ResolutionStrategy fromDbValue(String dbValue) {
        if (dbValue == null) {
            return KEEP_NEWEST;
        }
        for (ResolutionStrategy strategy : values()) {
            if (strategy.dbValue.equals(dbValue)) {
                return strategy;
            }
        }
        return KEEP_NEWEST;
    }
}