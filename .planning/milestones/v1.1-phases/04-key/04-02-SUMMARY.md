---
phase: 04-key
plan: 02
subsystem: api
tags: [validation, physiological-limits, key-normalization, alias-expansion, sql-migration]

# Dependency graph
requires:
  - 04-01
provides:
  - ValidMetricRecord annotation and MetricRecordValidator
  - HealthMetricRequest DTO @JsonSetter key normalization and relaxed 1000 limit
  - HealthMetricServiceImpl transparent alias expansion using SQL IN clause
  - v1.1_normalize_metric_keys.sql idempotent database cleansing patch
affects: [05-ui, 06-input, 07-ai]

# Actuals (#2632)
actuals:
  tokens: 14408
  tasks: 2
  commits: 2
  plan_head_before: cf7f3808b1af71920c975095566da07e882ab3a2

# Tech tracking
tech-stack:
  added: []
  patterns: [Jakarta class-level constraint validator, parameterised SQL IN alias expansion, idempotent data migration patch]

key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/validation/ValidMetricRecord.java
    - hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java
    - hhs-backend/src/main/resources/sql/migration/v1.1_normalize_metric_keys.sql
    - hhs-backend/src/test/java/com/hhs/validation/MetricRecordValidatorTest.java
    - hhs-backend/src/test/java/com/hhs/service/impl/HealthMetricServiceNormalizationTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/dto/HealthMetricRequest.java
    - hhs-backend/src/main/java/com/hhs/service/impl/HealthMetricServiceImpl.java
    - hhs-backend/src/test/java/com/hhs/service/HealthMetricServiceTest.java

key-decisions:
  - "Intercept physically impossible biological values via Jakarta Validation class-level constraint @ValidMetricRecord with strictly formatted guidance messages (D-07, D-08)"
  - "Permit pathological and critical illness data (e.g., 140 bpm, 195 mmHg, 18.0 mmol/L) to pass validation and persist safely"
  - "Remove rigid 1000 maximum constraint on HealthMetricRequest.value to allow daily steps and water intake"
  - "Normalize incoming metric keys automatically during DTO deserialization and service-layer write operations"
  - "Expand single metric key filter to full alias set in service-layer queries via MyBatis-Plus LambdaQueryWrapper.in() (D-05)"
  - "Provide idempotent SQL migration script v1.1_normalize_metric_keys.sql for safe batch cleansing of historical records (D-06)"

patterns-established:
  - "Physiological Limit Interception: @ValidMetricRecord delegates to HealthMetricMetaRegistry to bound inputs by hard limits while passing clinical abnormalities"
  - "Transparent Query Alias Expansion: Query methods expand single keys into alias sets using LambdaQueryWrapper.in() to bridge legacy snake_case and modern camelCase records"
  - "Safe Migration Patching: Table-specific idempotent SQL scripts handling unique key collisions before updating aliases"

requirements-completed:
  - KEY-01
  - KEY-02
  - META-01

coverage:
  - id: D4
    description: "Physical limit hard interception on impossible values (e.g. 400 bpm, 80°C) with D-08 compliant Chinese message"
    requirement: "META-01"
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/validation/MetricRecordValidatorTest.java#testPhysicallyImpossibleHeartRateIntercepted"
        status: pass
    human_judgment: false
  - id: D5
    description: "Clinical pathological abnormalities (140 bpm, 195 mmHg, 18 mmol/L) pass validation"
    requirement: "META-01"
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/validation/MetricRecordValidatorTest.java#testPathologicalAbnormalValuesPass"
        status: pass
    human_judgment: false
  - id: D6
    description: "Automatic key normalization on JSON deserialization in HealthMetricRequest"
    requirement: "KEY-02"
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/validation/MetricRecordValidatorTest.java#testJacksonKeyNormalization"
        status: pass
    human_judgment: false
  - id: D7
    description: "Service-level transparent alias expansion querying both camelCase and snake_case historical records"
    requirement: "KEY-02"
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/impl/HealthMetricServiceNormalizationTest.java#testListWithSnakeCaseAliasExpandsToInQuery"
        status: pass
    human_judgment: false
  - id: D8
    description: "Idempotent SQL migration patch v1.1_normalize_metric_keys.sql for batch data cleansing"
    requirement: "KEY-02"
    verification:
      - kind: manual
        ref: "hhs-backend/src/main/resources/sql/migration/v1.1_normalize_metric_keys.sql"
        status: pass
    human_judgment: false

# Metrics
duration: 25min
completed: 2026-11-04
status: complete
---

# Phase 04 Plan 02: Biological Limit Validation & Query Alias Expansion Summary

**Jakarta class-level physiological limit validation `@ValidMetricRecord`, transparent service query alias expansion with SQL IN clauses, and idempotent batch data cleansing migration patch**

## Performance

- **Duration:** 25 min
- **Started:** 2026-11-04T15:15:00Z
- **Completed:** 2026-11-04T15:40:00Z
- **Tasks:** 2 completed
- **Files modified:** 8 files (5 created, 3 modified)

## Accomplishments

- **Jakarta Validation `@ValidMetricRecord` & `MetricRecordValidator`:** Implemented custom class-level constraint annotation and validator connected to `HealthMetricMetaRegistry`. Physical impossibilities (e.g., heart rate 400 bpm, body temperature 80.0 °C, negative values) are intercepted and bound to property `"value"` with exact error messages formatted per D-08 (`"[指标中文名]有效生理区间为 [min] ~ [max] [unit]，请核对后重新输入"`).
- **Clinical Abnormality Tolerance:** Ensured clinically severe but physiologically possible conditions (e.g., tachycardia 140 bpm, hypertensive crisis 195 mmHg, severe hyperglycemia 18.0 mmol/L, high fever 39.8 °C) pass validation without rejection, protecting emergency triage and diagnostic tracking.
- **DTO Key Normalization & Ceiling Removal:** Removed `@DecimalMax("1000.0")` ceiling from `HealthMetricRequest`, allowing legitimate lifestyle entries like 10,000 steps and 2,000 ml water intake. Added `@JsonSetter` key normalization converting snake_case and aliases to standard camelCase on deserialization.
- **Service Layer Query Alias Expansion:** Refactored `HealthMetricServiceImpl.list` and paginated `list` to retrieve alias sets via `MetricKeyNormalizer.getAliases()`, generating SQL `IN (...)` conditions when alias count > 1. Allows users to query with `heart_rate` or `heartRate` and retrieve all historical and incremental records.
- **Service Write & Trend Normalization:** Canonicalized metric keys upon `add`, `create`, and `update`, and delegated standard unit resolution to `HealthMetricMetaRegistry`. Canonicalized `HealthMetricTrendVO.metricKey` so frontends always receive standard camelCase keys.
- **Idempotent SQL Cleansing Patch:** Created `v1.1_normalize_metric_keys.sql` covering `health_metric`, `realtime_metric`, `alert_rule`, `user_threshold`, `alert_template`, `health_alert`, and `alert_merge_log` with unique constraint collision handling.
- **Comprehensive Test Suite:** Created unit and integration test suites in `MetricRecordValidatorTest` and `HealthMetricServiceNormalizationTest` (all 192 backend tests passed).

## Task Commits

Each task was committed atomically:

1. **Task 1: 基于 Jakarta Validation 的请求级生理极限硬拦截与 DTO 反序列化 Key 归一化** - `89a8a21` (feat)
2. **Task 2: 服务层透明别名查询展开、规范化存储与幂等数据清洗补丁** - `6373808` (feat)

## Files Created/Modified

- `hhs-backend/src/main/java/com/hhs/validation/ValidMetricRecord.java` - Class-level constraint annotation for health metric validation
- `hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java` - Physiological boundary validator querying metadata registry
- `hhs-backend/src/main/java/com/hhs/dto/HealthMetricRequest.java` - Attached `@ValidMetricRecord`, removed 1000 max, added `@JsonSetter` normalization
- `hhs-backend/src/main/java/com/hhs/service/impl/HealthMetricServiceImpl.java` - Alias expansion via SQL IN, canonical storage, registry unit resolution
- `hhs-backend/src/main/resources/sql/migration/v1.1_normalize_metric_keys.sql` - Idempotent historical data cleansing SQL patch
- `hhs-backend/src/test/java/com/hhs/validation/MetricRecordValidatorTest.java` - Tests for physical limit blocking, pathological pass, and JSON deserialization
- `hhs-backend/src/test/java/com/hhs/service/impl/HealthMetricServiceNormalizationTest.java` - Tests for alias query expansion, canonical persistence, and trend normalization
- `hhs-backend/src/test/java/com/hhs/service/HealthMetricServiceTest.java` - Updated assertions to expect canonical camelCase metric keys

## Decisions Made

- Bound physiological validation violations to the `"value"` property node using `context.buildConstraintViolationWithTemplate(errorMessage).addPropertyNode("value").addConstraintViolation()`, which seamlessly surfaces through `GlobalExceptionHandler.handleMethodArgumentNotValid` (D-07, D-08).
- Implemented dual-constructor pattern in `MetricRecordValidator` and optional autowiring in `HealthMetricServiceImpl` to support standalone unit tests alongside Spring dependency injection.
- Handled unique constraints in `alert_rule` and `user_threshold` in `v1.1_normalize_metric_keys.sql` by deleting duplicate alias records prior to updating remaining rows.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Updated HealthMetricServiceTest assertions to match canonical keys**
- **Found during:** Task 2 (HealthMetricServiceTest execution)
- **Issue:** Existing test `HealthMetricServiceTest` asserted `metricKey == "heart_rate"` on the VO returned from `add()` and `getTrend()`. Because `setMetricKey` now canonicalizes `heart_rate` to `heartRate`, the test failed.
- **Fix:** Updated the assertions in `HealthMetricServiceTest.testAdd_Success` and `testGetTrend_WithData` to assert `"heartRate"`.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/HealthMetricServiceTest.java`
- **Verification:** `HealthMetricServiceTest` passes completely (17/17 tests).
- **Committed in:** `6373808` (Task 2 commit)

**2. [Rule 3 - Blocking Issue] Initialized MyBatis-Plus TableInfoHelper in unit test**
- **Found during:** Task 2 (HealthMetricServiceNormalizationTest execution)
- **Issue:** Standalone Mockito test inspecting `LambdaQueryWrapper.getSqlSegment()` failed with `MybatisPlusException: can not find lambda cache for this entity [HealthMetric]` because MyBatis-Plus metadata cache was not initialized without Spring container.
- **Fix:** Added `@BeforeAll static void initMyBatisPlus()` calling `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), HealthMetric.class)`.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/impl/HealthMetricServiceNormalizationTest.java`
- **Verification:** `HealthMetricServiceNormalizationTest` passes completely (7/7 tests).
- **Committed in:** `6373808` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 test assertion alignment, 1 test mock helper initialization)
**Impact on plan:** None. All functionality verified and compliant.

## Issues Encountered

None.

## User Setup Required

None. The SQL script `hhs-backend/src/main/resources/sql/migration/v1.1_normalize_metric_keys.sql` is provided for optional database maintenance.

## Next Phase Readiness

- All Phase 4 backend deliverables (Registry, Normalizer, REST endpoint, Validation pipeline, Service alias expansion, and SQL patch) are complete and tested.
- Ready for Phase 05: Frontend metadata store, Pinia integration, and unified display formatting.

## Self-Check: PASSED
- Created files verified on disk:
  - `hhs-backend/src/main/java/com/hhs/validation/ValidMetricRecord.java`
  - `hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java`
  - `hhs-backend/src/main/resources/sql/migration/v1.1_normalize_metric_keys.sql`
  - `hhs-backend/src/test/java/com/hhs/validation/MetricRecordValidatorTest.java`
  - `hhs-backend/src/test/java/com/hhs/service/impl/HealthMetricServiceNormalizationTest.java`
- Commits verified in git history: `89a8a21`, `6373808`
- Automated tests verified: 192 tests passing

---
*Phase: 04-key*
*Completed: 2026-11-04*
