---
schema_version: 1
open_count: 5
waived_count: 0
fixed_count: 2
total_count: 7
last_updated: 2026-11-10T12:24:41.503Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 12 | deviation | hhs-frontend-v2/src/assets/styles/theme.css |  | UI-SPEC Contract 9 global dialog button-weight rules (.el-dialog .el-button--primary 600/400 + line-height 1.5) deferred: 12-07 scoped them to the page plan; 12-08 applied only the page-level portion within its files_modified — the wizard teleports to body so its buttons still render at theme.css 500 14px/1 until a theme.css-scoped change lands | fixed |  | 2026-11-08T11:24:35.513Z | 2026-11-08T11:29:01.155Z |
| 2 | 12 | deviation | hhs-frontend-v2/src/assets/styles/theme.css |  | UI-SPEC Contract 9 global dialog button-weight rules (.el-dialog .el-button--primary 600/400 + line-height 1.5) deferred: 12-07 scoped them to the page plan; 12-08 applied only the page-level portion within its files_modified - the wizard teleports to body so its buttons still render at theme.css 500 14px/1 until a theme.css-scoped change lands | fixed |  | 2026-11-08T11:24:44.767Z | 2026-11-08T11:25:43.437Z |
| 3 | 13 | unrun-verify | hhs-backend/src/test/java/com/hhs/integration/PreventionIntegrationTest.java |  | PreventionIntegrationTest not run: Testcontainers cannot reach the Docker daemon in this environment (Failsafe excludes it from Surefire); pre-existing, also fails for HealthScoreIntegrationTest | open |  | 2026-11-09T12:07:34.828Z |  |
| 4 | 13 | unrun-verify | hhs-frontend-v2/src/components/common/EstimateBadge.vue |  | Backstop: verify the estimate badge label does not wrap at a 320px viewport | open |  | 2026-11-09T13:09:53.236Z |  |
| 5 | 13 | unrun-verify | hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue |  | Backstop: verify the banner's two buttons plus close control do not collide at 640px or 768px | open |  | 2026-11-09T13:10:01.346Z |  |
| 6 | 13 | unrun-verify | hhs-frontend-v2/src/views/prevention/Profile.vue |  | Hint wrapping and the 639px label-position flip cannot be measured in happy-dom; CSS rules implemented, visual review pending at 320-639px | open |  | 2026-11-09T13:27:01.200Z |  |
| 7 | 14 | unrun-verify | hhs-frontend-v2/src/assets/styles/theme.css |  | UI-SPEC 4.2 four computed-style self-checks (errored input border-color resolves to the token, textarea shadow contains inset, 3px tint halo present with border still red for all three control families, halo gone after blur) are deferred to the phase UAT in 14-14 — happy-dom returns empty strings for getComputedStyle | open |  | 2026-11-10T12:24:41.503Z |  |

````json
[
  {
    "id": 1,
    "kind": "deviation",
    "phase": "12",
    "file": "hhs-frontend-v2/src/assets/styles/theme.css",
    "line": null,
    "description": "UI-SPEC Contract 9 global dialog button-weight rules (.el-dialog .el-button--primary 600/400 + line-height 1.5) deferred: 12-07 scoped them to the page plan; 12-08 applied only the page-level portion within its files_modified — the wizard teleports to body so its buttons still render at theme.css 500 14px/1 until a theme.css-scoped change lands",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-11-08T11:24:35.513Z",
    "resolved_at": "2026-11-08T11:29:01.155Z",
    "milestone": "v1.3"
  },
  {
    "id": 2,
    "kind": "deviation",
    "phase": "12",
    "file": "hhs-frontend-v2/src/assets/styles/theme.css",
    "line": null,
    "description": "UI-SPEC Contract 9 global dialog button-weight rules (.el-dialog .el-button--primary 600/400 + line-height 1.5) deferred: 12-07 scoped them to the page plan; 12-08 applied only the page-level portion within its files_modified - the wizard teleports to body so its buttons still render at theme.css 500 14px/1 until a theme.css-scoped change lands",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-11-08T11:24:44.767Z",
    "resolved_at": "2026-11-08T11:25:43.437Z",
    "milestone": "v1.3"
  },
  {
    "id": 3,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "hhs-backend/src/test/java/com/hhs/integration/PreventionIntegrationTest.java",
    "line": null,
    "description": "PreventionIntegrationTest not run: Testcontainers cannot reach the Docker daemon in this environment (Failsafe excludes it from Surefire); pre-existing, also fails for HealthScoreIntegrationTest",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-11-09T12:07:34.828Z",
    "resolved_at": null,
    "milestone": "v1.3"
  },
  {
    "id": 4,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "hhs-frontend-v2/src/components/common/EstimateBadge.vue",
    "line": null,
    "description": "Backstop: verify the estimate badge label does not wrap at a 320px viewport",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-11-09T13:09:53.236Z",
    "resolved_at": null,
    "milestone": "v1.3"
  },
  {
    "id": 5,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue",
    "line": null,
    "description": "Backstop: verify the banner's two buttons plus close control do not collide at 640px or 768px",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-11-09T13:10:01.346Z",
    "resolved_at": null,
    "milestone": "v1.3"
  },
  {
    "id": 6,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "hhs-frontend-v2/src/views/prevention/Profile.vue",
    "line": null,
    "description": "Hint wrapping and the 639px label-position flip cannot be measured in happy-dom; CSS rules implemented, visual review pending at 320-639px",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-11-09T13:27:01.200Z",
    "resolved_at": null,
    "milestone": "v1.3"
  },
  {
    "id": 7,
    "kind": "unrun-verify",
    "phase": "14",
    "file": "hhs-frontend-v2/src/assets/styles/theme.css",
    "line": null,
    "description": "UI-SPEC 4.2 four computed-style self-checks (errored input border-color resolves to the token, textarea shadow contains inset, 3px tint halo present with border still red for all three control families, halo gone after blur) are deferred to the phase UAT in 14-14 — happy-dom returns empty strings for getComputedStyle",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-11-10T12:24:41.503Z",
    "resolved_at": null,
    "milestone": "v1.3"
  }
]
````
