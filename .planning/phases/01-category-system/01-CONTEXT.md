# Phase 1: Category System - Context

**Gathered:** 2026-03-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement metric category classification system to distinguish medical (health) metrics from wellness metrics. This includes:
- Category enum definition in backend
- Database schema changes
- Proper categorization of existing health metrics

</domain>

<decisions>
## Implementation Decisions

### Category Storage
- Use database enum type (MySQL ENUM or dedicated categories table)
- Allow future extension for custom categories

### Migration Strategy
- Default existing metrics to "health" category
- Wellness metrics added in Phase 2

### API Design
- Category included in metric response
- Separate wellness endpoints in Phase 2

### Claude's Discretion
- Exact enum values and names
- Database column naming conventions
- Migration approach details

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches following existing codebase patterns.

</specifics>

<deferred>
## Deferred Ideas

- Custom user-defined categories — Phase 2+
- Category-specific thresholds — Phase 2+

</deferred>

---

*Phase: 01-category-system*
*Context gathered: 2026-03-02*
