---
phase: 11-error-foundation
plan: 02
subsystem: backend-error-messages
tags: [validation, i18n, dto, error-messages, china-localization]
status: complete
requires:
  - 11-01（ErrorCode / Result 信封基线）
provides:
  - 全部 8 个 DTO 约束消息的中文文案源
  - UserThresholdServiceImpl 中文业务异常
  - PushConfigController / ScreeningServiceImpl 无用户输入回显的中文错误消息
  - UserThresholdRequestValidationTest 中文消息端到端基线
affects:
  - 11-03（GlobalExceptionHandler 统一信封）
  - 11-04（前端 FIELD_LABELS 与 fallback 文案；后端中文为第一来源，D-05）
  - 11-05（登录取证）
tech-stack:
  added: []
  patterns:
    - "只替换 message 字符串，注解类型/正则/数值边界零变更"
    - "错误 detail 不拼接用户输入（T-11-04），原始值只进日志"
    - "不引入 messages.properties / MessageSource（D-13）"
key-files:
  created:
    - hhs-backend/src/test/java/com/hhs/validation/UserThresholdRequestValidationTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/dto/UserThresholdRequest.java
    - hhs-backend/src/main/java/com/hhs/service/impl/UserThresholdServiceImpl.java
    - hhs-backend/src/test/java/com/hhs/service/UserThresholdServiceTest.java
    - hhs-backend/src/main/java/com/hhs/dto/HealthProfileRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/HealthMetricRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/RealtimeMetricRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/AlertVO.java
    - hhs-backend/src/main/java/com/hhs/dto/AIClassifyRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/ExportRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/PushConfigRequest.java
    - hhs-backend/src/main/java/com/hhs/service/impl/ScreeningServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/controller/PushConfigController.java
    - hhs-backend/src/test/java/com/hhs/validation/ValidationTest.java
decisions:
  - "确认 D-13：不引入 messages.properties，中文直接内联在注解 message 上"
  - "T-11-04 按源头治理：detail 不再拼接 channelType / 上传文件名 / e.getMessage()"
  - "修正一处自 Phase 4 起即已失效的既有断言，而非重新引入 @DecimalMax 边界"
metrics:
  duration: 22min
  completed: 2026-11-06
  tasks: 2
  commits: 2
actuals:
  tokens: 10252
  tasks: 2
  commits: 2
plan_head_before: e289c794167a229f58fb3d1ced84db7dabb5ac38
plan_head_after: 383b7e5da09d0b8477dc446808c0deb17befe911
requirements: [ERR-02]
---

# Phase 11 Plan 02: 后端英文校验原文与异常字面量中文化 Summary

把后端所有会被用户看到的英文校验原文与异常字面量改写为中文：8 个 DTO 共 65 条约束消息、
`UserThresholdServiceImpl` 三处业务异常、`ScreeningServiceImpl` 两处错误 detail、
`PushConfigController` 八处 `Result.failure` 全部中文化，并新建按 `propertyPath` 精确断言的
阈值 DTO 校验测试作为回归基线。

## What Was Built

### Task 1 — 阈值 DTO 端到端中文化（tracer）

`UserThresholdRequest` 七条注解消息全部改为中文，并新建
`UserThresholdRequestValidationTest`（6 个用例）把链路锁死：

- `nullUserId_ReportsChineseMessageOnUserIdProperty` —— 同时断言消息文本与 `propertyPath == userId`
- `blankMetricKey_ReportsChineseLengthMessage` —— `@Size` 中文长度消息
- `negativeWarningHigh_ReportsChineseMessageOnWarningHighProperty` —— 断言 `propertyPath == warningHigh`
- `allFourDecimalMinMessages_AreDistinct` —— 四条 `@DecimalMin` 消息互不相同，防止复制粘贴改错字段
- `nullMetricKey_ReportsChineseRequiredMessage` —— `@NotNull` 中文必填消息
- `everyConstraintMessage_IsPureChineseWithoutLatinWords` —— 用 `[A-Za-z]{3,}` 正则证明 7 条消息零英文单词

`UserThresholdServiceImpl` 三处字面量中文化，其中第 108 行 `log.warn` 一并改（计划已说明：
`grep -c "Threshold not found"` 基线为 2，只改 throw 会让接受门禁无法收敛）。
`UserThresholdServiceTest` 的 `// Given` 注释与三条异常消息断言同步中文化。

### Task 2 — 其余 7 个 DTO 与两处服务/控制器推广

- **7 个 DTO**：`HealthProfileRequest`(13)、`HealthMetricRequest`(10)、`RealtimeMetricRequest`(11)、
  `AlertVO`(15)、`AIClassifyRequest`(4)、`ExportRequest`(4)、`PushConfigRequest`(1) —— 共 58 条消息。
  逐条按计划 `<action>` 的固定译法替换，未自由发挥。
- **`ScreeningServiceImpl`**：`"Invalid file path: " + originalFilename` → `"文件路径无效，请重新上传"`
  （去掉用户文件名回显）；`"文件保存失败: " + e.getMessage()` → `"文件保存失败"`，原始异常仍由
  `log.error(..., e)` 保留完整堆栈（T-11-04）。
- **`PushConfigController`**：八处 `Result.failure(400, ...)` 全部改为固定中文文案；
  `"Invalid channel type: " + channelType` 与 `"Push failed: " + result.getMessage()` 均去掉回显。
  原始 `result.getMessage()` 仍进 `log.warn`。
- **`ValidationTest`**：16 处英文 `.contains(...)` 断言改为对应中文片段；纯数字断言
  （`contains("200")`）按计划保持不变。

## Verification

| Gate | Command | Result |
|------|---------|--------|
| Task 1 测试 | `mvn test -Dtest=UserThresholdRequestValidationTest,UserThresholdServiceTest` | 21 tests, 0 failures |
| Task 2 测试 | `mvn test -Dtest=ValidationTest,PushConfigControllerTest` | 50 tests, 0 failures |
| 全量回归 | `mvn clean verify -DskipITs` | 691 tests, 0 failures, BUILD SUCCESS |
| DTO 英文消息清点 | `grep -rc 'message = "[A-Za-z]' hhs-backend/src/main/java/com/hhs/dto/` | 全部为 0 |
| 控制器字面量 | `grep -c 'Result.failure(400, "[A-Za-z]' PushConfigController.java` | 0（基线 8） |
| 文件路径回显 | `grep -c 'Invalid file path' ScreeningServiceImpl.java` | 0（基线 1） |
| 异常原文拼接 | `grep -c '文件保存失败: " + e.getMessage()'` | 0（基线 1） |
| 测试英文断言 | `grep -c 'contains("[A-Za-z][a-z]' ValidationTest.java` | 0（基线 16） |
| 阈值服务中文 | `grep -c '该指标已存在阈值' UserThresholdServiceImpl.java` | 2 |
| 边界值零变更 | `git diff -U0 -- hhs-backend/src/main/java/com/hhs/dto/` 非 message 行 | 0 行 |
| 无 messages.properties | `find hhs-backend/src/main/resources -name 'messages*.properties'` | 无 |
| 无新依赖 | `git diff --stat HEAD~2 -- hhs-backend/pom.xml` | 空 |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] 修正 `testHealthMetricRequest_ValueTooHigh_Fails` 自 Phase 4 起即已失败的断言**

- **Found during:** Task 2 的 `mvn test -Dtest=ValidationTest,PushConfigControllerTest`
- **Issue:** 该测试断言 `value = 1001` 会命中 `"Value must not exceed 1000"`。但 commit
  `a18dfe8`（Phase 7）与 `89a8a21`（Phase 4）把 `HealthMetricRequest.value` 的 DTO 级
  `@DecimalMax(value = "1000.0")` 换成了类级 `@ValidMetricRecord`，边界改由注册表驱动、
  消息模板变为 `心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入`。**在纯净 HEAD 工作树中
  实测 `ValidationTest` 同样是这 1 条失败**，确认是先前就存在的红测试，不是本次改动引入。
- **Fix:** 将断言改为 `contains("有效生理区间为")`，并对齐 `@ValidMetricRecord` 的实际消息来源。
  没有重新加回 `@DecimalMax(1000.0)` —— 那会违反威胁登记 T-11-05「注解类型/正则/数值边界一律不动」，
  且会与 Phase 4 建立的注册表 SSOT 冲突。同时补充注释说明边界归属。
- **Files modified:** `hhs-backend/src/test/java/com/hhs/validation/ValidationTest.java`
- **Commit:** `383b7e5`

**2. [Rule 1 - Bug] `UserThresholdRequestValidationTest` 中 `@NotNull` 先于 `@Size` 短路导致消息计数不符**

- **Found during:** Task 1 首次运行
- **Issue:** 计划要求「断言以上所有消息都不匹配 `[A-Za-z]{3,}`」。首版测试在同一个请求上同时
  置 `userId = null` 与 `metricKey = ""` 期望收集全部约束消息，但断言集合大小写成 5；
  实际 `metricKey = ""` 只触发 `@Size`（`@NotNull` 通过），预期 6 条。第二次修正时又漏算了
  `@NotNull` 需以 `metricKey = null` 单独触发才能出现的「指标类型不能为空」。
- **Fix:** 拆出独立用例 `nullMetricKey_ReportsChineseRequiredMessage` 专门验证 `@NotNull` 消息，
  纯净度用例改为在 `metricKey = null` 的请求集合上再并入 `metricKey = ""` 的 `@Size` 消息，
  断言总数 7。测试因此从 5 个用例增至 6 个。
- **Files modified:** `hhs-backend/src/test/java/com/hhs/validation/UserThresholdRequestValidationTest.java`
- **Commit:** `0d4a2aa`

### Not Deviations

- 计划 `<action>` 第 2 步要求「若该测试断言的异常消息含英文，同步改为中文断言」。
  `UserThresholdServiceTest` 原有断言是
  `contains("already exists") || contains("已存在")` 与 `contains("not found") || contains("不存在")`，
  中文分支本可通过；仍按计划一律收敛为单一中文断言，去掉了英文或分支。

## Threat Model Disposition

| Threat | Disposition | Evidence |
|--------|-------------|----------|
| T-11-04 Information Disclosure | mitigated | `PushConfigController` 八处 detail 不再拼接 `channelType` / `type.getLabel()` / `result.getMessage()`；`ScreeningServiceImpl` 不再拼接 `originalFilename` 与 `e.getMessage()`。原始值仍进 `log.warn` / `log.error`。 |
| T-11-05 Tampering（约束边界） | mitigated | `git diff -U0 -- hhs-backend/src/main/java/com/hhs/dto/` 的非 `message = ` 变更行为 **0**；`@DecimalMin(value = "0.0"` 在 `UserThresholdRequest` 仍为 4 处。 |
| T-11-SC Tampering（依赖安装） | mitigated | 无 `pom.xml` 变更，无新增依赖。 |

## Known Stubs

None. 本次改动只替换字符串常量并新增一个测试类，未引入任何空实现、占位数据或未接线的 UI 数据源。

## Deferred / Out of Scope

- `ScreeningServiceImpl:124` 的
  `new BusinessException(ErrorCode.RESOURCE_FILE_PARSE_ERROR, "报告日期格式错误: " + reportDateStr, e)`
  仍拼接用户输入的日期字符串。计划 `<action>` 只点名第 87 行与第 110 行，故未改动。
  记录于此供 11-03 / 11-04 复核：该回显同样可能把任意输入送进错误消息，属 T-11-04 同类面。
- `ScreeningServiceImpl:148` 的
  `reportStatusService.updateOcrStatus(reportId, "FAILED", "调度OCR处理失败: " + e.getMessage())`
  把底层异常原文写进用户可见状态字段。同样未在计划范围内，留待后续计划收口。

## Self-Check

- [x] `hhs-backend/src/test/java/com/hhs/validation/UserThresholdRequestValidationTest.java` 存在
- [x] commit `0d4a2aa` 在 HEAD 祖先链上
- [x] commit `383b7e5` 在 HEAD 祖先链上
- [x] 未修改 `.planning/STATE.md` / `.planning/ROADMAP.md`

## Self-Check: PASSED