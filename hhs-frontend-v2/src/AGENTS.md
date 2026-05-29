<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# hhs-frontend-v2/src

## Purpose

Vue 3 前端主源码目录，包含所有页面、组件、状态管理、API 调用、工具函数。

## Key Files

- `main.ts` — 应用入口，创建 Vue app 并安装插件 (Pinia, Router, Element Plus)
- `App.vue` — 根组件
- `vite-env.d.ts` — Vite 类型声明

## Subdirectories

### `api/` — API 模块

与后端 controller 1:1 对应，共 20 个文件: `ai.ts`, `ai-parse.ts`, `alert.ts`, `auth.ts`, `device.ts`, `goals.ts`, `health.ts`, `mood.ts`, `ocr.ts`, `prevention.ts`, `push.ts`, `realtime.ts`, `reminders.ts`, `score.ts`, `screening.ts`, `screening-compare.ts`, `stats.ts`, `threshold.ts`, `user.ts`, `wellness.ts`

[see AGENTS.md](api/AGENTS.md)

### `stores/` — Pinia 状态管理

每个领域一个 store，共 8 个: `ai.ts`, `alert.ts`, `auth.ts`, `floatingAi.ts`, `push.ts`, `realtime.ts`, `theme.ts`, `wellness.ts`

[see AGENTS.md](stores/AGENTS.md)

### `views/` — 页面组件

按领域组织，共 15 个子目录:

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

[see AGENTS.md](views/AGENTS.md)

### `components/` — 共享组件

- `ai-floating-ball/` — 浮动 AI 聊天球
- `charts/` — 图表组件
- `device/` — 设备相关组件
- `layout/` — 布局组件
- `HealthScoreCircle.vue` — 健康分数圆环
- `ActivityRings.vue` — 活动圆环
- `SparklineChart.vue` — 迷你折线图

[see AGENTS.md](components/AGENTS.md)

### `composables/` — Vue 组合式函数

- `useDraggable.ts` — 拖拽功能
- `useECharts.ts` — ECharts 图表封装
- `useTilt3D.ts` — 3D 倾斜效果
- `useWebGLBall.ts` — WebGL 球体效果

### `router/` — 路由配置

`index.ts` — 17 条路由，含 auth guard (beforeEach 中重定向未认证用户到 /login)，meta.requiresAdmin 控制管理员页面访问

### `types/` — TypeScript 类型定义

- `api.ts` — 所有 API 类型定义
- `platform.ts` — 平台相关类型

### `utils/` — 工具函数

- `request.ts` — Axios 封装，自动附加 JWT Bearer token，检查 `res.code === 200`，401 触发 `authStore.logout()`
- `storage.ts` — localStorage 封装
- `format.ts` — 数据格式化
- `echarts-theme.ts` — ECharts 主题配置

### `assets/styles/` — 全局样式

### `__tests__/` — 单元测试

Vitest 单元测试，`components/` 子目录，`setup.ts` 测试初始化

## For AI Agents

- **新增页面**: 在 `views/` 创建 `.vue` 文件 -> 在 `router/index.ts` 添加路由 -> (可选) 创建 `api/` 模块和 `stores/`
- **新增 API 调用**: 在 `api/` 创建 `.ts` 文件，使用 `request` from `@/utils/request.ts`
- **新增共享组件**: 在 `components/` 创建 `.vue` 文件
- **组件风格**: `<script setup lang="ts">` + Composition API
- **自动导入**: 不需要手动 import ref/computed/watch 等 Vue API
- **样式**: 使用 Element Plus 组件，scoped CSS
- **HTTP 客户端**: `@/utils/request.ts` 封装 Axios，自动附加 JWT，检查 `res.code === 200`
- **路由**: `vue-router` + `createWebHistory`，auth guard 在 beforeEach，meta.requiresAdmin
- **API 类型**: `ApiResponse<T> = { code: number, message: string, data: T }`
- **状态管理**: Pinia stores，每个领域一个 store
- **代码风格**: 2-space 缩进，LF 换行，UTF-8
- **ECharts 图表**: 使用 `useECharts` composable
- **AI 浮动球**: `ai-floating-ball/` 组件 + `floatingAi` store

## Dependencies

Vue 3, TypeScript, Vite, Element Plus, Pinia, Axios, ECharts, vue-router
