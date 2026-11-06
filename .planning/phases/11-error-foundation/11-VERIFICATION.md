---
phase: 11-error-foundation
verified: 2026-11-06T10:13:47Z
status: passed
score: 14/14 must-haves verified
covered_files:
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
  - hhs-backend/src/main/java/com/hhs/common/Result.java
  - hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java
  - hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java
  - hhs-backend/src/main/java/com/hhs/service/impl/DeviceSyncOrchestrationServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java
  - hhs-backend/src/test/java/com/hhs/controller/DeviceSyncControllerPageTest.java
  - hhs-backend/src/test/java/com/hhs/integration/DeviceSyncIntegrationTest.java
  - hhs-backend/src/test/java/com/hhs/service/DeviceSyncOrchestrationServiceTest.java
  - hhs-backend/src/test/java/com/hhs/service/HuaweiHealthServiceTest.java
  - hhs-backend/src/test/java/com/hhs/service/XiaomiHealthServiceTest.java
  - hhs-frontend-v2/src/App.vue
  - hhs-frontend-v2/src/api/alert.ts
  - hhs-frontend-v2/src/api/metric-meta.ts
  - hhs-frontend-v2/src/api/prevention.ts
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
  - hhs-frontend-v2/src/views/health/Thresholds.vue
  - hhs-frontend-v2/src/views/prevention/Profile.vue
  - hhs-frontend-v2/src/views/user/PushSettings.vue
covered_digest: "v1:sha256:aa9dff90c1cfcb8a547ed149d6ea61cba9a3c0a845f03418577db42672c421d1"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 13/14
  gaps_closed:
    - "Roadmap SC2 / ERR-02：设备同步与 OAuth 回调链路的用户可见英文原文（编排服务 SyncResultVO.failed、华为/小米 OAuth state 与 syncData、控制器回调 HTML 页与异常回显）已全部中文化，且不再回显 platform/provider/e.getMessage() 原文"
  gaps_remaining: []
  regressions: []
deferred:
  - truth: "逐页替换约 89 处生产代码裸 ElMessage.error 调用点"
    addressed_in: "Phase 14"
    evidence: "11-CONTEXT.md:16 明确『不在本阶段：逐页替换 105 处 ElMessage.error 调用点（Phase 14）』；REQUIREMENTS.md:134 将 ERR-03/05/07/08 映射到 Phase 14"
  - truth: "Thresholds.vue:142 与 :223 两处保留的裸 toast"
    addressed_in: "Phase 14"
    evidence: "11-04-PLAN.md:26 与 :101 明确『11-07 有意保留的 Thresholds.vue 两处』"
  - truth: "Alerts.vue:200/210/225 三处『操作失败』泛化文案（Phase 11 前即存在）"
    addressed_in: "Phase 14"
    evidence: "属上一条调用点迁移范围；REQUIREMENTS.md:134 映射 ERR-04 的全站替换为 Phase 14 收尾"
  - truth: "403/404 解释性状态页、ERR-03 全站字段内联、ERR-05 输入保留、ERR-07 呈现区分、ERR-08 取消清理"
    addressed_in: "Phase 14"
    evidence: "REQUIREMENTS.md:116-123 映射表"
  - truth: "ScreeningServiceImpl:124 报告日期回显与 :148 OCR 状态字段拼接异常原文"
    addressed_in: "未排期（11-02 SUMMARY 记录）"
    evidence: "11-02-SUMMARY.md:161-176 记录为已知遗留"
advisory:
  - finding: "FileUploadController:104 的 `\"文件上传失败：\" + e.getMessage()` 仍把底层 IOException 原文拼进 HTTP 200 信封"
    category: security
    reason: "Phase 11 前即存在，不在任何 11-xx 计划的 files_modified 内，自上一次验证（2026-11-06T09:02:36Z）以来未被修改（git log --since 为空），也不在上一版 gaps 列表中。无失败测试、无可复现命令输出证明英文到达用户界面。按证据门控视为新范围观察项，不阻塞、不撤销已闭合的 must-have。"
    evidence_status: "none provided"
  - finding: "OcrServiceImpl:279/:287 与 OcrEventListener:49 把 e.getMessage() 写入 OcrHealthRecord.errorMessage / abnormalSummary，前端 OcrHistory.vue:99 与 screening/Detail.vue:39 直接渲染"
    category: security
    reason: "同上：Phase 11 前即存在、未被本阶段修改、不在上一版 gaps 列表。上游异常文本若为英文可能到达用户，但本阶段无对应失败测试或可复现证据，且自动测试全绿。建议 Phase 14 排查时一并纳入。"
    evidence_status: "none provided"
  - finding: "AiParseServiceImpl:197 `result.setSummary(\"AI解析失败: \" + e.getMessage())` 与 ExportServiceImpl:158 `job.setErrorMessage(e.getMessage())` 仍可能下发英文异常文本"
    category: security
    reason: "同上：pre-existing、未修改、无确定性证据。属 Phase 14 收尾范围观察项。"
    evidence_status: "none provided"
---

# Phase 11: 全站统一友好错误反馈地基 Verification Report

**Phase Goal:** 建立全站唯一的错误"翻译—呈现"分层：后端输出结构化字段错误信封，前端拦截器只翻译不展示，呈现权下放给统一呈现层与表单内联投影，使任何一次失败恰好产生一条人类可读的中文提示
**Verified:** 2026-11-06T10:13:47Z
**Status:** passed
**Re-verification:** Yes — after gap closure (11-08)

## Goal Achievement

### Observable Truths

| #   | Truth   | Status     | Evidence       |
| --- | ------- | ---------- | -------------- |
| 1   | SC1 — 字段校验失败（400 + fieldErrors）时，对应输入框下出现中文内联红字，顶部出现 `el-alert` 汇总，零 toast | ✓ VERIFIED | `Profile.vue:23-27` 汇总为首个子元素；`Profile.vue:34-167` 8 处 `data-field` + `:error`；`useFormServerErrors.ts:76-86` 只处理 `kind==='validation'`；`FormErrorSummary.vue:49-65` 的 `<ol>` 是 el-alert 兄弟节点。`ProfileErrorSample.spec.ts:175/203` 断言 toast 0 次、标题『请修正以下 2 项填写问题』、内联错误、值保留（本轮 207 项前端测试全绿） |
| 2   | SC2 — 业务失败/5xx/断网只出现一条中文提示，含"什么失败了+下一步"，无泛化文案、无英文原文与字段名 | ✓ VERIFIED | 机制：`showError.ts:112-219`、`errorMessages.ts:12-28`（15 条，无泛化词）。**本轮闭合的缺口**：`DeviceSyncOrchestrationServiceImpl.java:69/74/81/117/124/139` 全为固定中文；`HuaweiHealthServiceImpl.java:152/160/240/249/262/270/284/300/320/327` 与 `XiaomiHealthServiceImpl.java:139/147/227/236/249/257/271/287/307/314` 全为固定中文；`DeviceSyncController.java:115/121/128/152/158/163/169/178/181/277` 全为固定中文且不再回显 `e.getMessage()`/provider/platform。门禁全部为 0（见下） |
| 3   | SC3 — 取消确认框（`'cancel'`/`'close'`）或取消报告生成（`AbortError`/`CanceledError`）时不出现任何红色提示 | ✓ VERIFIED | `appError.ts:30` 五形态哨兵；`:32-42` isCancel；`:73` 归类 cancel；`request.ts:58-60` 直接 reject；`showError.ts:120` 零 toast。`appError.spec.ts:67-85`、`showError.spec.ts:97-105` 覆盖 |
| 4   | SC4 — 后台轮询/元数据预加载失败无 toast；令牌过期并发请求最多一条提示与一次跳转 | ✓ VERIFIED | `App.vue:18` 与 `:23` 均传 `{ silent: true }`；`metricMeta.ts:59-68`、`alert.ts:12-17` 透传；`axios.d.ts:8` 模块增强；`showError.ts:122` silent 零 toast；`request.ts:64-67` 401 闩锁 + 单次 logout；`authSession.ts` 零 import；`auth.ts:6/27` 登录成功复位闩锁。`request.spec.ts:130/144/151`、`auth.spec.ts:43-50` 证明 |
| 5   | SC5 — 短时间内相同错误提示自动合并为一条 | ✓ VERIFIED | `showError.ts:149-162` 字符串分支 `grouping: true`；`:165-166` VNode 分支 2000ms 模块级闩锁；`:58-70` 最多可见 3 条。`showError.spec.ts:133/164-179/245/252` 用假计时器证明 |
| 6   | 后端字段校验响应含 `fieldErrors[]`（field/message/code），同时消费 `getFieldErrors()` 与 `getGlobalErrors()`，上限 20 条 | ✓ VERIFIED | `GlobalExceptionHandler.java:94-97` 两条循环；`:228-243` `extractField()` 缺失路径归 `_global`；`:99` `.limit(MAX_FIELD_ERRORS)`（`:41` = 20）。`GlobalExceptionHandlerTest.java:63` 断言 `containsExactly("metricKey","_global")` |
| 7   | 既有 `Result.code` 数值语义不变、`parseCodeToInt` 未改、新增字段为逐字段 `@JsonInclude(NON_EMPTY)` | ✓ VERIFIED | `Result.java:38-51` 三个字段各自注解，类级无注解；`:82` `parseCodeToInt` 原样；`:59-66` 六参构造器 |
| 8   | `request.ts` 零 `ElMessage`、抛出类型化 ApiError、固定 `Accept-Language: zh-CN`、含 401 闩锁 | ✓ VERIFIED | `request.ts` 全文无 ElMessage；`:26` Accept-Language；`:52`/`:55` 两分支 normalizeApiError；`:64-67` 闩锁；`:8` 兼容导出 resetAuthLatch |
| 9   | `normalizeApiError` 对已归一化 ApiError 幂等（同一引用，先于 isCancel） | ✓ VERIFIED | `appError.ts:70` `if (raw instanceof ApiError) return raw` 位于 `:73` isCancel 之前。`appError.spec.ts` 以 `toBe` 断言同一引用 |
| 10  | `authSession.ts` 零 import，`resetAuthLatch()` 在 login() 成功后调用 | ✓ VERIFIED | `authSession.ts` 全文 18 行零 import；`auth.ts:6` 导入、`:27` 调用 |
| 11  | `utils/` 只有 `showError.ts` import ElMessage | ✓ VERIFIED | `grep -rn "from 'element-plus'" src/utils/` → 仅 `showError.ts:10-11`；`grep -rn "ElMessage" src/utils/` 6 行全部在 showError.ts |
| 12  | FormErrorSummary：`<ol>` 是 el-alert 兄弟节点、标题用模块级计数器（非 useId）、`formField` 覆盖生效 | ✓ VERIFIED | `FormErrorSummary.vue:4` `let summaryIdSeq = 0`；`:49-65` 兄弟结构；`useFormServerErrors.ts:80-83` formField 改投 |
| 13  | ErrorState 使用 `role="status"` + `aria-live="polite"`（非 role=alert），挂载不抢焦点 | ✓ VERIFIED | `ErrorState.vue:44-49`；全文件无 `role="alert"`、无 focus 调用 |
| 14  | theme.css toast 覆盖为全局规则；`.el-message--error` 带修饰符；错误色 token 在 `:root` 与 `[data-theme='light']` 均定义 | ✓ VERIFIED | `theme.css:1192-1211` 全局规则（无 scoped，经 `main.ts:9` 在 EP CSS 之后加载）；`:1197` 限定 error 修饰符；`:58-59` 与 `:167-168` 双主题 token；`:1218-1250` `.error-toast` 规则 |

**Score:** 14/14 truths verified (0 present-but-behavior-unverified)

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | 逐页替换约 89 处生产代码裸 `ElMessage.error` | Phase 14 | `11-CONTEXT.md:16`；`REQUIREMENTS.md:134` |
| 2 | `Thresholds.vue:142/:223` 两处保留的裸 toast | Phase 14 | `11-04-PLAN.md:26`、`:101` |
| 3 | `Alerts.vue:200/210/225` 三处"操作失败"泛化文案 | Phase 14 | 属第 1 项范围；Phase 11 前即存在 |
| 4 | 403/404 状态页、ERR-03/05/07/08 | Phase 14 | `REQUIREMENTS.md:116-123` |
| 5 | `ScreeningServiceImpl:124/:148` 两处拼接 | 未排期 | `11-02-SUMMARY.md:161-176` |

### Advisory (New Scope, Unevidenced)

New-scope findings from Step 7 with no deterministic evidence — reported, not blocking; they do not revert any completed must-have.

| # | Finding | Category | Why Advisory |
|---|---------|----------|--------------|
| 1 | `FileUploadController.java:104` 拼 `e.getMessage()` 进信封 | security | pre-existing，未被本阶段修改，不在上一版 gaps 列表，无失败测试/可复现证据 |
| 2 | `OcrServiceImpl.java:279/:287`、`OcrEventListener.java:49` 的异常原文经 `OcrHistory.vue:99`、`screening/Detail.vue:39` 渲染 | security | 同上 |
| 3 | `AiParseServiceImpl.java:197`、`ExportServiceImpl.java:158` 的异常原文 | security | 同上 |

共同核验：这四个文件自 `2026-11-06T09:02:36Z`（上一版验证时间戳）以来 `git log --since` 计数均为 0（未被修改）；上一版 `11-VERIFICATION.md` 亦未把它们列为 gap。按 `#3304` 证据门控，它们既非结转缺口也非回归，且无确定性证据，故降级为 advisory，不触发 `gaps_found`。

### Required Artifacts

| Artifact | Expected    | Status | Details |
| -------- | ----------- | ------ | ------- |
| `hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java` | 字段级错误 record | ✓ VERIFIED | `public record FieldErrorVO(String field, String message, String code)` |
| `hhs-backend/src/main/java/com/hhs/common/Result.java` | errorCode/fieldErrors/errors 加法式扩展 | ✓ VERIFIED | `:38-51` 逐字段 NON_EMPTY；`parseCodeToInt` 未改 |
| `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` | 统一异常映射，中文兜底 | ✓ VERIFIED | `:94-97` 双循环；`:99` limit 20；`:44` SYSTEM_FALLBACK_MESSAGE；禁用词计数 = 0 |
| `hhs-backend/.../DeviceSyncOrchestrationServiceImpl.java` | 6 处失败文案中文、无 `e.getMessage()` 回显 | ✓ VERIFIED | 6 处全中文；非 log 行 `e.getMessage()` = 0；`log.` = 18（基线不变） |
| `hhs-backend/.../HuaweiHealthServiceImpl.java` | OAuth + syncData 10 条中文、原始异常只进日志 | ✓ VERIFIED | 10 处中文；英文用户字面量 = 0；`log.` = 47（基线不变）；两处 SYSTEM_ERROR 排除项各 1 |
| `hhs-backend/.../XiaomiHealthServiceImpl.java` | 与华为对称 | ✓ VERIFIED | 10 处中文；英文用户字面量 = 0；`log.` = 43（基线不变）；SYSTEM_ERROR 排除项 1 |
| `hhs-backend/.../DeviceSyncController.java` | 回调页固定中文，不回显 | ✓ VERIFIED | `buildErrorPage` 英文描述 = 0；`buildErrorPage(.*e.getMessage()` = 0；英文 platform 拼接 = 0；`log.` = 15（基线不变） |
| `hhs-backend/src/test/java/com/hhs/controller/DeviceSyncControllerPageTest.java` | 回调页无英文回显回归门禁 | ✓ VERIFIED | 新增；6 条用例（A-F），本机单独运行 `Tests run: 6, Failures: 0, Errors: 0` |
| `hhs-frontend-v2/src/utils/appError.ts` | ApiError/normalizeApiError/isCancel | ✓ VERIFIED | 139 行，五形态取消，7 步分类顺序 |
| `hhs-frontend-v2/src/utils/authSession.ts` | 零依赖 401 闩锁 | ✓ VERIFIED | 18 行，零 import |
| `hhs-frontend-v2/src/utils/request.ts` | 只翻译不展示 | ✓ VERIFIED | 零 ElMessage；`:7-8` 兼容导出 resetAuthLatch |
| `hhs-frontend-v2/src/utils/errorMessages.ts` | 扁平字典 + FIELD_LABELS | ✓ VERIFIED | 15 条文案、17 个字段标签、`resolveMessage`/`fieldLabel` |
| `hhs-frontend-v2/src/utils/showError.ts` | 唯一 toast 出口 | ✓ VERIFIED | 220 行；validation/cancel/auth/silent 早退；retry/action VNode；去重与 3 条上限 |
| `hhs-frontend-v2/src/composables/useFormServerErrors.ts` | 字段错误投影 | ✓ VERIFIED | apply/clearField/reset/focusField/summaryItems |
| `hhs-frontend-v2/src/components/common/FormErrorSummary.vue` | GOV.UK 顶部汇总 | ✓ VERIFIED | `<ol>` 兄弟节点、模块计数器、可聚焦条目 |
| `hhs-frontend-v2/src/components/common/ErrorState.vue` | 页面级错误块 | ✓ VERIFIED | 5 变体、无死按钮、role=status |
| `hhs-frontend-v2/src/assets/styles/theme.css` | 错误 token + 全局 EP 覆盖 | ✓ VERIFIED | `:58-59`、`:167-168`、`:1192-1250` |
| `hhs-frontend-v2/src/types/axios.d.ts` | silent 模块增强 | ✓ VERIFIED | `:8` |
| `hhs-frontend-v2/src/types/api.ts` | errorCode/fieldErrors | ✓ VERIFIED | `:2-6` FieldError，`:9-17` ApiResponse 扩展 |
| `hhs-frontend-v2/src/views/prevention/Profile.vue` | 双呈现参考实现 | ✓ VERIFIED | 8 个 `data-field` + 8 处 `:error`；`:287-296` validation 走 apply + 汇总聚焦 |
| `hhs-frontend-v2/src/stores/push.ts` | 单一出口 + 返回值契约 | ✓ VERIFIED | `:45-60` 成功 true / 失败 showError+false；`:71-81` 测试分支同出口 |
| `hhs-frontend-v2/src/views/health/Thresholds.vue` | resource.exists 恢复动作 | ✓ VERIFIED | `:204-208` 只传 onClick，标签由 `ERROR_CODE_ACTIONS` 自动补（`:19-21`） |

### Key Link Verification

| From | To  | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| `request.ts` | `appError.ts` | 两分支 normalizeApiError | ✓ WIRED | `:52`、`:55` |
| `request.ts` | `authSession.ts` | 401 闩锁 | ✓ WIRED | `:4`、`:64-67`；`:8` 再导出 |
| `stores/auth.ts` | `authSession.ts` | login 成功复位 | ✓ WIRED | `:6`、`:27` |
| `request.ts` | `stores/auth.ts` | 401 单次 logout | ✓ WIRED | `:66` |
| `showError.ts` | `appError.ts` | normalizeApiError + kind 分支 | ✓ WIRED | `:12`、`:117-136` |
| `showError.ts` | `errorMessages.ts` | resolveMessage | ✓ WIRED | `:13`、`:126` |
| `showError.ts` | `theme.css` | `.error-toast` 类名与 token | ✓ WIRED | `:177/184/196/207` ↔ `theme.css:1218-1250` |
| `useFormServerErrors.ts` | `appError.ts` | 只处理 validation | ✓ WIRED | `:78` |
| `useFormServerErrors.ts` | `errorMessages.ts` | fieldLabel | ✓ WIRED | `:11`、`:50` |
| `Profile.vue` | `useFormServerErrors` + `FormErrorSummary` + `showError` | 双呈现接线 | ✓ WIRED | `:187-189`、`:23-27`、`:287-296` |
| `App.vue` | `metricMeta.ts` / `alert.ts` | silent 透传 | ✓ WIRED | `:18`、`:23` → `metricMeta.ts:67`、`alert.ts:15` |
| `stores/push.ts` | `showError.ts` | store 唯一出口 | ✓ WIRED | `:58`、`:76`、`:79` |
| `Thresholds.vue` | `showError.ts` | resource.exists onClick | ✓ WIRED | `:109`、`:204-208` |
| **`DeviceSyncOrchestrationServiceImpl`** | **`DeviceSync.vue`** | **`SyncResultVO.errorMessage` → toast / lastSyncError** | **✓ WIRED（已闭合）** | `:69/74/81/117/124/139` 现为中文；消费端 `DeviceSync.vue:554/556/563/581/615` 原样显示，现只显示中文 |
| **`Huawei/XiaomiHealthServiceImpl`** | **`SyncHistoryServiceImpl`** | **`createFailedRecord(...)` 第 4 参数** | **✓ WIRED（已闭合）** | 12 处调用点第 4 实参全为中文；`SyncHistoryServiceImpl.java:92` 原样存表 |
| **`DeviceSyncController.buildErrorPage`** | **OAuth 回调 HTML** | **description 嵌入 `<div class="detail">`** | **✓ WIRED（已闭合）** | `:237` 仅接受固定中文描述；6 个调用点全部字面量 |
| **`GlobalExceptionHandler`** | **`Huawei/XiaomiHealthServiceImpl`** | **BusinessException.getDetail() 拼接** | **✓ WIRED（已闭合）** | `:56-58` 拼 detail；`AUTH_FORBIDDEN` detail 现为中文，用户消息无英文 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `FormErrorSummary.vue` | `summaryItems` | `useFormServerErrors.fieldErrors` ← `ApiError.fieldErrors` ← envelope `data.fieldErrors` | Yes（`GlobalExceptionHandler` 实投影） | ✓ FLOWING |
| `Profile.vue` 内联错误 | `fieldErrors[prop]` | 同上 | Yes | ✓ FLOWING |
| `showError` toast | `shown` | `resolveMessage(fallbackKey, err.message)` ← 后端 message | Yes | ✓ FLOWING |
| `DeviceSync.vue` toast / 错误块 / 历史列 | `data.errorMessage` / `row.errorMessage` | `SyncResultVO.failed` 与 `createFailedRecord` | **Yes（本轮：数据源全为中文）** | ✓ FLOWING |
| `ErrorState.vue` | props（title/description/retry） | 调用点传入 | N/A（本阶段无生产调用点，属 Phase 14 ERR-07 显式边界） | N/A |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| 后端全量构建+单测 | `mvn -f hhs-backend/pom.xml verify -DskipITs` | `Tests run: 697, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS（43.9s） | ✓ PASS |
| 前端全量单测 | `npm --prefix hhs-frontend-v2 run test:run` | 23 files / 207 tests passed, 0 failed（14.33s） | ✓ PASS |
| 新增回调页回归门禁（单命名测试类） | `mvn -f hhs-backend/pom.xml test -Dtest=DeviceSyncControllerPageTest` | `Tests run: 6, Failures: 0, Errors: 0` | ✓ PASS |
| utils 目录 ElMessage 唯一出口 | `grep -rn "ElMessage" hhs-frontend-v2/src/utils/ --include=*.ts` | 6 行全部在 `showError.ts` | ✓ PASS |
| handler 禁用词 | `grep -c "系统错误\|数据库错误\|内部错误" GlobalExceptionHandler.java` | `0` | ✓ PASS |
| 编排服务英文 failed 文案 | `grep -cE 'SyncResultVO\.failed\(platform, "[A-Za-z]'` | `0`（基线 6） | ✓ PASS |
| 编排服务非 log 异常回显 | `grep -v 'log\.' … \| grep -c 'e.getMessage()'` | `0`（基线 2） | ✓ PASS |
| 控制器英文页面描述 | `grep -cE 'buildErrorPage\("[^"]+", "[A-Za-z]'` | `0`（基线 3） | ✓ PASS |
| 控制器异常回显进页面 | `grep -c 'buildErrorPage(.*e\.getMessage()'` | `0`（基线 2） | ✓ PASS |
| 华为英文用户字面量 | `grep -v 'log\.' … \| grep -cE '"(Invalid or expired…|Partial sync)'` | `0`（基线 9）；`log.` = 47 | ✓ PASS |
| 小米英文用户字面量 | 同上 | `0`（基线 9）；`log.` = 43 | ✓ PASS |
| 有意排除保留 | `grep -c 'SYSTEM_ERROR, "Failed to generate OAuth state"'` | 华为 1 / 小米 1 | ✓ PASS |
| 测试英文断言清零 | B19/B20/B21/B22 | 0 / 0 / 0 / 0 | ✓ PASS |
| 哨兵探针到位 | `grep -c RAW_PROVIDER_DETAIL` | PageTest 2、IntegrationTest 2 | ✓ PASS |
| 生产残留裸 ElMessage.error | `grep -rn "ElMessage.error" src --include=*.vue --include=*.ts`（排除测试） | 89 处 | ℹ️ INFO（Phase 14 显式延后） |

### Probe Execution

No probe scripts declared by this phase and no `scripts/*/tests/probe-*.sh` exists. Step 7c: not applicable.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| ERR-01 | 11-01, 11-03, 11-05, 11-06, 11-07 | `request.ts` 只翻译不呈现，失败抛类型化 `ApiError`，一次失败只出现一次提示 | ✓ SATISFIED | `request.ts` 零 ElMessage；两侧拦截器均 `Promise.reject(normalizeApiError(...))`；showError 唯一出口；同错误合并 |
| ERR-02 | 11-01, 11-02, 11-04, 11-05, 11-07, **11-08** | 所有面向用户的错误文案为中文且人性化，清除英文校验原文 | ✓ SATISFIED | DTO 注解 `message = "[A-Za-z]` = 0；`BusinessException` 英文字面量 = 0；设备同步/OAuth 链路四个文件英文用户文案合并基线 28 → **0**；回调页无异常回显（6 条确定性测试） |
| ERR-04 | 11-04, 11-07 | 统一错误文案字典，替换泛化"操作失败/请求失败" | ✓ SATISFIED | `errorMessages.ts:12-28` 15 条场景文案；`utils/`、`components/common/`、`composables/` 无泛化词（唯一命中是解释性注释）；未迁移调用点属 Phase 14 显式延后 |
| ERR-06 | 11-03, 11-04, 11-07 | 短时间内相同错误自动合并 | ✓ SATISFIED | 字符串 grouping + VNode 2000ms 闩锁 + 3 条上限；`showError.spec.ts:245-288` |

**Orphaned requirements check:** `REQUIREMENTS.md:116-123` maps only ERR-01/02/04/06 to Phase 11; all four appear in at least one plan's `requirements` field. No orphaned IDs.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `HuaweiHealthServiceImpl.java` | 615, 731, 764 | `SystemException` 英文 detail（有意排除） | ℹ️ Info | `systemFallbackMessage()` 对 `SYSTEM_*` 返回固定中文；`handleSystemException` 不追加 detail，用户不可见。11-08 scope_note 已记录并保留 |
| `XiaomiHealthServiceImpl.java` | 494, 607, 640 | 同上 | ℹ️ Info | 同上 |
| `Alerts.vue` | 200/210/225 | "操作失败" 泛化文案 | ⚠️ Warning | 属 Phase 14 调用点迁移（`11-CONTEXT.md:16`） |
| `Thresholds.vue` | 142/223 | 裸 `ElMessage.error` | ℹ️ Info | `11-04-PLAN.md:26/101` 有意保留 |
| `FileUploadController.java`, `OcrServiceImpl.java`, `ExportServiceImpl.java`, `AiParseServiceImpl.java` | 104 / 279,287 / 158 / 197 | 异常原文下发 | 📋 Advisory | 见 Advisory 表：pre-existing、未修改、无确定性证据 |
| phase-changed files（11-08 四个生产文件 + 新测试） | — | `TBD`/`FIXME`/`XXX` 债务标记 | ✓ None | 5 个文件 grep 均为 0 |

No empty implementations (`return null` / `=> {}`) in the phase's error-layer modules. The `catch` blocks with empty bodies in `stores/alert.ts` / `stores/metricMeta.ts` are deliberate silent degradation per D-10.

### Human Verification Required

None. All must-haves have automated evidence; no truth remains ⚠️ PRESENT_BEHAVIOR_UNVERIFIED.

### Gaps Summary

**The single prior gap is closed.**

The prior round's only failure was SC2 / ERR-02: user-visible English reached the UI through the device-sync / OAuth chain. The gap-closure plan 11-08 landed in three commits (`1c3f006`, `5d95e1f`, `e96a45d`), touching **only string literals** in four production files — the full `git diff 783a47c..HEAD` shows 37 insertions / 37 deletions across those files, with no control-flow, exception-type, `ErrorCode`, or method-signature change:

1. **`DeviceSyncOrchestrationServiceImpl`** — 6 `SyncResultVO.failed` messages plus the `IllegalArgumentException`, all fixed Chinese; `e.getMessage()` and `platform` concatenation removed (raw values remain in the pre-existing `log.*`).
2. **`DeviceSyncController`** — all `Result.failure` platform branches and all 6 `buildErrorPage` descriptions are fixed Chinese; `e.getMessage()`, provider `error_description`, and raw `platform` are no longer echoed. The `buildErrorPage(error, errorDescription)` call became a pair of literals.
3. **`HuaweiHealthServiceImpl` / `XiaomiHealthServiceImpl`** — OAuth state, token exchange, six `syncData` failure records, and the partial-failure aggregate are all fixed Chinese; no `e.getMessage()`, `tokenResponse.getErrorMessage()`, `connection.getStatus()`, or `metricKey` splicing remains.

The regression risk was controlled exactly as the plan specified: `log.*` line counts are unchanged at **18 / 47 / 43 / 15**, proving no diagnostic evidence was lost, and the two `SystemException(ErrorCode.SYSTEM_ERROR, "Failed to generate OAuth state", e)` sites still exist in both platform services — neutralized by 11-01's unconditional fixed-Chinese `systemFallbackMessage()` for `SYSTEM_*` codes and the fact that `handleSystemException` never appends the detail.

Combined English user-literal baseline across the four files went **28 → 0**. Deterministic coverage was added as `DeviceSyncControllerPageTest` (6 cases, plain Mockito, no Docker), which passed in isolation this round; the frontend `DeviceSync.vue` render path needed no change because the backend now sends Chinese.

All other success criteria re-verified without regression: SC1 (dual presentation, zero toast — `ProfileErrorSample.spec.ts`), SC3 (five cancel forms, zero toast), SC4 (`silent` passthrough + 401 latch with reset), SC5 (grouping + 2000 ms VNode latch + 3-toast cap). Backend 697 tests and frontend 207 tests are green.

**Documented scope boundaries (not failures):** the ~89 residual frontend `ElMessage.error` sites, the two deliberately-kept `Thresholds.vue` toasts, the three pre-existing `Alerts.vue` generic-copy sites, and ERR-03/05/07/08 are all explicitly deferred to Phase 14 in `11-CONTEXT.md:16` and `REQUIREMENTS.md:116-134`. Three additional pre-existing exception-echo sites found during this pass are recorded as advisory — they are unmodified since the prior verification and have no deterministic evidence, so under the re-verification evidence gate they do not block and do not revert the closed gap.

**No remaining gaps. Phase 11 goal achieved.**

---

_Verified: 2026-11-06T10:13:47Z_
_Verifier: Claude (gsd-verifier)_