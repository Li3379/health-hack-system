# Phase 13: 健康档案完善引导（非阻塞）- Research

**Researched:** 2026-11-09
**Domain:** Spring Boot 3.2 后端假设捕获与完整度权威计算；Vue 3 + Element Plus 2.14 非阻塞引导四表面
**Confidence:** HIGH（后端全部结论来自本会话逐行读取的第一手源码；前端结论来自文件全文读取 + 本会话实际跑通的 263 项测试基线）

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**1. 完整度口径与计算归属 (PRF-01, PRF-03)**

- **D-01 (后端权威加权评分 + 三等级):**
  - 完整度由**后端**计算并下发，前端不做独立计算（避免两套口径漂移）。当前 `ProfileScorer.java` 的硬编码扣分制（`-10/-15/-10/-15`，缺数据返回 70）需**重构为加权口径**。
  - **权重分层（待确认默认值）**：必需字段（`gender`、`birthDate`）= 60%；推荐字段（`heightCm`、`weightKg`、`bmi`）= 30%；可选字段（`bloodType`、`allergyHistory`、`familyHistory`、`lifestyleHabits`）= 10%。每组内平均分配。
  - **三等级**：`基础档案`（< 60%）、`标准档案`（60%–84%）、`完整档案`（≥ 85%）。前端文案形如「档案完整度 62% · 标准档案」。
  - **逐字段状态**：后端下发 `fields[]`，每项含 `key`、`label`、`importance`（`required`/`recommended`/`optional`）、`filled`(bool)、`reason`（"为何需要"文案）。

**2. 字段重要度与"不填也可保存"承诺 (PRF-02, PRF-03)**

- **D-02 (必需仅指"业务必需"，不是表单强制):**
  - **必需（required）**：`gender`、`birthDate` — 这两项缺失会让风险模型无法计算，但**仍不阻断任何既有功能**（缺失时用默认假设并披露，见 D-03）。
  - **推荐（recommended）**：`heightCm`、`weightKg`、`bmi` — 影响 BMI 与体重维度评分精度。
  - **可选（optional）**：`bloodType`、`allergyHistory`、`familyHistory`、`lifestyleHabits` — 影响生活方式维度与 AI 建议丰富度。
  - 每个字段下方必须有**一行说明 + 重要度徽标 + 统一承诺文案**「推荐与可选字段不填也可保存，不影响核心功能使用」。
  - **"为何需要"文案必须与实际消费方一致**：文案来源须对照后端真实消费点（`RiskAnalyzer` / `DimensionScoreCalculator` / `ProfileScorer`），不写泛泛而谈的理由。例如 `birthDate` → 「用于计算年龄，影响心血管风险评估」。

**3. 后端假设披露的数据形状 (PRF-05)**

- **D-03 (`profileCompleteness` + `assumptions[]` 双字段):**
  - 风险与评分响应 VO **新增两个字段**（附加，不破坏既有契约）：
    - `profileCompleteness: Integer`（0–100）
    - `assumptions: List<String>`（人类可读的中文假设条目，如「缺少身高，BMI 按 22.0 估算」「缺少出生日期，按 30 岁估算」）
  - 涉及 VO：`HealthScoreVO`（已有 `score/level/factors/dimensionScores/calculationMethod/...`）、`RiskAssessmentVO`。
  - **实现要求**：假设条目必须在**实际发生默认代入的位置**捕获（`DimensionScoreCalculator` / `RiskAnalyzer` / 风险服务），而不是事后推测。这样披露与行为天然一致。
  - 前端**直接渲染数组**，不做二次解释。

**4. 下游结果页徽标 (PRF-06)**

- **D-04 (徽标 + 跳转档案页 + 自动聚焦首个待填字段):**
  - 当 `profileCompleteness < 100` 且 `assumptions` 非空时，风险评估页（`prevention/Risk.vue`）与健康评分页展示徽标：**「基于不完整档案估算 · 完善档案」**。
  - 点击徽标 → `router.push('/prevention/profile')`（保留当前页不刷新），并在档案页**自动聚焦第一个未填的必需/推荐字段**（通过 query 参数或 store 传标记，档案页 `onMounted` 消费）。
  - 徽标样式复用现有 `el-tag`（`Risk.vue` 已在用 `el-tag` 展示风险等级），不新建组件。

**5. 仪表盘提醒的形态与频率 (PRF-07)**

- **D-05 (可关闭横幅 + 7 天静默 + 全站单实例):**
  - 形态：仪表盘内一条**可关闭横幅**（非弹窗、非遮罩），提供两个动作：「完善档案」与「稍后提醒」。
  - 频率上限：关闭后 **7 天内不再出现**，时间戳存 `localStorage`（键名遵循项目命名习惯，如 `hhs_profile_reminder_dismissed_at`）。
  - **全站单实例**：横幅只挂在仪表盘一处，其他页面不重复渲染，避免"刷新多个页面出现多条提示"。
  - 触发阈值：`profileCompleteness < 60`（即非「标准档案」以上）且未被静默。
  - 只填性别+出生日期的用户（完整度约 60%）**不触发**该横幅 —— 与成功标准 5 一致。

**6. 风险评估就地补填 (PRF-08)**

- **D-06 (就地提示 + 最小必需补填，不跳页不阻断):**
  - 创建风险评估时若档案缺失：先在**当前页**用一个 `el-alert` 列出「将按以下假设估算：…」，并提供**最小必需字段的内联补填表单**（性别 + 出生日期；身高体重可选补）。
  - 用户可**直接继续创建**（跳过补填），也可填完再创建。**绝不硬性阻断**流程。
  - 补填成功后立即重算并刷新假设列表。
  - 复用 Phase 11 的 `FormErrorSummary` / `useFormServerErrors` 机制（D-04 of Phase 11：全页表单与弹窗表单共用同一套错误呈现）。

### Claude's Discretion

- 完整度权重具体数值（D-01 的 60/30/10 为待确认默认值，planner 可按医学合理性微调，但分层结构不可变）
- 三等级的精确阈值（60/85 为待确认默认值）
- `assumptions[]` 的具体文案措辞
- 档案页字段说明的具体文案（但必须对照真实消费方）
- 横幅的具体视觉样式（复用现有 token 与 `el-alert` 变体）
- "自动聚焦"的实现方式（query 参数 vs pinia 标记）

### Deferred Ideas (OUT OF SCOPE)

- **个性化基线（PERS-01）**：结合既往病史自定义个人目标值 —— 已在 v1.4+ Future Requirements，本阶段不做
- **档案字段扩展**（如腰围、静息心率、吸烟史结构化）—— 属新增档案字段，独立阶段
- **AI 建议的档案感知深度化**（让 AI 顾问主动追问缺失档案）—— 新能力，独立阶段
- **完整度历史趋势图**（档案完善过程可视化）—— 新能力，独立阶段
- **档案数据的社交/家庭共享** —— 超出本阶段范围

### 与 UI-SPEC 的两处已裁决冲突（research 采信 UI-SPEC）

1. **D-04 说「徽标复用 `el-tag`」**，但 `13-UI-SPEC.md` §Design System 显式覆盖：`theme.css:542-549` 用 `!important` 把 `.el-tag` 锁成 `500 11px/1.4 var(--font-mono)` + 全圆角 + accent 文字色（浅色 3.74:1 不达 AA），因此改为自绘 `button.hhs-estimate-badge`。**采信 UI-SPEC**（approved 状态，7/7 PASS）。
2. **D-05 说「可关闭横幅」可用 `el-alert`**，但 `13-UI-SPEC.md` §Component Contract 3 显式覆盖：EP 的默认插槽渲染进 `<p>`，双按钮会被浏览器提升出去，改为自绘 `div.profile-banner`。**采信 UI-SPEC**。

</user_constraints>

## Summary

Phase 13 的表面工作量（4 个页面、3 个新组件、1 个 store）远小于其**后端正确性工作量**。研究得出五条会直接改变计划写法的硬结论：

**（一）完整度不能只挂在 `HealthScoreVO` 上 —— 必须新增独立端点，否则最需要引导的新用户拿不到完整度。** `HealthScoreServiceImpl.calculateScore` 在 `!metricValidator.hasAnyData(userId)` 时**提前返回** `createDefaultScore()`（`score=0, level="NO_DATA"`），根本不进入 `ScoreCalculator`：

```java
// hhs-backend/src/main/java/com/hhs/service/impl/HealthScoreServiceImpl.java:44-47
if (!metricValidator.hasAnyData(userId)) {
    log.info("User {} has no health data, returning default score", userId);
    return createDefaultScore();
}
```

也就是说「新用户 = 无指标 = 无评分」与「新用户 = 无档案 = 完整度 0%」是同一批人，而 D-03 想加的 `profileCompleteness` 恰好在这条路径上**永远为 null**。仪表盘横幅（`score < 60` 触发）、档案页头部、风险页徽标都需要 `fields[]`（`missing.length` / `firstMissingKey` / 缺失字段名枚举），而 D-03 只给 `Integer` + `List<String>`。**结论：新增 `GET /api/prevention/profile/completeness` 返回 `{score, level, filledCount, totalCount, fields[]}`，作为三个表面的唯一数据源；`HealthScoreVO`/`RiskAssessmentVO` 的 `profileCompleteness`+`assumptions[]` 只服务下游徽标（D-03 的原文要求）。**

**（二）假设捕获用「显式传入的收集器 + 保留旧签名的重载」，不要用 request-scoped bean。** 三个候选方案中，(a) request-scoped `AssumptionCollector` 与项目既有纪律直接冲突 —— `CLAUDE.md` 明确写着 `service/domain/` 是「Stateless domain logic … No Spring annotations, pure logic」，而现有 `ProfileScorer`/`DimensionScoreCalculator` 等虽然挂了 `@Component`，其单测（`WellnessScorerTest` 15 个、`ScreeningScorerTest` 4 个）全部是**纯 Mockito 无 Spring 上下文**，且 `calculateScore` 会被 `HealthReportServiceImpl` 在非请求链路调用，request scope 会直接抛异常。推荐方案 (c)：新增可变 POJO `AssumptionCollector`，各 scorer 增加 `calculate(userId, collector)` 重载，原 `calculate(userId)` 委托到 `calculate(userId, AssumptionCollector.noop())`。**零测试破坏、零 Spring 魔法、签名 churn 只发生在 `ScoreCalculator` 内部一处。**

**（三）`birthDate` 在 DTO 层是 `@NotNull` —— 「缺少出生日期，年龄按 35 岁估算」这条最显眼的假设对新建/编辑路径不可达。** `HealthProfileRequest.java:16-17` 是 `@NotNull(message = "出生日期不能为空")`，而 `gender` 只有 `@Pattern`（允许空串）。因此该假设只可能由两类数据触发：① 历史遗留 `birth_date IS NULL` 行；② `profile == null` 分支（固定分 30/25/25）。planner 不该把主要文案预算押在不可达路径上，而应把重点放在 `profile == null` 与「缺 gender」这两条真实路径。

**（四）Redis 里会存在**缺少新字段的旧 VO**，但窗口只有 5 分钟，用改前缀的方式最干净。** `RedisConfig` 启用了 `activateDefaultTyping(NON_FINAL)` + `LaissezFaireSubTypeValidator`，`HealthScoreServiceImpl` 的缓存 key 是 `health:score:` + userId、TTL 5 分钟。部署瞬间的旧条目反序列化进新 `HealthScoreVO` 会得到 `profileCompleteness == null`。UI-SPEC Contract 4 已规定「`null` 不渲染徽标」，但建议额外把 `CACHE_KEY_PREFIX` 改成 `health:score:v2:`（对齐 Phase 12 的 `VERSION_TAG` 递增先例），让旧条目自然过期而非依赖下游判空。

**（五）两处「静默默认值」所在的 `case` 分支其实是死代码 —— 不要基于它们写假设文案。** `DimensionScoreCalculator.java:55` 与 `MetricsScorer.java:60` 都用 `case "glucose"`，但 `HealthMetricServiceImpl` 在写库前统一规范化：`request.setMetricKey(canonicalKey);` + `e.setMetricKey(canonicalKey);`（`HealthMetricServiceImpl.java:80,91`），`MetricKeyNormalizer` 把 `glucose` 归到 `bloodGlucose`（`MetricKeyNormalizer.java:58`），v1.1 迁移脚本也把存量 `glucose` 改写成 `bloodGlucose`。**所以血糖惩罚分支对规范化后的数据永不命中，`metabolic` 维度恒为 100。** 同理 `RiskAssessmentServiceImpl.java:122` 用中文字面量 `"血糖"` 查 `health_metric.metric_key`，也永不命中。这两处是既有缺陷，**本阶段不修**（超范围），但 PRF-05 的诚实披露**绝不能**声称"用了血糖数据"或"缺血糖按 X 估算"。

**Primary recommendation:** 先后端（`ProfileCompletenessCalculator` 权威加权口径 → 独立 completeness 端点 → `AssumptionCollector` 注入 `ScoreCalculator`/`RiskRuleEngine` → 两个 VO 加字段 + 缓存前缀 bump），再前端（`useProfileCompletenessStore` 单源 → 3 个新组件 → 4 个表面接线 → 静默与聚焦）。所有 scorer 改造走「新增重载 + 旧签名委托」，禁止改既有 `calculate(Long)` 的签名。

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PRF-01 | 档案页与仪表盘持续展示完整度分数与等级，逐字段标注已填/未填 | 后端 `ProfileCompletenessCalculator`（加权 60/30/10，9 字段含派生 `bmi`）→ 独立端点 `GET /api/prevention/profile/completeness`；前端 `useProfileCompletenessStore` 单源 + `ProfileCompleteness.vue`（`variant: full/compact`） |
| PRF-02 | 每字段展示"为何需要"说明，且与实际消费方一致 | 消费点已逐条核实：`birthDate`→`RiskAssessmentServiceImpl:118-119` 年龄、`heightCm/weightKg/bmi`→`RiskAssessmentServiceImpl:120` BMI 默认 22、`familyHistory`→`:121,124` 糖尿病 +25、`lifestyleHabits`→`WellnessScorer`/AI prompt；文案由后端 `fields[].reason` 下发，前端不自撰 |
| PRF-03 | 字段标注必需/推荐/可选，并说明推荐与可选不填也可保存 | 后端 `fields[].importance` 三档；前端 `.hhs-imp-badge` 三档 + 单条承诺句（UI-SPEC Contract 2 定为**只出现一次**）；`gender`/`birthDate` 的 `required: true` **保留** |
| PRF-04 | 档案缺失时展示点名下游后果的影响横幅 | 影响句四档（缺必需/缺推荐/仅缺可选/完整）由后端 `fields[]` 派生；仪表盘 `ProfileReminderBanner`（UI-SPEC Contract 3） |
| PRF-05 | 后端在评分与风险响应中披露实际采用的默认假设 | `AssumptionCollector` 显式传入 `ScoreCalculator` / `RiskRuleEngine`；`HealthScoreVO` 加 `profileCompleteness`+`assumptions`；`RiskAssessmentVO` record 加同二字段（唯一构造点 `:105`） |
| PRF-06 | 结果页展示「基于不完整档案估算 · 完善档案」徽标并可跳转 | `EstimateBadge.vue`（`button.hhs-estimate-badge`）；渲染条件 `profileCompleteness != null && < 100`（null 绝不渲染，防旧缓存/旧后端） |
| PRF-07 | 仪表盘可关闭提示 + 完善入口 + 频率上限 | `localStorage.hhs_profile_reminder_dismissed_at`（ISO 串）+ 7 天窗口；`< 60` 触发；全站仅 `.dashboard` 一处 |
| PRF-08 | 创建风险评估时就地展示估算内容并允许补填，不阻断 | `Risk.vue` 就地 `section.risk-estimate` 面板；判定顺序「先假设后分数」（Contract 5 第 1 序优先）；补填复用 `FormErrorSummary`+`useFormServerErrors` |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|-----------|-------------|----------------|-----------|
| 完整度分数/等级/逐字段状态计算 | **API / Backend**（`ProfileCompletenessCalculator`） | — | D-01 锁定后端权威；前端不得二次计算，否则两套口径漂移 |
| 字段重要度与"为何需要"文案 | **API / Backend**（随 `fields[]` 下发） | Client（仅渲染） | 文案必须对照真实消费方（`RiskAssessmentServiceImpl`/`WellnessScorer`），只有后端知道分支是否可达 |
| 默认假设捕获与披露 | **API / Backend**（在默认代入点捕获） | — | D-03 硬要求「在实际发生默认代入的位置捕获，而不是事后推测」 |
| 完整度数据缓存与共享 | **Client**（Pinia store 单源） | API（端点） | 三处表面需同一份 `fields[]`；store 避免 3 次重复请求，与 `useMetricMetaStore` 同构 |
| 7 天静默时间戳 | **Client**（`localStorage`） | — | 纯本地偏好，无需服务端持久化（D-05 明确 localStorage） |
| 横幅/徽标/补填面板渲染 | **Client** | — | 纯展示与交互，宽度 `100%` 文档流内布局 |
| 补填后的档案写入 | **API / Backend**（`PUT /api/prevention/health-profile/{id}`） | Client（表单） | 复用既有 CRUD，无新写入路径 |

## Standard Stack

**本阶段零新增依赖。** 后端不引入任何新库；前端不引入任何新库。

### Core（全部为既有，已核验版本）

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.2.0 `[VERIFIED: hhs-backend/pom.xml:9]` | 后端框架 | 项目既有 |
| MyBatis-Plus | 3.5.5 `[VERIFIED: hhs-backend/pom.xml:22]` | 数据访问 | 项目既有 |
| spring-boot-starter-data-redis | 随 Boot 3.2.0 `[VERIFIED: hhs-backend/pom.xml:48]` | 评分缓存 | 既有缓存层，本阶段需 bump key 前缀 |
| element-plus | 2.14.0 `[VERIFIED: node_modules 实际解析]` | 全部 UI 组件 | UI-SPEC 已枚举 91 个可用组件 |
| @element-plus/icons-vue | 2.3.2 `[VERIFIED: node_modules 实际解析]` | `InfoFilled` 等图标 | UI-SPEC §Design System |
| vue | 3.5.34 `[VERIFIED: node_modules 实际解析]` | 前端框架 | 注意：`useId()` 需 3.5+，`FormErrorSummary.vue` 已改用模块级计数器规避 |
| pinia | ^2.1.7 `[VERIFIED: hhs-frontend-v2/package.json]` | 完整度 store | 与 `useMetricMetaStore` 同构 |
| vitest | 4.1.7 `[VERIFIED: node_modules 实际解析]` | 前端单测 | 既有 263 项基线 |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 + Mockito | 随 `spring-boot-starter-test` | 后端单测 | 全部新 domain 测试（纯 Mockito，无 Spring 上下文） |
| @vue/test-utils | 随 devDependencies | 组件测试 | 新组件 spec；需 stub `router-link` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 新增独立 completeness 端点 | 只扩展 `HealthScoreVO` | **不可行**：`NO_DATA` 早返回使新用户永远拿不到完整度（见 Summary 一） |
| 显式 `AssumptionCollector` 参数 | request-scoped bean | 与 `service/domain/` 纯逻辑纪律冲突，非请求链路（`HealthReportServiceImpl`）会抛异常 |
| 显式 `AssumptionCollector` 参数 | 每个 scorer 返回 `value+assumptions` 富类型 | 改动 `WellnessScorer`/`ScreeningScorer` 全部签名 → 破坏 19 个既有单测 |
| 改 `CACHE_KEY_PREFIX` 为 `v2` | 依赖前端判 `null` | 前者让旧条目自然过期，不依赖每个渲染点都记得判空；两者可并存，但前者是防御底线 |

**Installation:**

```bash
# 无 —— 本阶段不安装任何新依赖
```

**Version verification:** 无需验证新包；`element-plus@2.14.0`、`@element-plus/icons-vue@2.3.2`、`vue@3.5.34`、`vitest@4.1.7` 均从 `hhs-frontend-v2/node_modules/*/package.json` 实际读取确认。

## Package Legitimacy Audit

**本阶段不安装任何外部包。** 无需运行 `package-legitimacy check`。

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| （无） | — | — | — | — | — | N/A |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

> 约束提醒：`REQUIREMENTS.md` §Out of Scope 明确禁止引入 `vue-i18n` / `VeeValidate` / `driver.js` / 第二套 UI 库，以及**直接 `import @vueuse/core`**（它只作为 Element Plus 的传递依赖存在）。本阶段设计不需要其中任何一个。

## Architecture Patterns

### System Architecture Diagram

```
┌────────────────────────────── 数据产生侧（后端） ──────────────────────────────┐
│                                                                              │
│  GET /api/prevention/profile/completeness        POST /api/prevention/         │
│              │                                    risk-assessment             │
│              ▼                                          │                      │
│  ProfileCompletenessCalculator                          ▼                      │
│  (加权 60/30/10, 9 字段, 三等级)              RiskAssessmentServiceImpl         │
│              │                                   ├─ profile == null?           │
│              │                                   │    └─ 固定 30/25/25         │
│              │                                   └─ RiskRuleEngine.evaluate    │
│              │                                        ├─ birthDate==null → 35  │
│              │                                        ├─ bmi==null → 22.0      │
│              │                                        ├─ familyHistory 无"糖尿病"│
│              │                                        └─ collector.add(...)    │
│              │                                          ↓                     │
│              │                                   RiskAssessmentVO              │
│              │                                   (+profileCompleteness         │
│              │                                    +assumptions)                │
│              ▼                                                                 │
│  GET /api/health/score → HealthScoreServiceImpl                                │
│      ├─ hasAnyData? ── 否 ──► createDefaultScore()  [无 completeness！]        │
│      └─ 是 ─► Redis(5min) ─► ScoreCalculator                                  │
│                                 ├─ ProfileScorer        → collector            │
│                                 ├─ MetricsScorer        → collector            │
│                                 ├─ RiskScorer           → collector            │
│                                 ├─ ScreeningScorer      → collector            │
│                                 ├─ WellnessScorer       → collector            │
│                                 └─ DimensionScoreCalculator → collector        │
│                                        ↓                                       │
│                                 HealthScoreVO (+profileCompleteness             │
│                                               +assumptions)                    │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │  HTTP (Result<T> 信封)
                                    ▼
┌────────────────────────────── 呈现侧（前端） ────────────────────────────────┐
│                                                                             │
│  useProfileCompletenessStore  ◄── 唯一 fetcher（completeness 端点）          │
│    state: {score, level, filledCount, totalCount, fields[]}                  │
│    state: dismissedAt (localStorage, 7 天)                                   │
│         │                    │                     │                        │
│         ▼                    ▼                     ▼                        │
│  ProfileCompleteness.vue  ProfileReminderBanner  EstimateBadge.vue          │
│   (full: 档案页)          (dashboard 单实例)      (Risk.vue / Score.vue)     │
│   (compact: 仪表盘)        触发: score<60 且未静默   触发: completeness       │
│         │                    │                     <100 且非 null            │
│         ▼                    ▼                     │                        │
│  prevention/Profile.vue   dashboard/Index.vue      ▼                        │
│  + 9× field-hint          + 完善/稍后/×        router.push(                  │
│  + 承诺句                    │                 '/prevention/profile'          │
│  + focus 消费 ◄──────────────┘                 ?focus=<firstMissingKey>)     │
│         │                                                                   │
│         ▼                                                                   │
│  prevention/Risk.vue: section.risk-estimate（就地补填，不跳页）               │
└─────────────────────────────────────────────────────────────────────────────┘
```

**主用例可追踪路径：** 新用户登录 → 仪表盘 → store 拉 completeness（0% / 基础档案 / 9 项全 ○）→ `score < 60` 且未静默 → 横幅出现 → 点「完善档案」→ `/prevention/profile?focus=gender` → 档案页 `onMounted` 消费 query、给 `gender` 的 `el-form-item` 加 `is-focus-target` 并 `focusField('gender')` → `router.replace({query:{}})` → 填完保存 → 重拉 completeness → 横幅消失。

### Recommended Project Structure（仅列出本阶段新增/改动）

```
hhs-backend/src/main/java/com/hhs/
├── service/domain/
│   ├── ProfileCompletenessCalculator.java      # 新增：加权完整度 + 三等级 + fields[]
│   ├── ProfileFieldCatalog.java                # 新增：9 字段元数据（key/label/importance/reason）
│   ├── AssumptionCollector.java                # 新增：可变 POJO，LinkedHashSet 去重
│   ├── ProfileScorer.java                      # 改：委托到 ProfileCompletenessCalculator
│   ├── DimensionScoreCalculator.java           # 改：+ calculate(userId, collector) 重载
│   ├── MetricsScorer.java                      # 改：+ 重载（如需披露指标缺失）
│   ├── RiskScorer.java                         # 改：+ 重载
│   ├── WellnessScorer.java                     # 改：+ 重载
│   └── ScreeningScorer.java                    # 改：+ 重载
├── service/impl/
│   ├── HealthScoreServiceImpl.java             # 改：CACHE_KEY_PREFIX → health:score:v2:
│   ├── HealthProfileServiceImpl.java           # 改：+ getCompleteness(userId)
│   └── RiskAssessmentServiceImpl.java          # 改：RiskRuleEngine 接 collector；toVO 补两字段
├── vo/
│   ├── ProfileCompletenessVO.java              # 新增（record：score/level/filledCount/totalCount/fields）
│   ├── ProfileFieldStatusVO.java               # 新增（record：key/label/importance/filled/reason/assumption）
│   ├── HealthScoreVO.java                      # 改：+ profileCompleteness + assumptions
│   └── RiskAssessmentVO.java                   # 改：record 加两组件（唯一构造点 :105）
└── controller/PreventionController.java        # 改：+ GET /health-profile/completeness

hhs-frontend-v2/src/
├── stores/profileCompleteness.ts               # 新增
├── components/common/
│   ├── ProfileCompleteness.vue                 # 新增（variant: full|compact）
│   ├── ProfileReminderBanner.vue               # 新增
│   └── EstimateBadge.vue                       # 新增
├── utils/profileFields.ts                      # 新增：离线兜底静态目录（9 字段）
├── api/prevention.ts                           # 改：+ getCompleteness()
├── types/api.ts                                # 改：+ ProfileCompletenessVO 等 3 个类型
├── assets/styles/theme.css                     # 改：+ 4 个共享类
├── views/prevention/Profile.vue                # 改：完整度头部 + 9 条 field-hint + 承诺句 + focus 消费
├── views/prevention/Risk.vue                   # 改：徽标 + 就地补填面板
├── views/dashboard/Index.vue                   # 改：横幅 + compact 完整度行
└── views/health/Score.vue                      # 改：徽标
```

### Pattern 1: 假设收集器（显式传入，非 Spring 作用域）

**What:** 一个普通可变 POJO，沿调用链显式传入；在真正代入默认值的那一行调用 `collector.add(...)`。
**When to use:** 任何「必须与行为同源」的披露需求。
**Example:**

```java
// hhs-backend/src/main/java/com/hhs/service/domain/AssumptionCollector.java（新增）
public final class AssumptionCollector {
    private final Set<String> items = new LinkedHashSet<>();

    /** 空实现：既有调用方无需感知，行为完全不变。 */
    public static AssumptionCollector noop() {
        return new AssumptionCollector();
    }

    public void add(String humanReadable) {
        if (humanReadable != null && !humanReadable.isBlank()) {
            items.add(humanReadable);
        }
    }

    public List<String> toList() {
        return List.copyOf(items);
    }
}
```

```java
// hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java（改）
// 旧签名保留（唯一调用点 :51 之外无外部调用）
static List<RiskAssessment> evaluate(Long userId, HealthProfile profile,
                                     List<HealthMetric> recentMetrics,
                                     AssumptionCollector collector) {
    ...
    if (profile != null) {
        int age;
        if (profile.getBirthDate() != null) {
            age = LocalDate.now().getYear() - profile.getBirthDate().getYear();
        } else {
            age = 35;
            collector.add("缺少出生日期，年龄按 35 岁估算");
        }
        BigDecimal bmi;
        if (profile.getBmi() != null) {
            bmi = profile.getBmi();
        } else {
            bmi = BigDecimal.valueOf(22);
            collector.add("缺少 BMI，按 22.0 估算");
        }
        if (!hasFamilyHistory(profile.getFamilyHistory(), "糖尿病")) {
            collector.add("缺少家族病史，糖尿病风险评估按「无家族史」计算");
        }
        ...
    } else {
        collector.add("未填写健康档案，风险得分按通用参考值估算");
        ...
    }
}
```

> **注意（D-03 的硬约束）：** `collector.add` 必须写在**赋值默认值的同一分支内**，不能写在方法末尾统一判断「哪些字段为 null」。后者就是 D-03 明令禁止的"事后推测"，一旦分支逻辑变化就会立刻漂移。

### Pattern 2: 加权完整度（单一口径 + 派生字段）

**What:** 一个纯函数式计算器，输入 `HealthProfile`（可 null），输出 `score/level/filledCount/totalCount/fields[]`。
**When to use:** 所有需要完整度的读路径（独立端点、`ProfileScorer`、风险服务的 `profileCompleteness`）。
**Example:**

```java
// 权重结构（D-01，分层不可变，具体数值可微调）
// required   : gender, birthDate                          → 合计 60
// recommended: heightCm, weightKg, bmi                    → 合计 30
// optional   : bloodType, allergyHistory,
//              familyHistory, lifestyleHabits              → 合计 10
// 组内平均分配：required 30/项、recommended 10/项、optional 2.5/项
// 用 int 分（×2 放大避免 2.5 的小数）或 BigDecimal，二选一后在测试里锁定
```

> **`bmi` 必须出现在 `fields[]` 里**（9 项，不是 8）。依据：`HealthProfile` 实体确有 9 个业务字段（`gender`/`birthDate`/`heightCm`/`weightKg`/`bmi`/`bloodType`/`allergyHistory`/`familyHistory`/`lifestyleHabits`，`[VERIFIED: hhs-backend/src/main/java/com/hhs/entity/HealthProfile.java:23-31]`，逐字引用见下），D-01 的权重分组是 2+3+4=9，UI-SPEC Copywriting Contract 也给了 9 条字段说明（含「字段说明 · BMI」）。`bmi` 为**派生只读**：`filled` 由后端判 `profile.getBmi() != null`，前端不自行推算（UI-SPEC §partial 明确要求）。

```java
// 逐字引用（HealthProfile.java:23-31）
//     private String gender;
//     private LocalDate birthDate;
//     private BigDecimal heightCm;
//     private BigDecimal weightKg;
//     private BigDecimal bmi;
//     private String bloodType;
//     private String allergyHistory;
//     private String familyHistory;
//     private String lifestyleHabits;
```

### Pattern 3: 重载保留旧签名（零测试破坏的改造法）

**What:** 新逻辑进新重载，旧签名变成一行委托。
**When to use:** 改造 `service/domain/` 下任何被既有单测直接调用的组件。

```java
// 旧签名保持逐字不变（19 个既有测试继续通过）
public int calculate(Long userId) {
    return calculate(userId, AssumptionCollector.noop());
}

public int calculate(Long userId, AssumptionCollector collector) {
    // 真实逻辑
}
```

### Anti-Patterns to Avoid

- **在 VO 上"事后推断"假设：** 例如拿到 `profileCompleteness < 100` 后由前端拼「缺少身高…」。这违反 D-03，且一旦后端分支改动，文案立刻变成谎言。
- **request-scoped / ThreadLocal 收集器：** 与 `service/domain/` 纯逻辑纪律冲突；`HealthReportServiceImpl` 在非请求链路调用 `calculateScore`，会抛 `IllegalStateException`。
- **把完整度塞进 `HealthScoreVO` 就完事：** `NO_DATA` 早返回让新用户拿不到（见 Summary 一）。
- **改 `ProfileScorer.calculate` 的返回类型：** 唯一调用点 `ScoreCalculator:54` 是 `int profileScore = profileScorer.calculate(userId);`，改签名是可控的，但会让所有未来调用点失去「只要分数」的简洁性；用**新增** `calculateCompleteness` 更清晰。
- **前端自行计算完整度做兜底分数：** UI-SPEC Contract 1 末段明确「后端返回的 `score`/`level` 优先，前端不做独立计算」。离线兜底只允许合成**静态目录**（`fields[]` 全 `filled:false`、`score:0`、`level:'基础档案'`），不得按权重算分。
- **把「缺少出生日期」当作主文案路径：** 见 Summary 三，`@NotNull` 使其对新建/编辑不可达。

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 字段级错误汇总 + 内联 | 新的错误呈现组件 | `FormErrorSummary.vue` + `useFormServerErrors.ts` | Phase 11 D-01/D-04 已锁定「全页表单与弹窗共用同一套」；`apply()` 只处理 `kind==='validation'`，`focusField()` 已做 `CSS.escape` + `scrollIntoView({block:'center'})`（正是 D-04 自动聚焦要的语义） |
| 错误 toast | 裸 `ElMessage.error` | `showError(err, 'profile.loadFailed')` | `src/utils/showError.ts` 是全站唯一 toast 出口，已实现 2000ms 去重、最多 3 条、validation 零 toast |
| 中文文案字典 | 组件内字面量 | `ERROR_MESSAGES` / `FIELD_LABELS`（`utils/errorMessages.ts`） | `FIELD_LABELS` 已含全部 9 个档案字段中文名（`birthDate`/`gender`/`heightCm`/`weightKg`/`bmi`/`lifestyleHabits`/`familyHistory`/`allergyHistory` + `notes`） |
| 元数据单源 store 模式 | 每页各自 fetch | 照抄 `useMetricMetaStore`（`src/stores/metricMeta.ts`）的 `ref` + `computed` + 静默降级结构 | 已解决离线兜底、重复请求抑制（`if (loading.value) return`）、永不 throw |
| 分区分段进度条 | `ElProgress` | 自绘 `.hhs-completeness-meter`（UI-SPEC Contract 1） | `ElProgress` 无法表达逐字段分段，且 `.el-progress__text` 走 EP 变量不在本站 token 表 |
| 徽标 chip | `ElTag` | 自绘 `.hhs-tier-pill` / `.hhs-imp-badge` / `.hhs-estimate-badge` | `theme.css:542-549` 用 `!important` 锁死 `.el-tag` 为 `500 11px/1.4 mono` + 全圆角 + accent 文字（浅色 3.74:1 不达 AA） |
| 路由跳转参数传递 | 新建一次性 store 状态位 | `router.push({path, query:{focus}})` + `router.replace({query:{}})` | Contract 7 已裁定 query 参数：可分享、刷新仍在、无需清理生命周期 |

## Common Pitfalls

### Pitfall 1: `NO_DATA` 早返回让最需要引导的用户拿不到完整度

**What goes wrong:** 只按 D-03 给 `HealthScoreVO` 加 `profileCompleteness`，仪表盘从 `/api/health/score` 读。新用户（无任何指标）拿到 `{score:0, level:'NO_DATA', profileCompleteness:null}`，横幅永不出现。
**Why it happens:** `HealthScoreServiceImpl:44-47` 在计算前短路。
**How to avoid:** 新增独立 completeness 端点，横幅与档案页只读 store（store 只调该端点）。
**Warning signs:** 用新账号（无指标、无档案）登录仪表盘，横幅不出现。

### Pitfall 2: `ProfileErrorSample.spec.ts` 会因 `useRoute()` 与新增 API 调用而失败

**What goes wrong:** `Profile.vue` 当前**没有** `useRoute`/`useRouter`（本会话 grep 确认为空）。若直接加 `const route = useRoute()`，该 spec 以 `mount(Profile, { global: { stubs } })` 挂载、未安装 router → `inject(routeLocationKey)` 返回 `undefined` → `route.query` 抛 `TypeError`。若组件再调用 `preventionApi.getCompleteness()`，而 spec 的 `vi.mock('@/api/prevention')` 只声明了 `getProfile`/`createProfile`/`updateProfile`，会得到 `is not a function`。
**Why it happens:** `useRoute` 的实现是 `inject(routeLocationKey)`（`vue-router/dist/vue-router.mjs:1552-1554`），不是全局单例。
**How to avoid:** 在 `ProfileErrorSample.spec.ts` 中补 `vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ replace: vi.fn(), push: vi.fn() }) }))`，并在 `vi.mock('@/api/prevention')` 中补 `getCompleteness: vi.fn()`；新写的 dashboard/score/risk spec 需 stub `router-link`。
**Warning signs:** `TypeError: Cannot read properties of undefined (reading 'query')`。

### Pitfall 3: 新增任何 `ERROR_MESSAGES` 键会让字典测试转红

**What goes wrong:** `src/__tests__/utils/errorMessages.spec.ts:33` 断言 `expect(Object.keys(ERROR_MESSAGES)).toHaveLength(DICTIONARY_KEYS.length)`，`DICTIONARY_KEYS` 是硬编码的 18 项清单。
**Why it happens:** 该断言是**精确相等**，不是"至少包含"。
**How to avoid:** 若本阶段确实要加键（建议**不加** —— UI-SPEC 明确 `profile.loadFailed` 已存在且「不新增」），必须同时更新 spec 的 `DICTIONARY_KEYS` 数组与 30 字上限断言。
**Warning signs:** `expected 19 to have length 18`。

### Pitfall 4: 修改 `ProfileScorer.calculate` 的口径会改变全站健康评分

**What goes wrong:** 当前空档案返回 70（"中性"），加权口径下空档案返回 0。`ScoreCalculator:60` 的贡献是 `totalScore -= (int)((100 - profileScore) * 0.25)`，即档案分下降 70 分会让总分下降 17.5 分。既有用户（有指标、无档案）的评分会**突然下降**，并写入 `health_score_history`，历史趋势出现断点。
**Why it happens:** D-01 锁定重构为加权口径，但没说健康评分贡献必须等于完整度。
**How to avoid:** 二选一并显式写进 PLAN（见 Open Question 1）：(a) 贡献 = 完整度（口径最诚实，接受评分变化）；(b) 新增独立 `ProfileCompletenessCalculator`，`ProfileScorer.calculate` 保持 70 中性语义不变，完整度只用于展示与披露。**research 推荐 (b)**，因为它同时满足「完整度加权权威」与「不产生评分回归」。
**Warning signs:** 已有用户反馈"我的健康评分掉了十几分"。

### Pitfall 5: `bmi` 会残留陈旧值，导致 `filled` 判定失真

**What goes wrong:** `HealthProfileServiceImpl.toEntity` 只在身高体重**都非空且 > 0** 时才 `e.setBmi(...)`（`:94-98`）；用户清空身高或体重时，`e.bmi` 为 `null`，MyBatis-Plus 默认 `NOT_NULL` 更新策略会把该列从 `UPDATE` 语句中省略 → 数据库保留旧 BMI。于是 `fields[]` 里 `bmi.filled == true`，但实际身高体重已空。
**Why it happens:** 缺 `e.setBmi(null)` 的显式清空 + MP 默认字段策略（项目未配置 `update-strategy`，本会话 grep 确认为空）。
**How to avoid:** 本阶段**不修**（超范围）；但在 `ProfileCompletenessCalculator` 里把 `bmi.filled` 定义为 `heightCm != null && weightKg != null && bmi != null` 而非仅 `bmi != null`，即可免疫陈旧值。
**Warning signs:** 清空身高后刷新，完整度不下降。

### Pitfall 6: `case "glucose"` 是死代码，基于它写假设文案会说假话

**What goes wrong:** `DimensionScoreCalculator.java:55` 的 `case "glucose" ->` 与 `MetricsScorer.java:60` 的 `case "glucose" ->` 对规范化数据永不命中（写库前已归一为 `bloodGlucose`）；`RiskAssessmentServiceImpl.java:122` 的 `getLatestMetric(recentMetrics, "血糖")` 同样永不命中。若据此写「缺血糖，代谢评分按参考值估算」，披露内容与真实行为不符。
**Why it happens:** v1.1 Key 归一化把 `glucose` → `bloodGlucose`，但这两个 switch 未同步。
**How to avoid:** 本阶段**不修**；PRF-05 的假设条目**只**覆盖三条真实可达路径：`profile == null`（通用参考值）、`birthDate == null`（35 岁）、`bmi == null`（22.0）、`familyHistory` 无「糖尿病」（按无家族史）。不要声称使用了血糖/血压指标。
**Warning signs:** 假设列表出现「缺少血糖…」但该字段在档案里根本不存在。

### Pitfall 7: 风险页徽标的数据来自 `listByUserId`，而假设只在 `createAssessment` 时产生

**What goes wrong:** `RiskAssessmentVO` 的 `assumptions[]` 在 `createAssessment` 里捕获，但页面首屏走 `GET /api/prevention/risk-assessment` → `listByUserId` → `toVO(r)`，从数据库行重建，**没有任何地方存过假设**。
**Why it happens:** `risk_assessment` 表无 `assumptions`/`completeness` 列（`schema.sql:359-371` 只有 `id/user_id/profile_id/disease_name/risk_level/risk_score/suggestion/create_time`）。
**How to avoid:** 二选一（见 Open Question 2）：(a) `toVO` 时用**当前**档案重算（无需改表，语义为"基于当前档案"，需在 UI 文案上避免声称是"本次评估采用的假设"）；(b) 加两列 + 幂等 ALTER 脚本（保真，但 `schema.sql` 只在空卷首次启动执行，见 Pitfall 8）。research 推荐 (a)，并把详情对话框的标题从「本次评估采用的假设」调整为与语义相符的措辞，或仅在刚创建后展示捕获到的假设。
**Warning signs:** 刷新风险页后徽标消失。

### Pitfall 8: `schema.sql` 只在 MySQL 空数据卷时执行 —— 加列需要独立幂等脚本

**What goes wrong:** `docker-compose.yml:104` 把 `schema.sql` 挂到 `/docker-entrypoint-initdb.d/`，该目录**仅在 `mysql_data` 卷为空时**执行。已有部署加列不会生效。
**Why it happens:** 项目已明确「延迟引入 Flyway」（REQUIREMENTS §Out of Scope）。
**How to avoid:** 若选 (b) 加列，必须仿照 `schema.sql:663-678` 的 `INFORMATION_SCHEMA.COLUMNS` + `PREPARE/EXECUTE` 幂等 ALTER 模式，写成独立脚本并纳入 UAT 手动步骤（对齐 Phase 12 FIX-04 的脚本先例）。
**Warning signs:** 本地 `docker compose up` 后 `SHOW COLUMNS` 看不到新列（因为卷非空）。

### Pitfall 9: UI-SPEC 引用的 `theme.css` 行号有 1-2 行漂移

**What goes wrong:** UI-SPEC 写 `.el-tag` 锁在 `theme.css:542-549`，实际 `.el-tag { font: 500 11px/1.4 var(--font-mono) !important; }` 在 **547** 行；写 `.el-input__inner` 在 `516-519`，实际 font 声明在 **518**；写 `.el-form-item__label` 在 `660-664`，实际 font 声明在 **663**；写 `.el-button--primary` 在 `449-459`，实际 font 声明在 **454**。`ul` 全局重置在 **333** 行。
**How to avoid:** executor 按**选择器**而非行号定位；本文件已记录实际行号，可作为权威。
**Warning signs:** 按行号改错位置。

### Pitfall 10: `:gutter="20"` 确实出现 3 次（已确认）

**What goes wrong:** 若只改一处，另两处仍渲染每侧 10px 的 off-grid 值。
**How to avoid:** `prevention/Profile.vue` 的 **29 / 63 / 101** 行三处 `el-row :gutter="20"` 全部改为 `16`（`[VERIFIED: hhs-frontend-v2/src/views/prevention/Profile.vue:29,63,101]`）。补填面板的 `el-row` 也必须是 `16`。
**Warning signs:** UI-SPEC §Spacing 的 4 倍数断言在 review 时失败。

### Pitfall 11: `el-form-item__content` 是 flex 容器，说明行必须 `flex-basis: 100%`

**What goes wrong:** 把 `.field-hint` 直接放进 `el-form-item` 默认插槽会与输入框挤在同一行。
**How to avoid:** 严格照抄 UI-SPEC Contract 2 的 `.field-hint { flex-basis: 100%; display: flex; align-items: flex-start; gap: 4px; margin-top: 4px; }`；窄屏用 `label-position="top"` 而**不是**改 `el-form-item` 的 `display`（会连带破坏 `flex-wrap`）。
**Warning signs:** 说明文字出现在输入框右侧。

### Pitfall 12: 用 `--text-3` 写说明文字在浅色主题不达 AA

**What goes wrong:** `--text-3` 浅色为 `#7c7f8e`，实测 **3.97:1**，正文要求 4.5:1。
**How to avoid:** 所有说明性小字一律 `--text-2`（浅色 **8.38:1**）。UI-SPEC 已全表覆盖，executor 不得"顺手"换成 `--text-3`。
**Warning signs:** 对比度复核不通过。

### Pitfall 13: `Profile.vue` 的 GSAP 时间线会与新 DOM 抢跑

**What goes wrong:** `onMounted` 里 `tl.fromTo('.el-form .el-row', ...)` 只对既有 `.el-row` 生效；若完整度头部插在 `.el-card` 内、且 store 数据异步到达，头部会在时间线结束后才渲染，出现"突现"。
**How to avoid:** UI-SPEC Contract 8 已裁定「数据未到达前不渲染、不加骨架屏」；横幅也明确不进入 GSAP 时间线（新类名天然不在选择器内）。executor 不要为完整度头部新增入场动画。
**Warning signs:** 档案页加载时头部闪烁。

## Code Examples

### 完整度 VO（后端契约，供前后端共同遵守）

```java
// hhs-backend/src/main/java/com/hhs/vo/ProfileCompletenessVO.java（新增）
public record ProfileCompletenessVO(
        Integer score,        // 0-100，后端权威
        String level,         // 基础档案 / 标准档案 / 完整档案
        Integer filledCount,
        Integer totalCount,   // = fields.size()，前端不得硬编码 8 或 9
        List<ProfileFieldStatusVO> fields
) {}

// hhs-backend/src/main/java/com/hhs/vo/ProfileFieldStatusVO.java（新增）
public record ProfileFieldStatusVO(
        String key,           // gender / birthDate / heightCm / ... / lifestyleHabits（9 项）
        String label,         // 中文名，与 FIELD_LABELS 保持一致
        String importance,    // required / recommended / optional
        boolean filled,
        String reason,        // 「为何需要」，必须对照真实消费方
        String assumption     // 该字段缺失时的替代说明；已填时为 null（Contract 5 需要）
) {}
```

### 前端 store（单源 + 静默 + 离线兜底）

```ts
// hhs-frontend-v2/src/stores/profileCompleteness.ts（新增，结构照抄 useMetricMetaStore）
const DISMISS_KEY = 'hhs_profile_reminder_dismissed_at'
const SILENCE_MS = 7 * 24 * 60 * 60 * 1000

// 关键不变量：
// 1. 加载失败 → state 保持 null，绝不合成 0%（UI-SPEC Contract 8：失败与 0% 必须可区分）
// 2. 404（档案不存在）→ 用 utils/profileFields.ts 静态目录合成 0% / 基础档案 / 全未填
// 3. score/level 只用后端值；前端不算分
// 4. dismissedAt 解析失败（NaN）→ 视为未静默，不吞掉提醒
```

### 假设条目文案（后端产出，前端逐条原样渲染）

```
缺少出生日期，年龄按 35 岁估算            ← 仅 legacy 行可达（DTO @NotNull）
缺少 BMI，按 22.0 估算                    ← heightCm/weightKg 任一缺失时可达
缺少家族病史，糖尿病风险评估按「无家族史」计算
未填写健康档案，风险得分按通用参考值估算    ← profile == null，最常见
```

### 前端渲染条件（防止 null 被画成 0%）

```ts
// EstimateBadge 的显示判定（UI-SPEC Contract 4「条件收紧」）
const showBadge = computed(() =>
  data.value?.profileCompleteness != null && data.value.profileCompleteness < 100
)
// 横幅触发（UI-SPEC Contract 3）
const showBanner = computed(() =>
  c.value != null && c.value.score < 60 && !isDismissed()
)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| 硬编码扣分制（`-10/-15/-10/-15`，空档案 70） | 加权分层口径（60/30/10）+ 三等级 | 本阶段（D-01） | 完整度从"一个魔数"变成可解释的逐字段状态 |
| 静默代入默认值（`default -> 70` / `35` 岁 / BMI 22.0） | 在代入点捕获并披露 `assumptions[]` | 本阶段（PRF-05） | 披露与行为同源，不会漂移 |
| 徽标复用 `el-tag` | 自绘 `button.hhs-estimate-badge` | 本阶段（UI-SPEC 覆盖 D-04） | 避开 `theme.css` 的 `!important` 锁与浅色 3.74:1 |
| 横幅复用 `el-alert` | 自绘 `div.profile-banner` | 本阶段（UI-SPEC 覆盖 D-05） | 避开 EP 默认插槽渲染进 `<p>` 导致按钮被提升 |
| `CACHE_KEY_PREFIX = "health:score:"` | `"health:score:v2:"` | 本阶段建议 | 旧条目自然过期，不依赖下游判 null |

**Deprecated/outdated:**
- `RiskAssessmentListVO`（`vo/RiskAssessmentListVO.java`）：全仓库零引用（本会话 grep 确认），不要往它上面加字段。
- `RiskAnalyzer`（`service/domain/RiskAnalyzer.java`）：全仓库零注入点（本会话 grep 确认只有自身定义），CONTEXT 把它列为"假设捕获位置"之一，但**它当前根本不参与风险评分链路**（真实链路是 `RiskAssessmentServiceImpl` 的私有静态 `RiskRuleEngine`）。planner 不要为它安排任务。

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 完整度权重 60/30/10 与阈值 60/85 采信 D-01 的"待确认默认值" | Summary / Pattern 2 | 等级文案与横幅触发点整体偏移；需同步改前端常量与测试 |
| A2 | `bmi` 计入 `fields[]` 共 9 项（而非 8 项） | Pattern 2 | UI-SPEC 分段数、`已填 x / y`、图例项数全部错位；`totalCount` 若前端硬编码会立刻不一致 |
| A3 | 健康评分贡献是否等于完整度（Pitfall 4 的 (a)/(b)） | Pitfall 4 | 选 (a) 会让无档案用户评分骤降约 17.5 分并产生历史断点 |
| A4 | 风险页假设用"读时重算当前档案"而非加列持久化 | Pitfall 7 | 详情对话框文案「本次评估采用的假设」会与语义不符；若用户补填档案，旧评估的假设列表会变 |
| A5 | 7 天静默键名用 `hhs_profile_reminder_dismissed_at`（不含 userId） | Standard Stack / Pattern | 同一浏览器多账号共用时会互相静默；CONTEXT D-05 已给定该键名，故按锁定值实现 |
| A6 | `gender` 的 `required: true` 保留，`birthDate` 后端本就 `@NotNull` | Summary 三 / Pitfall | 若改为软提示，需同步放宽 `HealthProfileRequest` 的 `@NotNull`，会改变既有 API 契约 |
| A7 | 不修 `case "glucose"` 与 `"血糖"` 死分支 | Pitfall 6 | 若修，`metabolic` 维度分数会变化，属跨阶段回归风险 |

## Open Questions (RESOLVED)

1. **健康评分贡献是否等于完整度？**
   - What we know: D-01 要求完整度重构为加权口径；`ProfileScorer.calculate` 的唯一外部调用点是 `ScoreCalculator:54`；空档案当前返回 70。
   - What's unclear: D-01 没有说"健康评分里的档案贡献必须等于完整度"。
   - **RESOLVED (2026-11-09, 方案 b 采纳):** 新增 `ProfileCompletenessCalculator` 作为权威完整度来源；`ProfileScorer.calculate` 保留 70 中性语义不动，完整度只用于展示与披露。健康评分历史无断点，零回归风险。

2. **风险页 `assumptions[]` 的来源：读时重算 vs 加列持久化？**
   - What we know: `createAssessment` 删旧插新；`listByUserId`/`getById` 从 DB 行重建；`risk_assessment` 表无假设列；`schema.sql` 只在空卷执行。
   - What's unclear: PRF-06 的徽标是描述"本次评估"还是"当前档案"。
   - **RESOLVED (2026-11-09, 读时重算采纳):** 列表/详情展示时按当前档案重新推导假设，零 DDL 零迁移；徽标语义定为「当前档案下，该评估所依赖的默认假设」，详情对话框标题措辞相应调整。

3. **横幅静默是否应按用户隔离？**
   - What we know: D-05 给定键名 `hhs_profile_reminder_dismissed_at`（无 userId）；`storage.ts` 用 `hhs_token`/`hhs_user` 下划线风格。
   - What's unclear: 多账号同浏览器场景是否需隔离。
   - **RESOLVED (2026-11-09, 按锁定键名):** 实现 `hhs_profile_reminder_dismissed_at`（无 userId，多账号共享静默期）；`logout()` 不清理该键（提醒偏好非会话状态）；已知限制写入 PLAN。

4. **完整度端点是否也需要 `silent` 语义？**
   - What we know: `preventionApi.getProfile` 已支持 `config?: AxiosRequestConfig` 透传 `{ silent: true }`（`api/prevention.ts:18-21`）；`request.ts` 拦截器本身已不展示任何东西。
   - **RESOLVED (2026-11-09):** 新端点沿用 `getCompleteness(config?: AxiosRequestConfig)` 签名，调用点传 `{ silent: true }`；404 / `health.profile_not_found` 不弹 toast（档案不存在是正常状态）。

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | 后端构建/测试 | ✓ | 17（`D:\JAVA\jdk-17`） | — |
| Maven | 后端构建/测试 | ✓ | 3.8.4 | — |
| Node.js | 前端构建/测试 | ✓ | 24.15.0 | — |
| npm | 前端依赖/脚本 | ✓ | 11.12.1 | — |
| Docker | 集成测试（Testcontainers）/ 本地 MySQL+Redis | ✓ | `docker info` 成功 | — |
| MySQL 8 / Redis 7 | 运行时 | 由 `docker compose` 提供（3307/6380） | — | 单测全部 Mockito，无需真实 DB/Redis |

**Missing dependencies with no fallback:** 无
**Missing dependencies with fallback:** 无

> 本会话实测：`mvn -f hhs-backend/pom.xml test -Dtest=HealthScoreServiceTest` 退出码 0（仅输出预期的 Redis 降级 WARN，无断言失败）；`npx vitest run` → **29 files / 263 tests 全绿**（约 11.7s）。

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework (backend) | JUnit 5 + Mockito（`spring-boot-starter-test`）；Surefire 3.0.0；集成测试 Failsafe + Testcontainers 1.19.3 |
| Framework (frontend) | Vitest 4.1.7 + @vue/test-utils；`environment: 'happy-dom'`, `globals: true` |
| Config file (backend) | `hhs-backend/pom.xml:301-314`（checkstyle google_checks）；Surefire 排除 `**/*IntegrationTest.java`、`**/*PerformanceTest.java` |
| Config file (frontend) | `hhs-frontend-v2/vitest.config.ts`（`setupFiles: ['./src/__tests__/setup.ts']`, `exclude: ['node_modules','e2e/**']`） |
| Quick run (backend, 单类) | `mvn -f hhs-backend/pom.xml test -Dtest=ProfileCompletenessCalculatorTest` |
| Quick run (frontend, 单 spec) | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/components/ProfileCompleteness.spec.ts` |
| Full suite (backend) | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` |
| Full suite (frontend) | `npm --prefix hhs-frontend-v2 run test:run` |
| Lint / 类型 | `mvn -f hhs-backend/pom.xml checkstyle:check` + `npm --prefix hhs-frontend-v2 run lint:check` + `npm --prefix hhs-frontend-v2 run build`（含 `vue-tsc -b`） |
| Baseline（本会话实测） | 前端 29 files / **263 tests** 全绿；后端 `HealthScoreServiceTest` 退出码 0 |

> 命名纪律：`*IntegrationTest` 由 Failsafe 拾取，**不会**被 Surefire 执行。本阶段若加集成测试（如 completeness 端点 MVC slice），要么用 `*IntegrationTest` 命名走 Failsafe，要么用 `@WebMvcTest` 的 `*Test` 命名留在 Surefire。

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PRF-01 | 加权完整度：9 字段、三等级边界（59/60/84/85）、空档案 0% | unit (backend) | `mvn -f hhs-backend/pom.xml test -Dtest=ProfileCompletenessCalculatorTest` | ❌ Wave 0 |
| PRF-01 | 完整度端点返回 `fields[]` 长度 9 且 `totalCount == fields.size()` | unit (backend MVC slice) | `mvn … -Dtest=ProfileCompletenessControllerTest` | ❌ Wave 0 |
| PRF-01 | store 单源、404 合成 0%、加载失败保持 null（不画 0%） | unit (frontend) | `npm … test:run -- src/__tests__/stores/profileCompleteness.spec.ts` | ❌ Wave 0 |
| PRF-01 | `ProfileCompleteness.vue` 分段数 = `fields.length`；图例 ✓/○ + 读屏文本 | unit (component) | `npm … test:run -- src/__tests__/components/ProfileCompleteness.spec.ts` | ❌ Wave 0 |
| PRF-02 | 9 个字段的 `reason` 非空且与消费方一致（birthDate→年龄、bmi→22.0、familyHistory→糖尿病） | unit (backend) | `mvn … -Dtest=ProfileFieldCatalogTest` | ❌ Wave 0 |
| PRF-02 | 档案页渲染 9 条 `.field-hint`，文案来自后端而非硬编码 | unit (component) | `npm … test:run -- src/__tests__/views/ProfileCompleteness.spec.ts` | ❌ Wave 0 |
| PRF-03 | 三档 `importance` 与 `.hhs-imp-badge--*` 类映射正确；承诺句**只出现一次** | unit (component) | 同上 | ❌ Wave 0 |
| PRF-04 | 横幅文案点名缺失字段 + 下游后果；`score>=60` 不渲染 | unit (component) | `npm … test:run -- src/__tests__/components/ProfileReminderBanner.spec.ts` | ❌ Wave 0 |
| PRF-05 | `profile == null` → 1 条假设 + 固定分 30/25/25；`bmi==null` → 「按 22.0 估算」 | unit (backend) | `mvn … -Dtest=RiskAssessmentAssumptionsTest` | ❌ Wave 0 |
| PRF-05 | `ScoreCalculator` 汇总的 `assumptions` 与各 scorer 实际默认代入一致 | unit (backend) | `mvn … -Dtest=ScoreCalculatorAssumptionsTest` | ❌ Wave 0 |
| PRF-05 | 旧签名 `calculate(Long)` 行为不变（既有 19 个 domain 测试必须保持绿） | unit (backend) | `mvn … -Dtest=WellnessScorerTest,ScreeningScorerTest` | ✅ 已存在 |
| PRF-06 | 徽标仅当 `completeness != null && < 100`；`null` 不渲染（绝不画 0%） | unit (component) | `npm … test:run -- src/__tests__/components/EstimateBadge.spec.ts` | ❌ Wave 0 |
| PRF-06 | 点击徽标 → `router.push({path:'/prevention/profile', query:{focus}})` | unit (component) | 同上 | ❌ Wave 0 |
| PRF-07 | 关闭后 7 天内不渲染；第 7 天+1ms 恢复；`dismissedAt` 非法值视为未静默 | unit (frontend, fake timers) | `npm … test:run -- src/__tests__/stores/profileCompleteness.spec.ts` | ❌ Wave 0 |
| PRF-07 | 全站单实例：仅 `dashboard/Index.vue` 渲染横幅 | unit (component) | `npm … test:run -- src/__tests__/views/DashboardProfileBanner.spec.ts` | ❌ Wave 0 |
| PRF-08 | 判定顺序：假设为空 → 直接创建（面板不展开）；假设非空且 `<100` → 展开不调 API | unit (component) | `npm … test:run -- src/__tests__/views/RiskBackfill.spec.ts` | ❌ Wave 0 |
| PRF-08 | 「直接创建（按估算）」「放弃创建」均可用；保存失败不清空输入 | unit (component) | 同上 | ❌ Wave 0 |
| 回归 | `ProfileErrorSample.spec.ts` 3 个既有用例保持绿（需补 `vue-router` 与 `getCompleteness` mock） | unit (component) | `npm … test:run -- src/__tests__/views/ProfileErrorSample.spec.ts` | ✅ 已存在（需改 mock） |
| 回归 | `errorMessages.spec.ts` 18 键精确断言不被破坏 | unit (frontend) | `npm … test:run -- src/__tests__/utils/errorMessages.spec.ts` | ✅ 已存在（不得加键） |
| 回归 | `HealthScoreServiceTest` 12 用例保持绿（VO 加字段不影响） | unit (backend) | `mvn … -Dtest=HealthScoreServiceTest` | ✅ 已存在 |

### 预期失败信号（用于确认测试真的在测东西）

| Test | 实现前的预期失败信息 |
|------|----------------------|
| `ProfileCompletenessCalculatorTest.emptyProfile_scoresZero` | 当前 `ProfileScorer` 返回 70 → `expected: <0> but was: <70>` |
| `ProfileCompletenessCalculatorTest.requiredOnly_scoresSixty` | 类不存在 → 编译失败（Wave 0 必须先建空壳再断言） |
| `ProfileCompletenessControllerTest.fieldsHasNineEntries` | 端点 404 → `Status expected:<200> but was:<404>` |
| `RiskAssessmentAssumptionsTest.noProfile_emitsGenericAssumption` | 断言 `assumptions` 为空 → `expected size 1 but was 0` |
| `ScoreCalculatorAssumptionsTest.bmiMissing_mentions22` | `assumptions` 字段为 null → NPE / `expected not null` |
| `profileCompleteness.spec.ts` 404 分支 | store 未实现 → import 失败 |
| `ProfileReminderBanner.spec.ts` 7 天窗口 | 无静默逻辑 → 第 8 天仍渲染 → `expected false but was true` |
| `RiskBackfill.spec.ts` 判定顺序 | 面板无条件展开 → 假设为空时也展开 → `expected false but was true` |
| `ProfileErrorSample.spec.ts`（改后） | 未 mock `vue-router` → `TypeError: Cannot read properties of undefined (reading 'query')` |

### Sampling Rate

- **Per task commit:** 该任务所属语言的单类 / 单 spec 命令
- **Per wave merge:** `mvn -f hhs-backend/pom.xml clean verify -DskipITs` + `npm --prefix hhs-frontend-v2 run test:run`
- **Phase gate:** 后端全量单测 + 前端全量（≥263 项且全绿）+ `checkstyle:check` + `lint:check` + `build`（`vue-tsc -b` 会暴露类型错误）全部通过后再进 `/gsd:verify-work`
- **Max feedback latency:** 前端单 spec ~8s；前端全量 ~12s；后端单类 ~20s；后端全量 ~90s

### Wave 0 Gaps

- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ProfileCompletenessCalculatorTest.java` — PRF-01（9 字段、三等级边界 59/60/84/85、空档案 0、全填 100、陈旧 bmi 免疫）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java` — PRF-02（9 条 `reason` 非空且含真实消费方关键词）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ScoreCalculatorAssumptionsTest.java` — PRF-05（collector 汇总正确、旧签名行为不变）
- [ ] `hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java` — PRF-05（`profile==null` / `bmi==null` / 缺家族史三条路径）
- [ ] `hhs-backend/src/test/java/com/hhs/controller/ProfileCompletenessControllerTest.java` — PRF-01（`@WebMvcTest` slice，断言 `fields.length==9`）
- [ ] `hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts` — PRF-01/07（单源、404 合成、失败保持 null、7 天窗口 fake timers）
- [ ] `hhs-frontend-v2/src/__tests__/components/ProfileCompleteness.spec.ts` — PRF-01（分段数 = `fields.length`、图例可访问性）
- [ ] `hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts` — PRF-04/07
- [ ] `hhs-frontend-v2/src/__tests__/components/EstimateBadge.spec.ts` — PRF-06（null 不渲染、点击跳转带 focus）
- [ ] `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts` — PRF-08（判定顺序三序）
- [ ] `hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts` — PRF-07（全站单实例）
- [ ] 修改 `hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts` — 补 `vi.mock('vue-router')` 与 `getCompleteness` mock（否则既有 3 用例转红）

*(其余既有测试无需改动：`HealthScoreServiceTest`、`WellnessScorerTest`、`ScreeningScorerTest`、`PreventionIntegrationTest`、`HealthScoreIntegrationTest`、`errorMessages.spec.ts` 均不受影响，前提是遵守 Pattern 3 的重载纪律与"不加字典键"约束。)*

## Security Domain

`workflow.nyquist_validation: true`，`security_enforcement` 未显式关闭 → 本节必需。

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | 沿用既有 JWT Bearer；无新认证路径 |
| V3 Session Management | no | 无会话状态变更 |
| V4 Access Control | **yes** | 新端点 `GET /api/prevention/profile/completeness` 必须用 `SecurityUtils.getCurrentUserId()` 取用户，**禁止**接受路径/查询参数中的 `userId`。参照 `PreventionController.getMyHealthProfile()`（`:54-64`）的既有写法，而**不要**参照 `getHealthProfile(@PathVariable Long userId)`（`:66-81`，它必须额外做 `currentUserId.equals(userId)` 校验） |
| V5 Input Validation | **yes** | 补填面板只写 `gender`/`birthDate`，走既有 `@Valid HealthProfileRequest`（`birthDate` `@NotNull` + `@Past`；`gender` `@Pattern`）。前端 `el-form` 规则复用现有 `rules`，服务端错误经 `useFormServerErrors.apply()` 内联 |
| V6 Cryptography | no | 无加密逻辑；不在前端或后端手写任何哈希/加密 |

### Known Threat Patterns for {Spring Boot + Vue 3}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR：完整度端点被传入他人 userId 读取档案完整度 | Information Disclosure | 端点**不接受** userId 参数，只从 `SecurityUtils.getCurrentUserId()` 取（V4） |
| 越权写档案：补填面板伪造 `profile.id` 更新他人档案 | Tampering | 走既有 `PUT /health-profile/{id}`，其实现已校验 `existing.getId().equals(id)` 且按 userId 查询（`HealthProfileServiceImpl:70-79`） |
| 披露信息泄露：`assumptions[]` 内含内部字段名或异常堆栈 | Information Disclosure | 假设条目是后端常量中文串，**不拼接**任何异常 message / SQL / 类名；前端逐条原样渲染（不做 HTML 解释） |
| localStorage 静默被篡改为永久静默 | Repudiation | 该键只影响本机提示频率，无安全后果；解析失败时按"未静默"处理（fail-open to reminder，不 fail-silent） |
| 大量重复请求打爆 completeness 端点 | Denial of Service | store 内 `if (loading.value) return` 抑制并发（照抄 `useMetricMetaStore:71-73`）；端点本身为单表查询，成本与既有 `getByUserId` 同级 |

## Sources

### Primary (HIGH confidence) — 本会话逐行读取的第一手源码

- `hhs-backend/src/main/java/com/hhs/service/domain/ProfileScorer.java`（全文 63 行）— 硬编码 `-10/-15/-10/-15`、空档案 70
- `hhs-backend/src/main/java/com/hhs/service/domain/DimensionScoreCalculator.java`（全文 100 行）— `getPenalty` 的 `value == null → 0`、`case "glucose"` 死分支
- `hhs-backend/src/main/java/com/hhs/service/domain/RiskScorer.java`、`RiskAnalyzer.java`、`MetricsScorer.java`、`WellnessScorer.java`、`ScreeningScorer.java`、`ScoreCalculator.java`
- `hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java`（全文 170 行）— `:118-120` 年龄 35 / BMI 22 默认、`:122` 中文 key 死分支、`:135-139` 无档案固定分
- `hhs-backend/src/main/java/com/hhs/service/impl/HealthScoreServiceImpl.java`（全文 167 行）— `:44-47` NO_DATA 早返回、`:32-33` TTL 与 key 前缀、`:131-155` 缓存读写
- `hhs-backend/src/main/java/com/hhs/config/RedisConfig.java`（全文 104 行）— `activateDefaultTyping(NON_FINAL)`
- `hhs-backend/src/main/java/com/hhs/vo/HealthScoreVO.java`、`RiskAssessmentVO.java`、`HealthProfileVO.java`、`RiskAssessmentListVO.java`
- `hhs-backend/src/main/java/com/hhs/entity/HealthProfile.java`、`RiskAssessment.java`
- `hhs-backend/src/main/java/com/hhs/dto/HealthProfileRequest.java` — `:16-17` `birthDate` `@NotNull`
- `hhs-backend/src/main/java/com/hhs/controller/PreventionController.java`（全文 194 行）、`HealthScoreController.java`
- `hhs-backend/src/main/java/com/hhs/service/impl/HealthMetricServiceImpl.java` — `:79-91` 写库规范化
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java` — `:58` `glucose → bloodGlucose`
- `hhs-backend/src/main/resources/sql/schema.sql` — `:66-82` health_profile、`:359-371` risk_assessment、`:663-694` 幂等 ALTER 先例
- `hhs-frontend-v2/src/views/prevention/Profile.vue`（全文 391 行）、`prevention/Risk.vue`（全文 185 行）、`dashboard/Index.vue`（全文 706 行）、`health/Score.vue`（全文 981 行）
- `hhs-frontend-v2/src/stores/metricMeta.ts`、`utils/storage.ts`、`utils/errorMessages.ts`、`utils/appError.ts`、`utils/request.ts`、`composables/useFormServerErrors.ts`、`api/prevention.ts`、`types/api.ts`、`router/index.ts`
- `hhs-frontend-v2/src/assets/styles/theme.css` — `:333` ul 重置、`:454` 主按钮、`:518` 输入框、`:547` el-tag、`:663` 表单标签
- `hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts`（全文 265 行）、`utils/errorMessages.spec.ts`
- `hhs-backend/src/test/java/com/hhs/service/HealthScoreServiceTest.java`、`HealthProfileServiceTest.java`、`integration/PreventionIntegrationTest.java`、`integration/HealthScoreIntegrationTest.java`

### Secondary (MEDIUM confidence) — 本会话实测运行

- `npx vitest run` → 29 files / 263 tests passed（11.66s）
- `mvn -f hhs-backend/pom.xml test -Dtest=HealthScoreServiceTest` → 退出码 0
- `docker info` → 可用
- `element-plus@2.14.0` / `@element-plus/icons-vue@2.3.2` / `vue@3.5.34` / `vitest@4.1.7` 从 `node_modules/*/package.json` 读取

### Tertiary (LOW confidence) — 需在实施中验证

- 三等级阈值 60/85 的医学合理性（D-01 标注为"待确认默认值"）
- 权重 60/30/10 的具体数值（同上）
- 「7 天」静默窗口的产品合理性（D-05 已锁定，无需再议）

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — 零新增依赖，全部版本从 `node_modules` / `pom.xml` 实际读取
- Architecture: HIGH — 所有调用链、默认代入点、缓存与短路路径均逐行读取源码确认；`RiskAnalyzer` 零注入点、`case "glucose"` 死分支、`NO_DATA` 早返回三条关键结论均有文件+行号支撑
- Pitfalls: HIGH — 13 条中 11 条有源码行号或实测运行证据；2 条（Pitfall 5 的陈旧 BMI、Pitfall 7 的语义取舍）依赖 MyBatis-Plus 默认字段策略与产品语义，已标注为需在实施中确认

**Research date:** 2026-11-09
**Valid until:** 2026-12-09（30 天；后端结构稳定，但若 Phase 12 执行改动 `HealthScoreServiceImpl`/`ScoreCalculator` 需重新核对缓存前缀与调用链）
