package com.hhs.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 风险阈值注册表的声明测试。
 *
 * <p>这些断言的存在理由不是「类里有几个方法」，而是三种已经发生过的失败模式：
 * 规则键拼错（引擎静默 0 分、面板静默无 note）、规则被悄悄裁剪或顺手多加一条、
 * 以及把 (指标, 疾病) 二元组误当成单指标阈值。每一种都必须让这里变红。</p>
 */
@DisplayName("风险阈值注册表测试")
class RiskThresholdRegistryTest {

    private final RiskThresholdRegistry registry = new RiskThresholdRegistry();

    @Test
    @DisplayName("规则集恰好 4 条，消费键精确等于 4 个 canonical key")
    void declaresExactlyTheFourMigratedRules() {
        assertEquals(4, registry.allRules().size(),
                "本阶段只搬移引擎原本硬编码的 4 个阈值；多一条会声称引擎读了它没读的指标");

        // 精确集合而非 size：拼错一个字母会让集合少一个元素，多注册一个则多一个 ——
        // size 断言只能拦住后者之一半。
        assertEquals(Set.of("bloodGlucose", "systolicBP", "diastolicBP", "bmi"),
                registry.consumedKeys(),
                "consumedKeys 必须与引擎实际读取的指标一一对应");
    }

    @Test
    @DisplayName("四条规则的疾病/方向/阈值/分值逐条精确")
    void eachRuleCarriesTheMigratedValues() {
        assertRule("bloodGlucose", "糖尿病", RiskThresholdRegistry.Direction.ABOVE, "6.1", 30);
        assertRule("systolicBP", "高血压", RiskThresholdRegistry.Direction.ABOVE, "130", 35);
        assertRule("diastolicBP", "高血压", RiskThresholdRegistry.Direction.ABOVE, "85", 30);
        assertRule("bmi", "糖尿病", RiskThresholdRegistry.Direction.ABOVE, "24.0", 25);
    }

    @Test
    @DisplayName("阈值不得写成元数据参考上限（139/89/23.9 会让评分变松）")
    void thresholdsAreNotTheReferenceUpperBounds() {
        // 参考上限是 139 / 89 / 23.9；风险阈值必须严于它，否则 130-139 的人群从计 35 分变 0 分。
        assertEquals("130", registry.ruleFor("systolicBP", "高血压").orElseThrow()
                .threshold().toPlainString(), "收缩压风险阈值必须是 130，不是参考上限 139");
        assertEquals("85", registry.ruleFor("diastolicBP", "高血压").orElseThrow()
                .threshold().toPlainString(), "舒张压风险阈值必须是 85，不是参考上限 89");
        // bmi 是唯一的例外：24.0 比参考上限 23.9 更松 0.1，保留既有行为（见注册表注释）。
        assertEquals("24.0", registry.ruleFor("bmi", "糖尿病").orElseThrow()
                .threshold().toPlainString(), "BMI 阈值必须是既有行为 24.0");
    }

    @Test
    @DisplayName("每个消费键既是 canonical key，也是元数据注册表里的指标")
    void everyConsumedKeyIsBothCanonicalAndRegistered() {
        HealthMetricMetaRegistry metaRegistry = new HealthMetricMetaRegistry();
        for (String key : registry.consumedKeys()) {
            assertTrue(MetricKeyNormalizer.isCanonicalKey(key),
                    key + " 必须是 canonical key —— 否则引擎的别名匹配会恒落空且不报错");
            assertTrue(metaRegistry.contains(key),
                    key + " 必须是元数据注册表里的指标 —— canonical 但未注册同样是漏网");
        }
    }

    @Test
    @DisplayName("拼错的 key 在构造期就失败，而不是静默返回空规则集")
    void misspeltKeyFailsAtConstruction() {
        // 真实缺陷形态：SystolicBP 是 systolicBP 的大小写变体，isCanonicalKey 为 false，
        // 引擎的 getAliases 会经 simplify 把它归一，但规则表按原样存键就恒查不到。
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> RiskThresholdRegistry.requireCanonicalKey("SystolicBP"));
        assertTrue(thrown.getMessage().startsWith("风险阈值必须使用 canonical key"),
                "异常消息必须点明是 key 拼写问题，实际为: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("SystolicBP"),
                "消息必须带上出错的 key，否则启动失败时无从定位");

        // 合法的 canonical key 必须放行 —— 否则上面的断言在「全部抛异常」的实现下也会通过。
        RiskThresholdRegistry.requireCanonicalKey("systolicBP");
    }

    @Test
    @DisplayName("heartRate 不参与任何评估（D-08 的显式排除）")
    void heartRateIsNotConsumed() {
        assertTrue(registry.rulesFor("heartRate").isEmpty(),
                "D-08 明确把心率排除在评估输入之外；顺手加一条心率必须显式改测试");
        assertFalse(registry.consumes("heartRate"));
    }

    @Test
    @DisplayName("规则键是 (指标, 疾病) 二元组，不是单指标阈值")
    void rulesAreKeyedByMetricAndDisease() {
        assertTrue(registry.ruleFor("bmi", "糖尿病").isPresent());
        assertTrue(registry.ruleFor("bmi", "高血压").isEmpty(),
                "同一个指标在不同疾病下是不同的规则；按单指标存会让 16-03 的心血管 BMI 规则覆盖糖尿病那条");
    }

    @Test
    @DisplayName("rulesFor 返回不可变列表，且保持声明顺序")
    void rulesForIsImmutableAndOrdered() {
        List<RiskThresholdRegistry.RiskThresholdRule> rules = registry.rulesFor("bmi");
        assertEquals(1, rules.size());
        assertThrows(UnsupportedOperationException.class,
                () -> rules.add(rules.get(0)), "rulesFor 必须返回不可变列表");

        List<String> declared = registry.allRules().stream()
                .map(RiskThresholdRegistry.RiskThresholdRule::metricKey)
                .toList();
        assertEquals(List.of("bloodGlucose", "systolicBP", "diastolicBP", "bmi"), declared,
                "声明顺序就是 riskNote 的取用顺序，血糖必须在 BMI 之前");
    }

    private void assertRule(String key, String disease, RiskThresholdRegistry.Direction direction,
                            String threshold, int points) {
        RiskThresholdRegistry.RiskThresholdRule rule = registry.ruleFor(key, disease)
                .orElseThrow(() -> new AssertionError("缺少规则: " + key + "/" + disease));
        assertEquals(disease, rule.disease(), key + " 的疾病");
        assertEquals(direction, rule.direction(), key + " 的方向");
        // toPlainString 而非 compareTo：小数位形态（6.1 vs 6.10）也必须被钉住。
        assertEquals(threshold, rule.threshold().toPlainString(), key + " 的阈值");
        assertEquals(points, rule.points(), key + " 的分值");
    }
}
