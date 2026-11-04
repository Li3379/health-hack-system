# Stack Research

**Domain:** Intelligent Health Metric Input, Unified Range Validation & Pre-guidance System (HHS v1.1)
**Researched:** 2026-11-04
**Confidence:** HIGH

## Recommended Stack

This milestone adds unified health metric range metadata, client/server validation, smart input guidance, and metric key normalization (camelCase vs snake_case). All architectural capabilities are built directly on top of the validated existing foundation (Spring Boot 3.2.0 and Vue 3.4.15) with zero third-party dependency bloat.

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Jakarta Bean Validation (Hibernate Validator)** | 3.0.0 (Hibernate Validator 8.0.1.Final via `spring-boot-starter-validation`) | Server-side validation of incoming metric payloads, dynamic range constraints | Standard in Spring Boot 3.2; supports custom class-level constraints (`@ValidMetricRecord`) to evaluate dynamic bounds based on metric type and provide rich localized failure messages. |
| **Spring Boot Web MVC + Jackson** | 3.2.0 (Jackson 2.15.3) | REST metadata endpoint, custom deserialization / property aliasing | Jackson `@JsonSetter` and `@JsonAlias` combined with a Spring-managed normalizer seamlessly accept both `snake_case` (`heart_rate`, `systolic_bp`) and `camelCase` (`heartRate`, `systolicBP`) payloads without breaking legacy clients. |
| **Vue 3 (Composition API & `<script setup>`)** | 3.4.15 (TypeScript 5.5) | Reactive input state, dynamic guidance computation, non-blocking warning hints | Provides instant reactivity (`ref`, `computed`, `watch`) for soft validation guidance, calculating deviations and contextual hints as user types before form submission. |
| **Element Plus Form & Input** | 2.5.6 (`el-form`, `el-input-number`, `el-alert`) | Form input controls, async-validator rule engine, warning banners | Element Plus natively wraps `async-validator`. Using its built-in rule system avoids adding third-party form libraries while cleanly separating hard blocking errors from soft educational guidance. |
| **Pinia Store** | 2.1.7 (`useMetricMetaStore`) | Client-side caching and distribution of unified metric metadata | Caches metadata fetched from `/api/health-metrics/metadata` in memory with localStorage persistence fallback, eliminating redundant API requests across quick input, dialogs, and AI parse views. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Hutool Core** | 5.8.28 (`cn.hutool:hutool-all`, already in `pom.xml`) | Fallback string case conversion (`StrUtil.toCamelCase`, `StrUtil.toUnderlineCase`) | Used as a defensive fallback in `MetricKeyNormalizer` for unforeseen metric keys from external devices or OCR. |
| **Spring Cache + Redis** | 3.2.0 (`spring-boot-starter-cache` + Redis 7.0) | Caching metadata registry responses | Applied to `HealthMetricMetaService.getMetadataMap()` with `@Cacheable(value = "metric_meta")` to guarantee sub-millisecond API response times. |
| **Vitest** | 4.1.0 (already in `package.json`) | Frontend unit testing for validation rules and key normalizer | Used to test `metricNormalizer.ts` and `metricValidator.ts` across all 20+ health and wellness metrics. |
| **Spring Boot Starter Test (JUnit 5 + MockMvc)** | 3.2.0 (already in `pom.xml`) | Backend validation testing | Used to verify `@ValidMetricRecord` constraints, hard boundary rejections, and camel/snake alias handling. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **ESLint 9 + Prettier 3** | Frontend code style and typing compliance | Ensure TypeScript interfaces (`MetricMeta`, `MetricValidationResult`) stay strictly typed across all components. |
| **Maven Checkstyle Plugin** | Backend Google Checkstyle (4-space indentation) | Enforce consistent Java code conventions for new validation annotations and normalizer beans. |

## Installation

No new external packages or maven artifacts are required. The entire intelligent guidance and validation architecture leverages built-in features of the existing validated stack.

```bash
# Backend (hhs-backend)
# All dependencies already present in pom.xml:
# - spring-boot-starter-validation:3.2.0
# - spring-boot-starter-web:3.2.0
# - hutool-all:5.8.28
# Verify existing dependencies:
mvn dependency:resolve

# Frontend (hhs-frontend-v2)
# All dependencies already present in package.json:
# - vue:^3.4.15
# - element-plus:^2.5.6
# - pinia:^2.1.7
# Verify existing dependencies:
npm list element-plus pinia vue
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| **In-Memory Java Registry (`HealthMetricRegistry`) + REST API (`/api/health-metrics/metadata`)** | Dedicated Database Table (`health_metric_definition`) | If clinical range definitions need to be edited at runtime by hospital administrators or doctors via a GUI without redeploying code. For HHS, health metrics are standardized clinical constants (~25 items); a code-defined registry provides zero DB overhead, zero migration risk, compile-time type safety, and instant startup performance. |
| **Dual-Tier Validation (Element Plus Rule for Hard Limits + Reactive Helper Card for Soft Limits)** | Single Strict Blocking Rule (`callback(new Error(...))`) | If any out-of-range value is strictly illegal. In healthcare, physiological impossibility (e.g. body temperature 25°C or 50°C) is an error, but abnormal clinical values (e.g. fasting glucose 7.5 mmol/L) are valid medical observations. Blocking abnormal values with red errors frustrates users and prevents recording real health crises. |
| **Element Plus Native `async-validator` Rules** | VeeValidate / Zod / Yup | When building multi-step schema-driven forms with headless UI. In Element Plus, adding VeeValidate or Zod creates competing validation states, duplicate error messages, and styling friction. |
| **Authoritative Normalizer Map (`MetricKeyNormalizer`) + Jackson `@JsonSetter`** | Global Jackson `PropertyNamingStrategies.SNAKE_CASE` | When the entire API ecosystem strictly adopts snake_case. In HHS, frontend APIs and DB entities use camelCase, but external hardware/OCR logs use snake_case (`heart_rate`, `systolic_bp`). A global Jackson naming strategy would break existing camelCase frontend requests. An explicit bidirectional normalizer dictionary resolves both gracefully. |
| **Pinia Store (`useMetricMetaStore`) with Local TS Fallback** | Hardcoded Constants per Component | Never. HHS currently suffers from duplicate definitions across `AiParseServiceImpl`, `OcrServiceImpl`, `MetricDisplayFormatter`, and `QuickInputDialog`. A centralized Pinia store fed by the backend API ensures single-source truth. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **Drools / EasyRules (Heavy Rule Engines)** | Adds 20MB+ jar dependencies, steep learning curve, and high heap allocation for straightforward numeric range comparisons. | Spring Component `HealthMetricMetaRegistry` with standard Java records and `BigDecimal` comparisons. |
| **VeeValidate / Zod on Element Plus Forms** | Conflicts with `el-form`'s internal validation lifecycle and Element Plus DOM event triggers (`change`, `blur`). | Element Plus built-in `FormRules` validator functions paired with reactive Vue composables (`useMetricValidation`). |
| **Full Lodash / Humps NPM Libraries (`lodash-es`, `humps`, `change-case`)** | Adds 50KB+ to the bundle for trivial string casing operations, and algorithmic camelCase fails on domain acronyms like `systolic_bp` -> `systolicBp` (canonical is `systolicBP`). | Lightweight `@/utils/metricNormalizer.ts` with explicit acronym-aware mapping dictionary. |
| **Scattered Hardcoded Ranges in UI Components** | Currently `QuickInputDialog.vue` has its own ranges, `AiParseServiceImpl.java` has its own ranges, and `OcrServiceImpl.java` has its own ranges. Inevitably leads to divergence and stale bugs. | Centralized `HealthMetricRegistry` on backend, consumed by frontend via `useMetricMetaStore` and fallback `@/constants/metricMeta.ts`. |
| **Rigid Red Errors for Mild Clinical Abnormalities** | Showing red blocking borders when a user types blood glucose 6.8 mmol/L stops them from saving valid data. | Soft guidance: show warning tags with gentle suggestions (e.g. "略高于正常参考值 3.9~6.1 mmol/L，建议空腹静息复测") while keeping the Save button active. |

## Stack Patterns by Variant

### Server-Side Dynamic Payload Validation

**If receiving metric records via `HealthMetricRequest` / `WellnessMetricRequest`:**
- Use a custom class-level constraint `@ValidMetricRecord` evaluated by `MetricRecordValidator`:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MetricRecordValidator.class)
public @interface ValidMetricRecord {
    String message() default "健康指标数据校验失败";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```
- In `MetricRecordValidator`:
  1. Normalize `metricKey` using `MetricKeyNormalizer.toCanonicalKey(request.getMetricKey())`.
  2. Reject unknown keys with `ErrorCode.VALIDATION_INVALID_PARAMETER`.
  3. Validate `value` against physiological bounds (`hardMin <= value <= hardMax`).
  4. If `metricKey` is `systolicBP`, validate that accompanying `diastolicValue` satisfies `diastolicBP < systolicBP` and both lie within physiological limits.

### Flexible Metric Key Deserialization

**If metricKey is transmitted as either `heart_rate` or `heartRate`:**
- In `HealthMetricRequest.java`:
```java
public void setMetricKey(String metricKey) {
    this.metricKey = MetricKeyNormalizer.toCanonicalKey(metricKey);
}
```
- In `MetricKeyNormalizer.java`:
  - Provide a high-performance hash map mapping both `heart_rate`, `heartrate`, `heartRate` to canonical `heartRate`.
  - Provide fallback to `StrUtil.toCamelCase(key)` for unexpected inputs.
  - In `MetricDisplayFormatter.java`, always pass keys through `MetricKeyNormalizer.toCanonicalKey()` before matching display names, eliminating raw English keys leaking to the UI.

### Client-Side Dual-Tier Form Guidance & Validation

**If user is entering data in `QuickInputDialog.vue` or custom form:**
- **Tier 1 (Hard Limits - Blocking):**
  - Set `el-input-number` `:min="meta.hardMin"` and `:max="meta.hardMax"`.
  - In `FormRules.validator`: Return `callback(new Error('...'))` only when value is outside physiological hard limits or null.
- **Tier 2 (Soft Guidance - Advisory & Non-blocking):**
  - Compute reactive status:
    ```typescript
    const metricStatus = computed<'normal' | 'low' | 'high' | 'critical'>(() => {
      if (!val || !meta) return 'normal';
      if (val < meta.normalMin) return 'low';
      if (val > meta.normalMax) return 'high';
      return 'normal';
    });
    ```
  - Display non-blocking contextual guidance card (`el-alert` or tag):
    - When `low`: "数值低于参考范围（[normalMin] ~ [normalMax] [unit]）。提示：[meta.lowAdvice]"
    - When `high`: "数值高于参考范围（[normalMin] ~ [normalMax] [unit]）。提示：[meta.highAdvice]"
    - When `normal`: "数值在正常理想范围内。"
  - Pre-input placeholders: "参考范围: 3.9 ~ 6.1 mmol/L"

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `spring-boot-starter-validation@3.2.0` | `hibernate-validator@8.0.1.Final` | Uses `jakarta.validation.*` namespace (not legacy `javax.validation.*`). |
| `element-plus@2.5.6` | `vue@3.4.15` | Native async-validator engine fully compatible with Vue 3 reactive proxy system. |
| `pinia@2.1.7` | `vue@3.4.15` | Seamless store setup syntax `defineStore('metricMeta', () => { ... })`. |
| `jackson-databind@2.15.3` | `spring-boot-starter-web@3.2.0` | Supports custom setter normalization and `@JsonAlias`. |
| `vitest@4.1.0` | `vite@5.0.11` & `typescript@5.5.0` | Fast unit test execution for normalization and validation logic. |

## Sources

- **Codebase Analysis**:
  - `hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java` (verified existing `METRIC_RANGES`)
  - `hhs-backend/src/main/java/com/hhs/service/impl/OcrServiceImpl.java` (verified existing `REFERENCE_RANGES`)
  - `hhs-backend/src/main/java/com/hhs/service/domain/MetricDisplayFormatter.java` (verified raw key display leak bug)
  - `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` (verified localized hardcoded `METRIC_RANGES`)
  - `hhs-backend/src/main/java/com/hhs/dto/HealthDataPoint.java` (verified device snake_case mappings)
- **Jakarta Bean Validation 3.0 Specification** — Class-level dynamic constraints (`ConstraintValidator<A, T>`).
- **Element Plus 2.5.6 Documentation** — Form validation with custom async-validator functions.
- **Internal Seam Verification**: Verified provider tiers via `gsd_run query classify-confidence`.

---
*Stack research for: Intelligent Health Metric Input & Unified Validation (HHS v1.1)*
*Researched: 2026-11-04*
