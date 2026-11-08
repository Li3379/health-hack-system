---
phase: 12-threshold-wizard
plan: 03
subsystem: backend-data-migration
tags: [sql, data-migration, fix-04, alerting, thresholds, manual-ops]
requires:
  - "12-02: alias-IN reads + write-path canonicalization (code-before-script prerequisite, already shipped)"
provides:
  - "hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql — idempotent, transactional, manually-executed metric-key repair script (BEFORE counts, family dedupe, rename, AFTER counts)"
  - "Canonical alert_rule / alert_template seeds in schema.sql — fresh databases never need the fix script"
  - "docs/architecture/DATABASE.md §数据迁移与手工修复 — script name, idempotency, manual-only execution, mandatory code-before-script order, fresh-DB exemption"
affects:
  - "Deployment runbook (manual step added: run script AFTER alias-aware code release)"
  - "12-VALIDATION §Manual-Only Verifications row 2 (script UAT against real MySQL)"
tech-stack:
  added: []
  patterns:
    - "Family-matched derived-table DELETE JOIN (MySQL 8 same-table subquery workaround, manual §15.2.2)"
    - "Dedupe-before-rename on unique-indexed columns (uk_user_metric / uk_metric)"
    - "D-14 keep-latest merge: latest updated_at wins, id DESC tie-break, alias may beat canonical"
key-files:
  created:
    - hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql
  modified:
    - hhs-backend/src/main/resources/sql/schema.sql
    - docs/architecture/DATABASE.md
key-decisions:
  - "D-13: standalone manual script — no migration framework, no application-startup execution"
  - "D-14: keep-latest merge semantics — per (user_id, canonical family) the newest updated_at row survives (id DESC tie-break), so a newer alias row beats an older canonical row"
  - "D-15: alert_rule legacy keys (glucose/temperature) fixed alongside user_threshold, because unconfigured users fall back to seed rules"
requirements-completed: [FIX-04]
metrics:
  duration: 498s
  completed: 2026-11-08
status: complete
actuals:
  tokens: 7540
  tasks: 2
  commits: 2
  plan_head_before: d27a2b7de35421aef63d8e110586b13d775c4af7
patterns-established:
  - "Derived-table DELETE JOIN for same-table dedupe in MySQL 8 (materialization bypasses the delete/select restriction)"
  - "BEFORE/AFTER count bracket as operator-facing audit record (threat T-12-10)"
---

# Phase 12 Plan 03: 阈值指标键手工修复脚本 + 规范化种子 Summary

**One-liner:** Idempotent transactional SQL script that dedupes-then-renames all 19 MetricKeyNormalizer alias keys across user_threshold / alert_rule / alert_template / health_alert (D-14 keep-latest), plus canonical schema seeds and a documented code-before-script deployment order.

## What Was Built

### Task 1 — `fix-threshold-metric-key.sql` (tracer, commit 9f3be80)

Manually-executed repair script with the mandated shape:

1. **BEFORE count block** — one SELECT with per-table non-canonical counts (`ut_total`, `ut_noncanonical`, `ar_noncanonical`, `at_noncanonical`, `ha_noncanonical`), where non-canonical = one of the 19 alias keys in the script's normalization scope.
2. **Single explicit transaction** (`START TRANSACTION` ... `COMMIT`, exactly one of each) — any statement failure rolls back the whole mutation (T-12-08).
3. **user_threshold family dedupe FIRST** (Step 1a) — derived-table `DELETE ut FROM user_threshold ut JOIN (SELECT ut2.id FROM user_threshold ut2 JOIN user_threshold cand ON cand.user_id = ut2.user_id AND <canon>(cand.metric_key) = <canon>(ut2.metric_key) AND (cand.updated_at > ut2.updated_at OR (cand.updated_at = ut2.updated_at AND cand.id > ut2.id))) AS loser ON loser.id = ut.id`, with the same 19-branch CASE canonical mapping on BOTH join sides. Because the join matches by canonical FAMILY, alias-alias pairs (`glucose` + `GLUCOSE`) collapse and a newer alias row evicts an older canonical row (D-14), so the rename never aborts on `uk_user_metric`.
4. **user_threshold rename** (Step 1b) — `UPDATE ... SET metric_key = CASE ... END WHERE metric_key IN (<19 aliases>)`; safe because ≤1 survivor per family.
5. **alert_rule dedupe-then-rename** (Steps 2a/2b) — same pair, no user_id dimension (`uk_metric` is globally unique).
6. **Plain UPDATEs** for `alert_template` and `health_alert` (no unique index on `metric_key`; `template_key` deliberately untouched — display-only, never parsed).
7. **AFTER count block** — same queries labeled `'AFTER'`; non-canonical counts must be 0 and stay identical across repeated runs.

Header comments document purpose, idempotency guarantee, the mandatory code-before-script execution order (RESEARCH §Pitfall 4), and the rollback boundary (one-way, no automatic reverse mapping). No migration framework, no startup execution (D-13).

### Task 2 — canonical seeds + documented order (commit 1a84b1a)

- `schema.sql`: `alert_rule` seeds `glucose → bloodGlucose`, `temperature → bodyTemperature` (numeric values 7.0/11.1/3.9/3.0 and 37.5/38.5/36.0/35.5, the other five rows, and `ON DUPLICATE KEY UPDATE` untouched). The two corresponding `alert_template` groups' `metric_key` and `template_key` prefixes updated (`glucose.high.critical → bloodGlucose.high.critical` etc.) for fresh-DB internal consistency — `template_key` is display-only and never parsed by code (lookup is `metric_key` + `severity_level` only).
- `docs/architecture/DATABASE.md`: new `## 数据迁移与手工修复` section stating the script filename, its idempotency + transactionality, manual-only execution, the forced order「先发布代码，后执行脚本——严禁反向」, and the fresh-DB exemption.

## Coverage (must-have truths)

| # | Must-have truth | Verification refs | Status |
|---|-----------------|-------------------|--------|
| 1 | Script is idempotent: second run changes zero rows, after-count equals first run's | All UPDATEs carry `WHERE metric_key IN (<19-alias set>)` (0 matches on rerun); both DELETE JOINs only fire when a newer same-family sibling exists (≤1 row per family after run 1). Static evidence: acceptance greps below. Runtime double-run: 12-VALIDATION §Manual-Only Verifications row 2 | verified (static) / runtime UAT pending |
| 2 | Normalization covers the complete MetricKeyNormalizer alias set (temp, TEMP, spo2, SPO2, glu, GLU, blood_oxygen — not v1.1's subset) | MetricKeyNormalizer.java alias groups cross-checked; grep: each of the 19 aliases appears exactly 20× in statement lines (8 CASE expressions + 12 WHERE IN lists) | verified |
| 3 | user_threshold dedupe keeps latest `updated_at` per (user_id, family) with id DESC tie-break, deleting other family rows BEFORE the rename | Step 1a derived-table DELETE with `(cand.updated_at > ut2.updated_at OR (cand.updated_at = ut2.updated_at AND cand.id > ut2.id))`; `sed -n '/^DELETE ut$/,/^) AS loser/p' \| grep -c updated_at` = 2; DELETE precedes Step 1b UPDATE | verified |
| 4 | alert_rule duplicates deleted before the glucose/temperature rename (uk_metric would otherwise abort) | Step 2a `DELETE ar ... JOIN (SELECT ...) AS loser` precedes Step 2b `UPDATE alert_rule` | verified |
| 5 | After a first run no (user_id, metric_key) combination has count > 1 in user_threshold | Guaranteed by Step 1a: every family collapses to its single newest row before the rename; runtime `GROUP BY ... HAVING COUNT(*)>1` check is UAT row 2 step ④ | verified-by-design / runtime UAT pending |
| 6 | Whole data mutation wrapped in START TRANSACTION / COMMIT | `grep -c "START TRANSACTION\|COMMIT"` = 2 (exactly one of each); `grep -v '^--' \| grep -c 'START TRANSACTION'` = 1 | verified |
| 7 | schema.sql seeds alert_rule with bloodGlucose / bodyTemperature so fresh DBs never need the script | `grep -c "bloodGlucose\|bodyTemperature" schema.sql` = 9 (2 rule rows + 7 template rows); `grep -n "'glucose'"` and `"'temperature'"` return nothing; numerics 7.0/11.1/3.9/3.0/37.5/38.5/36.0/35.5 all present | verified |
| 8 | Deployment doc states code-before-script order (release alias-aware code first, never reverse) | DATABASE.md §数据迁移与手工修复:「先发布代码，后执行脚本——严禁反向」+ step list + fresh-DB exemption; script header carries the same order (T-12-11) | verified |

**Key link honored:** both `DELETE ... JOIN (SELECT ...)` derived-table forms exist (user_threshold line 103, alert_rule line 191) — the mandatory MySQL 8 form since a table cannot be deleted from and selected from in one subquery.

## Verification

- Task 1 `<automated>`: `grep -v '^--' ... | grep -c 'START TRANSACTION'` = 1 (exactly 1)
- Acceptance: START TRANSACTION|COMMIT = 2; glucose/spo2/blood_oxygen/glu/blood_glucose spellings all present; `grep -ci "flyway"` = 0; both derived-table DELETEs present; updated_at ranking inside the DELETE block; file ends with the AFTER block
- Task 2 `<automated>`: `grep -c "bloodGlucose\|bodyTemperature" schema.sql` = 9 (≥2 beyond pre-existing 0); no bare `'glucose'`/`'temperature'` seed literals remain
- `mvn -f hhs-backend/pom.xml verify -DskipITs` → exit 0, zero [ERROR]/BUILD FAILURE (resource change does not break the build)
- **Script deliberately NOT executed against any database** — creating the file is the deliverable; execution is a manual UAT step (12-VALIDATION §Manual-Only Verifications row 2: record BEFORE counts → run → run again → confirm unchanged totals, no duplicate (user_id, metric_key), alert_rule keys all canonical)

## Deviations from Plan

**1. [Additive - minor] BEFORE/AFTER count SELECTs extended to all four touched tables**
- **Found during:** Task 1
- **Issue:** Plan listed three BEFORE counts (ut_total, ut_noncanonical, ar_noncanonical) but required the AFTER block's "non-canonical counts" (plural) to be zero across the mutation.
- **Fix:** Symmetric per-table counts for alert_template and health_alert added to both BEFORE and AFTER blocks — pure operator-facing audit completeness (T-12-10), no behavioral change to the mutation.
- **Files modified:** fix-threshold-metric-key.sql (count blocks only)
- **Commit:** 9f3be80

No other deviations — plan executed as written.

## Threat Model Mitigations Applied

- T-12-08: single transaction wrap — any duplicate-key abort rolls back completely
- T-12-09: dedupe executes before rename on both unique-indexed tables
- T-12-10: BEFORE/AFTER count brackets printed for the operator's audit record
- T-12-11: code-before-script order in both the script header and DATABASE.md
- T-12-SC: no package installs in this plan

## Known Stubs

None — the script is complete, no placeholders or unwired data paths.

## Self-Check: PASSED

- [x] hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql exists (committed 9f3be80)
- [x] schema.sql seed canonicalization present (committed 1a84b1a)
- [x] docs/architecture/DATABASE.md section present (committed 1a84b1a)
- [x] Both commits found in git log (9f3be80, 1a84b1a)
- [x] commits measured from ledger: 2 (d27a2b7..HEAD)
