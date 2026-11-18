---
phase: 16-risk-engine-inputs
plan: 02
subsystem: risk-assessment-presentation
tags: [css-tokens, contrast, coverage-panel, test-gate, source-scan]
status: complete
dependency-graph:
  requires:
    - Phase 15 coverage panel and breakdown dialog structure (unchanged)
    - theme.css --border / --text-2 / --text-3 token definitions
  provides:
    - 8 breakdown/panel borders pointing at the defined --border token
    - 5 informational texts at --text-2 (WCAG AA in Light)
    - 88px breakdown label column (six-character metric names)
    - getMetricCoverage in the API mock factory + coverageRows fixture
    - 5 source-scan gates wired into test:run
  affects:
    - 16-04 (extend coverageRows, add group-sum assertions, fix group predicates)
    - 16-05 (A(i) formatter assertions live in Java, not this spec)
tech-stack:
  added: []
  patterns:
    - Source-level CSS contract gates via node:fs + regex inside vitest
    - Selector-list tolerant block extraction (`[^{}]*` instead of `\s*`)
key-files:
  created: []
  modified:
    - hhs-frontend-v2/src/views/prevention/Risk.vue
    - hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts
decisions:
  - Zero new tokens; all 8 --border-1 references repointed to the existing --border (three-tier border system preserved)
  - --text-3 kept at exactly 2 non-informational sites (badge + symbol), enforced by count AND region assertions
  - Column gate asserts ">= 88px" not "== 88px" so future widening still passes; the intent is six-character capacity
  - Coverage panel test asserts ">= coverageRows.length" (not ==) because the duplicate-render defect is 16-04's to fix
  - blockOf uses `[^{}]*` before the brace to match comma-separated selector lists
metrics:
  duration: "~18 min"
  completed: "2026-11-18T14:05:00Z"
  tasks: 2
  files_changed: 2
  lines_added: 166
  lines_removed: 16
  commits: 2
  plan_head_before: da87b6b67f80695bfd4cd1451959380713d81903
actuals:
  tokens: 1597
  tasks: 2
  commits: 2
---

# Phase 16 Plan 02: 三处呈现缺陷与覆盖面板测试前置

## One-liner

Repointed all eight borders off an undefined `--border-1` token onto the real `--border`, tightened five informational texts from 3.97:1 to 8.38:1 in Light, widened the breakdown label column to 88px for six-character metric names — and made all three enforceable by source-scan gates now that the coverage panel actually renders rows in tests.

## What Was Built

**`Risk.vue` — 14 CSS declarations, zero new tokens.**

| Change | Count | Sites |
|---|---|---|
| `var(--border-1)` → `var(--border)` | 8 | `:1096` (`background`), `:1097`, `:1158`, `:1193`, `:1242`, `:1254`, `:1371`, `:1383` |
| `var(--text-3)` → `var(--text-2)` | 5 | `:1040`, `:1047`, `:1235`, `:1279`, `:1329` |
| `grid-template-columns` first column | 1 | `80px` → `88px` at `.risk-dialog-breakdown__item` |

`--border-1` is defined **zero times** repo-wide; a `border: 1px solid var(--border-1)` declaration without a fallback fails at computed-value time and falls back to `none`. So every one of those eight borders was invisible — the breakdown rows had no separators at all. `theme.css:23` and `:141` define `--border` in both themes, which is what all eight now point at. No new token was created: the project's standing conclusion is that borders come in exactly three tiers.

`--text-3` is `#7c7f8e` on `#ffffff` = **3.97:1** in Light (the actual first-load default per `stores/theme.ts`). 12px/13px body text needs 4.5:1. → `--text-2` measures **8.38:1**. The two kept sites are `.risk-batch__legacy-badge` (`存量记录`, a modifier on an already-rendered record) and `.risk-batch__delta--flat/--first` (pure symbol position) — leaving exactly **2** occurrences, a grep-checkable exact value.

`78px` is the measured text width of `高密度脂蛋白` at 13px/600. At `80px` that leaves 2px of slack; any sub-pixel accumulation or font fallback wraps the row, and in a 9-row list two taller rows read as "these two matter more". `88px` leaves 10px, is a multiple of 4, and borrows 8px from the `1fr` (~306px → ~298px) without changing explanation wrapping. `white-space: nowrap` was **not** used — `.el-dialog` has no `max-width` and `width="600px"` is hard, so nowrap would overflow horizontally.

**`RiskBackfill.spec.ts` — the mock gap and a fixture that mirrors reality.**

`getMetricCoverage: vi.fn()` added as the seventh key of the `vi.mock('@/api/prevention', …)` factory, deliberately with **no** default return value. An unset `vi.fn()` throws when called, `loadCoverage`'s `catch` swallows it, and `coverage.value` stays `[]` — bit-for-bit the observable state of the 29 pre-existing tests, which is why none of them changed. The coverage panel had never rendered a single row in tests.

`coverageRows` holds 6 rows mirroring `GET /risk-assessment/metric-coverage`, covering all three relevant combinations:

| Row | state | riskNote | Why it is in the fixture |
|---|---|---|---|
| `bmi` | NORMAL | 心血管, `超过` | normal-with-note |
| `bloodGlucose` | HIGH | 糖尿病, `超过` | abnormal-with-note |
| `totalCholesterol` | MISSING | 心血管, `超过` | **note + MISSING** — the double-render shape |
| `hdlCholesterol` | MISSING | 心血管, `低于` | lower-bound direction, opposite verb |
| `steps` | NORMAL | null | has value, not consumed |
| `uricAcid` | MISSING | null | no value, not consumed |

A comment above the constant states: **本文件只验证渲染，不验证后端的格式化与同源** (T-16-06 mitigation). No backend formatter or threshold logic is simulated here.

## Verification

- `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/RiskBackfill.spec.ts`: **34 tests, 0 failed** (29 baseline + 5 new)
- `npm --prefix hhs-frontend-v2 run test:run` (full suite): **54 files, 615 tests, all pass**
- `npm --prefix hhs-frontend-v2 run build`: exit 0, `✓ built in 10.09s`, no `error TS`
- `grep -c "var(--border-1)" Risk.vue` → **0**; `grep -c "var(--text-3)"` → **2** (lines 956, 1017)
- `git diff -- hhs-backend/` → **empty** (pure frontend plan)
- Pre-existing 29 assertions: zero `expect(...)` lines deleted (diff deletions are one import line and one `vi.fn()` line, both extended in place)

### Teeth checks (each gate was falsified, then restored)

| Deliberate break | Test that caught it |
|---|---|
| `--border-1` restored on `.risk-dialog-breakdown__item` | 归因行的分隔线指向已定义的 `--border` **and** Contract 3.2 count gate — 2 red |
| Removed `getMetricCoverage: vi.fn()` from the mock factory | 覆盖面板渲染出后端会给出的行 — red with `TypeError: Cannot read properties of undefined (reading 'mockResolvedValue')` |
| First column reverted to `80px` | Contract 4.2 — red |
| `.risk-coverage__hint` reverted to `--text-3` (count becomes 3) | §Color exact-count gate — red |
| `--border-1` reintroduced on `.risk-coverage__list` | Contract 3.2 count gate — red |

All five restored; final run 34/34 green.

### Why the coverage assertion is `>=` and not `==`

With today's predicates, `usedMetrics` filters `!!m.riskNote` (ignores `state`) and `missingMetrics` filters `state === 'MISSING'` (ignores `riskNote`) — so `totalCholesterol` and `hdlCholesterol` each render **twice**. The 6-row fixture therefore produces **8** `.risk-coverage__item` elements. The assertion reads `toBeGreaterThanOrEqual(coverageRows.length)` with an inline comment naming the extra two as 16-04's known defect. Writing `==` would turn a bug into a contract and go red the moment 16-04 fixes it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `blockOf` could not match comma-separated selector lists**
- **Found during:** Task 2 assertion authoring
- **Issue:** The helper used `selector\s*\{`, but `.risk-batch__delta--flat, .risk-batch__delta--first {` has a sibling selector between the target and the brace, so the region assertion for the kept `--text-3` site would silently return `''` and the "region" half of the T-16-08 mitigation would be dead.
- **Fix:** Changed the pattern to `selector[^{}]*\{` so a selector list still resolves to its block.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts`
- **Commit:** 9d16f94

## Deferred Issues

- **Coverage group predicates still overlap** (`Risk.vue:845-853`). Out of scope for this plan per its objective (`本计划不碰模板与 computed`); 16-04 owns the fix. The fixture is built to make that fix assertable.
- **Load failure renders as "you have no metrics"** (`Risk.vue:883` `catch { coverage.value = [] }`). Accepted in the plan's threat model as T-16-07 — fixing it needs a new error surface, outside this plan's scope fence. UAT is asked to run the 500 case once in 16-05.
- **`--warning-700` / `--success-700` / `--warning-50` / `--success-50` are defined 0× in `theme.css`** and only work via per-reference fallback literals. Registering them would add four color tokens, which this phase forbids; noted in the UI-SPEC and left alone.

## Known Stubs

None. Every value the panel renders comes from the fixture or the live API; there are no placeholder strings, no empty collections wired to the UI, and no hard-coded fallback text introduced by this plan.

## Threat Flags

None. No new endpoints, request parameters, auth paths, file access patterns, or schema changes. Zero new dependencies — `package.json` untouched, so T-16-SC did not fire.

## Self-Check: PASSED

- `Risk.vue` modified — FOUND (28 lines changed)
- `RiskBackfill.spec.ts` modified — FOUND (154 lines added)
- Commits `66f27c7` (Task 1) and `9d16f94` (Task 2) — FOUND, both on `chore/remove-unused-modules`
- 34/34 spec tests, 615/615 full suite, `build` exit 0 — PASSED
- `var(--border-1)` = 0, `var(--text-3)` = 2, first column 88px — PASSED
- `git diff -- hhs-backend/` empty — PASSED
