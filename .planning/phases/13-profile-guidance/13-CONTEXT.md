# Phase 13: 健康档案完善引导（非阻塞）- Context

**Gathered:** 2026-11-09
**Phase:** 13 — 健康档案完善引导（非阻塞）
**Status:** Ready for planning

<domain>
## Phase Boundary

把健康档案的缺失从**"静默代入默认值"**变为**"持续可见、可理解、可关闭、绝不阻断"**的引导。

交付四件事：
1. **完整度可见化**：档案页与仪表盘展示完整度分数与等级 + 逐字段已填/未填状态
2. **字段级解释**：每个档案字段的"为何需要"说明、重要度标注（必需/推荐/可选）、以及"不填也可保存"的明确承诺
3. **假设披露**：后端在风险评估与健康评分响应中**真实披露**它用了哪些默认假设；前端在下游结果页展示"基于不完整档案估算"徽标
4. **非阻塞提醒**：仪表盘可关闭提示 + 频率上限（不每次刷新都打扰）

**关键边界（来自 ROADMAP / PRF-08 / 成功标准 5）：** 全程**无路由门禁、无新增必填字段**。仅填性别+出生日期的既有用户，健康评分/风险评估/AI 建议/指标录入**全部照常可用**。创建风险评估时档案缺失只**就地提示**并允许补填，绝不硬性阻断。

</domain>

<decisions>
## Implementation Decisions

### 1. 完整度口径与计算归属 (PRF-01, PRF-03)

- **D-01 (后端权威加权评分 + 三等级):**
  - 完整度由**后端**计算并下发，前端不做独立计算（避免两套口径漂移）。当前 `ProfileScorer.java` 的硬编码扣分制（`-10/-15/-10/-15`，缺数据返回 70）需**重构为加权口径**。
  - **权重分层（待确认默认值）**：必需字段（`gender`、`birthDate`）= 60%；推荐字段（`heightCm`、`weightKg`、`bmi`）= 30%；可选字段（`bloodType`、`allergyHistory`、`familyHistory`、`lifestyleHabits`）= 10%。每组内平均分配。
  - **三等级**：`基础档案`（< 60%）、`标准档案`（60%–84%）、`完整档案`（≥ 85%）。前端文案形如「档案完整度 62% · 标准档案」。
  - **逐字段状态**：后端下发 `fields[]`，每项含 `key`、`label`、`importance`（`required`/`recommended`/`optional`）、`filled`(bool)、`reason`（"为何需要"文案）。

### 2. 字段重要度与"不填也可保存"承诺 (PRF-02, PRF-03)

- **D-02 (必需仅指"业务必需"，不是表单强制):**
  - **必需（required）**：`gender`、`birthDate` — 这两项缺失会让风险模型无法计算，但**仍不阻断任何既有功能**（缺失时用默认假设并披露，见 D-03）。
  - **推荐（recommended）**：`heightCm`、`weightKg`、`bmi` — 影响 BMI 与体重维度评分精度。
  - **可选（optional）**：`bloodType`、`allergyHistory`、`familyHistory`、`lifestyleHabits` — 影响生活方式维度与 AI 建议丰富度。
  - 每个字段下方必须有**一行说明 + 重要度徽标 + 统一承诺文案**「推荐与可选字段不填也可保存，不影响核心功能使用」。
  - **"为何需要"文案必须与实际消费方一致**：文案来源须对照后端真实消费点（`RiskAnalyzer` / `DimensionScoreCalculator` / `ProfileScorer`），不写泛泛而谈的理由。例如 `birthDate` → 「用于计算年龄，影响心血管风险评估」。

### 3. 后端假设披露的数据形状 (PRF-05)

- **D-03 (`profileCompleteness` + `assumptions[]` 双字段):**
  - 风险与评分响应 VO **新增两个字段**（附加，不破坏既有契约）：
    - `profileCompleteness: Integer`（0–100）
    - `assumptions: List<String>`（人类可读的中文假设条目，如「缺少身高，BMI 按 22.0 估算」「缺少出生日期，按 30 岁估算」）
  - 涉及 VO：`HealthScoreVO`（已有 `score/level/factors/dimensionScores/calculationMethod/...`）、`RiskAssessmentVO`。
  - **实现要求**：假设条目必须在**实际发生默认代入的位置**捕获（`DimensionScoreCalculator` / `RiskAnalyzer` / 风险服务），而不是事后推测。这样披露与行为天然一致。
  - 前端**直接渲染数组**，不做二次解释。

- **D-03a (假设句的单一来源，2026-11-09 追加 — 消解 plan-check B2):**
  - 「什么会被代入」这组句子由**后端一个静态函数**决定：`ProfileFieldCatalog.assumptionsFor(HealthProfile)`。它按档案状态分支，与风险引擎的真实分支一一对应：
    - 档案行不存在 → **仅**一条通用句 `未填写健康档案，风险得分按通用参考值估算`（风险引擎此时返回固定 30/25/25，不做年龄/BMI/家族史代入，因此不得下发三条逐字段句）。
    - 出生日期为空（仅历史遗留行可达）→「年龄按 35 岁估算」；BMI 为空 →「按 22.0 估算」。
    - 家族病史为空 →「缺少家族病史…」；**已填但未提及糖尿病**（如 `父亲有高血压`）→「家族病史未提及糖尿病…」—— 不得声称一个用户已填的字段缺失。
  - 该函数同时填充 `ProfileCompletenessVO.assumptions` 与 `RiskAssessmentVO.assumptions`，风险页补填面板（创建前承诺）与结果页披露（创建后）**渲染的是同一个数组**。这是 D-03「在实际发生默认代入的位置捕获」的落地方式：引擎的关键词判定 `RiskRuleEngine.hasFamilyHistory` 改为委托 `ProfileFieldCatalog.mentionsDiabetes`，句子选择与行为判定共用同一谓词，因此不可能漂移。
  - 逐字段的 `fields[].assumption` 仍保留（作为字段级元数据供 `assumptionsFor` 使用与前端字段提示），但它**不再是**风险页面板的数据来源 —— 逐字段拼接无法表达「无档案只有一条通用句」和「已填但未提及糖尿病」两种状态。

### 4. 下游结果页徽标 (PRF-06)

- **D-04 (徽标 + 跳转档案页 + 自动聚焦首个待填字段):**
  - 当 `profileCompleteness < 100` 且 `assumptions` 非空时，风险评估页（`prevention/Risk.vue`）与健康评分页展示徽标：**「基于不完整档案估算 · 完善档案」**。
  - 点击徽标 → `router.push('/prevention/profile')`（保留当前页不刷新），并在档案页**自动聚焦第一个未填的必需/推荐字段**（通过 query 参数或 store 传标记，档案页 `onMounted` 消费）。
  - 徽标样式复用现有 `el-tag`（`Risk.vue` 已在用 `el-tag` 展示风险等级），不新建组件。

### 5. 仪表盘提醒的形态与频率 (PRF-07)

- **D-05 (可关闭横幅 + 7 天静默 + 全站单实例):**
  - 形态：仪表盘内一条**可关闭横幅**（非弹窗、非遮罩），提供两个动作：「完善档案」与「稍后提醒」。
  - 频率上限：关闭后 **7 天内不再出现**，时间戳存 `localStorage`（键名遵循项目命名习惯，如 `hhs_profile_reminder_dismissed_at`）。
  - **全站单实例**：横幅只挂在仪表盘一处，其他页面不重复渲染，避免"刷新多个页面出现多条提示"。
  - 触发阈值：`profileCompleteness < 60`（即非「标准档案」以上）且未被静默。
  - 只填性别+出生日期的用户（完整度约 60%）**不触发**该横幅 —— 与成功标准 5 一致。

### 6. 风险评估就地补填 (PRF-08)

- **D-06 (就地提示 + 最小必需补填，不跳页不阻断):**
  - 创建风险评估时若档案缺失：先在**当前页**用一个 `el-alert` 列出「将按以下假设估算：…」，并提供**最小必需字段的内联补填表单**（性别 + 出生日期；身高体重可选补）。
  - 用户可**直接继续创建**（跳过补填），也可填完再创建。**绝不硬性阻断**流程。
  - 补填成功后立即重算并刷新假设列表。
  - 复用 Phase 11 的 `FormErrorSummary` / `useFormServerErrors` 机制（D-04 of Phase 11：全页表单与弹窗表单共用同一套错误呈现）。

- **D-06a (补填表单字段集，2026-11-09 追加 — 消解 plan-check W5 的未记录收窄):**
  - D-06 括注的「身高体重可选补」**按字面执行，不做收窄**：补填面板共 4 个输入 —— 性别（radio）与出生日期（date picker）为必需；身高与体重为**可选**（无校验规则，留空即为合法提交，`:min`/`:max` 对齐 `HealthProfileRequest` 的 50–300 cm / 20–500 kg）。
  - 面板打开时，身高/体重若档案已有值则**预填**；用户不修改时按已有值回写（`backfill.heightCm ?? existing?.heightCm ?? null`），避免"打开面板→保存"把既有可选字段清空。
  - 前端**不计算 bmi**：提交身高体重后由后端 `HealthProfileServiceImpl.toEntity` 派生，随补填成功后的完整度重拉自然反映（D-01 禁止前端算派生值）。
  - 记录在案的收窄：无。此前 draft 只渲染 2 个字段即为未声明收窄，本条已将其纠正为与 D-06 字面一致。

### Claude's Discretion

- 完整度权重具体数值（D-01 的 60/30/10 为待确认默认值，planner 可按医学合理性微调，但分层结构不可变）
- 三等级的精确阈值（60/85 为待确认默认值）
- `assumptions[]` 的具体文案措辞
- 档案页字段说明的具体文案（但必须对照真实消费方）
- 横幅的具体视觉样式（复用现有 token 与 `el-alert` 变体）
- "自动聚焦"的实现方式（query 参数 vs pinia 标记）

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 后端档案与计算核心（PRF-05 主战场）
- `hhs-backend/src/main/java/com/hhs/entity/HealthProfile.java` — 档案实体，8 个业务字段（gender/birthDate/heightCm/weightKg/bmi/bloodType/allergyHistory/familyHistory/lifestyleHabits）
- `hhs-backend/src/main/java/com/hhs/service/domain/ProfileScorer.java` — 当前硬编码扣分制（`-10/-15/-10/-15`，空档案返回 70），**需重构为加权口径**
- `hhs-backend/src/main/java/com/hhs/service/domain/DimensionScoreCalculator.java` — 维度评分，`value == null → return 0` 的静默放行点（假设捕获位置之一）
- `hhs-backend/src/main/java/com/hhs/service/domain/RiskAnalyzer.java` — 风险评估分析，多处 `default -> 70` / `default -> "未知"` 兜底
- `hhs-backend/src/main/java/com/hhs/service/domain/RiskScorer.java` — 风险评分，`default -> 70`
- `hhs-backend/src/main/java/com/hhs/service/impl/HealthScoreServiceImpl.java` — 评分服务主流程
- `hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java` — 风险评估服务主流程
- `hhs-backend/src/main/java/com/hhs/service/HealthProfileService.java` / `impl/HealthProfileServiceImpl.java` — 档案 CRUD 与查询

### 响应 VO（PRF-05 契约扩展点）
- `hhs-backend/src/main/java/com/hhs/vo/HealthScoreVO.java` — 现字段：score/level/factors/dimensionScores/calculationMethod/calculatedAt/expiresAt/isCached/message
- `hhs-backend/src/main/java/com/hhs/vo/RiskAssessmentVO.java` — 风险评估响应 VO

### 前端档案与结果页
- `hhs-frontend-v2/src/views/prevention/Profile.vue` — **健康档案页**（性别/出生日期/身高/体重/BMI + 校验规则第 218-219 行）
- `hhs-frontend-v2/src/views/user/Profile.vue` — 用户账号页（用户名/昵称/邮箱/手机号/改密，**与健康档案无关，勿混**）
- `hhs-frontend-v2/src/views/prevention/Risk.vue` — 风险评估页（已在用 `el-tag` 展示风险等级，徽标落点）
- `hhs-frontend-v2/src/views/dashboard/Index.vue` — 仪表盘（`HealthScoreCircle` 第 70 行，横幅落点）
- `hhs-frontend-v2/src/api/prevention.ts` — `getProfile`（第 19 行）
- `hhs-frontend-v2/src/api/user.ts` — `getProfile`（第 6 行，账号资料）

### 复用的 Phase 11 错误与反馈地基
- `.planning/phases/11-error-foundation/11-CONTEXT.md` — D-01 GOV.UK 双呈现 / D-02 错误状态块 vs toast 分工 / D-04 全页表单与弹窗共用机制
- `hhs-frontend-v2/src/components/common/FormErrorSummary.vue` — 表单错误汇总块（就地补填复用）
- `hhs-frontend-v2/src/components/common/ErrorState.vue` — 页面级错误状态块
- `hhs-frontend-v2/src/composables/useFormServerErrors.ts` — 服务端字段错误投影
- `hhs-frontend-v2/src/utils/errorMessages.ts` — 扁平 key → 中文文案字典

### 阶段定义
- `.planning/ROADMAP.md` §Phase 13 — 8 条 PRF 需求与 5 条成功标准
- `.planning/REQUIREMENTS.md` §PRF — PRF-01..PRF-08 逐条定义

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets（可直接复用，勿新建）
- **`el-tag`**：`Risk.vue` 已在用它展示风险等级 → 「基于不完整档案估算」徽标直接复用同一组件与配色体系
- **`el-alert`**：Phase 11 D-01/D-02 已把它定为"汇总/横幅"的标准载体 → 档案影响横幅、就地补填提示均用它
- **`FormErrorSummary.vue` + `useFormServerErrors.ts`**：就地补填表单的错误呈现直接复用（Phase 11 D-04 明确要求全页表单与弹窗共用同一套）
- **`errorMessages.ts`**：新增文案走这个扁平字典，不散落到各处字面量
- **主题 token**：`theme.css` 的 `--text-1/2/3`、`--accent-cool-soft`、`--radius-*`、`--surface-*` 已齐备（Phase 12 已验证 `--text-3` 浅色下对比度仅 3.97:1 不可用，说明文案用 `--text-2`）

### Established Patterns
- **后端 VO 附加字段演进**：Phase 12 给 `MetricDefinition` 加 `alertable`/`recommended` 并 bump `VERSION_TAG` 让 ETag 失效 —— 本轮给 `HealthScoreVO`/`RiskAssessmentVO` 加字段应沿用同一做法（附加字段 + 版本递增，不破坏既有调用方）
- **前后端字段命名**：全站统一小驼峰（`bloodGlucose`/`systolicBP` 等），新字段应为 `profileCompleteness`/`assumptions`
- **Pinia store 单例共享**：`useMetricMetaStore` 的模式可参考，若需前端共享完整度状态可新建精简 store
- **`Profile.vue` 表单现状**：性别与出生日期已有 `required: true` 校验规则（第 218-219 行）—— **注意：这与 PRF-03「不填也可保存」存在张力，须明确是保留表单校验还是改为软提示**（见待确认项）

### Integration Points
- **假设捕获点**：`DimensionScoreCalculator` 的 `value == null → return 0`、`RiskAnalyzer` 的多处 `default -> 70`、`RiskScorer` 的 `default -> 70` —— 这些就是"静默填充"的真实发生地，`assumptions[]` 必须在此产生
- **完整度计算点**：`ProfileScorer.calculate()` 当前返回 int 分数，需扩展为返回分数+等级+逐字段状态
- **前端渲染点**：`dashboard/Index.vue`（横幅）、`prevention/Risk.vue`（徽标）、`prevention/Profile.vue`（字段说明+完整度）
- **跳转链路**：徽标 → `/prevention/profile` → 档案页自动聚焦首个待填字段

</code_context>

<specifics>
## Specific Ideas

- 完整度展示文案格式：**「档案完整度 62% · 标准档案」**（百分比 + 中点 + 等级名）
- 影响横幅文案格式：**「缺少身高/体重，BMI 与体重评分将按参考值估算」**（点名具体字段 + 点名具体下游后果）
- 徽标文案：**「基于不完整档案估算 · 完善档案」**（状态说明 + 动作入口，用中点分隔）
- 假设条目格式：**「缺少身高，BMI 按 22.0 估算」**（缺什么 + 用什么替代），可人读、可直接渲染

</specifics>

<open_questions>
## 待确认项（供 planner / 用户在规划时拍板）

1. **表单校验与"不填也可保存"的张力**：`prevention/Profile.vue` 现有 `gender`/`birthDate` 为 `required: true`。PRF-03 要求"推荐与可选字段不填也可保存"——但必需字段的 `required` 是否保留？**建议**：保留 `gender`/`birthDate` 的 `required`（它们是档案本身的定义基线），PRF-03 的承诺针对**推荐与可选**字段；若用户希望连必需字段也不强制，则改为 `required: false` + 软提示。
2. **完整度权重与阈值**：60/30/10 权重与 60%/85% 等级阈值为默认建议值，可调。
3. **"自动聚焦"实现方式**：query 参数（`?focus=heightCm`）vs pinia 一次性标记 —— 前者可分享、刷新仍在；后者更干净。建议 query 参数。
4. **横幅是否也出现在其他页面**：D-05 定为仅仪表盘单实例；若用户希望"健康评分页也提示"，需扩展（但会触及"全站单实例"约束）。

</open_questions>

<deferred>
## Deferred Ideas

- **个性化基线（PERS-01）**：结合既往病史自定义个人目标值 —— 已在 v1.4+ Future Requirements，本阶段不做
- **档案字段扩展**（如腰围、静息心率、吸烟史结构化）—— 属新增档案字段，独立阶段
- **AI 建议的档案感知深度化**（让 AI 顾问主动追问缺失档案）—— 新能力，独立阶段
- **完整度历史趋势图**（档案完善过程可视化）—— 新能力，独立阶段
- **档案数据的社交/家庭共享** —— 超出本阶段范围

</deferred>

---

*Phase: 13-健康档案完善引导（非阻塞）*
*Context gathered: 2026-11-09*