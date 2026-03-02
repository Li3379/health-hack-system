# Roadmap: v1.1 健康指标与保健指标分离

## Overview

**Milestone:** v1.1
**Goal:** Separate health metrics from wellness metrics, implement wellness tracking

| Phase | Name | Goal | Requirements | Success Criteria |
|-------|------|------|--------------|------------------|
| 1 | Category System | Implement metric category enum and database schema | MC-01, MC-02, MC-03 | 3 |
| 2 | Backend Wellness API | Create wellness data endpoints | WELL-01-WELL-06, API-01 | 8 |
| 3 | Wellness Dashboard UI | Build frontend wellness tracking UI | WELL-07, WELL-08 | 2 |
| 4 | Health Score Integration | Update AI scoring with wellness data | WELL-09, WELL-10, API-02 | 3 |

## Phase 1: Category System

**Goal:** Implement metric category classification system

Requirements: MC-01, MC-02, MC-03

Success Criteria:
1. Metric category enum defined in backend
2. Database migration adds category field
3. Existing health metrics properly categorized

## Phase 2: Backend Wellness API

**Goal:** Create API endpoints for wellness data management

Requirements: WELL-01 through WELL-06, API-01

Success Criteria:
1. POST /api/wellness supports all wellness metric types
2. GET /api/wellness returns user's wellness data
3. Wellness data stored in separate or categorized table

## Phase 3: Wellness Dashboard UI

**Goal:** Build frontend for wellness data visualization

Requirements: WELL-07, WELL-08

Success Criteria:
1. Wellness dashboard displays all metric types
2. Trend charts show weekly/monthly data

## Phase 4: Health Score Integration

**Goal:** Update health score to include wellness factors

Requirements: WELL-09, WELL-10, API-02

Success Criteria:
1. Health score algorithm includes wellness metrics
2. User sees wellness contribution in score breakdown

---
*Roadmap created: 2026-03-02*
