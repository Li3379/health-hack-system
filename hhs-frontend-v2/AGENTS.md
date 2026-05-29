<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# hhs-frontend-v2

## Purpose

Vue 3 健康监控前端，提供仪表盘、健康数据可视化、AI 对话、设备管理、实时监控等界面。TypeScript + Vite + Element Plus + Pinia。

## Key Files

- `package.json` — 依赖和脚本
- `vite.config.ts` — Vite 构建配置 (端口 5173，代理 /api 到 localhost:8082)
- `tsconfig.json` — TypeScript 配置
- `eslint.config.js` — ESLint 配置
- `vitest.config.ts` — Vitest 单元测试配置
- `playwright.config.ts` — Playwright E2E 测试配置
- `index.html` — 入口 HTML

## Subdirectories (under `src/`)

### `api/` — API 模块

与后端 controller 1:1 对应: `ai.ts`, `ai-parse.ts`, `alert.ts`, `auth.ts`, `device.ts`, `goals.ts`, `health.ts`, `mood.ts`, `ocr.ts`, `prevention.ts`, `push.ts`, `realtime.ts`, `reminders.ts`, `score.ts`, `screening.ts`, `screening-compare.ts`, `stats.ts`, `threshold.ts`, `user.ts`, `wellness.ts`

### `stores/` — Pinia 状态管理

每个领域一个 store: `ai.ts`, `alert.ts`, `auth.ts`, `floatingAi.ts`, `push.ts`, `realtime.ts`, `theme.ts`, `wellness.ts`

### `views/` — 页面组件，按领域组织

- `dashboard/` — 仪表盘
- `health/` — 健康数据
- `ai/` — AI 分析
- `data-input/` — 数据录入 (含 components/)
- `prevention/` — 预防保健
- `screening/` — 筛查对比
- `realtime/` — 实时监控
- `wellness/` — 健康生活方式
- `user/` — 用户管理
- `settings/` — 系统设置
- `auth/` — 登录注册
- `oauth/` — OAuth 回调
- `goals/` — 目标管理
- `mood/` — 心情追踪
- `reminders/` — 提醒管理

### `components/` — 共享组件

- `ai-floating-ball/` — 浮动 AI 聊天球
- `charts/` — 图表组件
- `device/` — 设备相关组件
- `layout/` — 布局组件
- `HealthScoreCircle.vue` — 健康分数圆环
- `ActivityRings.vue` — 活动圆环
- `SparklineChart.vue` — 迷你折线图

### `composables/` — Vue 组合式函数

- `useDraggable.ts` — 拖拽功能
- `useECharts.ts` — ECharts 图表封装
- `useTilt3D.ts` — 3D 倾斜效果
- `useWebGLBall.ts` — WebGL 球体效果

### `router/` — Vue Router 路由定义

`index.ts` — 17 条路由，含 auth guard (beforeEach 中重定向未认证用户到 /login)，meta.requiresAdmin 控制管理员页面访问

### `types/` — TypeScript 类型

- `api.ts` — 所有 API 类型定义
- `platform.ts` — 平台相关类型

### `utils/` — 工具函数

- `request.ts` — Axios 封装，自动附加 JWT Bearer token，检查 `res.code === 200`，401 触发 `authStore.logout()`
- `storage.ts` — localStorage 封装
- `format.ts` — 数据格式化
- `echarts-theme.ts` — ECharts 主题配置

### `assets/styles/` — 全局样式

### `__tests__/` — Vitest 单元测试

`components/` 子目录，`setup.ts` 测试初始化

## E2E tests (under `e2e/`)

- `ai-input.spec.ts` — AI 输入流程
- `device-sync.spec.ts` — 设备同步
- `ocr-input.spec.ts` — OCR 录入
- `quick-input.spec.ts` — 快速录入
- `wellness.spec.ts` — 健康生活方式
- `fixtures/` — 测试数据

## For AI Agents

- **HTTP 客户端**: `@/utils/request.ts` 封装 Axios，自动附加 JWT，检查 `res.code === 200`
- **路由**: `vue-router` + `createWebHistory`，auth guard 在 beforeEach，meta.requiresAdmin
- **自动导入**: `unplugin-auto-import` + `unplugin-vue-components`，Vue API 和 Element Plus 组件自动导入
- **API 类型**: `ApiResponse<T> = { code: number, message: string, data: T }`
- **状态管理**: Pinia stores，每个领域一个 store
- **组件风格**: `<script setup>` + Composition API
- **代码风格**: 2-space 缩进，LF 换行，UTF-8
- **ECharts 图表**: 使用 `useECharts` composable
- **AI 浮动球**: `ai-floating-ball/` 组件 + `floatingAi` store

## Dependencies

Vue 3, TypeScript, Vite, Element Plus, Pinia, Axios, ECharts, vue-router
