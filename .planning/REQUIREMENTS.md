# Requirements: HHS v1.1

**Defined:** 2026-03-02
**Core Value:** Users can track health data and wellness data in unified platform with AI insights

## v1 Requirements

### Metric Categories

- [ ] **MC-01**: System distinguishes between medical (health) metrics and wellness metrics
- [ ] **MC-02**: Medical metrics: blood pressure, heart rate, glucose, BMI, temperature, weight
- [ ] **MC-03**: Wellness metrics: sleep duration, sleep quality, steps, exercise minutes, water intake, mood/energy

### Wellness Data Recording

- [ ] **WELL-01**: User can record sleep duration (hours)
- [ ] **WELL-02**: User can record sleep quality (1-5 scale)
- [ ] **WELL-03**: User can record daily steps
- [ ] **WELL-04**: User can record exercise minutes
- [ ] **WELL-05**: User can record water intake (glasses/ml)
- [ ] **WELL-06**: User can record mood/energy level (1-5 scale)

### Wellness Display

- [ ] **WELL-07**: User can view wellness metrics dashboard
- [ ] **WELL-08**: Wellness metrics display with trends (weekly/monthly)

### Health Score Integration

- [ ] **WELL-09**: Wellness metrics contribute to AI health score calculation
- [ ] **WELL-10**: Health score displays contribution from both medical and wellness data

### Backend API

- [ ] **API-01**: New wellness metric endpoints (GET/POST /api/wellness/*)
- [ ] **API-02**: Health score calculation includes wellness data factors

## Out of Scope

| Feature | Reason |
|---------|--------|
| Wearable device sync | Requires hardware partnerships |
| Social sharing | Privacy concerns for v1.x |
| Advanced sleep analysis | Requires more complex algorithms |

---
*Requirements defined: 2026-03-02*
