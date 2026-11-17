package com.hhs.service.domain;

import com.hhs.entity.HealthMetric;
import com.hhs.vo.MetricCoverageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指标覆盖面板的判定测试。
 *
 * <p>这张表存在的唯一理由，是把「测了且正常」与「根本没测」分开。因此最重要的断言不是
 * 状态枚举齐全，而是：缺失项**永远**不能被渲染成正常，且它不会被误报为「参与评估」。</p>
 */
@DisplayName("指标覆盖面板测试")
class MetricCoverageAssemblerTest {

    private MetricCoverageAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new MetricCoverageAssembler(new HealthMetricMetaRegistry());
    }

    @Test
    @DisplayName("每个注册表指标都出现且只出现一次")
    void coversEveryRegisteredMetricExactlyOnce() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of());

        Map<String, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(MetricCoverageVO::key, Collectors.counting()));
        assertEquals(rows.size(), counts.size(), "同一个 key 不得重复出现");
        assertTrue(rows.size() >= 20, "注册表有 23 项指标，实际为 " + rows.size());
    }

    @Test
    @DisplayName("没有任何数据时全部标 MISSING，绝不标 NORMAL")
    void emptyMetricsAreAllMissing() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of());

        for (MetricCoverageVO row : rows) {
            assertEquals(MetricCoverageVO.STATE_MISSING, row.state(),
                    row.label() + " 没有数据时必须标 MISSING —— 否则「没测」会被读成「正常」");
            assertNull(row.value(), row.label() + " 缺失项不得伪造取值");
        }
    }

    @Test
    @DisplayName("有数据且超上限标 HIGH，低于下限标 LOW，区间内标 NORMAL")
    void classifiesAgainstRegistryRange() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of(
                metric("systolicBP", 168),
                metric("heartRate", 45),
                metric("diastolicBP", 80)));

        assertEquals(MetricCoverageVO.STATE_HIGH, find(rows, "systolicBP").state());
        assertEquals(MetricCoverageVO.STATE_LOW, find(rows, "heartRate").state());
        assertEquals(MetricCoverageVO.STATE_NORMAL, find(rows, "diastolicBP").state());
    }

    @Test
    @DisplayName("别名 key 也能被识别（legacy glucose 不应漏判）")
    void acceptsLegacyAliasKeys() {
        // 旧行可能存的是 glucose 而不是 bloodGlucose；漏判会让「有数据」显示成「未测」。
        List<MetricCoverageVO> rows = assembler.assemble(List.of(metric("glucose", 13.8)));

        MetricCoverageVO sugar = find(rows, "bloodGlucose");
        assertEquals(MetricCoverageVO.STATE_HIGH, sugar.state(),
                "别名 glucose 必须归到 bloodGlucose 并判为 HIGH");
        assertNotNull(sugar.value(), "别名行必须带出取值");
    }

    @Test
    @DisplayName("同一指标多条记录取最新（列表已按日期倒序）")
    void takesLatestValuePerMetric() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of(
                metric("systolicBP", 168),
                metric("systolicBP", 120)));

        assertEquals(MetricCoverageVO.STATE_HIGH, find(rows, "systolicBP").state(),
                "必须取第一条（最新），否则用户会看到旧值");
    }

    @Test
    @DisplayName("只有真正参与评估的三个指标带 riskNote")
    void onlyEngineInputsCarryRiskNote() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of());

        List<String> withNote = rows.stream()
                .filter(r -> r.riskNote() != null)
                .map(MetricCoverageVO::key)
                .sorted()
                .toList();
        assertEquals(List.of("bloodGlucose", "diastolicBP", "systolicBP"), withNote,
                "riskNote 必须与引擎实际读取的指标一致，多一个会误导用户，少一个会漏报");

        // 反向：这些指标确实不被引擎读取，绝不能声称它们参与评估。
        for (String unused : List.of("heartRate", "bmi", "steps", "sleepDuration", "uricAcid")) {
            assertNull(find(rows, unused).riskNote(),
                    unused + " 不参与风险评估，不得标注 riskNote");
        }
    }

    @Test
    @DisplayName("缺失的参与指标仍带 riskNote，让用户知道「这项本该参与但没数据」")
    void missingEngineInputStillNamesItsRisk() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of());

        MetricCoverageVO systolic = find(rows, "systolicBP");
        assertEquals(MetricCoverageVO.STATE_MISSING, systolic.state());
        assertNotNull(systolic.riskNote(),
                "缺失的高血压输入必须仍标注它会影响高血压评估，否则用户不知道该补什么");
        assertTrue(systolic.riskNote().contains("高血压"));
    }

    @Test
    @DisplayName("参考范围被格式化输出，供用户对照")
    void formatsReferenceRange() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of(metric("bloodGlucose", 5)));

        MetricCoverageVO sugar = find(rows, "bloodGlucose");
        assertNotNull(sugar.normalRange(), "有参考范围的指标必须输出范围");
        assertTrue(sugar.normalRange().contains("3.9") && sugar.normalRange().contains("6.1"),
                "实际范围: " + sugar.normalRange());
    }

    @Test
    @DisplayName("HEALTH 分组排在 WELLNESS 之前")
    void healthMetricsSortFirst() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of());

        int lastHealth = -1;
        int firstWellness = Integer.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            if ("HEALTH".equals(rows.get(i).category())) {
                lastHealth = Math.max(lastHealth, i);
            } else {
                firstWellness = Math.min(firstWellness, i);
            }
        }
        assertTrue(lastHealth < firstWellness,
                "HEALTH 指标必须全部排在 WELLNESS 之前，便于扫读体检数据");
    }

    private MetricCoverageVO find(List<MetricCoverageVO> rows, String key) {
        return rows.stream().filter(r -> r.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("未找到指标: " + key));
    }

    private HealthMetric metric(String key, double value) {
        HealthMetric m = new HealthMetric();
        m.setMetricKey(key);
        m.setValue(BigDecimal.valueOf(value));
        m.setRecordDate(LocalDate.now());
        return m;
    }
}
