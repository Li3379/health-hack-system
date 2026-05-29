<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# components/

**Purpose**: 共享 Vue 组件，跨页面复用的 UI 组件。

**Key Files**:
- `HealthScoreCircle.vue` — 健康分数圆环组件 (6 symbols)
- `ActivityRings.vue` — 活动圆环组件 (17 symbols，类似 Apple Watch)
- `SparklineChart.vue` — 迷你折线图组件 (11 symbols)

**Subdirectories**:
- `ai-floating-ball/` — 浮动 AI 聊天球组件 (全局悬浮，点击展开聊天)
- `charts/` — 图表组件
- `device/` — 设备相关组件
- `layout/` — 布局组件 (导航栏、侧边栏、页脚)

**For AI Agents**:
- 使用 `<script setup lang="ts">`
- Props 定义: `defineProps<Props>()`
- Emits 定义: `defineEmits<Emits>()`
- 样式: scoped CSS，使用 Element Plus 设计变量
