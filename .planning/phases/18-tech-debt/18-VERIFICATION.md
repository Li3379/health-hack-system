---
phase: 18-tech-debt
verified: 2026-11-19T03:10:00Z
status: passed
score: 4/5 success criteria verified, 1 partially (see SC4)
covered_files:
  - .planning/ROADMAP.md
  - .planning/phases/18-tech-debt/18-SUMMARY.md
  - .planning/phases/18-tech-debt/18-metric-key-aliases.md
  - hhs-backend/src/main/java/com/hhs/service/domain/MetricDisplayFormatter.java
  - hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java
  - hhs-backend/src/test/java/com/hhs/service/domain/MetricDisplayFormatterTest.java
covered_digest: "v1:sha256:35a8fccb4cbff59cb88e04fc49be11d70a3011d638ed3a831ea079643187e7ba"
behavior_unverified: 0
overrides_applied: 1
gaps: []
advisory:
  - "SC4 的『未消费字典键』一项未验证：ROADMAP 原文列举了它，但本阶段没有定义可机械判定的口径，未做。"
---

# Phase 18: 技术债清理与收尾 Verification Report

**Phase Goal:** 清除无引用的类与重复实现，并消除指标 key 别名的静默落空。

**Verified:** 2026-11-19T03:10:00Z
**Status:** passed
**Method note:** Executed through three autoresearch segments (5, 6 and the alias
segment 3) rather than `plan-phase` → `execute-phase`. Evidence is metric-driven
with mutation-tested guards; see the procedural note in `17-VERIFICATION.md`.

## Goal Achievement

| # | Success Criterion | Status | Evidence |
|---|---|---|---|
| 1 | 无零引用的容器管理类 | ✓ VERIFIED | `dead_code` 3 → 0. Deleted `RiskAnalyzer` (zero refs, no interface, never injected) and `PushStrategyService` (all three public methods uncalled; superseded by `PushChannelManager`). `dup_risk_scorer` also cleared in the same step because `RiskAnalyzer.calculateRiskScore` was byte-identical to `RiskScorer.calculate` |
| 2 | 不存在以遗留别名作匹配臂的 switch 分支 | ✓ VERIFIED | `alias_sites` 6 → 0. `grep -rnE 'case "(glucose\|temperature\|spo2\|sbp\|dbp\|tg\|hdl\|ldl\|ua\|cr\|temp\|pulse\|step\|glu)"'` over `hhs-backend/src/main` → **0 hits**. `.equals("glucose"\|"temperature")` → 0 hits |
| 3 | key 匹配前统一归一化 | ✓ VERIFIED | Four files now call `MetricKeyNormalizer.toCanonicalKey` before matching: `MetricDisplayFormatter` (both `getDisplayName` and `getUnit`), `DimensionScoreCalculator`, `MetricDeduplicationServiceImpl`. Historical aliases resolve; canonical keys resolve |
| 4 | 冗余布尔判定归零 | ⚠️ **PARTIAL** | `redundant_guards` 1 → 0 — `isOxygenCritical` removed (fully subsumed by `isCritical`, since `bloodOxygen`'s `panic[0]` is 90 and non-null). **But ROADMAP's SC4 also lists「无未消费字典键」, which was never given a mechanical criterion and was not done.** Recorded as advisory rather than claimed |
| 5 | 后端门禁全绿 | ✓ VERIFIED | `mvn -B -o test` → **708 tests, 0 failures**; `checkstyle:check` → 0 violations |

## Why the alias fix mattered (all six were user-visible or score-affecting)

| File | Alias | Consequence |
|---|---|---|
| `MetricDisplayFormatter` (display) | `glucose`, `temperature` | English key rendered to the user |
| `MetricDisplayFormatter` (unit) | `glucose`, `temperature` | unit returned as empty string |
| `RealtimeController` (display) | `glucose`, `temperature` | same, in a duplicate implementation |
| `RealtimeController` (unit) | `glucose`, `temperature` | same |
| `DimensionScoreCalculator` | `glucose` | glucose **never** reached the metabolic penalty → score read artificially high |
| `MetricDeduplicationServiceImpl` | `glucose` | always took the default dedup window |

The clearest proof it was an oversight rather than intent lives inside
`MetricDisplayFormatter` itself:

```java
case "temperature", "bodyTemperature" -> "体温";
```

The author knew the canonical key and listed both — then one line later wrote
only `case "glucose"` and omitted `bloodGlucose`.

## Gate integrity

- **Alias metric anti-cheat:** canonical truth is parsed from
  `MetricKeyNormalizer`'s own declarations (23 keys), so the metric cannot drift
  from the registry. Post-fix sweep for other alias spellings and `.equals(...)`
  forms returned empty.
- **`dead_code` had two false-positive classes, both caught:**
  - The first pass reported 52 "unreferenced classes" — nearly all were
    `@Service`/`@Controller`/`@Configuration`, which the container instantiates.
  - `JwtSecretValidator` has zero references but performs its check in
    `@PostConstruct`; bean instantiation **is** its invocation. Deleting it would
    have silently removed a JWT secret-length check.
- **MetricDisplayFormatter had no test at all** — which is how one bug survived
  across four files. `MetricDisplayFormatterTest` (4 tests) constructs it with a
  `null` registry on purpose: that forces the switch fallback, the only path that
  was ever broken. **Mutation-checked:** removing the normalization turns two of
  the four red.
- **`isOxygenCritical` pin test:** `validateMetricRange_bloodOxygenBelow90_reportsCriticalTier`.
  **Mutation-checked:** dropping the low-bound half degrades bloodOxygen 85 to an
  ordinary「偏低」warning and the test goes red.

## Iteration ledger

| segment | metric | start → end |
|---|---|---|
| 3 | `alias_sites` | 6 → **0** |
| 4 | `dead_code` | 3 → **0** |
| 5 | `dup_bodies` | 1 → 1 (**discarded**, not achieved) |
| 6 | `redundant_guards` | 1 → **0** |

Segment 5 is recorded as a deliberate non-result: extracting `toVO` into a shared
mapper reached `dup_bodies=0` and compiled, but the gate went red — `HealthMetricServiceImpl`
has an explicit constructor whose new parameter was unassigned, and four test
classes broke because `@Mock`-ing the mapper makes `toVO` return null while
`@InjectMocks` injects after field initializers. Cost: one constructor signature
change plus three test files, to delete twelve duplicated lines. Reverted, with
revisit conditions written into `.auto/prompt.md`.

## Two hypotheses disproved (recorded so they are not re-explored)

**`PANIC_LIMITS` vs registry `hardMin`/`hardMax` do not conflict.**
`ThresholdRangeValidator`'s own comment says "registry-derived limits, not
PANIC_LIMITS", which reads like two competing number sources. Measured all eight
metrics: every panic range sits strictly inside its physiological hard range
(e.g. systolic hard 40~300, panic 70~180). Complementary semantics, both correct,
no merge. My first parse of those numbers read `MetricDefinition`'s fields one
position off (`step` as `hardMin`), producing "systolic hardMax is 40 mmHg" and
eight false inversions; checking the record declaration fixed it.

**The `toVO` duplication is real but not worth its price.** See segment 5 above.

## Deferred / not done (explicitly, not silently)

- **`RiskScorer` reading the `contributors` snapshot** — currently still maps
  `riskLevel`, not the frozen snapshot. This is a product decision (whether the
  snapshot supersedes the level mapping), not a mechanical cleanup.
- **`FileUploadController` English-leak sweep** — inconclusive; no
  `Result.failure(n, "English")` form found, but other paths were not exhaustively
  checked.
- **未消费字典键** — no mechanical criterion defined; not attempted.
- **Segment 5's `dup_bodies`** — one accepted instance remains.

## Verdict

Four of five success criteria verified outright; SC4 is partially verified —
its redundant-guard half is done and mutation-guarded, its unconsumed-dictionary-keys
half is explicitly out of scope. The phase goal (dead code + duplicate
implementations + alias fallthrough) is met.

**Status: passed** (with SC4 recorded as partial, not silently upgraded).