package com.hhs.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the alias-matching property that silently degraded user-visible output
 * four separate times: a switch arm written for a legacy alias ("glucose",
 * "temperature") never matches the canonical key, so the metric falls through to
 * a default. No exception is raised — the user just sees an English key, or a
 * score that reads better than the data warrants.
 *
 * <p>A registry of {@code null} is used deliberately: it forces the switch
 * fallback to execute, which is exactly the path that used to be broken. With a
 * registry present, {@code getDefinition} canonicalizes for us and hides the bug.</p>
 */
@DisplayName("指标显示格式化 - 规范键归一化")
class MetricDisplayFormatterTest {

    private final MetricDisplayFormatter formatter = new MetricDisplayFormatter(null);

    @Test
    @DisplayName("规范键与遗留别名都解析为同一中文名（血糖）")
    void displayName_glucoseAndCanonical_bothResolve() {
        assertEquals("血糖", formatter.getDisplayName("bloodGlucose"));
        assertEquals("血糖", formatter.getDisplayName("glucose"));
        assertEquals("血糖", formatter.getDisplayName("BLOOD_GLUCOSE"));
    }

    @Test
    @DisplayName("规范键与遗留别名都解析为同一中文名（体温）")
    void displayName_temperatureAndCanonical_bothResolve() {
        assertEquals("体温", formatter.getDisplayName("bodyTemperature"));
        assertEquals("体温", formatter.getDisplayName("temperature"));
        assertEquals("体温", formatter.getDisplayName("TEMP"));
    }

    @Test
    @DisplayName("单位查询同样归一化：别名不会落到空串")
    void unit_aliasesDoNotFallThroughToEmpty() {
        assertEquals("mmol/L", formatter.getUnit("bloodGlucose"));
        assertEquals("mmol/L", formatter.getUnit("glucose"));
        assertEquals("°C", formatter.getUnit("bodyTemperature"));
        assertEquals("°C", formatter.getUnit("temperature"));
    }

    @Test
    @DisplayName("未识别键原样返回，不抛异常")
    void displayName_unknownKey_passesThrough() {
        assertEquals("someUnknownKey", formatter.getDisplayName("someUnknownKey"));
        assertEquals("", formatter.getUnit("someUnknownKey"));
    }
}
