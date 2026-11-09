---
phase: 13-profile-guidance
plan: 02
subsystem: backend-api
tags: [completeness, profile, endpoint, asvs-v4, mvc-slice]
requires:
  - phase: 13-profile-guidance
    provides: "ProfileCompletenessCalculator, ProfileFieldCatalog, ProfileCompletenessVO, ProfileFieldStatusVO (13-01)"
  - phase: prevention-module
    provides: "PreventionController /api/prevention mapping, SecurityUtils.getCurrentUserId, TestSecurityConfig slice"
provides:
  - "com.hhs.service.HealthProfileService#getCompleteness(Long)"
  - "com.hhs.service.impl.HealthProfileServiceImpl#getCompleteness(Long)"
  - "GET /api/prevention/profile/completeness"
  - "com.hhs.controller.PreventionController#getProfileCompleteness()"
  - "com.hhs.controller.ProfileCompletenessControllerTest (wire-shape pin)"
affects:
  - "13-05 (frontend store calls this endpoint with { silent: true }; its 404 branch becomes a defensive fallback)"
  - "13-06 (profile page header consumes score/level/filledCount/totalCount)"
  - "13-07 (reminder banner reads score < 60 and the unfilled required fields)"
  - "13-08 (risk badge reads assumptions + hasProfile)"
  - "13-09 (backfill panel renders fields[] with per-field reason/assumption)"
tech-stack:
  added: []
  patterns:
    - "Service method delegates the null case to the domain calculator rather than catching or hand-building a VO"
    - "MVC slice test stubs the service with the real calculator output so the wire fixture cannot drift from production semantics"
    - "Absent resource represented as 200 + hasProfile:false instead of 404"
key-files:
  created:
    - hhs-backend/src/test/java/com/hhs/controller/ProfileCompletenessControllerTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/service/HealthProfileService.java
    - hhs-backend/src/main/java/com/hhs/service/impl/HealthProfileServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/controller/PreventionController.java
    - hhs-backend/src/test/java/com/hhs/service/HealthProfileServiceTest.java
key-decisions:
  - "getCompleteness reuses the exact LambdaQueryWrapper shape of getByUserId (eq userId + selectOne) and does NOT add an orderByDesc(createTime).last(\"LIMIT 1\") variant: create() already rejects a second row per user, so at most one row matches and one wrapper shape keeps header and score agreeing by construction"
  - "The no-profile case is a 200 with score 0 and hasProfile false, never 404: a 404 would hide the guidance from the user who most needs it, and the frontend store's 404 branch is left as a defensive fallback only"
  - "The controller handler takes no userId parameter; the only identity source is SecurityUtils.getCurrentUserId(), asserted by grep so a future edit cannot reintroduce an IDOR shape (ASVS V4)"
  - "The MVC slice stubs getCompleteness with new ProfileCompletenessCalculator(new ProfileFieldCatalog()).calculate(null) rather than a hand-built 7-component record, so any future record or catalog change breaks the test instead of pinning a stale fixture"
  - "totalCount is asserted against the serialized fields array size (JSON read from MvcResult), not against a Java-side constant, so the assertion covers the wire contract the frontend actually consumes"
patterns-established:
  - "Additive service method on an existing service rather than a parallel CompletenessService: the calculator stays the single weighting owner and the service stays a thin lookup + delegate"
  - "Controller identity discipline: handler has zero userId input, and the grep acceptance criterion makes the IDOR-free shape mechanically checkable"
  - "Sparse-row vs absent-row are distinguished by hasProfile on the wire, so no consumer infers state from the score alone"
requirements-completed: [PRF-01, PRF-04]
actuals:
  tokens: 4320
  tasks: 2
  commits: 3   # MEASURED: git rev-list --count plan_head_before..HEAD (feat + test + this docs commit)
plan_head_before: b8eb659efcc7e22c0759c1e8627c0c0f2bc508cd
status: complete
---

# Phase 13 Plan 02: 档案完整度 HTTP 端点 Summary

One endpoint, `GET /api/prevention/profile/completeness`, is now the single HTTP source for every profile-guidance surface: it returns the authoritative 7-component payload — score, 三等级 level, filledCount, catalog-derived totalCount, hasProfile, the substitution assumptions, and nine per-field statuses — for both the user with a profile and the user with none, and it never 404s.

## What Was Built

**`HealthProfileService.getCompleteness(Long userId)`** — additive interface method with Javadoc stating it never returns `null` and that a missing profile yields 0%. It sits next to `getByUserId` because it answers a different question about the same row.

**`HealthProfileServiceImpl.getCompleteness`** — injects `ProfileCompletenessCalculator` as a second `final` field through the existing `@RequiredArgsConstructor`, is `@Transactional(readOnly = true, timeout = 30)`, and does exactly two things: run the same `LambdaQueryWrapper` (`eq(HealthProfile::getUserId, userId)` → `selectOne`) that `getByUserId` uses, then hand the possibly-null row to `completenessCalculator.calculate(profile)`. There is no try/catch and no fallback VO — the calculator owns the absent-row answer, so exactly one `completenessCalculator.calculate` call exists in the file.

**`GET /api/prevention/profile/completeness`** — `@GetMapping("/profile/completeness")` with `@PreAuthorize("isAuthenticated()")` and the `获取档案完整度` OpenAPI summary, placed among the `/profile` alias endpoints. The handler signature takes **no** path variable and **no** request parameter; the only `userId` reference is the local assigned from `SecurityUtils.getCurrentUserId()`. The path cannot collide with `GET /api/prevention/health-profile/{userId}` because `profile` and `health-profile` are different first segments.

**`ProfileCompletenessControllerTest`** — `@WebMvcTest(PreventionController.class)` + `@Import(TestSecurityConfig.class)` + `@ActiveProfiles("test")`, with `@MockBean` on the three constructor collaborators and a `Mockito.mockStatic(SecurityUtils.class)` returning `1L` (the `PushConfigControllerTest` recipe, since this handler's only identity comes from a static).

| test | pins |
|---|---|
| `noProfileReturnsZeroScoreAndNineFields` | HTTP 200 (not 404); code 200; score 0; level 基础档案; filledCount 0; hasProfile false; assumptions length 1 and exactly `未填写健康档案，风险得分按通用参考值估算`; fields length 9; fields[0] = gender/性别/required/false with a non-empty reason |
| `totalCountEqualsFieldListLength` | `data.totalCount` == serialized `data.fields` length == 9, read from the JSON body |

**`HealthProfileServiceTest`** — `@InjectMocks` was replaced by an explicit `new HealthProfileServiceImpl(healthProfileMapper, new ProfileCompletenessCalculator(new ProfileFieldCatalog()))` in `@BeforeEach`, so the real 60/30/10 weighting runs instead of a stubbed score. Three tests were added: null row (0 / 基础档案 / filledCount 0 / hasProfile false / `List.of(NO_PROFILE_ASSUMPTION)`), gender+birthDate only (60 / 标准档案 / filledCount 2 / hasProfile true), and fully populated including the derived bmi (100 / 完整档案 / filledCount == totalCount / empty assumptions). Each asserts `totalCount == fields().size()`; the literal 9 is not repeated for `totalCount`.

## Verification Results

| Check | Result |
|---|---|
| `mvn -f hhs-backend/pom.xml test -Dtest=ProfileCompletenessControllerTest` | 2 tests, 0 failures |
| `mvn -f hhs-backend/pom.xml test -Dtest=HealthProfileServiceTest` | 15 tests, 0 failures (12 pre-existing + 3 new — the constructor change broke nothing) |
| `mvn -f hhs-backend/pom.xml test -Dtest=ProfileCompletenessControllerTest,HealthProfileServiceTest` | 17 tests, 0 failures |
| `mvn -f hhs-backend/pom.xml test` (full Surefire suite) | **816 tests, 0 failures, BUILD SUCCESS** |
| `mvn -f hhs-backend/pom.xml checkstyle:check` | 0 violations |
| `grep -n "userId" PreventionController.java` | the new handler contributes only `Long userId = SecurityUtils.getCurrentUserId();` + the debug log + the call; no `@PathVariable` / `@RequestParam` |
| `grep -c "completenessCalculator.calculate" HealthProfileServiceImpl.java` | 1 |

## Deviations from Plan

### Scope-boundary notes (no code deviation)

**1. `PreventionIntegrationTest` could not be executed in this environment.**
- **Found during:** plan-level `<verification>`
- **Issue:** `AbstractIntegrationTest.<clinit>` starts Testcontainers MySQL/Redis unconditionally; the container runtime is unreachable from the Maven JVM (`Could not find a valid Docker environment`). Docker CLI itself answers (`docker version` reports server 29.8.1), so this is a Testcontainers-to-daemon discovery failure in this sandbox, not a code defect.
- **Evidence it is pre-existing:** a control run of the untouched `HealthScoreIntegrationTest` fails identically (4 errors, same `ExceptionInInitializerError` root). `DOCKER_HOST` and an unsandboxed run were both attempted and changed nothing.
- **Fix:** none attempted — out of scope per the scope-boundary rule. The endpoint is additive and touches no existing route; the full 816-test Surefire suite (which covers the controller slice) is green.
- **Still to run in a Docker-capable environment:** `mvn -f hhs-backend/pom.xml failsafe:integration-test failsafe:verify -Dit.test=PreventionIntegrationTest`.

**2. The MVC slice mocks fewer beans than the plan's list.** The plan named `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` and `corsConfigurationSource` as `@MockBean`s (`MetricMetadataControllerTest`'s recipe, which imports `SecurityConfig`). With `@Import(TestSecurityConfig.class)` — `PushConfigControllerTest`'s recipe, and the one the plan also pointed at for the `SecurityUtils` static mock — `TestSecurityConfig` supplies its own `SecurityFilterChain` and `application-test.yml` supplies the `test` profile CORS bean, so those four mocks are unnecessary. Declaring them would have shadowed the very chain the slice needs. Net effect on the assertions: none; the request still runs through a real security chain with `@PreAuthorize("isAuthenticated()")` enforced.

## Known Stubs

None. The payload is fully sourced from the catalog and calculator — no hardcoded empty array, no placeholder copy, no unwired field.

## Threat Flags

None beyond the plan's register. T-13-05 (IDOR) is mitigated and mechanically checkable via the grep criterion above; T-13-06 (`@PreAuthorize`) is exercised through a real filter chain, not a permit-all slice; T-13-07 is satisfied because the payload carries no userId, no profile id and no exception text; T-13-08 stays accepted. No new network, file-access or schema surface.

## Coverage of Must-Have Truths

| Must-have truth | Verification ref | Status |
|---|---|---|
| No profile → 200 with score 0, level 基础档案, filledCount 0, fields length 9 | `ProfileCompletenessControllerTest.noProfileReturnsZeroScoreAndNineFields`, `HealthProfileServiceTest.testGetCompleteness_NoProfileRow` | covered |
| Handler derives the user from `SecurityUtils.getCurrentUserId()`, accepts no userId input (ASVS V4) | `grep -n "userId" PreventionController.java` — new handler has only the local assignment | covered |
| `data.totalCount` always equals `data.fields.length` on the wire | `ProfileCompletenessControllerTest.totalCountEqualsFieldListLength` (JSON read from `MvcResult`); also asserted in all three service tests | covered |
| hasProfile false + exactly one generic assumption for a no-profile caller | `ProfileCompletenessControllerTest.noProfileReturnsZeroScoreAndNineFields` asserts `assumptions.length()==1` and the literal sentence; `.testGetCompleteness_NoProfileRow` asserts `List.of(NO_PROFILE_ASSUMPTION)` | covered |
| hasProfile true for an existing but sparse row | `HealthProfileServiceTest.testGetCompleteness_MinimumViableProfile` | covered |
| Endpoint never returns 404 for a missing profile | `ProfileCompletenessControllerTest.noProfileReturnsZeroScoreAndNineFields` (`status().isOk()`) — fails with `Status expected:<200> but was:<404>` otherwise | covered |
| `getCompleteness` is the only service entry point and delegates to the calculator; no second weighting | `grep -c completenessCalculator.calculate` == 1; the class has no other scoring expression | covered |
| gender + birthDate only → score 60 (feeds the banner's <60 trigger) | `HealthProfileServiceTest.testGetCompleteness_MinimumViableProfile` | covered |
| Pre-existing `HealthProfileServiceTest` cases survive the constructor change | full class run: 15 tests, 0 failures | covered |
| Pre-existing prevention routes unperturbed | full Surefire suite 816 green; `PreventionIntegrationTest` deferred (see Deviations 1) | partial — deferred |

## Self-Check: PASSED

- FOUND: hhs-backend/src/main/java/com/hhs/service/HealthProfileService.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/impl/HealthProfileServiceImpl.java
- FOUND: hhs-backend/src/main/java/com/hhs/controller/PreventionController.java
- FOUND: hhs-backend/src/test/java/com/hhs/controller/ProfileCompletenessControllerTest.java
- FOUND: hhs-backend/src/test/java/com/hhs/service/HealthProfileServiceTest.java
- FOUND: bd4eab7 (feat 13-02 endpoint + slice), 9300541 (test 13-02 service contract)