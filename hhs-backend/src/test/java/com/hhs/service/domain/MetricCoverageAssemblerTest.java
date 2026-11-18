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
import java.util.regex.Pattern;
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
    private RiskThresholdRegistry riskThresholds;

    @BeforeEach
    void setUp() {
        riskThresholds = new RiskThresholdRegistry();
        assembler = new MetricCoverageAssembler(new HealthMetricMetaRegistry(), riskThresholds);
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
    @DisplayName("只有真正参与评估的八个指标带 riskNote")
    void onlyEngineInputsCarryRiskNote() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of());

        List<String> withNote = rows.stream()
                .filter(r -> r.riskNote() != null)
                .map(MetricCoverageVO::key)
                .sorted()
                .toList();
        // 字面量：钉住「今天恰好是这 8 个」。16-01 当时写成 4 键，本计划扩到 8 键 ——
        // 这行不跟着改就会在规则表扩到 8 条的那一刻变成永久红。
        assertEquals(List.of("bloodGlucose", "bmi", "diastolicBP", "hdlCholesterol",
                        "ldlCholesterol", "systolicBP", "totalCholesterol",
                        "triglycerides"),
                withNote,
                "riskNote 必须与引擎实际读取的指标一致，多一个会误导用户，少一个会漏报");

        // 同源派生：面板声称参与评估的键，必须恰好是注册表声明的那一组。
        // 与上面那条字面量断言并存不是重复 —— 字面量证明「今天的键集正确」，
        // 这一条证明「下一阶段扩规则时测试自己会说话」，任何一侧漂移都会红。
        List<String> derivedFromRegistry = riskThresholds.consumedKeys().stream().sorted().toList();
        assertEquals(derivedFromRegistry, withNote,
                "面板的 riskNote 键集必须与注册表声明的那一组完全一致，而不是各自维护");

        // 反向：这些指标确实不被引擎读取，绝不能声称它们参与评估（D-08 的守卫）。
        for (String unused : List.of("heartRate", "steps", "sleepDuration", "uricAcid")) {
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

    @Test
    @DisplayName("每条 riskNote 都符合唯一形状与 24 字上限，且三条迁移文案逐字节不变")
    void riskNotesMatchTheSinglePermittedShapeAndLength() {
        List<MetricCoverageVO> rows = assembler.assemble(List.of());
        Pattern shape = Pattern.compile("^[^：]+评估：(超过|低于|超出) .+ 计入 \\d+ 分$");

        for (MetricCoverageVO row : rows) {
            String note = row.riskNote();
            if (note == null) {
                continue;
            }
            assertTrue(shape.matcher(note).matches(),
                    row.key() + " 的 riskNote 形状不符（唯一允许的形状见 UI-SPEC 5.2）: " + note);
            // 计字口径：先去空白，再按码点计长。必须先把去空白结果赋给变量 ——
            // 写成 note.replaceAll(...).codePointCount(0, note.length()) 会用未去空白的长度
            // 当 endIndex，恒抛 StringIndexOutOfBoundsException，那种失败会掩盖真正的问题。
            String compact = note.replaceAll("\\s", "");
            int length = compact.codePointCount(0, compact.length());
            assertTrue(length <= 24, row.key() + " 的 riskNote 去空白后为 " + length + " 字，超过上限 24: " + note);
        }

        // 分隔符守卫：形状正则的 `.+` 会吞掉 `6.1mmol/L`，长度上限也放行（少一个空格只是更短），
        // 所以只有整串相等能钉住阈值与单位之间那一个空格。三条迁移文案逐字照抄改动前的
        // MetricCoverageAssembler 硬编码字面量，bmi 是新增键、无迁移前形态，故不参与本断言。
        assertEquals("糖尿病评估：超过 6.1 mmol/L 计入 30 分", find(rows, "bloodGlucose").riskNote());
        assertEquals("高血压评估：超过 130 mmHg 计入 35 分", find(rows, "systolicBP").riskNote());
        assertEquals("高血压评估：超过 85 mmHg 计入 30 分", find(rows, "diastolicBP").riskNote());
        assertFalse(find(rows, "bloodGlucose").riskNote().contains("6.1mmol/L"),
                "阈值与单位之间必须有空格，模板是唯一能提供它的地方");
    }

    @Test
    @DisplayName("riskNote 不承载来源出处")
    void riskNotesCarryNoProvenance() {
        // 来源禁令的可执行否定守卫：形状正则放行括号注（例如「超过 24（档）kg/m²」），
        // 只有否定断言能拦住它。出处由 .risk-estimate__source-origin 表达，不在本字段。
        Pattern provenance = Pattern.compile("(健康档案|档案|来源|取自)");

        for (MetricCoverageVO row : assembler.assemble(List.of())) {
            String note = row.riskNote();
            if (note == null) {
                continue;
            }
            assertFalse(provenance.matcher(note).find(),
                    row.key() + " 的 riskNote 不得承载来源信息: " + note);
        }
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
