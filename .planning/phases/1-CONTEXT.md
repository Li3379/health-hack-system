# Phase 1 Context: Score Persistence Bug Fix

**Created:** 2026-05-28
**Phase:** 1 — Score Persistence Bug Fix
**Mode:** mvp

---

## Decisions

### D1: Dimension Score Derivation Strategy (CR-03)

**Decision:** Derive 4 dimension scores from existing sub-scorer data sources.

**Rationale:** The `health_score_history` table has columns `cardiovascular_score`, `metabolic_score`, `weight_score`, `lifestyle_score` that are never populated. The current `ScoreCalculator` produces 5 factor scores (healthProfile, latestMetrics, riskAssessment, screeningReport, wellness) using a different categorization. Rather than building a new scoring system, we derive dimension scores from the same data the existing sub-scorers already query.

**Mapping:**
| DB Dimension | Data Source | Logic |
|---|---|---|
| `cardiovascularScore` | `realtime_metrics` (heartRate, systolicBP, diastolicBP) | 100 − sum of CV metric penalties (each up to 15pts), min 0 |
| `metabolicScore` | `realtime_metrics` (glucose) | 100 − glucose penalty (up to 20pts), min 0 |
| `weightScore` | `realtime_metrics` (bmi) | 100 − BMI penalty (up to 10pts), min 0 |
| `lifestyleScore` | `health_metrics` (wellness category) | Direct use of `WellnessScorer.calculate(userId)` |

**Implementation approach:**
1. Add `calculateDimensionScores(Long userId)` to `ScoreCalculator` — queries `RealtimeMetricMapper.getLatestMetricsByUser()` and applies the same penalty logic as `MetricsScorer.getMetricPenalty()` but grouped by dimension
2. Add a `DimensionScores` record (or Map) to `HealthScoreVO`
3. Add 4 dimension fields + constructor overload to `ScoreUpdatedEvent`
4. Pass dimension scores from `HealthScoreServiceImpl` → `ScoreUpdatedEvent`
5. Set dimension scores in `ScoreUpdatedEventListener` → `HealthScoreHistory.builder()`

**Why not refactor MetricsScorer:** MetricsScorer is a @Component with injected mapper. We duplicate the penalty switch in ScoreCalculator (which already has access to MetricsScorer's mapper via its own sub-scorers). Alternatively, extract the penalty logic to a shared static method in a `MetricPenalties` utility. Prefer the static utility to avoid coupling.

**Files to change:**
- NEW: `com.hhs.service.domain.DimensionScoreCalculator` — pure logic, no Spring annotations
- MODIFY: `HealthScoreVO` — add `Map<String, Integer> dimensionScores` field
- MODIFY: `ScoreUpdatedEvent` — add 4 BigDecimal fields + constructor overload
- MODIFY: `ScoreCalculator` — call DimensionScoreCalculator, set on HealthScoreVO
- MODIFY: `HealthScoreServiceImpl` — pass dimension scores to event (lines 66, 98)
- MODIFY: `ScoreUpdatedEventListener` — set dimension scores on HealthScoreHistory
- MODIFY/NEW: `ScoreHistoryServiceImplTest` — verify dimension score persistence

### D2: WR-02 DeviceSync Mock Detection Fix

**Decision:** Expose `device.mock.enabled` config via a lightweight API endpoint, then check it in the frontend.

**Rationale:** The backend already has `device.mock.enabled` (true in dev, false in prod) in Spring config. The frontend hardcodes `computed(() => false)`. Adding a config endpoint is minimal backend work and gives the frontend a reliable signal.

**Implementation approach:**
1. Add a `GET /api/device/config` endpoint in `DeviceController` that returns `{ mockEnabled: boolean }`
2. In `DeviceSync.vue`, call this endpoint on mount and set `isMockData` from the response
3. Cache the result to avoid repeated calls (the value doesn't change at runtime)

**Files to change:**
- MODIFY: `DeviceController` — add config endpoint
- MODIFY: `DeviceSync.vue` — fetch config and derive `isMockData` from it

### D3: No Schema Migration Needed

**Decision:** No DDL changes for Phase 1. The `health_score_history` table already has the 4 dimension columns — they're just never populated. This fix populates existing columns.

---

## Constraints

- Java 17, Spring Boot 3.2, MyBatis-Plus 3.5.5
- Frontend: Vue 3 Composition API with `<script setup>`, TypeScript
- All sub-scorers in `service/domain/` are pure logic (no Spring annotations except @Component for DI)
- `ScoreCalculator` already depends on all 5 sub-scorers via constructor injection
- `DimensionScoreCalculator` should be stateless pure logic (like `RiskScorer`) — injected into `ScoreCalculator`
- Backend tests must pass: `mvn clean verify -DskipITs`

## Locked Decisions (from prior phases / project setup)

- Event-driven architecture: `ScoreUpdatedEvent` published via `ApplicationEventPublisher`
- `@TransactionalEventListener(AFTER_COMMIT)` for history persistence (already fixed)
- Penalty-based scoring pattern: start at 100, subtract penalties
- Default score when no data: 70 (or 50 for screening)

## Out of Scope

- Changing the 5-factor scoring model or weights
- Adding new database columns
- Frontend visualization of dimension scores (deferred to v2)
- Schema migration tooling (deferred to v2)

---
*Context created: 2026-05-28*
