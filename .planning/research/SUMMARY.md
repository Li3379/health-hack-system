# Project Research Summary

**Project:** Health Hack System (HHS) — Milestone v1.1  
**Domain:** Intelligent Health Metric Input, Unified Range Validation & Pre-guidance System  
**Researched:** 2026-11-04  
**Confidence:** HIGH  

---

## Executive Summary

Health Hack System (HHS) Milestone v1.1 focuses on transforming health metric input and validation from a rigid, fragmented form-checking mechanism into an intelligent, medically accurate, and friendly guidance pipeline. Clinical data entry differs fundamentally from generic web forms: human vital signs represent a continuous physiological spectrum ranging from optimal baseline to pathological abnormalities, life-threatening clinical emergencies, and physical impossibilities. Prior HHS implementations suffered from four major systemic flaws: scattered hardcoded threshold definitions across UI dialogs and backend services, destructive client-side blur handlers that wiped user input upon out-of-range detection (`form.value = undefined`), rigid server validation that rejected acute clinical crises (e.g. hypertensive crisis or diabetic ketoacidosis) with generic 400 errors, and inconsistent metric key naming (`camelCase` vs `snake_case` vs aliases) that silently broke scoring calculations and leaked raw English codes to the UI.

The recommended architectural approach establishes a centralized **Single Source of Truth (SSOT)** in the backend (`MetricMetadataRegistry`) managing 19+ standardized health and wellness metrics, without introducing third-party dependency bloat. This registry powers a four-tier graduated validation pipeline (Normal, Warning, Critical, Hard Limit) enforced at the DTO level via custom Jakarta validation (`@ValidMetricRecord`). On the client side, a dedicated Pinia store (`useMetricMetaStore`) caches metadata and feeds reactive Vue 3 composables (`useMetricValidation`). Form controls dynamically configure placeholders, units, precision steps, and non-destructive warning banners. A bidirectional normalization bridge (`MetricKeyNormalizer`) unifies key casing across manual input, IoT wearables, Baidu OCR, and Tongyi Qianwen AI parsing, completely eliminating English code leakage.

The primary project risks involve data integrity and operational resilience: cross-channel unit discrepancies (such as IoT glucometers submitting `mg/dL` into a `mmol/L` database), blood pressure half-record persistence or inverted systolic/diastolic values, and synchronous bulk IoT sync failures caused by noisy sensor points. These risks are mitigated through a canonical storage unit contract with automated ingestion conversion, atomic composite blood pressure endpoints with cross-field validation (`systolicBP >= diastolicBP + 15`), and a partial-success quarantine pattern for batch IoT ingestion.

---

## Key Findings

### Recommended Stack

The entire guidance and validation infrastructure is built on top of the validated existing foundation (Spring Boot 3.2.0, Java 17, Vue 3.4.15, Element Plus 2.5.6, Pinia 2.1.7) with zero additional Maven dependencies or npm packages.

**Core technologies:**
- **Jakarta Bean Validation 3.0 (Hibernate Validator 8.0.1.Final via `spring-boot-starter-validation`):** Implements dynamic, class-level constraints (`@ValidMetricRecord`) to validate payloads against runtime registry boundaries while providing contextual error diagnostics.
- **Spring Boot Web MVC + Jackson 2.15.3:** Exposes the `/api/metrics/metadata` dictionary endpoint and leverages custom setter normalization and `@JsonAlias` to accept both `snake_case` and `camelCase` payloads seamlessly.
- **Vue 3 (Composition API & `<script setup>`):** Delivers sub-millisecond reactive input feedback (`ref`, `computed`, `watch`) for real-time health status chips and non-blocking advice banners before form submission.
- **Element Plus 2.5.6 Form & Input Controls (`el-form`, `el-input-number`, `el-alert`):** Integrates native `async-validator` rules for physiological hard limits while avoiding destructive blur wipes and relaxing restrictive numeric steppers.
- **Pinia Store 2.1.7 (`useMetricMetaStore`):** Caches centralized metric definitions on the client, backed by localStorage/sessionStorage and fallback static constants (`metricMetaDefaults.ts`), ensuring zero UI lag and offline resilience.
- **Hutool Core 5.8.28 (`cn.hutool:hutool-all`):** Supplies defensive fallback casing conversions (`StrUtil.toCamelCase`) for unregistered dynamic keys.

### Expected Features

Features are categorized according to digital health standards (Apple Health, Huawei Health, Omron Connect) and national clinical practice guidelines (Hypertension 2024, Diabetes 2020).

**Must have (table stakes):**
- **Unified Metric Metadata SSOT (`UNIFIED-METRIC-META`):** Single authoritative registry in backend covering 19+ health and wellness metrics, exposed via `/api/metrics/metadata` to replace all scattered hardcoded dictionaries.
- **Pre-Input Range & Unit Guidance (`INPUT-GUIDE`):** Constant visibility of clinical reference ranges in input placeholders, pinned unit labels (`mmol/L`, `mmHg`), and metric-appropriate precision steppers (e.g., glucose: 0.1, steps: 500).
- **Non-Destructive Soft-Boundary Feedback (`FRIENDLY-FEEDBACK`):** Total abolition of blur-clearing logic (`form.value = undefined`). Pathological values outside normal reference are accepted with amber/yellow status tags and educational guidance.
- **Metric Key Normalization & Display Mapping (`KEY-NORMALIZATION`):** Bidirectional dictionary normalizing `camelCase`, `snake_case`, and `SCREAMING_SNAKE` keys, guaranteeing zero English codes leak into UI tables, charts, or alert logs.
- **Blood Pressure Dual-Metric Association (`BP-DUAL-ASSOCIATION`):** Simultaneous SBP/DBP input with cross-field physiological validation (`systolicBP >= diastolicBP + 15`) and one-click inversion swap.
- **Biologically Impossible Hard-Block:** Blocking form submission exclusively for physiologically impossible extremes (e.g. body temperature > 45°C or < 30°C; heart rate > 300 bpm), while keeping user text intact for backspace correction.

**Should have (competitive differentiators):**
- **Critical/Panic Range Double-Confirmation (`CRITICAL-CONFIRM-MODAL`):** Interactive confirmation modal when readings breach clinical crisis limits (e.g., SBP ≥ 180 mmHg, glucose ≥ 16.7 mmol/L), prompting confirmation while routing data to high-priority emergency alerts.
- **Intelligent Explanatory Feedback & Diagnosis:** Medical context-aware suggestions (e.g., detecting temperature 98.6 as Fahrenheit and offering one-click conversion to 37.0°C; suggesting fasting vs postprandial glucose categorization).
- **Cross-Channel Rule Convergence:** Unifying manual entry, AI text parsing (`AiParseServiceImpl`), OCR report recognition (`OcrServiceImpl`), and IoT telemetry under the exact same domain validator and warning messages.
- **Real-Time Health Status Chip Preview:** Dynamic visual badges ("Ideal", "Normal", "Pre-hypertension", "Crisis") rendered inline without server round-trips.

**Defer (v2+):**
- **Time & Context-Aware Smart Suggestions (v1.2):** Auto-highlighting morning fasting glucose or evening step counts based on time of day.
- **Personal Historical Baseline Comparison (v1.2):** Deviation alerts calculated against personal 30-day moving averages instead of population standards.
- **Multi-Day Cross-Metric Anomaly Synthesis (v2.0+):** Correlating rapid weight gain with pulse and edema indicators for heart failure warnings.
- **Continuous CGM/Telemetry Waveform Filtering (v2.0+):** Real-time pulse noise filtering for continuous monitoring devices.

### Architecture Approach

The architecture enforces strict separation between domain metadata, validation logic, presentation state, and ingestion channels.

**Major components:**
1. **`MetricMetadataRegistry` (Backend SSOT):** In-memory Spring `@Component` holding immutable `MetricDefinition` records for 19+ metrics. Manages canonical keys, aliases, units, precisions, steppers, and four-tier boundaries.
2. **`MetricKeyNormalizer` (Backend & Frontend Bridge):** Authoritative lookup dictionaries mapping aliases (`heart_rate`, `HEART_RATE`, `pulse`) to canonical keys (`heartRate`) with acronym preservation (`systolicBP` rather than `systolicBp`).
3. **`MetricValidationPipeline` (Backend Core):** Pure domain validation service evaluating four severity tiers (`NORMAL`, `WARNING`, `CRITICAL`, `HARD_LIMIT`) and generating structured results with localized medical explanations.
4. **`ValidMetricRecord` / `MetricRecordValidator` (Backend DTO Constraint):** Jakarta class-level constraint integrating the validation pipeline directly into Spring MVC `@Valid` lifecycle.
5. **`MetricMetadataController` (REST API):** High-performance controller exposing `/api/metrics/metadata` with HTTP caching (`Cache-Control`, `ETag`) and Redis caching.
6. **`useMetricMetaStore` & `useMetricValidation` (Frontend Store & Composable):** Client-side metadata cache and reactive validation logic coordinating Element Plus forms and non-destructive alerts.

### Critical Pitfalls

1. **Hard-Rejecting Clinical Emergencies ("Dying Patient Rejection" Trap):** Confusing optimal reference ranges with biological life limits. Mitigated by the four-tier graduated boundary model where clinical crises (e.g., SBP 220 mmHg) are preserved, confirmed, and escalated to urgent alerts rather than rejected with a 400 error.
2. **Disastrous Cross-Channel Unit Mismatch:** Ingesting divergent units (e.g., Huawei mock glucose generating `80-140 mg/dL` vs canonical `3.9-6.1 mmol/L`; cups vs mL) causing catastrophic 18x health score miscalculations. Mitigated by defining canonical storage units and embedding an automated `UnitConverter` in the ingestion pipeline.
3. **Multi-Format Key Drift Breaking Downstream Scorers & Rule Engines:** Persistence of non-canonical keys (`heart_rate`, `temp`, `spo2`) falling through switch statements in `MetricsScorer` and failing exact-match queries in `AlertRuleEngine`. Mitigated by mandatory normalization before persistence and an idempotent database backfill script.
4. **Destructive Client-Side Blur Clearing & Rigid Input Steppers:** Auto-wiping input values on blur (`form.value = undefined`) and restrictive min/max stepper bindings preventing smooth multi-digit typing. Mitigated by non-destructive reactive guidance banners and submission-time async-validation.
5. **Broken Blood Pressure Atomicity & Independent Validation:** Submitting SBP and DBP in separate uncoordinated HTTP requests or accepting inverted blood pressure (SBP 80 / DBP 120). Mitigated by composite validation rules (`systolicBP >= diastolicBP + 15`) and atomic batch endpoints.
6. **IoT Ingestion Pipeline Meltdown on Synchronous Hard Validation:** Dropping entire 120-point telemetry batches due to a single noisy sensor blip. Mitigated by segregating interactive form validation from machine stream ingestion, adopting a partial-success + quarantine log pattern.

---

## Implications for Roadmap

Based on research findings, architectural boundaries, and pitfall prevention dependencies, the milestone should be executed in a structured 5-phase sequence:

### Phase 1: Backend Metadata Core & Single Source of Truth Registry
**Rationale:** All downstream layers (validation, normalizer, DTO annotations, API controllers, and frontend stores) depend on an authoritative definition of metric properties and boundaries. Building the domain metadata foundation first guarantees zero disruption to existing systems.  
**Delivers:**
- `MetricDefinition` Java Record encapsulating 19+ health and wellness metrics.
- `MetricMetadataRegistry` Spring `@Component` with sub-millisecond lookup and alias indexes.
- Canonical storage unit declarations and baseline multi-tier boundary definitions.
- Comprehensive JUnit 5 test suite verifying metadata completeness and lookup performance.  
**Addresses:** `UNIFIED-METRIC-META` (Backend Core).  
**Avoids:** Pitfall 7 (Split-Brain Hardcoded Range Constants), Pitfall 2 (Unit Mismatch baseline).

### Phase 2: Multi-Tier Validation Pipeline, Unit Conversion & REST Metadata API
**Rationale:** With the central registry established, the system requires domain validation logic, key normalizer utilities, unit conversion adapters, and client-facing metadata endpoints before frontend components can be refactored.  
**Delivers:**
- `MetricKeyNormalizer` supporting camelCase, snake_case, and medical acronym preservation.
- `MetricValidationPipeline` evaluating four severity tiers (Normal, Warning, Critical, Hard Limit).
- Composite metric validation rules (`systolicBP >= diastolicBP + 15`) and atomic compound submission support.
- `UnitConverter` adapter (supporting `mg/dL` $\leftrightarrow$ `mmol/L`, cups $\leftrightarrow$ mL, °F $\leftrightarrow$ °C).
- Jakarta constraint `@ValidMetricRecord` and `MetricRecordValidator`.
- `MetricMetadataController` exposing `GET /api/metrics/metadata` with Redis/HTTP cache headers.  
**Addresses:** `KEY-NORMALIZATION` (Backend), `BP-DUAL-ASSOCIATION` (Backend), Ingestion Resilience.  
**Avoids:** Pitfall 1 (Hard-Rejecting Emergencies), Pitfall 2 (Cross-Channel Unit Mismatch), Pitfall 3 (Scoring Calculation Black Hole), Pitfall 5 (Broken BP Atomicity).

### Phase 3: Frontend Store, Client Normalizer & Display Shield
**Rationale:** Before altering user-facing forms, the client application needs the data infrastructure to consume metadata, resolve aliases, and format display labels without regression.  
**Delivers:**
- `api/metricMeta.ts` for fetching metadata from the backend.
- `stores/metricMeta.ts` Pinia store with memory caching, localStorage persistence, and offline fallback (`metricMetaDefaults.ts`).
- `utils/metricNormalizer.ts` client-side key normalizer.
- Refactored `utils/format.ts` bridging `getMetricDisplayName` and `getMetricUnit` through the normalizer, completely eliminating raw English key leaks.
- Vitest unit test suite covering key normalization and format mappings.  
**Addresses:** `UNIFIED-METRIC-META` (Frontend Store), `KEY-NORMALIZATION` (Frontend Shield).  
**Avoids:** Pitfall 3 (Frontend English Key Leaks), Pitfall 7 (Split-Brain Frontend Constants).

### Phase 4: Non-Destructive Input Guidance, Friendly Feedback & BP Dual Association
**Rationale:** With backend validation endpoints and frontend store ready, the UI layer can be completely overhauled to eliminate aggressive input wipes and deliver rich educational feedback.  
**Delivers:**
- `composables/useMetricValidation.ts` providing real-time health status chips and non-blocking advice.
- Complete overhaul of `QuickInputDialog.vue`: removal of `form.value = undefined` blur handler, dynamic step/precision/placeholder binding, and inline explanatory alert banners.
- Blood pressure dual-metric synchronized input with inversion detection and one-click swap.
- Critical value secondary confirmation modal (`CRITICAL-CONFIRM-MODAL`) with urgent medical advice.
- Refactored `QuickInput.vue`, `views/health/Metrics.vue`, and `Thresholds.vue` binding to dynamic store options.  
**Addresses:** `INPUT-GUIDE`, `FRIENDLY-FEEDBACK`, `BP-DUAL-ASSOCIATION` (Frontend UI), `CRITICAL-CONFIRM-MODAL`.  
**Avoids:** Pitfall 4 (Destructive Blur Clearing & Stepper Lock), Pitfall 1 (Friendly UI Handling of Crisis Values), Pitfall 5 (Inverted Pressure Bug).

### Phase 5: Cross-Channel Convergence, Resilient Batch Quarantine & Historical Backfill
**Rationale:** To complete the milestone, legacy background services, external ingestion pipelines, and historical database rows must be aligned to the new canonical standard.  
**Delivers:**
- Refactored `AiParseServiceImpl` and `OcrServiceImpl` consuming `MetricMetadataRegistry` and retiring redundant internal range maps.
- Resilient batch sync handling for `HuaweiHealthServiceImpl` and `XiaomiHealthServiceImpl` with unit conversion and an anomaly quarantine pattern.
- Database migration script backfilling legacy metric keys (`temperature` $\to$ `bodyTemperature`, `spo2` $\to$ `bloodOxygen`) across `health_metric`, `realtime_metric`, and `alert_rule`.
- Updated scoring logic in `MetricsScorer` and `DimensionScoreCalculator` supporting normalized sub-keys.
- End-to-end regression verification across manual, AI, OCR, and IoT ingestion paths.  
**Addresses:** Cross-Channel Alignment, Data Integrity, End-to-End System Polish.  
**Avoids:** Pitfall 3 (Historical Data Black Hole), Pitfall 6 (IoT Batch Meltdown).

---

### Phase Ordering Rationale

- **Dependency flow:** Domain Metadata Registry (Phase 1) $\to$ Validation Pipeline & REST API (Phase 2) $\to$ Client Store & Normalizer (Phase 3) $\to$ UI Form Overhaul (Phase 4) $\to$ External Pipeline & DB Convergence (Phase 5).
- **Zero-downtime safety:** Backend APIs and normalizers are backward-compatible, meaning existing frontend builds and external IoT mocks remain fully functional during intermediate phases.
- **Risk isolation:** Heavy refactoring of AI prompts, OCR matchers, and database backfills is deferred to Phase 5 after the core validation engine and UI workflows are thoroughly verified with automated tests.

---

### Research Flags

**Phases with standard patterns (skip research-phase):**
- **Phase 1 (Backend Metadata Core):** Pure Java 17 Records, Spring `@Component`, and ConcurrentHashMap caching. Well-established in-memory registry pattern.
- **Phase 3 (Frontend Store & Normalizer):** Standard Pinia setup syntax and TypeScript dictionary utilities with Vitest coverage.
- **Phase 4 (Frontend Form Refactoring):** Standard Vue 3 Composition API reactive patterns and Element Plus form validation configurations.

**Phases likely needing targeted research during planning:**
- **Phase 2 (Validation Pipeline & Composite Rules):** Dynamic Jakarta class-level constraint implementation (`@ValidMetricRecord`) validating polymorphic/multi-field requests, and designing clean atomic transaction semantics for compound blood pressure.
- **Phase 5 (Cross-Channel Convergence & DB Backfill):** Auditing existing foreign keys and index performance on `health_metric` prior to SQL backfill, plus designing the partial-success quarantine log schema without disrupting device sync response formats.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| **Stack** | HIGH | Based on verified versions in `pom.xml` and `package.json`. Zero new dependencies required. Standard Jakarta Validation, Pinia, and Element Plus patterns. |
| **Features** | HIGH | Aligned with national clinical guidelines (China Hypertension 2024, Diabetes 2020), WHO biological boundaries, and standard digital health app interaction paradigms. |
| **Architecture** | HIGH | Grounded in extensive codebase audit of 35 tables, 25 controllers, DTO constraints, and existing data flow bottlenecks across frontend and backend. |
| **Pitfalls** | HIGH | Verified against actual code defects currently in repository (`QuickInputDialog.vue` blur clearing, `HuaweiHealthServiceImpl` mg/dL mock, switch default 0 in `MetricsScorer`). |

**Overall confidence:** HIGH

### Gaps to Address

- **Historical Database Key Inventory:** Prior to running Phase 5 migration scripts, execute read-only queries (`SELECT DISTINCT metric_key, unit FROM health_metric`) to catalog all historical anomalous keys and units currently in production.
- **AI Prompt Token Optimization:** Ensure the prompt template generated dynamically from `MetricMetadataRegistry` for `AiParseServiceImpl` remains concise to minimize LLM token overhead and latency.
- **Composite Metric Schema Evolution:** In v1.1, blood pressure is stored as two associated records (`systolicBP` and `diastolicBP`). If v2.0 introduces a dedicated compound table, the registry must support forward migration.

---

## Sources

### Primary (HIGH confidence)
- **HHS Production Codebase Audit:**
  - `hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java` (verified `METRIC_RANGES`)
  - `hhs-backend/src/main/java/com/hhs/service/impl/OcrServiceImpl.java` (verified `REFERENCE_RANGES`)
  - `hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java` (verified `mg/dL` glucose and `spo2` key)
  - `hhs-backend/src/main/java/com/hhs/service/domain/MetricDisplayFormatter.java` & `MetricsScorer.java`
  - `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` (verified destructive blur wipe)
  - `hhs-frontend-v2/src/utils/format.ts` & `views/health/Metrics.vue`
- **Clinical & Regulatory Standards:**
  - 《中国高血压防治指南（2024年修订版）》— National Hypertension Prevention & Treatment Guidelines
  - 《中国2型糖尿病防治指南（2020版）》— National Type 2 Diabetes Management Standards
  - World Health Organization (WHO) Biological Plausibility Limits for Physiological Telemetry
- **Official Specifications:**
  - Jakarta Bean Validation 3.0 (Hibernate Validator 8.0.1.Final Specification)
  - Element Plus 2.5.6 Form & Async-Validator Documentation

### Secondary (MEDIUM confidence)
- Apple HealthKit Human Interface Guidelines & Huawei Health SDK Integration Guide regarding non-destructive input tolerance and unit synchronization.

---
*Research completed: 2026-11-04*  
*Ready for roadmap: yes*  
