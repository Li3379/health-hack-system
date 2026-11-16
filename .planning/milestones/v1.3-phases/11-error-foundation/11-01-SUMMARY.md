---
phase: 11-error-foundation
plan: 01
subsystem: api
tags: [spring-boot, bean-validation, error-envelope, field-errors, global-exception-handler, chinese-messages]

# Dependency graph
requires: []
provides:
  - "Structured `fieldErrors[]` payload (field / message / code) for every Bean Validation failure path"
  - "`Result.errorCode` string field coexisting with the unchanged numeric `code`"
  - "Legacy `Result.errors[]` compatibility array (one release)"
  - "Class-level violations project under the `_global` pseudo-field; property-bound class-level violations land in the normal field list"
  - "`fieldErrors` / `errors` capped at 20 entries per response"
  - "Chinese-only user-facing fallbacks across every handler branch"
  - "`SYSTEM_*` error codes always render as 服务暂时不可用，请稍后重试 (never the enum's banned wording)"
affects: [11-02, 11-03, 11-04, 11-05, 11-06, 11-07, phase-14]

# Actuals (#2632) — same scale as the plan estimate (chars/4 over the realized diff).
actuals:
  tokens: 8400
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Per-field @JsonInclude(NON_EMPTY) on optional envelope diagnostics (never class-level, which would drop `data: null` from success responses)"
    - "Explicit 3-arg and 6-arg Result constructors instead of @AllArgsConstructor"
    - "Single `systemFallbackMessage(ErrorCode, String)` choke point for the SYSTEM_* message rule"
    - "Detail sanitization at the throw site, never by regex filtering in the handler"

key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java
    - hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/common/Result.java
    - hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java
    - hhs-backend/src/main/java/com/hhs/service/impl/AIChatServiceImpl.java
    - hhs-backend/src/test/java/com/hhs/integration/RealtimeIntegrationTest.java

key-decisions:
  - "Result keeps `@Data`/`@NoArgsConstructor` and gains explicit 3-arg + 6-arg constructors; @AllArgsConstructor was removed because the new fields would have silently broken existing `new Result<>(200, \"success\", null)` call sites"
  - "`code` numeric semantics preserved verbatim (parseCodeToInt untouched); business failures still fall back to 500 so PushConfigControllerTest and MetricMetadataControllerTest keep passing"
  - "Both getFieldErrors() and getGlobalErrors() are consumed: MetricRecordValidator's addPropertyNode(\"value\") surfaces as a FieldError, while path-less class-level violations only appear in getGlobalErrors()"
  - "`extractField` degrades to `_global` on IllegalArgumentException, because ObjectError.unwrap throws rather than returning null when no ConstraintViolation source exists"
  - "SYSTEM_* codes are replaced unconditionally by a fixed Chinese message — not conditionally on how generic the enum text looks — because the enum's own wording is on the UI-SPEC banned list and travels back through HTTP 200 business envelopes rendered verbatim by the client"
  - "AIChatServiceImpl drops the `\": \" + e.getMessage()` splice so internal exception text never becomes user-visible detail"

requirements-completed: [ERR-01, ERR-02]

coverage:
  - id: D1
    description: "Validation failures return HTTP 400 with code=400, errorCode=validation.invalid_parameter, structured fieldErrors (field/message/code), and the legacy errors array"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#methodArgumentNotValid_projectsFieldAndGlobalErrors"
        status: pass
    human_judgment: false
  - id: D2
    description: "Class-level violations (no property path) are reported under `_global` alongside ordinary field violations"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#methodArgumentNotValid_projectsFieldAndGlobalErrors (asserts fields are exactly metricKey, _global)"
        status: pass
    human_judgment: false
  - id: D3
    description: "fieldErrors are capped at 20 entries per response (DoS mitigation T-11-02)"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#methodArgumentNotValid_capsFieldErrors"
        status: pass
    human_judgment: false
  - id: D4
    description: "Business failures expose errorCode while keeping the legacy numeric code=500 fallback (no existing controller assertion broken)"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#handleBusinessException_keepsNumericCodeAndExposesErrorCode"
        status: pass
      - kind: unit
        ref: "mvn -f hhs-backend/pom.xml test -Dtest=PushConfigControllerTest,MetricMetadataControllerTest"
        status: pass
    human_judgment: false
  - id: D5
    description: "SYSTEM_* codes (error / database / internal) always render 服务暂时不可用，请稍后重试 on both the SystemException and BusinessException channels"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#handleSystemException_coversEverySystemCode"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#handleBusinessException_replacesSystemCodes"
        status: pass
    human_judgment: false
  - id: D6
    description: "Internal exception details (db down, Token encryption failed) appear in logs only, never in the response body"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#handleSystemException_hidesInternalDetail + #handleException_hidesInternalDetail"
        status: pass
    human_judgment: false
  - id: D7
    description: "Auth / security / malformed-body / 404 / 500 handlers all return Chinese messages; non-SYSTEM codes keep their enum text (AUTH_FAILED -> 认证失败)"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java#fallbackChannels_areAllChinese + #handleSystemException_keepsEnumMessageForNonSystemCodes"
        status: pass
    human_judgment: false

# Metrics
duration: 17min
completed: 2026-11-06
status: complete
---

# Phase 11 Plan 01: Error Envelope Foundation Summary

Unified failure payload: structured `fieldErrors`, a coexisting `errorCode`, and fully Chinese handler fallbacks with server details confined to logs.

## Performance

- **Duration:** 17 min
- **Started:** 2026-11-06T06:45:00Z
- **Completed:** 2026-11-06T07:02:00Z
- **Tasks:** 2/2
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- Every Bean Validation failure path now returns a structured envelope instead of a free-form `Map`: `code`, `message`, optional `errorCode`, `fieldErrors[]` (each `{field, message, code}`) and the legacy `errors[]`, with both lists capped at 20.
- Class-level constraints are no longer silently dropped — `getFieldErrors()` and `getGlobalErrors()` are both consumed, so a path-less violation (e.g. `@ScriptAssert`) lands under `_global` while `MetricRecordValidator`'s `addPropertyNode("value")` violation lands in the normal field list.
- Business / system / auth / security / parse / 404 / 500 branches all emit Chinese user-facing text, and `SYSTEM_*` codes are unconditionally rewritten to the fixed friendly message on **both** the `SystemException` and `BusinessException` channels.
- Internal exception detail is contained: `handleSystemException` and the 500 fallback route `ex.getMessage()` to `log.error` only, and `AIChatServiceImpl` no longer splices the runtime error text into `BusinessException` detail.
- Existing numeric `code` semantics are untouched — `parseCodeToInt` is byte-identical, so `PushConfigControllerTest` (11 assertions on `code`) and `MetricMetadataControllerTest` still pass.

## Task Commits

Each task was committed atomically:

1. **Task 1: 字段错误信封端到端 —— 校验异常 → fieldErrors 数组** - `fb2023b` (feat)
2. **Task 2: 业务/系统/认证分支补 errorCode 与中文兜底文案** - `9cebd57` (feat)

**Plan metadata:** `docs(11-01): complete error envelope foundation plan` (this SUMMARY)

## Files Created/Modified

- `hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java` (new) — `record FieldErrorVO(String field, String message, String code)`, the field-level payload replacing `"field: msg"` strings.
- `hhs-backend/src/main/java/com/hhs/common/Result.java` — adds `errorCode` / `fieldErrors` / `errors` (each `@JsonInclude(NON_EMPTY)`), `failure(int, String, String)`, `invalidInput(String, List<FieldErrorVO>)`, and `withErrorCode` / `withFieldErrors` / `withErrors`; explicit 3-arg and 6-arg constructors replace `@AllArgsConstructor`.
- `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` — three validation branches now return `ResponseEntity<Result<Void>>` and project field + global errors; business/system/auth/security/parse/404/500 branches carry `errorCode` and Chinese text; `systemFallbackMessage` and `extractField` added.
- `hhs-backend/src/main/java/com/hhs/service/impl/AIChatServiceImpl.java` — `BusinessException(AI_ERROR, "AI服务暂时不可用")`; the exception object goes to `log.error` only.
- `hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java` (new) — 9 direct-handler tests, no Spring context or Docker.
- `hhs-backend/src/test/java/com/hhs/integration/RealtimeIntegrationTest.java` — `$.code` assertion becomes `400` and `$.fieldErrors` array presence is asserted.

## Decisions Made

- **`fieldErrors`/`errors` both capped via `.limit(20)`** — mitigates T-11-02 (oversized response from a hostile body) at the projection point rather than the validation point.
- **`_global` pseudo-field instead of a separate `globalErrors` array** — keeps one client contract; the frontend maps `_global` to the form-level summary alert.
- **`extractField` catches `IllegalArgumentException`** — `ObjectError.unwrap` throws (does not return null) when the error is not conversion-backed; catching is the only way to reach the `_global` fallback for plain `ObjectError` instances.
- **SYSTEM_* rule lives in exactly one method** — `systemFallbackMessage(ErrorCode, String)` so the "SYSTEM_* → fixed text" invariant has a single testable落点 and cannot drift between the two channels.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `ObjectError.unwrap` throws instead of returning null**
- **Found during:** Task 1, first test run (`IllegalArgument No source object of the given type available: interface jakarta.validation.ConstraintViolation`)
- **Issue:** The plan's `extractField` spec assumed `ge.unwrap(ConstraintViolation.class)` returns `null` when no source is available; Spring's implementation throws `IllegalArgumentException`, which would escape the handler as a 500.
- **Fix:** Wrapped the unwrap in a try/catch that returns `_global`, then re-checked for a null result and no property-path node.
- **Files modified:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java`
- **Verification:** `GlobalExceptionHandlerTest#methodArgumentNotValid_projectsFieldAndGlobalErrors` passes with fields `[metricKey, _global]`.
- **Committed in:** `fb2023b`

**2. [Rule 1 - Bug] Generic inference broke `Result<Void>` returns in the business/system branches**
- **Found during:** Task 2, first compile after adding `.withErrorCode(...)`
- **Issue:** `Result.failure(String, String)` and `Result.failure(int, String)` infer `Result<Object>` at the call site once the chain is returned directly, producing "Result<Object> cannot be converted to Result<Void>".
- **Fix:** Used the explicit type witness `Result.<Void>failure(...)` (and bound to a local `Result<Void>` in the business branch so the acceptance grep for the legacy `Result.failure(ec.getCode(), message)` call site stays exact).
- **Files modified:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java`
- **Verification:** `mvn -f hhs-backend/pom.xml test -Dtest=GlobalExceptionHandlerTest,PushConfigControllerTest,MetricMetadataControllerTest` → 22/22 pass.
- **Committed in:** `9cebd57`

**3. [Rule 3 - Blocking] Test-side type references and constructor arity**
- **Found during:** Task 2, test compile
- **Issue:** The `com.hhs.exception` package contains its own `AuthenticationCredentialsNotFoundException`, shadowing the Spring Security one; `HttpMessageNotReadableException` was ambiguous between the two-arg constructor overloads; and `NoResourceFoundException` in this Spring version takes `(HttpMethod, String)`.
- **Fix:** Fully-qualified the Spring type, added an explicit `(HttpInputMessage)` cast to disambiguate, and dropped the third constructor argument.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java`
- **Verification:** Same 22/22 run.
- **Committed in:** `9cebd57`

**4. [Rule 3 - Blocking] Missing final newline and checkstyle literal gates**
- **Found during:** Task 1/2 acceptance greps
- **Issue:** `Result.java` was written without a trailing newline (`.editorconfig` requires `insert_final_newline = true`), and the first draft of Task 2 had two literal gate failures — the banned Chinese words appeared in a Javadoc paragraph, and `Result.failure(ec.getCode(), message)` had been inline-chained out of existence.
- **Fix:** Appended the final newline; reworded the Javadoc to describe the ban without quoting the words; bound the failure envelope to a local before chaining `withErrorCode`.
- **Files modified:** `Result.java`, `GlobalExceptionHandler.java`
- **Verification:** `grep -cE '系统错误|数据库错误|内部错误' → 0`, `grep -c 'Result.failure(ec.getCode(), message)' → 1`, `grep -c '<100-char English fallbacks>' → 0`.
- **Committed in:** `fb2023b`, `9cebd57`

---

**Total deviations:** 4 auto-fixed (2 × Rule 1, 2 × Rule 3)
**Impact on plan:** All fixes were required for correctness or to make the specified acceptance gates measurable. No architectural change, no scope creep, no new dependencies.

## Issues Encountered

- The Unicode output of Maven/Checkstyle on this Windows shell is mojibake (`?`-replaced CJK), so checkstyle warnings were read from the exit code and the `0 Checkstyle violations` summary line rather than the message text. The build reported 0 violations in every run.
- `grep` gates that assert on Chinese literals are byte-sensitive; verifying them required checking the source file directly rather than relying on console echo.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The envelope contract downstream plans depend on is live and test-locked: `code`, `message`, `data`, `errorCode`, `fieldErrors[].field|message|code`, `errors[]`.
- Wave-2 plans (frontend `normalizeApiError` / `useFormServerErrors`, DTO annotation Chinese cleanup in 11-02) can now bind to stable field names.
- Known follow-ups intentionally left to other plans: `ScreeningServiceImpl:110` still splices the runtime error text (owned by 11-02), and `RealtimeIntegrationTest` remains an integration test that requires Docker in CI (not run in this plan).

## Self-Check: PASSED

- `hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java` — FOUND
- `hhs-backend/src/main/java/com/hhs/common/Result.java` — FOUND
- `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` — FOUND
- `hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java` — FOUND
- Commit `fb2023b` — FOUND (ancestor of HEAD)
- Commit `9cebd57` — FOUND (ancestor of HEAD)

---
*Phase: 11-error-foundation*
*Completed: 2026-11-06*