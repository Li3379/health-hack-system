---
phase: 11-error-foundation
plan: 08
subsystem: api
tags: [java, spring-boot, oauth, device-sync, error-messages, i18n-zh, security]

# Dependency graph
requires:
  - phase: 11-error-foundation
    plan: 01
    provides: "GlobalExceptionHandler systemFallbackMessage 对 SYSTEM_* 固定中文且 SystemException 分支不追加 detail"
  - phase: 11-error-foundation
    plan: 02
    provides: "后端英文字面量中文化的源头治理形状（DTO 注解 / BusinessException 字面量）"
provides:
  - "设备同步编排层 6 处失败文案固定中文（未知/未配置/服务缺失/同步异常/批量聚合）"
  - "OAuth 回调 HTML 错误页 6 条固定中文描述，不回显异常/provider/platform 原文"
  - "华为/小米平台服务 handleCallback 与 syncData 的 10 条用户可见文案中文化"
  - "DeviceSyncControllerPageTest：不依赖 Docker 的回调页无英文回显回归门禁"
affects: [phase-14-全站错误文案迁移, 设备同步前端 DeviceSync.vue]

actuals:
  tokens: 8920
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "用户可见文案只写固定中文，原始异常/provider/platform 细节只进既有 log.*"
    - "控制器 HTML 页用确定性 Mockito 单测锁死「不得回显原始字符串」"

key-files:
  created:
    - hhs-backend/src/test/java/com/hhs/controller/DeviceSyncControllerPageTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/service/impl/DeviceSyncOrchestrationServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java
    - hhs-backend/src/test/java/com/hhs/service/DeviceSyncOrchestrationServiceTest.java
    - hhs-backend/src/test/java/com/hhs/service/HuaweiHealthServiceTest.java
    - hhs-backend/src/test/java/com/hhs/service/XiaomiHealthServiceTest.java
    - hhs-backend/src/test/java/com/hhs/integration/DeviceSyncIntegrationTest.java

key-decisions:
  - "测试夹具中的英文异常文本改为 RAW_SYNC_DETAIL / RAW_PROVIDER_DETAIL 哨兵，既满足「不含英文前缀断言」门禁，又保留「原始异常未回显」的精确探针能力"
  - "Huawei/Xiaomi 测试用 ArgumentCaptor 断言 createFailedRecord 第 4 实参，证明的是生产文案而非回读 mock 夹具"
  - "两处 SYSTEM_ERROR SystemException 与四处 AUTH_FAILED SystemException 按计划「有意排除」保留原样"

patterns-established:
  - "用户可见字符串 = 固定中文；诊断文本 = 既有 log.*，两者严格分离"
  - "「不得回显」用唯一哨兵字符串 + not(containsString(...)) 断言，可同时用于单测与集成测试"

requirements-completed: [ERR-02]

coverage:
  - id: D1
    description: "设备同步失败（未知/未配置平台、服务缺失、同步异常）在 toast、lastSyncError 与历史表中只显示中文"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/DeviceSyncOrchestrationServiceTest.java#测试3.2/3.3/3.4/4.7"
        status: pass
    human_judgment: false
  - id: D2
    description: "OAuth 回调失败页只显示固定中文，不回显 Unknown platform / User denied access / 底层异常原文"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/controller/DeviceSyncControllerPageTest.java#用例A/C/D/E/F"
        status: pass
      - kind: integration
        ref: "hhs-backend/src/test/java/com/hhs/integration/DeviceSyncIntegrationTest.java#DEV-INT-014（需 Docker，本次未运行，由单测等价覆盖）"
        status: unknown
    human_judgment: false
  - id: D3
    description: "华为/小米 OAuth state 失效与凭证换取失败 detail 为中文，经 GlobalExceptionHandler 拼接后无英文"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/HuaweiHealthServiceTest.java#CallbackHandlingTests + XiaomiHealthServiceTest"
        status: pass
    human_judgment: false
  - id: D4
    description: "华为/小米 syncData 六条失败记录与部分失败聚合文案为中文，且不再拼接 metricKey/异常/状态"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/HuaweiHealthServiceTest.java#测试5.1/5.2/7.2 + XiaomiHealthServiceTest#SyncDataTests"
        status: pass
    human_judgment: false
  - id: D5
    description: "四个生产文件的 log.* 行数与内容保持不变（O=18 / H=47 / X=43 / C=15），原始诊断信息取证路径不变"
    requirement: ERR-02
    verification:
      - kind: other
        ref: "grep -c 'log\\.' 四个文件 → 18/47/43/15"
        status: pass
    human_judgment: false
  - id: D6
    description: "全后端单测不低于 691 项且全绿"
    requirement: ERR-02
    verification:
      - kind: other
        ref: "mvn -f hhs-backend/pom.xml verify -DskipITs → Tests run: 697, Failures: 0, Errors: 0, BUILD SUCCESS"
        status: pass
    human_judgment: false

duration: 16min
completed: 2026-11-06
status: complete
---

# Phase 11 Plan 08: 设备同步与 OAuth 链路用户文案中文化 Summary

**设备同步与 OAuth 回调链路的 28 处用户可见英文字面量归零，页面/信封不再回显原始异常、provider 描述或 platform 字符串，新增无 Docker 的回调页回归门禁**

## Performance

- **Duration:** 16 min
- **Started:** 2026-11-06T09:42Z (approx)
- **Completed:** 2026-11-06T09:58Z
- **Tasks:** 3
- **Files modified:** 9 (4 生产 + 5 测试，其中 1 个新增)

## Accomplishments

- 关闭 `11-VERIFICATION.md` 唯一失败项 SC2 / ERR-02：设备同步与 OAuth 链路的英文原文不再经 HTTP 200 信封、同步历史表或 OAuth HTML 页到达用户
- 合并英文字面量基线从 28 降到 0（O=7→0、C=3→0、H=9→0、X=9→0），四个生产文件的 `log.*` 行数保持 18 / 47 / 43 / 15 不变
- 新增 `DeviceSyncControllerPageTest` 6 条用例，以纯 Mockito 直接调用控制器 public 方法，在不依赖 Docker/Testcontainers 的前提下锁死"回调页无英文回显"
- 后端单测从 691 增至 697（新增 6 条），Failures 0 / Errors 0

## Task Commits

Each task was committed atomically:

1. **Task 1: 平台标识失败路径端到端** - `1c3f006` (feat)
2. **Task 2: 同步聚合与 OAuth 回调页推广** - `5d95e1f` (feat)
3. **Task 3: 华为/小米平台服务 OAuth 与 syncData 中文化** - `e96a45d` (feat)

Ledger: `plan_head_before: 783a47c09cba784f29e94e7e35e2aa8bc1644531`, `plan_head_after: e96a45d8f7ce179b1a7c5961f8afa2018a8a6407`, `commits: 3` (measured via `git rev-list --count`).

## Files Created/Modified

- `hhs-backend/src/main/java/com/hhs/service/impl/DeviceSyncOrchestrationServiceImpl.java` - 6 处 `SyncResultVO.failed` 文案固定中文；`IllegalArgumentException` 消息中文化；移除 `e.getMessage()` 拼接
- `hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java` - `Result.failure` 平台分支与 6 处 `buildErrorPage` 描述固定中文；不再回显 platform / provider `error_description` / `e.getMessage()`
- `hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java` - state 失效、凭证换取、四条 syncData 失败记录与部分失败聚合中文化
- `hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java` - 与华为对称的 10 处
- `hhs-backend/src/test/java/com/hhs/controller/DeviceSyncControllerPageTest.java` - **新增**，6 条回调页用例（未知平台 / 成功页 / 缺码 / 缺 state / provider 报错 / 服务异常）
- `hhs-backend/src/test/java/com/hhs/service/DeviceSyncOrchestrationServiceTest.java` - 4 处断言改中文 + 异常消息断言 + 哨兵断言
- `hhs-backend/src/test/java/com/hhs/service/HuaweiHealthServiceTest.java` - 夹具中文 + 2 处 `ArgumentCaptor` 捕获断言 + 部分失败用例补中文断言
- `hhs-backend/src/test/java/com/hhs/service/XiaomiHealthServiceTest.java` - 同上（补 `ArgumentCaptor` import）
- `hhs-backend/src/test/java/com/hhs/integration/DeviceSyncIntegrationTest.java` - `RAW_PROVIDER_DETAIL` 哨兵 + `not(containsString(...))` 断言 + 夹具文案

## Decisions Made

- **哨兵替代英文断言。** 计划建议在 `DeviceSyncOrchestrationServiceTest` 追加 `assertFalse(...contains("Sync error"))`，但 Task 2 的门禁同时要求 `contains("(Unknown platform|not configured|Sync error|not available)` 计数为 0 —— 两者互斥。改用 `RAW_SYNC_DETAIL` 作为异常哨兵并把断言写成 `assertFalse(...contains("RAW_SYNC_DETAIL"))`，既满足门禁 0 计数，又比英文前缀更精确地证明"原始异常未回显"。
- **断言生产文案而非 mock 回读。** Huawei/Xiaomi 的 `createFailedRecord` 第 4 实参用 `ArgumentCaptor<String>` 捕获后 `assertEquals` 中文串，避免用 `SyncResultVO.failed(...)` 夹具"自己证明自己"。

## Deviations from Plan

**1. [Rule 1 - Bug] 修正计划内自相矛盾的断言门禁**
- **Found during:** Task 2 (同步聚合与 OAuth 回调页)
- **Issue:** 计划 action 第 4 条要求在 `DeviceSyncOrchestrationServiceTest:318` 追加 `assertFalse(result.getErrorMessage().contains("Sync error"))`，而同一任务的 acceptance_criteria 要求 `grep -cE 'contains\("(Unknown platform|not configured|Sync error|not available)'` 结果为 0。按 action 写会让该门禁恒为 1，任务无法通过。
- **Fix:** 把夹具异常文本改为 `RAW_SYNC_DETAIL`，断言改为 `assertFalse(...contains("RAW_SYNC_DETAIL"))`。探针强度提高（哨兵唯一），门禁可满足。
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/DeviceSyncOrchestrationServiceTest.java`
- **Verification:** 门禁实测 0；该用例通过
- **Committed in:** `5d95e1f` (Task 2 commit)

**2. [Rule 1 - Bug] `buildErrorPage(error, errorDescription)` 改为一对固定参数**
- **Found during:** Task 2
- **Issue:** acceptance_criteria 要求 `grep -c 'buildErrorPage(error, errorDescription)'` 为 0，计划 action 要求把该调用改为 `buildErrorPage("provider_error", "授权未完成，请返回应用重新发起授权")`。这会把变量引用整体替换为字面量，属于必要变更而非误改。
- **Fix:** 按 action 执行；`buildErrorPage` 的签名与 `description != null ? description : error` 回退逻辑未动。
- **Files modified:** `hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java`
- **Verification:** `git diff` 复核：生产文件改动行只含字符串字面量，无 `if`/`catch`/`throw new <不同类型>`/`ErrorCode.` 变更
- **Committed in:** `5d95e1f` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1)
**Impact on plan:** 无范围扩张。偏差 1 是计划内部自相矛盾的必要消解；偏差 2 是计划 action 的直译结果，均不影响交付形状。

## Issues Encountered

- `DeviceSyncOrchestrationServiceTest` 的嵌套类在 surefire 报告里逐类计数，顶层报告显示 `Tests run: 0` 属正常（用例都在 `@Nested` 内），全量汇总为 697。
- Windows 工作树对 LF 文件产生 `LF will be replaced by CRLF` 警告（`.editorconfig` 要求 LF，但 core.autocrlf 为 true）。这是仓库既有配置的产物，未在本次改动范围内处理。

## Known Stubs

None — 本次改动未引入空实现、占位文案或未接线数据源。

## Threat Flags

None — 无新增网络端点、认证路径或文件访问模式；改动方向是**减少**信任边界上的信息外泄（T-11-25 / T-11-26 已缓解）。

## 有意排除（保留原样，非遗漏）

| 位置 | 内容 | 理由 |
|------|------|------|
| `HuaweiHealthServiceImpl.java:615` / `XiaomiHealthServiceImpl.java:494` | `SystemException(SYSTEM_ERROR, "Failed to generate OAuth state", e)` | 11-01 的 `systemFallbackMessage()` 对 `SYSTEM_*` 返回固定中文，且 SystemException 分支不追加 detail，用户不可见 |
| `HuaweiHealthServiceImpl.java:731/764`、`XiaomiHealthServiceImpl.java:607/640` | `SystemException(AUTH_FAILED, "Failed to exchange/refresh ..." + e.getMessage(), e)` | 同上，用户看到 AUTH_FAILED 的固定中文「认证失败」 |
| `HuaweiHealthServiceImpl.java:413-418` | `"heart_rate"` 等 dataType 常量 | 指标数据类型标识，不是错误文案，中文化会破坏 API 契约 |
| `SyncResultVO.getPlatformName()` | `"Apple Health"` | 品牌专名 |

## Next Phase Readiness

- SC2 / ERR-02 的缺口已闭合，Phase 11 的 14 条 must-have 全部具备自动化证据
- 需 Docker 时建议补跑 `mvn -f hhs-backend/pom.xml verify -DskipTests failsafe:integration-test failsafe:verify -Dit.test=DeviceSyncIntegrationTest` 验证 DEV-INT-010~014（非阻塞门禁，等价行为已由 `DeviceSyncControllerPageTest` 覆盖）
- 同步历史表中既有英文历史数据未做存量迁移，属 Phase 14 排查范围
- 前端 `DeviceSync.vue` 的 `errorMessage` 直显路径无需改动：后端下发的串现在已是中文

---

*Phase: 11-error-foundation*
*Completed: 2026-11-06*

## Self-Check: PASSED

- FOUND: `hhs-backend/src/test/java/com/hhs/controller/DeviceSyncControllerPageTest.java`
- FOUND: `hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java`
- FOUND: `hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java`
- FOUND: `.planning/phases/11-error-foundation/11-08-SUMMARY.md`
- FOUND: `1c3f006`, `5d95e1f`, `e96a45d` (task commits), `cd921f1` (metadata commit)
- CONFIRMED: `.planning/STATE.md` 与 `.planning/ROADMAP.md` 在本计划提交中零改动