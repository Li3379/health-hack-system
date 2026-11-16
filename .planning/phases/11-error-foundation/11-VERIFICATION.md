---
phase: 11-error-foundation
verified: 2026-11-16T15:50:00Z
status: passed
score: 18/18 must-haves verified
covered_files:
  - hhs-backend/src/main/java/com/hhs/common/Result.java
  - hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java
  - hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java
  - hhs-backend/src/main/java/com/hhs/service/impl/DeviceSyncOrchestrationServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java
  - hhs-backend/src/test/java/com/hhs/controller/DeviceSyncControllerPageTest.java
  - hhs-frontend-v2/src/App.vue
  - hhs-frontend-v2/src/__tests__/utils/appError.spec.ts
  - hhs-frontend-v2/src/__tests__/utils/request.spec.ts
  - hhs-frontend-v2/src/__tests__/utils/showError.spec.ts
  - hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts
  - hhs-frontend-v2/src/__tests__/views/LoginAuthError.spec.ts
  - hhs-frontend-v2/src/__tests__/views/OAuthCallbackError.spec.ts
  - hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts
  - hhs-frontend-v2/src/assets/styles/theme.css
  - hhs-frontend-v2/src/components/common/ErrorState.vue
  - hhs-frontend-v2/src/components/common/FormErrorSummary.vue
  - hhs-frontend-v2/src/composables/useFormServerErrors.ts
  - hhs-frontend-v2/src/stores/alert.ts
  - hhs-frontend-v2/src/stores/auth.ts
  - hhs-frontend-v2/src/stores/metricMeta.ts
  - hhs-frontend-v2/src/stores/push.ts
  - hhs-frontend-v2/src/types/api.ts
  - hhs-frontend-v2/src/types/axios.d.ts
  - hhs-frontend-v2/src/utils/appError.ts
  - hhs-frontend-v2/src/utils/authSession.ts
  - hhs-frontend-v2/src/utils/errorMessages.ts
  - hhs-frontend-v2/src/utils/request.ts
  - hhs-frontend-v2/src/utils/showError.ts
  - hhs-frontend-v2/src/views/auth/Login.vue
  - hhs-frontend-v2/src/views/auth/Register.vue
  - hhs-frontend-v2/src/views/health/Thresholds.vue
  - hhs-frontend-v2/src/views/oauth/OAuthCallback.vue
  - hhs-frontend-v2/src/views/prevention/Profile.vue
  - hhs-frontend-v2/src/views/user/PushSettings.vue
  - .planning/phases/11-error-foundation/11-01-PLAN.md
  - .planning/phases/11-error-foundation/11-01-SUMMARY.md
  - .planning/phases/11-error-foundation/11-02-PLAN.md
  - .planning/phases/11-error-foundation/11-02-SUMMARY.md
  - .planning/phases/11-error-foundation/11-03-PLAN.md
  - .planning/phases/11-error-foundation/11-03-SUMMARY.md
  - .planning/phases/11-error-foundation/11-04-PLAN.md
  - .planning/phases/11-error-foundation/11-04-SUMMARY.md
  - .planning/phases/11-error-foundation/11-05-PLAN.md
  - .planning/phases/11-error-foundation/11-05-SUMMARY.md
  - .planning/phases/11-error-foundation/11-06-PLAN.md
  - .planning/phases/11-error-foundation/11-06-SUMMARY.md
  - .planning/phases/11-error-foundation/11-07-PLAN.md
  - .planning/phases/11-error-foundation/11-07-SUMMARY.md
  - .planning/phases/11-error-foundation/11-08-PLAN.md
  - .planning/phases/11-error-foundation/11-08-SUMMARY.md
covered_digest: "v1:sha256:39169a5644021422c37c47607d2751c66d5d21d7964dbfd4ecc686fd1c05123d"
behavior_unverified: 0
overrides_applied: 0
re_verification_2026_11_11:
  trigger: "verification.status reported stale — Phase 14 migration commits touched 19 of this phase's covered_files"
  scope: "re-ran the drifted presentation checkpoints in a live stack plus full regression gates; no full UAT re-run"
  result: "6/6 reverification checkpoints pass, 0 new defects"
  gates:
    backend: "845 tests / 0 failures (BUILD SUCCESS)"
    frontend: "642 tests / 59 files passed"
  drifted_files_justification: |
    All 19 drifted files fall into two groups, neither of which invalidates a Phase 11 truth:
    (a) the shared error utilities themselves (appError.ts, showError.ts, errorMessages.ts,
        ErrorState.vue, useFormServerErrors.ts, theme.css) — Phase 14 EXTENDED them additively
        (forbidden/notFound kinds, presentStates, 46-key dictionary) and its own 14-VERIFICATION.md
        re-verified them line by line; the 9-step classification order keeps 401 at step 3, ahead of
        the new 403/404 at steps 4-5, so every Phase 11 channel classification is unchanged.
    (b) consumer pages Phase 14 migrated from bare ElMessage.error to showError (Login.vue,
        Register.vue, Thresholds.vue, Profile.vue, PushSettings.vue, metricMeta.ts, push.ts) —
        migrating MORE call sites onto the single exit strengthens truth 2 and truth 10, and the
        residual count fell 87 -> 4.
  environment_note: |
    IAB non-foreground (document.hasFocus() === false) freezes every CSS animation timeline
    (all 8 observed animations held currentTime at 0), so el-message-box leave transitions never
    complete and the overlay lingers. Same class of environment false positive recorded for
    Phase 12's wizard auto-close. Cancel-channel verdicts were therefore taken from
    "zero toast + row count unchanged", not from overlay disappearance.
re_verification:
  previous_status: gaps_found
  previous_score: 17/18
  gaps_closed:
    - "D-02 / ERR-01：推送设置页加载失败的二次呈现已消除 —— loadConfigs 固定 silent，只写 loadError，呈现权归 PushSettings.vue 的 ErrorState（94c1475）"
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "FileUploadController.java:104 仍把 e.getMessage() 拼进 HTTP 500 信封（\"文件上传失败：\" + e.getMessage()）"
    category: security
    reasoning: "本阶段未修改该文件（git diff adf4f94..HEAD 为空），也不在上一版 gaps 列表，无失败测试或可复现输出证明英文到达用户。按 #3304 证据门控保持 advisory，不阻塞。"
    evidence_status: "none provided"
  - finding: "OcrServiceImpl.java:289 return OcrHealthResultVO.failed(e.getMessage())（该行本身为 13dd26b3 旧代码，文件因 WR-03 修改过，但此行为未改）"
    category: security
    reasoning: "OcrHealthResultVO.failed 会把 errorMessage 复制进 summary；OcrInput.vue:105 直接渲染 summary。百度 OCR 客户端抛出的 RuntimeException 文案含英文/上游文本。这是本阶段前即存在的路径，非本轮引入，也无失败测试。建议 Phase 14 一并收敛为固定中文。"
    evidence_status: "none provided"
  - finding: "AiParseServiceImpl.java:197 与 ExportServiceImpl.java:158 仍将 e.getMessage() 下发到用户可见字段"
    category: security
    reasoning: "两个文件自上一版验证以来未被修改，不在上一版 gaps 列表，无确定性证据。属 Phase 14 收尾观察项。"
    evidence_status: "none provided"
  - finding: "Alerts.vue:200/210/225 三处泛化文案「操作失败」，为 Phase 11 前存量"
    category: other
    reasoning: "属 Phase 14 调用点迁移范围（11-CONTEXT.md:16 明确 105 处调用点延后）。非本阶段回归。"
    evidence_status: "none provided"
  - finding: "PushSettings.vue 右侧卡片：loadStats 失败时统计卡永久停在「加载中...」，loadHistory 失败时显示「暂无推送记录」（零呈现 + 空态冒充）"
    category: other
    reasoning: "两处 store 分支（push.ts:101-125）本轮未被改动，行为与上一版验证时一致：按 WR-09 设计次要区块静默降级，因此失败时既不 toast 也无错误块。上一版验证未列为 gap，本轮亦无确定性证据，且不属于「同一失败被呈现两次」的 at-most-once 不变式。记录为 Phase 14 调用点/次要区块状态化观察项，不阻塞。"
    evidence_status: "none provided"
deferred:
  - truth: "逐页替换约 87 处生产代码裸 ElMessage.error 调用点"
    addressed_in: "Phase 14"
    evidence: "11-CONTEXT.md:16『不在本阶段：逐页替换 105 处 ElMessage.error 调用点（Phase 14）』；REQUIREMENTS.md 将 ERR-03/05/07/08 映射到 Phase 14"
  - truth: "403/404 解释性状态页、ERR-03 全站字段内联、ERR-05 输入保留、ERR-07 呈现区分、ERR-08 取消清理"
    addressed_in: "Phase 14"
    evidence: "REQUIREMENTS.md:116-123 映射表；ERR-07/08 仍为未勾选状态"
  - truth: "OAuth 非法 state 未即时校验（已知平台 + 非法 state 仍走 60s 等待超时）"
    addressed_in: "Phase 14"
    evidence: "11-UAT.md:197-198 记为与错误文案无关的功能缺口"
  - truth: "「分类: 消息」拼接读感重复（如「密码不正确: 用户名或密码错误」）"
    addressed_in: "Phase 14"
    evidence: "11-UAT.md:199-200 文案打磨项"
---

# Phase 11: 全站统一友好错误反馈地基 Verification Report

**Phase Goal:** 建立全站唯一的错误"翻译—呈现"分层：后端输出结构化字段错误信封，前端拦截器只翻译不展示，呈现权下放给统一呈现层与表单内联投影，使任何一次失败恰好产生一条人类可读的中文提示
**Verified:** 2026-11-07T09:42:10Z
**Status:** passed
**Re-verification:** Yes — targeted re-verification of the single D-02 gap after commit `94c1475`. Everything else was re-confirmed by diff scoping: `git log --since=2026-11-07T09:18:13Z` contains only `94c1475`, and that commit touches exactly two files (`stores/push.ts`, `ErrorLayerSample.spec.ts`). No backend or other frontend file changed since the prior verdict.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | ROADMAP SC1 — 字段校验失败（400 + fieldErrors）时，对应输入框下方出现中文内联红字，顶部出现 `el-alert` 汇总，零 toast | ✓ VERIFIED | `Profile.vue:23-27` 汇总为首个子元素，8 处 `:error="fieldErrors[...]"` + `data-field`；`useFormServerErrors.ts:77-87` 仅处理 `kind==='validation'`；`FormErrorSummary.vue:39-66` 的 `<ol>` 是 el-alert 兄弟节点。`ProfileErrorSample.spec.ts:174-232` 断言 toast 0 次、标题「请修正以下 2 项填写问题」、内联错误、已填值保留（219 项前端测试全绿） |
| 2 | ROADMAP SC2 — 业务失败/5xx/断网只出现一条中文提示，说明「什么失败了 + 下一步」，无泛化文案、无英文原文与字段名 | ✓ VERIFIED | 机制：`showError.ts:120-241`（字符串/VNode 两分支、最多 3 条、2000ms VNode 闩锁）；`errorMessages.ts:12-31` 18 条场景文案（`errorMessages.spec.ts` 断言无「操作失败/请求失败/系统错误/出错了」及英文词）。后端：DTO 英文注解消息 0 处；`BusinessException` 英文字面量 0 处；`GlobalExceptionHandler.java` 禁用词 0 处；`DeviceSyncController.buildErrorPage` 6 处全为固定中文；设备同步四个生产文件用户可见英文字面量 0。**注意**：本真值指「呈现机制 + 文案来源」，不等同于全站调用点数量 —— 见真值 15 与 deferred。 |
| 3 | ROADMAP SC3 — 取消确认框（`'cancel'`/`'close'`）或取消报告生成（`AbortError`/`CanceledError`）时零红色提示 | ✓ VERIFIED | `appError.ts:41-53` 五形态哨兵（字符串 2 + `CanceledError`/`AbortError`/`ERR_CANCELED`）；`normalizeApiError:84` 归 `cancel`；`request.ts:65-67` 直接 reject；`showError.ts:129` 零 toast。`appError.spec.ts:67-85`、`request.spec.ts:127-133`、`showError.spec.ts:97-105`、`ErrorLayerSample.spec.ts:175-183` 覆盖 |
| 4 | ROADMAP SC4 — 后台轮询/元数据预加载失败无 toast；令牌过期并发请求最多一条提示与一次跳转，且闩锁在登录成功后复位 | ✓ VERIFIED | `App.vue:18/23` 均传 `{ silent: true }`；`metricMeta.ts:59-84`、`alert.ts:11-22` 透传；`axios.d.ts:8` 模块增强；`showError.ts:131` silent 零 toast；`request.ts:71-75` 401 闩锁 + 单次 `logout({reason:'expired'})`；`authSession.ts` 全文 18 行零 import；`auth.ts:28` 登录成功 `resetAuthLatch()`。`request.spec.ts:139-165`（并发 401 单次 logout、复位后再次通知）、`auth.spec.ts:43-50`、`metricMeta.spec.ts:119-127` 证明 |
| 5 | ROADMAP SC5 — 短时间内相同错误提示自动合并为一条 | ✓ VERIFIED | 字符串分支 `grouping: true`（`showError.ts:173-182`）；VNode 分支 `isDuplicateVNodeToast` 2000ms 模块级闩锁（`:95-106`、`:188`）；`registerToast` 最多 3 条且跳过 retry 类（`:66-78`）。`showError.spec.ts:133/164-179/245/252` 用假计时器证明；`ErrorLayerSample.spec.ts:185-199` 断言连续两次网络失败 ≤ 1 条 |
| 6 | 后端字段校验响应含 `fieldErrors[]`（field/message/code），同时消费 `getFieldErrors()` 与 `getGlobalErrors()`，上限 20 条 | ✓ VERIFIED | `GlobalExceptionHandler.java:111-127` 两条循环 + `:117` `.limit(MAX_FIELD_ERRORS)`（`:44` = 20）；`:294-309` `extractField()` 缺失路径归 `_global`；`FieldErrorVO.java:16` record 三键 |
| 7 | `Result.code` 数值语义与 `parseCodeToInt` 的契约、`errorCode`/`fieldErrors`/`errors` 为逐字段 `@JsonInclude(NON_EMPTY)` | ✓ VERIFIED（语义已修订，见右） | **2026-11-16 修订**：原表述为“`Result.code` 数值语义不变”，该约束已被 `ee4d534` 主动推翻 —— `handleBusinessException` 改为 `Result.failure(getHttpStatus(), code, message)`，业务失败的信封 `code` 现在等于 `ErrorCode.httpStatus`（404/409/400），不再恒为回退值 500。修订后的契约：`parseCodeToInt` 本身仍原样回退 500 且未改（`:140-146`），仍是 `failure(String,String)` 的路径；显式传数值码的调用方（含 `PreventionController` 的 3 处 `failure(404, msg)`）行为不变。三字段逐字段 `@JsonInclude(NON_EMPTY)`（`:38-51`）与 `failure(int,String,String)` / `invalidInput`（`:104-123`）均未变。**该修订使 truth 15/16 的“一次失败一次呈现”契约更强而非更弱**：404 现在数值可判定，前端无需仅靠 `errorCode` 反推。回归证据：`GlobalExceptionHandlerTest` 断言 `code == 409` 与 404 族 `code == 404`；后端 849 / 0 failures。 |
| 8 | `request.ts` 零 `ElMessage`、抛出类型化 ApiError、固定 `Accept-Language: zh-CN`、含 401 闩锁与 retryRequest | ✓ VERIFIED | `request.ts` 全文无 ElMessage；`:26` Accept-Language；`:52`/`:55` 两分支 `normalizeApiError`；`:59-62` retryRequest 挂载；`:71-75` 闩锁 |
| 9 | `normalizeApiError` 对已归一化 ApiError 幂等（同一引用，先于 isCancel） | ✓ VERIFIED | `appError.ts:81` `if (raw instanceof ApiError) return raw` 位于 `:84` isCancel 之前；`appError.spec.ts` 以 `toBe` 断言同一引用 |
| 10 | `utils/` 只有 `showError.ts` import ElMessage；`composables/` 与 `components/common/` 无 ElMessage import | ✓ VERIFIED | `grep -rn "ElMessage" src/utils src/composables src/components/common` → 6 行命中全部落在 `showError.ts`；`spec` 用例 5 另行断言 `stores/push.ts` 源码无 `ElMessage.error` |
| 11 | FormErrorSummary：`<ol>` 是 el-alert 兄弟节点、标题用模块级计数器（非 useId）、标题 14px/600/1.4、列表 max-height 144px + overflow-y auto | ✓ VERIFIED | `FormErrorSummary.vue:4` `let summaryIdSeq = 0`；`:49-66` 兄弟结构；`:85-92` 显式 `font-size:14px; font-weight:600; line-height:1.4` + `color: var(--hhs-color-error-text)`；`:94-105` `max-height:144px; overflow-y:auto` |
| 12 | ErrorState 使用 `role="status"` + `aria-live="polite"`（非 role=alert），未提供 retry 时不渲染按钮，retrying 时 aria-busy | ✓ VERIFIED | `ErrorState.vue:50-55`；`:69-79` `v-if="retry"`；全文件无 `role="alert"`、无 focus 调用 |
| 13 | 双主题错误色 token 与 EP 覆盖：`--hhs-color-error-text` = `#b91c1c`（light）/ `#f56c6c`（dark）；`.el-form-item__error` line-height 1.4 + padding-top 4px；`.el-message` 12px 16px + 1.5 行高；`.el-message--error` 只染 error 修饰符 | ✓ VERIFIED | `theme.css:58-59`（dark）、`:167-168`（light `[data-theme='light']`）；`:1193-1215` 四条全局规则均用 `body .类名`（特异性 0,1,1，与 EP 路由 chunk 注入顺序解耦，无 `!important`）；`:1220-1252` `.error-toast` 规则 |
| 14 | D-03 — 网络/传输层 5xx toast 自动带「重试」，业务失败不出现死按钮；重试再次失败仍重新呈现 | ✓ VERIFIED | `appError.ts:37/117-128/141` retryable 仅 network/server 为 true；`request.ts:59-62` 挂 retryRequest；`showError.ts:150-162` 自动 retry + 重试失败重走 showError，`:162` `hasRetry && err.retryable`。`showError.spec.ts:209-253` 覆盖自动重试、重试失败重呈现、业务失败无重试 |
| 15 | 「使任何一次失败恰好产生一条人类可读的中文提示」—— 每个呈现面最多呈现一次（D-02 的固定分工） | ✓ VERIFIED | **D-02 缺口已关闭（94c1475）。** `stores/push.ts:34` `loadConfigs()` 不再接受 `options`；`:43` 固定 `showError(error, 'server.unavailable', { silent: true })`，`:131` 的 silent 早退保证零 toast，只把 `err.message`（5xx 由 `appError.ts:133-143` 归一为「服务暂时不可用，请稍后重试」）写入 `loadError`；`PushSettings.vue:18-26` 的 ErrorState 成为唯一呈现面与唯一重试入口。全部 3 个调用点（`PushSettings.vue:10/23/172`）均为无参调用，store 仅在返回对象中导出函数；无残留参数。回归测试 `ErrorLayerSample.spec.ts:201-212` 断言 `toastCount()===0` 且 `loadError` 真值；`store.loadConfigs()` 在真实 Chromium 中经 500 强制后页面为 `无法加载推送配置 / 服务暂时不可用，请稍后重试 / 重试` 且 `.el-message` 计数为 0 |
| 16 | D-01 双呈现覆盖客户端 `el-form` 校验失败（不只服务端 400） | ✓ VERIFIED | `useFormServerErrors.ts:96-98` `setErrors`；`Profile.vue:287-315` 摊平 `invalidFields` 后 `setErrors(...)` + 聚焦汇总；`useFormServerErrors.spec.ts:40-63` 两条用例（含拷贝隔离） |
| 17 | OAuth 回调失败页只显示固定中文（不含 `error_description`、原始平台串、异常文本）；未知平台立即拒绝 | ✓ VERIFIED | `OAuthCallback.vue:126-136` `OAUTH_ERROR_MESSAGES` 8 码白名单 + 默认兜底；`:169-172` 未知平台 `PLATFORM_NAMES` 校验；`:183-186` 只取白名单映射；`:210-212` catch 不用 `err.message`；`:151` platformName 回退「该设备」。`OAuthCallbackError.spec.ts` 3 条用例 |
| 18 | CR-01 回归：登录/注册失败由调用点呈现，恰好一条中文提示，无英文原文 | ✓ VERIFIED | `Login.vue:213-216` `showError(error, 'auth.loginFailed')`；`Register.vue:280-284` 同理；`errorMessages.ts:26-27` 两条字典键。`LoginAuthError.spec.ts` 断言 errorToastCount=1、文案取后端中文、零成功提示 |

**Score:** 18/18 truths verified (0 present-but-behavior-unverified)

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | 逐页替换约 87 处生产裸 `ElMessage.error` | Phase 14 | `11-CONTEXT.md:16`；`REQUIREMENTS.md` ERR-03/05/07/08 → Phase 14 |
| 2 | 403/404 状态页、ERR-03/05/07/08 | Phase 14 | `REQUIREMENTS.md:116-123` |
| 3 | OAuth 非法 state 未即时校验 | Phase 14 | `11-UAT.md:197-198` |
| 4 | 「分类: 消息」拼接读感重复 | Phase 14 | `11-UAT.md:199-200` |

### Advisory (New Scope, Unevidenced)

| # | Finding | Category | Why Advisory |
|---|---------|----------|--------------|
| 1 | `FileUploadController.java:104` 把 `e.getMessage()` 拼进 HTTP 500 信封 | security | 本阶段未修改该文件，不在上一版 gaps，无确定性证据 |
| 2 | `OcrServiceImpl.java:289` `failed(e.getMessage())` → `summary` → `OcrInput.vue:105` 直渲 | security | 该行是 13dd26b3 旧代码（文件虽因 WR-03 修改，此行为未动）；无失败测试 |
| 3 | `AiParseServiceImpl.java:197`、`ExportServiceImpl.java:158` 异常原文下发 | security | 两文件自上一版验证以来未修改，无确定性证据 |
| 4 | `Alerts.vue:200/210/225` 「操作失败」泛化文案 | other | 存量代码，属 Phase 14 调用点迁移 |
| 5 | `PushSettings.vue` 右侧卡片：`loadStats` 失败永久停在「加载中...」，`loadHistory` 失败显示「暂无推送记录」 | other | `push.ts:101-125` 本轮未改动、行为与上一版一致（WR-09 次要区块静默降级）；非「同一失败两次呈现」，不违反 at-most-once 不变式。Phase 14 次要区块状态化观察项 |

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `hhs-backend/.../vo/FieldErrorVO.java` | 字段级错误 record | ✓ VERIFIED | `public record FieldErrorVO(String field, String message, String code)` |
| `hhs-backend/.../common/Result.java` | errorCode/fieldErrors/errors 加法式扩展 | ✓ VERIFIED | 逐字段 NON_EMPTY；`parseCodeToInt` 未改 |
| `hhs-backend/.../exception/GlobalExceptionHandler.java` | 统一异常映射，中文兜底 | ✓ VERIFIED | 双循环 + limit 20；`SYSTEM_FALLBACK_MESSAGE`；禁用词 0 |
| `hhs-backend/.../DeviceSyncOrchestrationServiceImpl.java` | 6 处失败文案中文、无 `e.getMessage()` 回显 | ✓ VERIFIED | 6 处全中文（`:69/74/81/117/124/139`） |
| `hhs-backend/.../HuaweiHealthServiceImpl.java` | OAuth + syncData 中文文案 | ✓ VERIFIED | 用户可见文案全中文；SYSTEM_ERROR 英文 detail 保留但被 `systemFallbackMessage` 屏蔽 |
| `hhs-backend/.../XiaomiHealthServiceImpl.java` | 与华为对称 | ✓ VERIFIED | 同上 |
| `hhs-backend/.../controller/DeviceSyncController.java` | 回调页固定中文，不回显 | ✓ VERIFIED | `:152/158/163/169/178/181` 全为固定中文；`buildErrorPage` 不接 `e.getMessage()` |
| `hhs-backend/.../DeviceSyncControllerPageTest.java` | 回调页无英文回显门禁 | ✓ VERIFIED | 存在于测试目录；后端 724 项测试含该类全绿 |
| `hhs-frontend-v2/src/utils/appError.ts` | ApiError/normalizeApiError/isCancel | ✓ VERIFIED | 158 行，五形态取消，7 步分类顺序，幂等直通 |
| `hhs-frontend-v2/src/utils/authSession.ts` | 零依赖 401 闩锁 | ✓ VERIFIED | 18 行，零 import |
| `hhs-frontend-v2/src/utils/request.ts` | 只翻译不展示 | ✓ VERIFIED | 零 ElMessage；:7-8 兼容导出；retryRequest 挂载 |
| `hhs-frontend-v2/src/utils/errorMessages.ts` | 扁平字典 + FIELD_LABELS | ✓ VERIFIED | 18 条文案、17 个字段标签 |
| `hhs-frontend-v2/src/utils/showError.ts` | 唯一 toast 出口 | ✓ VERIFIED | 241 行；四通道早退；retry/action VNode；去重与 3 条上限 |
| `hhs-frontend-v2/src/composables/useFormServerErrors.ts` | 字段错误投影 | ✓ VERIFIED | apply / setErrors / clearField / reset / focusField / summaryItems |
| `hhs-frontend-v2/src/components/common/FormErrorSummary.vue` | GOV.UK 顶部汇总 | ✓ VERIFIED | 兄弟节点、模块计数器、可聚焦条目、UI-SPEC 排版值 |
| `hhs-frontend-v2/src/components/common/ErrorState.vue` | 页面级错误块 | ✓ VERIFIED | 5 变体、无死按钮、role=status、retrying 处理 |
| `hhs-frontend-v2/src/assets/styles/theme.css` | 错误 token + 全局 EP 覆盖 | ✓ VERIFIED | 双主题 token；4 条 `body .类名` 规则；`.error-toast` |
| `hhs-frontend-v2/src/types/axios.d.ts` | silent 模块增强 | ✓ VERIFIED | `:8` |
| `hhs-frontend-v2/src/types/api.ts` | errorCode/fieldErrors | ✓ VERIFIED | `:2-7` FieldError，`:10-17` ApiResponse |
| `hhs-frontend-v2/src/views/prevention/Profile.vue` | 双呈现参考实现 | ✓ VERIFIED | 8 个 `data-field` + 8 处 `:error`；服务端 `apply` 与客户端 `setErrors` 共用汇总 |
| `hhs-frontend-v2/src/stores/push.ts` | 单一出口 + 返回值契约 | ✓ VERIFIED | **已修复**：`loadConfigs()` 无参、固定 `{ silent: true }`，只写 `loadError`（`:34-48`）；saveConfig/testChannel 仍走唯一出口并返回布尔；模块内无 `ElMessage.error` |
| `hhs-frontend-v2/src/views/user/PushSettings.vue` | 错误态渲染 | ✓ VERIFIED | `:18-26` ErrorState 为加载失败的唯一呈现面（title/description/retry/retrying 齐备）；store 侧不再并发 toast |
| `hhs-frontend-v2/src/views/health/Thresholds.vue` | resource.exists 恢复动作 | ✓ VERIFIED | `:200-210` 只传 onClick，标签由 `ERROR_CODE_ACTIONS` 自动补；`:240-242` 删除重试重跑整个动作 |
| `hhs-frontend-v2/src/views/oauth/OAuthCallback.vue` | 固定中文错误页 | ✓ VERIFIED | 8 码白名单；不回显 `error_description`/平台串/异常 |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `request.ts` | `appError.ts` | 两分支 normalizeApiError | ✓ WIRED | `:52`、`:55` |
| `request.ts` | `authSession.ts` | 401 闩锁 | ✓ WIRED | `:4`、`:71-75`；`:8` 再导出 |
| `request.ts` | `stores/auth.ts` | 401 单次 logout(expired) | ✓ WIRED | `:74` |
| `stores/auth.ts` | `authSession.ts` | login 成功复位 | ✓ WIRED | `:6`、`:28` |
| `showError.ts` | `appError.ts` | normalizeApiError + kind 分支 | ✓ WIRED | `:12`、`:125` |
| `showError.ts` | `errorMessages.ts` | resolveMessage | ✓ WIRED | `:13`、`:135` |
| `showError.ts` | `theme.css` | `.error-toast` 类名与 token | ✓ WIRED | `:199/204-227` ↔ `theme.css:1220-1252` |
| `useFormServerErrors.ts` | `appError.ts` | 只处理 validation | ✓ WIRED | `:79` |
| `useFormServerErrors.ts` | `errorMessages.ts` | fieldLabel | ✓ WIRED | `:11`、`:48-52` |
| `Profile.vue` | `useFormServerErrors` + `FormErrorSummary` + `showError` | 双呈现接线 | ✓ WIRED | `:23-27`、`:203`、`:307-341` |
| `App.vue` | `metricMeta.ts` / `alert.ts` | silent 透传 | ✓ WIRED | `:18`、`:23` → `metricMeta.ts:67`、`alert.ts:15` |
| `stores/push.ts` | `showError.ts` | store 出口 | ✓ WIRED | **已修复**：saveConfig（`:70`）与 testChannel（`:88/:91`）呈现瞬时失败；loadConfigs（`:43`）仅分类，`silent: true` 零 toast。三条路径互不重叠，无双重呈现 |
| `PushSettings.vue` | `ErrorState` | loadError → 错误块 | ✓ WIRED | `:18-26`，唯一呈现面；`retry` 绑定 `loadConfigs()`（`:23`），`retrying` 绑定 `loading`（`:25`） |
| `Thresholds.vue` | `showError.ts` | resource.exists onClick + 删除 retry | ✓ WIRED | `:200-210`、`:240-242` |
| `OAuthCallback.vue` | `PLATFORM_NAMES` / 白名单 | 固定中文 | ✓ WIRED | `:126-136`、`:169-186` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `FormErrorSummary.vue` | `summaryItems` | `useFormServerErrors.fieldErrors` ← `ApiError.fieldErrors` ← 信封 `fieldErrors` | Yes（`GlobalExceptionHandler` 实投影） | ✓ FLOWING |
| `Profile.vue` 内联错误 | `fieldErrors[prop]` | 同上（含客户端 `invalidFields` 分支） | Yes | ✓ FLOWING |
| `showError` toast | `shown` | `resolveMessage(fallbackKey, err.message)` ← 后端中文 | Yes | ✓ FLOWING |
| `OAuthCallback.vue` 错误文案 | `errorMessage` | 白名单常量 / 固定字面量 | Yes（固定中文，无外部输入） | ✓ FLOWING |
| `PushSettings.vue` 错误块 | `pushStore.loadError` | `showError(...,{silent:true}).message` ← `appError.ts:133-143` 的固定中文，或后端中文 | Yes | ✓ FLOWING（**单一通道**：silent 分支不产生 toast） |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| D-02 回归用例（单条） | `npx vitest run src/__tests__/views/ErrorLayerSample.spec.ts -t "4b."` | `1 passed \| 8 skipped`（2.88s） | ✓ PASS |
| ErrorLayerSample 整文件 | `npx vitest run src/__tests__/views/ErrorLayerSample.spec.ts` | 9 tests passed | ✓ PASS |
| 前端全量单测 | `npm --prefix hhs-frontend-v2 run test:run` | 25 files / 219 tests passed, 0 failed（12.62s） | ✓ PASS |
| 后端构建 + 单测 | `mvn -f hhs-backend/pom.xml clean verify -DskipITs`（上一版验证，后端零改动） | `Tests run: 724, Failures: 0, Errors: 0`；checkstyle severity=error 计 0 | ✓ PASS（沿用，`git diff adf4f94..HEAD -- hhs-backend` 为空） |
| 前端构建 | `npm --prefix hhs-frontend-v2 run build` | vue-tsc + vite build 成功（17.08s） | ✓ PASS |
| 前端 lint | `npm --prefix hhs-frontend-v2 run lint:check` | 0 errors / 200 warnings | ✓ PASS |
| utils 目录 ElMessage 唯一出口 | `grep -rn "ElMessage" src/utils src/composables src/components/common` | 6 行命中全部落在 `showError.ts` | ✓ PASS |
| `loadConfigs` 调用点无残留参数 | `grep -rn "loadConfigs" src` | `push.ts:34/141`、`PushSettings.vue:10/23/172`、spec:208 —— 全部无参 | ✓ PASS |
| `loadConfigs` 无死 `silent` 选项 | `grep -n "silent\|options" src/stores/push.ts` | `:43` 固定 `silent: true`；`:101/:116` 的 `options` 属于 loadHistory/loadStats（未改动、仍在用）；`:30` 为过去时注释，非过期声明 | ✓ PASS |
| 生产裸 `ElMessage.error` 存量 | `grep -rn "ElMessage.error" src --include=*.vue --include=*.ts \| grep -v __tests__ \| wc -l` | 87 | ℹ️ INFO（Phase 14 显式延后，较上版无变化） |
| 债务标记（`TBD`/`FIXME`/`XXX`） | `grep -nE "TBD\|FIXME\|XXX" src/stores/push.ts src/__tests__/views/ErrorLayerSample.spec.ts` | 0 | ✓ PASS |

### Probe Execution

No probe scripts declared by this phase and no `scripts/*/tests/probe-*.sh` exists. Step 7c: not applicable.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| ERR-01 | 11-01, 11-03, 11-05, 11-06, 11-07 | `request.ts` 只翻译不呈现，失败抛类型化 `ApiError`，一次失败只出现一次提示 | ✓ SATISFIED | 翻译层完全成立（request.ts 零 ElMessage、两分支归一化、ApiError 幂等）；「一次失败只出现一次提示」的最后一处违例（推送设置页加载路径）已由 94c1475 关闭，并由 `ErrorLayerSample.spec.ts:201-212` 锁定 |
| ERR-02 | 11-01, 11-02, 11-04, 11-05, 11-07, 11-08 | 所有面向用户的错误文案为中文且人性化，清除英文校验原文 | ✓ SATISFIED | DTO 注解英文消息 0；`BusinessException` 英文字面量 0；设备同步/OAuth 四个文件用户可见英文 0；OAuth 回调页 3 条回归测试证明不回显 |
| ERR-04 | 11-04, 11-07 | 统一错误文案字典，替换泛化「操作失败/请求失败」 | ✓ SATISFIED | `errorMessages.ts` 18 条场景文案；error-layer 目录零泛化词；未迁移调用点属 Phase 14 显式延后 |
| ERR-06 | 11-03, 11-04, 11-07 | 短时间内相同错误自动合并 | ✓ SATISFIED | 字符串 grouping + VNode 2000ms 闩锁 + 3 条上限，均有假计时器测试 |

**Orphaned requirements check:** `REQUIREMENTS.md` 只把 ERR-01/02/04/06 映射到 Phase 11，且四者均出现在至少一个计划的 `requirements` 字段中（11-01/02/03/04/05/06/07/08 合并覆盖）。无孤儿需求。

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `hhs-frontend-v2/src/views/health/Alerts.vue` | 200/210/225 | 「操作失败」泛化文案 | ⚠️ Warning | 存量，Phase 14 调用点迁移范围（已 deferred） |
| `hhs-backend/.../OcrServiceImpl.java` | 289 | `failed(e.getMessage())` 下发异常原文 | 📋 Advisory | 旧代码，无确定性证据；见 Advisory 表 |

> **已关闭**：`stores/push.ts:38` 的「写 `loadError` 前先 `showError`」与 `PushSettings.vue:18-25` 的双重呈现配对缺陷，由 `94c1475` 修复；`grep -n "silent" src/stores/push.ts` 确认 `:43` 恒为 `true`，`:30` 的旧行为仅作为过去时历史注释保留，不构成可走通的逃生舱。

### Human Verification Required

None. All must-haves have automated evidence; no truth remains ⚠️ PRESENT_BEHAVIOR_UNVERIFIED. The D-02 truth (15) now has both a unit-level behavioural assertion (`ErrorLayerSample.spec.ts:201-212`) and a real-browser observation recorded from the interim Playwright probe.

### Gaps Summary

**No open gaps. The single blocker from the previous report is closed.**

The previous verification failed truth 15 on exactly one path: `PushSettings.vue`'s parameterless `loadConfigs()` produced a persistent error toast from `stores/push.ts` *and* an `ErrorState` block from `loadError` — one failure presented twice with two retry affordances, contrary to D-02.

Commit `94c1475` resolves it with the second of the two fix directions the prior report named: `loadConfigs()` no longer accepts options and calls `showError(error, 'server.unavailable', { silent: true })` unconditionally (`push.ts:34/43`). The silent early-return at `showError.ts:131` guarantees zero toast; the classified `ApiError.message` is still written to `loadError`, so `PushSettings.vue:18-26` remains a fully functional presentation + retry surface. Ownership now matches D-02 exactly: page-level load failures → error block; transient action failures (`saveConfig`, `testChannel`) → toast.

**Closing evidence:**

1. New regression test `ErrorLayerSample.spec.ts:201-212` asserts `toastCount() === 0` and `store.loadError` truthy on an HTTP 500 — it fails against the pre-fix implementation and passes now (single-test run: `1 passed | 8 skipped`).
2. The interim real-browser check (temporary Playwright spec, since deleted) rendered `无法加载推送配置 / 服务暂时不可用，请稍后重试 / 重试` with `document.querySelectorAll('.el-message').length === 0`.
3. Call-site audit: `loadConfigs` appears only at `push.ts:34/141`, `PushSettings.vue:10/23/172`, and the spec — every call is parameterless, so the signature narrowing breaks nothing and leaves no dead `silent` plumbing. The `options` still present in `loadHistory`/`loadStats` (`:101/:116`) is a different pair of methods, unchanged and still consumed.
4. No stale comment: the removed note claiming background refresh could opt into silence is now an explicit past-tense regression note (`:25-33`), and `grep` shows no code path that can turn the toast back on.
5. Diff scoping: `git log --since=<prior verdict>` contains only `94c1475`, and it touches only `stores/push.ts` + `ErrorLayerSample.spec.ts`. No backend or other frontend file changed, so the other 17 truths are untouched — the full frontend suite (25 files / 219 tests), build, and lint are green as a combined regression guard.

The ~87 residual bare `ElMessage.error` sites, `Alerts.vue` generic copy, and ERR-03/05/07/08 remain Phase 14 scope, as documented in Deferred Items. Advisory items 1-4 are unchanged from the prior report; item 5 (PushSettings stats/history secondary-block state) is recorded for Phase 14 and is pre-existing, not a regression from this fix.

---

_Verified: 2026-11-07T09:42:10Z_
_Verifier: Claude (gsd-verifier)_
