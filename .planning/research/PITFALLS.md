# Pitfalls Research

**Domain:** Intelligent Health Metric Input, Unified Range Validation & Pre-guidance System (HHS v1.1)  
**Researched:** 2026-11-04  
**Confidence:** HIGH (Codebase-verified against 35 tables, 25 controllers, and active IoT/AI/OCR ingestion pipelines)

---

## Critical Pitfalls

### Pitfall 1: Hard-Rejecting Clinical Emergencies via Single-Tier Over-Validation (The "Dying Patient Rejection" Trap)

**What goes wrong:**  
Enforcing strict Jakarta Bean Validation (`@DecimalMin`/`@DecimalMax`) or blocking UI validation rules (`callback(new Error(...))`) using normal physiological reference boundaries (e.g., systolic blood pressure capped at 180 mmHg, blood glucose capped at 20 mmol/L, heart rate capped at 140 bpm). When real patients experience acute medical emergencies—such as a hypertensive crisis (systolic BP 220 mmHg), diabetic ketoacidosis (glucose 28 mmol/L), or paroxysmal supraventricular tachycardia (heart rate 190 bpm)—the platform rejects their submission with an unhelpful `400 Bad Request` or an impassable red input border. The user cannot log their data precisely when life-saving monitoring and urgent intervention are needed most.

**Why it happens:**  
Developers confuse "clinical normal reference range" (the optimal target zone, e.g., fasting glucose 3.9–6.1 mmol/L, systolic BP 90–140 mmHg) with "biological plausibility limits" (the extreme physical boundaries compatible with human life, e.g., glucose 1.0–33.3 mmol/L, systolic BP 40–300 mmHg). Applying strict validation against normal bounds turns an alertable medical anomaly into an application validation error.

**How to avoid:**  
Implement a **Three-Tier Validation Architecture**:
1. **Tier 1: Physiological Impossibility (Hard Blocking Boundary):** Values physically impossible for a living human or sensor artifact (e.g., heart rate $\le 0$ or $> 300$ bpm, body temperature $< 30^\circ\text{C}$ or $> 45^\circ\text{C}$, systolic BP $\le 0$ or $> 300$ mmHg). Reject immediately with actionable error feedback.
2. **Tier 2: Clinical Crisis / Extreme Anomaly (Soft Warning + Elevated Alert):** Values within physical possibility but indicating urgent clinical danger (e.g., systolic BP $> 180$ mmHg, glucose $> 16.7$ mmol/L). Allow submission with an interactive confirmation prompt ("您的数值达到危机预警范围，请确认数值是否有误。如属实，建议立即就医"), persist the record, and immediately trigger high-priority alerts via WebSocket and external channels.
3. **Tier 3: Sub-optimal / Lifestyle Deviation (Educational Inline Guidance):** Values mildly outside ideal range (e.g., fasting glucose 6.2–7.0 mmol/L). Allow submission seamlessly; display a gentle, non-blocking amber badge or contextual suggestion ("略高于参考范围，建议空腹复测并关注近期饮食").

**Warning signs:**  
- DTO annotations like `@DecimalMax(value = "140.0")` on `systolicBP` or `@DecimalMax(value = "7.0")` on `glucose`.
- Unit tests that assert a `400 Bad Request` when sending extreme medical values.
- Production error logs recording failed submissions from users experiencing severe hypertension or high fever.

**Phase to address:**  
Phase 1 (Unified Metric Dictionary & Multi-Tier Range Definition).

---

### Pitfall 2: Disastrous Cross-Channel Unit Mismatch (mg/dL vs mmol/L, Cups vs mL)

**What goes wrong:**  
Different metric ingestion channels use divergent measurement units, but the ingestion engine validates and stores the raw numeric value directly into the database without unit-aware conversion.
- **Glucose disaster:** `HuaweiHealthServiceImpl.java` generates mock glucose values of `80–140 mg/dL` (standard in overseas glucometers). However, HHS defines its canonical glucose range as `3.9–6.1 mmol/L` (standard in Chinese hospitals, where $1\text{ mmol/L} \approx 18\text{ mg/dL}$). If range validation evaluates $80 \le \text{value} \le 140$ against a mmol/L boundary ($\le 33.3$), the IoT sync fails entirely. Conversely, if accepted without conversion, `MetricsScorer.java` and `DimensionScoreCalculator.java` evaluate $110\text{ mmol/L}$ as a lethal overdose (equivalent to nearly $2000\text{ mg/dL}$), completely destroying the user's metabolic health score!
- **Water intake discrepancy:** `format.ts` configures `waterIntake` with the unit "杯" (cups, typical daily value 6–10), whereas `MetricDisplayFormatter.java` and `QuickInput.vue` configure `waterIntake` with the unit "ml" (typical daily value 1500–3000 mL). Storing a "8" into a system expecting mL flags severe dehydration.

**Why it happens:**  
Existing code in `HealthMetricServiceImpl.java` (`normalizeUnit`) merely compares strings:
```java
// Current flaw: logs a warning and stores the wrong unit/value as-is!
if (!providedUnit.equals(standardUnit) && !standardUnit.isEmpty()) {
    log.warn("User provided unit '{}' for metric '{}', standard unit is '{}'. Using provided unit.",
            providedUnit, metricKey, standardUnit);
}
return providedUnit;
```
It does not perform numeric conversion. Different teams, libraries, and mock generators introduced conflicting conventions across frontend and backend.

**How to avoid:**  
1. Declare a single **Canonical Storage Unit** for every metric in the central dictionary (e.g., glucose $\to$ `mmol/L`, waterIntake $\to$ `ml`, bodyTemperature $\to$ `°C`).
2. Build an automated bidirectional `UnitConverter` component inside the ingestion pipeline.
3. When incoming payloads (whether from Huawei Health, Xiaomi Health, Baidu OCR, or AI chat) supply an alternate recognized unit, convert the numeric value to the canonical unit *before* range validation and database persistence (e.g., `mg/dL -> val / 18.0182 -> mmol/L`; `杯 -> val * 250 -> ml`).
4. Reject or request confirmation for unrecognizable, incompatible units.

**Warning signs:**  
- `normalizeUnit()` in `HealthMetricServiceImpl.java` returning `providedUnit` unchanged.
- `HuaweiHealthServiceImpl.java` lines 556–558 generating `glucose` with `80 + random.nextInt(60)` and unit `"mg/dL"`.
- `format.ts` having `waterIntake: { unit: '杯' }` while `QuickInput.vue` displays `unit: 'ml'`.

**Phase to address:**  
Phase 1 (Dictionary & Canonical Unit Normalizer) and Phase 2 (Ingestion Gateway Unit Conversion Adapter).

---

### Pitfall 3: Multi-Format Key Drift Breaking Downstream Scorers & Rule Engines (The Silent Calculation Black Hole)

**What goes wrong:**  
Metric keys arrive in heterogeneous formats: camelCase (`heartRate`, `systolicBP`, `bodyTemperature`), snake_case (`heart_rate`, `systolic_bp`, `body_temperature`), abbreviations (`spo2` vs `bloodOxygen`, `temp` vs `temperature`), and SCREAMING_SNAKE_CASE (`HEART_RATE`, `BLOOD_PRESSURE`).
When records are persisted with non-canonical keys:
1. `MetricsScorer.java` and `DimensionScoreCalculator.java` only evaluate 5 hardcoded keys (`heartRate`, `systolicBP`, `diastolicBP`, `glucose`, `bmi`). Submissions named `fastingGlucose`, `heart_rate`, `bodyTemperature`, or `spo2` fall into `default -> 0` penalty points. The health score calculation silently ignores them!
2. `AlertRuleEngineImpl.java` and `ThresholdEvaluator.java` query `alert_rule` using exact key equality (`eq(AlertRule::getMetricKey, metricKey)`). Because `alert_rule` contains `'temperature'`, any data ingested via AI parse or OCR as `'bodyTemperature'` will NEVER trigger an alert, even if the user has a 41°C life-threatening fever!
3. The frontend displays raw English strings like `fastingGlucose`, `spo2`, or `body_temperature` on the dashboard because `getMetricDisplayName` in `format.ts` lacks those specific mappings.

**Why it happens:**  
Over 15 distinct backend and frontend files maintain separate, uncoordinated string mappings (`MetricCategoryService`, `MetricDisplayFormatter`, `AiParseServiceImpl`, `OcrServiceImpl`, `MetricsScorer`, `DimensionScoreCalculator`, `format.ts`, `schema.sql`). No single canonical enum or normalizer service was designated as the sole authority.

**How to avoid:**  
1. **Canonical Metric Enum:** Establish `MetricKey` enum in the backend (canonical camelCase: `heartRate`, `systolicBP`, `diastolicBP`, `fastingGlucose`, `postprandialGlucose`, `bodyTemperature`, `bloodOxygen`, `weight`, `bmi`, etc.).
2. **Authoritative Normalizer:** Implement `MetricKeyNormalizer` that maps known aliases (`temp`, `temperature`, `body_temperature` $\to$ `bodyTemperature`; `spo2`, `blood_oxygen` $\to$ `bloodOxygen`; `heart_rate` $\to$ `heartRate`) before any validation, scoring, or persistence occurs.
3. **Database Backfill:** Execute a database migration script to normalize existing rows in `health_metric`, `realtime_metric`, `alert_rule`, and `alert_template`.
4. **Sub-metric Aggregation:** Update `DimensionScoreCalculator` and `MetricsScorer` so sub-types (e.g., `fastingGlucose` and `postprandialGlucose`) contribute properly to their respective dimension (metabolic score).

**Warning signs:**  
- `alert_rule` table having `'temperature'` while `AiParseServiceImpl` and `MetricCategoryService` use `'bodyTemperature'`.
- `HuaweiHealthServiceImpl.java` persisting `"spo2"` while `MetricCategoryService` expects `"bloodOxygen"`.
- `format.ts` maintaining two completely disjoint maps for camelCase and SCREAMING_SNAKE_CASE.
- Hardcoded `switch (key)` blocks with `default -> 0` across scoring classes.

**Phase to address:**  
Phase 2 (Key Normalization Engine & Database Backfill).

---

### Pitfall 4: Destructive Client-Side Blur Clearing & Rigid Input Steppers (The Zero-Tolerance Input Wipe Anti-Pattern)

**What goes wrong:**  
In `QuickInputDialog.vue`, on the `@blur` event of numeric input, if the value is out of range, the component fires a warning message AND wipes the user's input:
```javascript
// QuickInputDialog.vue lines 186-189
if (val < r.min || val > r.max) {
  ElMessage.warning(`数值超出有效范围 ${r.min} - ${r.max}，请重新输入`)
  form.value.value = undefined // Destructive wipe!
  formRef.value?.validateField('value')
}
```
If an elderly user or a patient with tremors accidentally enters `1200` instead of `120`, the input field instantly empties. If they pause or attempt to use backspace to edit the typo, their work is gone. Furthermore, adding rigid `:min="40"` on `<el-input-number>` in Element Plus causes the component to refuse intermediate key presses (e.g., typing "1" or "2" while intending to type "120" gets instantly clamped or rejected).

**Why it happens:**  
Developers treat input validation as a destructive reset mechanism rather than an informative feedback mechanism. The desire to keep the form state strictly compliant at all millisecond intervals leads to aggressive UI event handlers that ruin user experience.

**How to avoid:**  
1. **Non-Destructive Guidance:** Never wipe, reset, or set `form.value = undefined` on blur or change events. Preserve whatever the user typed.
2. **Contextual Inline Explanations:** Display an inline guidance banner under the field explaining *why* the value looks wrong and what was expected (e.g., "您输入的收缩压为 1200 mmHg。正常收缩压通常在 90~140 mmHg 之间，您是否想输入 120 mmHg？").
3. **One-Click Correction:** Provide an interactive autofix button ("修正为 120") if a common order-of-magnitude typo or decimal slip is detected (e.g., entered 365 for temperature $\to$ suggest 36.5°C).
4. **Relaxed Widget Constraints:** Do not bind restrictive `:min` and `:max` HTML attributes that inhibit normal typing of multi-digit numbers. Use form rules (`FormRules`) for validation on submission and soft banners for real-time guidance.

**Warning signs:**  
- `form.value.value = undefined` inside blur event listeners.
- Users complaining that numbers "disappear" or cannot be typed on mobile devices.
- `<el-input-number>` configured with step=1 and min/max that blocks smooth decimal entry.

**Phase to address:**  
Phase 3 (Frontend Smart Guidance & Non-Destructive Feedback Components).

---

### Pitfall 5: Broken Blood Pressure Atomicity & Independent Validation (The Half-Pressure & Inverted Pressure Bug)

**What goes wrong:**  
Blood pressure is a single physiological clinical event consisting of two coupled values: Systolic Blood Pressure (SBP / 收缩压) and Diastolic Blood Pressure (DBP / 舒张压).
Currently in `QuickInputDialog.vue`:
```typescript
// QuickInputDialog.vue lines 220-231
await healthApi.createMetric(data) // SBP request
if (props.metric.key === 'systolicBP' && form.value.diastolicValue) {
  const diastolicData = { metricKey: 'diastolicBP', ... }
  await healthApi.createMetric(diastolicData) // DBP request
}
```
1. **Half-Record Bug:** SBP is dispatched in HTTP request 1; DBP is dispatched in HTTP request 2. If request 1 succeeds and request 2 fails (network interruption, validation error, server restart), the patient is left with half a blood pressure record.
2. **Inverted Pressure Bug:** Single-field validators evaluate SBP in $[60, 200]$ and DBP in $[40, 200]$. Because they evaluate fields independently, an inverted entry where $\text{SBP} = 80$ and $\text{DBP} = 120$ passes both validators with flying colors! Physiologically, systolic pressure must exceed diastolic pressure by at least 15–20 mmHg (Pulse Pressure / 脉压差); systolic $\le$ diastolic is a physiological impossibility in human circulation.

**Why it happens:**  
The relational schema models health metrics as independent scalar rows (`user_id`, `metric_key`, `value`) without supporting compound metric types or cross-field validation rules during ingestion.

**How to avoid:**  
1. **Cross-Field Validation Rule:** Add composite validation logic: `systolicBP >= diastolicBP + 15`. Both frontend `async-validator` and backend validation must reject inverted or zero-pulse-pressure combinations with a clear explanation: "收缩压（高压）必须高于舒张压（低压）至少 15 mmHg".
2. **Atomic Ingestion Endpoint:** Provide a batch/composite metric endpoint (`POST /api/health-metrics/batch` or dedicated compound BP submission) wrapped in `@Transactional` so both SBP and DBP succeed or fail together as a unit.
3. **Compound Input in AI/OCR:** If a user enters or uploads a single blood pressure number (e.g., "血压 120"), prompt for the missing diastolic number rather than blindly dropping or saving an incomplete record.

**Warning signs:**  
- Successive `await healthApi.createMetric()` calls in frontend code without transaction wrapping.
- Database records where a user has systolic records without matching diastolic records at the same timestamp.
- Validation logic that checks `min` and `max` on individual fields without inspecting sibling fields.

**Phase to address:**  
Phase 1 (Composite Metric Validation Rules) and Phase 2 (Atomic Ingestion Endpoint & Frontend Compound Form).

---

### Pitfall 6: IoT & AI Ingestion Pipeline Meltdown on Synchronous Hard Validation (The Fragile Bulk Sync)

**What goes wrong:**  
Adding strict synchronous validation (`@Valid` throwing `MethodArgumentNotValidException` or `BusinessException`) to bulk ingestion endpoints (`POST /api/device/huawei/sync`, `POST /api/ai/confirm-metrics`, `POST /api/ocr/confirm`).
When an IoT wearable syncs 120 historical telemetry points from a 24-hour monitoring period, if just **one** reading contains sensor noise (e.g., optical heart rate sensor blip of 0 bpm or 350 bpm when the watch was briefly taken off), the entire HTTP request fails with 400 or 500. All 119 perfectly healthy readings are discarded. The mobile device enters a retry loop, draining battery and failing sync indefinitely.

**Why it happens:**  
Developers treat machine-to-machine batch telemetry streams with the same "all-or-nothing rollback" semantics used for interactive manual form submissions.

**How to avoid:**  
1. **Validation Strategy Segregation:**
   - *Interactive Manual Form (Human-in-the-loop):* Synchronous, highly communicative, blocks save if impossible, guides correction.
   - *Batch / IoT / OCR Ingestion (Machine Streams):* **Partial Success + Quarantine Pattern**.
2. Return a partial-success response DTO (`savedCount`, `rejectedCount`, `errors: [...]`).
3. Routinely accept valid data points into `health_metric`, and route out-of-range anomalies into a dedicated `metric_quarantine_log` table with the raw payload and error reason.
4. Notify the user or log a low-priority warning: "成功同步 119 条健康数据，1 条异常数据已过滤（心率 0 次/分）".

**Warning signs:**  
- `HuaweiHealthServiceImpl.java` or `XiaomiHealthServiceImpl.java` failing the entire sync loop if an individual `healthMetricService.add()` throws an unchecked exception.
- Lack of partial-success response structures (`Result<BatchSyncResultVO>`).
- IoT sync integration tests testing only 100% valid mock data without testing noisy sensor streams.

**Phase to address:**  
Phase 2 (Resilient Batch Ingestion & Anomaly Quarantine Strategy).

---

### Pitfall 7: Split-Brain Hardcoded Range Constants Across Frontend and Backend

**What goes wrong:**  
Frontend maintains ranges in `QuickInputDialog.vue` (`weight: 20-200 kg`, `mood: 1-5`, `exerciseMinutes: 0-300 min`). Backend maintains ranges in `AiParseServiceImpl.java` (`weight: 1-500 kg`, `mood: 1-10`, `exerciseMinutes: 0-1440 min`). Backend also maintains alert thresholds in MySQL `alert_rule` table.
Inconsistencies quickly emerge:
- In `QuickInputDialog.vue`, `exerciseMinutes` max is 300 minutes. If a marathon runner logs a 360-minute training session, the frontend refuses to submit. But in `AiParseServiceImpl`, 360 minutes is parsed without warning ($0 \le 360 \le 1440$).
- In `QuickInputDialog.vue`, `mood` is $1–5$. In `AiParseServiceImpl`, `mood` is $1–10$. A user who tells the AI "我今天心情还不错，打 7 分" gets parsed by AI, but the frontend rate component breaks or clamps to 5.
- If a clinical board updates the pre-hypertension threshold from 140 to 130 mmHg, developers update `alert_rule` in MySQL, but forget `QuickInputDialog.vue`, `AiParseServiceImpl.java`, and `format.ts`.

**Why it happens:**  
Dual-maintenance of domain configuration across TypeScript files, Java source files, and database rows without an authoritative, single source of truth.

**How to avoid:**  
1. **Single Source of Truth (Backend Registry):** Define metric metadata once in a Spring Boot registry (`HealthMetricMetaRegistry`).
2. **Metadata API:** Expose `GET /api/health-metrics/metadata` returning all metric definitions, display names, canonical units, valid units, Tier 1 boundaries, Tier 2 warning thresholds, precision, steps, and default guidance text.
3. **Pinia Client Store (`useMetricMetaStore`):** Frontend fetches metadata on app startup, caches in Pinia + `sessionStorage`, and supplies it reactively to `QuickInput`, `QuickInputDialog`, `Metrics.vue`, `AIInput`, and `format.ts`.
4. Provide a static fallback JSON in frontend (`@/constants/metricMetaFallback.ts`) solely for offline or initial hydration before the API returns.

**Warning signs:**  
- Hardcoded numerical dictionaries (`METRIC_RANGES = { ... }`) in Vue `<script>` blocks.
- Divergent scale definitions (e.g., mood 1–5 in UI vs 1–10 in backend AI prompt).
- Updating a single metric range requiring pull requests in both backend and frontend repositories.

**Phase to address:**  
Phase 1 (Single Source of Truth Metric Metadata Service).

---

## Technical Debt Patterns

Shortcuts that seem reasonable during implementation but create long-term architectural debt.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| **Client-Only Range Validation** | Zero backend coding; instant Element Plus form feedback. | Direct API calls, IoT synchronization, and AI auto-confirm bypass validation completely, corrupting database with absurd values. | **NEVER.** All physiological rules must be enforced by the backend authority. |
| **Algorithmic Casing Conversion (`StrUtil.toCamelCase`) without Acronym Map** | 2 lines of utility code; handles simple cases like `heart_rate -> heartRate`. | Fails on medical acronyms: converts `systolic_bp` to `systolicBp` instead of canonical `systolicBP`, silently severing scoring and alert links. | Only acceptable as a fallback for unregistered, custom user metrics. |
| **In-Memory Java Registry without DB Table for Range Metadata** | Zero database migrations, compile-time type safety, sub-millisecond startup, no SQL overhead. | Clinical range adjustments require a code change and container redeployment rather than a database row update. | **ACCEPTABLE for v1.1.** Human biological limits (Tier 1) are physiological invariants; HHS does not require non-technical staff to edit ranges at runtime. |
| **Silently Dropping Non-Compliant Data Points from IoT Sync** | Ingestion pipeline never crashes; error handling is 1 line of code (`catch (e) { continue; }`). | Users experience mysterious data gaps (e.g. missing 3 hours of sleep or heart rate during workout); impossible to audit or diagnose sensor bugs. | **NEVER.** Rejected data must be logged to a quarantine log with explicit reason. |
| **Dual-Column Unit Storage (storing raw value + raw unit alongside converted value)** | Preserves the exact user input without commiting to a single system of measurement. | Massive schema duplication, index bloat, and confusion in SQL queries regarding which column to aggregate or score. | **NEVER.** Convert immediately to canonical storage unit upon ingestion; store user-provided unit in an optional metadata audit column if needed. |

---

## Integration Gotchas

Common pitfalls when interfacing with external data providers, devices, and AI models.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **Huawei Health SDK** | Assuming blood glucose arrives in `mmol/L` and oxygen arrives as `bloodOxygen`. Huawei mock and hardware SDK transmit glucose in `mg/dL` ($80–140$) and SpO2 as `spo2`. | Ingestion adapter must intercept `spo2` $\to$ alias to `bloodOxygen`, and detect unit `mg/dL` $\to$ divide by $18.0182$ to convert to `mmol/L` before range validation. |
| **Baidu OCR (Hospital Lab Reports)** | Hospital reports print varied Chinese terminology ("白细胞计数", "WBC", "白血球"), scientific notation units (`10^9/L`, `10*9/L`), and composite blood pressure ("125/82 mmHg"). | OCR parser must use regex-based alias matching to map Chinese/abbreviations to canonical keys, sanitize scientific units to `10e9/L`, and parse compound BP into separate systolic and diastolic data points. |
| **Tongyi Qianwen AI Parsing (LLM)** | The LLM may hallucinate non-standard metric keys (`heart_bpm`, `blood_sugar`, `bodyTemp`) or compound units ("mmol/l", "毫摩尔/升", "次每分"). | Provide strict system prompt instructions specifying only canonical keys; add a backend post-processing fuzzy normalizer that coerces common AI hallucination variations into canonical keys. |
| **Xiaomi Health SDK (Stub)** | Assuming mock data timestamps match server local date; during real device activation, timestamps arrive as UTC millisecond timestamps, leading to timezone date shifting. | Ingestion layer must parse timestamps using timezone-aware `Instant` mapped to user's configured timezone (`ZoneId.of("Asia/Shanghai")`) before setting `recordDate`. |

---

## Performance Traps

Patterns that work during unit testing but fail under production concurrency or data volume.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| **Global Score Cache Eviction on Every Sub-Metric** | A user inputs blood pressure (generating 2 events: SBP and DBP in rapid succession). Cache is evicted twice in 50ms, triggering 2 redundant full health score calculations in MySQL. | Debounce event publishing or introduce a multi-metric transaction event (`MetricsBatchRecordedEvent`) that triggers only one recalculation. | Noticeable with $> 50$ concurrent users or frequent IoT syncs. |
| **Uncached Metadata API Calls on Every UI Interaction** | Opening `QuickInputDialog.vue`, `AIInput.vue`, or navigating between pages triggers a `GET /api/health-metrics/metadata` request every time. | Cache metadata in Pinia store backed by `sessionStorage` with HTTP `Cache-Control: public, max-age=86400` and `ETag`. | $> 500$ daily active users navigating data input views. |
| **Unindexed Case-Insensitive SQL Queries** | Using `LOWER(metric_key) = LOWER(?)` or `TRIM(metric_key)` in SQL queries to handle dirty casing data. | Normalizes keys in application memory *before* issuing SQL. All database queries must query canonical keys directly against existing index `idx_user_metric_date`. | When `health_metric` table exceeds $100,000$ rows (full table scan latency). |
| **Synchronous AI Range Validation on Typing** | Invoking an LLM to evaluate whether a user's input value is reasonable while they type. | Use local deterministic mathematical rule checks in JavaScript/Java; never invoke an LLM for simple range or boundary validation. | Immediate failure ($1–3\text{ second}$ latency per keystroke; API quota exhaustion). |

---

## Security Mistakes

Domain-specific security issues in health data ingestion and validation.

| Mistake | Risk | Prevention |
|---------|------|------------|
| **Client-Supplied `userId` in `HealthMetricRequest` Body** | A malicious or compromised client submits health metrics with another user's `userId` in the JSON payload, corrupting someone else's medical history. | In `HealthMetricServiceImpl.java`, ignore or overwrite `request.setUserId(SecurityUtils.getCurrentUserId())`. Never trust client-supplied identity. |
| **Regular Expression Denial of Service (ReDoS) in Metric Key Matching** | Using vulnerable nested-quantifier regex patterns to validate custom metric keys or OCR strings (e.g., `^([a-zA-Z0-9]+-?)+$`). | Use simple, linear character set checks: `^[a-zA-Z0-9_\-]{1,32}$`, or check against pre-compiled static `Set<String>` sets. |
| **Unsanitized Unit String Injection (Stored XSS)** | Allowing free-form user input in the `unit` field of manual metric entry (e.g., `<script>alert(1)</script>`). When rendered in the doctor/admin dashboard or trend tooltip, the script executes. | Restrict `unit` field to a strict whitelist or regex `^[a-zA-Z0-9°℃/%\^μ ]{1,16}$`; ensure Vue template interpolation escapes HTML (`{{ row.unit }}`). |
| **Unchecked Reflection / Dynamic Enum Deserialization** | Using `Enum.valueOf(request.getKey())` or reflection to instantiate metric handlers dynamically based on untrusted client strings. | Use a defensive lookup method returning `Optional<MetricKey>` with a fallback to `UNKNOWN` or throwing a sanitized `BusinessException`. |

---

## UX Pitfalls

Common user experience mistakes when dealing with health measurements.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| **The "Angry Red Wall" on Mild Outliers** | When a user types a fasting glucose of 6.3 mmol/L (upper reference is 6.1), the field turns blood red with an exclamation mark, alarming the user that something is catastrophically wrong. | Reserve red error styling exclusively for Tier 1 hard input errors (e.g., impossible numbers). Use gentle amber/blue badges for mild clinical variations with reassuring context: "比参考范围上限略高一点点，建议保持清淡饮食并择日复查". |
| **Wiping Input Values on Blur** | Accidental typos (e.g. typing 1200 instead of 120) cause the entire input field to reset to blank on blur, forcing the user to re-type from scratch. | Retain user input intact; display an explanatory inline banner below the field ("数值疑似超出人体生理范围，您是否想输入 120 mmHg？") with a single-click autofix option. |
| **Click Exhaustion on Integer Stepper Controls** | `<el-input-number>` configured with step=1 for water intake (2000 mL) or daily steps (10000). Clicking the stepper button thousands of times is unusable. | Configure sensible `step` and `step-strictly` per metric (e.g. steps: 500, water: 100 mL, glucose: 0.1 mmol/L). Always allow direct free-form numeric keyboard input. |
| **Cryptic Medical Technical Jargon in Error Messages** | Displaying raw database constraint messages or medical terms: `ConstraintViolation: systolicBP exceeds threshold` or `脉压差收敛异常`. | Provide clear, empathetic, three-part messages: 1) What was entered, 2) Expected reference range and standard unit, 3) Actionable recommendation ("收缩压（高压）通常应在 90~140 mmHg 之间，请输入有效数值"). |
| **Exposing Raw English Metric Keys** | Showing `fastingGlucose`, `systolicBP`, or `bodyTemperature` on column headers or mobile cards when translation is missing. | Never allow an unmapped key to fall back to the raw key string without a graceful label and category fallback. |

---

## "Looks Done But Isn't" Checklist

Things that appear complete in a demo or simple test but fail in real production workflows.

- [ ] **Multi-Format Key Normalization:** Works for manual entry, but verify if `HuaweiHealthService` (uses `spo2`), `OcrService` (uses `white_blood_cell` variants), and `alert_rule` (uses `temperature`) all normalize to the same canonical key.
- [ ] **Severe Outlier Ingestion:** Try entering an acute hypertensive crisis reading ($\text{SBP} = 210\text{ mmHg}, \text{DBP} = 125\text{ mmHg}$). Verify it is NOT blocked by a 400 Bad Request, but is stored properly and dispatches an emergency alert.
- [ ] **Cross-Unit Ingestion:** Submit glucose with value `108` and unit `mg/dL`. Verify the database stores `6.00` with unit `mmol/L` instead of `108.00`, and that the health score is unaffected by unit confusion.
- [ ] **Blood Pressure Inversion Check:** Attempt to submit SBP = 80 and DBP = 120. Verify that validation blocks the submission and explains that systolic must exceed diastolic.
- [ ] **Partial Failure in IoT Batch Sync:** Send a mock batch of 50 records containing 1 corrupted reading (heart rate = -5 bpm). Verify that 49 records are saved, the corrupted record is quarantined, and the HTTP response returns a clean summary without crashing.
- [ ] **Input Stability on Mobile:** Test typing numbers on mobile touchscreens. Verify inputs do NOT clear on blur, do not clamp intermediate keystrokes, and soft feedback messages do not cause jarring page layout shifts.
- [ ] **Historical Data Backfill:** Query historical records in database. Verify that pre-existing records with legacy keys (`temperature`, `spo2`) are migrated or seamlessly mapped so trend graphs and dimension scores render uninterrupted.

---

## Recovery Strategies

When pitfalls occur in production despite prevention, how to recover quickly and safely.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| **Corrupted Metric Keys in Database (`spo2`, `temperature`, `heart_rate`)** | LOW | 1. Execute an idempotent SQL migration script: `UPDATE health_metric SET metric_key = 'bodyTemperature' WHERE metric_key = 'temperature'; UPDATE health_metric SET metric_key = 'bloodOxygen' WHERE metric_key = 'spo2';`<br>2. Clear Redis cache keys `metrics:latest:*` and `health:score:*`.<br>3. Verify trend charts reload properly. |
| **Unit Confusion Ingestion (e.g. Glucose stored as mg/dL numbers with mmol/L unit)** | MEDIUM | 1. Identify affected rows: `SELECT * FROM health_metric WHERE metric_key IN ('glucose', 'fastingGlucose') AND value > 35;`<br>2. Convert affected values: `UPDATE health_metric SET value = ROUND(value / 18.0182, 2) WHERE metric_key IN ('glucose', 'fastingGlucose') AND value > 35;`<br>3. Evict Redis score caches and trigger score recalculation for affected users. |
| **False-Emergency Alert Flood Triggered by Strict Threshold Misconfiguration** | MEDIUM | 1. Adjust Tier 2 threshold values in `alert_rule` table.<br>2. Clear duplicate alerts from `health_alert` table where created within the flood window.<br>3. Temporarily increase alert deduplication window in `AlertRuleEngineImpl.java` to prevent notification spam. |
| **IoT Sync Complete Blockage Caused by Overly Strict Ingestion Validation** | LOW | 1. Flip Spring application configuration `hhs.validation.mode: PERMISSIVE` to immediately downgrade hard rejections into quarantine logging.<br>2. Deploy hotfix without database downtime.<br>3. Reprocess quarantined records from `metric_quarantine_log`. |

---

## Pitfall-to-Phase Mapping

How roadmap phases in Milestone v1.1 should proactively address and verify prevention for each pitfall.

| Pitfall | Prevention Phase | Verification Strategy |
|---------|------------------|-----------------------|
| **Pitfall 1: Hard-Rejecting Clinical Emergencies** | Phase 1: Unified Metric Dictionary & Range Architecture | Automated integration tests submitting crisis values (e.g., SBP 210 mmHg); verify record is persisted and high-priority alert event is dispatched. |
| **Pitfall 2: Cross-Channel Unit Mismatch** | Phase 1 & Phase 2: Ingestion Normalizer & Adapters | Unit tests verifying that input `108 mg/dL` glucose produces stored `6.00 mmol/L`; verify `HuaweiHealthServiceImpl` mock data produces correct canonical units. |
| **Pitfall 3: Multi-Format Key Drift** | Phase 2: Key Normalization Engine & Database Backfill | Test suite passing camelCase, snake_case, and aliases through `MetricKeyNormalizer`; verify database migration script normalizes legacy `alert_rule` and `health_metric` records. |
| **Pitfall 4: Destructive Client-Side Blur Clearing** | Phase 3: Frontend Input Guidance & Smart Feedback | Frontend E2E / Vitest tests simulating blur on invalid values; verify input value remains in form and guidance banner appears without clearing text. |
| **Pitfall 5: Broken Blood Pressure Atomicity** | Phase 1 & Phase 2: Composite Metric Rules & Atomic Batch API | Test cross-validation blocking SBP $\le$ DBP; verify composite endpoint saves both or rollbacks on failure. |
| **Pitfall 6: IoT & AI Ingestion Pipeline Meltdown** | Phase 2: Resilient Batch Ingestion & Quarantining | Send batch with 1 invalid reading; assert 207 Multi-Status or partial success VO returning `saved: 49, quarantined: 1`. |
| **Pitfall 7: Split-Brain Hardcoded Range Constants** | Phase 1: Single Source of Truth Metadata API | Validate that frontend fetches `/api/health-metrics/metadata` and populates `useMetricMetaStore`; confirm zero hardcoded range maps remain in Vue components. |

---

## Sources

- **Internal HHS Codebase Audit:** Verified across `MetricCategoryService.java`, `MetricDisplayFormatter.java`, `AiParseServiceImpl.java`, `OcrServiceImpl.java`, `HuaweiHealthServiceImpl.java`, `MetricsScorer.java`, `DimensionScoreCalculator.java`, `QuickInputDialog.vue`, `Metrics.vue`, and `format.ts`.
- **Chinese Clinical Guidelines for Hypertension (2023 Revision):** Blood pressure classification, crisis boundaries, and pulse pressure definitions.
- **Chinese Guidelines for the Prevention and Control of Type 2 Diabetes (2020 Edition):** Fasting and postprandial glucose reference standards and conversion factor ($1\text{ mmol/L} = 18.0182\text{ mg/dL}$).
- **Jakarta Bean Validation Specification & Hibernate Validator 8.0.1.Final Documentation:** Custom class-level constraint validators, payload validation, and partial validation patterns.
- **Element Plus Form Validation Lifecycle Documentation:** `async-validator` integration and non-destructive reactive error rendering.

---
*Pitfalls research for: Intelligent Health Metric Input, Unified Range Validation & Pre-guidance System (HHS v1.1)*  
*Researched: 2026-11-04*
