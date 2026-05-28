# HHS v2.0 版本迭代计划
*Generated: 2026-05-27 | Based on: 4 parallel research agents + extensibility analysis*

---

## 愿景

从"功能可用的原型"升级为"设计精致、功能完整的健康监测平台"。v2.0 的核心目标：
1. **前端设计感** — 统一 Dark Editorial 设计系统，引入数据可视化和微动画
2. **后端功能补全** — 修复假数据、补全核心功能、提升用户体验
3. **用户粘性** — 通过游戏化、目标追踪、AI 洞察提升留存

---

## 总览：5 大迭代方向

| # | 方向 | 核心价值 | 预估工期 |
|---|------|---------|---------|
| A | 前端设计统一 | 消除主题割裂，全站视觉一致 | 1-2 周 |
| B | 数据可视化升级 | 让健康数据"活"起来 | 1-2 周 |
| C | 后端核心功能修复 | 修复假数据，补全缺失功能 | 2-3 周 |
| D | 用户粘性功能 | 游戏化 + 目标追踪 + 提醒 | 2-3 周 |
| E | 高级分析功能 | AI 洞察 + 关联分析 + 报告导出 | 2-3 周 |

---

## A. 前端设计统一（1-2 周）

### 问题现状

| 页面 | 主题状态 | 核心问题 |
|------|---------|---------|
| `dashboard/Index.vue` | ✅ 完整 | 无 |
| `auth/Login.vue` | ✅ 完整 | 无 |
| `data-input/Index.vue` | ✅ 完整 | 无 |
| `realtime/Monitor.vue` | ❌ 全部硬编码 | `#67c23a`, `#303133`, `#606266` 等 Element Plus 默认色 |
| `wellness/Dashboard.vue` | ❌ 全部硬编码 | 所有文字颜色用 Element Plus 默认值 |
| `health/Score.vue` | ⚠️ 混合 | 健康等级颜色硬编码 `#22c55e` 等，未用 `--color-health-*` token |
| `health/Alerts.vue` | ⚠️ 未审查 | 可能存在同样问题 |
| `health/Metrics.vue` | ⚠️ 未审查 | 可能存在同样问题 |
| `health/Thresholds.vue` | ⚠️ 未审查 | 可能存在同样问题 |
| `screening/*` | ⚠️ 未审查 | 可能存在同样问题 |

### A1. 修复 `realtime/Monitor.vue` 设计 [高优先级]
- 替换所有硬编码颜色为 theme tokens：
  - `#67c23a` → `var(--color-health-excellent)`
  - `#e6a23c` → `var(--color-health-fair)`
  - `#f56c6c` → `var(--color-health-poor)`
  - `#303133` → `var(--text-1)`
  - `#606266` → `var(--text-2)`
  - `#909399` → `var(--text-3)`
- 统计卡片应用 `.glass-card` 毛玻璃效果
- 添加页面入场动画（stagger reveal）
- ECharts 图表颜色改为 theme accent 色

### A2. 修复 `wellness/Dashboard.vue` 设计 [高优先级]
- 替换所有 Element Plus 默认色为 theme tokens
- 统计卡片应用毛玻璃效果
- 添加 scroll reveal 入场动画
- 趋势颜色使用 `--color-health-*` tokens

### A3. 修复 `health/Score.vue` 设计 [中优先级]
- 健康等级颜色使用 `var(--color-health-excellent/good/fair/poor)` tokens
- **复用 `HealthScoreCircle` 组件**替换当前的纯文本数字显示（当前页面的分数显示还不如 dashboard 的摘要卡片精致）
- ECharts 颜色从 `#409eff` 改为 `var(--accent-cool)`

### A4. 激活 `3d-enhance.css` [中优先级]
- **移除第 9 行调试代码**：`#app { outline: 3px solid red !important; }`（这是遗留的调试标记，如果导入会导致整个应用出现红色边框）
- 在 `main.ts` 中导入 `3d-enhance.css`
- 验证 3D 效果与现有主题的兼容性

### A5. 统一浮动球 CSS 变量 [低优先级]
- `FloatingBallButton.vue` 和 `FloatingChatPanel.vue` 使用了与 `theme.css` 不同的变量名：
  - `var(--color-surface)` → 应为 `var(--surface-1)`
  - `var(--color-border-light)` → 应为 `var(--border)`
  - `var(--color-text-primary)` → 应为 `var(--text-1)`
  - `var(--spacing-md)` → 应为 `var(--sp-4)`
- 统一变量名以确保主题切换生效

### A6. 审查其余页面 [低优先级]
- `health/Alerts.vue`, `health/Metrics.vue`, `health/Thresholds.vue`
- `screening/List.vue`, `screening/Detail.vue`
- `settings/*`, `user/*`
- 按 A1-A3 的模式统一修复

---

## B. 数据可视化升级（1-2 周）

### 问题现状

- ECharts 已安装（`echarts ^5.4.3`），但仅在 3 个文件中使用
- 所有图表都是基础折线图，无雷达图、仪表盘、迷你趋势线
- 无统一的 ECharts 暗色主题配置
- 三个视图重复相同的 ECharts init/dispose/resize 逻辑

### B1. 创建共享图表 Composable [高优先级]
- 提取 `useECharts()` composable，封装：
  - 初始化 + 暗色主题配置
  - 自动 resize 监听
  - dispose 清理
  - 主题 token 集成（`--accent-cool`, `--accent-warm` 等）
- 消除 `wellness/Dashboard.vue`, `realtime/Monitor.vue`, `health/Score.vue` 中的重复代码

### B2. 健康分数雷达图 [高优先级]
- 在 `health/Score.vue` 中添加 ECharts RadarChart
- 展示各维度分数：心血管、代谢、体重、生活方式等
- 支持"本周 vs 上周"对比叠加
- 配合 `HealthScoreCircle` 组件形成完整的分数可视化

### B3. 健康分数仪表盘 [高优先级]
- 在 `health/Score.vue` 中添加 ECharts GaugeChart
- 替换当前的纯文本数字
- 动态颜色映射：excellent → good → fair → poor
- 支持动画过渡效果

### B4. Dashboard 迷你趋势线（Sparklines）[中优先级]
- 在 `dashboard/Index.vue` 的统计卡片中添加迷你折线图
- 无坐标轴、无标签，仅展示趋势走向
- 用于展示：心率趋势、步数趋势、睡眠趋势、分数趋势
- 使用 ECharts minimal line chart 配置

### B5. 实时监控仪表盘 [中优先级]
- 在 `realtime/Monitor.vue` 中添加 ECharts GaugeChart
- 展示当前心率、血压等关键指标的实时值
- 动态更新，配合 WebSocket 推送

### B6. 活动环组件（Activity Rings）[低优先级]
- 创建 `ActivityRings.vue` 组件
- SVG 实现，3 个同心环：运动、睡眠、健康习惯
- CSS 动画填充效果
- 可用于 Dashboard 和 Wellness 页面

### B7. ECharts 暗色主题配置 [中优先级]
- 创建 `echarts-dark-theme.ts` 配置文件
- 使用 theme tokens 定义：
  - 坐标轴颜色：`var(--text-4)`
  - 网格线颜色：`var(--border)`
  - 提示框背景：`var(--surface-2)`
  - 系列颜色：`var(--accent-cool)`, `var(--accent-warm)`, `var(--color-health-excellent)` 等
- 所有图表统一应用

---

## C. 后端核心功能修复（2-3 周）

### 问题现状

| 功能 | 状态 | 严重度 |
|------|------|--------|
| 健康分数历史 | 假数据 — 只返回当前分数 | 🔴 高 |
| 统计接口 | 仅有"今日统计" | 🟡 中 |
| PDF/导出 | 完全缺失 | 🔴 高 |
| 用户提醒 | 完全缺失 | 🔴 高 |
| 体检报告对比 | 未实现 | 🟡 中 |
| 小米健康 API | TODO — 始终返回模拟数据 | 🔴 高 |
| AI 报告降级 | 硬编码默认值无提示 | 🟡 中 |

### C1. 实现健康分数历史持久化 [高优先级]
**问题：** `HealthScoreController.getScoreHistory()` 返回假数据 — 仅包装当前分数为单条记录，无历史表。

**修复方案：**
1. 新建 `health_score_history` 表：
```sql
CREATE TABLE health_score_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    score_date DATE NOT NULL,
    overall_score DECIMAL(5,2),
    cardiovascular_score DECIMAL(5,2),
    metabolic_score DECIMAL(5,2),
    weight_score DECIMAL(5,2),
    lifestyle_score DECIMAL(5,2),
    factors_snapshot JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, score_date),
    UNIQUE KEY uk_user_date (user_id, score_date)
);
```
2. 创建 `HealthScoreHistory` 实体 + Mapper
3. 在分数计算完成后，异步保存当日分数（通过 `ScoreUpdatedEvent` 监听器）
4. 修改 `getScoreHistory()` 从数据库查询真实历史数据
5. 支持 `days` 参数和 `granularity` 参数（daily/weekly/monthly）

### C2. 实现数据导出功能 [高优先级]
**方案：**
1. 添加依赖：`apache-poi` (Excel), `opencsv` (CSV)
2. 创建 `ExportService` + `ExportController`
3. 支持格式：CSV, Excel, JSON
4. 支持导出内容：
   - 健康指标数据（带日期范围筛选）
   - 健康分数历史
   - 体检报告结果
   - AI 对话记录
5. 异步生成 + 文件下载接口
6. 创建 `export_jobs` 表追踪导出状态

```sql
CREATE TABLE export_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    export_type ENUM('csv', 'excel', 'json'),
    content_type VARCHAR(50),
    date_range_start DATE,
    date_range_end DATE,
    status ENUM('pending', 'processing', 'completed', 'failed'),
    file_path VARCHAR(500),
    file_size BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    INDEX idx_user_exports (user_id, created_at)
);
```

### C3. 实现用户提醒系统 [高优先级]
**方案：**
1. 创建提醒相关表：
```sql
CREATE TABLE user_reminders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reminder_type ENUM('medication', 'exercise', 'hydration', 'checkup', 'custom'),
    title VARCHAR(200),
    description TEXT,
    schedule_cron VARCHAR(50),  -- cron 表达式
    schedule_time TIME,
    schedule_days JSON,  -- [1,2,3,4,5,6,7]
    is_active BOOLEAN DEFAULT TRUE,
    push_channels JSON,  -- ['websocket', 'email']
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_active (user_id, is_active)
);
```
2. 创建 `ReminderScheduler` 使用 `@Scheduled` 或动态 cron 任务
3. 复用现有 `PushChannelManager` 进行多渠道推送
4. 创建 `ReminderController` 提供 CRUD 接口
5. 前端创建提醒管理页面

### C4. 实现体检报告对比 [中优先级]
**方案：**
1. 扩展 `LabResult` 实体，添加历史对比逻辑
2. 创建 `ScreeningComparisonService`
3. 新建接口：`POST /api/screenings/compare` — 接收两个 session ID
4. 对比逻辑：
   - 逐指标对比变化值和变化百分比
   - 标记改善/恶化/持平
   - 计算参考范围内的变化趋势
5. 前端创建对比视图页面

### C5. 扩展统计接口 [中优先级]
**当前状态：** `StatsController` 仅有 `GET /api/stats/today`

**新增接口：**
- `GET /api/stats/weekly` — 本周统计 + 与上周对比
- `GET /api/stats/monthly` — 本月统计 + 与上月对比
- `GET /api/stats/trends?metric={type}&days={n}` — 指定指标的趋势数据
- `GET /api/stats/summary` — Dashboard 汇总数据

### C6. 修复 AI 报告降级逻辑 [中优先级]
**问题：** `HealthReportServiceImpl` 在 AI 解析失败时返回硬编码默认值（心血管=80, 代谢=75 等），用户无法区分真实分析和默认值。

**修复：**
- 在 `HealthReportVO` 中添加 `isEstimated: boolean` 字段
- 降级时设置 `isEstimated = true`
- 前端展示"预估"标签提示用户

### C7. 小米健康 API 集成 [低优先级]
**当前状态：** `XiaomiHealthServiceImpl.fetchRealHealthData()` 返回空列表，始终触发 mock 数据

**评估：** 小米运动健康 API 未公开文档，此功能可能需要：
- 逆向工程小米运动健康 App 的 API
- 或等待官方开放 API
- 建议暂时在前端明确标注"模拟数据"，避免用户误解

---

## D. 用户粘性功能（2-3 周）

### D1. 游戏化系统 — 连续打卡 [高优先级]
**方案：**
1. 创建表：
```sql
CREATE TABLE user_streaks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    streak_type VARCHAR(50),  -- 'daily_log', 'exercise', 'mood_tracking'
    current_streak INT DEFAULT 0,
    longest_streak INT DEFAULT 0,
    last_activity_date DATE,
    streak_start_date DATE,
    UNIQUE KEY uk_user_streak (user_id, streak_type)
);

CREATE TABLE achievements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    achievement_code VARCHAR(50) UNIQUE,
    achievement_name VARCHAR(100),
    description TEXT,
    category VARCHAR(50),
    icon VARCHAR(100),
    criteria JSON,
    points INT DEFAULT 0,
    rarity ENUM('common', 'uncommon', 'rare', 'epic', 'legendary'),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE user_achievements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    achievement_id BIGINT NOT NULL,
    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_achievement (user_id, achievement_id)
);

CREATE TABLE user_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    total_points INT DEFAULT 0,
    current_level INT DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```
2. 创建 `GamificationService` — 打卡、积分、成就解锁逻辑
3. 预置成就：首次记录、连续 7 天、连续 30 天、首次 AI 对话等
4. 通过 `MetricRecordedEvent` 事件驱动自动检查成就条件
5. 前端 Dashboard 展示连续打卡天数、最近解锁成就

### D2. 健康目标追踪 [高优先级]
**方案：**
1. 创建表：
```sql
CREATE TABLE health_goals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    goal_type VARCHAR(50),  -- 'weight', 'steps', 'sleep', 'exercise'
    goal_name VARCHAR(200),
    target_value DECIMAL(10,2),
    target_unit VARCHAR(20),
    current_value DECIMAL(10,2) DEFAULT 0,
    start_date DATE,
    target_date DATE,
    status ENUM('active', 'completed', 'paused', 'abandoned'),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_goals (user_id, status)
);

CREATE TABLE goal_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    recorded_value DECIMAL(10,2),
    recorded_date DATE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_goal_progress (goal_id, recorded_date)
);
```
2. 创建 `GoalService` + `GoalController`
3. 自动从健康指标中更新目标进度
4. 目标完成时触发成就解锁
5. 前端创建目标管理页面 + 进度可视化

### D3. 心情追踪增强 [中优先级]
**当前状态：** 心情仅作为 wellness metric key 存在（1-5 分），无专属功能

**增强方案：**
1. 创建 `mood_entries` 表，支持：
   - 心情分数（1-10）
   - 精力水平、焦虑水平、压力水平
   - 活动标签（运动、社交、工作、冥想）
   - 文字笔记
2. 创建 `MoodService` + `MoodController`
3. 心情趋势分析（按星期、时间段）
4. 心情与健康指标关联分析
5. 前端心情日历视图

### D4. 骨架屏加载 [中优先级]
**当前状态：** 全站零骨架屏实现，所有加载状态使用 `v-loading` spinner

**实施方案：**
- 使用 Element Plus `<el-skeleton>` 组件
- 优先应用于：
  1. `dashboard/Index.vue` — 统计卡片、分数显示、告警列表
  2. `health/Score.vue` — 分数显示、维度列表、图表
  3. `wellness/Dashboard.vue` — 指标卡片、图表
  4. `realtime/Monitor.vue` — 指标卡片
- 骨架形状匹配实际内容布局

---

## E. 高级分析功能（2-3 周）

### E1. 指标关联分析 [中优先级]
**方案：**
1. 创建 `CorrelationService`
2. 使用 Apache Commons Math 计算 Pearson/Spearman 相关系数
3. 支持分析：睡眠 vs 心情、运动 vs 睡眠质量、步数 vs 心率等
4. 生成关联洞察报告
5. 前端展示关联矩阵热力图

### E2. PDF 健康报告 [中优先级]
**方案：**
1. 添加依赖：`itext7-core` 或 `apache-pdfbox`
2. 创建 `PdfReportService`
3. 报告内容：
   - 健康分数概览 + 趋势图
   - 各维度详细分析
   - 关键指标趋势
   - AI 建议摘要
4. 支持模板化（个人报告、医生报告）
5. 异步生成 + 下载接口

### E3. 健康就绪度分数 [低优先级]
**方案：**
1. 创建 `ReadinessService`
2. 基于多个因素计算运动就绪度：
   - 睡眠质量（权重 25%）
   - 恢复状态（权重 20%）
   - 压力水平（权重 20%）
   - 水分摄入（权重 15%）
   - 营养状态（权重 10%）
   - 近期活动量（权重 10%）
3. 权重可用户自定义
4. 每日推荐运动强度

### E4. AI 健康洞察 [低优先级]
**方案：**
1. 基于历史数据生成个性化洞察
2. 使用 LLM 分析趋势并给出建议
3. 支持自然语言查询："我最近睡眠怎么样？"
4. 定期生成周报/月报

---

## 实施优先级排序

### 第一阶段：修复 + 设计统一（第 1-2 周）
| 序号 | 任务 | 方向 | 预估 |
|------|------|------|------|
| 1 | A1: 修复 realtime/Monitor.vue 设计 | 前端设计 | 0.5 天 |
| 2 | A2: 修复 wellness/Dashboard.vue 设计 | 前端设计 | 0.5 天 |
| 3 | A3: 修复 health/Score.vue 设计 + 复用 HealthScoreCircle | 前端设计 | 0.5 天 |
| 4 | A4: 激活 3d-enhance.css（移除调试代码） | 前端设计 | 0.5 天 |
| 5 | B1: 创建 useECharts composable | 可视化 | 0.5 天 |
| 6 | B7: ECharts 暗色主题配置 | 可视化 | 0.5 天 |
| 7 | C1: 健康分数历史持久化 | 后端功能 | 1.5 天 |
| 8 | D4: 骨架屏加载 | 用户体验 | 1 天 |

### 第二阶段：核心功能补全（第 3-4 周）
| 序号 | 任务 | 方向 | 预估 |
|------|------|------|------|
| 9 | B2: 健康分数雷达图 | 可视化 | 1 天 |
| 10 | B3: 健康分数仪表盘 | 可视化 | 0.5 天 |
| 11 | B4: Dashboard 迷你趋势线 | 可视化 | 1 天 |
| 12 | C2: 数据导出功能（CSV/Excel） | 后端功能 | 2 天 |
| 13 | C3: 用户提醒系统 | 后端功能 | 2 天 |
| 14 | C5: 扩展统计接口 | 后端功能 | 1 天 |
| 15 | C6: 修复 AI 报告降级逻辑 | 后端功能 | 0.5 天 |

### 第三阶段：用户粘性（第 5-6 周）
| 序号 | 任务 | 方向 | 预估 |
|------|------|------|------|
| 16 | D1: 游戏化系统 — 连续打卡 + 成就 | 用户粘性 | 2 天 |
| 17 | D2: 健康目标追踪 | 用户粘性 | 2 天 |
| 18 | D3: 心情追踪增强 | 用户粘性 | 1.5 天 |
| 19 | C4: 体检报告对比 | 后端功能 | 1.5 天 |
| 20 | B5: 实时监控仪表盘 | 可视化 | 1 天 |

### 第四阶段：高级功能（第 7-8 周）
| 序号 | 任务 | 方向 | 预估 |
|------|------|------|------|
| 21 | E1: 指标关联分析 | 高级分析 | 2 天 |
| 22 | E2: PDF 健康报告 | 高级分析 | 2 天 |
| 23 | A5: 统一浮动球 CSS 变量 | 前端设计 | 0.5 天 |
| 24 | A6: 审查其余页面设计 | 前端设计 | 1 天 |
| 25 | B6: 活动环组件 | 可视化 | 1 天 |
| 26 | E3: 健康就绪度分数 | 高级分析 | 1.5 天 |

---

## 技术债务清理（穿插进行）

| 任务 | 说明 | 优先级 |
|------|------|--------|
| 移除 `3d-enhance.css` 调试代码 | 第 9 行 `outline: 3px solid red` | 立即 |
| 清理 `useTilt3D` 死代码 | 零导入，完全未使用 | 低 |
| ECharts resize 逻辑去重 | 3 个视图重复相同代码 | 中 |
| 统一 ECharts 颜色方案 | 当前 3 个视图各用不同硬编码色 | 中 |

---

## 新增数据库表汇总

| 表名 | 用途 | 所属功能 |
|------|------|---------|
| `health_score_history` | 健康分数历史 | C1 |
| `export_jobs` | 导出任务追踪 | C2 |
| `user_reminders` | 用户自定义提醒 | C3 |
| `user_streaks` | 连续打卡记录 | D1 |
| `achievements` | 成就定义 | D1 |
| `user_achievements` | 用户已解锁成就 | D1 |
| `user_points` | 用户积分等级 | D1 |
| `health_goals` | 健康目标 | D2 |
| `goal_progress` | 目标进度记录 | D2 |
| `mood_entries` | 心情日志 | D3 |

---

## 新增 API 端点汇总

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/health-score/history` | 健康分数历史 |
| POST | `/api/exports` | 创建导出任务 |
| GET | `/api/exports/{id}/download` | 下载导出文件 |
| GET/POST | `/api/reminders` | 提醒 CRUD |
| POST | `/api/screenings/compare` | 体检报告对比 |
| GET | `/api/stats/weekly` | 周统计 |
| GET | `/api/stats/monthly` | 月统计 |
| GET | `/api/stats/trends` | 指标趋势 |
| GET | `/api/gamification/streaks` | 打卡记录 |
| GET | `/api/gamification/achievements` | 成就列表 |
| POST | `/api/gamification/check-in` | 打卡 |
| GET/POST | `/api/goals` | 目标 CRUD |
| POST | `/api/goals/{id}/progress` | 记录进度 |
| POST | `/api/mood/entries` | 心情记录 |
| GET | `/api/mood/history` | 心情历史 |
| POST | `/api/reports/generate` | 生成 PDF 报告 |
| GET | `/api/correlations` | 指标关联分析 |

---

## 设计亮点（值得保持的优秀实践）

1. **`theme.css` 设计系统** — 1167 行 CSS 变量体系，包含玻璃拟态、弹簧动画、健康语义色，是 v2.0 的基石
2. **`HealthScoreCircle` 组件** — SVG 动画分数环，仅需扩展使用范围
3. **`PushChannel` 策略模式** — 推送渠道抽象优秀，提醒系统可直接复用
4. **`FloatingBallButton` WebGL** — Three.js 3D 球体 + CSS 降级，设计品质高
5. **Domain Event 机制** — `MetricRecordedEvent`/`ScoreUpdatedEvent` 可驱动游戏化和目标追踪

---

*此计划基于 4 个并行研究代理的发现综合生成。建议按第一至第四阶段顺序推进。*
