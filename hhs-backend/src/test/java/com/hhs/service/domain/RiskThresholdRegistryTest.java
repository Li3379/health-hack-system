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
    @DisplayName("规则集恰好 12 条，消费键精确等于 8 个 canonical key")
    void declaresExactlyTheFourMigratedRules() {
        // 4 条既有（16-01 迁移）+ 8 条心血管（16-03）。多一条会声称引擎读了它没读的指标；
        // 少一条则心血管的某项输入静默不计分。
        assertEquals(12, registry.allRules().size(),
                "4 条既有 + 8 条心血管 = 12；数量漂移意味着引擎的输入集与声明不符");

        // 精确集合而非 size：拼错一个字母会让集合少一个元素，多注册一个则多一个 ——
        // size 断言只能拦住后者之一半。
        assertEquals(
                Set.of("bloodGlucose", "systolicBP", "diastolicBP", "bmi",
                        "totalCholesterol", "triglycerides", "hdlCholesterol", "ldlCholesterol"),
                registry.consumedKeys(),
                "consumedKeys 必须与引擎实际读取的指标一一对应（8 个键，恰好不多不少）");
    }

    @Test
    @DisplayName("十二条规则的疾病/方向/阈值/分值逐条精确")
    void eachRuleCarriesTheMigratedValues() {
        // ---- 既有 4 条（16-01）----
        assertRule("bloodGlucose", "糖尿病", RiskThresholdRegistry.Direction.ABOVE, "6.1", 30);
        assertRule("systolicBP", "高血压", RiskThresholdRegistry.Direction.ABOVE, "130", 35);
        assertRule("diastolicBP", "高血压", RiskThresholdRegistry.Direction.ABOVE, "85", 30);
        assertRule("bmi", "糖尿病", RiskThresholdRegistry.Direction.ABOVE, "24.0", 25);

        // ---- 心血管 8 条（本计划）----
        assertRule("bmi", "心血管", RiskThresholdRegistry.Direction.ABOVE, "24.0", 20);
        assertRule("systolicBP", "心血管", RiskThresholdRegistry.Direction.ABOVE, "130", 30);
        assertRule("diastolicBP", "心血管", RiskThresholdRegistry.Direction.ABOVE, "85", 15);
        assertRule("bloodGlucose", "心血管", RiskThresholdRegistry.Direction.ABOVE, "6.1", 10);
        assertRule("totalCholesterol", "心血管", RiskThresholdRegistry.Direction.ABOVE, "5.2", 20);
        assertRule("triglycerides", "心血管", RiskThresholdRegistry.Direction.ABOVE, "1.7", 10);
        assertRule("hdlCholesterol", "心血管", RiskThresholdRegistry.Direction.BELOW, "1.0", 15);
        assertRule("ldlCholesterol", "心血管", RiskThresholdRegistry.Direction.ABOVE, "3.4", 15);
    }

    @Test
    @DisplayName("HDL 是唯一的 BELOW，其余 11 条都是 ABOVE")
    void onlyHdlIsLowerBound() {
        // 下限型不是风格选择：它决定归因句说「低于」还是「超过」。多一条 BELOW 会让某类指标
        // 对偏高的人说反话，少一条则 HDL 偏低的人被告知「超过」了它实际低于的阈值。
        assertRule("hdlCholesterol", "心血管", RiskThresholdRegistry.Direction.BELOW, "1.0", 15);

        List<RiskThresholdRegistry.RiskThresholdRule> belowRules = registry.allRules().stream()
                .filter(rule -> rule.direction() == RiskThresholdRegistry.Direction.BELOW)
                .toList();
        assertEquals(1, belowRules.size(),
                "只有 HDL 是下限型；其余 11 条都是上限型，实际为: " + belowRules);
        assertEquals("hdlCholesterol", belowRules.get(0).metricKey());
    }

    @Test
    @DisplayName("同一个指标在高血压与心血管的分值不同 —— 键是 (指标, 疾病) 二元组")
    void sameMetricScoresDifferentlyPerDisease() {
        // 35 vs 30 与 30 vs 15：若实现退化成「单指标阈值」，两处必然相等，这两条断言立刻红。
        assertEquals(35, registry.ruleFor("systolicBP", "高血压").orElseThrow().points());
        assertEquals(30, registry.ruleFor("systolicBP", "心血管").orElseThrow().points());
        assertEquals(30, registry.ruleFor("diastolicBP", "高血压").orElseThrow().points());
        assertEquals(15, registry.ruleFor("diastolicBP", "心血管").orElseThrow().points());
        assertEquals(25, registry.ruleFor("bmi", "糖尿病").orElseThrow().points());
        assertEquals(20, registry.ruleFor("bmi", "心血管").orElseThrow().points());
        assertEquals(30, registry.ruleFor("bloodGlucose", "糖尿病").orElseThrow().points());
        assertEquals(10, registry.ruleFor("bloodGlucose", "心血管").orElseThrow().points());
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
    @DisplayName("rulesFor 返回不可变列表；每个键的第一条规则就是面板展示的那条")
    void rulesForIsImmutableAndOrdered() {
        List<RiskThresholdRegistry.RiskThresholdRule> bmiRules = registry.rulesFor("bmi");
        assertEquals(2, bmiRules.size(), "bmi 有糖尿病与心血管两条规则");
        assertThrows(UnsupportedOperationException.class,
                () -> bmiRules.add(bmiRules.get(0)), "rulesFor 必须返回不可变列表");

        // 面板只描述每个键的第一条（UI-SPEC 5.2 禁止拼接多个疾病名），所以「第一条是谁」
        // 是用户可见的文案契约，不是无关的声明顺序。逐键钉住：
        assertEquals("心血管", registry.rulesFor("bmi").get(0).disease(),
                "bmi 的第一条必须是心血管 —— 面板要读「心血管评估：超过 24.0 kg/m² 计入 20 分」");
        assertEquals("糖尿病", registry.rulesFor("bloodGlucose").get(0).disease(),
                "血糖的第一条必须是糖尿病 —— 面板那条 6.1/30 的文案是既有的");
        assertEquals("高血压", registry.rulesFor("systolicBP").get(0).disease());
        assertEquals("高血压", registry.rulesFor("diastolicBP").get(0).disease());

        // 血脂四项各只有一条规则，都是心血管。
        for (String lipid : List.of("totalCholesterol", "triglycerides",
                "hdlCholesterol", "ldlCholesterol")) {
            assertEquals(1, registry.rulesFor(lipid).size(), lipid + " 应当只有心血管一条规则");
            assertEquals("心血管", registry.rulesFor(lipid).get(0).disease());
        }

        // 声明顺序的粗粒度守卫：有规则的键恰好这 8 个（顺序可能与上面逐键断言不同，
        // 但键集必须一致 —— 派生自同一份声明，不是第二份列表）。
        List<String> declaredKeys = registry.allRules().stream()
                .map(RiskThresholdRegistry.RiskThresholdRule::metricKey)
                .distinct()
                .toList();
        assertEquals(8, declaredKeys.size(), "实际声明顺序: " + declaredKeys);
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
