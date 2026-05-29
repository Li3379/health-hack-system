<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# stores/ — Pinia 状态管理

**Purpose**: Pinia 状态管理 stores，每个 store 管理一个业务领域的全局状态。

## Key Files

| File | Purpose | Symbols |
|------|---------|---------|
| `auth.ts` | 认证状态 (token, user, login/logout, token 刷新) | 10 |
| `ai.ts` | AI 分析状态 | 8 |
| `alert.ts` | 告警状态 | 6 |
| `floatingAi.ts` | 浮动 AI 球状态 (聊天历史、开关、消息发送) | 9 |
| `push.ts` | 推送配置状态 | 7 |
| `realtime.ts` | 实时监控状态 | 6 |
| `theme.ts` | 主题状态 (暗色模式、颜色切换) | 8 |
| `wellness.ts` | 健康生活方式状态 | 7 |

## For AI Agents

- 使用 `defineStore` + setup 语法 (Composition API 风格)
- 新增 store: 创建 `.ts` 文件，使用 `defineStore('name', () => {...})`
- store 之间可互相引用
- 持久化: auth store 使用 localStorage 保存 token
- 遵循现有 store 的状态定义和 action 模式
