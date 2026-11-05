---
phase: 07-sync
plan: 02
subsystem: backend / data-sync
tags: [ai, ocr, validation, four-tier, SSOT, langchain4j, jakarta-validation]

# Dependency graph
requires:
  - phase: 04-meta-registry
    provides: HealthMetricMetaRegistry, MetricKeyNormalizer, MetricDefinition
provides:
  - Dynamic AI system prompt generation from HealthMetricMetaRegistry
  - LabRoutineMetricRegistry for CBC routine lab reports
  - OcrServiceImpl dual-registry integration and blood pressure decomposition
  - MetricValueProvider common contract for all ingestion DTOs
  - Universal @ValidMetricRecord physical limit interception
affects: [07-03-PLAN, 07-04-PLAN, health-input, data-ingestion]

# Actuals (#2632)
actuals:
  tokens: 9251
  tasks: 3
  commits: 8
  plan_head_before: 3682338875dd7a0a96d19d8bdd887e25b1c52c16

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Dynamic Markdown system prompt assembly at @PostConstruct from metadata registry
    - Domain-isolated medical laboratory registry (LabRoutineMetricRegistry)
    - Universal DTO contract validation via MetricValueProvider interface

key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/service/domain/LabRoutineMetricRegistry.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricValueProvider.java
  modified:
    - hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/service/impl/OcrServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/validation/ValidMetricRecord.java
    - hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java
    - hhs-backend/src/main/java/com/hhs/dto/HealthMetricRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/WellnessMetricRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/ConfirmMetricsRequest.java
    - hhs-backend/src/main/java/com/hhs/dto/OcrConfirmRequest.java
    - hhs-backend/src/test/java/com/hhs/service/AiParseServiceTest.java
    - hhs-backend/src/test/java/com/hhs/service/OcrServiceTest.java
    - hhs-backend/src/test/java/com/hhs/validation/MetricRecordValidatorTest.java

key-decisions:
  - "AiParseServiceImpl 废除写死的静态提示词表与私有范围，在 PostConstruct 阶段直接消费 HealthMetricMetaRegistry 动态装配 23 项指标 Markdown 表格并缓存"
  - "AI 解析提取的指标强制经 MetricKeyNormalizer 归一化为小驼峰 Key，血糖统一为 bloodGlucose，血压严格拆分为 systolicBP 与 diastolicBP"
  - "引入独立的 LabRoutineMetricRegistry 管理血常规 4 项指标（血红蛋白、白细胞、红细胞、血小板），OcrServiceImpl 优先查核心元数据注册表、次查化验单扩展注册表"
  - "抽取 MetricValueProvider 通用接口，扩展 @ValidMetricRecord 覆盖 HealthMetricRequest、WellnessMetricRequest、ConfirmMetricsRequest.MetricItem、OcrConfirmRequest.MetricItem"

patterns-established:
  - "Dynamic Prompt SSOT: 大模型结构化数据提取提示词统一从权威元数据注册中心动态生成，消除配置漂移与旧 Key 遗漏"
  - "Multi-DTO Validation Contract: 全渠道入库 DTO 统一实现 MetricValueProvider，由类级别校验器拦截 Tier 4 物理超限值"

requirements-completed:
  - SYNC-01

coverage:
  - id: D1
    description: "AiParseServiceImpl 动态组装提示词并对齐权威元数据与 Key 归一化"
    requirement: "SYNC-01"
    verification:
      - kind: unit
        ref: "com.hhs.service.AiParseServiceTest"
        status: pass
    human_judgment: false
  - id: D2
    description: "LabRoutineMetricRegistry 独立注册表及 OcrServiceImpl 双注册表接入与血压拆分"
    requirement: "SYNC-01"
    verification:
      - kind: unit
        ref: "com.hhs.service.OcrServiceTest"
        status: pass
    human_judgment: false
  - id: D3
    description: "MetricValueProvider 通用接口与全渠道入库 DTO 物理极限统一拦截"
    requirement: "SYNC-01"
    verification:
      - kind: unit
        ref: "com.hhs.validation.MetricRecordValidatorTest"
        status: pass
    human_judgment: false

duration: 38min
completed: 2026-11-05
status: complete
---

# Phase 7 Plan 02: 全渠道校验对齐（后端动态提示词、化验扩展与物理极限拦截） Summary

**后端 AI 解析与 OCR 体检识别全面接入权威元数据注册中心与化验单扩展注册表，通过通用接口 MetricValueProvider 在全渠道入库 DTO 网关层统一拦截 Tier 4 生理极限。**

## Performance

- **Duration:** 38 min
- **Started:** 2026-11-05T10:45:00Z
- **Completed:** 2026-11-05T11:23:00Z
- **Tasks:** 3
- **Files modified:** 13 (2 created, 11 modified)

## Accomplishments

1. **AI 智能文本解析动态提示词与四梯度预警对齐 (Task 1 / D-01, D-03, D-04)**:
   - 彻底废除 `AiParseServiceImpl.java` 中写死的静态 `PARSE_SYSTEM_PROMPT`、`HEALTH_KEYS`、`WELLNESS_KEYS` 以及私有 `METRIC_RANGES`。
   - 在 `@PostConstruct` 阶段动态读取 `HealthMetricMetaRegistry.getAllDefinitions()`，将权威 23 项指标分类组装为 Markdown 对照表并缓存。
   - 解析结果经 `MetricKeyNormalizer.toCanonicalKey()` 强制归一化，旧 Key `glucose` 统一转化为标准 Key `bloodGlucose`。
   - 强化血压拆分规则：返回 `bloodPressure` 复合字符串自动拆解为 `systolicBP` 与 `diastolicBP`，单一数值未拆分时阻断拦截。
   - 实施四梯度预警判定模型（Tier 1 正常放行、Tier 2 预警偏差友好提示、Tier 3 急危重症就医建议、Tier 4 物理生理极限警示）。

2. **化验单专属血常规指标扩展与 OCR 报告识别解耦 (Task 2 / D-02, D-03, D-04)**:
   - 新建领域隔离的血常规扩展注册表 `LabRoutineMetricRegistry.java`，管理血红蛋白 (`hemoglobin`)、白细胞计数 (`wbc`)、红细胞计数 (`rbc`)、血小板计数 (`platelet`) 4 项临床指标。
   - 重构 `OcrServiceImpl.java`：彻底删除私有 `METRIC_KEY_MAP`、`DEFAULT_UNITS`、`REFERENCE_RANGES`、`HEALTH_KEYS`、`WELLNESS_KEYS` 字典映射。
   - 指标识别解析采用分层检索架构：优先检索核心注册表 `HealthMetricMetaRegistry`，次查化验单扩展注册表 `LabRoutineMetricRegistry`。
   - 血压解析支持 `120/80` 复合格式严格拆解为 `systolicBP` 与 `diastolicBP`。

3. **MetricValueProvider 通用接口抽取与全渠道入库 DTO 物理极限拦截 (Task 3 / D-03)**:
   - 抽取轻量通用契约接口 `MetricValueProvider.java`，定义 `getMetricKey()` 与 `getValue()`。
   - 让 `HealthMetricRequest`、`WellnessMetricRequest`、`ConfirmMetricsRequest.MetricItem`、`OcrConfirmRequest.MetricItem` 全部实现 `MetricValueProvider` 并注解 `@ValidMetricRecord`。
   - 重构 `MetricRecordValidator.java` 泛化实现 `ConstraintValidator<ValidMetricRecord, MetricValueProvider>`，结合核心注册表与化验注册表在 HTTP 控制器入口统一拦截 Tier 4 生理不可能超限值（如心率 450 bpm、体温 55°C、收缩压 400 mmHg、睡眠 28 小时）。
   - 保证急危重症病理数据（如收缩压 195 mmHg、心率 140 次/分、血糖 18.0 mmol/L）放行入库，不阻断抢救与就医辅助。

## Task Commits

Each task was committed atomically:

1. **Task 1: Tracer slice — 重构 AiParseServiceImpl 动态组装提示词并对齐权威元数据与 Key 归一化** - `1b5c98e` (feat)
2. **Task 2: 创建 LabRoutineMetricRegistry 并重构 OcrServiceImpl 全面对接权威元数据与化验扩展** - `f6e56a6` (feat)
3. **Task 3: 提取 MetricValueProvider 统一接口，扩展 @ValidMetricRecord 并对齐全渠道入库 DTO 物理极限拦截** - `a18dfe8` (feat)

## Deviations from Plan

None - plan executed exactly as written.

## Threat Flags

None - all changes reinforce input validation and eliminate hardcoded string mappings.

## Self-Check: PASSED

1. Created files exist:
   - `hhs-backend/src/main/java/com/hhs/service/domain/LabRoutineMetricRegistry.java` (FOUND)
   - `hhs-backend/src/main/java/com/hhs/service/domain/MetricValueProvider.java` (FOUND)
2. Commits exist in git history:
   - `1b5c98e` (FOUND)
   - `f6e56a6` (FOUND)
   - `a18dfe8` (FOUND)
3. Verification suites:
   - `AiParseServiceTest`: 26 tests passed (0 failures)
   - `OcrServiceTest`: 33 tests passed (0 failures)
   - `MetricRecordValidatorTest`: 11 tests passed (0 failures)
   - Checkstyle: 0 violations, BUILD SUCCESS
