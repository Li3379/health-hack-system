---
status: complete
phase: 13-profile-guidance
source:
  - 13-VERIFICATION.md
  - 13-08-SUMMARY.md
started: 2026-11-10T00:20:00Z
updated: 2026-11-11T03:10:00Z
reverified: 2026-11-11
reverified_reason: verification fingerprint drift caused by Phase 14 migration commits touching 5 covered files
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
result: pass
result_note: "320px 实测：9 条 .field-hint 全部渲染、0 横向溢出、样本 hint（性别）w=229/h=34 且 scrollWidth 未超 clientWidth（无裁切）；三档徽标类名齐备（required/recommended/optional）；承诺句「不填也可保存」精确 1 次；完整度头部显示「档案完整度 0% · 基础档案 / 已填 0/9」+ 9 个 ○ 图例"


### 2. 639px 以下标签位置翻转
expected: |
  档案页窗口缩到 639px 以下：表单标签从左侧移到输入框**上方**（label-position="top" 生效）；表单布局不被压成 block 导致错位；恢复宽度后标签回到左侧。
result: pass
result_note: "编辑态实测 9 标签 + 9 hint。639px 与 1024px 两档均为 el-form--label-top，label.bottom <= content.top（stacked=true），未出现错位。注：本页设计为全程置顶标签（UI-SPEC Contract 2 的 label-position=\"top\" 是全宽度策略，非仅窄屏），故宽屏下同样置顶属预期而非缺陷——已按实际契约核对"


### 3. 估算徽标在 320px 不换行
expected: |
  在 320px 宽下看评分页与风险页的「基于不完整档案估算 · 完善档案」徽标：文案保持**单行不换行**，控件仍是单个按钮（min-height 32px），点击可跳转档案页并聚焦首个待填字段。
result: pass
result_note: "320px 评分页实测：Range.getClientRects() 返回 6 个 rect 全部 top=429 → distinctLines=1（单行）；盒高 32px == min-height；tagName=BUTTON（自绘非 el-tag）；截图确认「基于不完整档案估算 · 完善档案」完整单行显示、带边框与下划线动作暗示"


### 4. 横幅动作区在 640/768px 不重叠
expected: |
  仪表盘在 640px 与 768px 宽、且缺失字段列表较长时：`完善档案` / `稍后提醒` / `×` 三个控件**互不重叠**；768px 以下动作区换行为整行、左侧 44px 内缩；横幅文案的字段列表换行而非截断。
result: pass
result_note: "640px 与 768px 两档实测：三控件 pairwise 重叠检测 = false，0 横向溢出；9 项缺失字段文案完整换行未截断。动作区 padding-left:44px 生效（按钮 x=81 = 横幅起 37 + 44）；768px 以下 flex-wrap 令动作区独占整行（按钮彼此同行，符合设计）"


### 5. 横幅不抢夺健康评分圆环的视觉焦点
expected: |
  仪表盘上横幅与 HealthScoreCircle 同屏：横幅只有 surface-1 底色 + 单条边框，**无高饱和填充**；视觉焦点仍是评分圆环，横幅不显眼于它。
result: pass
result_note: "实测横幅 background-color=rgb(255,255,255)（纯 surface）、border 0.67px rgba(0,0,0,0.08)、background-image=none（无渐变）；主按钮 600/14px 符合 Contract 9。1280px 截图确认 29 分圆环 + 橙色弧为视觉重心，横幅仅浅灰白条带；评分卡片内另有紧凑「档案完整度 0% · 基础档案」行，双呈现一致"


### 6. 集成测试在可用 Docker 环境下执行（环境限制）
expected: |
  在 Docker daemon 可达的环境运行 `mvn -f hhs-backend/pom.xml verify -Dit.test=PreventionIntegrationTest`：测试实际执行（而非被 Surefire 排除）。此项为既有环境限制，与 Phase 13 代码无关。
result: blocked
blocked_by: third-party
reason: "本机 docker info 报 Server 29.8.1 可用，但 Testcontainers 仍报 'Could not find a valid Docker environment'，PreventionIntegrationTest 4 个用例全部因此 Error（非断言失败）。既有环境限制（WINDOWS.md #3），Phase 13 未引入；需在 Docker socket 对 JVM 可达的环境重跑"


## 复验会话（2026-11-11）：验证指纹漂移后的重新确认

**触发原因**：`verification.status` 报 `stale`。根因不是代码退化，而是 **Phase 14 的迁移提交改动了本阶段 VERIFICATION 的 5 个受覆盖文件**（`theme.css` ×2、`dashboard/Index.vue`、`health/Score.vue`、`prevention/Profile.vue`、`prevention/Risk.vue`），摘要比验证报告新，指纹失配。

**漂移性质**：全部为 Phase 14 把裸 `ElMessage.error` 迁移到 `showError` 的加法式改动。两处需注意的语义点已逐条核对：

- `Risk.vue` 的 `presentStates: true` **不改变校验通道** —— `showError` 中 `validation` 早退（`showError.ts:139`）先于 `presentStates` 判定（`:144`），因此 13-09 承诺的「校验 → 内联 + 汇总、零 toast」不受影响。
- `collectInvalidFieldMessages` 由各页私有副本改为共享导出，函数体逐字未变（`b0e9a16` 的 diff 为纯删除）。

### R-1. 9 条字段说明与三档重要度徽标（13-07 / SC1）
result: pass
evidence: |
  `/prevention/profile` 实测：`.field-hint` 恰 **9** 条；徽标类同时存在 `hhs-imp-badge--required` / `--recommended` / `--optional` 三档；
  承诺句「推荐与可选字段不填也可保存」全文出现 **1** 次。

### R-2. 完整度分段数与图例严格等于字段数（13-01/13-06 / SC1）
result: pass
evidence: |
  `.hhs-completeness-meter__seg` = **9**，`.completeness__legend-item` = **9**，`.completeness__state--missing` = **9**。
  分数显示 `0%`、「已填 0 / 9」、等级 pill `hhs-tier-pill--basic`、无档案时的影响句
  「尚未填写健康档案，评分与风险结果将按通用参考值估算。」；每行图例带 `visually-hidden` 的「未填」文本（无障碍）。

### R-3. 无路由门禁、必填字段仍为 2 个（13-07 / SC5）
result: pass
evidence: |
  `.el-form-item.is-required` 计数 = **2**（性别、出生日期）；页面全文无「无权访问 / 请先完善 / 必须先填写」类门禁文案。
  与阶段前 `git show 6b3c74d` 的 2 个 `required: true` 一致。

### R-4. 仪表盘提示横幅三动作 + 点名缺失项 + 频率上限写入（13-08 / SC4）
result: pass
evidence: |
  `.profile-banner` 文案：「档案完整度 0%，还缺 9 项 / 缺少性别、出生日期、身高、体重、BMI、血型、过敏史、家族病史、生活习惯，健康评分与风险结果会按参考值估算。」
  按钮实测 **3 个**：「完善档案」「稍后提醒」「×」。
  点「稍后提醒」→ 横幅卸载、`localStorage.hhs_profile_reminder_dismissed_at = 2026-11-11T02:54:45.301Z`（频率上限写入生效）。

### R-5. 估算徽标双页渲染且跳转带 ?focus=（13-10 / SC2）
result: pass
evidence: |
  `/health/score` 与 `/prevention/risk` 均渲染 `button` 承载「基于不完整档案估算 · 完善档案」。
  点击后跳转 `http://localhost:5173/prevention/profile?focus=gender` —— `goProfileWithFocus` 的 query 契约成立。

### R-6. 风险页三段式创建路径仍在（13-09 / SC5）
result: pass
evidence: |
  点「创建评估」后面板同时提供「直接创建（按估算）」与「放弃创建」，全程 `.el-message` 计数 **0**
  （放弃创建不产生任何提示，也无 API 调用）。

**环境说明（复验者必读）**：IAB 面板非前台时 `document.hasFocus() === false`，Chrome 会冻结 CSS 动画时间轴
（实测 8 个动画 `currentTime` 恒为 0），弹层 leave 过渡不结束、遮罩滞留。这与 Phase 12 向导自动关闭属同一类环境假阳性，
**不是代码缺陷**；本次所有判定均以 DOM/样式读数与 localStorage 为准，不以遮罩消失或过渡完成为准。

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
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 1

### 复验会话（2026-11-11）

total: 6
passed: 6
issues: 0
skipped: 0
blocked: 0

复验 6 项全部通过，无新增缺陷。唯一 blocked 项仍为既有 Testcontainers 环境限制（`WINDOWS.md #3`），与代码无关。

## Gaps

<!-- 无功能缺口：5 项视觉/布局全部通过，1 项因既有 Docker 环境限制阻塞（非 Phase 13 缺陷） -->
