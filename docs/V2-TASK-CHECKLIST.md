# HHS v2.0 任务清单与验收标准
*Generated: 2026-05-27 | 26 tasks across 5 directions | Structured for /using-superpowers rapid development*

---

## 使用方式

每个任务都是独立可执行的单元。开发时：
1. 选择一个任务
2. 按验收标准实现
3. 逐条检查验收条件
4. 完成后标记 ✅

---

## A. 前端设计统一

### A1. 修复 realtime/Monitor.vue 设计
**文件：** `hhs-frontend-v2/src/views/realtime/Monitor.vue`
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] 替换所有 `#67c23a` 为 `var(--color-health-excellent)`
- [ ] 替换所有 `#e6a23c` 为 `var(--color-health-fair)`
- [ ] 替换所有 `#f56c6c` 为 `var(--color-health-poor)`
- [ ] 替换所有 `#303133` 为 `var(--text-1)`
- [ ] 替换所有 `#606266` 为 `var(--text-2)`
- [ ] 替换所有 `#909399` 为 `var(--text-3)`
- [ ] 统计卡片添加 `class="glass-card"` 或应用 `var(--glass-bg)` + `var(--glass-blur)` + `var(--glass-border)`
- [ ] 页面容器添加 `IntersectionObserver` scroll reveal 动画（参考 `dashboard/Index.vue` 的 `.reveal` 实现）
- [ ] ECharts 图表颜色从硬编码改为 `var(--accent-cool)` 等 theme token
- [ ] 连接状态指示器颜色使用 theme tokens

**验收标准：**
- [ ] 页面中零个硬编码 hex 颜色值（`#xxx` 或 `#xxxxxx`）
- [ ] 所有统计卡片有毛玻璃半透明效果
- [ ] 页面加载时卡片有渐入动画
- [ ] 暗色/亮色主题切换时颜色正确跟随
- [ ] ECharts 图表颜色与主题一致
- [ ] `npm run build` 无报错

---

### A2. 修复 wellness/Dashboard.vue 设计
**文件：** `hhs-frontend-v2/src/views/wellness/Dashboard.vue`
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] 替换所有 `#303133` 为 `var(--text-1)`
- [ ] 替换所有 `#606266` 为 `var(--text-2)`
- [ ] 替换所有 `#909399` 为 `var(--text-3)`
- [ ] 替换所有 `#67c23a` 趋势色为 `var(--color-health-excellent)`
- [ ] 替换所有 `#f56c6c` 趋势色为 `var(--color-health-poor)`
- [ ] 统计卡片应用毛玻璃效果
- [ ] 添加页面入场 stagger reveal 动画
- [ ] ECharts 图表颜色使用 theme tokens

**验收标准：**
- [ ] 页面中零个 Element Plus 默认色硬编码
- [ ] 所有卡片有毛玻璃效果
- [ ] 卡片入场有 stagger 渐入动画
- [ ] 暗色/亮色主题切换正常
- [ ] `npm run build` 无报错

---

### A3. 修复 health/Score.vue 设计 + 复用 HealthScoreCircle
**文件：** `hhs-frontend-v2/src/views/health/Score.vue`
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] 替换所有硬编码健康等级颜色为 `var(--color-health-excellent/good/fair/poor)`
- [ ] 用 `HealthScoreCircle` 组件替换当前的纯文本 `.score-circle` 数字显示
- [ ] ECharts 颜色从 `#409eff` 改为 `var(--accent-cool)`
- [ ] 因子颜色使用 `var(--color-health-*)` tokens 替代硬编码

**验收标准：**
- [ ] 页面顶部显示 SVG 动画分数环（与 Dashboard 一致的 `HealthScoreCircle`）
- [ ] 健康等级颜色通过 CSS 变量定义，非硬编码
- [ ] ECharts 图表使用主题 accent 色
- [ ] 暗色/亮色主题切换正常
- [ ] `npm run build` 无报错

---

### A4. 激活 3d-enhance.css
**文件：** `hhs-frontend-v2/src/assets/styles/3d-enhance.css`, `hhs-frontend-v2/src/main.ts`
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] 删除 `3d-enhance.css` 第 9 行：`#app { outline: 3px solid red !important; }`
- [ ] 在 `main.ts` 中添加 `import './assets/styles/3d-enhance.css'`
- [ ] 验证 3D 卡片效果（`.stat-card`, `.entry-item` hover 效果）
- [ ] 验证页面过渡动画（`.page-transition`）
- [ ] 验证与 `theme.css` 的兼容性（无样式冲突）

**验收标准：**
- [ ] 应用无红色边框
- [ ] 卡片 hover 有 3D 透视效果
- [ ] 页面切换有过渡动画
- [ ] 暗色/亮色主题下 3D 效果正常
- [ ] 无 CSS 冲突导致的布局异常
- [ ] `npm run build` 无报错

---

### A5. 统一浮动球 CSS 变量
**文件：** `hhs-frontend-v2/src/components/ai-floating-ball/FloatingBallButton.vue`, `hhs-frontend-v2/src/components/ai-floating-ball/FloatingChatPanel.vue`
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] `FloatingChatPanel.vue` 中替换变量映射：
  - `var(--color-surface)` → `var(--surface-1)`
  - `var(--color-border-light)` → `var(--border)`
  - `var(--color-text-primary)` → `var(--text-1)`
  - `var(--color-text-secondary)` → `var(--text-2)`
  - `var(--color-bg-secondary)` → `var(--surface-2)`
  - `var(--spacing-md)` → `var(--sp-4)`
  - `var(--spacing-lg)` → `var(--sp-6)`
  - `var(--font-size-sm)` → `0.875rem`
  - `var(--transition-colors)` → `var(--dur-fast) var(--ease)`
- [ ] `FloatingBallButton.vue` 中同步替换（如有相同变量）

**验收标准：**
- [ ] 浮动球面板在暗色主题下背景、文字、边框颜色正确
- [ ] 亮色主题切换后面板样式正确
- [ ] 面板打开/关闭动画正常
- [ ] `npm run build` 无报错

---

### A6. 审查其余页面设计
**文件：** `health/Alerts.vue`, `health/Metrics.vue`, `health/Thresholds.vue`, `screening/List.vue`, `screening/Detail.vue`, `settings/*`, `user/*`
**预估：** 1 天
**依赖：** A1-A3 完成后作为参考模板

**任务清单：**
- [ ] 审查 `health/Alerts.vue` — 检查硬编码颜色
- [ ] 审查 `health/Metrics.vue` — 检查硬编码颜色
- [ ] 审查 `health/Thresholds.vue` — 检查硬编码颜色
- [ ] 审查 `screening/List.vue` — 检查硬编码颜色
- [ ] 审查 `screening/Detail.vue` — 检查硬编码颜色
- [ ] 审查 `settings/` 下所有页面
- [ ] 审查 `user/` 下所有页面
- [ ] 按 A1-A3 模式统一修复

**验收标准：**
- [ ] 全站所有 `.vue` 文件中无 Element Plus 默认色硬编码（`#303133`, `#606266`, `#909399`, `#67c23a`, `#f56c6c`, `#e6a23c`）
- [ ] 全站所有页面主题切换正常
- [ ] `npm run build` 无报错

---

## B. 数据可视化升级

### B1. 创建 useECharts Composable
**文件：** 新建 `hhs-frontend-v2/src/composables/useECharts.ts`
**修改：** `wellness/Dashboard.vue`, `realtime/Monitor.vue`, `health/Score.vue`
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] 创建 `useECharts.ts` composable，封装：
  - `init(dom, theme?)` — 初始化图表实例
  - 自动注册 `ResizeObserver` 监听容器大小变化
  - 组件卸载时自动 `dispose()`
  - 暗色主题配置（坐标轴、网格、tooltip 样式）
  - `setOption(option)` — 设置图表配置
  - `updateOption(option)` — 增量更新
- [ ] 重构 `wellness/Dashboard.vue` 使用 `useECharts`
- [ ] 重构 `realtime/Monitor.vue` 使用 `useECharts`
- [ ] 重构 `health/Score.vue` 使用 `useECharts`

**验收标准：**
- [ ] composable 导出 `useECharts()` 函数
- [ ] 三个视图中不再有重复的 `echarts.init()` / `dispose()` / `resize` 代码
- [ ] 图表在窗口 resize 时自动适配
- [ ] 组件卸载时图表正确销毁（无内存泄漏）
- [ ] `npm run build` 无报错
- [ ] 现有图表功能无回归

---

### B2. 健康分数雷达图
**文件：** `hhs-frontend-v2/src/views/health/Score.vue`
**预估：** 1 天
**依赖：** B1 (useECharts), C1 (分数历史数据)

**任务清单：**
- [ ] 在 `health/Score.vue` 中添加 ECharts RadarChart
- [ ] 雷达图维度：心血管、代谢、体重、生活方式（从 `scoreFactors` 数据获取）
- [ ] 支持"本周均值 vs 上周均值"对比叠加（两条雷达线）
- [ ] 使用 theme token 颜色
- [ ] 配合 `HealthScoreCircle` 形成完整的分数可视化区域

**验收标准：**
- [ ] 雷达图显示 4+ 个维度
- [ ] 有当前分数和对比分数两条线
- [ ] 颜色使用 theme tokens
- [ ] 响应式布局（移动端不溢出）
- [ ] `npm run build` 无报错

---

### B3. 健康分数仪表盘
**文件：** `hhs-frontend-v2/src/views/health/Score.vue`
**预估：** 0.5 天
**依赖：** B1

**任务清单：**
- [ ] 在分数区域添加 ECharts GaugeChart
- [ ] 动态颜色映射：0-40 poor(红), 40-60 fair(黄), 60-80 good(绿), 80-100 excellent(青)
- [ ] 分数变化时有动画过渡效果
- [ ] 仪表盘下方显示分数数值和等级文字

**验收标准：**
- [ ] 仪表盘颜色随分数动态变化
- [ ] 分数更新时有平滑动画过渡
- [ ] 与 HealthScoreCircle 视觉协调
- [ ] `npm run build` 无报错

---

### B4. Dashboard 迷你趋势线（Sparklines）
**文件：** `hhs-frontend-v2/src/views/dashboard/Index.vue`
**预估：** 1 天
**依赖：** B1, C5 (统计趋势接口)

**任务清单：**
- [ ] 在 Dashboard 统计卡片中添加 ECharts 迷你折线图
- [ ] 配置：无坐标轴、无标签、无 tooltip，仅数据线
- [ ] 用于：心率趋势、步数趋势、睡眠趋势、分数趋势
- [ ] 线条颜色使用对应 metric 的 theme token
- [ ] 数据从 `/api/stats/trends` 接口获取

**验收标准：**
- [ ] 每个统计卡片下方有迷你折线图
- [ ] 折线图无坐标轴和标签，仅展示趋势走向
- [ ] 线条颜色与卡片主题色一致
- [ ] 数据为空时优雅降级（不显示折线图）
- [ ] `npm run build` 无报错

---

### B5. 实时监控仪表盘
**文件：** `hhs-frontend-v2/src/views/realtime/Monitor.vue`
**预估：** 1 天
**依赖：** B1

**任务清单：**
- [ ] 在实时监控页面添加 ECharts GaugeChart 展示当前心率
- [ ] 血压使用双仪表盘（收缩压/舒张压）
- [ ] 仪表盘数据通过 WebSocket 实时更新
- [ ] 异常值自动变色（心率 > 100 变红）

**验收标准：**
- [ ] 心率仪表盘实时更新
- [ ] 血压双仪表盘正确显示
- [ ] 异常值颜色变化正确
- [ ] WebSocket 断开时仪表盘显示"无数据"状态
- [ ] `npm run build` 无报错

---

### B6. 活动环组件（Activity Rings）
**文件：** 新建 `hhs-frontend-v2/src/components/ActivityRings.vue`
**预估：** 1 天
**依赖：** 无

**任务清单：**
- [ ] 创建 `ActivityRings.vue` 组件
- [ ] SVG 实现，3 个同心环：运动（红）、睡眠（蓝）、健康习惯（绿）
- [ ] 每个环有进度百分比，CSS 动画填充
- [ ] Props：`movePercent`, `exercisePercent`, `standPercent`, `size`
- [ ] 环的间隙、粗细、颜色可通过 props 配置
- [ ] 支持 `prefers-reduced-motion` 无障碍

**验收标准：**
- [ ] 3 个同心环正确渲染
- [ ] 进度动画从 0 填充到目标值
- [ ] `prefers-reduced-motion` 下无动画
- [ ] 组件可被 Dashboard 和 Wellness 页面复用
- [ ] `npm run build` 无报错

---

### B7. ECharts 暗色主题配置
**文件：** 新建 `hhs-frontend-v2/src/utils/echarts-theme.ts`
**修改：** `main.ts` (注册主题)
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] 创建 `echarts-theme.ts` 导出主题配置对象
- [ ] 定义：坐标轴颜色、网格线颜色、tooltip 背景、系列颜色数组
- [ ] 系列颜色使用 `--accent-cool`, `--accent-warm`, `--color-health-*` 对应的 hex 值
- [ ] 在 `main.ts` 中注册主题：`echarts.registerTheme('hhs-dark', theme)`
- [ ] 所有图表实例使用 `'hhs-dark'` 主题

**验收标准：**
- [ ] 所有图表坐标轴、网格线颜色一致
- [ ] tooltip 背景与暗色主题协调
- [ ] 系列颜色在 5+ 条数据时有足够区分度
- [ ] `npm run build` 无报错

---

## C. 后端核心功能修复

### C1. 健康分数历史持久化
**新建文件：** `entity/HealthScoreHistory.java`, `mapper/HealthScoreHistoryMapper.java`, `service/ScoreHistoryService.java`, `service/impl/ScoreHistoryServiceImpl.java`
**修改文件：** `controller/HealthScoreController.java`, `handler/ScoreUpdatedEventListener.java`, `sql/schema.sql`
**预估：** 1.5 天
**依赖：** 无

**任务清单：**
- [ ] 在 `schema.sql` 中添加 `health_score_history` 表
- [ ] 创建 `HealthScoreHistory` 实体类
- [ ] 创建 `HealthScoreHistoryMapper` 接口
- [ ] 创建 `ScoreHistoryService` 接口 + `ScoreHistoryServiceImpl`
- [ ] 在 `ScoreUpdatedEvent` 监听器中添加异步保存逻辑
- [ ] 修改 `HealthScoreController.getScoreHistory()` 从数据库查询
- [ ] 支持 `days` 参数（默认 30）
- [ ] 支持 `granularity` 参数（daily/weekly/monthly）

**验收标准：**
- [ ] `GET /api/health-score/history?days=30` 返回真实历史数据
- [ ] 每次分数更新后自动保存到 `health_score_history` 表
- [ ] 同一天多次更新只保留最新分数（UPSERT）
- [ ] `days=7` 返回 7 条记录（如有数据）
- [ ] `granularity=weekly` 返回按周聚合的数据
- [ ] 无历史数据时返回空数组（不返回假数据）
- [ ] `mvn clean verify -DskipITs` 通过
- [ ] 新增单元测试覆盖 `ScoreHistoryServiceImpl`

---

### C2. 数据导出功能
**新建文件：** `service/ExportService.java`, `service/impl/ExportServiceImpl.java`, `controller/ExportController.java`, `entity/ExportJob.java`, `mapper/ExportJobMapper.java`
**修改文件：** `pom.xml` (添加依赖), `sql/schema.sql`
**预估：** 2 天
**依赖：** 无

**任务清单：**
- [ ] `pom.xml` 添加 `apache-poi-ooxml` 和 `opencsv` 依赖
- [ ] `schema.sql` 添加 `export_jobs` 表
- [ ] 创建 `ExportJob` 实体 + `ExportJobMapper`
- [ ] 创建 `ExportService` 接口 + `ExportServiceImpl`
- [ ] 实现 CSV 导出（健康指标、分数历史）
- [ ] 实现 Excel 导出（多 sheet：指标、分数、体检）
- [ ] 创建 `ExportController`：
  - `POST /api/exports` — 创建导出任务
  - `GET /api/exports/{id}/status` — 查询状态
  - `GET /api/exports/{id}/download` — 下载文件
  - `GET /api/exports` — 列出用户导出记录
- [ ] 导出文件存储到临时目录，过期自动清理

**验收标准：**
- [ ] `POST /api/exports` 返回 job ID
- [ ] CSV 文件可被 Excel 正确打开，中文无乱码
- [ ] Excel 文件包含表头、数据、正确的列宽
- [ ] 日期范围筛选正确过滤数据
- [ ] 大数据量（1000+ 条）导出不 OOM
- [ ] `GET /api/exports/{id}/download` 返回正确文件
- [ ] `mvn clean verify -DskipITs` 通过

---

### C3. 用户提醒系统
**新建文件：** `entity/UserReminder.java`, `mapper/UserReminderMapper.java`, `service/ReminderService.java`, `service/impl/ReminderServiceImpl.java`, `controller/ReminderController.java`, `schedule/ReminderScheduler.java`
**修改文件：** `sql/schema.sql`
**预估：** 2 天
**依赖：** 无

**任务清单：**
- [ ] `schema.sql` 添加 `user_reminders` 表
- [ ] 创建 `UserReminder` 实体 + `UserReminderMapper`
- [ ] 创建 `ReminderService` 接口 + `ReminderServiceImpl`
- [ ] 创建 `ReminderScheduler` — 每分钟检查到期提醒
- [ ] 复用 `PushChannelManager` 发送提醒通知
- [ ] 创建 `ReminderController`：
  - `POST /api/reminders` — 创建提醒
  - `GET /api/reminders` — 列出用户提醒
  - `PUT /api/reminders/{id}` — 更新提醒
  - `DELETE /api/reminders/{id}` — 删除提醒
  - `PUT /api/reminders/{id}/toggle` — 启用/禁用
- [ ] 支持提醒类型：用药、运动、喝水、体检、自定义
- [ ] 前端创建提醒管理页面 `views/reminders/Index.vue`

**验收标准：**
- [ ] 创建提醒后，到达指定时间时通过 WebSocket 推送通知
- [ ] 提醒支持按天/周配置（如每周一三五 9:00）
- [ ] 禁用提醒后不再触发
- [ ] 删除提醒后不再触发
- [ ] 提醒历史记录可查询
- [ ] `mvn clean verify -DskipITs` 通过

---

### C4. 体检报告对比
**新建文件：** `service/ScreeningComparisonService.java`, `service/impl/ScreeningComparisonServiceImpl.java`
**修改文件：** `controller/ScreeningController.java`
**预估：** 1.5 天
**依赖：** 无

**任务清单：**
- [ ] 创建 `ScreeningComparisonService` 接口 + 实现
- [ ] 对比逻辑：逐指标对比变化值和变化百分比
- [ ] 标记状态：improved / worsened / stable
- [ ] 标记是否在参考范围内
- [ ] 在 `ScreeningController` 添加：
  - `POST /api/screenings/compare` — 接收两个 session ID
  - `GET /api/screenings/{id}/history?metric={name}` — 指标历史趋势
- [ ] 前端创建对比视图页面 `views/screening/Compare.vue`

**验收标准：**
- [ ] `POST /api/screenings/compare` 返回逐指标对比结果
- [ ] 变化百分比计算正确
- [ ] 改善/恶化/持平状态标记正确
- [ ] 参考范围判断正确
- [ ] 两个报告无共同指标时返回空对比结果
- [ ] `mvn clean verify -DskipITs` 通过

---

### C5. 扩展统计接口
**修改文件：** `controller/StatsController.java`, `service/StatsService.java`, `service/impl/StatsServiceImpl.java`
**预估：** 1 天
**依赖：** 无

**任务清单：**
- [ ] 在 `StatsService` 中添加方法：
  - `getWeeklyStats(userId)` — 本周统计 + 与上周对比
  - `getMonthlyStats(userId)` — 本月统计 + 与上月对比
  - `getTrendData(userId, metricType, days)` — 指定指标趋势
  - `getDashboardSummary(userId)` — Dashboard 汇总
- [ ] 在 `StatsController` 中添加对应端点
- [ ] 趋势数据返回格式：`[{date, value}]`

**验收标准：**
- [ ] `GET /api/stats/weekly` 返回本周数据和环比变化百分比
- [ ] `GET /api/stats/monthly` 返回本月数据和环比变化百分比
- [ ] `GET /api/stats/trends?metric=heart_rate&days=7` 返回 7 天数据点
- [ ] `GET /api/stats/summary` 返回 Dashboard 所需的汇总数据
- [ ] 无数据时返回零值（不报错）
- [ ] `mvn clean verify -DskipITs` 通过

---

### C6. 修复 AI 报告降级逻辑
**修改文件：** `service/impl/HealthReportServiceImpl.java`, `dto/HealthReportVO.java`
**预估：** 0.5 天
**依赖：** 无

**任务清单：**
- [ ] 在 `HealthReportVO` 中添加 `boolean isEstimated` 字段
- [ ] 在 `HealthReportServiceImpl.getDefaultDimensions()` 调用处设置 `isEstimated = true`
- [ ] 正常 AI 分析时设置 `isEstimated = false`
- [ ] 前端在报告页面显示"预估数据"标签（当 `isEstimated = true` 时）

**验收标准：**
- [ ] AI 正常分析时 `isEstimated = false`
- [ ] AI 解析失败降级时 `isEstimated = true`
- [ ] 前端显示"预估"提示标签
- [ ] `mvn clean verify -DskipITs` 通过

---

### C7. 小米健康 API 集成
**修改文件：** `service/impl/XiaomiHealthServiceImpl.java`, 前端相关页面
**预估：** 0.5 天（仅标注，不做 API 逆向）
**依赖：** 无

**任务清单：**
- [ ] 前端设备同步页面添加"模拟数据"标签（当使用 mock 数据时）
- [ ] 后端在 mock 数据响应中添加 `isMock: true` 标记
- [ ] 前端根据标记显示提示："当前显示模拟数据，实际数据需连接小米设备"

**验收标准：**
- [ ] 使用 mock 数据时前端显示"模拟数据"提示
- [ ] 提示不影响正常功能使用
- [ ] `npm run build` 无报错

---

## D. 用户粘性功能

### D1. 游戏化系统 — 连续打卡 + 成就
**新建文件：** `entity/UserStreak.java`, `entity/Achievement.java`, `entity/UserAchievement.java`, `entity/UserPoint.java`, `mapper/*`, `service/GamificationService.java`, `service/impl/GamificationServiceImpl.java`, `controller/GamificationController.java`
**修改文件：** `sql/schema.sql`, `handler/MetricRecordedEventListener.java`
**预估：** 2 天
**依赖：** 无

**任务清单：**
- [ ] `schema.sql` 添加 4 张表：`user_streaks`, `achievements`, `user_achievements`, `user_points`
- [ ] 创建 4 个实体类 + 4 个 Mapper
- [ ] 创建 `GamificationService` 接口 + 实现
- [ ] 实现打卡逻辑：记录活动 → 更新连续天数 → 检查成就
- [ ] 实现积分系统：完成活动获得积分，积分决定等级
- [ ] 预置成就数据（INSERT）：
  - `first_record` — 首次记录健康数据 (10 分)
  - `streak_7` — 连续 7 天打卡 (50 分)
  - `streak_30` — 连续 30 天打卡 (200 分)
  - `first_ai_chat` — 首次 AI 对话 (10 分)
  - `score_90` — 健康分数达到 90 (100 分)
  - `goal_complete` — 完成首个目标 (30 分)
- [ ] 在 `MetricRecordedEventListener` 中触发打卡检查
- [ ] 创建 `GamificationController`：
  - `GET /api/gamification/streaks` — 打卡记录
  - `GET /api/gamification/achievements` — 成就列表（已解锁 + 未解锁）
  - `POST /api/gamification/check-in` — 手动打卡
  - `GET /api/gamification/points` — 积分和等级
- [ ] 前端 Dashboard 展示连续打卡天数、最近解锁成就

**验收标准：**
- [ ] 记录健康数据后自动更新连续打卡天数
- [ ] 连续 7 天打卡后自动解锁 `streak_7` 成就
- [ ] 成就解锁时通过 WebSocket 推送通知
- [ ] 积分正确累加
- [ ] 断签后连续天数重置为 0
- [ ] `mvn clean verify -DskipITs` 通过
- [ ] 新增单元测试覆盖打卡逻辑

---

### D2. 健康目标追踪
**新建文件：** `entity/HealthGoal.java`, `entity/GoalProgress.java`, `mapper/*`, `service/GoalService.java`, `service/impl/GoalServiceImpl.java`, `controller/GoalController.java`
**修改文件：** `sql/schema.sql`
**预估：** 2 天
**依赖：** 无

**任务清单：**
- [ ] `schema.sql` 添加 `health_goals` 和 `goal_progress` 表
- [ ] 创建实体类 + Mapper
- [ ] 创建 `GoalService` 接口 + 实现
- [ ] 实现目标 CRUD
- [ ] 实现进度记录和自动更新（从健康指标中提取）
- [ ] 目标完成时触发成就解锁（与 D1 联动）
- [ ] 创建 `GoalController`：
  - `POST /api/goals` — 创建目标
  - `GET /api/goals` — 列出目标（支持 status 筛选）
  - `PUT /api/goals/{id}` — 更新目标
  - `DELETE /api/goals/{id}` — 删除目标
  - `POST /api/goals/{id}/progress` — 记录进度
  - `GET /api/goals/{id}/progress` — 进度历史
  - `GET /api/goals/summary` — 目标概览
- [ ] 前端创建目标管理页面 `views/goals/Index.vue`

**验收标准：**
- [ ] 创建目标后可在列表中看到
- [ ] 记录进度后 current_value 正确更新
- [ ] 达到 target_value 时 status 自动变为 `completed`
- [ ] 完成目标时触发 `goal_complete` 成就
- [ ] 进度历史按日期排序
- [ ] `mvn clean verify -DskipITs` 通过

---

### D3. 心情追踪增强
**新建文件：** `entity/MoodEntry.java`, `mapper/MoodEntryMapper.java`, `service/MoodService.java`, `service/impl/MoodServiceImpl.java`, `controller/MoodController.java`
**修改文件：** `sql/schema.sql`
**预估：** 1.5 天
**依赖：** 无

**任务清单：**
- [ ] `schema.sql` 添加 `mood_entries` 表
- [ ] 创建实体类 + Mapper
- [ ] 创建 `MoodService` 接口 + 实现
- [ ] 实现心情记录 CRUD
- [ ] 实现心情趋势分析（按星期、时间段）
- [ ] 创建 `MoodController`：
  - `POST /api/mood/entries` — 记录心情
  - `GET /api/mood/history` — 心情历史（支持日期范围）
  - `GET /api/mood/insights` — 心情趋势洞察
  - `GET /api/mood/today` — 今日心情状态
- [ ] 前端创建心情记录组件 `components/MoodTracker.vue`
- [ ] 前端心情日历视图（展示每日心情颜色）

**验收标准：**
- [ ] 记录心情后保存到数据库
- [ ] 心情历史按日期正确展示
- [ ] 趋势分析显示按星期的平均心情
- [ ] 前端心情选择器交互流畅
- [ ] 心情日历颜色映射正确（1-3 红, 4-6 黄, 7-10 绿）
- [ ] `mvn clean verify -DskipITs` 通过

---

### D4. 骨架屏加载
**修改文件：** `dashboard/Index.vue`, `health/Score.vue`, `wellness/Dashboard.vue`, `realtime/Monitor.vue`
**预估：** 1 天
**依赖：** 无

**任务清单：**
- [ ] `dashboard/Index.vue`：统计卡片、分数显示、告警列表添加 `<el-skeleton>`
- [ ] `health/Score.vue`：分数显示、维度列表、图表区域添加骨架屏
- [ ] `wellness/Dashboard.vue`：指标卡片、图表添加骨架屏
- [ ] `realtime/Monitor.vue`：指标卡片添加骨架屏
- [ ] 骨架形状匹配实际内容布局
- [ ] 使用 `animated` 属性启用 shimmer 动画

**验收标准：**
- [ ] 页面加载时先显示骨架屏，数据加载完成后切换为真实内容
- [ ] 骨架屏形状与实际内容布局匹配
- [ ] 骨架屏有 shimmer 动画效果
- [ ] 数据加载失败时骨架屏正确消失（显示错误状态）
- [ ] `npm run build` 无报错

---

## E. 高级分析功能

### E1. 指标关联分析
**新建文件：** `service/CorrelationService.java`, `service/impl/CorrelationServiceImpl.java`, `controller/CorrelationController.java`
**修改文件：** `pom.xml` (添加 commons-math3)
**预估：** 2 天
**依赖：** C1 (需要历史数据)

**任务清单：**
- [ ] `pom.xml` 添加 `commons-math3` 依赖
- [ ] 创建 `CorrelationService` 接口 + 实现
- [ ] 实现 Pearson 相关系数计算
- [ ] 支持分析：睡眠 vs 心情、运动 vs 睡眠、步数 vs 心率等
- [ ] 生成关联洞察文本描述
- [ ] 创建 `CorrelationController`：
  - `GET /api/correlations?metric1=sleep&metric2=mood` — 两个指标的关联
  - `GET /api/correlations/matrix` — 关联矩阵
  - `GET /api/correlations/insights` — AI 生成的关联洞察
- [ ] 前端关联矩阵热力图（ECharts heatmap）

**验收标准：**
- [ ] 相关系数范围正确（-1 到 1）
- [ ] 至少 10 个数据点才计算相关性（否则返回"数据不足"）
- [ ] 关联矩阵显示所有可用指标的两两相关性
- [ ] 前端热力图颜色映射正确
- [ ] `mvn clean verify -DskipITs` 通过

---

### E2. PDF 健康报告
**新建文件：** `service/PdfReportService.java`, `service/impl/PdfReportServiceImpl.java`, `controller/ReportController.java`
**修改文件：** `pom.xml` (添加 itext7 或 pdfbox)
**预估：** 2 天
**依赖：** C1 (分数历史), C5 (统计接口)

**任务清单：**
- [ ] `pom.xml` 添加 PDF 生成依赖
- [ ] 创建 `PdfReportService` 接口 + 实现
- [ ] 报告内容：
  - 封面（用户信息、报告日期）
  - 健康分数概览 + 趋势图
  - 各维度详细分析
  - 关键指标趋势表格
  - AI 建议摘要
- [ ] 创建 `ReportController`：
  - `POST /api/reports/generate` — 生成报告
  - `GET /api/reports/{id}/download` — 下载 PDF
  - `GET /api/reports/templates` — 报告模板列表
- [ ] 异步生成（避免阻塞请求）

**验收标准：**
- [ ] 生成的 PDF 包含封面、分数、指标、建议
- [ ] PDF 中文显示正常（无乱码）
- [ ] PDF 中的图表正确渲染
- [ ] 文件大小合理（< 5MB）
- [ ] `mvn clean verify -DskipITs` 通过

---

### E3. 健康就绪度分数
**新建文件：** `service/ReadinessService.java`, `service/impl/ReadinessServiceImpl.java`, `controller/ReadinessController.java`, `entity/ReadinessScore.java`, `mapper/ReadinessScoreMapper.java`
**修改文件：** `sql/schema.sql`
**预估：** 1.5 天
**依赖：** 无

**任务清单：**
- [ ] `schema.sql` 添加 `readiness_scores` 和 `readiness_config` 表
- [ ] 创建实体类 + Mapper
- [ ] 创建 `ReadinessService` 接口 + 实现
- [ ] 就绪度计算算法：
  - 睡眠质量（权重 25%）
  - 恢复状态（权重 20%）
  - 压力水平（权重 20%）
  - 水分摄入（权重 15%）
  - 营养状态（权重 10%）
  - 近期活动量（权重 10%）
- [ ] 权重可用户自定义
- [ ] 创建 `ReadinessController`：
  - `GET /api/readiness` — 今日就绪度
  - `GET /api/readiness/history` — 就绪度历史
  - `PUT /api/readiness/config` — 自定义权重
  - `GET /api/readiness/recommendations` — 运动建议
- [ ] 前端就绪度展示组件

**验收标准：**
- [ ] 就绪度分数范围 0-100
- [ ] 各因素权重正确应用
- [ ] 自定义权重后分数重新计算
- [ ] 无数据因素使用默认值（不报错）
- [ ] 运动建议根据分数级别给出（高/中/低强度）
- [ ] `mvn clean verify -DskipITs` 通过

---

## 技术债务清理

### T1. 移除 3d-enhance.css 调试代码
**文件：** `hhs-frontend-v2/src/assets/styles/3d-enhance.css`
**预估：** 5 分钟

**任务：**
- [ ] 删除第 9 行 `#app { outline: 3px solid red !important; }`

**验收：**
- [ ] 文件中无 `outline: 3px solid red`

---

### T2. 清理 useTilt3D 死代码
**文件：** `hhs-frontend-v2/src/composables/useTilt3D.ts`
**预估：** 5 分钟

**任务：**
- [ ] 确认零导入后删除 `useTilt3D.ts`

**验收：**
- [ ] 文件不存在
- [ ] `npm run build` 无报错

---

### T3. ECharts resize 逻辑去重
**文件：** `wellness/Dashboard.vue`, `realtime/Monitor.vue`, `health/Score.vue`
**预估：** 随 B1 一起完成

**任务：**
- [ ] 由 B1 的 `useECharts` composable 自动处理

**验收：**
- [ ] 三个视图中无重复的 resize 监听代码

---

## 依赖关系图

```
Phase 1 (第 1-2 周):
  A1 ──┐
  A2 ──┤
  A3 ──┤
  A4 ──┼── 无依赖，可并行
  B1 ──┤
  B7 ──┤
  C1 ──┤
  D4 ──┘

Phase 2 (第 3-4 周):
  B1 → B2 (雷达图需要 useECharts)
  B1 → B3 (仪表盘需要 useECharts)
  B1 → B4 (sparklines 需要 useECharts)
  C5 → B4 (sparklines 需要趋势数据)
  C2 ── 无依赖
  C3 ── 无依赖
  C5 ── 无依赖
  C6 ── 无依赖

Phase 3 (第 5-6 周):
  D1 ── 无依赖
  D2 ── 无依赖
  D3 ── 无依赖
  C4 ── 无依赖
  B1 → B5 (实时仪表盘需要 useECharts)

Phase 4 (第 7-8 周):
  C1 → E1 (关联分析需要历史数据)
  C1 → E2 (PDF 报告需要历史数据)
  C5 → E2 (PDF 报告需要统计接口)
  A5 ── 无依赖
  A6 ── A1-A3 完成后
  B6 ── 无依赖
  E3 ── 无依赖
```

---

## 快速启动命令

```bash
# 后端测试
cd hhs-backend && mvn clean verify -DskipITs

# 前端构建检查
cd hhs-frontend-v2 && npm run build

# 前端开发服务器
cd hhs-frontend-v2 && npm run dev
```

---

*此清单为 /using-superpowers 快速开发准备。每个任务独立可执行，验收标准明确。*
