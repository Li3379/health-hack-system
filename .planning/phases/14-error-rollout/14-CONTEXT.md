# Phase 14: 全站收尾排查与回归验证 - Context

**Gathered:** 2026-11-10
**Phase:** 14 — 全站收尾排查与回归验证
**Status:** Ready for planning

<domain>
## Phase Boundary

v1.3 的**收尾阶段**（不是新功能）：把 Phase 11 建成的统一错误体系真正推行到全站每一个提交与加载路径，清掉残留的裸错误提示与取消误判，最后用全量自动化 + 端到端验证锁住测试基线，证明 v1.3 三个阶段的改动没有回归。

**四件事：**
1. **ERR-03 内联回填全覆盖**：全站表单提交遇到服务端字段校验失败时，把 `GlobalExceptionHandler` 返回的 `errors[]` 内联到对应 `el-form-item`，字段名映射为中文标签
2. **ERR-07 错误类型可区分**：校验错误内联、网络错误 toast 可重试、403/404 呈解释性状态（而非 toast）
3. **ERR-05 + ERR-08 非破坏性与取消语义**：校验/服务端失败后不清空已输入内容、弹窗保持打开；清理 `'cancel'` 哨兵与 `AbortError` 被误判为失败而弹红提示的问题
4. **回归基线锁定**：全量自动化 + 端到端验证，锁定测试基线并证明无回归

**不在本阶段范围**：新增功能、新增错误类型、重构既有错误地基（Phase 11 已定型）。

</domain>

<decisions>
## Implementation Decisions

### 1. 裸调用迁移范围（ERR-05 / 验收标准 2「裸 ElMessage.error 残留归零」）

- **D-01 (按「用户可见性」分批迁移，而非无差别全量替换):**
  - 现状实测：全站 **87 处**裸 `ElMessage.error`（其中 stores 内 8 处），而接入统一 `showError` 的页面仅 **7 处**。
  - 迁移原则：**用户可见的失败路径必须走 `showError`**（表单提交、列表拉取、详情加载、删除/导出等主动操作）；**内部静默路径保持静默**（现有 16 处 `{ silent: true }` 调用点、后台预加载、轮询心跳）——这些本就不该弹提示，改成 `showError` 反而制造噪音。
  - **验收口径**：验收标准 2 的「裸残留归零」解释为**用户可见路径**裸调用归零；静默路径不算残留（若其确实主动弹了 toast，则它不属静默路径，须迁移）。
  - 迁移后必须保留 Phase 11 D-01 的语义：一次失败恰好一条提示（`showError` 内部已有去重）。

- **D-02 (stores 内的 8 处按调用方契约处理):**
  - Pinia store 不在 UI 层，不应直接弹 toast。store 内的 `ElMessage.error` 一律改为**抛错/返回错误**，由调用它的组件用 `showError` 呈现。
  - 例外：store 内确实承担"用户主动动作"且无合理调用方承接的场景，可保留但须在台账注明理由。

### 2. 403 / 404 的解释性状态（ERR-07）

- **D-03 (403 全页级、404 内容区级，均不弹 toast):**
  - **403（无权限）** → 全页级解释页：标题「无权访问此页面」+ 说明当前账号缺少哪个权限 + 「返回首页」动作。语义：整个页面都不可用。
  - **404（资源不存在）** → 内容区 `ErrorState` 解释块（复用 Phase 11 已有组件）：标题「资源不存在」+ 说明可能已被删除 + 「返回列表」/「重试」动作。语义：页面壳可用，仅该资源缺失。
  - **与 Phase 11 D-02 的分工保持一致**：错误状态块占内容区，toast 只用于瞬时动作失败。403/404 是**状态**不是**动作失败**，故走状态块。
  - **不新建全页组件**：403 复用 `ErrorState` 的容器能力扩展一个 `variant="forbidden"`，或用一个轻量的页面级包装，具体形式留给 planner（关键约束是「全页 vs 内容区」的层级差异与「零 toast」）。

### 3. 取消语义清理（ERR-08）

- **D-04 (取消不是失败，必然零提示):**
  - `'cancel'` / `'close'` 哨兵（`appError.ts:41` 的 `CANCEL_SENTINELS`）与 `AbortError`（`:50`）已能识别为 `kind: 'cancel'`，`request.ts:65` 与 `showError.ts:129` 也已短路。
  - **本轮要做的**：清理**绕过**这两个短路、自行 `catch (e) { if (e !== 'cancel') ElMessage.error(...) }` 的手写判断（实测至少 `goals/Index.vue:157`、`health/Alerts.vue:224` 这类写法）。这类手写判断在 `ElMessageBox` 取消时能用，但遇到 `AbortError` 或归一化后的 `ApiError('', 'cancel')` 就会漏判 → 误弹红提示。
  - **统一做法**：这类地方改用 `showError`（它已识别 cancel）或直接 `catch` 后不做任何提示。

### 4. 回归基线锁定（验收标准 4）

- **D-05 (锁定当前实测值为基线，而非 ROADMAP 原文的 90):**
  - ROADMAP 写的「不少于既有 90 项基线」是 v1.0 时期的陈旧口径。本阶段实测：**后端 845 项、前端 445 项 / 37 文件**。
  - **基线锁定为**：后端 `mvn clean verify -DskipITs` 全绿且 ≥ 845；前端 `npm run test:run` 全绿且 ≥ 445（新增测试只能加不能减）。
  - 同时锁定：`npm run build` 绿、`lint:check` 0 errors、`checkstyle:check` 0 violations。
  - **E2E**：`quick-input.spec.ts` 的成功文案契约仍通过（需后端运行）。

### 5. 端到端验证方式（验收标准 3）

- **D-06 (浏览器实跑 + E2E 并行):**
  - 验收标准 3（停后端逐页点保存/加载、断网 60 秒轮询不重复 toast）**用真实浏览器实跑**，结果写入 UAT——这类行为依赖真实心跳/轮询节奏，单测无法覆盖。
  - 同时保留 `quick-input.spec.ts` 等既有 E2E 作为自动化回归。
  - 实跑清单：至少覆盖 3 类页面（列表页加载、表单提交、详情页），每类验证「恰好一条中文可行动提示」。

### Claude's Discretion

- 87 处迁移的具体分批顺序与批次边界
- 403 全页状态的具体实现形式（扩展 `ErrorState` variant vs 轻量包装组件）
- store 内 8 处错误的具体改造方式（抛错 vs 返回错误结果）
- 台账（migration ledger）的载体与字段（Markdown 表格 vs JSON）
- 「恰好一条提示」的观测方法（toast 计数用 DOM 查询还是网络请求计数）

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 11 错误地基（本阶段消费的既有资产，勿重建）
- `.planning/phases/11-error-foundation/11-CONTEXT.md` — D-01 GOV.UK 双呈现 / D-02 错误状态块 vs toast 分工 / D-03 网络错误带重试 / D-04 全页表单与弹窗共用 / D-05 文案后端优先 / D-09 silent 由调用点显式标记
- `hhs-frontend-v2/src/utils/appError.ts` — `ApiError` 类型、`ErrorKind`（含 `'cancel'`）、`CANCEL_SENTINELS = ['cancel','close']`（第 41 行）、`AbortError` 识别（第 50 行）、`normalizeApiError`
- `hhs-frontend-v2/src/utils/showError.ts` — 统一呈现入口；`kind === 'cancel'` 短路（第 129 行）；自动去重；自动补 retry
- `hhs-frontend-v2/src/utils/request.ts` — 只翻译不呈现拦截器；`apiError.kind === 'cancel'` 短路（第 65 行）
- `hhs-frontend-v2/src/utils/errorMessages.ts` — 扁平 key → 中文文案字典（当前 23 键；`errorMessages.spec.ts` 有 18 键精确相等断言，**不得新增键破坏它**）
- `hhs-frontend-v2/src/composables/useFormServerErrors.ts` — 服务端 fieldErrors 投影（内联回填核心）
- `hhs-frontend-v2/src/components/common/FormErrorSummary.vue` — 表单错误汇总块
- `hhs-frontend-v2/src/components/common/ErrorState.vue` — 页面级错误状态块（403/404 复用基础）

### 已正确接入的参考实现（迁移范式）
- `hhs-frontend-v2/src/views/health/Thresholds.vue` — Phase 12 完成的全页表单接入（`useFormServerErrors` + `FormErrorSummary` + `showError` + 重复指标恢复动作）
- `hhs-frontend-v2/src/views/health/components/ThresholdWizard.vue` — 弹窗表单接入（零 toast、GOV.UK 双呈现、未保存守卫）
- `hhs-frontend-v2/src/views/prevention/Profile.vue` — Phase 13 完成的全页表单接入（含 `?focus=` 与保存后刷新）
- `hhs-frontend-v2/src/views/prevention/Risk.vue` — Phase 13 完成的就地补填面板（`el-alert` + 内联表单 + 非阻断）

### 迁移对象（87 处裸调用的分布样本）
- `hhs-frontend-v2/src/stores/*.ts` — 8 处（`floatingAi.ts`、`wellness.ts`、`ai.ts` 等）
- `hhs-frontend-v2/src/views/goals/Index.vue:157` — 手写 `catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }`（ERR-08 典型）
- `hhs-frontend-v2/src/views/health/Alerts.vue:224` — 同类手写 cancel 判断
- `hhs-frontend-v2/src/views/data-input/components/*` — `AIInput`、`OcrInput`、`DeviceSync`、`DeviceConfigWizard`、`AiHistory`、`OcrHistory`
- `hhs-frontend-v2/src/views/mood/MoodTracker.vue`、`hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue` 等

### 阶段定义
- `.planning/ROADMAP.md` §Phase 14 — 目标与 4 条成功标准
- `.planning/REQUIREMENTS.md` §ERR — ERR-03 / ERR-05 / ERR-07 / ERR-08 逐条定义

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets（已就绪，直接消费）
- **`showError(err, fallbackKey?, retry?)`** — 一次失败恰好一条提示、自动去重、cancel 自动静默、网络错误自动带重试。**87 处迁移的目标 API**
- **`useFormServerErrors({ fieldOrder })`** — `fieldErrors` 投影 + `apply()` + `clearField()` + `focusField()`。内联回填的现成实现
- **`FormErrorSummary`** — GOV.UK 风格汇总块（可点击聚焦）
- **`ErrorState`** — 页面级错误块（图标 + 原因 + 重试）
- **`CANCEL_SENTINELS` / `AbortError` 识别** — ERR-08 的判定逻辑已存在，只需清理绕过它手写判断的地方

### Established Patterns
- **错误呈现三层分工**（Phase 11 D-02）：字段级内联 / 表单级汇总 / 页面级状态块；瞬时动作失败才用 toast
- **静默由调用点显式标记**（Phase 11 D-09）：`{ silent: true }`，无 URL 模式自动判定表（现有 16 处）
- **一次失败一条提示**（Phase 11 D-01 / ERR-06）：`showError` 内部去重，禁止调用点自行拼 toast
- **文案后端优先**（Phase 11 D-05）：后端下发中文消息，前端字典仅作回退

### Integration Points
- **迁移面**：87 处裸 `ElMessage.error`（其中 8 处在 stores）
- **内联回填面**：目前仅 4 个页面接入 `useFormServerErrors`（Thresholds / ThresholdWizard / prevention-Profile / Risk），其余表单页需接入
- **403/404 面**：`appError.ts` / `showError.ts` 中**完全没有** 403/404 的区分处理（grep 无结果）
- **cancel 手写判断面**：至少 2 处（`goals/Index.vue:157`、`health/Alerts.vue:224`）

### 约束
- `errorMessages.spec.ts` 有 **18 键精确相等断言** —— 新增字典键会破坏它；若必须新增键，需同步更新该断言（属测试变更，不是放宽）
- `quick-input.spec.ts` 的成功文案契约必须保持通过
- 不得修改 `request.ts` 的「只翻译不呈现」定位（Phase 11 D-01 已定型）

</code_context>

<specifics>
## Specific Ideas

- **迁移台账**应记录：文件、行号、原文案、迁移后形态（`showError` / 静默 / 保持）、理由
- **「裸残留归零」的验证命令**应可执行（如 grep 计数 = 0，排除 `utils/showError.ts` 自身与测试文件）
- **403 全页页文案**：「无权访问此页面」+ 说明 + 「返回首页」
- **404 内容区文案**：「资源不存在」+ 「可能已被删除」+ 「返回列表」/「重试」

</specifics>

<open_questions>
## 待确认项（供 planner / 用户在规划时拍板）

1. **87 处迁移的批次划分**：按模块（health / prevention / data-input / goals / mood / stores）分批，还是按错误类型（提交类 / 加载类 / 删除类）分批？建议按模块分批，便于逐批验证与回滚。
2. **store 内 8 处的改造方式**：改为抛错（调用方承接）还是返回 `{ ok, error }` 结果对象？前者改动小但要求所有调用方处理；后者更显式但需改签名。
3. **403 全页状态的实现载体**：扩展 `ErrorState` 加 `variant` 属性，还是新建轻量页面级包装？前者复用度高，后者语义更清晰。
4. **「恰好一条提示」的观测方法**：DOM 查询 `.el-message` 计数（简单但受动画影响）vs 拦截网络响应计数（准确但需 mock）？
5. **是否需要在本次一并处理 `errorMessages.spec.ts` 的 18 键断言**：若迁移中确实需要新增文案键，该精确相等断言必须同步更新（属测试变更）。

</open_questions>

<deferred>
## Deferred Ideas

- **错误监控上报**（Sentry 类）：本阶段只做用户可见呈现，不上报后端
- **错误文案国际化**（i18n 多语言）：当前只做中文
- **自动化视觉回归**（截图对比）：本阶段用实跑 + 断言，不引入视觉 diff 工具
- **错误码文档化**（给运营/客服的错误码手册）：独立文档工作，非本阶段
- **Testcontainers 集成测试环境修复**：WINDOWS.md #3 记录的 Docker socket 限制，属基础设施，不在本阶段

</deferred>

---

*Phase: 14-全站收尾排查与回归验证*
*Context gathered: 2026-11-10（4 个灰区问题返回空，按推荐默认落定并全部列入 open_questions 供推翻）*