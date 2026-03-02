# Phase 2: 健康数据模块 - Context

**Gathered:** 2026-03-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Complete health metrics management (CRUD), alert notifications, and personal threshold settings. This includes:
- Add/view/edit/delete health metrics (glucose, BP, heart rate, weight, temperature)
- Chart trend display for metrics
- Alert list, filters, mark read/confirm actions
- Personal threshold configuration per metric type

**Out of Scope:** AI health scoring (Phase 3), Prevention records (Phase 4), Real-time WebSocket (Phase 5)

</domain>

<decisions>
## Implementation Decisions

### Metrics List UI
- Table view with sortable columns (date, type, value, status)
- Each row shows: timestamp, metric type, value + unit, status indicator
- Inline actions: edit (opens modal), delete (with confirmation)
- Filter by metric type and date range
- Pagination (20 per page)

### Chart Display
- Use existing TrendChart.vue component
- Time range options: 24h, 7d, 30d, 90d
- Line chart for trends
- Shows threshold zones (warning/danger ranges) as background bands
- Interactive: click point to see details

### Threshold Settings
- Inline editing on metrics list or separate settings icon
- Per-metric configuration: min/max warning, min/max danger thresholds
- Enable/disable toggle per metric
- Visual preview showing where current values fall in threshold zones
- Save persists to backend

### Alert Actions
- Keep existing AlertCenter functionality
- Add: confirm action (marks as acknowledged)
- No delete for now (alerts are historical)
- Badge shows unread count in header

### Data Entry
- Keep existing DataEntry.vue form
- Supported types: heartRate, systolicBP, diastolicBP, glucose, weight, temperature
- Auto-save draft to localStorage
- Validation: numeric range per metric type

</decisions>

<specifics>
## Specific Ideas

**From existing code analysis:**
- DataEntry.vue already exists with form for adding metrics
- AlertCenter.vue already has filters (type, read status), mark all read
- TrendChart.vue component exists - should be reused
- health.ts store uses realtimeApi for fetching metrics

**No specific references provided** — open to standard approaches

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- **TrendChart.vue** - Chart component to reuse for metric trends
- **AlertItem.vue** - Alert display component
- **DataEntry.vue** - Existing form for adding metrics (refactor/reuse)
- **health.ts store** - Already fetches latestMetrics and trendData

### Established Patterns
- **UI Framework:** Element Plus (el-table, el-card, el-form)
- **State Management:** Pinia stores
- **API Pattern:** Separate API files per domain (alert.ts, healthScore.ts)
- **Types:** HealthMetric, HealthAlert, UserThreshold already defined in types/health.ts

### Integration Points
- **Routes:** Need new routes for /metrics (list) and /thresholds (settings)
- **Store:** Extend health.ts with CRUD actions for metrics
- **API:** Need metrics.ts API file (currently using realtime.ts)

</code_context>

<deferred>
## Deferred Ideas

- None — all items discussed are within Phase 2 scope

</deferred>

---

*Phase: 02-health-data*
*Context gathered: 2026-03-02*
