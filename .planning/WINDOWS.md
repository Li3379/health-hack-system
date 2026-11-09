---
schema_version: 1
open_count: 1
waived_count: 0
fixed_count: 2
total_count: 3
last_updated: 2026-11-09T12:07:34.828Z
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
  }
]
````
