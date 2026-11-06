---
phase: 11-error-foundation
verified: 2026-11-06T09:02:36Z
status: gaps_found
score: 13/14 must-haves verified
covered_files:
  - hhs-backend/src/main/java/com/hhs/common/Result.java
  - hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java
  - hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java
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
covered_digest: "v1:sha256:7d2dd65db101bc8b2784b6bbb371e31b9df27d537afa300c06eacc215310fa61"
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "Roadmap SC2: 当后端返回业务失败（HTTP 200 且 code != 200）、HTTP 5xx 或断网时，用户只看到一条中文提示，说明『什么失败了 + 下一步怎么做』，不再出现『操作失败/请求失败』这类泛化文案，也不再出现英文原文与字段名"
    status: failed
    reason: "错误层机制严格成立（拦截器只翻译、showError 单一出口、字典无泛化词），但后端仍有用户可见的英文原文经设备同步链路下发到前端 toast 与错误块，且部分英文 BusinessException detail 经 OAuth 回调 HTML 页直接渲染。D-16 要求既有英文 BusinessException 字面量在本阶段一并改为中文，未达成。"
    artifacts:
      - path: "hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java"
        issue: '第 151-152 行 BusinessException(AUTH_FORBIDDEN, "Invalid or expired OAuth state. Please try connecting again.")；第 159-160 行 SystemException(AUTH_FAILED, "Token exchange failed: " + tokenResponse.getErrorMessage())'
      - path: "hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java"
        issue: '第 138-139 行 BusinessException(AUTH_FORBIDDEN, "Invalid or expired OAuth state. Please try connecting again.")；第 146-147 行 SystemException(AUTH_FAILED, "Token exchange failed: " + tokenResponse.getErrorMessage())'
      - path: "hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java"
        issue: '第 158/163/169 行 buildErrorPage 描述为 "Missing authorization code" / "Missing state parameter" / "Unknown platform: " + platform；第 176-181 行 catch(Exception) 把 e.getMessage() 原文写入用户可见 HTML（"授权失败"页的 detail 行）'
      - path: "hhs-backend/src/main/java/com/hhs/service/impl/DeviceSyncOrchestrationServiceImpl.java"
        issue: '第 69/74/81/117/124/139 行 SyncResultVO.failed 的 errorMessage 为 "Unknown platform: " / "Platform not configured" / "Platform service not available" / "Sync error: " + e.getMessage()，经 Result.success 下发后由 DeviceSync.vue 第 563/581 行原样显示为 toast 与 lastSyncError.message'
      - path: "hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java"
        issue: '第 240/249/262 行 createFailedRecord 的英文 "Device not connected. Please connect your Huawei Health account first." / "Device connection is not active. Status: ..." / "Failed to authenticate with Huawei Health..."（XiaomiHealthServiceImpl 第 227/236/257/271 行同类）'
    missing:
      - "把 DeviceSyncOrchestrationServiceImpl 的 6 处 SyncResultVO.failed 文案、Huawei/Xiaomi syncData 的 createFailedRecord 文案、DeviceSyncController OAuth 回调页描述与异常回显、两处 BusinessException AUTH_FORBIDDEN OAuth state 字面量统一改为中文，且不再回显底层异常原文"
---

# Phase 11: 全站统一友好错误反馈地基 Verification Report

**Phase Goal:** 建立全站唯一的错误"翻译—呈现"分层：后端输出结构化字段错误信封，前端拦截器只翻译不展示，呈现权下放给统一呈现层与表单内联投影，使任何一次失败恰好产生一条人类可读的中文提示
**Verified:** 2026-11-06T09:02:36Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth   | Status     | Evidence       |
| --- | ------- | ---------- | -------------- |
| 1   | SC1 — 字段校验失败（400 + fieldErrors）时，对应输入框下出现中文内联红字，顶部出现 `el-alert` 汇总，零 toast | ✓ VERIFIED | `Profile.vue:23-27` 汇总为首个子元素；`:error="fieldErrors['heightCm']"`（`Profile.vue:69` 等 8 处）；`useFormServerErrors.apply()` 只处理 `kind==='validation'`（`useFormServerErrors.ts:76-86`）；`FormErrorSummary.vue:49-65` 的 `<ol>` 是 el-alert 兄弟节点。`ProfileErrorSample.spec.ts:175` 断言 toast 0 次、标题『请修正以下 2 项填写问题』、两条内联错误、值保留 |
| 2   | SC2 — 业务失败/5xx/断网只出现一条中文提示，含"什么失败了+下一步"，无泛化文案、无英文原文与字段名 | ✗ FAILED | 机制成立（`showError.ts:112-219`、`errorMessages.ts:12-28` 无泛化词），但设备同步链路仍下发用户可见英文：`DeviceSyncOrchestrationServiceImpl.java:69/74/81/117/124/139` → `DeviceSync.vue:563/581` toast；`HuaweiHealthServiceImpl.java:151-152/159-160`、`XiaomiHealthServiceImpl.java:138-139`；`DeviceSyncController.java:158/163/169` 与 `:180` 异常原文进 HTML。详见 gaps |
| 3   | SC3 — 取消确认框（`'cancel'`/`'close'`）或取消报告生成（`AbortError`/`CanceledError`）时不出现任何红色提示 | ✓ VERIFIED | `appError.ts:30` 五形态哨兵；`:32-42` isCancel；`:73` 归类 cancel；`request.ts:58-60` 直接 reject；`showError.ts:120` 零 toast。`appError.spec.ts:68/83-85` 覆盖 close 哨兵 |
| 4   | SC4 — 后台轮询/元数据预加载失败无 toast；令牌过期并发请求最多一条提示与一次跳转 | ✓ VERIFIED | `App.vue:18` 与 `:23` 均传 `{ silent: true }`；`metricMeta.ts:59-68`、`alert.ts:12-17` 透传；`axios.d.ts:8` 模块增强；`showError.ts:122` silent 零 toast；`request.ts:64-67` 401 闩锁 + 单次 logout；`authSession.ts` 零 import；`auth.ts:27` 登录成功复位闩锁。`request.spec.ts:133`/`:147`、`auth.spec.ts:48` 证明 |
| 5   | SC5 — 短时间内相同错误提示自动合并为一条 | ✓ VERIFIED | `showError.ts:149-162` 字符串分支 `grouping: true`；`:165-166` VNode 分支 2000ms 模块级闩锁；`:58-70` 最多可见 3 条。`showError.spec.ts:245/252/263/275` 用假计时器证明 |
| 6   | 后端字段校验响应含 `fieldErrors[]`（field/message/code），同时消费 `getFieldErrors()` 与 `getGlobalErrors()`，上限 20 条 | ✓ VERIFIED | `GlobalExceptionHandler.java:94-97` 两条循环；`extractField()` `:228-243` 缺失路径归 `_global`；`:99` limit(20)。`GlobalExceptionHandlerTest.java:63` 断言 `containsExactly("metricKey","_global")`、`:84` 断言 20 条 |
| 7   | 既有 `Result.code` 数值语义不变、`parseCodeToInt` 未改、新增字段为逐字段 `@JsonInclude(NON_EMPTY)` | ✓ VERIFIED | `Result.java:38-51` 三个字段各自注解，类级无注解；`:140-146` parseCodeToInt 原样；`git diff 13dd26b` 该方法仅上下文行未变更。`:104-108` failure(code, errorCode, message) |
| 8   | `request.ts` 零 `ElMessage`、抛出类型化 ApiError、固定 `Accept-Language: zh-CN`、含 401 闩锁 | ✓ VERIFIED | `grep -rn "ElMessage" src/utils/` 仅命中 `showError.ts`；`request.ts:26` Accept-Language；`:52`/`:55` 两分支 normalizeApiError；`:64-67` 闩锁。`request.spec.ts:63` 断言请求头 |
| 9   | `normalizeApiError` 对已归一化 ApiError 幂等（同一引用，先于 isCancel） | ✓ VERIFIED | `appError.ts:70` `if (raw instanceof ApiError) return raw` 位于 `:73` isCancel 之前。`appError.spec.ts:31` 用 `toBe` 断言同一引用 |
| 10  | `authSession.ts` 零 import，`resetAuthLatch()` 在 login() 成功后调用 | ✓ VERIFIED | `authSession.ts` 全文仅 18 行无 import；`auth.ts:6/27` |
| 11  | `utils/` 只有 `showError.ts` import ElMessage | ✓ VERIFIED | `grep -rn "from 'element-plus'" src/utils/` → 仅 `showError.ts:10-11`（`request.ts` 归零） |
| 12  | FormErrorSummary：`<ol>` 是 el-alert 兄弟节点、标题用模块级计数器（非 useId）、`formField` 覆盖生效 | ✓ VERIFIED | `FormErrorSummary.vue:4` `let summaryIdSeq = 0`；`:49-65` 兄弟结构；`useFormServerErrors.ts:80-83` formField 改投。`FormErrorSummary.spec.ts:65` 断言 `nextElementSibling`；`:122` 两实例 id 不同 |
| 13  | ErrorState 使用 `role="status"` + `aria-live="polite"`（非 role=alert），挂载不抢焦点 | ✓ VERIFIED | `ErrorState.vue:44-49`；全文件无 `role="alert"`、无 focus 调用。`ErrorState.spec.ts:58/173` |
| 14  | theme.css toast 覆盖为全局规则；`.el-message--error` 带修饰符；错误色 token 在 `:root` 与 `[data-theme='light']` 均定义 | ✓ VERIFIED | `theme.css:1190-1213` 全局（无 scoped，文件经 `main.ts:9` 在 EP CSS 之后加载）；`:1197` 限定 error 修饰符；`:58-59` 与 `:167-168` 双主题 token |

**Score:** 13/14 truths verified (0 present-but-behavior-unverified)

### Deferred Items

Items not met in this phase but explicitly scheduled/recorded as out of scope.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | 逐页替换约 100 处（实测生产代码 89 处）裸 `ElMessage.error` | Phase 14 | `11-CONTEXT.md:16` 明确 "不在本阶段：逐页替换 105 处 ElMessage.error 调用点（Phase 14）"；REQUIREMENTS.md 把 ERR-03/05/07/08 映射到 Phase 14 |
| 2 | `Thresholds.vue:142` 与 `:223` 两处保留的裸 toast | Phase 14 | `11-04-PLAN.md:26` 与 `:101` 明确 "11-07 有意保留的 Thresholds.vue 两处" |
| 3 | `Alerts.vue:200/210/225` 的 3 处 "操作失败" 泛化文案（Phase 11 前即存在，`git show 13dd26b` 计数同为 3） | Phase 14 | 属第 1 项调用点迁移范围 |
| 4 | 403/404 解释性状态页、ERR-03 全站字段内联、ERR-05 输入保留、ERR-07 呈现区分、ERR-08 取消清理 | Phase 14 | REQUIREMENTS.md 映射表 |
| 5 | `ScreeningServiceImpl:124` 报告日期回显与 `:148` OCR 状态字段拼接异常原文 | 未排期（11-02 SUMMARY 记录） | `11-02-SUMMARY.md:165-176` |

### Required Artifacts

| Artifact | Expected    | Status | Details |
| -------- | ----------- | ------ | ------- |
| `hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java` | 字段级错误 record | ✓ VERIFIED | `:16` `public record FieldErrorVO(String field, String message, String code)` |
| `hhs-backend/src/main/java/com/hhs/common/Result.java` | errorCode/fieldErrors/errors 加法式扩展 | ✓ VERIFIED | `:38-51`、`:104-138`；`parseCodeToInt` 未改 |
| `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` | 统一异常映射，中文兜底 | ✓ VERIFIED | `:94-97` 双循环；`:44` SYSTEM_FALLBACK；`grep -c "系统错误\|数据库错误\|内部错误"` = 0 |
| `hhs-frontend-v2/src/utils/appError.ts` | ApiError/normalizeApiError/isCancel | ✓ VERIFIED | 139 行，五形态取消，7 步分类顺序 |
| `hhs-frontend-v2/src/utils/authSession.ts` | 零依赖 401 闩锁 | ✓ VERIFIED | 18 行，零 import |
| `hhs-frontend-v2/src/utils/request.ts` | 只翻译不展示 | ✓ VERIFIED | 零 ElMessage；`:7-8` 兼容导出 resetAuthLatch |
| `hhs-frontend-v2/src/utils/errorMessages.ts` | 扁平字典 + FIELD_LABELS | ✓ VERIFIED | 15 条文案、17 个字段标签、`resolveMessage`/`fieldLabel` |
| `hhs-frontend-v2/src/utils/showError.ts` | 唯一 toast 出口 | ✓ VERIFIED | 220 行；validation/cancel/auth/silent 早退；retry/action VNode；去重与 3 条上限 |
| `hhs-frontend-v2/src/composables/useFormServerErrors.ts` | 字段错误投影 | ✓ VERIFIED | apply/clearField/reset/focusField/summaryItems |
| `hhs-frontend-v2/src/components/common/FormErrorSummary.vue` | GOV.UK 顶部汇总 | ✓ VERIFIED | `<ol>` 兄弟节点、模块计数器、可聚焦条目 |
| `hhs-frontend-v2/src/components/common/ErrorState.vue` | 页面级错误块 | ✓ VERIFIED | 5 变体、无死按钮、role=status |
| `hhs-frontend-v2/src/assets/styles/theme.css` | 错误 token + 全局 EP 覆盖 | ✓ VERIFIED | `:58-59`、`:167-168`、`:1190-1250` |
| `hhs-frontend-v2/src/types/axios.d.ts` | silent 模块增强 | ✓ VERIFIED | `:8` |
| `hhs-frontend-v2/src/types/api.ts` | errorCode/fieldErrors | ✓ VERIFIED | `:9-17`，`FieldError` 定义 `:2-6` |
| `hhs-frontend-v2/src/views/prevention/Profile.vue` | 双呈现参考实现 | ✓ VERIFIED | 8 个 `data-field` + 8 处 `:error` + `clearField` 绑定；`:293` validation 走 apply；档案 404 静默进入创建模式 `:264` |
| `hhs-frontend-v2/src/stores/push.ts` | 单一出口 + 返回值契约 | ✓ VERIFIED | `:54-59` 成功 true / 失败 showError+false；`:71-81` 测试与异常分支 |
| `hhs-frontend-v2/src/views/health/Thresholds.vue` | resource.exists 恢复动作 | ✓ VERIFIED | `:204-208` 只传 onClick，标签由 `ERROR_CODE_ACTIONS` 自动补 |

### Key Link Verification

| From | To  | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| `request.ts` | `appError.ts` | 两分支 normalizeApiError | ✓ WIRED | `:52`、`:55` |
| `request.ts` | `authSession.ts` | 401 闩锁 | ✓ WIRED | `:64-67`；`:8` 再导出 resetAuthLatch |
| `stores/auth.ts` | `authSession.ts` | login 成功复位 | ✓ WIRED | `:6`、`:27` |
| `request.ts` | `stores/auth.ts` | 401 单次 logout | ✓ WIRED | `:66` |
| `showError.ts` | `appError.ts` | normalizeApiError + kind 分支 | ✓ WIRED | `:117`、`:120-136` |
| `showError.ts` | `errorMessages.ts` | resolveMessage | ✓ WIRED | `:126` |
| `showError.ts` | `theme.css` | `.error-toast` 类名与 token | ✓ WIRED | `:177/184/196/207` 与 `theme.css:1218-1250` |
| `useFormServerErrors.ts` | `appError.ts` | 只处理 validation | ✓ WIRED | `:78` |
| `useFormServerErrors.ts` | `errorMessages.ts` | fieldLabel | ✓ WIRED | `:50` |
| `FormErrorSummary.vue` | `theme.css` | error-tint / error-text | ✓ WIRED | `:73`、`:110`、`:118` |
| `Profile.vue` | `useFormServerErrors` + `FormErrorSummary` + `showError` | 双呈现接线 | ✓ WIRED | `:187-189`、`:23-27`、`:293-296`；`:200` 解构 |
| `App.vue` | `metricMeta.ts` / `alert.ts` | silent 透传 | ✓ WIRED | `:18`、`:23` → `metricMeta.ts:66-68` → `metric-meta.ts:11` |
| `stores/push.ts` | `showError.ts` | store 唯一出口 | ✓ WIRED | `:58`、`:76`、`:79`；`PushSettings.vue:215-218` 读布尔回滚 |
| `Thresholds.vue` | `showError.ts` | resource.exists onClick | ✓ WIRED | `:204-208` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `FormErrorSummary.vue` | `summaryItems` | `useFormServerErrors.fieldErrors` ← `ApiError.fieldErrors` ← envelope `data.fieldErrors` | Yes（`GlobalExceptionHandler` 实投影） | ✓ FLOWING |
| `Profile.vue` 内联错误 | `fieldErrors[prop]` | 同上 | Yes | ✓ FLOWING |
| `showError` toast | `shown` | `resolveMessage(fallbackKey, err.message)` ← 后端 message | Yes | ✓ FLOWING |
| `ErrorState.vue` | props（title/description/retry） | 调用点传入 | N/A（展示组件，本阶段无生产调用点，属 Phase 14 ERR-07 显式边界） | N/A |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| 前端全量单测 | `npm --prefix hhs-frontend-v2 run test:run` | 23 files / 207 tests passed, 0 failed（11.84s） | ✓ PASS |
| 后端全量构建+单测 | `mvn -f hhs-backend/pom.xml verify -DskipITs` | Tests run: 691, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS（42.7s） | ✓ PASS |
| utils 目录 ElMessage 唯一出口 | `grep -rn "ElMessage" hhs-frontend-v2/src/utils/ --include=*.ts` | 仅 `showError.ts`（请求的 6 行均在该文件） | ✓ PASS |
| handler 禁用词 | `grep -c "系统错误\|数据库错误\|内部错误" GlobalExceptionHandler.java` | 0（exit 1 = 无匹配） | ✓ PASS |
| handler 英文兜底 | `grep -n "System error\|Request body format error\|Resource not found\|Authentication required\|Invalid request\|Internal server error"` | 无匹配 | ✓ PASS |
| request.ts 零 ElMessage | `grep "ElMessage" request.ts` | 无匹配 | ✓ PASS |
| 生产残留裸 ElMessage.error | `grep -rn "ElMessage.error" src --include=*.vue --include=*.ts`（排除测试） | 89 处（Phase 14 范围，CONTEXT.md:16 显式延后） | ℹ️ INFO（documented deferral） |

### Probe Execution

No probe scripts declared by this phase and no `scripts/*/tests/probe-*.sh` exists. Step 7c: not applicable.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| ERR-01 | 11-01, 11-03, 11-05, 11-06, 11-07 | `request.ts` 只翻译不呈现，失败抛类型化 `ApiError`，一次失败只出现一次提示 | ✓ SATISFIED | `request.ts` 零 ElMessage；两侧拦截器均 `Promise.reject(normalizeApiError(...))`；showError 为唯一出口；8 条通道测试 |
| ERR-02 | 11-01, 11-02, 11-04, 11-05, 11-07 | 所有面向用户的错误文案为中文且人性化，清除英文校验原文 | ✗ BLOCKED | DTO 注解与 `UserThresholdServiceImpl`/`ScreeningServiceImpl`/`PushConfigController` 已中文化（`grep 'message = "[A-Za-z]'` = 0、`ValidationTest` 无英文断言），但设备同步/OAuth 链路仍有用户可见英文原文（见 gaps） |
| ERR-04 | 11-04, 11-07 | 统一错误文案字典，替换泛化"操作失败/请求失败"为"什么失败了+下一步" | ✓ SATISFIED | `errorMessages.ts:12-28` 15 条场景文案；`grep` 在 `utils/` 与 `components/common/` 无泛化词；未迁移调用点属 Phase 14 显式延后 |
| ERR-06 | 11-03, 11-04, 11-07 | 短时间内相同错误自动合并，避免多条重复 toast | ✓ SATISFIED | 字符串 grouping + VNode 2000ms 闩锁 + 3 条上限；`showError.spec.ts:245-288` |

**Orphaned requirements check:** REQUIREMENTS.md maps only ERR-01/02/04/06 to Phase 11 (`REQUIREMENTS.md:116-123`、`:131`). All four appear in at least one plan's `requirements` field. No orphaned IDs for this phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `HuaweiHealthServiceImpl.java` | 151-152, 159-160 | 英文 BusinessException/SystemException detail | 🛑 Blocker | 经回调 HTML 页/信封透出（见 gaps） |
| `XiaomiHealthServiceImpl.java` | 138-139, 146-147 | 同上 | 🛑 Blocker | 同上 |
| `DeviceSyncController.java` | 158/163/169/180 | 英文回调页描述 + `e.getMessage()` 回显进 HTML | 🛑 Blocker | 用户在 OAuth 授权失败页看到英文原文 |
| `DeviceSyncOrchestrationServiceImpl.java` | 69/74/81/117/124/139 | 英文 `SyncResultVO.errorMessage` | 🛑 Blocker | `DeviceSync.vue:563/581` toast 与错误块原样显示 |
| `HuaweiHealthServiceImpl.java` | 240/249/262/270/284 | 英文 `createFailedRecord` errorMessage | 🛑 Blocker | 同上（Xiaomi 227/236/257/271 同类） |
| `Alerts.vue` | 200/210/225 | "操作失败" 泛化文案 | ⚠️ Warning | 属 Phase 14 调用点迁移（11-CONTEXT.md:16 显式延后） |
| `Thresholds.vue` | 142/223 | 裸 `ElMessage.error` | ℹ️ Info | 11-04-PLAN.md:26/101 有意保留 |
| phase-changed files | — | `TBD`/`FIXME`/`XXX` 债务标记 | ✓ None | 10 个核心文件 grep 均为 0 |

No empty implementations (`return null` / `=> {}`) found in the phase's error-layer modules. The `catch` blocks with empty bodies in `stores/alert.ts` / `stores/metricMeta.ts` are deliberate silent-degradation per D-10, with documented state preservation, not stubs.

### Gaps Summary

核心错误地基（SC1、SC3、SC4、SC5 与全部机制层 must-have）真实成立并有自动化证据：前端 207 项、后端 691 项测试全绿；`utils/` 只剩 `showError.ts` 一处 ElMessage；拦截器零 UI 副作用；401 闩锁含复位；字段错误双呈现有端到端行为测试。

未达成的只有 Roadmap SC2 的"不再出现英文原文"这一条。错误层本身没有产生英文，但后端设备同步与 OAuth 回调链路仍把英文原文直接下发到用户可见位置：

1. `DeviceSyncOrchestrationServiceImpl` 的 `SyncResultVO.failed` 六处英文 errorMessage → DeviceSync.vue toast / 错误块；
2. `HuaweiHealthServiceImpl` / `XiaomiHealthServiceImpl` 的 `syncData` 失败记录与 `"Invalid or expired OAuth state..."` BusinessException；
3. `DeviceSyncController` 的回调错误页描述与 `e.getMessage()` 回显。

这些文件均不在 11-02 的 `files_modified` 清单内，且 CONTEXT.md 只把"105 处前端 ElMessage.error 调用点"延后到 Phase 14，未延后后端英文文案；D-16 反而明确要求"既有英文 BusinessException 字面量在本阶段一并改为中文"。因此这不是可记录为 Phase 14 的范围边界，而是本阶段 must-have 的实质缺口。

修复方向：把这些字面量统一改为中文并停止回显底层异常原文（原始错误只进日志），沿用 11-02 已建立的"源头治理"模式。

---

_Verified: 2026-11-06T09:02:36Z_
_Verifier: Claude (gsd-verifier)_