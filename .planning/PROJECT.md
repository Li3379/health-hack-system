# HHS Frontend Reconstruction

## What This Is

基于现有 HHS 后端 API，完全重构前端页面和代码，使用 Vue 3 + Element Plus 技术栈，实现前后端无缝对接。忽略现有前端代码，从零开始构建。

## Core Value

提供完美的用户体验，实现所有后端 API 功能，确保前后端联调零错误。

## Requirements

### Validated

<!-- 已完成并验证的需求 -->

- [x] Phase 1: 认证与用户模块
- [x] Phase 2: 健康数据模块

### Active

<!-- 当前正在构建的范围 -->

- [ ] Phase 3: AI 与评分模块
- [ ] Phase 4: 预防与体检模块
- [ ] Phase 5: 实时与集成测试

### Out of Scope

- 现有前端代码修复
- 后端 API 修改（除非本里程碑需要）

## Context

- 后端：Spring Boot 3.2 + MySQL + Redis + JWT
- 前端技术栈：Vue 3.3.7 + Vite 4.5.0 + Element Plus 2.5.6 + Pinia
- 现有后端 API 已完整实现，包含 10+ 控制器
- 端口：后端 8082，前端 5173
- v1.0 已完成：认证模块、健康数据模块（血糖、血压、心率）
- 本次里程碑新增：保健指标（Wellness）数据管理

## Constraints

- **Tech**: Vue 3 + Element Plus — 现代前端技术栈
- **API**: 完全对接现有后端，不修改后端（本里程碑需要修改以支持 wellness）
- **Quality**: 前后端联调零错误

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 前端完全重构 | 现有前端代码质量问题多 | ✓ Good - 进度顺利 |
| 先构建健康指标再构建保健指标 | 健康指标优先级更高 | ✓ Good - 已完成 |
| 保健指标独立成表 | 与健康指标属性不同，需要分离 | — Pending - 本里程碑验证 |

---

## Current Milestone: v1.1 健康指标与保健指标分离

**Goal:** 分离健康指标（health_metric）和保健指标（wellness_metric），创建独立的 wellness 数据管理功能

**Target features:**
- [ ] 创建 wellness_metric 表分离保健指标数据
- [ ] 新增 /api/wellness REST API 端点
- [ ] 前端分离健康指标和保健指标展示页面
- [ ] 保健指标数据可视化（图表展示）
- [ ] 保健指标阈值告警功能

---

## Previous Milestone: v1.0

**Completed phases:**
- Phase 1: 认证与用户模块 ✓
- Phase 2: 健康数据模块 ✓

---

*Last updated: 2026-03-02 after v1.1 milestone started*
