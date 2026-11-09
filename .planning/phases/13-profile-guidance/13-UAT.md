---
status: testing
phase: 13-profile-guidance
source:
  - 13-VERIFICATION.md
  - 13-08-SUMMARY.md
started: 2026-11-10T00:20:00Z
updated: 2026-11-10T00:20:00Z
---

## Current Test

number: 1
name: 档案页字段说明在 320–639px 的换行表现
expected: |
  1. 浏览器窗口缩到 320px 宽，打开 /prevention/profile；
  2. 每个字段下方有一行"为何需要"说明（如过敏史 → 用于过敏风险提示与用药建议）；
  3. 说明文字独占一行、位于输入控件下方，24 字的说明**不被裁切、不溢出**；
  4. 每个说明行首有一个重要度徽标（必需实心 / 推荐实线 / 可选虚线），徽标与文字不重叠；
  5. 页面底部"推荐与可选字段不填也可保存，不影响核心功能使用"承诺句**只出现一次**。
awaiting: user response

## Tests

### 1. 档案页字段说明在 320–639px 的换行表现
expected: |
  窗口缩到 320px 打开 /prevention/profile：每个字段下方有一行"为何需要"说明，独占一行不裁切；行首重要度徽标（必需实心/推荐实线/可选虚线）与文字不重叠；承诺句只出现一次。
result: pending

### 2. 639px 以下标签位置翻转
expected: |
  档案页窗口缩到 639px 以下：表单标签从左侧移到输入框**上方**（label-position="top" 生效）；表单布局不被压成 block 导致错位；恢复宽度后标签回到左侧。
result: pending

### 3. 估算徽标在 320px 不换行
expected: |
  在 320px 宽下看评分页与风险页的「基于不完整档案估算 · 完善档案」徽标：文案保持**单行不换行**，控件仍是单个按钮（min-height 32px），点击可跳转档案页并聚焦首个待填字段。
result: pending

### 4. 横幅动作区在 640/768px 不重叠
expected: |
  仪表盘在 640px 与 768px 宽、且缺失字段列表较长时：`完善档案` / `稍后提醒` / `×` 三个控件**互不重叠**；768px 以下动作区换行为整行、左侧 44px 内缩；横幅文案的字段列表换行而非截断。
result: pending

### 5. 横幅不抢夺健康评分圆环的视觉焦点
expected: |
  仪表盘上横幅与 HealthScoreCircle 同屏：横幅只有 surface-1 底色 + 单条边框，**无高饱和填充**；视觉焦点仍是评分圆环，横幅不显眼于它。
result: pending

### 6. 集成测试在可用 Docker 环境下执行（环境限制）
expected: |
  在 Docker daemon 可达的环境运行 `mvn -f hhs-backend/pom.xml verify -Dit.test=PreventionIntegrationTest`：测试实际执行（而非被 Surefire 排除）。此项为既有环境限制，与 Phase 13 代码无关。
result: pending

## Automated Coverage (pre-verified — 不再重复人工验证)

以下由全量自动化钉死（后端 845 / 前端 445 全绿，编排器独立复跑）：

| 计划 | 已钉死行为 |
|------|-----------|
| 13-01 | 9 字段加权完整度、三等级边界 59/60/84/85、陈旧 bmi 免疫、9 条 reason 逐字段真实消费方 + 2 负向守卫 |
| 13-02 | 完整度端点：无档案 200/0%、`totalCount == fields.length`、`hasProfile:false` |
| 13-03 | 假设在代入点捕获、旧签名 70 中性不变、`assumptions` 永不为 null、缓存键 v2 |
| 13-04 | 风险 VO 双字段、读时重算零 DDL、四类档案形状句子与目录源逐字相等 |
| 13-05 | store 单源 single-flight awaitable（删 await 即红）、7 天窗口 `<=` 边界、404 合成 0% 且不 toast |
| 13-06 | 三组件分段数=fields.length、null 安全、`el-tag`/`el-alert`/`el-progress` 零使用 |
| 13-07 | 9 条 hint、保存后 40→70 刷新、失败不二次请求、`?focus=` 聚焦、gutter 16×3 |
| 13-08 | 单实例横幅、`score<60` 门禁（60 分静默）、关闭后 7 天、`aria-labelledby` |
| 13-09 | 三序判定、面板假设逐字来自后端（`f.assumption`=0）、4 字段补填、删 await 即红 |
| 13-10 | 评分页徽标 `?? null` 不伪造 0%、NO_DATA 不渲染、裸标签清零、六腿门禁全绿 |

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps

<!-- None yet -->
