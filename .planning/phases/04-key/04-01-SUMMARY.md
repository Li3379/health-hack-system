---
phase: 04-key
plan: 01
subsystem: api
tags: [metadata, domain, registry, key-normalization, spring-security, caching]

# Dependency graph
requires: []
provides:
  - MetricDefinition Java Record with clinical boundaries and validation limits
  - MetricKeyNormalizer with camelCase normalization and alias mapping
  - HealthMetricMetaRegistry authoritative in-memory metadata store for 23 metrics
  - MetricMetadataController exposing GET /api/metrics/metadata with 24h HTTP caching and ETag
  - Refactored MetricDisplayFormatter and MetricCategoryService delegating to SSOT registry
affects: [04-02, 05-ui, 06-input, 07-ai]

# Actuals (#2632)
actuals:
  tokens: 14754
  tasks: 2
  commits: 2
  plan_head_before: 13eb2c86f685736d9e763acf83af99cfa3341b2b

# Tech tracking
tech-stack:
  added: []
  patterns: [In-memory SSOT registry, Java Record metadata entity, HTTP strong cache & ETag negotiation, lenient alias passthrough]

key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricDefinition.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java
    - hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java
    - hhs-backend/src/main/java/com/hhs/controller/MetricMetadataController.java
    - hhs-backend/src/test/java/com/hhs/controller/MetricMetadataControllerTest.java
    - hhs-backend/src/test/java/com/hhs/service/domain/MetricMetadataRegistryTest.java
    - hhs-backend/src/test/java/com/hhs/service/domain/MetricKeyNormalizerTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/config/SecurityConfig.java
    - hhs-backend/src/main/java/com/hhs/config/SecurityHeadersConfig.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricDisplayFormatter.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricCategoryService.java

key-decisions:
  - "Standardized all authoritative metric keys to camelCase with protected acronyms (e.g. systolicBP, diastolicBP) and lenient passthrough for unknown keys (D-01, D-02)"
  - "Constructed in-memory singleton HealthMetricMetaRegistry covering 23 health and wellness metrics with zero database migration overhead (D-03)"
  - "Configured public GET /api/metrics/metadata endpoint with 24-hour Cache-Control (public, max-age=86400) and ETag header support (D-04)"
  - "Disabled Spring Security default CacheControlHeadersWriter to allow controller-defined Cache-Control headers"

patterns-established:
  - "SSOT Domain Registry: Domain services delegate to HealthMetricMetaRegistry before falling back to legacy switches"
  - "Dual-Constructor Domain Service: Providing default and @Nullable Autowired constructors to support standalone instantiation in unit tests alongside Spring dependency injection"

requirements-completed:
  - META-01
  - META-02
  - KEY-01

coverage:
  - id: D1
    description: "Public GET /api/metrics/metadata endpoint with Cache-Control: public, max-age=86400 and ETag"
    requirement: "META-02"
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/controller/MetricMetadataControllerTest.java#testGetMetadataAnonymousAccess"
        status: pass
    human_judgment: false
  - id: D2
    description: "In-memory HealthMetricMetaRegistry with 23 metrics covering physiological and clinical reference limits"
    requirement: "META-01"
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/MetricMetadataRegistryTest.java#testRegistrySizeAndCompleteness"
        status: pass
    human_judgment: false
  - id: D3
    description: "MetricKeyNormalizer bidirectional alias mapping, camelCase acronym protection, and lenient unknown passthrough"
    requirement: "KEY-01"
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/MetricKeyNormalizerTest.java#testToCanonicalKeyKnownVariants"
        status: pass
    human_judgment: false

# Metrics
duration: 18min
completed: 2026-11-04
status: complete
---

# Phase 04 Plan 01: Backend Metric Metadata Registry & Key Normalization Summary

**Immutable single source of truth (SSOT) metric registry for 23 metrics, multi-variant key normalizer, and public HTTP-cached metadata endpoint**

## Performance

- **Duration:** 18 min
- **Started:** 2026-11-04T14:35:00Z
- **Completed:** 2026-11-04T14:53:00Z
- **Tasks:** 2 completed
- **Files modified:** 11 files (7 created, 4 modified)

## Accomplishments

- **MetricDefinition Record & Registry:** Implemented Java Record `MetricDefinition` and Spring `@Component HealthMetricMetaRegistry` defining 23 health (16) and wellness (7) metrics with display names, units, precision, step sizes, physiological limits (`hardMin`/`hardMax`), and clinical reference ranges (`normalMin`/`normalMax`).
- **MetricKeyNormalizer Engine:** Implemented bidirectional normalization mapping common aliases (`heart_rate`, `systolic_bp`, `temp`, `spo2`, `glucose`) to standard camelCase keys while preserving domain acronyms (`systolicBP`, `diastolicBP`) and leniency towards unrecognized keys.
- **Public REST Metadata Endpoint:** Exposed `GET /api/metrics/metadata` with `Cache-Control: public, max-age=86400` and `ETag: "v1.1-metric-meta"` for 24-hour client and gateway caching.
- **Domain Service Delegation:** Refactored `MetricDisplayFormatter` and `MetricCategoryService` to query `HealthMetricMetaRegistry` first, eliminating English leakage for `bodyTemperature`.
- **Test Coverage:** Created comprehensive automated tests in `MetricMetadataControllerTest`, `MetricMetadataRegistryTest`, and `MetricKeyNormalizerTest` (160 tests passed).

## Task Commits

Each task was committed atomically:

1. **Task 1: Tracer slice — 权威元数据字典查询与 Key 归一化端到端贯通** - `0495e9b` (feat)
2. **Task 2: 全量 19+ 种指标注册表扩充、别名字典补全与旧格式化/分类服务委托** - `4767a2b` (feat)

## Files Created/Modified

- `hhs-backend/src/main/java/com/hhs/service/domain/MetricDefinition.java` - Java Record defining immutable metric attributes and validation boundaries
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java` - Key normalization utility with alias dictionary and lenient passthrough
- `hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java` - Single source of truth registry for 23 metrics
- `hhs-backend/src/main/java/com/hhs/controller/MetricMetadataController.java` - Public REST controller returning metadata with HTTP caching headers
- `hhs-backend/src/main/java/com/hhs/config/SecurityConfig.java` - PermitAll configuration for `/api/metrics/metadata`
- `hhs-backend/src/main/java/com/hhs/config/SecurityHeadersConfig.java` - Disabling default Spring Security cache control writer so endpoint cache headers are retained
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricDisplayFormatter.java` - Refactored to delegate to metadata registry
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricCategoryService.java` - Refactored to delegate to metadata registry
- `hhs-backend/src/test/java/com/hhs/controller/MetricMetadataControllerTest.java` - Integration test for anonymous access and caching headers
- `hhs-backend/src/test/java/com/hhs/service/domain/MetricMetadataRegistryTest.java` - Unit tests for 23 metric definitions and clinical boundaries
- `hhs-backend/src/test/java/com/hhs/service/domain/MetricKeyNormalizerTest.java` - Parameterized unit tests for key variants and alias lookups

## Decisions Made

- Standardized all canonical metric keys to camelCase with protected uppercase acronyms (`systolicBP`, `diastolicBP`) per D-01 and D-02.
- Provided dual constructors (default + `@Nullable Autowired`) in `MetricDisplayFormatter` and `MetricCategoryService` to maintain backwards compatibility with existing unit tests that instantiate domain services via `new`.
- Stored metadata in memory using immutable data structures (`Map.ofEntries`, `Collections.unmodifiableMap`) for sub-millisecond response times without DB dependencies (D-03).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spring Security default CacheControlHeadersWriter overwriting controller Cache-Control**
- **Found during:** Task 1 (MetricMetadataControllerTest execution)
- **Issue:** Spring Security's default `HeaderWriterFilter` overwrote the controller's `Cache-Control: public, max-age=86400` header with `no-cache, no-store, max-age=0, must-revalidate`.
- **Fix:** Added `.cacheControl(cache -> cache.disable())` in `SecurityHeadersConfig.java` so controller-defined Cache-Control headers are preserved on public endpoints.
- **Files modified:** `hhs-backend/src/main/java/com/hhs/config/SecurityHeadersConfig.java`
- **Verification:** `MetricMetadataControllerTest` asserts `Cache-Control` contains `max-age=86400` and `public`, test passed.
- **Committed in:** `0495e9b` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 configuration bug)
**Impact on plan:** Essential for contract compliance with D-04. No scope creep.

## Issues Encountered

None beyond the auto-fixed Spring Security cache header configuration.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Metric metadata registry and normalizer are fully operational and verified by 160 passing tests.
- Ready for Plan 04-02: Jakarta Validation `@ValidMetricRecord` physiological limit interception, service layer query alias expansion, and SQL key migration script.

---
*Phase: 04-key*
*Completed: 2026-11-04*
