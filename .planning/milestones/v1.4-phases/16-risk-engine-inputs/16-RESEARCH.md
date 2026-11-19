# Phase 16: 风险评估引擎输入重组 - Research

**Researched:** 2026-11-18
**Domain:** Spring Boot 3.2 / Java 17 领域服务重构（规则引擎输入集 + 阈值 SSOT）+ 3 处 Vue 3 呈现缺陷修正
**Confidence:** HIGH（后端行为全部读源码与实测数据库确认）；MEDIUM（心血管权重是产品判断，无外部权威可引用）；HIGH（三处前端缺陷均可 grep/读源码复现）

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** 心血管疾病改为**完全独立**的评估，不再引用糖尿病分与高血压分的折半值。它有自己的输入集（血压、BMI、年龄、血糖、血脂），归因逐项独立。**理由**：「独立评估」的定义就是有自己的依据；当前实现让用户点开心血管看到的全是别处的分数，那不叫评估，叫汇总。

- **D-02:** 血脂四项（总胆固醇 / 甘油三酯 / HDL / LDL）**纳入**心血管评估的输入，尽管当前数据库里它们**零条记录**。缺失时标 `MISSING` 并明示「此项未计入」，**绝不按人群默认值代入**。**理由**：血脂是心血管最核心的可干预因子，不读它等于承认这个评估不完整；而代入默认值等于凭空造临床值，与 v1.3 建立的「区分无数据与正常」原则直接冲突。宁可让用户看到「缺血脂」，也不给一个看起来完整但编造的分数。

- **D-03:** 风险判定阈值**不直接用**元数据里的 `normalMin/normalMax`，改为在**独立的 `RiskThresholdRegistry`** 中定义风险专用阈值（预防性，严于正常上限），引擎与覆盖面板都从它读。**理由**：实测发现直接用参考范围会让评分变松 —— 收缩压 130–139 的人从「计 35 分」变成 0 分，可能把中风险误降为低风险。风险阈值本就该严于正常上限。

- **D-04:** `RiskThresholdRegistry` **新建独立类，不改 `MetricDefinition` record**。**理由**：`MetricDefinition` 有 15 个字段、24 个构造点，加字段要改 24 处且破坏紧凑构造器的不变式；而风险阈值只涉及少数参与评估的指标。独立类把改动面收敛到一处，也让「风险阈值」这个概念有了明确的归属。

- **D-05:** 覆盖面板的 `riskNote` 必须与引擎实际读取的指标**同源**。当前 `RISK_CONSUMERS` 硬编码在 `MetricCoverageAssembler` 里，与引擎的 `getLatestMetric` 调用是两处独立维护。重组后必须让二者从同一份声明派生，否则面板会说「参与评估」而引擎并不读，或反之。**理由**：这是本次重组最容易产生的新缺陷 —— 面板已经能显示每项指标的去向，如果它开始说谎，比不显示更糟。

- **D-06:** 归因快照（`contributors` JSON）继续在创建时冻结，读时不重算。新增的输入项同样进入快照。**理由**：Phase 15 已建立的契约，历史记录必须是当时的真实依据。

- **D-07:** 缺失输入的计分方式是 **0 分 + `MISSING` 状态**，与 Phase 15 一致。不做「按缺失比例折算总分」这类归一化 —— 那会让「什么都没测」也得出一个看起来正常的分数。

### Claude's Discretion

CONTEXT.md 未单列「Claude's Discretion」章节。按 DISCUSSION-LOG.md，以下由 Claude 裁定后记录为锁定决策：D-01（灰区 3 选项一）、D-02（灰区 1 选项一）、D-05/D-06/D-07（技术实现细节，用户无需决策）。D-03 阈值来源由用户选择「元数据加 riskThreshold 字段」，实施形态由 D-04 收敛为独立类。

**因此本阶段没有剩余的裁量区**：输入集、阈值来源、缺失语义、快照语义全部锁定。研究不探索替代方案，只回答「怎么落地」。

### Deferred Ideas (OUT OF SCOPE)

- 新增疾病（高尿酸血症、慢性肾病等）、趋势图、AI/ML 模型、表结构变更（CONTEXT「领域边界」）
- 16-UI-SPEC.md「Deferred」表全部条目：覆盖面板加载失败的错误态（建议 Phase 18）、`el-dialog` 375px 横向溢出、`el-tag` 500 字重归一化、血脂补录入口、为「心血管已独立」加视觉强调、视觉回归、把 `--warning-700/-50` 与 `--success-700/-50` 补进 `theme.css`、回溯改写历史快照小数位、给 S1 加单位列
- 体检报告 `structured_data` 的解析（1 份报告、内容为空）

</user_constraints>

<phase_requirements>

## Phase Requirements

v1.4 未新建 REQUIREMENTS.md（ROADMAP「Requirements」行为「无独立 REQ-ID」）。本阶段以 ROADMAP 的 6 条成功标准 + CONTEXT 的 8 条验收标准为需求锚点，下文以 **AC-1…AC-8**（CONTEXT 验收标准编号）和 **SC-1…SC-6**（ROADMAP 成功标准编号）引用。

| ID | Description | Research Support |
|----|-------------|------------------|
| AC-1 / SC-2 | 心血管不再引用糖尿病分或高血压分，归因全部来自自身输入 | Q2 给出 9 项独立输入集与权重表；Q3 定位 `halve()`（:455-461）与 `cardioParts` 组装（:418-433）为唯一删除点 |
| AC-2 / SC-3 | 血脂四项进入心血管评估；缺失显示 `MISSING` + 「此项未计入」 | Q1 的 `RiskThresholdRegistry` 承载 4 条血脂规则；`RiskContributorVO.threshold(null,…)` → `missing()` 已产出该文案（:99-102） |
| AC-3 | 风险阈值来自 `RiskThresholdRegistry` 且严于元数据参考上限（130 < 139） | Q1 给出类形状与 8 条阈值的严格不等式；Q7 给出 130–139 区分性夹具 |
| AC-4 / SC-4 | 覆盖面板 `riskNote` 与引擎实际读取的指标一致（同源派生） | Q1 定义 `consumedKeys()` 为唯一声明；Q4 说明 `assemble()` 签名；Q6 给出双向断言 |
| AC-5 / SC-1 | 归因各项之和等于风险评分（含封顶按比例折算） | Q2 的心血管权重上限 150 > 100，使 `scaleTo()`（:481-516）可被真实触发，测试有牙齿 |
| AC-6 | 所有 `metric_key` 匹配先经 `MetricKeyNormalizer` | Q7 记录两条实证：`getAliases` 用法（:534-539）与库中 `systolicBloodPressure` 恒落空 |
| AC-7 / SC-5 | 历史快照冻结，读时不重算 | Q5 实测 54 行 / 18 批次 / 6 行 NULL / 16 批次带「折半」快照 |
| AC-8 / SC-6 | 后端 `mvn verify -DskipITs` + `checkstyle:check` 全绿；前端 `test:run` + `build` 全绿 | Validation Architecture：基线实测 9 + 26 条后端、29 条前端全绿 |

</phase_requirements>

---

## Summary

本阶段是**后端输入重组**，前端只有三处必须修的呈现缺陷。核心增量是一个新的纯数据类 `RiskThresholdRegistry`（`com.hhs.service.domain`，`@Component`，无构造依赖），它同时承担三件事：①风险专用阈值（D-03）；②「引擎读哪些指标」的唯一声明（D-05）；③`riskNote` 的派生源。引擎与覆盖面板都从它取规则，`MetricCoverageAssembler.RISK_CONSUMERS`（:37-47）整体删除。

心血管（D-01）改为 9 项自有输入：年龄、BMI、收缩压、舒张压、血糖、血脂四项。权重合计上限 150，因此 `scaleTo()` 在「多项异常」的用户上会真实触发 —— 这使 AC-5 的「含封顶折算」成为可被测试证伪的路径，而不是一句写在契约里、实现里永远走不到的注释。9 行归因与 8 个 `riskNote` 键**恰好等于** UI-SPEC Contract 2 与 Contract 3.1 已测量的数量，因此 Contract 4 的 `88px` 列宽与 Contract 2 的 `1fr` 宽度链路不需要重算。

三处前端缺陷全部复核成立：`--border-1` 仓库内 8 处引用 0 处定义（`theme.css` 只有 `--border` / `--border-strong` / `--border-accent`）；覆盖面板三条 `filter` 谓词交叠（`Risk.vue:845/849/853`），D-02 落地后每个用户固定重复 5 行（BMI + 血脂四项）；面板按 registry precision 格式化（`22.9`）而引擎按 `stripTrailingZeros()`（`22.86`）。第三处的修法必须让**两侧共用同一段代码**（`MetricCoverageAssembler` 的 `public static format`），因为 `RiskRuleEngine` 是 `com.hhs.service.impl` 的私有静态嵌套类（:272），包级私有跨包不可见。

研究过程中发现**三处 UI-SPEC 未登记的连带缺陷**，都会在本阶段被 D-01/D-02 放大，必须在计划里显式排入：`RiskContributorVO.threshold()` 的措辞是硬编码的「超过参考值」，对下限型 HDL 会说反话；同一句里把风险阈值称作「参考值」，与同屏的「参考范围」列直接矛盾；D-03 让覆盖面板按风险阈值分类后，摘要第二段「{n} 项超出参考范围」在收缩压 130–139 区间变成假话。三者各有最小修法，见 Q3/Q7 与 Open Questions。

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|---|---|---|---|
| 风险阈值定义（风险专用、严于参考上限） | 领域层 `com.hhs.service.domain.RiskThresholdRegistry` | — | D-04 明确不入 `MetricDefinition`；阈值是病例规则而非指标元数据 |
| 「引擎读哪些指标」的唯一声明 | 领域层 `RiskThresholdRegistry.consumedKeys()` | 引擎（消费方，只迭代不硬编码） | D-05 要求单一来源；面板不得自己维护第二份 |
| 数值格式化（贡献值、阈值文本） | 领域层 `MetricCoverageAssembler.format` (`public static`) | 引擎（调用方） | 唯一算式；跨包可见性迫使 `public`（`impl` ≠ `domain`） |
| 三种疾病的评分构成与归因 | 服务层 `RiskAssessmentServiceImpl.RiskRuleEngine` | 领域层（读规则与阈值） | 分支逻辑与分值属于引擎；阈值不属于 |
| 归因快照的冻结与读取 | 服务层 `createAssessment` / `deserializeContributors` | 持久层 `risk_assessment.contributors` JSON | D-06：写入时序列化，读取时反序列化，无重算 |
| 覆盖面板的行状态判定 | 领域层 `MetricCoverageAssembler.classify` | 领域层 `RiskThresholdRegistry` | D-03 要求阈值同源；双源因子（`bmi`）以引擎源为唯一取值源（UI-SPEC §5.3） |
| 覆盖面板分组互斥与摘要措辞 | 表现层 `Risk.vue` computed | — | 纯前端派生逻辑，fixture 只能提供输入，不能伪造结论 |
| 归因/覆盖两套状态词表 | 表现层 `stateLabel` / `coverageStateLabel` | 后端 `state` 枚举常量 | 两套词表刻意不同（`缺数据` vs `未测`），不得合并 |

---

## Standard Stack

本阶段**零新增依赖**。所有能力由现有栈承担。

### Core

| Library | Version | Purpose | Why Standard |
|---|---|---|---|
| Spring Boot | 3.2.0（`hhs-backend/pom.xml:8-9`） | 领域组件装配（`@Component` + 构造注入） | 项目既有形态；`RiskThresholdRegistry` 与 `MetricCoverageAssembler` 同包同注解 |
| Java | 17（`pom.xml:20`） | `record`、`switch` 表达式、`List.copyOf` | 项目基线；`MetricDefinition`、`MetricCoverageVO`、`RiskContributorVO` 全是 `record` |
| MyBatis-Plus | 继承自 parent | 档案与指标读取 | `getProfileByUserId` / `getRecentMetrics` 既有 |
| JUnit 5 + Mockito | 继承自 `spring-boot-starter-parent` 3.2.0 | 后端单测 | `RiskAssessmentAssumptionsTest` 已是「真实 service + mock 3 个 mapper」形态 |
| Vue 3 + TypeScript 5 | `vue ^3.4.15` | 覆盖面板与构成弹窗 | 组件已存在，本阶段不改结构 |
| Vitest | `^4.1.0` | 前端单测 | `vitest.config.ts` 使用 `happy-dom`，无后端端点 |

### Supporting

| Library | Version | Purpose | When to Use |
|---|---|---|---|
| `element-plus` | `^2.5.6`（实测安装 2.14.0） | 面板容器、骨架、弹窗 | 本阶段不新增组件用法 |
| `@vue/test-utils` | `^2.4.6` | `mount` + stub 集合 | 新增断言复用 `RiskBackfill.spec.ts` 既有 `globalStubs` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| 新建 `RiskThresholdRegistry` | 给 `MetricDefinition` 加 `riskThreshold` 字段 | ❌ D-04 明令否决：15 字段 record、24 个构造点（实测 `grep -c "new MetricDefinition("` → 24，全部在 `HealthMetricMetaRegistry.java`），且要改紧凑构造器的 `alertable → recommended` 不变式（`MetricDefinition.java:49-55`） |
| `RiskThresholdRegistry` 里存展示标签 | 从 `HealthMetricMetaRegistry.getDefinition(key).displayName()` 取 | ✅ 采纳后者：标签/单位/精度已有 SSOT，第二份会漂移 |
| 引擎内 `new HealthMetricMetaRegistry()` 取精度 | 由服务把同一个实例/注册表传进 `evaluateDetailed` | 后者少一个实例且是 D-05 的字面形态；代价是服务构造器 +1 参数，命中 1 处测试（`RiskAssessmentAssumptionsTest:62-64`） |
| 断言里做 `toFixed(1)` 归一 | 两侧共用 `MetricCoverageAssembler.format` | ❌ UI-SPEC §5.3.1 已证：容差不单调（`|22.9−22.86|=0.0399` 通过、`|20.2−20.15|=0.0500000000000007` 失败），会把实现缺陷固化成契约 |

**Installation:** 无。本阶段不执行任何包安装命令，因此 **Package Legitimacy Audit 不适用**（无新增 registry、无新依赖、无新组件文件；UI-SPEC `## Registry Safety` 已声明 `第三方 registry: none`）。

**Version verification:** 无需验证新包。既有版本已由 `hhs-frontend-v2/package.json` 与 `hhs-backend/pom.xml` 给出，本文所有版本号均读自这两个文件。

---

## Package Legitimacy Audit

**不适用 —— 本阶段零新增外部包。** 后端只新增一个同仓库内的 Java 类；前端只改 `Risk.vue` 既有声明与模板表达式。无 `npm install`、无 `pip`、无 `cargo`。

---

## Architecture Patterns

### System Architecture Diagram

```
                      ┌──────────────────────────────────────────┐
  HTTP GET           │  PreventionController                     │
  /risk-assessment/  │    getMetricCoverage()  → :211-214        │
  metric-coverage ──►│                                          │
                      └───────────────┬──────────────────────────┘
                                      │ userId
                                      ▼
                      ┌──────────────────────────────────────────┐
                      │ RiskAssessmentServiceImpl                 │
                      │  getProfileByUserId → health_profile      │
                      │  getRecentMetrics  → health_metric (3M)   │
                      └───────┬──────────────────────┬───────────┘
             (新) profile ────┘                      │ metrics
                                                      ▼
   ┌────────────────────────────────────┐   ┌────────────────────────────────┐
   │ MetricCoverageAssembler            │   │ RiskRuleEngine (private static)│
   │  assemble(profile, metrics)        │   │  evaluateDetailed(...)         │
   │   for def in metaRegistry:         │   │   糖尿病 / 高血压 / 心血管      │
   │     hit = latest[canonicalKey]     │   │                                │
   │     bmi 特殊：读 profile.getBmi()   │◄──┤ 两侧都只读这两个领域对象：      │
   │     rules = thresholds.rulesFor(k) │   │   RiskThresholdRegistry        │
   │     state = classifyBy(rule|range) │   │   HealthMetricMetaRegistry     │
   │     riskNote = note(rule, def)     │   │  （format 也是同一段代码）      │
   └──────────────┬─────────────────────┘   └───────────┬────────────────────┘
                  │                                     │
                  ▼                                     ▼
   ┌──────────────────────────────┐        ┌──────────────────────────────────┐
   │ MetricCoverageVO[]           │        │ RiskAssessmentVO[]               │
   │  key/label/unit/category     │        │  riskScore ← Σ contributors       │
   │  state/value/normalRange     │        │  contributors[] ← 冻结进 JSON      │
   │  riskNote ← 同源派生          │        │  assumptions[]                    │
   └──────────────┬───────────────┘        └───────────┬──────────────────────┘
                  │                                     │
                  ▼                                     ▼
   ┌──────────────────────────────┐        ┌──────────────────────────────────┐
   │ Risk.vue  S1 覆盖面板          │        │ Risk.vue  S2 评分构成弹窗          │
   │  usedMetrics (!!riskNote)     │        │  contributors 逐行                 │
   │  otherMeasured (!note && 有值) │        │  两套词表各自渲染                  │
   │  missingMetrics (未测 && !note)│        │  底部缺失警示句                    │
   └──────────────────────────────┘        └──────────────────────────────────┘

  决策点：def 是否有风险规则？→ 有：按 RiskThresholdRegistry 判定 + 生成 riskNote
                            → 无：按 normalMin/normalMax 判定 + riskNote=null
  数据源决策点：key 是双源因子（当前仅 bmi）？→ 是：只读 health_profile（引擎源）
```

### Recommended Project Structure

无新增目录。新增/修改文件：

```
hhs-backend/src/main/java/com/hhs/service/domain/
├── RiskThresholdRegistry.java        # 新增：风险阈值 + 消费声明 + 规则查询
├── RiskThresholdRule.java            # 新增（或作为 Registry 的嵌套 record）
├── MetricCoverageAssembler.java      # 改：删 RISK_CONSUMERS；加 public static format；assemble 加 profile
├── HealthMetricMetaRegistry.java     # 不改（D-04）
└── MetricDefinition.java             # 不改（D-04）

hhs-backend/src/main/java/com/hhs/service/impl/
├── RiskAssessmentServiceImpl.java    # 改：心血管独立；阈值/格式化改为读注册表；删 halve()
└── ...

hhs-backend/src/main/java/com/hhs/vo/
└── RiskContributorVO.java            # 改：新增方向感知的工厂（HDL 用）

hhs-frontend-v2/src/views/prevention/
└── Risk.vue                          # 改：分组谓词、摘要措辞、8 处 --border-1、80→88px、5 处 --text-3
```

### Pattern 1: 纯数据注册表（对齐 `MetricKeyNormalizer` 与 `HealthMetricMetaRegistry`）

**What:** 无 IO、无 Spring 生命周期的静态表，构造即完成注册；Spring 侧用 `@Component` 暴露单实例，测试侧可 `new`。
**When to use:** 阈值、别名、元数据这类「编译期就知道的内容」。
**Example:**

```java
// Source: hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java:24-33 [VERIFIED]
public HealthMetricMetaRegistry() {
    registerAllMetrics();
}

@PostConstruct
public void init() {
    if (definitions.isEmpty()) {
        registerAllMetrics();
    }
}
```

同一形态可直接套用：`RiskThresholdRegistry` 的构造函数只跑 `registerAllRules()`，`@PostConstruct` 用 `isEmpty()` 守卫；测试 `new RiskThresholdRegistry()` 即可。

### Pattern 2: 声明式消费清单 + 引擎迭代（D-05 的落地形态）

**What:** 面板的 `riskNote` 与引擎的 `getLatestMetric` 不再各写一份；注册表暴露 `consumedKeys()`，引擎按它逐个取值，面板按它逐行生成 note。
**When to use:** 任何「两处代码必须永远一致」的映射。
**Example:**

```java
// Source: hhs-backend/.../MetricCoverageAssembler.java:37-47（本阶段要删除的反面教材）[VERIFIED: hhs-backend/src/main/java/com/hhs/service/domain/MetricCoverageAssembler.java:37-47]
private static final Map<String, String> RISK_CONSUMERS = buildRiskConsumers();
...
map.put("bloodGlucose", "糖尿病评估：超过 6.1 mmol/L 计入 30 分");
map.put("systolicBP", "高血压评估：超过 130 mmHg 计入 35 分");
map.put("diastolicBP", "高血压评估：超过 85 mmHg 计入 30 分");
```

这三条 literal 与引擎的 `BigDecimal.valueOf(6.1/130/85)`（`RiskAssessmentServiceImpl.java:383/404/409`）是**两处独立维护**，且 note 里的分值（30/35/30）与引擎的分支（30/35/30）是巧合对齐 —— 任何一侧单独改动都会让面板说谎。改为注册表后，`riskNote` 由 `(rule, def)` 渲染，分值只有一份。

### Pattern 3: 方向感知的归因工厂

**What:** 归因文案的动词由阈值方向决定，不由「是否命中」决定。上限型命中 → `超过`，下限型命中 → `低于`，未命中 → 未超过 / 不低于。
**When to use:** 任何下限型指标（当前只有 `hdlCholesterol`）。
**Example（现状的反面）:**

```java
// Source: hhs-backend/src/main/java/com/hhs/vo/RiskContributorVO.java:61-71 [VERIFIED]
public static RiskContributorVO threshold(String key, String label, String value,
                                          String threshold, boolean hit, int points) {
    if (value == null) {
        return missing(key, label);
    }
    String state = hit ? STATE_CONTRIBUTED : STATE_NORMAL;
    String explanation = hit
            ? label + " " + value + "，超过参考值 " + threshold + "，因此计入 " + points + " 分"
            : label + " " + value + "，未超过参考值 " + threshold + "，未加分";
    return new RiskContributorVO(key, label, state, value, threshold, points, explanation);
}
```

HDL 0.90 mmol/L（偏低）命中会得到「高密度脂蛋白 0.90 mmol/L，**超过**参考值 1.00 mmol/L」—— 与事实相反。UI-SPEC Contract 5.2 只在 `riskNote` 层面立了方向规则，归因句这一层没有，必须一起补。

### Anti-Patterns to Avoid

- **在 `impl` 包里复制一份 formatter：** 直接摧毁 AC-5/断言 A(i) 的全部价值。`MetricCoverageAssembler.format` 必须是 `public static` 且被引擎调用（UI-SPEC §5.3.1「formatter 的归属」）。
- **把 `missingMetrics` 改成「从 used 里减掉缺失项」：** 那会把血脂四项从「参与」组里挪走，重新引入「面板说未参与、引擎实际读」的谎言（UI-SPEC Contract 1.2 明确否决）。
- **给 `riskNote` 加来源前缀/后缀：** 前缀版 30 字、后缀版 39 字，均超 24 字上限；来源由 `risk-estimate` 的 origin 列承担（UI-SPEC §5.3）。
- **用 `white-space: nowrap` 解决六字标签：** `.el-dialog` 无 `max-width`，`width="600px"` 是硬宽度，nowrap 会横向溢出（UI-SPEC Contract 4）。
- **对缺失的血脂代入人群默认值：** D-02 明确禁止；`STATE_ASSUMED` 只对年龄/BMI 的档案兜底成立。

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| 指标键归一化与别名匹配 | 自己写 `key.equals("收缩压")` 或 `equalsIgnoreCase` | `MetricKeyNormalizer.getAliases(canonicalKey)`（`RiskAssessmentServiceImpl.java:534-539`） | 硬编码中文名查 `metric_key` 恒落空且**不报错**，本项目已复发两次 |
| 数值精度格式化 | 引擎里 `stripTrailingZeros()` 或用 `String.format("%.1f")` | `MetricCoverageAssembler.format(value, def)`（由 `formatValue` :137-140 抽出） | 两侧必须同一算式；`BigDecimal` HALF_UP ≠ 二进制浮点 `toFixed` |
| 风险阈值分级 | 再写一遍 `compareTo` | `RiskThresholdRegistry` 的规则 + 既有 `ThresholdRangeChecker` 语义参考 | D-03 单一来源；Phase 17 还要把告警侧也收敛到 `ThresholdRangeChecker` |
| 归因合计与封顶折算 | 重新算一遍总分 | `scaleTo()`（:481-516，最大余数法） | 已实现且保证合计**恰好**等于 `target`；重写会引入残留误差 |
| 前端分组互斥 | 在三个 `filter` 里各加各的条件 | 只改 `missingMetrics` 一处加 `&& !m.riskNote`，并断言三组之和 | 三处同时改会出现新的交叠；单向修正可被「和 == 行数」的断言兜住 |
| 源码级门禁（`--border-1` 归零、列宽 ≥88px） | 写在 plan 的验收清单里靠人跑 grep | 采用既有 `ResidueGate.spec.ts` 的做法：`readFileSync` + 断言 | 需要有人记得去跑的 grep，就是里程碑关闭后不再跑的那个 grep（该文件头注释原文） |

**Key insight:** 本阶段的每一个手工实现（两份阈值、两份 formatter、两份消费清单）都会在「面板与引擎不一致」这个缺陷族里再长出一个成员 —— 而这正是 D-05 要一次性消灭的东西。

---

## Runtime State Inventory

> 本阶段是**代码重组**，不是重命名/迁移。但 D-06 涉及「已有运行时数据如何被新代码读取」，逐类回答如下。

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| **Stored data** | `risk_assessment` 实测 **54 行** / **18 批次** / 2 个用户（`user_id` 7 与 1）。其中 **48 行有 `contributors` 快照**，**6 行为 NULL**（2 个 `legacy-*` 批次）。心血管 18 行里 **16 行含「折半」**、2 行为 NULL。快照里出现的 BMI 字面量是 `20.15`（32 行），`22.86` **0 行** | **不改写。** D-06 要求读时不重算；旧快照保持旧形状（`来自糖尿病：…（折半计入 N 分）`）。新代码必须在**新建评估**时写出新形状 |
| **Live service config** | 无。阈值是代码常量，不在 `alert_rule` 表（那是 Phase 17 的 `ThresholdRangeChecker` 域），本阶段**不读库**定义风险阈值 | 无 |
| **OS-registered state** | 无。无定时任务、无 pm2/launchd 注册 | 无 |
| **Secrets/env vars** | 无新增。本阶段不引入配置项、不引入环境变量 | 无 |
| **Build artifacts / installed packages** | 无新增依赖。后端 `target/` 下 `checkstyle-result.xml` 会被 `verify` 重写（无害） | 无 |

**Canonical question 的回答：** *在仓库里每个文件都更新之后，还有哪些运行时系统缓存着旧字符串？* —— 只有 `risk_assessment.contributors` 里的 16 个心血管快照。它们**必须**保持旧形状（D-06），不需要数据迁移，也不需要回填。这正是 AC-7 要求的状态。

---

## 研究问答（对应 research_questions）

### Q1 — `RiskThresholdRegistry` 的形状（D-03 + D-04）

#### 1.1 为什么必须是「规则表」而不是「每指标一个阈值」

一个指标可被多个疾病读取，且各疾病的阈值/分值不同。实证（当前代码）：

```java
// Source: hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java:383-387 [VERIFIED]
boolean sugarHigh = latestBloodSugar != null && latestBloodSugar.compareTo(BigDecimal.valueOf(6.1)) > 0;
int sugarPoints = sugarHigh ? 30 : 0;
diabetesParts.add(RiskContributorVO.threshold("bloodGlucose", "血糖",
        latestBloodSugar == null ? null : latestBloodSugar.stripTrailingZeros().toPlainString() + " mmol/L",
        "6.1 mmol/L", sugarHigh, sugarPoints));
```

```java
// Source: hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java:358-359 [VERIFIED]
boolean bmiHigh = bmi.compareTo(BigDecimal.valueOf(24)) > 0;
int bmiPoints = bmiHigh ? 25 : 10;
```

`bmi` 在糖尿病是「>24 计 25」、在心血管是「>28 额外计 20」（:426-433）。D-01 之后心血管要自己的 BMI 规则，因此 `(metricKey, disease)` 才是规则的唯一键。**结论：注册表存一组 `RiskThresholdRule`，按 `(metricKey, disease)` 索引。**

#### 1.2 类名、包、公开 API

**类：** `com.hhs.service.domain.RiskThresholdRegistry`（与 `MetricCoverageAssembler`、`HealthMetricMetaRegistry` 同包 —— 这是 `assemble()` 能直接读它的前提，也是 UI-SPEC §5.3.1 讨论可见性时的同一个包边界）。
**注解：** `@Component`，构造函数**无参**（纯静态表），`@PostConstruct` 用 `isEmpty()` 守卫 —— 与 `HealthMetricMetaRegistry:24-33` 逐一对应。
**规则类型：** 嵌套 `record RiskThresholdRule(String metricKey, String disease, Direction direction, BigDecimal threshold, int points)`，`enum Direction { ABOVE, BELOW }`。`ABOVE` 用严格 `>`，`BELOW` 用严格 `<`（与 HDL 注册表说明「正常参考值 >= 1.00」自洽）。

**公开 API（唯一允许的形状）：**

| 方法 | 返回 | 用途 |
|---|---|---|
| `List<RiskThresholdRule> rulesFor(String canonicalKey)` | 该指标的规则，按**声明顺序**；无规则返回 `List.of()` | 引擎按疾病取规则；面板取 `get(0)` 生成 `riskNote` |
| `Optional<RiskThresholdRule> ruleFor(String canonicalKey, String disease)` | 单条 | 引擎在明确疾病上下文里取值 |
| `Set<String> consumedKeys()` | 全部规则涉及的 canonical key（插入序） | **D-05 的唯一声明**；引擎按它遍历、面板按它判 `riskNote` 是否为空 |
| `boolean consumes(String canonicalKey)` | 是否有任何规则 | 面板一行判断 |
| `List<RiskThresholdRule> allRules()` | 全部规则 | 测试与审计 |

**不要**在注册表里放 `label` / `unit` / `precision`：它们已有 SSOT（`MetricDefinition.java:31-47`，`displayName`/`unit`/`precision` 字段），复制会漂移。

#### 1.3 与现有硬编码字面量的对应关系

现有 6 处数字字面量（全部在同一条链上）与注册表规则的映射：

| 现状（file:line） | 现状值 | 迁移后 |
|---|---|---|
| `RiskAssessmentServiceImpl.java:383` | `BigDecimal.valueOf(6.1)`，`sugarPoints=30` | `rule(bloodGlucose, 糖尿病, ABOVE, 6.1, 30)`；另加 `rule(bloodGlucose, 心血管, ABOVE, 6.1, 10)` |
| `:404` | `BigDecimal.valueOf(130)`，`35` | `rule(systolicBP, 高血压, ABOVE, 130, 35)`；另加 `rule(systolicBP, 心血管, ABOVE, 130, 30)` |
| `:409` | `BigDecimal.valueOf(85)`，`30` | `rule(diastolicBP, 高血压, ABOVE, 85, 30)`；另加 `rule(diastolicBP, 心血管, ABOVE, 85, 15)` |
| `:358` | `BigDecimal.valueOf(24)`，`25`（否则 10） | `rule(bmi, 糖尿病, ABOVE, 24.0, 25)`；**10 分是无条件基线，不是阈值奖励，留在引擎里**并注明 |
| `:426` | `BigDecimal.valueOf(28)`，`20` | 改为 `rule(bmi, 心血管, ABOVE, 24.0, 20)`（见 Q2 的理由） |
| `:327` | `BigDecimal.valueOf(22)` | **不是风险阈值**，是缺失代入值（`MISSING_BMI_ASSUMPTION`，`:59`）。不入注册表 |

新增 4 条血脂规则（心血管）：`totalCholesterol ABOVE 5.2 20`、`triglycerides ABOVE 1.7 10`、`hdlCholesterol **BELOW** 1.0 15`、`ldlCholesterol ABOVE 3.4 15`。

**阈值必须严于参考上限（AC-3）—— 逐条核对：**

| 指标 | 参考上限（registry） | 风险阈值 | 严格更严？ |
|---|---|---|---|
| systolicBP | `new BigDecimal("139")`（`HealthMetricMetaRegistry.java:66`） | 130 | ✅ 130 < 139 |
| diastolicBP | `new BigDecimal("89")`（`:84`） | 85 | ✅ 85 < 89 |
| bloodGlucose | `new BigDecimal("6.1")`（`:102`） | 6.1 | 相等（CONTEXT 阈值对照表已登记「一致」） |
| bmi | `new BigDecimal("23.9")`（`:192`） | 24.0 | ⚠️ 24.0 **不严于** 23.9（风险更松 0.1） |
| totalCholesterol | `new BigDecimal("5.2")`（`:228`） | 5.2 | 相等 |
| triglycerides | `new BigDecimal("1.7")`（`:246`） | 1.7 | 相等 |
| hdlCholesterol | `normalMin=1.0`（`:263`） | 1.0（下限） | 相等 |
| ldlCholesterol | `new BigDecimal("3.4")`（`:282`） | 3.4 | 相等 |

> **`bmi` 是唯一不满足「严于参考上限」的条目。** CONTEXT 阈值对照表已把它登记为「风险更严 0.1」（实际上是**更松** 0.1 —— 表格把 24 与 23.9 的方向写反了）。这是既有行为（`:358` 用 24），本阶段不改变数值：改成 23.9 会让 BMI 23.9–24.0 区间的人**新增**计分，属于未被决策授权的评分变化。**建议：保留 24.0，并在注册表注释里显式写明「与参考上限 23.9 同量级，取整数便于用户理解；不宣称严于参考上限」。** AC-3 的可执行断言因此只能对收缩压/舒张压成立（见 Q7），不得写成「所有阈值均严于参考上限」—— 那会立刻红。

#### 1.4 与 `MetricCoverageAssembler` 的同源读取（D-05 的落地）

`MetricCoverageAssembler` 构造器增加 `RiskThresholdRegistry thresholds` 字段（第二参数）。三处改动：

1. **删** `RISK_CONSUMERS` 及其 `buildRiskConsumers()`（`:37-47`）。
2. `toRow` 里把 `RISK_CONSUMERS.get(key)` 换成 `riskNoteForKey(def)`：

```java
// 目标形态（伪代码，落地时写成真实 Java）
private String riskNoteForKey(MetricDefinition def) {
    List<RiskThresholdRule> rules = thresholds.rulesFor(def.canonicalKey());
    if (rules.isEmpty()) {
        return null;
    }
    return formatRiskNote(rules.get(0), def);   // 只描述第一条，UI-SPEC 5.5
}
```

3. `classify` 改为「有规则按规则、无规则按参考范围」：

```java
// 目标形态
private String classify(MetricDefinition def, BigDecimal value) {
    Optional<RiskThresholdRule> rule = thresholds.rulesFor(def.canonicalKey()).stream().findFirst();
    if (rule.isPresent()) {
        // ABOVE: value > threshold → HIGH；BELOW: value < threshold → LOW
    }
    // 无规则：维持现有 normalMax/normalMin 判定（:120-128 一行不改）
}
```

**`riskNote` 的渲染必须是 `public static`，与数值格式化同处一地（UI-SPEC §5.3.1 route (b)）：**

```java
// 目标签名（两处唯一算式）
public static String format(BigDecimal value, MetricDefinition def);          // 数值部分
public static String formatRiskNote(RiskThresholdRule rule, MetricDefinition def); // 规则句
```

`formatRiskNote` 的模板**唯一**（UI-SPEC Contract 5.2）：`{疾病}评估：{方向动词} {阈值}{单位} 计入 {分数} 分`，`{方向动词}` = `ABOVE → 超过`、`BELOW → 低于`；`{阈值}` 按 **registry precision** 渲染（血脂 precision=2 → `5.20`，见 `:223/:241/:259/:277`），单位取 `MetricDefinition.unit()`（`kg/m²`、`mmHg`、`mmol/L`）。

**去空白码点计长（UI-SPEC §5.2 的可执行口径）：**

```java
// 正确：长度取自同一个已去空白的字符串
String s = note.replaceAll("\\s", "");
int n = s.codePointCount(0, s.length());
```

实测四条示例均 ≤24（详见本文件 Q7 的牙齿表）。

---

### Q2 — 心血管独立性（D-01）

#### 2.1 输入集：9 项，与 UI-SPEC 已测量的行数一致

| # | 因子 | 取值来源 | 键/字段 | 有数据？ |
|---|---|---|---|---|
| 1 | 年龄 | `health_profile.birthDate`（兜底 35 岁 + `ASSUMED`） | `age` | 3/3 有 |
| 2 | BMI | `health_profile.bmi`（兜底 22.0 + `ASSUMED`） | `bmi` | 3/3 有 |
| 3 | 收缩压 | `health_metric.systolicBP` | `systolicBP` | 9 行 |
| 4 | 舒张压 | `health_metric.diastolicBP` | `diastolicBP` | 7 行 |
| 5 | 血糖 | `health_metric.bloodGlucose` | `bloodGlucose` | 8 行 |
| 6 | 总胆固醇 | `health_metric.totalCholesterol` | `totalCholesterol` | **0 行** |
| 7 | 甘油三酯 | `health_metric.triglycerides` | `triglycerides` | **0 行** |
| 8 | HDL | `health_metric.hdlCholesterol` | `hdlCholesterol` | **0 行** |
| 9 | LDL | `health_metric.ldlCholesterol` | `ldlCholesterol` | **0 行** |

**恰好 9 行**，等于 UI-SPEC Contract 3.1 枚举的「年龄 / BMI / 收缩压 / 舒张压 / 血糖 / 总胆固醇 / 甘油三酯 / 高密度脂蛋白 / 低密度脂蛋白」。**恰好 8 个 `riskNote` 键**（去年龄），等于 Contract 2 的「血糖、收缩压、舒张压、BMI、血脂四项」。

#### 2.2 明确排除的两项，及排除理由

| 排除项 | 有数据 | 为什么排除 | 代价 |
|---|---|---|---|
| `heartRate` | 5 用户 / 10 行 | 加它会变成 **10 行归因 / 9 个 `riskNote`**，同时破坏 UI-SPEC Contract 2（「约 8 项」）、Contract 3.1（9 行枚举）、Contract 4（88px 列宽只对 9 行列表量过） | CONTEXT 首段举的例子正是「我 122 的心率偏高，但系统没拿它算风险」。这条会**留到下一阶段**（见 Open Question 1） |
| `weight` | 1 用户 / 4 行 | 与 BMI 重复表达同一件事（`HealthProfileServiceImpl.java:120-121` 用身高体重算 BMI 入库），同时读两者会双重计分 | 用户点开「体重」仍会看到「不参与风险评估」，与今天一致 |

#### 2.3 权重表（含与 UI-SPEC 示例的三处对齐）

UI-SPEC §5.2 与 §5.3 已给出三条**心血管**示例，权重必须与之自洽，否则 UAT 一读就矛盾：

| 依据 | UI-SPEC 原文 | 本表取值 |
|---|---|---|
| §5.3 | `心血管评估：超过 24.0 kg/m² 计入 20 分` | `bmi` ABOVE 24.0 → **20** |
| §5.2 | `心血管评估：超过 5.2 mmol/L 计入 20 分` | `totalCholesterol` ABOVE 5.2 → **20** |
| §5.2 | `心血管评估：低于 1.0 mmol/L 计入 15 分` | `hdlCholesterol` BELOW 1.0 → **15** |

其余四项按「单一异常不足以让心血管卡失真、多项异常能让总分封顶」定：

| 因子 | 方向 | 阈值 | 命中分值 | 未命中 | 说明 |
|---|---|---|---|---|---|
| 年龄 | ABOVE | 55 岁 | **15** | **5**（基线） | 基线 5 让「严重血压单项」能跨 MEDIUM（见 2.4）。年龄不可干预，故权重低于血压 |
| BMI | ABOVE | 24.0 | **20** | 0 | 对齐 §5.3 示例；取 24.0（而非旧 `bmiExtra` 的 28.0）使「超重」而非「肥胖」即纳入 |
| 收缩压 | ABOVE | 130 | **30** | 0 | 高于舒张压；单项严重即跨 MEDIUM |
| 舒张压 | ABOVE | 85 | **15** | 0 | |
| 血糖 | ABOVE | 6.1 | **10** | 0 | |
| 总胆固醇 | ABOVE | 5.2 | **20** | 0 | 对齐 §5.2 示例；TC 是血脂总量，权重最高 |
| 甘油三酯 | ABOVE | 1.7 | **10** | 0 | |
| HDL | **BELOW** | 1.0 | **15** | 0 | 对齐 §5.2 示例；下限型 |
| LDL | ABOVE | 3.4 | **15** | 0 | |

**上限合计 = 15 + 20 + 30 + 15 + 10 + 20 + 10 + 15 + 15 = 150。**

#### 2.4 两个可执行的设计不变量

**不变量 A（封顶可达）：** 上限 150 > 100，因此 `scaleTo()`（`:481-516`）在「血压 + 血脂 + 超重 + 年龄」同时异常的用户上**真实触发**，AC-5 的「含封顶时的按比例折算」不是一句走不到的注释。这也是**唯一**能让「合计 = 分数」这条测试有牙齿的设计（见 Q7 的 T-8）。

**不变量 B（单项严重不读 LOW）：** 分级带来自 `create()`：`score >= 60 → HIGH`、`score >= 35 → MEDIUM`、否则 LOW（`:563-571`）。年龄基线 5 + 收缩压 30 = **35 → MEDIUM**。因此「血压 168/80 的年轻用户，心血管卡不会是低风险」，与同一用户在高血压卡上的 MEDIUM 不再自相矛盾。

> 注意：孤立肥胖（5+20=25）、孤立高胆固醇（5+20=25）、孤立低 HDL（5+15=20）仍是 LOW。这是**有意**的 —— 单项血脂异常不构成与 2 级高血压同等的近期风险。此结论应写进注册表注释，否则会被后续阶段当成「权重没调好」。

#### 2.5 归因句的形态（Contract 7.2）

删除 `halve()`（`:455-461`）后，每条 `explanation` 必须自足、以指标名或「年龄」开头、**不含**「来自」/「折半」/「（折半计入」。当前快照里的反面样本（实测数据库原文）：

```
"explanation": "来自糖尿病：年龄 20 岁，未超过 45 岁，计入 10 分（折半计入 5 分）"
```

目标形态：`"BMI 22.9，未超过 24.0，未加分"`、`"收缩压 168 mmHg，超过 130 mmHg，计入 30 分"`。

> **一处必须同批修正的措辞**：`RiskContributorVO.threshold` 现在写「超过**参考值** 130 mmHg」（`:67-69`）。D-03 之后 130 是**风险阈值**，而同一屏的「参考范围」列显示 `90–139` —— 用户会看到两个互相矛盾的数字都叫「参考」。建议改为「超过**评估阈值**」。这不是 UI-SPEC 授权的文案改动，但对 D-03 是必要的一致性修正（Open Question 3）。

---

### Q3 — 三处确认的实现缺陷

#### 3(a) `--border-1` 未定义 → 归因行没有分隔线

**证据（本会话实测）：**

```
$ grep -rn "var(--border-1)" hhs-frontend-v2/src/ | wc -l
8
$ grep -rn "border-1" hhs-frontend-v2/src/ | grep -v "var(--border-1)"
(空)
```

8 处位置与 UI-SPEC Contract 3.2 的清单逐条对齐：`Risk.vue:1096`（`.risk-estimate__sources` background）、`:1097`（同元素 border）、`:1158`（`.risk-preparing`）、`:1193`（`.risk-coverage`）、`:1242`（`.risk-coverage__list`）、`:1254`（`.risk-coverage__item` border-bottom）、`:1371`（`.risk-dialog-breakdown__list`）、`:1383`（`.risk-dialog-breakdown__item` border-bottom）。

**真实存在的 token（`theme.css`，两套主题各三档）：**

```
23:  --border: rgba(67, 70, 81, 0.5);
24:  --border-strong: rgba(67, 70, 81, 0.9);
25:  --border-accent: rgba(94, 234, 212, 0.3);
...
141:  --border: rgba(0, 0, 0, 0.08);
142:  --border-strong: rgba(0, 0, 0, 0.15);
143:  --border-accent: rgba(13, 148, 136, 0.3);
```
[VERIFIED: hhs-frontend-v2/src/assets/styles/theme.css:23-25,141-143]

**最小修法：** 8 处 `var(--border-1)` → `var(--border)`，纯文本替换，零新增 token、零结构变更。`--border` 在两套主题都定义，不需要 fallback。
**耦合：** ①`border-bottom: 1px solid var(--border)` 是 4 行连续 MISSING 唯一的行分隔手段，必须在后端 D-02 上线**之前或同批**落地（UI-SPEC A-2 执行顺序第 2 项）；②`:1096` 是 `background` 而非 `border`，替换后 `gap: 1px` 的缝隙才可见，属同一缺陷的第二种表现。
**牙齿：** 新增源码扫描断言 `readFileSync(Risk.vue)` 中 `/var\(--border-1\)/g` 命中数为 **0**（做法与 `ResidueGate.spec.ts:518-526` 一致）。

#### 3(b) 覆盖面板三组谓词交叠 → 同一指标渲染两次

**证据（逐行）：**

```ts
// Source: hhs-frontend-v2/src/views/prevention/Risk.vue:845-853 [VERIFIED]
const usedMetrics = computed(() => coverage.value.filter(m => !!m.riskNote))

/** 有数据但不参与评估的指标。 */
const otherMeasured = computed(() =>
  coverage.value.filter(m => !m.riskNote && m.state !== 'MISSING')
)

/** 尚未采集的指标。 */
const missingMetrics = computed(() => coverage.value.filter(m => m.state === 'MISSING'))
```

`usedMetrics` 不看 `state`，`missingMetrics` 不看 `riskNote` → 交集非空。D-02 之后带 `riskNote` 的键是 8 个，其中 BMI 与血脂四项在任何用户上都可能是 `MISSING`（实测：血脂 0 行、`bmi` 档案 3/3 有但指标行 0 条），因此**每个用户固定重复 5 行**。

**最小修法（只改一行）：**

```ts
const missingMetrics = computed(() => coverage.value.filter(m => m.state === 'MISSING' && !m.riskNote))
```

**不改** `usedMetrics` / `otherMeasured`（UI-SPEC Contract 1.2 的「为什么改 missing 而不是改 used」）。
**连带（Contract 1.3）：** `coverageSummary.used` 保持 `usedMetrics.length`；新增 `usedMissing = usedMetrics.filter(m => m.state === 'MISSING').length`；模板 `:88-90` 文案 `项参与风险评估` → `项纳入评估范围`，并在 `usedMissing > 0` 时追加 `（其中 {{ usedMissing }} 项未测）`。**用 `未测` 不用 `缺数据`**（S1 的词）。
**耦合：** `missingMetrics.length` 同时驱动分组标题的 `（{n} 项；…）`（模板 `:143`），修正后该数字自动变小 5 —— 这是正确行为，不是回归。
**牙齿：** fixture 必须含一条 `{riskNote: '…', state: 'MISSING'}`；断言 ①该指标名在 `.risk-coverage__item` 中只出现一次，②三组之**和**等于 `coverage.length`（UI-SPEC ② 明确指出：只断言「互斥」可被「两组都空」满足）。

#### 3(c) 面板精度 ≠ 引擎精度

**证据（两侧算式）：**

```java
// 面板：Source: MetricCoverageAssembler.java:137-140 [VERIFIED]
private String formatValue(BigDecimal value, MetricDefinition def) {
    int precision = def.precision() != null ? def.precision() : 0;
    return value.setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString();
}

// 引擎：Source: RiskAssessmentServiceImpl.java:328 [VERIFIED]
String bmiValue = bmiAssumed ? "22.0（估算）" : bmi.stripTrailingZeros().toPlainString();
```

引擎侧共 4 处 `stripTrailingZeros()`：`:328`（bmi）、`:386`（bloodGlucose）、`:406`（systolicBP）、`:411`（diastolicBP）。`bmi` 的 precision 是 **1**（`HealthMetricMetaRegistry.java:187` 是 `1`，`normalMax` 在同段 `:192`），而入库值由 `HealthProfileServiceImpl.java:121` 以 `divide(..., 2, HALF_UP)` 写成 2 位小数 → **三行现有档案全部不一致**（`22.86`→`22.9`/`22.86`；`20.15`→`20.2`/`20.15`）。

**最小修法（UI-SPEC §5.3.1 route (b)）：** 把 `formatValue` 提为 `public static String format(BigDecimal value, MetricDefinition def)`，`formatValue` 改为委托；引擎四处调用点改为 `MetricCoverageAssembler.format(raw, def)`。
**为什么必须 `public`：** `RiskRuleEngine` 是 `RiskAssessmentServiceImpl` 的 `private static final class`（`:272`，同文件 `com.hhs.service.impl`），`MetricCoverageAssembler` 在 `com.hhs.service.domain` —— 包级私有跨包不可见，那条路线**编译不过**（UI-SPEC §5.3.1 已修正过这一点）。
**引擎如何拿到 `MetricDefinition`：** 两个合法形态（UI-SPEC 未指定，二选一即可）：
- **静态常量**：`RiskRuleEngine` 内 `private static final HealthMetricMetaRegistry METRICS = new HealthMetricMetaRegistry();` —— 构造无副作用（`HealthMetricMetaRegistry():24-26` 只跑 `registerAllMetrics()`；`init()` 有 `isEmpty()` 守卫，:28-33）；`applyRecommendations` 只改 `alertable`/`recommended` 并**复制** `precision`（`:520-535`），因此自建实例的 precision 与 Spring 实例一致。
- **传参**：`evaluateDetailed(..., RiskThresholdRegistry thresholds)`，由 `createAssessment` 传入 —— 与 Q1 的注册表注入同批完成，**推荐此形态**（少一个实例，且注册表本来就要传）。

**单位后缀留在 formatter 之外**（`" mmHg"`、`" mmol/L"`），`（估算）` 后缀保持不变（它是字面量兜底，不经 formatter）。
**牙齿：** 见 Q7 的 T-6（`22.86`→`22.9` 且 ≠`22.86`；`5.55`→`5.6`）。

---

### Q4 — `assemble()` 签名变更

**现状：**

```java
// Source: RiskAssessmentServiceImpl.java:219-221 [VERIFIED]
public List<MetricCoverageVO> getMetricCoverage(Long userId) {
    return metricCoverageAssembler.assemble(getRecentMetrics(userId));
}
```
```java
// Source: MetricCoverageAssembler.java:55 [VERIFIED]
public List<MetricCoverageVO> assemble(List<HealthMetric> recentMetrics) {
```

**最小签名变更：** `assemble(HealthProfile profile, List<HealthMetric> recentMetrics)`。
- 第一参数让 `bmi` 行能取 `profile.getBmi()`（引擎源，UI-SPEC §5.3）。
- `getMetricCoverage` 增加一次 `getProfileByUserId(userId)`（同一个 mapper，`stubProfile` 已覆盖）。
- **不得**回落到 `latest.get("bmi")`：UI-SPEC §5.3 明确，档案无值而指标表有行时，读指标行会让面板显示引擎从未使用的数字。

**调用点实测（本会话 `grep -rn "\.assemble("`）：**

| 位置 | 处数 |
|---|---|
| `RiskAssessmentServiceImpl.java:220` | 1 |
| `MetricCoverageAssemblerTest.java` `:42` `:53` `:65` `:79` `:90` `:101` `:121` `:133` `:144` | **9** |
| **合计** | **10** |

**UI-SPEC 的「9 处」正确**（它说的是测试文件内的处数），实际总改动面是 10 处（9 处测试 + 1 处生产）。测试侧可传 `null`（保持既有断言语义）或 `profileWithBmi(...)`（新增双源断言）。
**附带改动：** `MetricCoverageAssembler` 构造器从 `(HealthMetricMetaRegistry)` 变为 `(HealthMetricMetaRegistry, RiskThresholdRegistry)` → 命中 **2 处**：`MetricCoverageAssemblerTest.java:36`、`RiskAssessmentAssumptionsTest.java:64`。
**Strict stubs 注意：** `@ExtendWith(MockitoExtension.class)` 默认 STRICT_STUBS。`getMetricCoverage` 现在会触达 `healthProfileMapper`，新写的**只调 `getMetricCoverage`** 的测试必须自己 `stubProfile(...)`，否则 `selectOne` 返回 `null`（合法，得到全 MISSING 面板 —— 通常不是想要的）。

---

### Q5 — 迁移与数据现实

**实测（2026-11-18，容器 `health-hack-system-mysql-1`）：**

| 查询 | 结果 |
|---|---|
| `SELECT COUNT(*) FROM risk_assessment` | **54** |
| `COUNT(DISTINCT batch_id)` | **18**（每批 3 行） |
| `WHERE batch_id LIKE 'legacy%'` 的批次数 | **2** |
| `WHERE contributors IS NULL` | **6**（正是那 2 个 legacy 批次） |
| `WHERE contributors LIKE '%折半%'` | **16**（全部是心血管行） |
| `disease_name='心血管疾病'` | 18 行，其中 16 行含「折半」、2 行 NULL |
| `WHERE contributors LIKE '%22.86%'` | **0** |
| `WHERE contributors LIKE '%20.15%'` | 32 |
| `health_profile` | 3 行，`bmi` 全非空：`22.86` / `20.15` / `20.15`（profile id 1/2/3，对应 user 8/1/7） |
| `health_metric` 血脂四项 / `uricAcid` / `creatinine` / `fastingGlucose` | **0 行** |

> **与任务描述的一处偏差：** 提示里写「existing 17 stored assessment rows」，实测是 **54 行 / 18 批次**。18 批次 × 3 行 = 54，自洽；「17」大概是批次数的近似。计划应以 54/18 为准。

**D-02 落地后「4 行血脂 MISSING」可达吗？** 可达，且是**必然**：血脂四项在 `health_metric` 里 0 行，`HealthMetricMetaRegistry` 里 4 项定义齐全（`:217-287`），`MetricKeyNormalizer` 的 canonical 集合包含这 4 个键（`:36-39`）。因此引擎对 4 个键的 `getLatestMetric` 全部返回 `null` → `RiskContributorVO.threshold(..., null, ...)` → `missing()`（`:99-102`）→ `state=MISSING`、`points=0`、`explanation="缺少{label}数据，此项未计入评分"`。**四行逐行保留**（UI-SPEC Contract 3.2 明确否决聚合），弹窗底部警示句 `missingContributorCount` 显示 **4**。

**已有 54 行会变成什么样？** 不变。读取路径 `listByUserId` → `deserializeContributors`（`:156-166`）把 JSON 原样反序列化，**没有任何重算**：

```java
// Source: RiskAssessmentServiceImpl.java:203-209 [VERIFIED]
// 归因直接读快照：每行都带着它创建时的真实依据，与分数必然一致。
return assessments.stream()
        .map(r -> toVO(r, completeness, assumptions,
                deserializeContributors(r.getContributors())))
        .toList();
```

因此：
- 16 个心血管旧批次继续显示 `来自糖尿病：…（折半计入 N 分）` —— **正确**（当时确实是那么算的）。
- 2 个 legacy 批次 `contributors` 为 NULL → 空列表 → 前端 `v-if="currentAssessment?.contributors?.length"`（`Risk.vue:283`）不渲染构成区块，批次卡片显示「该记录创建时未保存评分明细」（`Risk.vue:184-186`）—— **既有且正确**。
- UI-SPEC §5.3.1 登记的「已知残留」（旧记录 `22.86` vs 面板 `22.9`）在本库里表现为 `20.15` vs `20.2`（32 行）。**不得回溯改写**（D-06）。
- 判定 A(i) 与 ④ 只能在**本次运行 `createAssessment` 的返回值**上断言，不能读历史批次（UI-SPEC §5.3.1 与 A-4.3 已写明）。

**表结构变更：** 无。`risk_assessment.contributors` 是 JSON 列（`sql/migration/v1.4_risk_snapshot.sql:57-71`），`RiskAssessment.contributors` 是 `String`（entity `:43`）。新增的输入项只是 JSON 里多几条记录，不需要 DDL。

---

### Q6 — Validation Architecture 的分层（简答，详见下方专节）

**判定原则：一条断言只能写在真正执行被测代码的语言里。**

| 断言 | 必须写在哪 | 为什么 | 当前构造形态是否够用 |
|---|---|---|---|
| 心血管归因不含「来自」/「折半」（④） | JUnit `RiskAssessmentAssumptionsTest` | 只有它能跑真实引擎 | ✅ 现成（`service.createAssessment`） |
| 两侧 `bmi.value()` 字符串相等（A(i)） | JUnit 同上 | 它比较**后端两次方法调用**的返回值；前端 `createRiskAssessment` 是 `vi.fn()` | ⚠️ 需 `getMetricCoverage` 首次被本文件调用 + `stubMetrics` 支持小数 |
| 血脂四项全 MISSING、无 ASSUMED | JUnit 同上 | 真实引擎 | ✅ 现成 |
| `riskNote` 形状/长度/无来源词（⑤⑥） | JUnit `MetricCoverageAssemblerTest` | `riskNote` 是后端常量产出，前端只是 `{{ m.riskNote }}` 直出 | ⚠️ 构造器 +1 参数 |
| 面板按风险阈值分类、bmi 读档案 | JUnit `MetricCoverageAssemblerTest` | 判定逻辑在 assembler | ⚠️ `assemble(profile, metrics)` 9 处 |
| 分组互斥（①②）、摘要措辞（③） | Vitest `RiskBackfill.spec.ts` | 纯 Vue computed/模板 | ⚠️ mock 缺 `getMetricCoverage`，必须补 |
| 两套词表各自渲染（⑦A(ii)/A(iii)） | Vitest 同上 | 渲染层 | ⚠️ 同上 |
| `--border-1` 归零、列宽 ≥88px | Vitest 源码扫描 | 纯前端源码属性 | ✅ 有 `ResidueGate.spec.ts` 先例 |

**UI-SPEC 的分工可实现**，但有三处前置条件必须在计划的第一个 wave 完成，否则「测试编译不过 / 断言无对象」：
1. `RiskBackfill.spec.ts:22-31` 的 `vi.mock('@/api/prevention', …)` 工厂**追加** `getMetricCoverage: vi.fn()`（否则 `coverage.value` 恒为 `[]`，①②③⑦ 全部在断言空数组）。
2. `RiskAssessmentAssumptionsTest.stubMetrics` 只接受 `Integer` —— 实测 `:373-383` 的 `new BigDecimal((Integer) keysAndValues[i + 1])`，造不出 `5.55`；需要 `new BigDecimal(String.valueOf(v))` 或重载。
3. `RiskAssessmentAssumptionsTest:62-64` 构造 service 的 5 参数调用要随构造器变更同步（+`RiskThresholdRegistry`）。

**基线实测（本会话）：** 后端 `MetricCoverageAssemblerTest` **9 条全绿**、`RiskAssessmentAssumptionsTest` **26 条全绿**；前端 `RiskBackfill.spec.ts` **29 条全绿**（`npx vitest run` → `29 passed`）。与 UI-SPEC A-4.4 声明的条数完全一致。

---

### Q7 — 本项目已经烧过的两个陷阱

#### 7(i) 硬编码中文名 / 旧别名查 `metric_key` 恒落空

**已有机制：**

```java
// Source: RiskAssessmentServiceImpl.java:534-539 [VERIFIED]
private static BigDecimal getLatestMetric(List<HealthMetric> metrics, String canonicalKey) {
    Set<String> aliases = MetricKeyNormalizer.getAliases(canonicalKey);
    return metrics.stream()
            .filter(m -> m.getMetricKey() != null && aliases.contains(m.getMetricKey()))
            .findFirst()
            .map(HealthMetric::getValue)
            .orElse(null);
}
```

**本阶段新增的落空风险（三处，必须在计划里指名）：**

1. **`RiskThresholdRegistry` 的键拼错** → `rulesFor(key)` 返回空 → 引擎静默不读、面板静默无 note，**两边同时沉默，测试若只断言「有 9 行」仍会绿**。
   **对策：** 构造期自检 —— 每个注册的 key 必须 `MetricKeyNormalizer.isCanonicalKey(key)` **且** `metaRegistry.contains(key)`，否则抛 `IllegalStateException`。把静默 null 变成启动失败。牙齿：`assertEquals(consumedKeys(), consumedKeys().stream().filter(MetricKeyNormalizer::isCanonicalKey).collect(...))` 只能证明拼写，真正有牙齿的是「`consumedKeys()` 恰好等于 8 个预期键的集合」。
2. **实测存在一条非别名变体：** `health_metric` 有 `id=29, user_id=8, metric_key='systolicBloodPressure', value=118.00`。`MetricKeyNormalizer` 的 `systolicBP` 别名组只有 `{"systolic_bp", "systolicBp", "SYSTOLIC_BP", "sbp", "SBP"}`（`:56`），`simplify("systolicBloodPressure")` = `systolicbloodpressure` 不在 `SIMPLIFIED_MAP` → **lenient passthrough**（`:135-136`）→ 引擎与面板都读不到这一行。这正是陷阱 7(i) 的现役实例。
   **对策（建议，需确认）：** 在 `aliasGroup.put("systolicBP", …)` 里加 `"systolicBloodPressure"`、`"diastolicBP"` 里加 `"diastolicBloodPressure"`（各 1 个字符串）。影响面：该行会开始参与高血压评估与覆盖面板（今天是「未测」）。若不改，必须在计划里**显式记录为已知漏读**，否则下一阶段的报告会把它当新 bug 重查一遍。
3. **测试夹具用中文键：** `stubMetrics("收缩压", 168)` 会让 `getAliases("systolicBP")` 匹配不到，分数静默少 35。夹具必须用 canonical 键（现有测试已经这么做，`:413`）。

#### 7(ii) 没有牙齿的测试

本项目实证先例（`.planning/phases/15-risk-history-batch/15-02-SUMMARY.md:85-88`）：聚类阈值改 1 小时，用「相隔一天」的夹具照样通过；改成相隔 30 分钟后才有约束力。

**本阶段每一条新阈值/新规则的「无牙版」与「有牙版」：**

| # | 被测规则 | ❌ 无牙夹具（两种实现都过） | ✅ 有牙夹具（只有正确实现过） | 断言 |
|---|---|---|---|---|
| T-1 | 收缩压风险阈值 130（参考上限 139） | `168` | **`135`** | `state == CONTRIBUTED`、`points == 30`。若误用参考范围 139 → `NORMAL`、0 分，红 |
| T-2 | 舒张压风险阈值 85（参考上限 89） | `95` | **`87`** | `CONTRIBUTED`、15 分 |
| T-3 | BMI 风险阈值 24.0（参考上限 23.9） | `30` | **`23.96`** | 面板：按风险阈值 → `NORMAL`；按参考范围 → `HIGH`。**这是唯一能区分两种分类源的夹具** |
| T-4 | 血糖精度 1（引擎旧实现输出 `5.55`） | `14`（整数，两种实现都给 `14`/`14.0`） | **`5.55`** | formatter → `"5.6"`；≠ `"5.55"` |
| T-5 | BMI 精度 1 | `BigDecimal.valueOf(22)` | **`22.86`** | `"22.9"`；≠ `"22.86"`；两侧字符串相等 |
| T-6 | HDL 下限方向 | 只测 MISSING | **`0.90`** | `CONTRIBUTED` + explanation 含「低」且**不含**「超过」；`riskNote` 含「低于」 |
| T-7 | 血脂缺失不代入默认值 | 测血脂有值 | **四项全缺** | 4 条 `MISSING`，且 `contributors` 中**无任何** `ASSUMED` |
| T-8 | 封顶折算（AC-5） | 全部正常的用户（合计 5，`scaleTo` 不执行） | **年龄 60 + BMI 26 + BP 168/95 + 血糖 8 + 血脂四项全异常**（合计 150） | `riskScore == 100` **且** `Σ contributors.points == 100` **且** 至少一条 explanation 含「按比例折算」 |
| T-9 | 面板分组互斥 | 全部有值、无 MISSING | **一条 `{riskNote, state:'MISSING'}`** | 该键只渲染一次；**三组之和 == `coverage.length`** |
| T-10 | 面板与引擎同源（`riskNote` 集合） | 只断言「有 riskNote」 | **精确集合** `{bloodGlucose, systolicBP, diastolicBP, bmi, totalCholesterol, triglycerides, hdlCholesterol, ldlCholesterol}` | 多一个或少一个都红 |
| T-11 | `--border-1` 归零 | 无 | 源码全文 | `Risk.vue` 中 `var(--border-1)` 计数 **== 0** |

**T-3 需要额外说明：** 数据库 `value` 是 `DECIMAL(10,2)`（`schema.sql:94`），`23.96` 可入库；但 `stubMetrics` 目前在内存里造 `BigDecimal`，只需支持两位小数。

---

## Common Pitfalls

### Pitfall 1: 把「参与评估」当成「有值」
**What goes wrong:** 修 3(b) 时顺手把缺失的参与项从「参与」组移走，让摘要数字好看。
**Why it happens:** `8 项纳入` 与 `已测 8/23` 并排读起来矛盾（其实已测只有 3 项有值）。
**How to avoid:** UI-SPEC Contract 1.3 的做法 —— 保留在「参与」组（状态列已写 `未测`），把摘要措辞改成「纳入评估范围」并追加「（其中 N 项未测）」。
**Warning signs:** `usedMetrics.length` 与 `consumedKeys().size()` 不相等。

### Pitfall 2: 在 `impl` 包复制 formatter「省一次构造函数改动」
**What goes wrong:** 编译更快，但两侧精度重新分叉，且 `assert A(i)` 变成同义反复。
**How to avoid:** `public static` + 引擎调用；把「复制一份」写进计划的禁止项。
**Warning signs:** `grep -rn "setScale" hhs-backend/src/main/java` 命中数 > 2。

### Pitfall 3: 用 `normalMin/normalMax` 给风险指标分类（D-03 的反例）
**What goes wrong:** 收缩压 130–139 从「计 30 分」变成「不计分」，中风险掉成低风险。
**Why it happens:** 「全站单一来源」听起来更干净。
**How to avoid:** 有规则的指标按 `RiskThresholdRegistry`，无规则的维持参考范围；T-1/T-2/T-3 三个夹具夹住。
**Warning signs:** 一条 135 mmHg 的血压在覆盖面板显示「正常」。

### Pitfall 4: 依赖 `stripTrailingZeros()` 的数值字符串
**What goes wrong:** `BigDecimal("22.86").stripTrailingZeros().toPlainString()` = `"22.86"`，而面板给 `"22.9"`；反过来 `BigDecimal("22.0")` 会变成 `"2.2E+1"`（科学计数法的历史坑，本仓库用 `toPlainString()` 规避）。
**How to avoid:** 只用 `MetricCoverageAssembler.format`。
**Warning signs:** 两个表面显示同一个数的不同小数位。

### Pitfall 5: 让历史快照「跟着新代码走」
**What goes wrong:** 有人为了让 ④（不含「折半」）在全部批次上通过，回去批量更新 `contributors`。
**Why it happens:** 测试读历史批次比读新创建的结果更容易。
**How to avoid:** D-06；测试**先跑一次 `createAssessment` 再断言返回值**（UI-SPEC A-4.3 的取数口径）。
**Warning signs:** 出现针对 `risk_assessment` 的 UPDATE 语句。

### Pitfall 6: 血栓式补全 —— 顺手加 heartRate / uricAcid / creatinine
**What goes wrong:** 行数与 `riskNote` 数超出 UI-SPEC 已测量的几何，Contract 2/3/4 的列宽与宽度链路全部要重量。
**How to avoid:** 本阶段严格 9 行 / 8 键；新增指标必须另开阶段并重跑 UI 契约。
**Warning signs:** `consumedKeys().size() != 8`。

### Pitfall 7: Checkstyle 的「绿」是宽松的绿
**What goes wrong:** 以为新文件必须 0 告警。
**Why it happens:** `hhs-backend/target/checkstyle-result.xml` 现有 **15333 条 `severity="warning"`、0 条 error**，插件未配置 `violationSeverity`，默认只在 error 级失败（`pom.xml:301-326`）。且 `checkstyle-result.xml` 中 `src/test` 命中数为 **0** —— 只扫主源码。
**How to avoid:** 仍然遵守 100 列与 Javadoc 习惯（`google_checks.xml`），但不要把「新增 warning」当成门禁红。**真正的门禁是 `mvn verify -DskipITs` 的测试结果。**
**Warning signs:** 为了消警告去改 `pom.xml`。

---

## Code Examples

### 示例 1：注册表的骨架（可照写）

```java
// Source: 形态取自 HealthMetricMetaRegistry.java:24-33（构造即注册 + isEmpty 守卫）
package com.hhs.service.domain;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 风险专用阈值与「引擎读哪些指标」的唯一声明（D-03 / D-05）。
 *
 * <p>风险阈值严于或等于元数据参考上限（预防性），因此不能复用
 * {@link MetricDefinition#normalMax()}。规则以 {@code (metricKey, disease)} 为唯一键：
 * 同一个指标在不同疾病里可以有不同的阈值与分值（如 BMI 在糖尿病与心血管）。
 */
@Component
public class RiskThresholdRegistry {

    public enum Direction { ABOVE, BELOW }

    public record RiskThresholdRule(String metricKey, String disease,
                                    Direction direction, BigDecimal threshold, int points) {
    }

    private final Map<String, List<RiskThresholdRule>> byMetric = new LinkedHashMap<>();

    public RiskThresholdRegistry() {
        registerAllRules();
    }

    @PostConstruct
    public void init() {
        if (byMetric.isEmpty()) {
            registerAllRules();
        }
    }

    private void registerAllRules() {
        // 顺序即 riskNote 的取用顺序（UI-SPEC 5.5：只描述第一条）
        // 糖尿病 / 高血压：数值与现状完全一致，只是搬了家
        rule("bloodGlucose", "糖尿病", Direction.ABOVE, "6.1", 30);
        rule("systolicBP", "高血压", Direction.ABOVE, "130", 35);
        rule("diastolicBP", "高血压", Direction.ABOVE, "85", 30);
        rule("bmi", "糖尿病", Direction.ABOVE, "24.0", 25);
        // 心血管：D-01 的独立输入集（UI-SPEC 5.2/5.3 的三个示例分值已对齐）
        rule("bmi", "心血管", Direction.ABOVE, "24.0", 20);
        rule("systolicBP", "心血管", Direction.ABOVE, "130", 30);
        rule("diastolicBP", "心血管", Direction.ABOVE, "85", 15);
        rule("bloodGlucose", "心血管", Direction.ABOVE, "6.1", 10);
        rule("totalCholesterol", "心血管", Direction.ABOVE, "5.2", 20);
        rule("triglycerides", "心血管", Direction.ABOVE, "1.7", 10);
        rule("hdlCholesterol", "心血管", Direction.BELOW, "1.0", 15);
        rule("ldlCholesterol", "心血管", Direction.ABOVE, "3.4", 15);
    }

    private void rule(String key, String disease, Direction dir, String threshold, int points) {
        if (!MetricKeyNormalizer.isCanonicalKey(key)) {
            throw new IllegalStateException("风险阈值必须使用 canonical key: " + key);
        }
        byMetric.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new RiskThresholdRule(key, disease, dir, new BigDecimal(threshold), points));
    }

    public List<RiskThresholdRule> rulesFor(String canonicalKey) {
        return List.copyOf(byMetric.getOrDefault(canonicalKey, List.of()));
    }

    public Set<String> consumedKeys() {
        return Collections.unmodifiableSet(byMetric.keySet());
    }

    public List<RiskThresholdRule> allRules() {
        return byMetric.values().stream().flatMap(List::stream).toList();
    }
}
```

> `bmi` 的两条规则阈值同为 24.0，因此谁在前只影响 `riskNote` 的疾病名（UI-SPEC §5.3 的示例期望是「心血管评估：超过 24.0 kg/m² 计入 20 分」），**建议把心血管那条声明在前**。`bloodGlucose` 则相反：Contract 5.2 的示例是「糖尿病评估：超过 6.1 mmol/L 计入 30 分」，必须糖尿病在前。这个「逐键手选的声明顺序」必须写注释，否则下一个人会「统一排序」把它打乱。

### 示例 2：方向感知的归因工厂

```java
// RiskContributorVO 新增（与 threshold(...) 并列，不动既有签名）
public static RiskContributorVO below(String key, String label, String value,
                                      String threshold, boolean breached, int points) {
    if (value == null) {
        return missing(key, label);
    }
    String state = breached ? STATE_CONTRIBUTED : STATE_NORMAL;
    String explanation = breached
            ? label + " " + value + "，低于评估阈值 " + threshold + "，因此计入 " + points + " 分"
            : label + " " + value + "，不低于评估阈值 " + threshold + "，未加分";
    return new RiskContributorVO(key, label, state, value, threshold, points, explanation);
}
```

### 示例 3：心血管段的重写轮廓（删除 `halve` 后）

```java
// 目标形态（伪代码；9 条归因，逐项独立）
List<RiskContributorVO> cardioParts = new ArrayList<>();

int ageCardioPoints = age > 55 ? 15 : 5;   // 基线 5 保证「单项严重血压 → MEDIUM」
cardioParts.add(new RiskContributorVO("age", "年龄", ageState, ageValue, "55 岁",
        ageCardioPoints, "年龄 " + ageValue + (age > 55 ? "，超过 55 岁，" : "，未超过 55 岁，")
                + "计入 " + ageCardioPoints + " 分"));

cardioParts.add(thresholdOrMissing("bmi", bmi, bmiAssumed, thresholds, metrics));         // 20
cardioParts.add(thresholdOrMissing("systolicBP", systolic, false, thresholds, metrics));  // 30
cardioParts.add(thresholdOrMissing("diastolicBP", diastolic, false, thresholds, metrics));// 15
cardioParts.add(thresholdOrMissing("bloodGlucose", latestBloodSugar, false, ...));         // 10
// 血脂四项：全部走同一个 helper，value == null → RiskContributorVO.missing(...)
cardioParts.add(thresholdOrMissing("totalCholesterol", totalChol, false, ...));            // 20
cardioParts.add(thresholdOrMissing("triglycerides", triglycerides, false, ...));           // 10
cardioParts.add(belowOrMissing("hdlCholesterol", hdl, ...));                               // 15（下限型）
cardioParts.add(thresholdOrMissing("ldlCholesterol", ldl, ...));                           // 15

int cardioRaw = cardioParts.stream().mapToInt(RiskContributorVO::points).sum();  // max 150
if (cardioRaw > 100) {
    cardioParts = scaleTo(cardioParts, cardioRaw, 100);   // 保留：AC-5 的封顶折算
}
int cardioScore = Math.min(100, cardioRaw);
```

四条血脂的取值必须来自**同一批** `getLatestMetric` 调用，且键从 `thresholds.consumedKeys()` 派生（D-05）。

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| `cardioRaw = diabetesScore/2 + bpScore/2 + bmiBonus` | 心血管自有 9 项输入独立求和 | 本阶段（D-01） | `halve()` 删除；`来自`/`折半` 措辞从源头消失 |
| `RISK_CONSUMERS`（面板）+ `getLatestMetric`（引擎）两处维护 | `RiskThresholdRegistry.consumedKeys()` 单一声明 | 本阶段（D-05） | 面板不再可能「说参与而引擎不读」 |
| 阈值散落在引擎的 6 个 `BigDecimal.valueOf` | 集中在 `RiskThresholdRegistry` | 本阶段（D-03/D-04） | 面板分类与引擎判定同源 |
| 面板精度 formatter 与引擎 `stripTrailingZeros` | 单一 `public static format` | 本阶段（UI-SPEC §5.3.1） | 同一数字在两个表面同一个字符串 |
| 缺失项「计 0 分且看起来正常」 | `MISSING` 状态 + 0 分（D-07） | v1.3 起 | 本阶段把该语义扩展到血脂 |

**Deprecated/outdated:**
- `MetricCoverageAssembler.RISK_CONSUMERS`（`:37-47`）及其 `buildRiskConsumers()` —— 本阶段删除。
- `RiskAssessmentServiceImpl.halve()`（`:455-461`）—— 本阶段删除。
- `RiskRuleEngine.evaluate(...)` 的两个不需要 collector 的重载（`:273-282`）在本阶段无调用者（`grep RiskRuleEngine` 只命中 `ProfileFieldCatalog` 的一句注释），可保留不动（Phase 18 技术债清理范围）。

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | 心血管权重取 **年龄15/5、BMI20、SBP30、DBP15、血糖10、TC20、TG10、HDL15、LDL15（上限150）** | Q2 | 分值分布是产品判断，无外部权威可引。若用户希望与糖尿病/高血压的分值梯度一致，需重排；T-8 的封顶夹具依赖上限 >100 |
| A2 | 心血管**不**读 `heartRate`（避免 10 行归因 / 9 个 note，破坏 UI-SPEC 已测量的几何） | Q2.2 | CONTEXT 首段以「心率 122 未被使用」为例说明可见性问题。若用户认为它必须本阶段纳入，Contract 2/3/4 的全部数量与列宽需重算 |
| A3 | 心血管**不**读 `weight`（与 BMI 重复） | Q2.2 | 若纳入，需决定与 BMI 的去重规则 |
| A4 | `bmi` 风险阈值保留 **24.0**（与参考上限 23.9 相比更松 0.1），不改数值 | Q1.3 | AC-3 的「严于参考上限」对该指标不成立；改成 23.9 会新增一段计分人群，属未授权行为变化 |
| A5 | 覆盖面板的 `classify` 对**有风险规则的指标**改用风险阈值，其余维持参考范围 | Q1.4 | 会让摘要第二段「{n} 项超出参考范围」对 3 个指标（SBP 130–139、DBP 85–89、BMI 23.9–24.0）失真。UI-SPEC 明确把该措辞留待「与 D-03 同批确认」，需一次显式决策（Open Question 2） |
| A6 | `RiskContributorVO.threshold` 的「超过参考值」改为「超过评估阈值」 | Q2.5 | D-03 之后 130 是风险阈值而参考范围列显示 90–139，同屏两个「参考」互相矛盾；但这是 UI-SPEC 未登记的文案改动 |
| A7 | 在 `MetricKeyNormalizer` 补 `systolicBloodPressure` / `diastolicBloodPressure` 两个别名 | Q7(i) | 库中有 1 行 `systolicBloodPressure`（user 8, 118.00）今天被静默忽略。补别名会让它开始参与评估；不补则需显式登记为已知漏读 |
| A8 | 引擎取 `MetricDefinition` 走**传参**（`evaluateDetailed(..., RiskThresholdRegistry)`），而非 `RiskRuleEngine` 内 `new` 一个注册表实例 | Q3(c) / Q1.4 | 需改服务构造器（+1 参数），命中 1 处测试（`RiskAssessmentAssumptionsTest:62-64`）。UI-SPEC 明确两种都可 |

---

## Open Questions

1. **`heartRate` 要不要进心血管输入集？**
   - What we know: 有 5 个用户 / 10 行数据（全场第 3 多）；CONTEXT 首段举的例子是「我 122 的心率偏高，但系统没拿它算风险」，暗示它是本阶段的动机；`HealthMetricMetaRegistry:37-53` 有完整定义（precision 0、normalMax 100）。
   - What's unclear: UI-SPEC Contract 2 明列「约 8 项」riskNote、Contract 3.1 明列 9 行归因、Contract 4 的 88px 只对 9 行量过。加 `heartRate` → 10 行 / 9 键，三处已测量的数字全部失效。
   - Recommendation: **本阶段不加**，在计划的里程碑说明里显式登记「心率仍不参与评估」为已知缺口，留到 Phase 17/18 与几何重量一起做。若用户坚持，须先修订 UI-SPEC 再执行。

2. **覆盖面板按风险阈值分类后，摘要第二段「{n} 项超出参考范围」怎么办？**
   - What we know: D-03 要求面板从 `RiskThresholdRegistry` 读阈值；UI-SPEC §5.3 明确写「两者同批落地时，这句话的措辞需一并确认；本契约不单方面改动它」。受影响区间：SBP 130–139、DBP 85–89、BMI 23.9–24.0（实测库里当前 **0 行**落在这三个区间，所以今天看不出问题，但用户录入后立刻显现）。
   - What's unclear: 改成什么词。候选：`项超出评估阈值`（一词之差，最小改动）或拆成两个计数（新文案，超出 Copywriting Contract）。
   - Recommendation: 改一个词 → `项超出评估阈值`。理由是同一屏的 pill 已写「偏高/偏低」，而「参考范围」列在紧邻位置显示 `90–139`，继续用「参考范围」会构成自相矛盾。需在计划里标注为「对 Copywriting Contract「不变」项的一次显式修订」。

3. **「超过参考值」是否改为「超过评估阈值」？**
   - What we know: `RiskContributorVO.java:67-69` 硬编码「参考值」；D-03 之后 130/85/24 是风险阈值，与面板同行显示的参考范围（139/89/23.9）不同值。
   - What's unclear: 是否算 scope 蔓延。
   - Recommendation: 改。它不是新功能，而是 D-03 的直接一致性后果；不改会让同一行出现两个「参考值」。

4. **`systolicBloodPressure` 别名要不要补？**
   - 见 A7。Recommendation: 补（2 个字符串），并加一条测试断言 `getAliases("systolicBP").contains("systolicBloodPressure")`；若不补，在计划的 Known Issues 里写明这 1 行被忽略。

5. **`bmi` 在糖尿病里「未超阈值也计 10 分」的基线是否合理？**
   - What we know: `:358-359` `int bmiPoints = bmiHigh ? 25 : 10;` —— 一个 BMI 20 的人仍得 10 分。
   - What's unclear: 这是有意的基础分，还是历史遗留。
   - Recommendation: **本阶段不动**（改它会改变所有糖尿病人群的分数分布，且无决策授权）。在注册表注释里写明「10 分是无条件基线，不入阈值表」，避免下一个人把它当漏掉的规则。

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---|---|---|
| JDK | 后端测试 | ✓ | 17（`java -version` → 17+35-LTS-2724） | — |
| Maven | 后端门禁 | ✓ | 3.8.4 | — |
| Node.js | 前端测试/构建 | ✓ | v24.15.0 | — |
| npm | 前端门禁 | ✓ | 11.12.1 | — |
| Maven 本地仓库（离线可跑） | `mvn -o test` | ✓ | 实测 `mvn -o -Dtest=… test` 成功，9 + 26 条全绿 | 联网跑 `mvn verify` |
| Docker + MySQL 容器 | 数据现实核对（只读） | ✓ | `health-hack-system-mysql-1` healthy，库 `hhs` | 无（不阻塞编码；缺失时以 CONTEXT 的数据表为准） |
| 前端依赖已安装 | `npx vitest` | ✓ | `node_modules` 存在，`29 passed` | `npm ci` |

**Missing dependencies with no fallback:** 无。
**Missing dependencies with fallback:** 无。

---

## Validation Architecture

> `.planning/config.json` 的 `workflow.nyquist_validation` 为 `true`（实测键存在且为真），本节按启用处理。

### Test Framework

| Property | Value |
|---|---|
| Framework（后端） | JUnit 5（`spring-boot-starter-parent` 3.2.0 管理）+ Mockito（`MockitoExtension`，STRICT_STUBS） |
| Framework（前端） | Vitest `^4.1.0` + `@vue/test-utils ^2.4.6`，`environment: happy-dom`（`vitest.config.ts`） |
| Config file（后端） | `hhs-backend/pom.xml`（surefire 3.0.0，排除 `*IntegrationTest`/`*PerformanceTest`） |
| Config file（前端） | `hhs-frontend-v2/vitest.config.ts` |
| Quick run（后端，单类） | `mvn -f hhs-backend/pom.xml -o -DskipITs -Dtest=RiskAssessmentAssumptionsTest test` |
| Quick run（后端，两类） | `mvn -f hhs-backend/pom.xml -o -DskipITs -Dtest='RiskAssessmentAssumptionsTest,MetricCoverageAssemblerTest' test` |
| Quick run（前端，单文件） | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/RiskBackfill.spec.ts` |
| Full suite（后端门禁） | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` + `mvn -f hhs-backend/pom.xml checkstyle:check` |
| Full suite（前端门禁） | `cd hhs-frontend-v2 && npm run test:run` + `npm run build` |

**基线（本会话实测，改前）：**

| 套件 | 条数 | 结果 |
|---|---|---|
| `MetricCoverageAssemblerTest` | 9 | 0 errors / 0 failures |
| `RiskAssessmentAssumptionsTest` | 26 | 0 errors / 0 failures |
| `RiskBackfill.spec.ts` | 29 | 29 passed |
| `checkstyle-result.xml` | 15333 warning / **0 error** | `checkstyle:check` 通过（默认 `violationSeverity=error`，且只扫 `src/main`） |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|
| AC-1 | 心血管归因不含 `来自`/`折半`/`（折半计入` | unit | `mvn -f hhs-backend/pom.xml -o -DskipITs -Dtest=RiskAssessmentAssumptionsTest test` | ✅ 加 1 条 `it` |
| AC-1 | 心血管归因 **9 条**，key 集合恰为预期集合 | unit | 同上 | ✅ 加 1 条 |
| AC-2 | 血脂四项全缺 → 4 条 `MISSING`，无 `ASSUMED`，无 0 分伪造 | unit | 同上 | ✅ 加 1 条 |
| AC-3 | SBP 135 计 30（参考范围 139 下会计 0）；DBP 87 计 15 | unit | 同上 | ✅ 加 1 条（参数化） |
| AC-3 | `RiskThresholdRegistry` 的 12 条规则 `consumedKeys().size()==8`，且每个键 `isCanonicalKey` | unit | `-Dtest=RiskThresholdRegistryTest` | ❌ **Wave 0** 新建类 |
| AC-4 | `riskNote` 键集合恰为 8 个；每条匹配 `^[^：]+评估：(超过\|低于\|超出) .+ 计入 \d+ 分$` 且去空白 ≤24 | unit | `-Dtest=MetricCoverageAssemblerTest` | ✅ 改现有 `onlyEngineInputsCarryRiskNote`（`:99-116` 现在断言 3 个键） |
| AC-4 | 每个有 `riskNote` 的键都被引擎读过（反向：`consumedKeys()` ⊆ 引擎 `getLatestMetric` 集合） | unit | 同上 + 引擎 | ✅ 加 1 条 |
| AC-5 | 合计 150 的夹具 → `riskScore==100` 且 `Σpoints==100` 且 explanation 含「按比例折算」 | unit | `-Dtest=RiskAssessmentAssumptionsTest` | ✅ 加 1 条 |
| AC-5 | `bmi` 两侧 `value()` 字符串相等（`"22.9"`）、`≠ "22.86"` | unit | 同上 | ✅ 加 1 条（A(i)） |
| AC-6 | `glucose` 别名仍计分（既有）；`systolicBloodPressure` 是否计分（取决于 Open Question 4） | unit | 同上 | ✅ 既有 2 条 + 可选 1 条 |
| AC-7 | 历史批次读时返回快照原文（含「折半」），不重算 | unit | `-Dtest=RiskAssessmentAssumptionsTest` | ✅ 加 1 条（构造 1 行带旧快照 JSON 的 `RiskAssessment`） |
| AC-8 | 后端全绿 | gate | `mvn … clean verify -DskipITs` + `checkstyle:check` | ✅ |
| AC-8 | 前端全绿 | gate | `npm run test:run` + `npm run build` | ✅ |
| 3(a) | `Risk.vue` 中 `var(--border-1)` 计数 == 0 | unit（源码扫描） | `npx vitest run src/__tests__/views/ResidueGate.spec.ts`（或 RiskBackfill） | ✅ 仿 `ResidueGate.spec.ts:518-526` |
| 3(b) | 有 `riskNote` 且 `MISSING` 的指标只渲染一次；三组之和 == `coverage.length` | unit（组件） | `npx vitest run src/__tests__/views/RiskBackfill.spec.ts` | ✅ 加 2 条 |
| 3(b) | `usedMissing>0` 时渲染「（其中 N 项未测）」，且不出现「缺数据」 | unit（组件） | 同上 | ✅ 加 1 条 |
| 3(c) | 面板 `bmi` 行 `value` 来自 profile；档案无值时报 `MISSING` 而非读指标行 | unit（domain） | `-Dtest=MetricCoverageAssemblerTest` | ✅ 加 2 条 |
| UI-SPEC C4 | 弹窗首列 ≥ 88px | unit（源码扫描） | `npx vitest run …RiskBackfill.spec.ts` | ✅ 加 1 条 |
| UI-SPEC C6 | 两套词表各自渲染（`未测`/`按默认值`） | unit（组件） | 同上 | ✅ 加 2 条 |

### Sampling Rate

- **Per task commit:** 单类快跑（后端 `-Dtest=<改动类>`；前端 `npx vitest run <改动文件>`）。
- **Per wave merge:** 后端 `mvn -f hhs-backend/pom.xml -o -DskipITs test` + 前端 `npm run test:run`。
- **Phase gate:** `mvn clean verify -DskipITs` + `checkstyle:check` + `npm run test:run` + `npm run build`，全绿后进 `/gsd:verify-work`。

### Wave 0 Gaps

- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/RiskThresholdRegistryTest.java` —— 新建。覆盖 AC-3（12 条规则的键/方向/阈值/分值）、AC-4 的 `consumedKeys()` 精确集合、构造期 canonical key 自检（`assertThrows`）。
- [ ] `RiskBackfill.spec.ts:22-31` 的 `vi.mock('@/api/prevention', …)` 工厂**追加** `getMetricCoverage: vi.fn()` —— 否则 ①②③⑦A(ii)/A(iii) 全部在断言空数组（UI-SPEC A-4.1）。**这是所有前端断言的唯一前置。**
- [ ] `RiskAssessmentAssumptionsTest.stubMetrics`（`:373-383`）支持两位小数（`new BigDecimal(String.valueOf(v))` 或 `Number` 重载）—— 否则 T-3/T-4/T-5 无法表达。
- [ ] `RiskAssessmentAssumptionsTest` 的 `service` 构造（`:62-64`）同步 +`RiskThresholdRegistry`（与 `MetricCoverageAssembler` 共用同一实例）。
- [ ] `MetricCoverageAssemblerTest` 的 `assembler` 构造（`:36`）同步 +`RiskThresholdRegistry`；9 处 `assemble(List.of(...))` 改为 `assemble(null, List.of(...))` 或带 profile。
- [ ] 无框架安装缺口（JUnit / Vitest 均已就位）。

---

## Security Domain

> `.planning/config.json` 无 `security_enforcement: false`（键不存在 → 按启用处理）。

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | no | 端点已 `@PreAuthorize("isAuthenticated()")`（`PreventionController.java:210`），本阶段不改 |
| V3 Session Management | no | 无状态 JWT 既有，不改 |
| V4 Access Control | yes（回归） | `getMetricCoverage` 用 `SecurityUtils.getCurrentUserId()`（`PreventionController.java:211-213`）取本人，**新增的 `getProfileByUserId` 必须用同一个 userId**，不得引入可被外部指定的参数 |
| V5 Input Validation | yes（间接） | 新增 `RiskThresholdRegistry` 的键在构造期自检（`isCanonicalKey` + `contains`）；无外部输入进入本阶段的判定路径 |
| V6 Cryptography | no | 本阶段不涉及 |

### Known Threat Patterns for {Spring Boot + 规则引擎 + Vue}

| Pattern | STRIDE | Standard Mitigation |
|---|---|---|
| 越权读取他人指标覆盖（`userId` 从请求参数取） | Information Disclosure | 只用 `SecurityUtils.getCurrentUserId()`；`getProfileByUserId(userId)` 与 `getRecentMetrics(userId)` 用同一 userId（现状即如此，`:211-221`） |
| 归因 JSON 反序列化注入（历史快照被篡改） | Tampering | `OBJECT_MAPPER.readValue(..., TypeReference<List<RiskContributorVO>>)` 强类型，失败返回 `List.of()`（`:156-166`），不抛 500、不渲染任意内容 |
| 前端把后端 `state` 原样渲染成 HTML | XSS | `{{ m.riskNote }}` 插值自动转义；本阶段不引入 `v-html` |
| 阈值拼写错误导致静默放行（风险分数偏低） | Tampering / Repudiation | 构造期 `IllegalStateException`；T-10 断言精确键集合 |
| 静默吞异常掩盖面板失败（`catch { coverage.value = [] }`，`Risk.vue:880-882`） | Information Disclosure（把「取不到」显示成「没有」） | **已知缺陷，本阶段不修**（UI-SPEC Deferred），但 UAT 必须实跑一次接口 500 并记录观察 |

---

## Sources

### Primary (HIGH confidence)
- `hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java`（全文 589 行，本会话 Read）—— 引擎三段评分、`halve`、`scaleTo`、`getLatestMetric`、快照读写、`getMetricCoverage`
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricCoverageAssembler.java`（全文 165 行）—— `RISK_CONSUMERS`、`assemble`、`classify`、`formatValue`、`formatRange`
- `hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java`（全文 548 行）—— 23 项定义、precision、参考范围、`applyRecommendations`
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricDefinition.java`（全文 56 行）—— 15 字段 record 与紧凑构造器不变式
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java`（全文 182 行）—— canonical 集合、别名组、`simplify`、lenient passthrough
- `hhs-backend/src/main/java/com/hhs/vo/RiskContributorVO.java`（全文 118 行）—— 四态常量与四个工厂
- `hhs-backend/src/main/java/com/hhs/vo/MetricCoverageVO.java`（全文 52 行）
- `hhs-frontend-v2/src/views/prevention/Risk.vue`（:70-180、:270-330、:355-470、:820-918、:1035-1450 逐段 Read）
- `hhs-frontend-v2/src/assets/styles/theme.css:23-25,141-143`（border token 实测）
- `hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java`（全文 631 行）—— 构造形态与 `stubMetrics` 限制
- `hhs-backend/src/test/java/com/hhs/service/domain/MetricCoverageAssemblerTest.java`（全文 171 行）—— 9 处 `assemble` 与「3 个 riskNote」断言
- `.planning/phases/16-risk-engine-inputs/16-CONTEXT.md`、`16-UI-SPEC.md`（1223 行）、`16-DISCUSSION-LOG.md`
- `.planning/ROADMAP.md:105-121`（Phase 16 目标与 6 条成功标准）
- 实测数据库 `hhs`（容器 `health-hack-system-mysql-1`，2026-11-18）：`risk_assessment` 54 行 / 18 批次 / 6 NULL / 16 带折半；`health_profile` 3 行 bmi `22.86`/`20.15`/`20.15`；`health_metric` 血脂四项 0 行、`systolicBloodPressure` 1 行
- 实测测试基线：`mvn -o -Dtest='MetricCoverageAssemblerTest,RiskAssessmentAssumptionsTest' test` → 9 + 26 全绿；`npx vitest run RiskBackfill.spec.ts` → 29 passed

### Secondary (MEDIUM confidence)
- `.planning/phases/15-risk-history-batch/15-02-SUMMARY.md:73-88`、`15-VERIFICATION.md:93,105` —— 「1 天间隔测 2 秒聚类」无牙测试的实证记录
- `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java`（全文）—— Phase 17 将收敛的判定源，本阶段仅参考其 `Band` 语义
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricRecommendedThresholdProvider.java:24-40` —— `@Component` + `@PostConstruct` + 构造注入注册表的既有形态

### Tertiary (LOW confidence)
- 心血管权重表（Q2.3）—— **无外部临床指南引用**，是基于「单项严重血压必须跨 MEDIUM」「上限 >100 使封顶可达」「与 UI-SPEC 三个示例分值对齐」三条内部约束的工程判断。标记为 A1，需用户确认。

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH —— 零新增依赖，全部版本读自 `pom.xml` / `package.json`
- Architecture: HIGH —— 每个主张都有 file:line 与逐字引文；三处前端缺陷可 grep 复现
- Pitfalls: HIGH —— 两条是已发生过的实证（`15-02-SUMMARY.md:85-88`；`MetricKeyNormalizer` 注释 `:519-528`），本阶段新增的 11 条陷阱全部配了可区分两种实现的夹具
- 心血管权重: MEDIUM/LOW —— 数值是工程判断，标记 A1
- 三处 Open Question 涉及 UI-SPEC 未授权的文案/别名改动：MEDIUM —— 均给出最小可行修法与理由，但需一次显式确认

**Research date:** 2026-11-18
**Valid until:** 2026-12-18（内部代码与数据，无外部 API 漂移风险；若 Phase 17 先动 `ThresholdRangeChecker`，Q1.4 的 classify 语义需重新对齐）
