# Phase 12: 阈值配置向导化与告警正确性修复 - Research

**Researched:** 2026-11-08
**Domain:** Spring Boot 3.2 + MyBatis-Plus 阈值判定/归一化修复；MySQL 8 幂等数据修复；Vue 3 + Element Plus 2.14 元数据驱动向导
**Confidence:** HIGH（后端全部结论来自本会话逐行读取的第一手源码与字节码；MySQL/Spring 语义经字节码与官方手册核实；前端结论来自文件全文读取）

## Summary

本阶段是"先修地基、再建上层"的组合：FIX-01..05 让阈值真的生效，THR-01..08 让用户配得对。研究得出三条会改变计划写法的硬结论：

**（一）`classify()` 的权威分档表达式已可精确推导，且不存在边界双计。** 以现有两个私有助手的包含式语义（`isBelowThreshold` = `v <= t`，`isAboveThreshold` = `v >= t`）为基准，正确写法是"临界优先 + 取反剔除"，而不是自行发明半开区间：

```
CRITICAL_LOW  : isBelowThreshold(v, criticalLow)
WARNING_LOW   : isBelowThreshold(v, warningLow)  && !isBelowThreshold(v, criticalLow)
WARNING_HIGH  : isAboveThreshold(v, warningHigh) && !isAboveThreshold(v, criticalHigh)
CRITICAL_HIGH : isAboveThreshold(v, criticalHigh)
NORMAL        : 其余
```

这组表达式在合法次序下等价于 `v <= criticalLow | criticalLow < v <= warningLow | warningLow < v < warningHigh | warningHigh <= v < criticalHigh | v >= criticalHigh`，正是 ROADMAP 成功标准 1 的措辞，并且与**未改动的** `isNormalRange`（`warningLow < v < warningHigh`）完全互补——`warningHigh` 归 WARNING_HIGH、`warningLow` 归 WARNING_LOW，各边界恰好归属一个档位，无缺口无重叠。每个边界独立可 null，上式天然 null-safe，不需要额外的 null 分支表。

**（二）`selectOne()` + `.in(aliases)` 会抛 `TooManyResultsException`，这是 D-17 的直接地雷。** 反编译 `mybatis-plus-core-3.5.5` 的 `BaseMapper.selectOne(Wrapper)` 证实其默认委托 `selectOne(wrapper, true)`，`throwEx=true`：结果数 > 1 时**抛异常**而非取第一条。存量库存在 `glucose` + `bloodGlucose` 并存窗口时（D-15 明确承认部署顺序可能产生），任何把 `.eq` 直改成 `.in` 的 `selectOne` 都会在运行时炸掉。必须改为 `selectList(wrapper.orderByDesc(...))` 取首条，或显式 `selectOne(wrapper, false)`。

**（三）"可告警指标 = 6 项"有 UI-SPEC 的独立印证，不应靠猜测。** `alert_rule` 种子 7 行中 `weight` 四个阈值列全 NULL（`schema.sql:521`），实际可评估的是 6 项；UI-SPEC Component Contract 6 的空状态默认规则表恰好列 6 项「心率 / 收缩压 / 舒张压 / 血糖 / 体温 / BMI」。因此 `alertable` 的权威定义应为「`alert_rule` 存在 enabled 且至少一个阈值非 null 的行」，bloodOxygen 因此为 `false`。同时需要新增的 `user_threshold` 行虽不会被向导提供，但列表页仍须展示——"非 alertable"不等于"删掉用户数据"。

**Primary recommendation:** 按 FIX-01/05 → FIX-02/03 → 代码部署 → FIX-04 脚本 → 元数据扩展 → 向导 UI → 校验 的顺序并行推进；所有阈值查询改 `selectList + orderByDesc(updatedAt)` 取首条，绝不在别名 IN 查询上保留 `selectOne`。

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**向导形态**
- **D-01:** 采用 `ElSteps` 三步向导，复用 `views/data-input/components/DeviceConfigWizard.vue` 的既有范式：
  - 第 1 步：选择指标（元数据驱动、分组、仅 alertable）→ 选定后自动预填推荐值
  - 第 2 步：四项阈值输入（各自带通俗中文说明 + 单位）+ 实时四区预览条
  - 第 3 步：自然语言确认语句 + 保存
- **D-02:** 编辑已有阈值时**跳过第 1 步**直接进入第 2 步；指标类型锁定不可改；第 3 步确认语句仍然显示，可回退到第 2 步修改。
  - **Reversibility:** costly — 105 个调用点之外的页面级交互契约一旦被用户与测试熟悉，改回单弹窗要重写整套向导与相关测试。

**指标范围**
- **D-03:** 可告警指标由**后端**元数据注册中心权威决定：为每个指标增加 `alertable` 标志，并在同一响应中下发推荐阈值。前端**不维护白名单常量**。
- **D-04:** 选择器只渲染 `alertable === true` 的指标；按元数据 `category` 分 HEALTH / WELLNESS 两组，**空组隐藏**（当前 WELLNESS 组无可告警指标，因此实际只显示 HEALTH 组）。
- **D-05:** 选项文案为 `显示名 (单位)`，例如 `收缩压 (mmHg)`。
  - **Reversibility:** costly — 若改为前端白名单，会与后端种子规则漂移，需要重新校准两端。

**推荐值来源**
- **D-06:** 推荐四值由**后端一并下发**（与 `alertable` 标记同一响应），来源为 `alert_rule` 种子派生；种子缺失时用元数据 `normalMin`/`normalMax` 推导。前端**不做独立计算**，避免前后端算出不同推荐值。
- **D-07:** 只有单一「推荐值」一档，**不做**宽松/标准/严格三档预设。
- **D-08:** 选定指标后**自动预填**四项为推荐值并标注「推荐」徽标；同时提供「恢复推荐值」按钮让用户回到初值。

**层级与生理极限校验（THR-05）**
- **D-09:** **双侧校验**。前端为即时反馈，后端为权威拦截（直接调用 API 可绕过前端）。
  - 前端：违反时实时禁用「下一步 / 保存」，并在**违反的两个输入框下各挂一条内联中文**（例：「警告高值必须大于警告低值」），第 2 步顶部同步出现 GOV.UK 汇总块。
  - 后端：新增类级约束 `@ValidThresholdRange` 挂到 `UserThresholdRequest`，沿用既有 `@ValidMetricRecord` 先例；违规消息同时 `addPropertyNode` 到涉及的**两个字段**，使两个输入框都获得内联红字，与 Phase 11 的 `fieldErrors` 契约天然对齐。
- **D-10:** 合法次序为 `criticalLow < warningLow < warningHigh < criticalHigh`，每个边界可独立为 `null`，仅当相邻两侧均非 null 时才要求成立（与"null = 无界"的既有语义一致）。
- **D-11:** 生理极限边界使用元数据 `hardMin`/`hardMax`，同时作为 `el-input-number` 的 `:min`/`:max` 与校验边界。**不使用** `PANIC_LIMITS`（语义是急危重症阈值而非物理可达边界，且仅覆盖 10 项）。
- **D-12:** 校验失败呈现严格遵循 Phase 11 已定契约：**零 toast**，汇总 + 内联双呈现，不清空用户已输入内容。

**存量数据修复（FIX-04）**
- **D-13:** 交付为**独立幂等 SQL 脚本** `hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql`，部署时手动执行。**不引入 Flyway**，**不在应用启动时自动执行**。
- **D-14:** 归一化后产生的重复行（同 user + 同标准 key）**保留 `updated_at` 最新的一条**，删除其余——反映用户最近一次设置意图。
- **D-15:** `alert_rule` 种子里的非标准 key（`glucose`、`temperature`）**一并修正**为 `bloodGlucose`、`bodyTemperature`，并写幂等 UPDATE 修存量 `alert_rule` 行。否则未配置阈值的用户回退到种子规则时仍会因 key 不匹配而失效。
  - **Reversibility:** one-way — 一旦生产库跑过归一化，旧 key 不再存在；回退需要反向映射且会丢失原行 id。

**正确性修复（FIX-01/02/03/05）**
- **D-16:** `isWarningRange` 修复后**抽出单一 `classify()`**，让 `isNormalRange` / `isWarningRange` / `isCriticalRange` / `getSeverityLevel` 共享同一判定，杜绝再次分叉。
- **D-17:** 阈值写入路径（`UserThresholdServiceImpl.create/update`）在**预检查与入库两处**都调用 `MetricKeyNormalizer.toCanonicalKey()`；查询路径（`getByUserAndMetricKey` 及告警侧所有 `selectOne/selectList`）改用 `MetricKeyNormalizer.getAliases()` 的 `IN` 查询，兼容尚未归一化的历史行。
- **D-18:** `ThresholdRangeCheckerTest` 必须覆盖：正常 / 警告（高、低两侧）/ 危急（高、低两侧）/ 单侧为 null / 全部为 null / 恰好等于边界值。

### Claude's Discretion
- 四区预览条的具体视觉实现（`ElSlider` range + marks、自绘色带、或 `div` 拼接）——只要满足"颜色带 + 当前输入位置标注"即可。
- 确认语句的具体中文句式。
- 教学式空状态（THR-07）的默认阈值展示方式：直接读 `alert_rule` 种子或复用向导的推荐值来源。
- 重复指标（THR-08）的提示回退到 Phase 11 已实现的 `ERROR_CODE_ACTIONS.resource.exists → 打开编辑` 机制，只需确保文案为「该指标已有阈值，是否改为编辑？」。

### Deferred Ideas (OUT OF SCOPE)
- **宽松/标准/严格三档预设**（研究文档 THR-09 differentiator）——本轮不做，若后续需要可单开阶段。
- **条件感知预设**（"我已确诊高血压" → 血压阈值对齐指南，THR-10）——涉及隐私与临床建议，不在本轮范围。
- **阈值变更影响预览**（"改为这些值后，你近 30 天会有 N 条记录触发告警"）——需要历史数据扫描，属新能力。
- **应用启动时自动迁移框架**（Flyway/Liquibase）——项目已明确推迟，见 REQUIREMENTS.md Out of Scope。
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FIX-01 | 修正 `isWarningRange` 使两个区间正确判定 WARNING | §A 给出权威 `classify()` 分档表达式，证明与 `isNormalRange` 互补且无边界双计；列出全部调用点 |
| FIX-02 | 写入路径先规范化 `metricKey` 再存储 | §B 定位 `UserThresholdServiceImpl.create:49,55` / `update:84,91` 的规范化落点与"预检查+入库双处"顺序 |
| FIX-03 | 查询路径兼容全部别名集合 | §B 枚举 8 处读取点，指出 `selectOne + .in` 必抛 `TooManyResultsException`（字节码证实）及三种安全改法 |
| FIX-04 | 幂等 SQL 归一并合并重复行 | §C 给出 MySQL 8 精确 idiom、UNIQUE KEY 阻塞机制、派生表绕行、执行顺序与前后 COUNT 校验 |
| FIX-05 | `ThresholdRangeChecker` 全区间单元测试 | §F 确认该测试类当前**不存在**，给出 D-18 全覆盖的用例矩阵与命令 |
| THR-01 | 指标选择器元数据驱动、分组、仅 alertable | §D 给出 `alertable` 的最小增量为"alert_rule 有可用种子"=6 项，与 UI-SPEC 空状态 6 项互相印证 |
| THR-02 | 选定指标预填推荐值 + 推荐徽标 + 一键回填 | §D 推荐值来源三方案对比，推荐"controller 启动期合并 alert_rule → 注册表"；§G 前端类型增补 |
| THR-03 | 四项输入带通俗中文说明与单位 | §G 文案来自 UI-SPEC Copywriting Contract，单位插值自 `meta.unit` |
| THR-04 | 实时四区预览条 | §G `ThresholdZoneBar.vue` 独立组件，纯 computed，新增 2 个 theme token（已核实 `--hhs-color-error-tint` 存在） |
| THR-05 | 前后端同规则的层级与生理极限校验 | §E 用字节码证实 `SpringValidatorAdapter.determineField` 的点连接语义 → 必须"两次 addConstraintViolation，每次单节点" |
| THR-06 | 保存前自然语言确认语句 | §G 确认语句由已填边界生成，null 分句省略 |
| THR-07 | 教学型空状态 | §D/§G 默认规则表数据来自同一 `alertable+recommended` 响应，零额外请求 |
| THR-08 | 重复指标友好提示 + 打开编辑 | §G 现有 `handleSubmit` catch 块已实现，仅需改后端文案与 `[...]` 匹配改为规范化比较 |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 四档判定（NORMAL/WARNING/CRITICAL） | API / Backend (`ThresholdRangeChecker`) | — | 判定在告警引擎内执行；前端任何副本都只是 UX 前置，权威只能有一处 |
| `metricKey` 规范化（写） | API / Backend (`UserThresholdServiceImpl`) | — | 前端传什么都不能替代服务端规范化；直接调 API 可绕过 |
| 别名兼容读取 | API / Backend (所有 `selectXxx`) | — | 历史行在数据库里，只有服务端查询能覆盖 |
| 存量数据归一化 | Database (`fix-threshold-metric-key.sql`) | API / Backend（别名 IN 作为迁移前兜底） | 数据一次性变更属 DB 层；应用层兜底保证窗口期可用 |
| `alertable` + 推荐值权威定义 | API / Backend (`HealthMetricMetaRegistry` + `alert_rule`) | — | D-03 明令前端不维护白名单 |
| 指标元数据离线兜底 | Browser / Client (`FALLBACK_METRIC_META`) | API（异步覆盖） | 启动零延迟 + 弱网可用；但只能"镜像"后端，不能"定义"后端 |
| 层级/生理极限校验（即时反馈） | Browser / Client (`thresholdValidation.ts`) | — | 减少往返，明确标注"非权威" |
| 层级/生理极限校验（权威拦截） | API / Backend (`@ValidThresholdRange`) | — | 类级约束挂在 DTO，`@Valid` 已就位，零新增调用点 |
| 四区预览 / 确认语句 / 空状态 | Browser / Client | — | 纯展示 + 纯 computed，无请求 |
| `alert_rule` 种子修正 | Database (`schema.sql`) | — | 新库初始化即正确；老库由 FIX-04 脚本覆盖 |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jakarta Bean Validation (Hibernate Validator) | 8.0.1.Final（`spring-boot-starter-validation` 传递） | `@ValidThresholdRange` 类级跨字段约束 | 与既有 `@ValidMetricRecord` 同包同模式；`GlobalExceptionHandler` 已消费两条集合 |
| MyBatis-Plus | 3.5.5（`hhs-backend/pom.xml:22`） | 阈值/规则查询 | 既有 ORM；`LambdaQueryWrapper.in()` 已在 `HealthMetricServiceImpl:155` 有先例 |
| Element Plus | 2.14.0（已安装） | `ElSteps`/`ElDialog`/`ElInputNumber`/`ElSelect`/`ElDescriptions` | UI-SPEC 组件清单全部来自已安装包 |
| Vue 3 + Pinia | 3.5.x / 2.3.1 | `useMetricMetaStore` 承载 `alertable` + 推荐值 | 已有的元数据 store 直接扩展，无新 store |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 + Mockito | via starter | `ThresholdRangeCheckerTest`、`ThresholdRangeValidatorTest`、`UserThresholdServiceTest` 扩展 | 纯逻辑类无需 Spring 上下文 |
| Vitest + @vue/test-utils | 4.1.0 / 2.4.6 | 向导组件、四区条、校验纯函数 | 前端单测唯一框架 |
| MySQL | 8.0（`docker-compose.yml` `mysql:8.0`） | 幂等修复脚本 | 支持窗口函数；但**本脚本不需要窗口函数**（见 §C） |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 类级 `@ValidThresholdRange` | 服务层手工校验 | 服务层需要抛 `BusinessException` → 走 HTTP 200 业务通道 → 前端拿不到 `fieldErrors`，D-09 的"两个输入框内联"落不了地。**否决** |
| 两次 `addConstraintViolation`（各单节点） | 一次链式 `addPropertyNode(a).addPropertyNode(b)` | 字节码证实 `determineField` 用 `"."` 连接 PROPERTY 节点 → 得到字段名 `warningHigh.criticalHigh`，与表单 `prop` 不匹配，内联永远不亮。**否决** |
| 注册表内置推荐值（方案 a） | controller 启动期合并 `alert_rule`（方案 b） | 方案 a 把 DB 真值复制进代码 → 双源漂移；方案 b 单一真值。选 b（详见 §D） |
| `selectOne(wrapper, false)` | `selectList(...orderByDesc(updatedAt)).get(0)` | 前者更短但静默丢信息；后者可断言排序与"最新优先"，且与 D-14 的语义一致。**推荐后者** |

**Installation:** 无需安装。本阶段零新增 Maven 依赖、零新增 npm 包（STACK.md §(a) 的零依赖论证在 §D 得到印证）。

**Version verification:** 全部版本取自仓库内第一手文件，未新增任何包，故未调用 registry 探测。

## Package Legitimacy Audit

**本阶段不安装任何外部包。** 无新增 Maven artifact、无新增 npm package。故本表为空。

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| — | — | — | — | — | — | 无需审计（零新增依赖） |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

> 直接引用传递依赖 `@vueuse/core` 属 REQUIREMENTS.md `## Out of Scope` 明令禁止，本阶段所有 composable 只使用 `ref`/`computed`/`watch`（项目已配置 `AutoImport({ imports: ['vue', ...] })`，见 `vite.config.ts:20`）。

## Architecture Patterns

### System Architecture Diagram

```
                      ┌──────────────────────────────────────────┐
                      │  阈值配置页 Thresholds.vue (列表)          │
                      │  ├─ 空状态（THR-07）← alertable+recommended │
                      │  └─ 添加/编辑 → ThresholdWizard.vue (D-01) │
                      └──────────────┬───────────────────────────┘
                                     │ ElSteps 三步
             ┌───────────────────────┼────────────────────────────┐
             ▼                       ▼                            ▼
    ┌────────────────┐    ┌───────────────────────┐    ┌──────────────────┐
    │ Step1 选指标    │    │ Step2 四值 + ZoneBar   │    │ Step3 确认语句    │
    │ metricMetaStore:│    │ thresholdValidation.ts│    │ 由已填边界生成     │
    │ alertable only  │    │ (纯函数，非权威)        │    │ null 分句省略      │
    └────────┬───────┘    └───────────┬───────────┘    └────────┬─────────┘
             │ 选定→预填推荐值          │ 违反→禁用下一步/保存           │
             │                        │ 两字段内联 + FormErrorSummary │
             ▼                        ▼                              ▼
    ══════════════════ POST/PUT /api/thresholds ══════════════════════════
             │
             ▼
    ┌──────────────────────────────────────────────────────────────────┐
    │ @Valid UserThresholdRequest                                      │
    │  ├─ 字段级：@NotNull / @Size / @DecimalMin（既有）                 │
    │  └─ 类级：@ValidThresholdRange → ThresholdRangeValidator         │
    │       违规 → 两次 addConstraintViolation（各单 propertyNode）      │
    │       每次读 HealthMetricMetaRegistry 取 hardMin/hardMax          │
    └──────────────────────────────┬───────────────────────────────────┘
                                   │ MethodArgumentNotValidException
                                   ▼
    ┌──────────────────────────────────────────────────────────────────┐
    │ GlobalExceptionHandler.handleMethodArgumentNotValid              │
    │  getFieldErrors() + getGlobalErrors() → fieldErrors[]  (HTTP 400) │
    └──────────────────────────────┬───────────────────────────────────┘
                                   │
                                   ▼
    ┌──────────────────────────────────────────────────────────────────┐
    │ UserThresholdServiceImpl.create/update                           │
    │  1) canonical = MetricKeyNormalizer.toCanonicalKey(request.key)  │
    │  2) 若 canonical != request.key → 置回 request（预检查与入库同一值）│
    │  3) 预检查用 aliases IN + selectList(orderByDesc updatedAt)       │
    │  4) 已存在 → BusinessException(RESOURCE_ALREADY_EXISTS, 中文)     │
    │  5) insert/update 落库 standard key                              │
    └──────────────────────────────┬───────────────────────────────────┘
                                   ▼
                        user_threshold (metric_key = 标准 key)

    ═════════════ 告警侧读取（写入后必须命中）═════════════
    ThresholdEvaluator.getThresholdForUser
      → user_threshold: aliases IN + selectList(updatedAt desc) → 首条
      → 未命中 → alert_rule: aliases IN + enabled → 首条
    IntelligentAlertService.getThresholdHigh/Low、RecoveryNotifier、
    TrendPredictor.getThresholdValue 同构改造
                                   ▼
    ThresholdRangeChecker.classify(value, threshold)
      → NORMAL → evaluateValue 返回 null（不告警）
      → WARNING → alertType=WARNING, alertLevel=MEDIUM, direction=isHigh
      → CRITICAL → alertType=CRITICAL, alertLevel=HIGH
```

### Recommended Project Structure

```
hhs-backend/src/main/java/com/hhs/
├── service/domain/
│   ├── ThresholdRangeChecker.java          # [MODIFIED] 抽出 classify()，四方法委托
│   ├── ThresholdEvaluator.java             # [MODIFIED] 别名 IN + selectList
│   ├── HealthMetricMetaRegistry.java       # [MODIFIED] +alertable +recommended 四值
│   └── MetricDefinition.java               # [MODIFIED] record 追加 2 字段
├── service/impl/
│   ├── UserThresholdServiceImpl.java       # [MODIFIED] 双处规范化 + 别名预检查
│   └── AlertRuleEngineImpl.java            # [UNCHANGED] 但受 classify 行为变更影响
├── service/alert/
│   ├── IntelligentAlertService.java        # [MODIFIED] :267,:297 别名 IN
│   ├── RecoveryNotifier.java               # [MODIFIED] :59 别名 IN
│   └── TrendPredictor.java                 # [MODIFIED] :218 别名 IN
├── service/domain/MetricRecommendedThresholdProvider.java  # [NEW] 读 alert_rule 供注册表
├── validation/
│   ├── ValidThresholdRange.java            # [NEW] 类级约束
│   └── ThresholdRangeValidator.java        # [NEW] 注 HealthMetricMetaRegistry
├── dto/UserThresholdRequest.java           # [MODIFIED] 挂 @ValidThresholdRange
└── resources/sql/
    ├── schema.sql                          # [MODIFIED] alert_rule 种子 key 修正
    └── fix-threshold-metric-key.sql        # [NEW] 幂等修复脚本

hhs-backend/src/test/java/com/hhs/
├── service/domain/ThresholdRangeCheckerTest.java       # [NEW] D-18 全覆盖
├── validation/ThresholdRangeValidatorTest.java         # [NEW]
├── service/UserThresholdServiceTest.java               # [MODIFIED] 规范化断言
├── service/domain/MetricKeyNormalizerTest.java         # [EXISTING] 作为别名集合真值源
├── controller/MetricMetadataControllerTest.java        # [MODIFIED] 断言 alertable/recommended
└── integration/ThresholdAliasIntegrationTest.java      # [NEW] Testcontainers：glucose 写入→bloodGlucose 命中

hhs-frontend-v2/src/
├── types/api.ts                            # [MODIFIED] MetricDefinitionVO +2 字段
├── constants/metricMeta.ts                 # [MODIFIED] 23 项补 alertable/recommended
├── stores/metricMeta.ts                    # [MODIFIED] 新增 alertableHealth/Wellness 计算属性
├── utils/thresholdValidation.ts            # [NEW] 纯函数：次序 + null 跳过
├── views/health/Thresholds.vue             # [MODIFIED] 列表 + 空状态 + 接线
├── views/health/components/ThresholdWizard.vue    # [NEW] 三步向导
└── views/health/components/ThresholdZoneBar.vue    # [NEW] 五段色带
```

### Pattern 1: Single-Source Classification（单一分档函数）

**What:** 把四档判定收敛为一个 `classify()`，其余四个公开方法全部委托，杜绝再次分叉。

**When to use:** D-16 强制要求；任何"多个方法各自比较同一组边界"的代码都应先合并再修 bug。

**Example:**

```java
// Source: 本会话读取 ThresholdRangeChecker.java:20-57 + 86-92 私助手的包含式语义
// 计划落位：service/domain/ThresholdRangeChecker.java
public enum Band { CRITICAL_LOW, WARNING_LOW, NORMAL, WARNING_HIGH, CRITICAL_HIGH }

private Band classify(BigDecimal v, UserThreshold t) {
    boolean belowCritLow = isBelowThreshold(v, t.getCriticalLow());   // v <= criticalLow
    boolean belowWarnLow = isBelowThreshold(v, t.getWarningLow());    // v <= warningLow
    boolean aboveWarnHigh = isAboveThreshold(v, t.getWarningHigh());  // v >= warningHigh
    boolean aboveCritHigh = isAboveThreshold(v, t.getCriticalHigh()); // v >= criticalHigh

    if (belowCritLow) return Band.CRITICAL_LOW;
    if (belowWarnLow) return Band.WARNING_LOW;      // 已排除 <= criticalLow
    if (aboveCritHigh) return Band.CRITICAL_HIGH;
    if (aboveWarnHigh) return Band.WARNING_HIGH;    // 已排除 >= criticalHigh
    return Band.NORMAL;
}
```

**为什么这里写"临界优先 + 取反剔除"而不是显式 `criticalLow < v && v <= warningLow`：** 助手的包含式语义已经隐含了取反关系，直接复用可保证 `isWarningRange` 与既有 `isNormalRange` / `isCriticalRange` 在**同一语义基**上互补；显式写严格不等号会引入一套新语义，与 `isNormalRange` 的 `<= warningLow` 产生新的分叉风险。这是本阶段最容易写偏的地方。

### Pattern 2: Alias-IN Read with Deterministic Pick（别名查询 + 确定性取首）

**What:** 任何按 `metric_key` 读阈值/规则的地方，统一走"别名集合 + 排序 + 取首条"。

**When to use:** D-17 要求的所有读取点。

**Example:**

```java
// Source: HealthMetricServiceImpl.java:155 的既有 in() 先例 + MP 3.5.5 字节码语义
private static final Predicate<String> HAS_ALIASES = k -> MetricKeyNormalizer.getAliases(k).size() > 1;

private List<UserThreshold> findByAlias(Long userId, String metricKey) {
    Set<String> aliases = MetricKeyNormalizer.getAliases(metricKey);
    LambdaQueryWrapper<UserThreshold> w = new LambdaQueryWrapper<UserThreshold>()
            .eq(UserThreshold::getUserId, userId)
            .in(UserThreshold::getMetricKey, aliases)              // 一律 IN，单元素集等价 eq
            .orderByDesc(UserThreshold::getUpdatedAt)              // D-14 语义：最新意图优先
            .orderByDesc(UserThreshold::getId);                    // updatedAt 相同时的确定性
    return userThresholdMapper.selectList(w);                      // 绝不用 selectOne
}
```

### Pattern 3: Startup-Merged Recommendation Source（启动期合并推荐值）

**What:** `HealthMetricMetaRegistry` 保持纯内存不可变；新增一个 `@Component` 在 `@PostConstruct` 一次性读 `alert_rule`，把 `alertable` 与四值写成注册表中每个 `MetricDefinition` 的字段；种子缺失时回退 `normalMin`/`normalMax`。

**When to use:** D-03/D-06 的"后端同一响应下发 alertable + 推荐值"，且不能让 `MetricMetadataController` 每次请求查库。

**Example:**

```java
// 计划落位：service/domain/MetricRecommendedThresholdProvider.java
@Component
@RequiredArgsConstructor
public class MetricRecommendedThresholdProvider {
    private final AlertRuleMapper alertRuleMapper;

    /** alert_rule 中有可用种子（enabled 且至少一列非 null）的 key → 四值。 */
    public Map<String, RecommendedThreshold> loadUsable() {
        return alertRuleMapper.selectList(
                new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getEnabled, true))
            .stream()
            .filter(r -> r.getWarningHigh() != null || r.getCriticalHigh() != null
                      || r.getWarningLow() != null || r.getCriticalLow() != null)
            .collect(Collectors.toMap(AlertRule::getMetricKey,
                    r -> new RecommendedThreshold(r.getWarningLow(), r.getWarningHigh(),
                                                  r.getCriticalLow(), r.getCriticalHigh()),
                    (a, b) -> a));
    }
}
```

### Anti-Patterns to Avoid

- **在 `isWarningRange` 里写"看起来对"的显式半开区间** —— 会与未改动的 `isNormalRange` 形成第二套语义基，`warningHigh` 边界归属将再次分叉（这正是本 bug 的成因）。
- **把 `.eq` 机械替换成 `.in` 而保留 `selectOne`** —— 存量并列数据存在时直接抛 `TooManyResultsException`，把"漏报"变成"500"。
- **一次性 `addPropertyNode(a).addPropertyNode(b)`** —— 字段名变成 `a.b`，前端 `fieldErrors` 匹配不到任何 `prop`，D-09 的内联静默失效。
- **把推荐值硬编码进前端** —— D-03/D-05 明令禁止；且会与 `alert_rule` 漂移。
- **在 SQL 脚本里用 `DELETE ... WHERE metric_key IN (SELECT ... FROM user_threshold)`** —— MySQL 8 明确禁止"从同一张表 DELETE 又在其子查询中 SELECT"，必须派生表包裹或临时表。
- **在应用启动时执行修复脚本** —— D-13 明令禁止；且无 Flyway，启动执行会产生不可回滚的隐式副作用。

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 字段错误内联 + 汇总 | 自写错误状态机 | `useFormServerErrors` + `FormErrorSummary`（Phase 11 已交付） | 契约已定，重建必与 105 处调用点不一致 |
| 唯一 toast 出口 | 页面内 `ElMessage.error` | `showError` + `ERROR_CODE_ACTIONS` | 项目有测试断言"`src/utils/` 内只有 `showError.ts` import ElMessage" |
| 指标 key 规范化 | 前端/后端各自写映射表 | 后端 `MetricKeyNormalizer`；前端镜像 `utils/metricNormalizer.ts` | 已有两套**刻意同步**的实现，且前端已有 `metricNormalizer.spec.ts` 锁定 |
| 元数据离线兜底 | 在向导里再写一份 23 项常量 | `useMetricMetaStore` + `FALLBACK_METRIC_META` | store 初始化即深拷贝，零延迟 |
| 生理极限 | `PANIC_LIMITS` 或自定义边界 | 元数据 `hardMin`/`hardMax` | D-11 明确排除 `PANIC_LIMITS`（急危重症 ≠ 物理可达） |
| 重复键去重 SQL | 应用层循环 SELECT/DELETE | 单条 SQL 内的派生表关联 | 幂等、原子、可重复执行 |

**Key insight:** 本阶段几乎全部"该用的东西"都已在仓库里。研究的主要产出不是"选什么库"，而是"既有三套契约（`fieldErrors`、别名集合、元数据 SSOT）的**精确接入方式**"——错误的方式都能编译通过，但运行时不生效。

## Runtime State Inventory

> 本阶段含数据迁移（FIX-04），必须逐类回答。

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| **Stored data** | `user_threshold`：线上 2 行，其中 `glucose`(id=3) 非标准；`alert_rule`：7 行中 `glucose`、`temperature` 非标准，`weight` 四列全 NULL；`alert_template`：`metric_key` 出现 `glucose`、`temperature` | **数据迁移**：`fix-threshold-metric-key.sql` 归一并合并重复；同步修 `schema.sql` 种子供新库 |
| **Live service config** | 无外部服务保存阈值 key。`alert_template.template_key` 内嵌 metric 前缀（如 `glucose.high.critical`），但代码仅按 `metric_key` + `severity_level` 查询（`AlertTemplateMapper.findByMetricKeyAndSeverity`），**不解析 `template_key`** | **不需迁移** `template_key`；仅 `metric_key` 列随脚本更新 |
| **OS-registered state** | 无。阈值不注册到任何 OS 级组件 | None — 已核实无 pm2 / 计划任务 / systemd 相关配置 |
| **Secrets and env vars** | 无。`USER_THRESHOLD` 相关的环境变量不存在；`DEVICE_ENCRYPTION_KEY`、`JWT_SECRET` 等与本能力无关 | None — 改名不触及任何密钥 |
| **Build artifacts / installed packages** | 无。Java 源码编译产物不含 metric key 字面量常量；前端 `dist/` 每次构建重建 | None — 重新构建即可；无需清理 egg-info 之类的残留 |
| **数据库初始化路径** | `docker-compose.yml` 把 `schema.sql` 挂到 `/docker-entrypoint-initdb.d/schema.sql:ro`，**仅在空数据卷首次启动时执行**。已有卷不会重跑 | 部署文档必须说明：老库需手工执行 `fix-threshold-metric-key.sql`；`docker compose down -v` 才会重放 schema |

**The canonical question:** *每个文件都改完后，哪些运行时系统仍缓存/存储/注册着旧字符串？* → 只有两个数据库表（`user_threshold`、`alert_rule`）与一个模板表（`alert_template.metric_key`）。没有 OS 级、密钥级或构建产物级的残留。

## Common Pitfalls

### Pitfall 1: 用"显式半开区间"修 `isWarningRange` 而与 `isNormalRange` 分叉
**What goes wrong:** 写成 `v > criticalLow && v <= warningLow` 看似正确，但 `isNormalRange` 仍用 `v <= warningLow || v >= warningHigh` 判定异常，两套语义基并存；一旦有人再改边界（如把 warning 判定改成 `>`），`isNormalRange` 与 `getSeverityLevel` 再次不一致——这正是本 bug 的成因复现。
**Why it happens:** 修改者只看到 bug 所在的那一个方法。
**How to avoid:** D-16 的 `classify()` 必须让四个方法全部委托；`ThresholdRangeCheckerTest` 断言"`isNormalRange` 与 `classify()==NORMAL` 在所有边界值上一致"。
**Warning signs:** 任何测试里 `isNormalRange(v) == false` 但 `getSeverityLevel(v) == "NORMAL"`。

### Pitfall 2: `selectOne` + 别名 `.in` 抛 `TooManyResultsException`
**What goes wrong:** 用户同时有 `glucose` 与 `bloodGlucose` 两行（UNIQUE 索引是别名盲的，`uk_user_metric` 只看字面 key，`schema.sql:205`）。改为 `.in(aliases)` 后 `selectOne` 命中 2 行 → 抛异常 → 实时录入接口 500，且告警彻底不产生。
**Why it happens:** MyBatis-Plus 的 `selectOne(Wrapper)` 默认 `throwEx=true`（字节码：`size>1` 且 `throwEx` 为真时 `new TooManyResultsException`）。
**How to avoid:** 所有别名查询用 `selectList(...).get(0)` 并显式 `orderByDesc(updatedAt)`；或 `selectOne(wrapper, false)`。
**Warning signs:** 集成测试插入两行同义 key 后接口返回 500；`TooManyResultsException` 出现在日志。

### Pitfall 3: 只规范化入库、不规范化预检查
**What goes wrong:** 用户在向导里选 `bloodGlucose` 创建成功；随后有人直接调 API POST `glucose`。预检查查的是 `glucose`（无命中），入库写 `bloodGlucose` → 撞 `uk_user_metric` 唯一索引 → 抛数据库约束异常 → 落到 `GlobalExceptionHandler` 兜底 500「服务暂时不可用」，用户完全不知道是重复。
**Why it happens:** D-17 的"预检查与入库两处"被实现成只做后者。
**How to avoid:** `create()` 开头一次性 `String canonical = toCanonicalKey(request.getMetricKey()); request.setMetricKey(canonical);`，之后预检查与 `BeanUtils.copyProperties` 天然使用同一值；`update()` 同理且需在 `:84` 的 `equals` 比较前完成。
**Warning signs:** 直接 POST 别名时得到 500 而非「该指标已有阈值」。

### Pitfall 4: SQL 脚本执行顺序颠倒（先跑脚本后发代码）
**What goes wrong:** 旧代码用 `.eq` 精确匹配。脚本把 `glucose` 改成 `bloodGlucose` 后，旧代码查 `glucose` → 查不到 → 用户阈值"消失"（回退到 `alert_rule`，而 `alert_rule` 也被改成 `bloodGlucose`，于是查不到规则 → 完全不告警）。
**Why it happens:** 把数据迁移当作可以在任意时刻执行的独立步骤。
**How to avoid:** **先发布含别名 IN 的代码，再执行脚本**。别名 IN 使新代码在脚本前后都命中，窗口期无缺口。
**Warning signs:** 部署后用户报"我的血糖阈值没了"。

### Pitfall 5: 类级约束的 property node 写法导致内联永远不亮
**What goes wrong:** `context.buildConstraintViolationWithTemplate(msg).addPropertyNode("warningHigh").addPropertyNode("criticalHigh").addConstraintViolation()` 只产生**一个** `ConstraintViolation`，其 propertyPath 为 `warningHigh.criticalHigh`。Spring 的 `SpringValidatorAdapter.determineField` 用 `"."` 连接 PROPERTY 节点 → `FieldError.field = "warningHigh.criticalHigh"` → 前端两个 `prop` 都不匹配 → 零内联，且汇总块显示一个不存在的字段标签。
**Why it happens:** 误以为链式 `addPropertyNode` 是"挂到多个字段"。
**How to avoid:** 调两次 `buildConstraintViolationWithTemplate` + 各自的单节点 + 各自的 `addConstraintViolation()`，产生两个独立 `ConstraintViolation` → 两个 `FieldError` → 两个 `FieldErrorVO` → 两个输入框各自内联。**这是 D-09 能被满足的唯一写法。**
**Warning signs:** 后端返回的 `fieldErrors[].field` 里出现带点的字符串。

### Pitfall 6: DTO 无法访问元数据 → 生理极限未被服务端拦截
**What goes wrong:** `UserThresholdRequest` 是纯 DTO，没有 `hardMin`/`hardMax`。只在前端用 `:min/:max` 拦截，直接调 API 可以写入 300 次/分的心率警告阈值。
**Why it happens:** 忘记 `ConstraintValidator` 支持 Spring 注入，误以为"DTO 拿不到注册表"。
**How to avoid:** `ThresholdRangeValidator` 注入 `HealthMetricMetaRegistry`（既有 `MetricRecordValidator` 就是这样做的：`MetricRecordValidator.java:34` 有 `@Autowired` 构造器 + 无参兜底构造器）。用无参兜底 `new HealthMetricMetaRegistry()` 保证单测可脱离容器。
**Warning signs:** 后端的 `UserThresholdRequestValidationTest` 无法覆盖 hardMin/hardMax。

### Pitfall 7: 空状态的"6 项默认规则"自造数据
**What goes wrong:** 前端为了空状态另写一份默认规则常量 → 与 `alert_rule` 漂移 → 用户看到"心率 100/120"，实际种子是别的值。
**Why it happens:** 空状态被认为"只是静态文案"。
**How to avoid:** 空状态读**同一个** `metricMetaStore` 的 `alertable` + `recommended`（即后端同一响应），零额外请求、零第二份真值。
**Warning signs:** 改了 `schema.sql` 种子而 UI 没变。

### Pitfall 8: 前端 `ErrorLayerSample.spec.ts` 断言的是"字面上存在的指标"
**What goes wrong:** 现有测试把 `EXISTING_THRESHOLD.metricKey = 'glucose'` 硬塞进 `thresholds` 列表，并设 `vm.form.metricKey = 'glucose'`，断言点击「打开编辑」后 `vm.editingId === 3`。若新实现改成"按规范化后比较"，`openExistingThreshold` 必须能匹配到 `glucose` 行，否则用例 6/7 失败。
**Why it happens:** 该 spec 是 Phase 11 的端到端契约，不属本阶段可改范围（改它等于削弱既有契约）。
**How to avoid:** `handleSubmit` 的 `thresholds.value.find(...)` 改为 `t => toCanonicalKey(t.metricKey) === toCanonicalKey(form.metricKey)`——既满足新语义又让老用例继续通过（`glucose` 与 `glucose` 规范化后仍相等）。
**Warning signs:** `npm run test:run` 中 `ErrorLayerSample.spec.ts` 用例 6/7 变红。

### Pitfall 9: 修复 `classify()` 会新增大量真实告警
**What goes wrong:** 修前 WARNING 永不触发；修后所有处于警告带的记录开始产生告警与推送（`PushStrategyService` 对 `MEDIUM` 会走 WebSocket + Email）。生产环境用户可能被突然涌入的邮件淹没。
**Why it happens:** 这是"漏报变正常上报"的行为变更，不是纯 bug fix。
**How to avoid:** STATE.md 已记录"需 UAT 条目"。计划中必须有显式 UAT 项：检查存量 `user_threshold` 的边界次序分布是否合法（`criticalLow < warningLow < warningHigh < criticalHigh`），违规行会产生不可预期的分档。
**Warning signs:** 部署后告警量突增；`email` 通道被启用且用户无预期。

### Pitfall 10: `weight` 行看起来"可告警"但四值全 NULL
**What goes wrong:** 若 `alertable` 仅按"`alert_rule` 有行"判定，`weight` 会进入选择器，用户选它 → 推荐值全 null → 四项空 → 校验拒绝"四项全空" → 用户卡死。
**Why it happens:** 未考虑 `schema.sql:521` 的 `('weight', NULL, NULL, NULL, NULL, ...)`。
**How to avoid:** `alertable` 判定必须要求"至少一个阈值非 null"（见 Pattern 3 的 `loadUsable()`）。`weight` 本身在 `alert_rule` 描述里写明"dynamically assessed based on height"，本就不该给静态阈值。

## Code Examples

### 阈值合法性纯函数（前端，非权威）

```ts
// Source: 契约来自 UI-SPEC Component Contract 3 的校验文案表 + D-10 的 null 语义
// 计划落位：hhs-frontend-v2/src/utils/thresholdValidation.ts
export interface ThresholdDraft {
  criticalLow?: number | null
  warningLow?: number | null
  warningHigh?: number | null
  criticalHigh?: number | null
}

export interface PairViolation {
  fields: [keyof ThresholdDraft, keyof ThresholdDraft]
  message: string
}

/** D-10：null = 无界；仅当相邻两侧均非 null 时比较。 */
export function validateThresholdOrder(d: ThresholdDraft): PairViolation[] {
  const out: PairViolation[] = []
  const pairs: Array<[keyof ThresholdDraft, keyof ThresholdDraft, string]> = [
    ['criticalLow', 'warningLow', '严重低值必须小于警告低值，请调整其中一个'],
    ['warningLow', 'warningHigh', '警告低值必须小于警告高值，请调整其中一个'],
    ['warningHigh', 'criticalHigh', '警告高值必须小于严重高值，请调整其中一个']
  ]
  for (const [lo, hi, message] of pairs) {
    const a = d[lo], b = d[hi]
    if (a == null || b == null) continue
    if (Number(a) >= Number(b)) out.push({ fields: [lo, hi], message })
  }
  return out
}

/** 四项全 null 不允许提交（UI-SPEC partial 行：禁用下一步/保存）。 */
export function hasAnyBoundary(d: ThresholdDraft): boolean {
  return [d.criticalLow, d.warningLow, d.warningHigh, d.criticalHigh]
    .some(v => v !== null && v !== undefined)
}
```

> 注意与 ROADMAP 措辞的关系：ROADMAP/UI-SPEC 用 `criticalLow < warningLow < warningHigh < criticalHigh`（严格小于），而阈值判定用的是包含式边界（`v <= warningLow` 属警告）。两者**不矛盾**：严格次序保证"警告带宽度 > 0"，包含式边界决定"等于边界时归属哪一档"。校验用严格、判定用包含，必须同时成立。

### 类级约束（两个 property node，两次 violation）

```java
// Source: 字节码核实 SpringValidatorAdapter.determineField 的点连接语义
// + 既有先例 MetricRecordValidator.java:104-106 的 buildConstraintViolationWithTemplate 用法
// 计划落位：hhs-backend/src/main/java/com/hhs/validation/ThresholdRangeValidator.java
@Component
public class ThresholdRangeValidator implements ConstraintValidator<ValidThresholdRange, UserThresholdRequest> {

    private final HealthMetricMetaRegistry registry;

    public ThresholdRangeValidator() { this(new HealthMetricMetaRegistry()); }

    @Autowired
    public ThresholdRangeValidator(HealthMetricMetaRegistry registry) {
        this.registry = registry != null ? registry : new HealthMetricMetaRegistry();
    }

    @Override
    public boolean isValid(UserThresholdRequest r, ConstraintValidatorContext ctx) {
        if (r == null) return true;                     // null 由 @NotNull 处理
        ctx.disableDefaultConstraintViolation();
        boolean ok = true;

        // 1) 相邻次序：三对，各失败时给两个字段各挂一条
        ok &= checkPair(ctx, r.getCriticalLow(),  r.getWarningLow(),  "criticalLow",  "warningLow",
                        "严重低值必须小于警告低值，请调整其中一个") & ok;
        ok &= checkPair(ctx, r.getWarningLow(),   r.getWarningHigh(), "warningLow",   "warningHigh",
                        "警告低值必须小于警告高值，请调整其中一个") & ok;
        ok &= checkPair(ctx, r.getWarningHigh(),  r.getCriticalHigh(),"warningHigh",  "criticalHigh",
                        "警告高值必须小于严重高值，请调整其中一个") & ok;

        // 2) 生理极限：取元数据 hardMin/hardMax（D-11）
        registry.getDefinition(r.getMetricKey()).ifPresent(def -> { /* 逐字段比较，越界则双节点/或单节点 */ });

        return ok;
    }

    /** 一个违规 → 两条独立 ConstraintViolation，各自单 propertyNode。 */
    private boolean checkPair(ConstraintValidatorContext ctx, BigDecimal lo, BigDecimal hi,
                              String loField, String hiField, String message) {
        if (lo == null || hi == null) return true;                        // null = 无界
        if (lo.compareTo(hi) < 0) return true;                           // 严格小于
        addViolation(ctx, loField, message);
        addViolation(ctx, hiField, message);
        return false;
    }

    private void addViolation(ConstraintValidatorContext ctx, String field, String message) {
        ctx.buildConstraintViolationWithTemplate(message)
           .addPropertyNode(field)      // 只挂一个节点
           .addConstraintViolation();   // 一次 violation → 一个 FieldError
    }
}
```

### 幂等 SQL 修复（MySQL 8 idiom）

```sql
-- Source: MySQL 8.0 Reference Manual §15.2.2（"You cannot delete from a table and
--         select from the same table in a subquery."）+ schema.sql:205 的 uk_user_metric
-- 计划落位：hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql

-- ============ Step 0：执行前计数（人工记录） ============
SELECT 'BEFORE' AS phase,
       (SELECT COUNT(*) FROM user_threshold) AS ut_total,
       (SELECT COUNT(*) FROM user_threshold WHERE metric_key NOT IN
          ('heartRate','systolicBP','diastolicBP','bloodGlucose','fastingGlucose',
           'postprandialGlucose','bodyTemperature','weight','bmi','bloodOxygen',
           'totalCholesterol','triglycerides','hdlCholesterol','ldlCholesterol',
           'uricAcid','creatinine','sleepDuration','sleepQuality','steps',
           'exerciseMinutes','waterIntake','mood','energy')) AS ut_noncanonical,
       (SELECT COUNT(*) FROM alert_rule WHERE metric_key IN ('glucose','temperature')) AS ar_noncanonical;

START TRANSACTION;

-- ============ Step 1：user_threshold —— 先删后改（UNIQUE uk_user_metric 会阻塞） ============
-- 1a. 删除"同 user 已存在标准 key 行"的非标准行（派生表绕行 MySQL 同表子查询禁令）
DELETE ut FROM user_threshold ut
JOIN (
    SELECT u.id
    FROM user_threshold u
    JOIN user_threshold c
      ON c.user_id = u.user_id
     AND c.metric_key = CASE u.metric_key
           WHEN 'glucose' THEN 'bloodGlucose'
           WHEN 'GLUCOSE' THEN 'bloodGlucose'
           WHEN 'blood_glucose' THEN 'bloodGlucose'
           WHEN 'temperature' THEN 'bodyTemperature'
           WHEN 'TEMP' THEN 'bodyTemperature'
           WHEN 'temp' THEN 'bodyTemperature'
           WHEN 'spo2' THEN 'bloodOxygen'
           WHEN 'SPO2' THEN 'bloodOxygen'
           ELSE NULL END
    WHERE u.metric_key IN ('glucose','GLUCOSE','blood_glucose',
                           'temperature','TEMP','temp','spo2','SPO2')
) AS dup ON dup.id = ut.id;

-- 1b. 剩余非标准行就地改名
UPDATE user_threshold SET metric_key = CASE metric_key
    WHEN 'glucose'       THEN 'bloodGlucose'
    WHEN 'GLUCOSE'       THEN 'bloodGlucose'
    WHEN 'blood_glucose' THEN 'bloodGlucose'
    WHEN 'temperature'   THEN 'bodyTemperature'
    WHEN 'TEMP'          THEN 'bodyTemperature'
    WHEN 'temp'          THEN 'bodyTemperature'
    WHEN 'spo2'          THEN 'bloodOxygen'
    WHEN 'SPO2'          THEN 'bloodOxygen'
    ELSE metric_key
END
WHERE metric_key IN ('glucose','GLUCOSE','blood_glucose',
                     'temperature','TEMP','temp','spo2','SPO2');

-- ============ Step 2：alert_rule —— 同样先删后改（UNIQUE uk_metric） ============
DELETE ar FROM alert_rule ar
JOIN alert_rule c
  ON c.metric_key = CASE ar.metric_key
        WHEN 'glucose' THEN 'bloodGlucose'
        WHEN 'temperature' THEN 'bodyTemperature'
        ELSE NULL END
WHERE ar.metric_key IN ('glucose','temperature');

UPDATE alert_rule SET metric_key = CASE metric_key
    WHEN 'glucose'     THEN 'bloodGlucose'
    WHEN 'temperature' THEN 'bodyTemperature'
    ELSE metric_key
END
WHERE metric_key IN ('glucose','temperature');

-- ============ Step 3：alert_template 与 health_alert（无唯一键，可直接改） ============
UPDATE alert_template SET metric_key = 'bloodGlucose'     WHERE metric_key = 'glucose';
UPDATE alert_template SET metric_key = 'bodyTemperature' WHERE metric_key = 'temperature';
UPDATE health_alert   SET metric_key = 'bloodGlucose'     WHERE metric_key = 'glucose';
UPDATE health_alert   SET metric_key = 'bodyTemperature' WHERE metric_key = 'temperature';

COMMIT;

-- ============ Step 4：执行后计数（必须全为 0，且 ut_total 只减不增） ============
SELECT 'AFTER' AS phase,
       (SELECT COUNT(*) FROM user_threshold) AS ut_total,
       (SELECT COUNT(*) FROM user_threshold WHERE metric_key IN
          ('glucose','temperature','spo2','TEMP','GLUCOSE')) AS ut_noncanonical,
       (SELECT COUNT(*) FROM alert_rule WHERE metric_key IN ('glucose','temperature')) AS ar_noncanonical;
```

**要点说明（每条都有依据）：**

1. **为什么必须先删后改而不是直接 UPDATE：** `user_threshold` 有 `UNIQUE KEY uk_user_metric (user_id, metric_key)`（`schema.sql:205`），`alert_rule` 有 `UNIQUE KEY uk_metric (metric_key)`（`schema.sql:190`）。若同一 user 已有 `bloodGlucose` 行，直接 UPDATE `glucose` → `bloodGlucose` 会在语句中途抛 `Duplicate entry`，整个语句回滚。**必须先删除将冲突的行再改名**。
2. **为什么用 `DELETE ... JOIN (派生表)`：** MySQL 8 手册明确「不能从一张表 DELETE 又在同一语句的子查询里 SELECT 同一张表」。`JOIN (SELECT ... FROM user_threshold ...) AS dup` 是派生表，物化后不受该禁令约束（既有 `v_latest_metrics_summary` 视图 `schema.sql:462-470` 已用同样的 `INNER JOIN (SELECT ... )` 手法）。这是比 `CREATE TEMPORARY TABLE` 更简洁的等价绕行。
3. **为什么不用 `ROW_NUMBER()`：** 本脚本的"保留哪一条"不由 `updated_at` 排序决定，而由"标准 key 行是否已存在"决定（D-14 只规定"重复时保留 updated_at 最新"，而不规定"是否该有两条"）。用 JOIN 语义更贴合。若将来需要按 `updated_at` 精选，MySQL 8 支持 `ROW_NUMBER() OVER (PARTITION BY user_id, canonical ORDER BY updated_at DESC)`，但同样必须走派生表。
4. **为什么整段包在 `START TRANSACTION`/`COMMIT`：** 删除是破坏性操作，全部成功或全部回滚；服务端脚本可重复执行（归一再跑时 `WHERE ... IN ('glucose', ...)` 已无匹配行，DELETE JOIN 也无匹配）。
5. **可重复执行性：** 所有 UPDATE 都带 `WHERE metric_key IN (<非标准集合>)`；第二次执行时集合为空 → 0 行受影响。所有 DELETE JOIN 在归一后无匹配 → 0 行。Step 0/Step 4 的计数查询可直接对比。

**执行顺序约束（关键）：** 脚本必须在**含别名 IN 的新代码上线之后**执行。旧代码 `.eq` 精确匹配，脚本跑完后用户阈值会"查不到"（Pitfall 4）。新代码别名 IN 使脚本前后都命中，因此顺序是 `代码发布 → 脚本执行`，而不是反过来。

### 前端类型增补

```ts
// Source: types/api.ts:435-449 的既有 MetricDefinitionVO
// 计划落位：hhs-frontend-v2/src/types/api.ts
export interface RecommendedThresholdVO {
  warningHigh?: number | null
  criticalHigh?: number | null
  warningLow?: number | null
  criticalLow?: number | null
}

export interface MetricDefinitionVO {
  canonicalKey: string
  aliases: string[]
  displayName: string
  category: 'HEALTH' | 'WELLNESS'
  unit: string
  precision: number
  step: number
  hardMin: number
  hardMax: number
  normalMin: number
  normalMax: number
  placeholder: string
  guideHint: string
  /** 新增：告警引擎可评估（alert_rule 有可用种子） */
  alertable: boolean
  /** 新增：推荐四值（后端派生，前端不计算） */
  recommended: RecommendedThresholdVO
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `isWarningRange` 内联反向条件 | 单一 `classify()` + 四方法委托 | 本阶段（FIX-01/D-16） | 告警从"永不 WARNING"变为正常上报 |
| 阈值写入不规范化 | `toCanonicalKey` 双处调用 | 本阶段（FIX-02/D-17） | 向导保存与 API 保存落库一致 |
| `metric_key` 精确 `.eq` | 别名集合 `.in` + 确定性取首 | 本阶段（FIX-03/D-17） | 兼容未迁移的历史行 |
| v1.1 迁移脚本（`sql/migration/v1.1_normalize_metric_keys.sql`） | 新增 `fix-threshold-metric-key.sql` | 本阶段（FIX-04） | 注意：**v1.1 脚本已存在但从未覆盖 `glucose`/`temperature` 在 `user_threshold`/`alert_rule` 的完整别名集合**，且其 UPDATE 未先删重复行——若在生产执行过会在 `uk_user_metric` 上失败或部分成功。新脚本必须能同时修复 v1.1 遗留状态 |
| 6 项硬编码 `el-option` | 元数据 `alertable` 驱动 | 本阶段（THR-01/D-03） | 选择器与告警引擎能力对齐 |

**Deprecated/outdated:**
- `Thresholds.vue` 的硬编码 `<el-option value="glucose">` / `value="temperature"` —— 非标准 key，本阶段删除。
- `schema.sql:518,520` 的 `glucose` / `temperature` 种子 —— 本阶段改为 `bloodGlucose` / `bodyTemperature`。
- `UserThresholdServiceImpl:51,87` 的「该指标已存在阈值，请直接编辑」—— 改为「该指标已有阈值，是否改为编辑？」（D-08 文案）。

> 关于既有 v1.1 迁移脚本的重要发现：`v1.1_normalize_metric_keys.sql:96-112` 的 `user_threshold` 部分已用 `DELETE ... WHERE user_id IN (SELECT ut.user_id FROM (SELECT ...) ut)` 派生表手法，但**别名集合不完整**（缺 `temp`/`TEMP`/`spo2`/`SPO2`），且 `glucose` 那一段（:105-106）只列了 `('blood_glucose','glucose','GLUCOSE')`——不含 `glu`/`GLU`。新脚本应使用 `MetricKeyNormalizer` 的**完整**别名集合，避免二次遗漏。

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `alertable` 应定义为「`alert_rule` 有 enabled 且至少一列非 null 的行」= 6 项（含 BMI，排除 weight 与 bloodOxygen） | §D | 若产品希望 bloodOxygen 也可配置，则需为其补 `alert_rule` 种子；否则血氧用户永久无法在向导配置。UI-SPEC 空状态 6 项文案是当前唯一书面依据，未经用户逐条确认 |
| A2 | bloodOxygen 现有 `user_threshold`（id=4）在 `alertable=false` 后**保留在列表中**（只从向导选项移除） | §D | 若产品要求删除，则需额外数据清理；保守做法（保留）不会丢失用户数据 |
| A3 | 修复后 WARNING 会产生真实推送（MEDIUM → WebSocket + Email），需 UAT 确认生产用户可接受 | §H / Pitfall 9 | 若用户无 Email 配置则仅 WebSocket；若已配置可能产生邮件洪水。需要用户确认是否在本阶段一并限流 |
| A4 | 推荐值取自 `alert_rule` 种子的四列原值，不做任何换算（如 mmol/L 与 mg/dL 不换算） | §D | 元数据单位与种子单位若不一致会产生错误推荐值——已核对：血糖种子 7.0/11.1/3.9/3.0 为 mmol/L，与元数据 `unit: 'mmol/L'` 一致 |
| A5 | 向导拆为 `ThresholdWizard.vue` + `ThresholdZoneBar.vue` 两个新组件，`Thresholds.vue` 保持列表职责 | §G | 若坚持单文件，按 UI-SPEC 全部 CSS 契约估算将远超 CLAUDE.md 的 800 行上限（详见 §G 估算） |
| A6 | `user_threshold` 与 `alert_rule` 的规范化可在**同一事务**内完成，无需停机 | §C | 若线上并发写入，需注意 `uk_user_metric` 的锁竞争；本阶段数据量极小（2 行），风险可忽略 |

**如本表为空则无需用户确认。此处有 6 条，均需在计划前与用户对齐，尤其 A1 / A2 / A3。**

## Open Questions

1. **`alertable` 的确切定义与 bloodOxygen 的归属**
   - What we know: `alert_rule` 有 7 行，`weight` 四列全 NULL，故可用者 6 项；UI-SPEC 空状态恰好列 6 项同一集合。
   - What's unclear: bloodOxygen 已有用户阈值行（id=4）却无 `alert_rule` 种子。它是"引擎能评估但没有默认值"还是"引擎不应评估"？
   - Recommendation: 按 A1 取"有可用种子"定义 → `alertable=false`，并保留其用户行。计划中列为显式确认点。

2. **`weight` 是否需要 `alertable`**
   - What we know: 种子描述写"dynamically assessed based on height"，四值全 NULL。
   - What's unclear: 是否希望用 `normalMin/normalMax`（40–120 kg）作为 weight 的推荐值，使 weight 可配置。
   - Recommendation: **不**纳入（A1）。为 weight 提供静态体重阈值与"基于身高的动态评估"语义冲突。

3. **v1.1 迁移脚本是否已在任何环境执行过**
   - What we know: 文件存在（`sql/migration/v1.1_normalize_metric_keys.sql`），且 `docs/` 中没有引用它的部署步骤；`ECS-DEPLOYMENT.md:161` 只提到 `schema.sql`。
   - What's unclear: 生产是否处于"部分归一化"状态。
   - Recommendation: 新脚本的 Step 0 前后计数即为诊断手段；脚本按完整别名集合编写，可幂等覆盖任何中间状态。

4. **修复后是否需要对存量告警量做限流/缓冲**
   - What we know: `PushStrategyService:36-50` 对 MEDIUM 走 WebSocket + Email。
   - What's unclear: 生产用户是否配置了 Email 通道。
   - Recommendation: 计划中列为 UAT 观察项；若无 Email 配置则影响为零。

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | 后端编译/测试 | ✓ | 17.0.1 (Oracle JDK) | — |
| Maven | 后端构建/测试 | ✓ | 3.8.4 | — |
| Node.js | 前端构建/测试 | ✓ | v24.15.0 | — |
| npm | 前端依赖 | ✓ | 11.12.1 | — |
| Docker daemon | 集成测试（Testcontainers MySQL）；本地 MySQL 8 | ✓ | 29.8.1（daemon running） | 无 Docker 时集成测试无法运行，改用单元测试 + 手工 UAT |
| MySQL 8.0 | FIX-04 脚本验证 | ✓（经 docker-compose `mysql:8.0`） | 8.0 | 若无实例，脚本只能做静态评审 |

**Missing dependencies with no fallback:** none
**Missing dependencies with fallback:** Docker（若不存在则跳过 Testcontainers 集成测试，其覆盖的"别名写入→告警命中"改由手工 UAT 兜底）

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework (backend) | JUnit 5 + Mockito + AssertJ（`spring-boot-starter-test`）；Surefire 3.0.0；集成测试 Failsafe 3.0.0 + Testcontainers 1.19.3 |
| Framework (frontend) | Vitest 4.1.0 + @vue/test-utils 2.4.6（`vitest.config.ts`：`environment: 'happy-dom'`, `globals: true`） |
| Config file | `hhs-backend/pom.xml:206-231`（surefire/failsafe 排除与包含规则）；`hhs-frontend-v2/vitest.config.ts` |
| Quick run command (backend, 单类) | `mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest` |
| Quick run command (frontend, 单 spec) | `npx vitest run src/__tests__/views/ThresholdWizard.spec.ts`（在 `hhs-frontend-v2/` 下） |
| Full suite command (backend, 排除集成) | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` |
| Full suite command (frontend) | `npm run test:run`（在 `hhs-frontend-v2/` 下） |

> Surefire 排除 `**/*IntegrationTest.java` 与 `**/*PerformanceTest.java`（`pom.xml:211-214`），因此 `-DskipITs` 与单纯 `mvn test` 等价；集成测试只有在 Docker 可用时由 Failsafe 以 `-Pintegration` 风格组跑。`*IntegrationTest` 命名**不会**被 surefire 执行——新的别名集成测试必须用该命名或 `@Tag("integration")`。

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| FIX-01 | 四档判定正确，`isNormalRange` 与 `classify` 互补 | unit | `mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest` | ❌ Wave 0（**该类当前不存在**） |
| FIX-02 | `glucose` 写入落库为 `bloodGlucose`；别名撞已有行时给业务错误而非 500 | unit + integration | `… -Dtest=UserThresholdServiceTest`；`… -Dtest=ThresholdAliasIntegrationTest`（需 Docker） | ⚠️ `UserThresholdServiceTest` 存在（需扩展），集成测试 ❌ Wave 0 |
| FIX-03 | 存量 `glucose` 行经 `bloodGlucose` 查询可命中 | unit（mock wrapper 断言 `.in`）+ integration | `… -Dtest=ThresholdEvaluatorTest`（新建）；集成同上 | ❌ Wave 0（`ThresholdEvaluator` 当前无任何测试） |
| FIX-04 | 脚本幂等、重复行合并且保留最新 | manual UAT + SQL 断言 | `docker compose exec -T mysql mysql -u root -p"$DB_PASSWORD" hhs < hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql` 然后比对 Step 0/Step 4 计数 | ❌ Wave 0（脚本新建） |
| FIX-05 | D-18 全覆盖：正常/警告双侧/危急双侧/单侧 null/全 null/恰等边界 | unit | `mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest` | ❌ Wave 0 |
| THR-01 | 选择器只列 alertable、按 category 分组、文案 `显示名 (单位)` | unit（组件） | `npx vitest run src/__tests__/views/ThresholdWizard.spec.ts` | ❌ Wave 0 |
| THR-02 | 选定后四项预填推荐值 + 「推荐」徽标 + 「恢复推荐值」 | unit（组件） | 同上 | ❌ Wave 0 |
| THR-03 | 四项说明文案含单位插值 | unit（组件） | 同上 | ❌ Wave 0 |
| THR-04 | 四区条随输入实时更新；null 显示 `—`；`aria-label` 完整 | unit（组件） | `npx vitest run src/__tests__/components/ThresholdZoneBar.spec.ts` | ❌ Wave 0 |
| THR-05 | 前端纯函数拦截 + 禁用保存；后端 400 + 两条 `fieldErrors` | unit ×2 + integration | `npx vitest run src/__tests__/utils/thresholdValidation.spec.ts`；`mvn … -Dtest=ThresholdRangeValidatorTest` | ❌ Wave 0 ×2 |
| THR-06 | 确认语句仅含已填边界；全 null 降级 | unit（组件） | `npx vitest run src/__tests__/views/ThresholdWizard.spec.ts` | ❌ Wave 0 |
| THR-07 | 空状态展示默认规则（6 项）+ 「使用推荐阈值」CTA | unit（组件） | `npx vitest run src/__tests__/views/Thresholds.spec.ts` | ⚠️ 现有 `ErrorLayerSample.spec.ts` 已挂载 `Thresholds.vue`，可直接扩展 |
| THR-08 | 重复保存给 1 条 toast + 「打开编辑」；列表缺失时退化为字符串 toast | unit（组件） | `npx vitest run src/__tests__/views/ErrorLayerSample.spec.ts` | ✅ 用例 6/7/8 已存在（**必须保持通过**） |
| 元数据 | `GET /api/metrics/metadata` 返回 `alertable` 与 `recommended` | unit（MVC slice） | `mvn … -Dtest=MetricMetadataControllerTest` | ⚠️ 存在，需扩展 jsonPath 断言 |
| 元数据完整性 | 每个 `MetricDefinition` 的 `alertable` 与 `recommended` 自洽（alertable=true ⟹ 至少一值非 null；推荐值落在 hardMin/hardMax 内） | unit | `mvn … -Dtest=MetricMetadataRegistryTest` | ⚠️ 存在，需扩展 |

### 预期失败信号（用于确认测试真的在测东西）

每个新测试在实现前必须先看到**具体**失败，而不是编译失败：

| Test | 先写测试后的预期失败信息 |
|------|--------------------------|
| `ThresholdRangeCheckerTest.warningHighBoundary_isWarning` | `expected: <WARNING> but was: <NORMAL>`（复现 FIX-01 的当前状态） |
| `UserThresholdServiceTest.create_glucoseAlias_persistsCanonicalKey` | `expected: "bloodGlucose" but was: "glucose"` |
| `ThresholdEvaluatorTest.getThresholdForUser_aliasRow_hits` | mock `selectList` 未收到 `.in`，返回空 → 断言 `expected not null` 失败 |
| `ThresholdRangeValidatorTest.warningLowAboveWarningHigh_twoViolations` | `expected size 2 but was 0`（约束未挂到 DTO 时） |
| `ThresholdWizard.spec.ts` 选择器 | `expected 6 options, found 0` 或裸值 `glucose` 出现 |
| `ThresholdZoneBar.spec.ts` | 渲染出 4 段而非 5 段，或 null 渲染为 `undefined` |
| `thresholdValidation.spec.ts` | 纯函数不存在 → import 失败（Wave 0 必须先建文件再断言） |
| `MetricMetadataControllerTest` | `jsonPath $.data.heartRate.alertable` 不存在 |

### Sampling Rate

- **Per task commit:** 该任务所属语言的单类/单 spec 命令（上表 Quick run）
- **Per wave merge:** `mvn -f hhs-backend/pom.xml clean verify -DskipITs` + `npm run test:run`
- **Phase gate:** 后端全量单测 + 前端全量 + `mvn checkstyle:check` + `npm run lint:check` + `npm run build`（`build` 内含 `vue-tsc -b`，类型错误会在此暴露）全部通过，再进入 `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java` — FIX-01 / FIX-05（D-18 六类用例）
- [ ] `hhs-backend/src/test/java/com/hhs/service/domain/ThresholdEvaluatorTest.java` — FIX-03（别名 IN + 确定性取首）
- [ ] `hhs-backend/src/test/java/com/hhs/validation/ThresholdRangeValidatorTest.java` — THR-05 后端（断言一次违规 → **两条** violation，字段名无点）
- [ ] `hhs-backend/src/test/java/com/hhs/integration/ThresholdAliasIntegrationTest.java` — FIX-02/FIX-03 端到端（需 Docker）
- [ ] `hhs-frontend-v2/src/__tests__/utils/thresholdValidation.spec.ts` — THR-05 前端纯函数
- [ ] `hhs-frontend-v2/src/__tests__/components/ThresholdZoneBar.spec.ts` — THR-04
- [ ] `hhs-frontend-v2/src/__tests__/views/ThresholdWizard.spec.ts` — THR-01/02/03/06
- [ ] `hhs-frontend-v2/src/__tests__/views/Thresholds.spec.ts` — THR-07（或扩展 `ErrorLayerSample.spec.ts`）
- [ ] 扩展 `UserThresholdServiceTest`（FIX-02 规范化断言）、`MetricMetadataControllerTest` / `MetricMetadataRegistryTest`（alertable + recommended）
- [ ] 框架安装：无需 —— 两端测试基建均已就绪

## Security Domain

> `workflow.security_enforcement` 未在 `.planning/config.json` 中显式关闭（该键不存在即视为启用），故本节必填。

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | 阈值端点已由 `SecurityConfig` 保护；本阶段不改鉴权 |
| V3 Session Management | no | 无会话变更 |
| V4 Access Control | **yes** | `UserThresholdController.enforceOwnership`（:83-88）已存在；`UserThresholdServiceImpl.update:78` / `delete:113` 校验 `threshold.getUserId().equals(userId)`。**本阶段新增的别名查询不得绕开这些校验**（`.in(aliases)` 仍须带 `.eq(userId)`） |
| V5 Input Validation | **yes** | 字段级 `@NotNull`/`@Size`/`@DecimalMin` + 新增类级 `@ValidThresholdRange`；生理极限由 `HealthMetricMetaRegistry` 强制。前端校验仅为 UX 前置，不作安全边界 |
| V6 Cryptography | no | 本阶段无加密操作 |

### Known Threat Patterns for {Spring Boot + Vue + MyBatis-Plus}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR：改 `userId` 参数读/改他人阈值 | Elevation of Privilege | 服务端一律用 `SecurityUtils.getCurrentUserId()` 并校验行归属（既有实现已正确，本阶段不得回退） |
| 通过 `metricKey` 注入异常 key 制造死配置 | Tampering | 写入路径 `toCanonicalKey` 规范化；未知 key 由注册表 `contains` 判定；向导只允许 `alertable` 项 |
| 别名撞库导致唯一约束异常暴露 500 | Denial of Service（信息暴露） | 预检查在规范化后进行，返回 `RESOURCE_ALREADY_EXISTS` 业务错误而非数据库异常 |
| 类级约束消息含用户可控内容 | Tampering / XSS | 消息全部为**静态中文模板**，不插值用户输入；`showError` 用文本子节点（`showError.ts:199`），不走 `dangerouslyUseHTMLString` |
| 直接 POST 绕过前端写入越界阈值 | Tampering | 后端类级约束为权威（D-09），前端 `:min/:max` 仅为便利 |
| 推荐徽标 / 内联错误用的 HTML 注入 | XSS | 全部走 Vue 模板插值（自动转义）；禁止 `v-html` |

## Sources

### Primary (HIGH confidence)

- 本会话逐行读取（第一手代码）：
  - `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java:20-92`
  - `hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java:23-181`
  - `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdEvaluator.java:39-169`
  - `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdComparator.java:24-80`
  - `hhs-backend/src/main/java/com/hhs/service/impl/UserThresholdServiceImpl.java:31-135`
  - `hhs-backend/src/main/java/com/hhs/dto/UserThresholdRequest.java:12-33`
  - `hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java:24-118`
  - `hhs-backend/src/main/java/com/hhs/validation/ValidMetricRecord.java:17-28`
  - `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:108-127, 294-309`
  - `hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java:35-458`
  - `hhs-backend/src/main/java/com/hhs/service/domain/MetricDefinition.java:26-45`
  - `hhs-backend/src/main/java/com/hhs/controller/MetricMetadataController.java:36-43`
  - `hhs-backend/src/main/resources/sql/schema.sql:179-208, 244-260, 513-527`
  - `hhs-backend/src/main/resources/sql/migration/v1.1_normalize_metric_keys.sql:1-143`
  - `hhs-backend/src/main/java/com/hhs/service/alert/{IntelligentAlertService,RecoveryNotifier,TrendPredictor}.java`（各 `metric_key` 查询处）
  - `hhs-frontend-v2/src/views/health/Thresholds.vue:1-301`
  - `hhs-frontend-v2/src/stores/metricMeta.ts:1-96`
  - `hhs-frontend-v2/src/constants/metricMeta.ts:1-372`
  - `hhs-frontend-v2/src/utils/{showError,errorMessages,metricValidation,metricNormalizer}.ts`
  - `hhs-frontend-v2/src/composables/useFormServerErrors.ts:1-157`
  - `hhs-frontend-v2/src/types/api.ts:150-169, 435-451`
  - `hhs-frontend-v2/src/assets/styles/theme.css`（token 与 `!important` 覆盖核实）
  - `hhs-backend/src/test/java/com/hhs/{exception/GlobalExceptionHandlerTest,validation/UserThresholdRequestValidationTest,service/UserThresholdServiceTest,controller/MetricMetadataControllerTest}.java`
  - `hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts:223-404`
- 字节码核实（第一手）：
  - `mybatis-plus-core-3.5.5.jar` → `BaseMapper.selectOne(Wrapper)` 默认 `throwEx=true`，size>1 抛 `TooManyResultsException`
  - `spring-context-6.0.0.jar` → `SpringValidatorAdapter.determineField` 以 `"."` 连接 PROPERTY 节点；`processConstraintViolations` 每个 `ConstraintViolation` 产出一个 `FieldError`
- 官方文档（CITED）：
  - `https://dev.mysql.com/doc/refman/8.0/en/delete.html` —「You cannot delete from a table and select from the same table in a subquery.」
- 项目权威文档（第一手）：
  - `.planning/phases/12-threshold-wizard/12-CONTEXT.md`（D-01..D-18）
  - `.planning/phases/12-threshold-wizard/12-UI-SPEC.md`（9 个 Component Contract）
  - `.planning/REQUIREMENTS.md`（FIX-01..05、THR-01..08、`## Out of Scope`）
  - `.planning/ROADMAP.md` §Phase 12（5 条成功标准）
  - `.planning/research/{ARCHITECTURE,STACK,FEATURES,PITFALLS}.md`
  - `.planning/phases/11-error-foundation/11-CONTEXT.md`（D-01..D-16）
  - `.planning/STATE.md`（Key Decisions、Blockers）

### Secondary (MEDIUM confidence)

- 无。本阶段全部结论均可用第一手代码或字节码判定，未使用二手 web 资料。

### Tertiary (LOW confidence)

- `gsd-tools` 的 `research-plan` / `classify-confidence` seam 在本次环境中不可用（`gsd-core/bin/gsd-tools.cjs` 与 `gsd_run` 均未找到），故未走 provider 路由；所有置信度按第一手代码直接判定，未降级。

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 零新增依赖，全部为已安装包的既有组件；README/CLAUDE.md 的构建命令与 `pom.xml`/`package.json` 一致
- Architecture: HIGH — 四档分档表达式由既有助手的包含式语义直接推导，并用 D-18 边界矩阵逐点验证；别名 IN 的异常行为由字节码证实
- Pitfalls: HIGH — 每个陷阱都对应一处具体代码位置与可复现失败信号；`selectOne` 的 `TooManyResultsException` 与 `determineField` 的点连接是本次研究最容易被忽略的两处

**Research date:** 2026-11-08
**Valid until:** 2026-12-08（30 天；后端代码为稳定仓库内代码，前端 Element Plus 版本已锁定 2.14.0。若 `user_threshold`/`alert_rule` 线上数据分布发生变化需重新核对 A1/A2）

## Project Constraints (from CLAUDE.md)

以下为 `CLAUDE.md` 中与本阶段直接相关的强制约束，计划必须逐条满足：

| 约束 | 对本阶段的具体含义 |
|------|--------------------|
| 后端构建/单测用 `mvn clean verify -DskipITs`；单类用 `mvn test -Dtest=X` | 验证命令不得自造 |
| 前端 `npm run build` 先跑 `vue-tsc -b` 再 `vite build` | 新增的 `RecommendedThresholdVO` 字段必须让 `vue-tsc` 通过；类型改动是构建门禁的一部分 |
| 前端 lint `npm run lint:check`、format `npm run format:check` | 两个新 `.vue` 组件必须过 ESLint + Prettier（2 空格缩进、单引号） |
| 后端 Java 17 + Google Checkstyle + 4 空格缩进 + Lombok | 新类命名与 import 顺序须过 `mvn checkstyle:check` |
| 行尾 LF、编码 UTF-8 | SQL 脚本与新增文件的 CRLF 会被 `.editorconfig` 约束 |
| JaCoCo 排除 entity/dto/vo/exception/config/component/controller/mapper | `ThresholdRangeChecker` 属 `service/domain`，**会被计入覆盖率**，因此必须真实覆盖（30% line / 25% branch 最低线） |
| Service Interface + Impl 模式 | 若新增推荐值服务，须 `MetricRecommendedThresholdProvider` 为具体类（纯组件，非接口+impl），与 `service/domain/` 既有风格一致（`ScoreCalculator` 等亦为具体类） |
| 领域纯逻辑类无 Spring 注解、可脱离容器测试 | `ThresholdRangeChecker` 重构**必须保持**无状态与可 `new` 实例化（注意：它当前有 `@Component` 注解——重构时保留注解但不得引入依赖） |
| 错误约定：业务失败 HTTP 200 + `code != 200`；校验失败 HTTP 400 + `fieldErrors[]` | THR-05 必须走 400 通道才能得到内联；重复指标必须走 200 业务通道以得到「打开编辑」按钮 |
| 数据库为单一 `schema.sql` 手工初始化，无 Flyway | FIX-04 只能是独立脚本 + 部署文档记录，与 D-13 一致 |
| MySQL 8.0+，35 表 | 脚本可用 MySQL 8 特性；但本次不需要窗口函数 |
| 前端 Pinia store 命名与 `api/` 模块 1:1 对应后端 controller | 不新增 store，仅扩展 `metricMeta` |
| 不直接 `import @vueuse/core` | 两个新组件的所有响应式能力只用 `ref`/`computed`（已由 AutoImport 提供） |