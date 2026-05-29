<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# views/ — 页面级 Vue 组件

**Purpose**: 页面级 Vue 组件，按业务领域组织，每个目录对应一个功能模块的页面。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `dashboard/` | 主仪表盘页面 |
| `health/` | 健康数据查看和管理 |
| `ai/` | AI 分析结果展示 |
| `data-input/` | 数据录入页面 (含 `components/` 子组件) |
| `prevention/` | 预防保健建议 |
| `screening/` | 健康筛查和对比 (含 Compare.vue) |
| `realtime/` | 实时健康监控 |
| `wellness/` | 健康生活方式 (运动、饮食、睡眠) |
| `user/` | 用户资料管理 |
| `settings/` | 系统设置 (推送配置、阈值设置) |
| `auth/` | 登录/注册页面 |
| `oauth/` | OAuth 回调处理 (OAuthCallback.vue) |
| `goals/` | 健康目标管理 |
| `mood/` | 心情追踪 |
| `reminders/` | 提醒管理 |

## For AI Agents

- 页面组件使用 `<script setup lang="ts">`
- 新增页面: 创建目录和 `.vue` 文件，然后在 `router/index.ts` 添加路由
- 页面通常使用 Element Plus 布局组件 (`el-container`, `el-card` 等)
- 数据加载: 在 `onMounted` 中调用 `api/` 模块
- 状态管理: 使用 `stores/` 中的 Pinia store
- 遵循现有页面的组件结构和样式模式
