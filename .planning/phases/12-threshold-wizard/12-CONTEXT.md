# Phase 12: 阈值配置向导化与告警正确性修复 - Context

**Gathered:** 2026-11-07
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段交付两件互相支撑的事：

1. **让阈值真实生效** — 修正 `ThresholdRangeChecker.isWarningRange` 的区间反向条件；阈值写入/查询路径统一 `metricKey` 规范化；提供幂等 SQL 脚本归一化存量 `user_threshold` 与 `alert_rule` 的非标准 key 并合并重复行。
2. **把阈值配置从四个裸数字框升级为引导式向导** — 元数据驱动的指标选择器（仅可告警指标）、推荐值自动预填、四项通俗解释与单位、实时四区预览条、前后端同规则的层级与生理极限校验、保存前自然语言确认语句、教学式空状态、友好的重复指标处理。

这是修复 + 引导的组合：先保证"配了就生效"，再保证"看得懂、填得对"。

</domain>

<decisions>
## Implementation Decisions

### 向导形态
- **D-01:** 采用 `ElSteps` 三步向导，复用 `views/data-input/components/DeviceConfigWizard.vue` 的既有范式：
  - 第 1 步：选择指标（元数据驱动、分组、仅 alertable）→ 选定后自动预填推荐值
  - 第 2 步：四项阈值输入（各自带通俗中文说明 + 单位）+ 实时四区预览条
  - 第 3 步：自然语言确认语句 + 保存
- **D-02:** 编辑已有阈值时**跳过第 1 步**直接进入第 2 步；指标类型锁定不可改；第 3 步确认语句仍然显示，可回退到第 2 步修改。
  - **Reversibility:** costly — 105 个调用点之外的页面级交互契约一旦被用户与测试熟悉，改回单弹窗要重写整套向导与相关测试。

### 指标范围
- **D-03:** 可告警指标由**后端**元数据注册中心权威决定：为每个指标增加 `alertable` 标志，并在同一响应中下发推荐阈值。前端**不维护白名单常量**。
- **D-04:** 选择器只渲染 `alertable === true` 的指标；按元数据 `category` 分 HEALTH / WELLNESS 两组，**空组隐藏**（当前 WELLNESS 组无可告警指标，因此实际只显示 HEALTH 组）。
- **D-05:** 选项文案为 `显示名 (单位)`，例如 `收缩压 (mmHg)`。
  - **Reversibility:** costly — 若改为前端白名单，会与后端种子规则漂移，需要重新校准两端。

### 推荐值来源
- **D-06:** 推荐四值由**后端一并下发**（与 `alertable` 标记同一响应），来源为 `alert_rule` 种子派生；种子缺失时用元数据 `normalMin`/`normalMax` 推导。前端**不做独立计算**，避免前后端算出不同推荐值。
- **D-07:** 只有单一「推荐值」一档，**不做**宽松/标准/严格三档预设。
- **D-08:** 选定指标后**自动预填**四项为推荐值并标注「推荐」徽标；同时提供「恢复推荐值」按钮让用户回到初值。

### 层级与生理极限校验（THR-05）
- **D-09:** **双侧校验**。前端为即时反馈，后端为权威拦截（直接调用 API 可绕过前端）。
  - 前端：违反时实时禁用「下一步 / 保存」，并在**违反的两个输入框下各挂一条内联中文**（例：「警告高值必须大于警告低值」），第 2 步顶部同步出现 GOV.UK 汇总块。
  - 后端：新增类级约束 `@ValidThresholdRange` 挂到 `UserThresholdRequest`，沿用既有 `@ValidMetricRecord` 先例；违规消息同时 `addPropertyNode` 到涉及的**两个字段**，使两个输入框都获得内联红字，与 Phase 11 的 `fieldErrors` 契约天然对齐。
- **D-10:** 合法次序为 `criticalLow < warningLow < warningHigh < criticalHigh`，每个边界可独立为 `null`，仅当相邻两侧均非 null 时才要求成立（与"null = 无界"的既有语义一致）。
- **D-11:** 生理极限边界使用元数据 `hardMin`/`hardMax`，同时作为 `el-input-number` 的 `:min`/`:max` 与校验边界。**不使用** `PANIC_LIMITS`（语义是急危重症阈值而非物理可达边界，且仅覆盖 10 项）。
- **D-12:** 校验失败呈现严格遵循 Phase 11 已定契约：**零 toast**，汇总 + 内联双呈现，不清空用户已输入内容。

### 存量数据修复（FIX-04）
- **D-13:** 交付为**独立幂等 SQL 脚本** `hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql`，部署时手动执行。**不引入 Flyway**，**不在应用启动时自动执行**。
- **D-14:** 归一化后产生的重复行（同 user + 同标准 key）**保留 `updated_at` 最新的一条**，删除其余——反映用户最近一次设置意图。
- **D-15:** `alert_rule` 种子里的非标准 key（`glucose`、`temperature`）**一并修正**为 `bloodGlucose`、`bodyTemperature`，并写幂等 UPDATE 修存量 `alert_rule` 行。否则未配置阈值的用户回退到种子规则时仍会因 key 不匹配而失效。
  - **Reversibility:** one-way — 一旦生产库跑过归一化，旧 key 不再存在；回退需要反向映射且会丢失原行 id。

### 正确性修复（FIX-01/02/03/05）
- **D-16:** `isWarningRange` 修复后**抽出单一 `classify()`**，让 `isNormalRange` / `isWarningRange` / `isCriticalRange` / `getSeverityLevel` 共享同一判定，杜绝再次分叉。
- **D-17:** 阈值写入路径（`UserThresholdServiceImpl.create/update`）在**预检查与入库两处**都调用 `MetricKeyNormalizer.toCanonicalKey()`；查询路径（`getByUserAndMetricKey` 及告警侧所有 `selectOne/selectList`）改用 `MetricKeyNormalizer.getAliases()` 的 `IN` 查询，兼容尚未归一化的历史行。
- **D-18:** `ThresholdRangeCheckerTest` 必须覆盖：正常 / 警告（高、低两侧）/ 危急（高、低两侧）/ 单侧为 null / 全部为 null / 恰好等于边界值。

### Claude's Discretion
- 四区预览条的具体视觉实现（`ElSlider` range + marks、自绘色带、或 `div` 拼接）——只要满足"颜色带 + 当前输入位置标注"即可。
- 确认语句的具体中文句式。
- 教学式空状态（THR-07）的默认阈值展示方式：直接读 `alert_rule` 种子或复用向导的推荐值来源。
- 重复指标（THR-08）的提示回退到 Phase 11 已实现的 `ERROR_CODE_ACTIONS.resource.exists → 打开编辑` 机制，只需确保文案为「该指标已有阈值，是否改为编辑？」。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 需求与路线
- `.planning/REQUIREMENTS.md` — FIX-01..05、THR-01..08 的权威定义；`## Out of Scope` 明确禁止：暴露全部 23 项指标、新建阈值模板表、引入 Flyway、引入 vue-i18n/VeeValidate/driver.js、把四阈值模型改为单一目标区间。
- `.planning/ROADMAP.md` §Phase 12 — 阶段目标与 5 条 Success Criteria。
- `.planning/PROJECT.md` — v1.3 Key Decisions：「阈值推荐值来自元数据注册表 + 告警种子规则派生，不新建阈值模板表」。

### 前期研究（阶段规划前必读）
- `.planning/research/FEATURES.md` §Table Stakes — THR-01..08 每项的实现复杂度与依赖说明。
- `.planning/research/ARCHITECTURE.md` §Flow 3 — 阈值关系校验的完整不变量推导（`criticalLow < warningLow < warningHigh < criticalHigh`）、校验归属表、为什么不做数据库 CHECK 约束。
- `.planning/research/ARCHITECTURE.md` §Recommended Project Structure — 新增 `validation/ValidThresholdRange.java` + `ThresholdRangeValidator.java` 的落位。
- `.planning/research/STACK.md` §(a) — 阈值向导的现有组件映射（`ElSteps`/`ElForm`/`ElInputNumber`/`ElSlider`）、零新增依赖的论证、`@vueuse/core` 不得直接 import 的警告。
- `.planning/research/PITFALLS.md` — 已知陷阱：`handleBusinessException` 拼接 `error_description`、key 规范化必须同时在预检查与插入处。

### Phase 11 已定契约（本阶段必须遵守）
- `.planning/phases/11-error-foundation/11-CONTEXT.md` — D-01 GOV.UK 双呈现、D-05 后端中文优先、D-08 恢复动作、D-09/D-10 silent 契约。
- `.planning/phases/11-error-foundation/11-UI-SPEC.md` — 错误色 token、汇总块排版、toast 间距与行高。
- `.planning/phases/11-error-foundation/11-PATTERNS.md` — 新增文件到最相近参考实现的映射。

### 现有代码锚点
- `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java` — 待修复的区间判定。
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java` — 权威 key 规范化；`toCanonicalKey()` / `getAliases()` / `getCanonicalKeys()`。
- `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdEvaluator.java` — 告警侧阈值查询（`:58-80`），需改为别名集合查询。
- `hhs-backend/src/main/java/com/hhs/service/impl/UserThresholdServiceImpl.java` — 写入/查询路径。
- `hhs-backend/src/main/java/com/hhs/controller/MetricMetadataController.java` + `service/domain/HealthMetricMetaRegistry.java` — 元数据出口，`alertable` 与推荐值要加在这里。
- `hhs-backend/src/main/resources/sql/schema.sql` §INITIAL DATA — `alert_rule` 种子（`:514-521`），含非标准 key。
- `hhs-frontend-v2/src/views/health/Thresholds.vue` — 待重写的页面（当前硬编码 6 项、`:min="0"` 裸数字框）。
- `hhs-frontend-v2/src/views/data-input/components/DeviceConfigWizard.vue` — `ElSteps` 向导的既有范式。
- `hhs-frontend-v2/src/stores/metricMeta.ts` — `healthMetrics` / `wellnessMetrics` / `getDefinition()`。
- `hhs-frontend-v2/src/constants/metricMeta.ts` — `FALLBACK_METRIC_META`，23 项指标的 `hardMin`/`hardMax`/`normalMin`/`normalMax`。
- `hhs-frontend-v2/src/views/data-input/components/ReferenceGuideCard.vue` — 引导卡片的视觉语言参考。
- `hhs-frontend-v2/src/utils/showError.ts` — 唯一错误出口；`ERROR_CODE_ACTIONS` 的 `打开编辑` 映射。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ElSteps` 三步范式**：`DeviceConfigWizard.vue:4-9` 已有 `:active` + `finish-status="success"` + `align-center`，可直接套用。
- **`useMetricMetaStore`**：`healthMetrics` / `wellnessMetrics` / `allDefinitions` 已按 category 分好组；`getDefinition()` 支持别名回退。向导的指标选择器几乎不用新逻辑。
- **`FALLBACK_METRIC_META`**：23 项指标的 `hardMin`/`hardMax`/`normalMin`/`normalMax`/`unit`/`precision`/`step`/`guideHint` 全都有——`hardMin`/`hardMax` 直接可作为输入框边界与校验边界。
- **`ERROR_CODE_ACTIONS.resource.exists → 打开编辑`**（Phase 11）：THR-08 的重复指标处理已经实现，本阶段只需确认文案。
- **`useFormServerErrors` + `FormErrorSummary`**（Phase 11）：后端 `fieldErrors` 到内联 + 汇总的投影链路现成可用。
- **`showError`**（Phase 11）：唯一错误出口，校验失败默认零 toast。

### Established Patterns
- **类级约束先例**：`@ValidMetricRecord` + `MetricRecordValidator` 已存在，`@ValidThresholdRange` 照抄即可（`ConstraintValidator<A, UserThresholdRequest>`）。
- **Domain 纯逻辑类**：`service/domain/` 下的类无 Spring 注解、纯函数、可脱离容器测试。`ThresholdRangeChecker` 的重构应保持这一属性。
- **`Result<T>` 信封**：`code`/`message`/`data`/`errorCode`/`fieldErrors`。业务失败是 HTTP 200 + `code != 200`，校验失败是 HTTP 400 + `fieldErrors[]`。
- **两套 key 语义并存**：`alert_rule`/`alert_template`/`user_threshold` 里实际存的是 `glucose`/`temperature`/`bloodOxygen` 这类混合写法，而 `MetricKeyNormalizer` 的标准 key 是 `bloodGlucose`/`bodyTemperature`/`bloodOxygen`。

### Integration Points
- `MetricMetadataController` → `MetricDefinitionVO` 新增 `alertable` + 推荐四值字段 → 前端 `metricMeta` store → 向导第 1 步。
- `UserThresholdRequest` 挂 `@ValidThresholdRange` → `GlobalExceptionHandler` 的三校验分支（Phase 11 已统一）→ `fieldErrors[]` → `useFormServerErrors` → 内联 + 汇总。
- `UserThresholdServiceImpl.create/update` 的 `toCanonicalKey()` + `getByUserAndMetricKey` 的 `getAliases()` IN 查询 → 告警引擎（`ThresholdEvaluator:58-80`）命中。
- `sql/fix-threshold-metric-key.sql` → 手动部署步骤，须在 `CLAUDE.md` 或部署文档中记录执行时机。

</code_context>

<specifics>
## Specific Ideas

- 用户在讨论中强调"我点击添加后不知道怎么填"——四项输入必须**各自带通俗中文说明 + 单位**，不能只有 `警告高值` 这类临床术语。
- 用户的原始痛点是"配置了不生效"——所以 FIX 系列必须**先于** THR 系列实施，否则向导做得再好也仍然不触发告警。
- 容忍历史数据：即使 SQL 脚本尚未在某环境执行，查询路径也必须通过别名集合命中历史非标准 key 行（D-17）。

</specifics>

<deferred>
## Deferred Ideas

- **宽松/标准/严格三档预设**（研究文档 THR-09 differentiator）——本轮不做，若后续需要可单开阶段。
- **条件感知预设**（"我已确诊高血压" → 血压阈值对齐指南，THR-10）——涉及隐私与临床建议，不在本轮范围。
- **阈值变更影响预览**（"改为这些值后，你近 30 天会有 N 条记录触发告警"）——需要历史数据扫描，属新能力。
- **应用启动时自动迁移框架**（Flyway/Liquibase）——项目已明确推迟，见 REQUIREMENTS.md Out of Scope。

</deferred>

---

*Phase: 12-threshold-wizard*
*Context gathered: 2026-11-07*