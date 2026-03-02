# HHS (Health Hack System)

## What This Is

HHS is a health monitoring platform with Spring Boot backend and Vue 3 frontend. The system provides health metric tracking, real-time monitoring, alerts, AI-powered health scoring, and preventive health assessments.

## Core Value

Users can track their health data (medical metrics like blood pressure, glucose) and wellness data (lifestyle metrics like steps, sleep) in one unified platform with AI-powered insights and alerts.

## Requirements

### Validated

- ✓ User authentication and profile management — v1.0
- ✓ Medical health metrics recording (heart rate, BP, glucose, BMI, temperature, weight) — v1.0
- ✓ Health alerts with threshold-based notifications — v1.0
- ✓ AI health score calculation — v1.0
- ✓ Risk assessment and prevention recommendations — v1.0

### Active

- [ ] **WELL-01**: Separate wellness metrics from health metrics with distinct categories
- [ ] **WELL-02**: Implement wellness metric types (sleep, steps, exercise, water intake)
- [ ] **WELL-03**: Add wellness data recording functionality
- [ ] **WELL-04**: Integrate wellness metrics into health score calculation

### Out of Scope

- Wearable device integration — requires hardware partnerships
- Social sharing of health data — privacy concerns for v1.x

## Context

**Current State:**
- Backend: Spring Boot 3.2, Java 17, MySQL 8, Redis 7
- Frontend: Vue 3.3, Element Plus 2.5, Vite 4.5
- Current health metrics are stored in single `health_metric` table with generic key-value structure

**The Problem:**
Health metrics (医学指标) and wellness metrics (保健指标) are currently mixed. Medical metrics (BP, glucose, heart rate) have clinical significance and require alerting thresholds. Wellness metrics (sleep, steps, exercise) are lifestyle data without critical alerts but valuable for overall health insights.

## Current Milestone: v1.1 健康指标与保健指标分离

**Goal:** Separate health metrics (医学指标) from wellness metrics (保健指标), implement wellness tracking features, and integrate wellness data into health scoring.

**Target features:**
- Distinct category system for medical vs wellness metrics
- Wellness metric types: sleep, steps, exercise minutes, water intake, mood/energy
- Wellness data recording and display
- Updated health score calculation including wellness data
- Separate UI for wellness tracking

## Constraints

- **Tech Stack**: Spring Boot 3.2, Vue 3 — No changes needed
- **Database Schema**: Need backward compatibility with existing health_metric table
- **Data Migration**: Must preserve existing health data

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Separate tables for wellness metrics | Different data patterns, easier management | — Pending |
| Wellness metrics in health score | Encourages holistic health tracking | — Pending |
| Metric category enum | Distinguish metric types programmatically | — Pending |

---
*Last updated: 2026-03-02 after v1.1 milestone started*
