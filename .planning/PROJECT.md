# HHS Frontend Reconstruction

## What This Is

基于现有 HHS 后端 API，完全重构前端页面和代码，使用 Vue 3 + Element Plus 技术栈，实现前后端无缝对接。忽略现有前端代码，从零开始构建。

## Core Value

提供完美的用户体验，实现所有后端 API 功能，确保前后端联调零错误。

## Requirements

### Active

- [ ] 完成前端项目初始化（Vue 3 + Vite + Element Plus）
- [ ] 实现用户认证模块（登录、注册、JWT 令牌管理）
- [ ] 实现用户资料管理（查看、修改、头像上传）
- [ ] 实现健康指标模块（血糖、血压、心率数据 CRUD + 图表展示）
- [ ] 实现健康预警模块（阈值告警、消息通知）
- [ ] 实现个人阈值设置模块
- [ ] 实现健康评分模块（AI 评分、趋势分析）
- [ ] 实现 AI 健康顾问模块（对话、报告解读）
- [ ] 实现预防保健模块（健康档案、风险评估）
- [ ] 实现体检报告 OCR 模块（上传、OCR 识别、结果展示）
- [ ] 实现 WebSocket 实时数据推送
- [ ] 前后端联调测试，确保零错误

### Out of Scope

- 现有前端代码修复
- 后端 API 修改

## Context

- 后端：Spring Boot 3.2 + MySQL + Redis + JWT
- 前端技术栈：Vue 3.3.7 + Vite 4.5.0 + Element Plus 2.5.6 + Pinia
- 现有后端 API 已完整实现，包含 10+ 控制器
- 端口：后端 8082，前端 5173

## Constraints

- **Tech**: Vue 3 + Element Plus — 现代前端技术栈
- **API**: 完全对接现有后端，不修改后端
- **Quality**: 前后端联调零错误

---

*Last updated: 2026-03-02 after initialization*
