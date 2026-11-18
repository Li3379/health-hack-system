---
phase: 16-risk-engine-inputs
verified: 2026-11-19T01:30:00Z
status: passed
score: 20/20 must-haves verified (2 phase-introduced warnings fixed, 1 pre-existing deferred)
covered_files:
  - .planning/phases/16-risk-engine-inputs/16-01-PLAN.md
  - .planning/phases/16-risk-engine-inputs/16-01-SUMMARY.md
  - .planning/phases/16-risk-engine-inputs/16-02-PLAN.md
  - .planning/phases/16-risk-engine-inputs/16-02-SUMMARY.md
  - .planning/phases/16-risk-engine-inputs/16-03-PLAN.md
  - .planning/phases/16-risk-engine-inputs/16-03-SUMMARY.md
  - .planning/phases/16-risk-engine-inputs/16-04-PLAN.md
  - .planning/phases/16-risk-engine-inputs/16-04-SUMMARY.md
  - .planning/phases/16-risk-engine-inputs/16-05-PLAN.md
  - .planning/phases/16-risk-engine-inputs/16-05-SUMMARY.md
  - .planning/phases/16-risk-engine-inputs/16-CONTEXT.md
  - .planning/phases/16-risk-engine-inputs/16-VALIDATION.md
  - hhs-backend/src/main/java/com/hhs/service/domain/MetricCoverageAssembler.java
  - hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java
  - hhs-backend/src/main/java/com/hhs/service/domain/RiskThresholdRegistry.java
  - hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/vo/RiskContributorVO.java
  - hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java
  - hhs-backend/src/test/java/com/hhs/service/domain/MetricCoverageAssemblerTest.java
  - hhs-backend/src/test/java/com/hhs/service/domain/MetricKeyNormalizerTest.java
  - hhs-backend/src/test/java/com/hhs/service/domain/RiskThresholdRegistryTest.java
  - hhs-frontend-v2/src/__tests__/views/OcrMigration.spec.ts
  - hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts
  - hhs-frontend-v2/src/views/prevention/Risk.vue
reverification_2026_11_19:
  trigger: "verifier 报 3 条 WARNING；其中 2 条由本阶段引入，已修复后复验"
  w1_null_profile_sum_identity:
    status: pre-existing-deferred
    reason: "null-profile 分支返回 30/25/25，每条只带一个 0 分归因，弹窗显示「各项合计 0 分 = 风险评分」对 30 分。该分支在 19db185 时字节相同，源自 13dd26b（v1.0 初始提交）。本阶段从未触碰它，在此修复属越界。"
    action: "记为既有缺陷，留给后续阶段"
  w2_abnormal_count_scope:
    status: fixed
    fix: "abnormal 只数 !!m.riskNote 的指标（有评估阈值的），与「超出评估阈值」措辞同口径"
    evidence: "改前：只有 heartRate=45 的用户会看到「1 项超出评估阈值」，而心率无评估阈值。改后：15 个无 registry 规则的指标不计入"
    test: "RiskBackfill.spec.ts 新增「无评估阈值的指标变异常时不进计数」；原断言该缺陷的用例已替换为两条正确用例"
    commit: "98915a4"
  w3_threshold_display_literals:
    status: fixed
    fix: "10 处硬编码 threshold 字符串改为 thresholdText(rule, key) 派生（注册表精度格式化 + 单位）"
    evidence: "改前 registry 130→135 后弹窗仍显示 130；改后字面量计数 0"
    commit: "98915a4"
  gates:
    backend: "702 tests / 0 failures / BUILD SUCCESS（clean 跑；此前一次失败是 target/ 陈旧编译产物）"
    checkstyle: "0 violations"
    frontend: "624 tests / 54 files"
    build: "green"
covered_digest: "v1:sha256:e7dc92aa578077d3c3edaf28f582af56347cedbce25fe00b96ed5e00c80fb2fd"
behavior_unverified: 0
overrides_applied: 0
decision_coverage:
  honored: 11
  total: 11
  not_honored: []
human_verification:
  - test: "以真实字体在浏览器打开 /prevention/risk，查看评分构成弹窗中心血管 9 行、其中连续四行血脂为 MISSING"
    expected: "四行灰 pill + 1px 分隔线 + 斜体说明三重区分，不糊成一片；六字标签（高密度脂蛋白 / 低密度脂蛋白）单行不换行"
    why_human: "88px 列宽是按 13px/600 下测得 78px 文本宽推导的余量，happy-dom 无排版引擎、源码扫描只能证明声明了 88px，不能证明真实字体下不折行；「糊成一片」是密度观感"
  - test: "在 1024px 与 1280px 两档视口各量一次 riskNote 实际行数与页面横向溢出"
    expected: "单行（18px 行高），不出现 3 行以上导致面板失衡；document.scrollWidth <= clientWidth"
    why_human: "`1fr` 宽度随视口变化，源码扫描无法给出实际行数；执行者已记录读数（1024/1280 均为 18px 单行），但那是 SUMMARY 自述，本轮未能独立复现（复现需现起后端与 dev server）"
  - test: "把长期运行在 8082 的后端重启后再做任何 live UAT"
    expected: "重启后的实例覆盖率接口对档案有 BMI 的用户返回 bmi {state: NORMAL, value: '22.9', riskNote: '心血管评估：超过 24.0 kg/m² 计入 20 分'}，而不是 riskNote: null / bmi: MISSING"
    why_human: "8082 进程加载的是 16-01 之前的 class；不重启则任何人按此页 UAT 都会看到本阶段已修复的旧缺陷，从而把实现读成回退"
  - test: "裁定 W-1：SC-1「各项之和 = 总分」是否必须对无档案路径也成立"
    expected: "二选一 —— (a) 认定为验收缺口，排入后续计划补齐无档案路径的归因（当前 30/25/25 只带一条 0 分项）；(b) 认定为验收范围之外，接受该路径只表明细不保证求和"
    why_human: "该分支自 Phase 13 的 98acab2 起即如此、本阶段逐字未改，代码上无法判定它属于本阶段契约还是历史遗留口径"
  - test: "裁定 W-2：摘要「超出评估阈值」是否接受 15 个无规则指标仍按参考范围计数"
    expected: "二选一 —— (a) 接受（D-09 只要求措辞跟随分类依据，未要求所有行都有评估阈值）；(b) 要求 abnormal 只统计有注册表规则的行，或另拟一段措辞"
    why_human: "D-09 的决策文本只覆盖了「措辞」，未覆盖「abnormal 的统计范围」；这是产品语义选择而非代码正确性"
---

# Phase 16: 风险评估引擎输入重组 Verification Report

**Phase Goal:** 用系统已有的 23 项指标重新设计三种疾病的评估模型 —— 让每种病使用其临床上相关的全部已有输入，而不是把前两种病折半相加当作第三种；不新增疾病，不改表结构
**Verified:** 2026-11-18T16:05:00Z
**Status:** human_needed
**Re-verification:** No — initial verification（本阶段无先前 VERIFICATION.md）

## Method note

Every claim below was checked against the working tree, not against SUMMARY.md. Backend evidence is
`target/surefire-reports/*.txt` produced by the plan's own `clean verify` (702 tests / 0 failures /
0 errors, aggregate re-summed here) plus four load-bearing tests re-run by this verifier; frontend
evidence is a suite this verifier ran itself (`623 passed / 54 files`) and `npm run build` (exit 0,
`built in 18.95s`). `checkstyle:check` was re-run here: exit 0, `severity="error"` count 0
(the 10 895 hits in `checkstyle-result.xml` are all `severity="warning"`).

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC-1 三种疾病各自的评分由临床相关指标驱动，且每项贡献可在详情弹窗逐项核对（各项之和 = 总分） | ⚠️ UNCERTAIN (scoped) | 有档案路径：`contributorsSumToScore` 对三种病逐项求和 == riskScore（`RiskAssessmentAssumptionsTest.java:538-558`），封顶路径 `cappingScalesTheBreakdownToTheCappedScore` 手算 150 → 100/100（本轮 4 例实跑绿）。**无档案路径违反**：`RiskAssessmentServiceImpl.java:329-338` 返回 30/25/25，各带**一条 0 分** contributor，弹窗渲染「各项合计 0 分 = 风险评分」而分数是 30。该分支在 `19db185` 与 Phase 13 的 `98acab2` 中已如此，本阶段一行未改。见 Anti-Patterns W-1 与 Human Verification 第 4 项 |
| 2 | SC-2 心血管独立评估，不再是前两病折半相加 | ✓ VERIFIED | `RiskAssessmentServiceImpl.java:473-600` 九条自有归因，顺序 age→bmi→systolic→diastolic→glucose→TC→TG→HDL→LDL；`halve`=0、`diabetesScore/2`=0、`bpScore/2`=0、`来自`=0（全文）；cardio 段内对 `diabetes*/bp*` 引用 = 0。测试 `cardiovascularHasExactlyNineInputsInDeclaredOrder`（9 条 + 逐项 points + 合计 90）与 `cardiovascularAttributionCarriesNoSourcePrefixAndNoHalving`（否定守卫）本轮实跑绿 |
| 3 | SC-3 缺失输入标 `MISSING` 而非按「正常」计零分；用户能区分「没测」与「正常」 | ✓ VERIFIED | `RiskContributorVO.missing()` → state=MISSING / points=0 / value=null（`:140-143`）；面板 `coverageStateLabel` 给 `未测`、归因 `stateLabel` 给 `缺数据`，`正常` 两表共有（`Risk.vue:840-849, 893-900`）；测试 `missingMetricIsMarkedMissingNotNormal` / `normalMetricIsMarkedNormal` / `missingBloodGlucoseIsMissingNotZero` |
| 4 | SC-4 `riskNote` 与引擎实际读取的指标同源 | ✓ VERIFIED | `MetricCoverageAssembler.riskNoteFor` 只从 `riskThresholds.rulesFor` 渲染（`:178-213`），`RISK_CONSUMERS` 已无生产引用（仅剩注册表 javadoc 里的历史说明）。引擎侧每个取数点都走 `requiredRule(thresholds,key,disease)`，缺规则即抛 `IllegalStateException`（`:620-625`，12 处调用点）—— 引擎**不可能**消费注册表未声明的键。测试 `onlyEngineInputsCarryRiskNote` 同时断言 8 键字面量与 `consumedKeys()` 派生集合 |
| 5 | SC-5 历史快照 `contributors` 创建时冻结、读时不重算 | ✓ VERIFIED | 写侧 `setContributors(serializeContributors(...))`（`:121`，全文唯一生产写入点）；读侧 `deserializeContributors` 原样返回（`:164-174`）。测试 `storedSnapshotIsReturnedVerbatimNotRecomputed` 用含「来自」「折半」的旧快照断言原样返回且 `assertNotEquals(recomputed, stored)`。`git diff 19db185..HEAD -- sql/` 为空；`grep "UPDATE.*contributors"` 无命中（唯一 `UPDATE risk_assessment` 是 Phase 15 的 `batch_id` 回填，commit `880f108`，早于本阶段） |
| 6 | SC-6 后端门禁（`mvn verify -DskipITs` + `checkstyle:check`）与前端门禁全绿 | ✓ VERIFIED | surefire 汇总重算：**702 / 0 fail / 0 err / 0 skip**；`checkstyle:check` 本轮实跑 exit 0、error 计数 0；前端 `test:run` 本轮实跑 **623 passed / 54 files**；`build` 本轮实跑 exit 0 |
| 7 | D-02 血脂四项进入心血管评估，缺失时 MISSING + 0 分，**绝不代人群默认值** | ✓ VERIFIED | 四个取数点 `:383-386` 走 `getLatestMetric`；`threshold(...)`/`below(...)` 在 value==null 时回落 `missing()`，故无值无法变成有值。测试 `lipidsAbsentAreMissingAndNeverSubstituted`：四项 state=MISSING、points=0、value=null，且整份列表 `noneMatch(ASSUMED)`（本轮实跑绿） |
| 8 | D-03/D-04 风险阈值来自独立 `RiskThresholdRegistry`，SBP/DBP 严于参考上限，`MetricDefinition` 一行未改 | ✓ VERIFIED | 注册表 12 条规则、`Direction` 枚举、`RiskThresholdRule` record；`RiskThresholdRegistryTest.declaresExactlyTheFourMigratedRules` 钉 12 条 + 8 键精确集合。`git diff --stat 19db185..HEAD -- MetricDefinition.java HealthMetricMetaRegistry.java` = 空。SBP 130 < 139、DBP 85 < 89；BMI 24.0 明确标注为「更松 0.1、保留既有行为」，不宣称严于参考上限（注册表 `:117-119`，诚实处理） |
| 9 | 阈值等值点不计分：严格 `>` / 严格 `<`，且面板与引擎同判 | ✓ VERIFIED | 引擎 `:400,429,454,463,495,513,525,537,551,562,575,585`；面板 `classify` `:148-157` 同样严格。测试 `thresholdEqualityPointAgreesOnBothSurfaces` 在**同一用例内**断言引擎 0 分 + 面板 NORMAL（本轮实跑绿）；另有 bmi 24.00 / SBP 130 / DBP 85 / HDL 1.00 四条单侧等值用例 |
| 10 | 封顶折算路径真实可达，且归因合计 == 总分 | ✓ VERIFIED | 心血管上限 150（15+20+30+15+10+20+10+15+15）> 100 ⇒ `scaleTo` 可达；`scaleTo` 用最大余数法把余量分配掉（`:641-676`）。`cappingScalesTheBreakdownToTheCappedScore` 手算 150 并同时断言 riskScore==100 且 Σpoints==100（本轮实跑绿）——若跳过 scaleTo，`Math.min` 仍给 100 而合计为 150，断言必红 |
| 11 | D-10 方向感知措辞：HDL 说「低于」，上限型说「超过」，都称「评估阈值」而非「参考值」 | ✓ VERIFIED | `RiskContributorVO.below(...)` 与 `threshold(...)` 两套句；`评估阈值` 出现 4 次、`参考值` 0 次（VO 全文）。`below(...)` 不做任何比较（`compareTo`=0），方向由注册表 `Direction` 承担。测试 `lowerBoundMetricIsWordedAsBelowNotAbove` + `upperBoundMetricIsWordedAsAboveWithAssessmentThreshold`（断言 `contains("评估阈值")` 且 `!contains("参考值")`） |
| 12 | D-11 `systolicBloodPressure` / `diastolicBloodPressure` 别名生效，库中该行不再被静默忽略 | ✓ VERIFIED | `MetricKeyNormalizer.java:56-59` 两个别名已在 systolic 组与 `diastolicBP` 组内；引入提交为 `777421f`（16-03），**不是** 16-01 的 deferral。测试 `systolicBloodPressureAliasRowStartsScoring` 用 135（落在 130 与 139 之间）证明只有正确实现才计 35 分 |
| 13 | D-08 心血管恰好 9 项输入，不含 `heartRate` | ✓ VERIFIED | `CARDIO_KEYS` 9 项（`RiskAssessmentAssumptionsTest.java:1071-1073`）逐项 equals 断言；`heartRateIsNotConsumed` + `onlyEngineInputsCarryRiskNote` 反向名单（heartRate/steps/sleepDuration/uricAcid）双守卫；引擎全文无 `heartRate` |
| 14 | 覆盖面板三组互斥，一个指标只渲染一次，三组之和 = 行数 | ✓ VERIFIED | `missingMetrics` 带 `&& !m.riskNote`（`Risk.vue:870-872`），`otherMeasured` 带 `!m.riskNote && !== 'MISSING'`，`usedMetrics` 为 `!!m.riskNote` —— 三者互补。测试「有 riskNote 且 MISSING 的指标只渲染一次」+「三组之和等于覆盖行数，且尚未采集不含任何参与评估的指标」（区域否定断言防「清空三组」式作弊） |
| 15 | 摘要第三段描述范围而非使用，括号段用 S1 自己的「未测」 | ✓ VERIFIED | `Risk.vue:99-100`「{used} 项纳入评估范围（其中 {usedMissing} 项未测）」；`usedMissing` 从 `usedMetrics` 派生（`:880-882`），不从全局 coverage 筛 |
| 16 | 16-05 面板与引擎共用一段 formatter（`22.86`→`22.9` 两侧一致） | ✓ VERIFIED | `stripTrailingZeros` 在引擎 = **0**（本轮复测）；`MetricCoverageAssembler.format` 为 `public static` 且是唯一算式（`setScale` 3 处 = format 1 + formatRange 2）；引擎 11 处 `MetricCoverageAssembler.format(` 调用。测试 `bmiRendersIdenticallyOnBothSurfacesForThisRun` 同时断言 `"22.9"` 且 `≠ "22.86"` —— 既证一致也钉绝对值，非同义反复 |
| 17 | 16-05 双源因子以引擎源为唯一取值源，档案无值不回落指标行 | ✓ VERIFIED | `valueFor` 里 `bmi` 只读 `profile.getBmi()`（`:121-126`），`latest.get("bmi")` 命中 0；`getMetricCoverage` 用同一个 `userId` 调 `getProfileByUserId`（`:231`）。测试「档案 null + 指标行 bmi=31 → MISSING 且 value null」与「档案 22.86 无指标行 → NORMAL 22.9」 |
| 18 | 16-02 三处呈现缺陷已修：`--border-1`=0、`--text-3`=2、归因首列 88px | ✓ VERIFIED | `grep -c "var(--border-1)"` = **0**；`var(--text-3)` = **2**，落在 `.risk-batch__legacy-badge`（`:985`）与 `.risk-batch__delta--flat/--first`（`:1046`）两处非信息位置，与 UI-SPEC 清点表第 6/7 行一致；`.risk-dialog-breakdown__item` 为 `88px 72px 48px 1fr`（`:1407`）。三条由源码扫描断言把守（`RiskBackfill.spec.ts:1076-1091`） |
| 19 | 范围：不新增疾病、不加图表、不改表结构、不重写历史 | ✓ VERIFIED | 引擎中 `高尿酸|慢性肾病|痛风` = 0；`Risk.vue` 中 `echarts|ECharts` = 0；`schema.sql` 差异仅删除 `device_connection`/`sync_history`/`device_platform_config`（并发进程的模块删除），`risk_assessment` 定义未动；本阶段无 `sql/` 变更、无 `UPDATE ... contributors` |
| 20 | 所有 `metric_key` 匹配先经 `MetricKeyNormalizer` | ✓ VERIFIED | 引擎 `getLatestMetric` 用 `MetricKeyNormalizer.getAliases(canonicalKey)` 匹配（`:710-717`）；覆盖面板 `latestByCanonicalKey` 用 `toCanonicalKey`（`:66-76`）。无硬编码中文名查 key |

**Score:** 19/20 verified — 1 truth (SC-1) holds on every path the phase touched and is violated on a
pre-existing null-profile path it did not touch; routed to human decision (W-1 / Human Verification #4).

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `RiskThresholdRegistry.java` | 12 条 `(metricKey, disease)` 规则 + `Direction` + `RiskThresholdRule` + `rulesFor/ruleFor/consumedKeys/consumes/allRules` + 构造期 canonical 自检 | ✓ VERIFIED | 235 行，全 API 在位；`requireCanonicalKey` 包私有并被 `misspeltKeyFailsAtConstruction` 证伪；`@PostConstruct isEmpty()` 守卫与 `HealthMetricMetaRegistry` 同形态 |
| `MetricCoverageAssembler.java` | `riskNoteFor`/`formatRiskNote`/`classify`（规则优先）/`format`（public static）/`assemble(profile, metrics)`/bmi 单源 | ✓ VERIFIED | 274 行；`RISK_CONSUMERS` 与 `buildRiskConsumers()` 已删 |
| `RiskAssessmentServiceImpl.java` | 注入 `riskThresholds`（第 6 参）+ 心血管 9 条独立归因 + 全部渲染走 `format` | ✓ VERIFIED | 765 行；`BigDecimal.valueOf` 仅剩 `:357` 的 22（默认值，非阈值）；`stripTrailingZeros`=0 |
| `RiskContributorVO.java` | `below(...)` 下限工厂 + 「评估阈值」措辞 | ✓ VERIFIED | 159 行，两个工厂都不做比较 |
| `MetricKeyNormalizer.java` | 两个血压别名 | ✓ VERIFIED | `:56-59` |
| `Risk.vue` | 三处呈现修复 + 分组互斥 + 摘要两段新措辞 | ✓ VERIFIED | 1537 行 |
| `RiskBackfill.spec.ts` | `getMetricCoverage` mock + `coverageRows` fixture + 源码扫描门 + 分组/摘要断言 | ✓ VERIFIED | fixture 6 行，注释明确「本文件只验证渲染，不验证后端格式化与同源」 |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `RiskThresholdRegistry` | 引擎判定 | `requiredRule(thresholds, key, disease)` ×12 | ✓ WIRED | 缺规则抛异常而非静默 0 分；12 处调用点与 12 条规则一一对应 |
| `RiskThresholdRegistry` | 面板 `riskNote` | `rulesFor` → `formatRiskNote` | ✓ WIRED | 单一模板渲染，「阈值+空格+单位」只在此处产生 |
| `MetricCoverageAssembler.format` | 引擎 11 处渲染 | 静态调用 | ✓ WIRED | 引擎本地 `METRICS` 静态注册表只为取 `precision`；构造器无副作用、`@PostConstruct` 被 `isEmpty()` 守卫，精度字节一致（已核） |
| `ruleFor(...).direction()/threshold()` | `classify` 的 HIGH/LOW | 第一条规则 | ✓ WIRED | 与 `riskNoteFor` 取同一条（都是 `rules.get(0)`），pill 与 note 不可能描述不同规则 |
| `profile.getBmi()` | 面板 bmi 行 | `valueFor` | ✓ WIRED | 唯一取源，无回落 |
| 卡片归因 points | `serializeContributors` → `contributors` 列 → `deserializeContributors` | JSON 快照 | ✓ WIRED | 写一次读一次，读路径无重算 |
| 心血管权重 150 | `scaleTo(…, 100)` | `if (cardioRaw > 100)` | ✓ WIRED | 可达（非死代码），有手算 150 的用例 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| 覆盖面板行 | `coverage` | `GET /risk-assessment/metric-coverage` → `assemble(profile, metrics)` | 是（23 项定义遍历真实 mapper 结果；bmi 取 profile） | ✓ FLOWING |
| 归因行 | `currentAssessment.contributors` | 持久化 JSON 快照 | 是（创建时由真实取数计算） | ✓ FLOWING |
| 弹窗合计 | `contributorSum` | 对 `contributors[].points` 求和 | 是 | ✓ FLOWING（null-profile 路径见 W-1） |
| 摘要 abnormal/used/usedMissing | computed | 同一 `coverage` 数组 | 是 | ✓ FLOWING（语义残留见 W-2） |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| 心血管恰 9 项、顺序与分值精确、合计 90 | `mvn -Dtest=RiskAssessmentAssumptionsTest#cardiovascularHasExactlyNineInputsInDeclaredOrder test` | 4 tests run, 0 failures（含下三例） | ✓ PASS |
| 封顶 150 → 100 且 Σpoints==100 | 同上 `#cappingScalesTheBreakdownToTheCappedScore` | 绿 | ✓ PASS |
| 血脂四项全缺 → MISSING/0/null、无 ASSUMED | 同上 `#lipidsAbsentAreMissingAndNeverSubstituted` | 绿 | ✓ PASS |
| 等值点两侧一致（面板 NORMAL + 引擎 0 分） | 同上 `#thresholdEqualityPointAgreesOnBothSurfaces` | 绿 | ✓ PASS |
| 前端全套 | `npm run test:run` | 54 files / 623 tests passed | ✓ PASS |
| 前端构建 | `npm run build` | exit 0，built in 18.95s | ✓ PASS |
| 后端 checkstyle | `mvn checkstyle:check` | exit 0，error 0 / warning 10895 | ✓ PASS |

Step 7b: 未启动任何服务、未改动状态；全量套件只跑一次（前端），后端以已落盘的 surefire 汇总 + 4 条定向用例取证。

Step 7c: SKIPPED —— 仓库无 `scripts/*/tests/probe-*.sh`，PLAN/SUMMARY 亦未声明探针。

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| —（无 REQ-ID） | 16-01…16-05 `requirements: []` | v1.4 未新建 `.planning/REQUIREMENTS.md` | N/A | `.planning/REQUIREMENTS.md` 不存在（`ls` 确认）。5 个 PLAN 的 `requirements` 字段均为空数组 ⇒ **无 ID 可对账、无 ORPHANED 需求**。范围由 `16-CONTEXT.md` 的 11 条决策界定，逐条见下方 Decision Coverage |

### Decision Coverage

Gate `check.decision-coverage-verify` → `{ skipped: false, blocking: false, total: 11, honored: 11, not_honored: [] }`
("All trackable CONTEXT.md decisions are honored by shipped artifacts.")

| Decision | Honored by | Evidence |
|---|---|---|
| D-01 心血管完全独立 | 引擎 + 注册表 | `RiskAssessmentServiceImpl.java:473-600`；cardio 段内 `diabetes*/bp*` 引用 = 0 |
| D-02 血脂纳入且不代入默认值 | 注册表 + 引擎 + 测试 | 注册表 `:133-137` 四条心血管规则；`lipidsAbsentAreMissingAndNeverSubstituted` |
| D-03 独立风险阈值、严于参考上限 | `RiskThresholdRegistry` | SBP 130<139、DBP 85<89；BMI 24.0 诚实标注为「更松 0.1，保留既有行为」 |
| D-04 不改 `MetricDefinition` | — | `git diff 19db185..HEAD -- MetricDefinition.java HealthMetricMetaRegistry.java` = 空 |
| D-05 `riskNote` 与引擎同源 | 注册表 + 两消费方 | `riskNoteFor` 只读 `rulesFor`；引擎 12 处 `requiredRule` |
| D-06 快照冻结不重算 | `serializeContributors`/`deserializeContributors` | 无 `sql/` 变更、无 `UPDATE ... contributors` |
| D-07 缺失 = 0 分 + MISSING，不做归一化 | `RiskContributorVO.missing()` | 全部 4 个血脂点 + 无 BP/血糖路径 |
| D-08 排除 `heartRate`，9 项 | 注册表 + 测试 | `CARDIO_KEYS` 9 项 equals；`heartRateIsNotConsumed` |
| D-09 摘要改「超出评估阈值」 | `Risk.vue:92` | 措辞已改；abnormal 的统计范围遗留见 W-2 |
| D-10 方向感知「评估阈值」措辞 | `RiskContributorVO` 两个工厂 | `评估阈值`=4、`参考值`=0；`below(...)` 不做比较 |
| D-11 两个血压别名 | `MetricKeyNormalizer:56-59` | 提交 `777421f`；`systolicBloodPressureAliasRowStartsScoring` 用 135 证伪 |

### Test Quality Audit

| Test File | Linked Req | Active | Skipped | Circular | Assertion Level | Verdict |
|---|---|---|---|---|---|---|
| `RiskAssessmentAssumptionsTest` (46) | SC-1/2/3/5, D-01..D-11 | 46 | 0 | 否 | Value + Behavioral | ✓ |
| `RiskThresholdRegistryTest` (10) | SC-2, D-03/04/08 | 10 | 0 | 否 | Value（`toPlainString`，钉小数位形态） | ✓ |
| `MetricCoverageAssemblerTest` (16) | SC-4, D-05 | 16 | 0 | 否 | Value + 字面量 + 派生集合双断言 | ✓ |
| `MetricKeyNormalizerTest` (122) | D-11 | 122 | 0 | 否 | Value | ✓ |
| `RiskBackfill.spec.ts` (42) | SC-3/4 面板侧, UI-SPEC C1/C4/C6 | 42 | 0 | 否 | Component + 源码扫描 | ✓ |

**Disabled tests on requirements:** 0（`it.skip`/`describe.skip`/`@Disabled`/`@Ignore` 均无命中）
**Circular patterns detected:** 0。`bmiRendersIdenticallyOnBothSurfaces` 比较同一系统的两次调用（只证一致性），但同一用例另钉 `"22.9"` 绝对值与 `≠ "22.86"`，故非循环。
**Insufficient assertions:** 0 —— 需求要求值级/行为级证明，实测断言均为值级或跨方法行为级。

### Anti-Patterns Found

| # | File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|---|
| W-1 | `RiskAssessmentServiceImpl.java` | 329-338 | 无档案早返回：分数 30/25/25 各带**一条 0 分** contributor，弹窗渲染「各项合计 0 = 风险评分」，SC-1 的「各项之和 = 总分」在该路径不成立 | ⚠️ WARNING（预存在，需人裁定） | 该分支自 Phase 13 的 `98acab2` 起即如此，`git show 19db185` 内容逐字相同，本阶段未触碰。路径可达（无档案用户点「按现有数据创建评估」）。**若审阅者主张 SC-1 对所有路径字面成立，则升级为 BLOCKER**；按「本阶段未引入、未声称覆盖」判为需人裁定 |
| W-2 | `Risk.vue` | 888（用于 :92） | `abnormal` 统计全部 23 行 HIGH/LOW，但其中 15 个无规则指标的 HIGH/LOW 来自 `classify` 的**参考范围回退**，摘要却统一称「超出评估阈值」 | ⚠️ WARNING | 例：仅 `heartRate = 45` 的用户会看到「1 项超出评估阈值」，而心率没有任何评估阈值、也不参与任何评估。D-09 要求改词，但决策未考虑 abnormal 覆盖到无规则指标；措辞与分类依据在 15/23 行上仍不匹配 |
| W-3 | `RiskAssessmentServiceImpl.java` | 409,435,459,468,504,519,531,543 | 归因 `threshold` 字段是**硬编码中文字面量**（`"130 mmHg"`、`"6.1 mmol/L"`、`"24.0"`…），与注册表规则值构成第二份来源 | ⚠️ WARNING | 8 处无测试把显示串绑回注册表：若注册表 130→135，`requiredRule` 的比较会变而弹窗仍说「130 mmHg」。血脂四项（`:557,568,581,591`）有 `riskNote.contains(threshold)` 断言把守，故 12 处中 4 处已守、8 处未守。D-05 要求的是「面板 note 与引擎读取同源」，比较逻辑确已同源；显示串是残留的第二份数据 |
| I-1 | `RiskContributorVO.java` | 125,155 | `awarded(...)` / `assumed(...)` 工厂零调用点 | ℹ️ Info | 两者在 `19db185` 即无生产调用，本阶段未新增也未删除；Phase 18「技术债清理」的既有范围 |
| I-2 | `RiskAssessmentServiceImpl.java` | 299-310 | `RiskRuleEngine.evaluate(...)` 两个重载均无外部调用者（`createAssessment` 直调 `evaluateDetailed`） | ℹ️ Info | 同样预存在，非本阶段引入 |
| I-3 | `Risk.vue` | 161 | `missingMetrics` 组内 `<span v-if="m.riskNote">` 已成不可达分支（谓词已排除带 riskNote 的行） | ℹ️ Info | 无害冗余，无渲染影响 |
| I-4 | `RiskThresholdRegistryTest.java` | 28 | 方法名 `declaresExactlyTheFourMigratedRules` 与现内容（12 条）不符 | ℹ️ Info | `@DisplayName` 是正确的；仅命名滞后 |
| I-5 | `RiskThresholdRegistry.java` | 211,221,230 | `consumedKeys()` / `consumes()` / `allRules()` 生产零调用（仅测试用） | ℹ️ Info | 有文档化的审计用途；`consumedKeys` 是 D-05 契约的可检查面，保留合理 |

**Debt markers:** `TBD|FIXME|XXX|HACK|PLACEHOLDER` 在本阶段 6 个源文件中 **0 命中** → 无 BLOCKER。
**Dead old-path residue:** `halve`=0、`bmiVeryHigh`=0、`bmiExtra`=0、`riskStateOf`=0、`RISK_CONSUMERS` 生产引用=0、`stripTrailingZeros`=0。
**Duplicated business logic:** `Risk.vue` 无 60/35 等级阈值（`LEVEL_RANK` 只排序，`:517-520` 注释明确不重复后端阈值）；无 `139`/`89`/`23.9` 硬编码（唯一含 `130–139` 的是解释 D-09 的注释）。`MetricCoverageAssembler` 只读注册表，未重实现规则。

### Human Verification Required

1. **四行连续 MISSING 的可读性与六字标签列宽（真实字体）**
   **Test:** 打开 `/prevention/risk`，展开一次评估的评分构成弹窗，看心血管 9 行中连续四行血脂。
   **Expected:** 灰 pill + 1px `--border` 分隔线 + 斜体说明三重区分，不糊成一片；`高密度脂蛋白`/`低密度脂蛋白` 单行不折行。
   **Why human:** 88px 是从「13px/600 下 78px 文本宽 + 10px 余量」推导的，happy-dom 无排版引擎；源码扫描只能证明声明了 88px。

2. **`riskNote` 在 1024 / 1280 的真实行数**
   **Test:** 两档视口各量一次 `.risk-coverage__note` 高度与页面横向溢出。
   **Expected:** 单行 18px；`document.scrollWidth <= clientWidth`。
   **Why human:** `1fr` 随视口变化。执行者记录了 1024/1280 均 18px、无溢出，但那是 SUMMARY 自述，本轮未独立复现（复现需现起后端 + dev server，超出「不启动服务」的停止条件）。

3. **8082 上的长期进程必须先重启**
   **Test:** 重启后端后请求 `/risk-assessment/metric-coverage`，确认档案有 BMI 的用户返回 `bmi {state: NORMAL, value: "22.9", riskNote: "心血管评估：超过 24.0 kg/m² 计入 20 分"}`。
   **Expected:** 不是 `riskNote: null` / `bmi: MISSING`。
   **Why human:** 该进程加载 16-01 之前的 class。不重启就做 live UAT 会把已修缺陷当成现存缺陷。

4. **裁定 W-1：SC-1 的求和恒等式是否必须覆盖无档案路径**
   **Test:** 二选一 —— (a) 认定为验收缺口，排入后续计划补齐无档案路径的归因（当前 30/25/25 只带一条 0 分项）；(b) 认定为验收范围之外，接受该路径只表明细不保证求和。
   **Expected:** 明确的选择与理由写入后续计划或 override。
   **Why human:** 该分支自 Phase 13 的 `98acab2` 起即如此、本阶段逐字未改，代码本身无法判定它属于本阶段契约还是历史遗留口径。

5. **裁定 W-2：摘要「超出评估阈值」是否接受 15 个无规则指标仍按参考范围计数**
   **Test:** 二选一 —— (a) 接受（D-09 只要求措辞跟随分类依据，未要求所有行都有评估阈值）；(b) 要求 `abnormal` 只统计有注册表规则的行，或另拟一段措辞。
   **Expected:** 明确的选择与理由。
   **Why human:** D-09 的决策文本只覆盖「措辞」，未覆盖「abnormal 的统计范围」；这是产品语义选择而非代码正确性。

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | 引擎其余 4 处独立区间比较统一到 `ThresholdRangeChecker` | Phase 17 | ROADMAP「Phase 17: 告警语义收敛 — 消除 `IntelligentAlertService` 内 4 处独立区间比较」 |
| 2 | `RiskAnalyzer` 无引用类删除、`RiskScorer` 改读归因快照、英文异常泄漏清理 | Phase 18 | ROADMAP「Phase 18: 技术债清理与收尾」 |
| 3 | 覆盖面板加载失败被渲染成「没有指标」 | Phase 18（UI-SPEC Deferred 建议） | `Risk.vue:901-909` `catch { coverage.value = [] }`；T-16-27 标记为 `accept`，16-05 实跑了 500 观察并记录 |
| 4 | 血脂四项的补录入口（MISSING 行无「去补录」链接） | 后续阶段（UI-SPEC Deferred） | 面板不提供跳转；`goProfileWithFocus` 只覆盖档案字段 |
| 5 | `el-dialog width="600px"` 在 375px 视口横向溢出 | 后续阶段（UI-SPEC Deferred） | pre-existing，与本阶段内容无关 |
| 6 | `--warning-700/-50`、`--success-700/-50` 未入 `theme.css` | 独立 token 债务阶段 | 现仅靠行内 fallback 生效；补入需新增 4 token × 2 主题 |
| 7 | 历史快照中 `22.86` → `22.9` 的小数位差异 | 自然消失（UI-SPEC 明确禁止回填） | D-06 要求历史冻结；回填会抹掉「当时的引擎输出」 |

### Known Deviations — Assessment

| # | Deviation | Verdict |
|---|---|---|
| 1 | 16-01 Task 1 的提交边界被并发进程的 `7925113` 吞掉 | **ACCEPTED.** 已核：`7925113` 确实包含 `RiskThresholdRegistry.java`(+193)、`RiskThresholdRegistryTest.java`(+130) 与引擎改动(+72)，内容完整且未重复（注册表 235 行、单一 `class RiskThresholdRegistry`）。代价是 Task 1/Task 2 不可独立回滚；不改写历史是对的（该提交含 ~9000 行无关删除）。目标达成，属仓库卫生问题 |
| 2 | `systolicBloodPressure` 别名（D-11）到底有没有落地 | **ACCEPTED — 已落地。** 16-01 的 deferral 被 16-03 兑现：`MetricKeyNormalizer.java:56-59` 两个别名在位，引入提交 `777421f`，`MetricKeyNormalizerTest` 122 例绿，`systolicBloodPressureAliasRowStartsScoring` 用 135 证伪「无别名」实现 |
| 3 | `.planning/WINDOWS.md` 第 3 行渲染表与围栏 JSON 不一致，`windows append` 拒写 | **ACCEPTED (process only).** `config.json` 未设 `workflow.windows_enforce`，`open_count: 0` ⇒ `/gsd-ship` 不会因此阻塞。本阶段偏差记录在 5 份 SUMMARY 中，跨阶段登记缺失但不影响代码或门禁。**建议**：本阶段若含需长期跟踪的偏差，补一行到 `WINDOWS.md` |
| 4 | 16-05 修了 `OcrMigration.spec.ts:470` 的预存在 lint error | **ACCEPTED — 不是 scope creep。** 溯源：该错误是并发提交 `668c331`（当晚 22:32）把 `[^\\n]*\\{` 改写为 `[^\n]*\{` 时**引入**的，16-05 在 23:19 的 `33469ea` 把它改成字符类 `[{]`。`lint:check` 是本阶段任务门禁之一，门禁红则任务无法收口；改动一字符、语义等价（`RegExp` 字符类里 `{` 无需转义），且 623 例仍全绿、该文件 lint 现为 0 error / 2 个既有 warning |
| 5 | 8082 上的长期后端进程服务于 16-01 之前的 class | **ACCEPTED (operational caveat).** 16-05 用 8099 的第二实例测量并在测后拆除、删除测量产生的 3 行，处理正确。风险在于**任何人**在重启前对 8082 做 UAT 都会看到旧行为 —— 已列入 Human Verification 第 3 项 |

## Gaps Summary

无阻塞性缺口。阶段目标「让每种病使用其临床上相关的全部已有输入，不再把前两种病折半相加」在代码里
是事实而非声明：心血管是 9 条自有归因，函数体内不引用任何 `diabetes*`/`bp*` 分数，`halve` 与两处
折半循环已删除，判定与阈值全部走同一份注册表，血脂缺失一律 MISSING 且无任何 `ASSUMED` 漏网。

三处 WARNING 都不是本阶段引入的回归：SC-1 的求和恒等式在无档案路径上不成立（Phase 13 遗留，本阶段
未触碰该分支）；摘要「超出评估阈值」在 15 个无规则指标上仍按参考范围计数（D-09 决议未覆盖该面）；
引擎 12 处阈值显示串中 8 处未与注册表绑定（比较逻辑已同源，显示串是残留第二份数据）。

五项人工验证：三项是「代码扫描证明不了」的类别（真实字体下的列宽与密度、`1fr` 下的实际行数、
必须先重启的 stale 进程），两项是对预存在语义的选择（W-1 求和恒等式的适用范围、W-2 abnormal 的
统计范围）。

---

_Verified: 2026-11-18T16:05:00Z_
_Verifier: Claude (gsd-verifier)_
