---
phase: 13-profile-guidance
verified: 2026-11-10T00:40:00Z
status: passed
score: 12/12 must-haves verified
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/13-profile-guidance/13-01-PLAN.md
  - .planning/phases/13-profile-guidance/13-01-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-02-PLAN.md
  - .planning/phases/13-profile-guidance/13-02-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-03-PLAN.md
  - .planning/phases/13-profile-guidance/13-03-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-04-PLAN.md
  - .planning/phases/13-profile-guidance/13-04-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-05-PLAN.md
  - .planning/phases/13-profile-guidance/13-05-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-06-PLAN.md
  - .planning/phases/13-profile-guidance/13-06-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-07-PLAN.md
  - .planning/phases/13-profile-guidance/13-07-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-08-PLAN.md
  - .planning/phases/13-profile-guidance/13-08-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-09-PLAN.md
  - .planning/phases/13-profile-guidance/13-09-SUMMARY.md
  - .planning/phases/13-profile-guidance/13-10-PLAN.md
  - .planning/phases/13-profile-guidance/13-10-SUMMARY.md
  - hhs-backend/src/main/java/com/hhs/controller/PreventionController.java
  - hhs-backend/src/main/java/com/hhs/service/HealthProfileService.java
  - hhs-backend/src/main/java/com/hhs/service/domain/AssumptionCollector.java
  - hhs-backend/src/main/java/com/hhs/service/domain/DimensionScoreCalculator.java
  - hhs-backend/src/main/java/com/hhs/service/domain/MetricsScorer.java
  - hhs-backend/src/main/java/com/hhs/service/domain/ProfileCompletenessCalculator.java
  - hhs-backend/src/main/java/com/hhs/service/domain/ProfileFieldCatalog.java
  - hhs-backend/src/main/java/com/hhs/service/domain/ProfileScorer.java
  - hhs-backend/src/main/java/com/hhs/service/domain/ScoreCalculator.java
  - hhs-backend/src/main/java/com/hhs/service/impl/HealthProfileServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/HealthScoreServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/vo/HealthScoreVO.java
  - hhs-backend/src/main/java/com/hhs/vo/ProfileCompletenessVO.java
  - hhs-backend/src/main/java/com/hhs/vo/ProfileFieldStatusVO.java
  - hhs-backend/src/main/java/com/hhs/vo/RiskAssessmentVO.java
  - hhs-backend/src/test/java/com/hhs/controller/ProfileCompletenessControllerTest.java
  - hhs-backend/src/test/java/com/hhs/service/HealthProfileServiceTest.java
  - hhs-backend/src/test/java/com/hhs/service/HealthScoreServiceTest.java
  - hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java
  - hhs-backend/src/test/java/com/hhs/service/domain/ProfileCompletenessCalculatorTest.java
  - hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java
  - hhs-backend/src/test/java/com/hhs/service/domain/ScoreCalculatorAssumptionsTest.java
  - hhs-frontend-v2/src/__tests__/components/EstimateBadge.spec.ts
  - hhs-frontend-v2/src/__tests__/components/ProfileCompleteness.spec.ts
  - hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts
  - hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts
  - hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts
  - hhs-frontend-v2/src/__tests__/views/ProfileCompleteness.spec.ts
  - hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts
  - hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts
  - hhs-frontend-v2/src/__tests__/views/ScoreEstimateBadge.spec.ts
  - hhs-frontend-v2/src/api/prevention.ts
  - hhs-frontend-v2/src/assets/styles/theme.css
  - hhs-frontend-v2/src/components/common/EstimateBadge.vue
  - hhs-frontend-v2/src/components/common/ProfileCompleteness.vue
  - hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue
  - hhs-frontend-v2/src/stores/profileCompleteness.ts
  - hhs-frontend-v2/src/types/api.ts
  - hhs-frontend-v2/src/utils/profileFields.ts
  - hhs-frontend-v2/src/views/dashboard/Index.vue
  - hhs-frontend-v2/src/views/health/Score.vue
  - hhs-frontend-v2/src/views/prevention/Profile.vue
  - hhs-frontend-v2/src/views/prevention/Risk.vue
covered_digest: "v1:sha256:cfb34cfe319032ffedf518f573bac77c36cd85f4918a12226eb14d435d6004cc"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Open /prevention/profile at a 320px viewport width and read a field's explanation line (e.g. 过敏史 hint)."
    expected: "The hint wraps onto its own line under the control; a 24-character explanation is never clipped, and no `overflow:hidden` truncation occurs."
    why_human: "Text wrapping and CSS clip behaviour cannot be measured in happy-dom; WINDOWS.md #6 records this as an unrun visual backstop."
  - test: "Narrow the profile page below 639px and confirm the form labels stack above their controls."
    expected: "`label-position` flips to `top` via matchMedia; el-form-item display is unchanged (never forced to block)."
    why_human: "The 639px media-query flip is a rendered-layout effect; jsdom/happy-dom does not evaluate media queries."
  - test: "Render the estimate badge on the health-score card at a 320px viewport."
    expected: "The fixed 14-character label (`基于不完整档案估算 · 完善档案`) does not wrap and the control stays a single 32px-min-height button."
    why_human: "WINDOWS.md #4 backstop; intrinsic label width at 320px is a visual measurement."
  - test: "Render the dashboard reminder banner at 640px and 768px with a long missing-field list."
    expected: "`完善档案`, `稍后提醒` and the close control do not collide; the action area wraps to a full-width row with the 44px inset below 768px."
    why_human: "WINDOWS.md #5 backstop; button collision is a visual/raster check."
  - test: "Run PreventionIntegrationTest / HealthScoreIntegrationTest against a live Docker daemon."
    expected: "Both integration suites execute and pass rather than being skipped."
    why_human: "WINDOWS.md #3 records that Testcontainers cannot reach a Docker daemon in this environment; pre-existing and not introduced by this phase."
---

# Phase 13: 健康档案完善引导（非阻塞）Verification Report

**Phase Goal:** 让健康档案的缺失从"静默代入默认值"变为"持续可见、可理解、可关闭、绝不阻断"的引导：展示完整度与逐字段状态、解释每个字段为何需要、披露后端实际采用的默认假设，并在下游结果页标注估算来源
**Verified:** 2026-11-09T14:47:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Gates re-run by the verifier

| Gate | Command | Result |
|------|---------|--------|
| Backend full verify | `mvn clean verify -DskipITs` | **845 tests, 0 failures, BUILD SUCCESS**, jacoco check met |
| Backend targeted assumption suites | `-Dtest=ProfileCompletenessCalculatorTest,ProfileFieldCatalogTest,ScoreCalculatorAssumptionsTest,RiskAssessmentAssumptionsTest,ProfileCompletenessControllerTest,HealthProfileServiceTest,HealthScoreServiceTest` | 22 / 11 / 10 / 17 / 2 / 15 / 14 — all 0 failures |
| Frontend unit suite | `npm run test:run` | **445 tests / 37 files passed** |
| Frontend phase-13 specs only | `npx vitest run` on the 9 phase-13 files | 185 tests passed |
| Frontend build | `npm run build` | green, `✓ built in 9.45s` |
| Frontend lint (phase-13 files) | `npx eslint <9 touched files>` | **0 errors**, 13 pre-existing warnings (`no-explicit-any` / unused `error`) |
| Decision coverage | `query check.decision-coverage-verify` | 8/8 CONTEXT decisions honored, `blocking: false` |
| Artifacts (all 10 plans) | `query verify.artifacts` | 4/4, 4/4, 3/3, 3/3, 4/4, 4/4, 2/2, 2/2, 2/2, 2/2 — `all_passed: true` |
| Disabled-test scan | grep over all 9 phase-13 specs + 5 backend test classes | none |
| Debt markers | grep `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER` over all 44 phase-13 impl files | **none** |
| Runtime identity | `query runtime-identity` | `@opengsd/gsd-core` v1.14.0 |

Baseline from the orchestrator was independently re-measured and matched (845 backend / 445 frontend / green build / 0 lint errors).

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 健康档案页与仪表盘持续展示完整度分数、等级与逐字段已填/未填 (SC1, PRF-01) | ✓ VERIFIED | `Profile.vue:17` mounts `ProfileCompleteness variant="full"`; `dashboard/Index.vue:113-118` mounts `variant="compact"`; the meter renders one segment per `completeness.fields` entry (`ProfileCompleteness.vue:95-102`), the count comes from the payload (`:91`), and each legend row carries ✓/○ plus a visually-hidden 已填/未填 (`:110-121`). Asserted by `ProfileCompleteness.spec.ts:69-127`. |
| 2 | 每个字段下方有与实际消费方一致的"为何需要"说明，标注必需/推荐/可选，并明确推荐与可选不填也可保存 (SC1, PRF-02/03) | ✓ VERIFIED | Exactly 9 `.field-hint` blocks in `Profile.vue` (grep count 9, spec asserts `toHaveLength(9)`); copy comes from `hintFor(key) = fieldMeta(key)?.reason` (`:322`) — never a local constant; badges map `hhs-imp-badge--{importance}` (`:326-330`); the promise sentence appears exactly once (`:20`, grep count 1). Reasons name real consumers in `ProfileFieldCatalog.java:51-72`: gender→健康报告/AI, birthDate→年龄/风险, height/weight→生成 BMI, bmi→风险评估/22.0, familyHistory→糖尿病风险评估. No reason contains 体重维度 (`ProfileFieldCatalogTest.noReasonClaimsTheWeightDimension`). |
| 3 | 缺失时展示点名下游后果的影响横幅 (SC2, PRF-04) | ✓ VERIFIED | `ProfileCompleteness.vue:39-77` branches `!hasProfile` → 通用参考值, birthDate missing → 35 岁, gender missing → 报告/AI 通用表述 (never a risk claim), recommended missing → splits on whether the payload's `assumptions` carry the bmi sentence (22.0 vs 沿用已记录), optional-only → 个性化降低, none → 完整. `ProfileReminderBanner.vue:67-71` names the missing labels in catalog order plus 健康评分与风险结果会按参考值估算. |
| 4 | 使用默认假设时风险页与评分页展示"基于不完整档案估算 · 完善档案"徽标并可跳转档案页 (SC2, PRF-06) | ✓ VERIFIED | `EstimateBadge.vue:17` gates on `!= null && < 100` (0 renders — spec asserts); `Risk.vue:128-132` places the badge outside the table and hides it while the panel is open; `Score.vue:59-62` places it inside `.score-info` between `.score-tips` and `.score-meta`; both call `goProfileWithFocus` → `router.push({path:'/prevention/profile', query: key ? {focus:key} : {}})`. |
| 5 | 后端风险评估与健康评分响应实际披露 assumptions[] / profileCompleteness (SC3, PRF-05) | ✓ VERIFIED | `RiskAssessmentVO` is a 10-component record with `profileCompleteness` + trailing `assumptions` (`vo/RiskAssessmentVO.java:24-35`) and `toVO` passes both (`RiskAssessmentServiceImpl.java:159-163`). `HealthScoreVO:69,78` add the same two fields; `ScoreCalculator.java:128-130` sets them from `ProfileCompletenessCalculator` and `collector.toList()`. The production one-arg `calculate(userId)` delegates with a **live** `AssumptionCollector.noop()` (returns `new AssumptionCollector()`, not a null sentinel), so `HealthScoreServiceImpl.java:68,101` really do emit assumptions. Cache namespace bumped to `health:score:v2:` (`:44`). |
| 6 | 仪表盘完整度低于阈值时展示可关闭提示，提供"完善档案"与"稍后提醒"，频率上限生效——最多一条、关闭后 7 天不再打扰 (SC4, PRF-07) | ✓ VERIFIED | `ProfileReminderBanner` is imported by exactly one view (`dashboard/Index.vue:211`; grep confirms no other view); `show = completeness != null && score < 60 && !dismissed` (`:29-31`); three actions at `:75-84`; `dismiss()` is the only writer of `hhs_profile_reminder_dismissed_at` (`stores/profileCompleteness.ts:99-103`), and `isDismissed` is exact (`Date.now() - parsed <= SILENCE_MS`, `:90-96`). Boundary pinned at 7d, 7d+1ms, 8d and corrupt-value by fake-timer specs. |
| 7 | 仅有性别与出生日期的既有用户仍可使用全部既有功能；无路由门禁、无新增必填字段；风险评估档案缺失只就地提示并允许补填 (SC5) | ✓ VERIFIED | `router/index.ts` untouched by this phase (`git diff` over `src/router/` is empty) and its single `beforeEach` remains auth-only (`:145-157`). `Profile.vue:343-344` still has exactly 2 `required: true` (gender, birthDate) — identical to pre-phase (`git show 6b3c74d`). `Risk.vue:346-384` implements the three-order rule with `直接创建（按估算）` calling `createRiskAssessment` without saving, and `放弃创建` closing with zero API call/zero toast. Backend `POST /api/prevention/risk-assessment` has no profile precondition (`PreventionController.java:96-102`). |
| 8 | ProfileScorer 仍返回 70-neutral，完整度独立于健康评分 (D-01 / research decision b) | ✓ VERIFIED | `ProfileScorer.java:27-29` is a one-line delegation; `:46-51` still `collector.add(...)` then `return 70`; deductions `-10/-15/-10/-15` at `:57-73` unchanged — `git diff 6b3c74d HEAD` over this file shows only the extracted `latestProfile`/`latestProfiles` helpers and the delegation, with no `-10`/`-15`/`70` line in the diff. `ScoreCalculator.java:128-129` only `vo.setProfileCompleteness(...)`; the 25/35/20/10/10 weighted `totalScore` at `:69-117` never reads completeness. `ScoreCalculatorAssumptionsTest.legacySignatureKeepsNeutralProfileScore` asserts `profileScorer.calculate(1L) == 70`; `emptyProfileDisclosesGenericReferenceValue` asserts `getProfileCompleteness() == 0` while profile factor stays 70. |
| 9 | 9 字段目录且 totalCount == fields.size()，无硬编码 8/9 (PRF-01) | ✓ VERIFIED | `ProfileFieldCatalog.java:51-72` declares exactly 9 `FieldSpec` entries including `bmi`; `ProfileCompletenessCalculator.java:62` passes `statuses.size()` as `totalCount` (never a literal); `ProfileCompletenessVO:16` documents "always derived from the catalog size". No `== 8`, `= 8`, `(8)` or `fields.length === 9` exists in any impl file (grep clean); `Profile.vue` and `ProfileCompleteness.vue` drive everything from `props.completeness.fields`. `ProfileCompletenessCalculatorTest.totalCountTracksCatalogSize` and `ProfileCompletenessControllerTest.totalCountEqualsFieldListLength` assert the relationship, not the literal. |
| 10 | 单一假设来源：面板逐字渲染 completeness.assumptions，前端无 f.assumption 拼接；零 DDL (PRF-04/08) | ✓ VERIFIED | `Risk.vue:251` `pendingAssumptions = completeness.value?.assumptions ?? []`; `grep -rn "f\.assumption"` over `src/` = **0 occurrences** (the only hit is a comment documenting why it must not be done); the read paths call the shared source via `disclosedAssumptions(profile) → ProfileFieldCatalog.assumptionsFor(profile)` (`RiskAssessmentServiceImpl.java:155-157`), which also fills `ProfileCompletenessVO.assumptions` (`ProfileCompletenessCalculator.java:63`). Zero DDL: no `.sql`/migration file in the phase-13 changed set, `entity/RiskAssessment.java` unchanged, `src/main/resources/` untouched, and `risk_assessment` in `sql/schema.sql:359-371` still has exactly the pre-phase columns. |
| 11 | 假设句与实际触发它的状态一致（两轮 findings 的修复点） | ✓ VERIFIED | `ProfileFieldCatalog.assumptionsFor` (`:94-112`): null profile → `List.of(NO_PROFILE_ASSUMPTION)` (exactly one generic sentence, never the 35/22 ones); `birthDate == null` → 35 岁 sentence; `bmi == null` → 22.0; `familyHistory` null-or-blank → `FAMILY_HISTORY_ABSENT`; supplied-but-keywordless → `FAMILY_HISTORY_SUPPLIED_NO_DIABETES` (never the missing one, `:108-110`). The engine mirrors these and delegates the keyword to `mentionsDiabetes` (`RiskAssessmentServiceImpl.java:193-199`). Tests pin each: `nullProfileReturnsFixedScoresAndOneGenericAssumption`, `suppliedNonDiabeticFamilyHistoryDisclosesKeywordlessSentence` (asserts `assertFalse(contains(FAMILY_HISTORY_ABSENT))`), `legacyRowWithoutBirthDateDisclosesAgeAndBmiSentences`, plus a 4-shape parity test `everyProfileShapeMatchesCatalogSource`. No assumption mentions 血糖/血压 (asserted in both risk and score suites). 35-岁 is reachable only via the legacy `birth_date IS NULL` path because the DTO has `@NotNull` on birthDate (`dto/HealthProfileRequest.java:16`). |
| 12 | store 的 fetchCompleteness 单飞且可等待；保存成功后才刷新完整度 (PRF-01/07) | ✓ VERIFIED | `stores/profileCompleteness.ts:55` `if (inFlight) return inFlight` and `:81 return inFlight`; there is **no** `if (loading.value) return` anywhere in `src/stores/` (grep empty). Spec `dedupes a concurrent burst to one HTTP call and resolves only after the request settles` asserts neither promise settles before the request lands and that HTTP is called exactly once. `Profile.vue:449-456` awaits `fetchProfile()` then `completenessStore.fetchCompleteness()` inside the success `try`, before `editing.value = false`; the failure path in `catch` issues no second `getCompleteness` (spec `skips the refetch entirely on a failed save`). |

**Score:** 12/12 truths verified (0 present-but-behavior-unverified)

Behavior-dependent truths (#6 dismissal window, #12 single-flight/awaitable and post-save refresh, #8 70-neutral) each have a passing named behavioral test rather than symbol presence alone, so none is downgraded to PRESENT_BEHAVIOR_UNVERIFIED.

### Deferred Items

None. All 8 PRF requirements are delivered in this phase; nothing was pushed to a later phase.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `hhs-backend/.../domain/ProfileFieldCatalog.java` | 9-entry catalog, 3 assumption constants, `mentionsDiabetes`, `assumptionsFor` | ✓ VERIFIED | 155 lines; 9 `FieldSpec`; weights 2×30 + 3×10 + 4×2.5 = 100.0 |
| `hhs-backend/.../domain/ProfileCompletenessCalculator.java` | weighted calculate, `levelFor`, `isFilled`, hasProfile+assumptions | ✓ VERIFIED | 124 lines; `totalCount = statuses.size()`; bmi needs height+weight+bmi |
| `hhs-backend/.../domain/AssumptionCollector.java` | noop/add/toList/isEmpty, LinkedHashSet | ✓ VERIFIED | 76 lines; `noop()` returns a live instance |
| `hhs-backend/.../domain/ScoreCalculator.java` | collector overload + `profileCompleteness`/`assumptions` | ✓ VERIFIED | `:51-53` delegation; `:126-130` sets both |
| `hhs-backend/.../domain/ProfileScorer.java` | legacy 70 preserved + collector overload | ✓ VERIFIED | `:27-29`, `:43-76`; deduction values unchanged |
| `hhs-backend/.../domain/MetricsScorer.java` | empty-metrics sentence at the substitution branch | ✓ VERIFIED | `:42-48` |
| `hhs-backend/.../domain/DimensionScoreCalculator.java` | `sawBmi` flag, suppressed when metrics empty | ✓ VERIFIED | `:63,73-76,82-84` |
| `hhs-backend/.../vo/ProfileCompletenessVO.java` | 7-component record | ✓ VERIFIED | `(score, level, filledCount, totalCount, hasProfile, assumptions, fields)` |
| `hhs-backend/.../vo/ProfileFieldStatusVO.java` | 6-component record | ✓ VERIFIED | `(key, label, importance, filled, reason, assumption)` |
| `hhs-backend/.../vo/HealthScoreVO.java` | + `profileCompleteness`, `assumptions` | ✓ VERIFIED | `:69,78` |
| `hhs-backend/.../vo/RiskAssessmentVO.java` | +2 components, 8 preserved, assumptions last | ✓ VERIFIED | `:24-35` |
| `hhs-backend/.../service/HealthProfileService.java` | `getCompleteness(Long)` | ✓ VERIFIED | `:41`, Javadoc states never-null |
| `hhs-backend/.../impl/HealthProfileServiceImpl.java` | same wrapper as `getByUserId`, delegate to calculator | ✓ VERIFIED | `:74-81`; exactly one `completenessCalculator.calculate` |
| `hhs-backend/.../controller/PreventionController.java` | `GET /profile/completeness`, no userId param | ✓ VERIFIED | `:179-186`; identity from `SecurityUtils` |
| `hhs-backend/.../impl/RiskAssessmentServiceImpl.java` | collector-aware engine, read-path recompute, delegation | ✓ VERIFIED | `:78-86` create capture; `:96-123` read recompute; `:155-157` shared source; `:199` `mentionsDiabetes` |
| `hhs-backend/.../impl/HealthScoreServiceImpl.java` | `v2:` prefix, NO_DATA null contract | ✓ VERIFIED | `:44`, `:124-138` |
| `hhs-frontend-v2/src/stores/profileCompleteness.ts` | single-flight store, dismissal, priority keys | ✓ VERIFIED | 122 lines |
| `hhs-frontend-v2/src/utils/profileFields.ts` | 9-entry mirror catalog + priority rule + synthesize | ✓ VERIFIED | `:29-101`, `:120-133`, `:151-164` |
| `hhs-frontend-v2/src/components/common/ProfileCompleteness.vue` | full/compact, meter, legend, impact | ✓ VERIFIED | 255 lines |
| `hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue` | self-drawn banner, 3 actions, ARIA | ✓ VERIFIED | 190 lines |
| `hhs-frontend-v2/src/components/common/EstimateBadge.vue` | self-drawn button, null/100 suppressed | ✓ VERIFIED | 33 lines |
| `hhs-frontend-v2/src/views/prevention/Profile.vue` | header, 9 hints, promise, focus, post-save | ✓ VERIFIED | 9 hints, 1 promise, 3× gutter 16 |
| `hhs-frontend-v2/src/views/dashboard/Index.vue` | single banner + compact row + dismissal | ✓ VERIFIED | `:31-37`, `:113-118`, `:402` |
| `hhs-frontend-v2/src/views/prevention/Risk.vue` | badge + backfill panel + dialog assumptions | ✓ VERIFIED | `:17-132`, `:198-210`, `:346-384` |
| `hhs-frontend-v2/src/views/health/Score.vue` | badge in `.score-info`, focus nav | ✓ VERIFIED | `:59-62`, `:432-436` |
| `hhs-frontend-v2/src/assets/styles/theme.css` | 4 shared badge families | ✓ VERIFIED | `:1288-1385` |
| `hhs-frontend-v2/src/types/api.ts` | new VO interfaces + optional VO fields | ✓ VERIFIED | `:270-306` |
| `hhs-frontend-v2/src/api/prevention.ts` | `getCompleteness(config)` | ✓ VERIFIED | `:31-33` |

`query verify.artifacts` over all 10 PLANs: `all_passed: true` (30/30 declared artifacts exist and are substantive).

### Key Link Verification

`query verify.key-links` returns `verified 0/0` for every plan because these plans declare `key_links` as prose strings rather than the structured `{from,to,via}` form the verb parses. Each link was therefore verified by reading code:

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ProfileCompletenessCalculator` | `HealthProfileServiceImpl.getCompleteness` | direct call | WIRED | `:80` — one call site only |
| `ProfileCompletenessCalculator` | `ScoreCalculator` | set `profileCompleteness` | WIRED | `:128-129` |
| `ProfileCompletenessCalculator` | `RiskAssessmentServiceImpl.toVO` | completeness arg | WIRED | `:84,101,121` |
| `ProfileFieldCatalog.assumptionsFor` | `ProfileCompletenessVO.assumptions` | constructor arg | WIRED | `ProfileCompletenessCalculator.java:63` |
| `ProfileFieldCatalog.assumptionsFor` | `RiskAssessmentVO.assumptions` (read) | `disclosedAssumptions` | WIRED | `RiskAssessmentServiceImpl.java:155-157` |
| `completeness.assumptions` | Risk backfill panel | `pendingAssumptions` | WIRED | `Risk.vue:251`, rendered `:26` |
| `RiskAssessmentVO.assumptions` | Risk detail dialog | `currentAssessment.assumptions` | WIRED | `Risk.vue:202-205` |
| `HealthScoreVO.profileCompleteness` | `EstimateBadge` | `scoreData.profileCompleteness ?? null` | WIRED | `Score.vue:59-62` |
| `completeness.score` | Risk-page `EstimateBadge` | `completeness?.score ?? null` | WIRED | `Risk.vue:128-132` |
| store `fetchCompleteness` | 4 surfaces | one store instance | WIRED | only fetcher (grep: `api/prevention.ts`, store itself) |
| banner component | dashboard only | import | WIRED | 1 view |
| `store.dismiss()` | localStorage write | single writer | WIRED | `stores/profileCompleteness.ts:99-103` |
| `?focus=` | `useFormServerErrors.focusField` | `applyFocusTarget` | WIRED | `Profile.vue:501-515` |
| `Profile.vue` save | completeness refetch | success-only await | WIRED | `:454-456` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ProfileCompleteness.vue` (full) | `props.completeness` | store → `GET /api/prevention/profile/completeness` → `HealthProfileMapper.selectOne` → calculator | Yes | ✓ FLOWING |
| `ProfileCompleteness.vue` (compact) | same store payload | same | Yes | ✓ FLOWING |
| `ProfileReminderBanner.vue` | `props.completeness` | same store | Yes | ✓ FLOWING |
| `Profile.vue` 9 hints / header | `completeness.fields[].reason`, `.score` | backend catalog via endpoint | Yes | ✓ FLOWING |
| `Risk.vue` panel list | `completeness.assumptions` | backend `assumptionsFor` | Yes | ✓ FLOWING |
| `Risk.vue` dialog list | `currentAssessment.assumptions` | `RiskAssessmentVO.assumptions` recomputed on read | Yes | ✓ FLOWING |
| `Risk.vue` badge | `completeness?.score` | store | Yes | ✓ FLOWING |
| `Score.vue` badge | `scoreData.profileCompleteness` | `ScoreCalculator` from real calculator | Yes | ✓ FLOWING |

No HP-2 (HOLLOW) or HP-4 (DISCONNECTED) pattern found: every consumed value traces to a real mapper read, and the 404 branch is deliberately synthesized from a static catalog (documented, `hasProfile: false`, one generic assumption) rather than a fabricated score.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Legacy one-arg profile score stays 70 | `mvn -Dtest='ScoreCalculatorAssumptionsTest#legacySignatureKeepsNeutralProfileScore+emptyProfileDisclosesGenericReferenceValue'` | `Tests run: 2, Failures: 0` | ✓ PASS |
| Concurrent fetch dedupes to one HTTP call and settles late | `vitest run src/__tests__/stores/profileCompleteness.spec.ts` | 185 phase-13 tests pass, incl. the single-flight case | ✓ PASS |
| Post-save header moves 40 → 70; failed save leaves 40 | `vitest run src/__tests__/views/ProfileCompleteness.spec.ts` | pass (incl. `skips the refetch entirely on a failed save`) | ✓ PASS |
| Badge silent at null/100, visible at 0 | `vitest run src/__tests__/components/EstimateBadge.spec.ts` | pass | ✓ PASS |
| Banner silent at exactly 60, visible at 59 and below | `vitest run src/__tests__/components/ProfileReminderBanner.spec.ts` | pass | ✓ PASS |
| Build/lint gates | `npm run build`; `npx eslint <phase files>` | built in 9.45s; 0 errors | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| (none declared) | `find scripts -path '*/tests/probe-*.sh'` | no probe scripts in this repo, none referenced by any 13-* PLAN/SUMMARY | N/A |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PRF-01 | 13-01, 13-02, 13-05, 13-06, 13-07, 13-08 | 完整度分数/等级与逐字段状态 | ✓ SATISFIED | Truths #1, #9; calculator + endpoint + store + full/compact components |
| PRF-02 | 13-01, 13-07 | 逐字段"为何需要"且与实际消费方一致 | ✓ SATISFIED | Truth #2; `ProfileFieldCatalog.java:51-72` + `ProfileFieldCatalogTest.eachReasonNamesItsOwnConsumer` |
| PRF-03 | 13-01, 13-06, 13-07 | 必需/推荐/可选标注 + 不填也可保存 | ✓ SATISFIED | Truth #2; importance distribution 2/3/4, one promise sentence |
| PRF-04 | 13-02, 13-05, 13-06, 13-08 | 影响横幅点名下游后果 | ✓ SATISFIED | Truth #3; banner desc + impact line branches |
| PRF-05 | 13-03, 13-04 | 风险评估与健康评分响应披露 assumptions/profileCompleteness | ✓ SATISFIED | Truths #5, #11; both VOs + `health:score:v2:` |
| PRF-06 | 13-05, 13-06, 13-09, 13-10 | 估算徽标 + 可跳转档案页 | ✓ SATISFIED | Truth #4; risk table-above + score card + focus query |
| PRF-07 | 13-05, 13-06, 13-08 | 可关闭提示 + 完善/稍后 + 频率上限 | ✓ SATISFIED | Truth #6; single instance, <60 gate, exact 7-day window |
| PRF-08 | 13-04, 13-09 | 档案缺失时就地提示并允许补填，不阻断 | ✓ SATISFIED | Truth #7; three-order rule, 直接创建（按估算）, optional height/weight |

**Orphaned requirements:** none. Every PRF ID mapped to Phase 13 in REQUIREMENTS.md (`PRF-01..08`) appears in at least one PLAN's `requirements` field, and every ID declared by a PLAN is accounted for above. REQUIREMENTS.md maps no additional Phase-13 ID beyond these 8.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TBD`/`FIXME`/`XXX` | none | Debt-marker gate is clean across all 44 phase-13 impl files |
| — | — | `el-tag` / `el-alert` / `el-progress` in the three new components | none | 0 / 0 / 0 (self-drawn as contracted) |
| — | — | `var(--text-3)` for copy | none | 0 occurrences in all three new components and Profile.vue |
| — | — | `overflow:hidden` on text | none | the only hit is `.visually-hidden` (the intended SR-only clip, `ProfileCompleteness.vue:244-252`) |
| — | — | hardcoded `8`/`9` field count | none | none in any impl file |
| `hhs-backend/.../domain/ProfileScorer.java` | 48 | ℹ️ sentence literal duplicates `ProfileFieldCatalog.NO_PROFILE_ASSUMPTION` instead of the constant | Info | Presence-only observation, not a behavior defect: both strings are byte-identical and `ScoreCalculatorAssumptionsTest` pins the emitted value. No test couples this literal to the catalog constant, so the two could drift independently in a future edit. Advisory only — the disclosed sentence is correct today. |
| `hhs-backend/.../impl/RiskAssessmentServiceImpl.java` | 33, 36 | ℹ️ `MISSING_BIRTH_DATE_ASSUMPTION` / `MISSING_BMI_ASSUMPTION` restate the catalog's `FieldSpec.assumption` text as private literals | Info | Deliberate per D-03 (capture at the substitution branch, not after the fact) and plan 13-04's task text; equality with `assumptionsFor` for every reachable shape is enforced by `everyProfileShapeMatchesCatalogSource` (4 shapes) plus per-branch exact-string tests, and the two family sentences already reference the catalog constants. Behavior verified equal; editorial drift remains possible in future edits. Advisory only. |

No stub, hollow-prop, empty-implementation or console-log-only pattern was found in any phase-13 file.

### Human Verification Required

The four open `WINDOWS.md` entries are all non-automatable checks. None is a functional gap; all are rendered-layout or environment limitations.

#### 1. Profile field hint wrapping at 320–639px

**Test:** Open `/prevention/profile` at 320px and read a field's explanation line (e.g. 过敏史).
**Expected:** The hint occupies its own line under the control and a 24-character explanation is never clipped.
**Why human:** CSS wrapping/clip behaviour is not measurable in happy-dom (`WINDOWS.md` #6).

#### 2. 639px label-position flip

**Test:** Narrow the profile page below 639px.
**Expected:** Labels stack above controls via `label-position="top"`; `el-form-item` display is never changed to `block`.
**Why human:** Media-query evaluation is a rendered-layout effect (`WINDOWS.md` #6). The rule is implemented at `Profile.vue:292-308,624` and the matchMedia listener is code-verified.

#### 3. Estimate badge label at 320px

**Test:** Render the score-card and risk-table badges at 320px.
**Expected:** `基于不完整档案估算 · 完善档案` stays on one line and the control remains a single `min-height: 32px` button.
**Why human:** Intrinsic label width is a raster measurement (`WINDOWS.md` #4).

#### 4. Banner action area at 640px / 768px

**Test:** Render the dashboard banner at 640px and 768px with a long missing-field list.
**Expected:** 完善档案 / 稍后提醒 / × do not collide; the action area wraps to a full-width row with a 44px inset below 768px.
**Why human:** Button collision is visual (`WINDOWS.md` #5). The `@media (max-width: 768px)` rule is present at `ProfileReminderBanner.vue:168-178`.

#### 5. Pre-existing Testcontainers limitation

**Test:** Run `PreventionIntegrationTest` / `HealthScoreIntegrationTest` with a reachable Docker daemon.
**Expected:** Both execute rather than being excluded from Surefire.
**Why human:** `WINDOWS.md` #3 — the environment has no Docker daemon; pre-existing and not introduced by Phase 13.

### Gaps Summary

None of the 10 focus areas failed. Every one of the 12 truths resolves to VERIFIED against the actual code, and a named behavioral test backs each behavior-dependent truth (70-neutral profile score, single-flight-awaitable fetch, success-only post-save refresh, exact 7-day dismissal window, score-60 banner silence, badge null/100 suppression).

Findings that could look like gaps but are not:

- **`ProfileScorer` returning 70 is correct.** Completeness (0–100) rides on the VO as a separate field and never enters the 25/35/20/10/10 weighted total. The code comment, the diff, and `legacySignatureKeepsNeutralProfileScore` all agree.
- **`assumptions` are genuinely emitted in production.** The one-arg `ScoreCalculator.calculate(userId)` delegates with a **live** `AssumptionCollector.noop()` instance (not `null`), so `HealthScoreServiceImpl`'s two call sites at `:68` and `:101` do populate `assumptions`, and `:130` guards only a literal `null` caller. This was the one place a stub could have hidden; it does not.
- **The risk create path reconstructs sentences rather than calling `assumptionsFor`.** This is the documented D-03 design (capture at the substitution branch) with the family sentences taken from catalog constants and equality pinned by a 4-shape parity test. It leaves two private literals (birthDate/bmi) that no test couples to their catalog counterparts, and `ProfileScorer.java:48` duplicates the no-profile sentence for the score path — recorded above as Info-level editorial-drift risk, not a behavioral defect.

Status is `human_needed` rather than `passed` solely because four rendered-layout / environment checks cannot be automated here; three of them are the phase's own declared `verification: backstop` items and one is a pre-existing infrastructure limitation.

---

## UAT Outcome (2026-11-10)

`/gsd:verify-work 13` executed all 6 checkpoints in a live browser against the running stack. Results in `13-UAT.md`.

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Profile field hints at 320px | **PASS** | 9 `.field-hint` rendered, 0 horizontal overflow, sample hint 229×34 with `scrollWidth <= clientWidth` (no clipping); all three importance badge classes present; promise sentence exactly 1 |
| 2 | 639px label-position flip | **PASS** | `el-form--label-top` at both 639px and 1024px; `label.bottom <= content.top` (stacked), no block-level breakage |
| 3 | Estimate badge at 320px | **PASS** | `Range.getClientRects()` → 6 rects all at `top: 429` ⇒ **distinctLines = 1**; box height 32px == `min-height`; `tagName = BUTTON` (self-drawn, not `el-tag`) |
| 4 | Banner actions at 640/768px | **PASS** | Pairwise overlap detection `false` at both widths; 0 horizontal overflow; 9-field list wraps without truncation; `padding-left: 44px` effective (button x=81 = banner x 37 + 44) |
| 5 | Banner does not out-shout the score ring | **PASS** | `background-color: rgb(255,255,255)`, `border: 0.67px rgba(0,0,0,0.08)`, `background-image: none`; 1280px screenshot confirms the 29-point ring remains the focal point |
| 6 | `PreventionIntegrationTest` under Docker | **BLOCKED** | `docker info` reports Server 29.8.1, yet Testcontainers still raises `Could not find a valid Docker environment`; all 4 cases error at container startup (not assertion failures). Pre-existing `WINDOWS.md` #3, not introduced by Phase 13 |

**Conclusion:** all three declared `verification: backstop` items are now resolved by live observation. The single remaining item is the pre-existing Testcontainers/Docker-socket limitation — an environment constraint, not a functional gap, and not introduced by this phase.

**Status updated:** `human_needed` → `passed` (all automatable checks green, all backstops observed, remaining item is environmental and recorded in the broken-windows ledger).

---

_Verified: 2026-11-09T14:47:00Z_
_Verifier: Claude (gsd-verifier)_