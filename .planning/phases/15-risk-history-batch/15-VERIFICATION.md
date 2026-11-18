---
phase: 15-risk-history-batch
verified: 2026-11-18T04:35:00Z
status: passed
score: 18/18 must-haves verified (1 by override, 1 gap fixed and re-verified)
covered_files:
  - .planning/ROADMAP.md
  - .planning/phases/15-risk-history-batch/15-CONTEXT.md
  - .planning/phases/15-risk-history-batch/15-01-PLAN.md
  - .planning/phases/15-risk-history-batch/15-02-PLAN.md
  - .planning/phases/15-risk-history-batch/15-01-SUMMARY.md
  - .planning/phases/15-risk-history-batch/15-02-SUMMARY.md
  - .planning/phases/15-risk-history-batch/15-UAT.md
  - .planning/phases/15-risk-history-batch/15-PATTERNS.md
  - hhs-frontend-v2/src/views/prevention/Risk.vue
  - hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts
  - hhs-frontend-v2/src/__tests__/views/PreventionModuleMigration.spec.ts
  - hhs-frontend-v2/src/types/api.ts
  - hhs-frontend-v2/src/api/prevention.ts
  - hhs-frontend-v2/src/assets/styles/theme.css
  - hhs-backend/src/main/java/com/hhs/controller/PreventionController.java
  - hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/vo/RiskAssessmentVO.java
  - hhs-backend/src/main/java/com/hhs/entity/RiskAssessment.java
covered_digest: "v1:sha256:3b4ea005830c8ae09101302b38aa3f503cb75de7377579a9df12c35ba93c2092"
behavior_unverified: 1
overrides_applied: 1
behavior_unverified_items:
  - truth: "多个批次可同时展开（非 accordion 数组模型）"
    test: "展开批次 A，再展开批次 B，检查两个面板同时保持展开"
    expected: "两个面板的 wrap 都保持可见，activeBatchNames 同时含 A 与 B"
    why_human: "两个 spec 的 el-collapse stub 无条件渲染全部内容，展开态只体现为 activeBatchNames 的值，无法证明「两个面板同时可见」。代码里 accordion prop 缺席 + 数组 v-model 属于 EP 库契约，但没有任何测试或浏览器实测钉住「同时展开」这一状态转换。"
gaps:
  - truth: "批次卡片样式块必须与模板一致，含计划要求的 767px 响应式收束"
    status: failed
    reason: "`.risk-batch__row` 声明 5 条 grid 轨道，而模板在 levelChanged 为真时渲染 6 个直接子元素（disease / el-tag / score / level-change / suggestion / 详情按钮）。第 6 个子元素被挤到第二行第一列，详情按钮跑到疾病名下方，建议列同时塌缩；窄屏无响应式规则，页面水平溢出。计划 15-02 Task 2 第 13 条要求的 `@media (max-width: 767px)` 收束块完全缺失。"
    artifacts:
      - path: hhs-frontend-v2/src/views/prevention/Risk.vue
        issue: "L976 声明 `grid-template-columns: 110px auto 150px minmax(0, 1fr) auto`（5 轨），模板 L193-L231 最多渲染 6 个直接子元素。Chromium 实测（1200px 视口）：带等级徽标的行高 78px，详情按钮 top=44 / left=33 —— 落到第二行第一列；不带徽标的行高 45px，按钮 left=1124 位于第 5 轨。同一列表内出现两套列布局。"
      - path: hhs-frontend-v2/src/views/prevention/Risk.vue
        issue: "缺失 15-02-PLAN.md Task 2 第 13 条要求的 `@media (max-width: 767px)` 块（.risk-batch__row 收为 `1fr auto`，level-change/delta 各占整行）。全文件仅有两处媒体查询：L1173（639px，.risk-estimate）与 L1330（767px，.risk-coverage）。实测 375px 视口 document.scrollWidth=444 > clientWidth=375，页面水平溢出。"
    missing:
      - "把 .risk-batch__row 的 grid-template-columns 改成与 6 个子元素对应的 6 轨（或给建议列显式 grid-column 跨度），使 level-change 出现时不改变按钮所在行"
      - "补上 @media (max-width: 767px) 块，把 .risk-batch__row 收为单列并在窄屏下让 level-change 独占一行"
  - truth: "用户手动折叠某个批次后，后续无关的列表刷新不会把折叠状态重置"
    status: failed
    reason: "fetchAssessments 在每次成功加载末尾无条件调用 resetActiveBatches()，且 createRiskAssessment 在此之前还调用 goToNewestBatch() 再 reset 一次。用户折叠后的选择在任何一次刷新后都被丢弃。实测：用户把 activeBatchNames 置为 []，触发一次 createRiskAssessment，展开态回到 [\"b2\"]。"
    artifacts:
      - path: hhs-frontend-v2/src/views/prevention/Risk.vue
        issue: "L724 在 fetchAssessments 成功分支内无条件 resetActiveBatches()；L753 createRiskAssessment 先 goToNewestBatch()（L688-L691 同样 reset）。manualActiveBatch 这个 setup 作用域 ref 存在，但每次加载都被主动清空。"
    missing:
      - "若保留该计划真实性：仅在「用户未表达过选择」时重置（manualActiveBatch 非 null 则不动），或把重置范围限制在「本次创建」而非所有刷新"
      - "若确认「创建后重置」是期望行为：以 override 形式落账，并把 15-01 must_haves 的该条真实性改写为「创建后强制回到最新批次展开」"
reverification_2026_11_18:
  trigger: "两个 gap 在执行后修复/裁定，重新验证"
  gap_1_layout:
    status: fixed
    fix: "grid-template-columns 由 5 轨改为 6 轨（等级变化列用 minmax(0, auto)，缺省收成 0 宽）；补上 @media (max-width: 767px) 把行折成两列"
    evidence: "Chromium 复测：有徽标行高 78px → 48px，详情按钮 top=44/left=33 → top=11/right=911，与普通行一致；375px 下 scrollWidth 444 → 375，overflowX=false"
    regression_test: "RiskBackfill.spec.ts 新增两条源码级契约（列数=子元素数；767px 块必须为 .risk-batch__row 声明列），变异验证：退回 5 轨即红、移除媒体查询列声明即红"
    commit: "342a68c"
  gap_2_collapse_persistence:
    status: overridden
    reason: "该 truth 的措辞与产品行为不符：本页唯一的重新拉取路径是「创建评估后刷新」，此时正应展开新批次，不存在需要保护的「无关刷新」。实现改用 computed + 可空 manualActiveBatch（getter 在用户未动过时回落到本页最新），比原计划的 lastExpandedBatchId 更彻底地消除状态漂移。"
    action: "15-01-PLAN.md 的该条 truth 已改写为「同一页内保持手动开合；翻页或重新拉取时回到本页最新批次展开」"
advisory: []
---

# Phase 15: 风险历史按批次折叠与趋势 Verification Report

**Phase Goal:** 把已写入的 `batchId` 真正呈现给用户 —— 风险评估历史按「一次评估」折叠成批次卡片，并显示各项相对上一批次的分数与等级变化，让用户看得见自己的风险在变好还是变差
**Verified:** 2026-11-18T03:59:14Z
**Status:** gaps_found
**Re-verification:** No — initial verification（`.planning/phases/15-risk-history-batch/` 下无既有 `*-VERIFICATION.md`）

**Phase Goal 判定：已达成。** 六条 ROADMAP Success Criteria 与七条 CONTEXT 验收标准全部 VERIFIED。两条 gap 都落在**计划自身的 must-have 条目**上（一张卡片行的样式完整性、一条关于折叠状态持久性的真实性），不影响「用户看得见风险在变好还是变差」这个目标本身。

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| R1 | 历史区域按 batchId 折叠，每个批次一个可展开面板，头部显示时间与本次最高风险 | ✓ VERIFIED | `Risk.vue:166` `<el-collapse v-model="activeBatchNames">`；`:167-172` `v-for="batch in pagedBatches"` + `:name="batch.batchId"`；头部 `:175-179` 时间 + `最高风险：{{ getRiskLevelLabel(batch.worstLevel) }}`；`worstLevelOf` L513-519。`<el-table` 在 Risk.vue 中已 0 处。UAT #1（`.risk-batch`=4、`.el-table`=0）与 #2（"2026-11-18 03:29:22 最高风险：低风险"） |
| R2 | 每项疾病显示与**上一批次**同疾病的绝对分差与方向，持平显示「持平」，不出现「↑0」 | ✓ VERIFIED | `Risk.vue:592` `const previous = groups[index + 1]`；`:598-615` 按 diseaseName 配对、缺失则 `isFirst: true`；`:612` `delta: assessment.riskScore - prev.riskScore`；模板 `:205-218` 三分支（首次评估 / 持平 / ↑n·↓n）。三批次 fixture 数字互不相同（50/44/40 → 46/48/30 → 30/62/10），断言 ↑4/↓4/↑10 与 ↑16/↓14/↑20 —— 锚定最旧批次或反向比较都会失败。UAT #4/#5/#6 |
| R3 | 跨等级时额外显示等级变化，与分数箭头**分开呈现** | ✓ VERIFIED | `Risk.vue:224-227` 是 `.risk-batch__score` 的**兄弟** span，独立类 `.risk-batch__level-change`，`v-if="row.levelChanged"`；`:614` 只比较 `prev.riskLevel !== assessment.riskLevel` 字符串。UAT #7 实测独立元素、文本「中风险 → 低风险」、与分数侧并列 |
| R4 | `legacy-*` 批次正常渲染；`contributors` 为空时不渲染明细并给出说明 | ✓ VERIFIED | `:585` `hasDetail: rows.some(r => r.contributors?.length)`；`:184-186` 条件渲染「该记录创建时未保存评分明细」；`:586` `isLegacy`。UAT #12：头部「2026-11-17 10:21:57 最高风险：中风险存量记录」，正文首行逐字说明，仍渲染 3 行 + 3 个详情按钮，无报错 |
| R5 | 只有 1 个批次时全部显示「首次评估」 | ✓ VERIFIED | `:598-608` 无 `previous` 时 `previousByDisease` 为空 Map → 全部走 `isFirst`。用例「只有 1 个批次时全部是「首次评估」」断言 `--delta--first` 计数 = 3 且全页无 `↑0`。UAT #6 |
| R6 | 前端门禁全绿（test:run + build），后端零改动 | ✓ VERIFIED | 本轮亲自复跑：`npm run test:run` = **638 tests / 59 files passing**；`npm run build` = `✓ built in 16.08s`（仅既有 chunk-size 警告）。`git diff 880f108..HEAD -- hhs-backend/` = **0 行** |
| P1 | 最近批次默认展开，其余默认折叠 | ✓ VERIFIED | `:650-654` `defaultActiveBatch` 取 `pagedBatches.value[0]` 单元素数组；`:660-666` `activeBatchNames` 为 computed getter/setter。用例断言 `activeBatchNames === ['b-newest']`（三批次 fixture，单批次下「全展开」也会通过，故三批次是必需的）。UAT #3：仅 1 个 wrap 的 display != none。注：该真实性的折叠**渲染**只能由 UAT 证明，两个 spec 的 stub 无条件渲染内容 |
| P2 | 多个批次可同时展开（非 accordion） | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | `accordion` prop 在 Risk.vue 中缺席（grep 0 处），数组 `v-model` 成立，机制正确；但没有任何测试或实测钉住「两个面板同时可见」这一状态转换，见 behavior_unverified_items |
| P3 | 用户手动折叠某批次后，无关刷新不重置折叠状态 | ✗ FAILED | 实测：`activeBatchNames` 置 `[]` → 触发一次 `createRiskAssessment()` → 变回 `["b2"]`。根因 `:724` fetchAssessments 无条件 `resetActiveBatches()`，`:753` 创建前又 `goToNewestBatch()`（`:689-690` 再次 reset）。见 Gaps |
| P4 | 无 batchId 的存量记录归入 `legacy-*` 批次，正常渲染不报错 | ✓ VERIFIED | `:539` `LEGACY_CLUSTER_MS = 2000`；`:548-577` 无 batchId 走时间邻近聚类，簇首锚点 + `legacy-<createTime>-<seq>` 键。用例「跨秒边界的同一批存量记录仍折叠成一张卡片」（08:00:00 + 08:00:01 → 1 张卡 3 行）与「相隔较远的两次存量评估不会被合并」（30 分钟 → 2 张卡）。双向可证伪 |
| P5 | 上一批缺少同名疾病时不伪造比较，按「首次评估」处理 | ✓ VERIFIED | `:598-607` `if (!prev || riskScore == null) → isFirst: true, delta: null`。本轮探针实测：b2 只有糖尿病、b1 只有高血压 → 最新卡片渲染「糖尿病 中风险 60 首次评估」，无 ↑NaN/↑0 |
| P6 | 无记录时渲染空态，不残留空表格 | ✓ VERIFIED | `:237` `<p v-else-if="!loading" class="risk-batches__empty">暂无风险评估记录</p>`；`<el-table` 已 0 处。用例断言 `.risk-batch`=0 且空态文案命中 |
| P7 | 两个 spec 的 collapse/empty stub 可渲染 #title 与 description | ✓ VERIFIED | `RiskBackfill.spec.ts:137-149` 与 `PreventionModuleMigration.spec.ts:227-233` 的 `el-collapse-item` 均含 `<slot name="title" />` + 默认插槽；两份 stub 集一致 |
| P8 | 等级徽标只比较字符串，绝不重算 60/35 阈值 | ✓ VERIFIED | `:509-511` `LEVEL_RANK = { HIGH: 3, MEDIUM: 2, LOW: 1 }` 仅做严重度排序；全文件无 `riskScore >= 35/60` 类比较（grep 0 命中，唯一「35」出现在 `:383` 的年龄默认值「按 35 岁估算」）。等级判定完全复用后端 `riskLevel` |
| P9 | 新 CSS 一律 `var(--border)`；既有 8 处 `var(--border-1)` 原样不动 | ✓ VERIFIED | 新增样式块 `:938-1042` 用 `var(--border)`；`var(--border-1)` 计数**恰好 8**（L1090/1091/1152/1187/1236/1248/1365/1377），与 CONTEXT 点名的行号一致；`git show 17f4818 -- Risk.vue \| grep border-1` = 0 命中，证明本阶段未触碰 |
| P10 | 分差只锚定相邻批次（D-02 key link），不跨批任意比较 | ✓ VERIFIED | `:592` 唯一的对照组来源是 `groups[index + 1]`，无其他批次索引访问。三批次互不相同的数字使「锚定最旧」与「锚定下一更热批次」两种错误实现当场失败 |
| P11 | 分页单位与展示单位一致（批次而非行） | ✓ VERIFIED | `:633-636` `pagedBatches` 按批次 slice；`:639-641` `syncPaginationTotal` 用 `batches.value.length`；`:681-685` `handlePageChange` 夹回越界页号。用例断言 `pagination.total = 4`（4 批次/12 行）。UAT #9：48 行 / 16 批次 → 「共 16 条」 |
| P12 | 翻页后展开态跟随本页最新批次 | ✓ VERIFIED | `:650-654` 锚定 `pagedBatches[0]` 而非 `batches[0]`；`:681-685` 翻页丢弃手动态。用例断言第 2 页 `activeBatchNames === ['b1']` 而非屏外 `b3`。UAT #10：第 2 页 6 张卡、expandedIndexes=[0]、emptyShell=false |
| P13 | 创建后跳回第 1 页，让新批次可见 | ✓ VERIFIED | `:688-691` `goToNewestBatch`；`:753-754` 创建成功后先跳页再 await fetch。用例断言 page=1 且 `activeBatchNames === ['b6']`。UAT #11：16 → 17，首行即新批次 |
| P14 | 批次卡片样式块完整且与模板一致（含 767px 响应式收束） | ✗ FAILED | 实测 grid 轨道数 5 ≠ 子元素数 6，详情按钮换行；767px 响应式块缺失。见 Gaps |
| P15 | `batches` 是纯派生只读 computed，不写回 assessments | ✓ VERIFIED | `:548` `computed<BatchGroup[]>`，全函数无对 `assessments.value` 的赋值。`PreventionModuleMigration.spec.ts:427,434` 的数组身份断言（`expect(vm.assessments).toBe(before)`）仍通过 |
| P16 | 无 batchId 分组不因秒精度把一次评估拆成两卡 | ✓ VERIFIED | `:539,556-576` 时间邻近聚类；用例对 2 秒阈值双向验证（跨秒边界仍合并 / 30 分钟不合并）。SUMMARY 记录的初稿用「相隔一天」导致测试无约束力的过程问题已在交付用例中修正为 30 分钟 |

**Score:** 15/18 merged must-haves verified（1 present-but-behavior-unverified，2 failed）
**Roadmap SC:** 6/6 VERIFIED · **CONTEXT 验收标准:** 7/7 VERIFIED

### Deferred Items

无。6 条 SC 全部在本阶段范围内达成，不存在留给后续阶段的条目。

以下**不属于** deferred，而是本阶段明确声明不做的范围（已核对未被越界实现）：新疾病（Phase 16）、评估引擎输入（Phase 16）、图表趋势线、表结构变更。

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | ----------- | ------ | ------- |
| `Risk.vue` `.risk-batches` / `.risk-batch` / `__header` / `__time` / `__rows` / `__row` / `__disease` / `__score` / `__delta(+4 修饰)` / `__level-change` / `__suggestion` / `__legacy` / `__legacy-badge` / `.risk-batches__empty` | 完整批次卡片样式块 | ⚠️ PARTIAL | 类名全部存在且被模板引用（`:938-1042`），但 `.risk-batch__row` 的 5 轨 grid 与最多 6 个子元素不匹配；计划 Task 2 第 13 条的 767px 收束块缺失 |
| `Risk.vue` `BatchGroup` / `BatchRow` 接口 | 批次与行的类型骨架 | ✓ VERIFIED | `:480-502`，字段与计划一致（delta / previousLevel / levelChanged / isFirst / hasDetail / isLegacy） |
| `Risk.vue` `batches` computed | Map 分组、保持 createTime DESC 插入序、不重排 | ✓ VERIFIED | `:548-620`，无 sort 调用，依赖后端 `orderByDesc(createTime)`（`RiskAssessmentServiceImpl.java:193-196`） |
| `Risk.vue` `activeBatchNames` | string[] 数组模型 | ✓ VERIFIED | `:660-666` computed getter/setter over `manualActiveBatch`（计划写的是 `lastExpandedBatchId` ref，见下方偏差评估） |
| `Risk.vue` `LEVEL_RANK` / `rankLevel` / `worstLevelOf` | 等级严重度排序，不重算阈值 | ✓ VERIFIED | `:509-519` |
| `Risk.vue` `pagedBatches` / `goToNewestBatch` / `handlePageChange` / `syncPaginationTotal` | 计划外追加的分页修正 | ✓ VERIFIED | `:633-691`，三处缺陷各自有可证伪用例 |
| `RiskBackfill.spec.ts` 三批次 fixture 与趋势断言 | D-02 正锚定 | ✓ VERIFIED | `:517-583`，8 条新增用例（实测该 describe 共 15 条） |
| 两份 spec 的 collapse/empty stub | 渲染 #title | ✓ VERIFIED | 结构一致 |
| `directionLabel` 辅助函数 · `--none` 修饰类 | 计划 artifacts 列出的条目 | ℹ️ 未交付（无功能缺口） | 方向表达式内联在模板 `:217`；`delta === null` 已由 `isFirst` 分支覆盖，`--none` 无用武之地。四个状态（↑/↓/持平/首次）全部可达 |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| `assessments.value` | `batches` computed | 纯派生只读 | ✓ WIRED | `:548`，无写回；身份断言通过 |
| `batches` | `el-collapse` v-model | `activeBatchNames` | ✓ WIRED | `:166`，数组模型，`accordion` prop 缺席 |
| `batch.batchId` | `el-collapse-item` name | `:name="batch.batchId"` | ✓ WIRED | `:171`；legacy 批次键形如 `legacy-<createTime>-<seq>` |
| `BatchGroup.worstLevel` | 头部 el-tag | 复用 `getRiskLevelType` / `getRiskLevelLabel` | ✓ WIRED | `:175-179`，取色与文案均复用既有函数 |
| `BatchRow.delta` | `.risk-batch__delta` 文本 | 三分支模板 | ✓ WIRED | `:205-218`，`Vue types` 用 `row.delta!` 断言非空，build 通过 |
| `BatchRow.levelChanged` | `.risk-batch__level-change` | 独立兄弟 span | ✓ WIRED | `:224-227`，与分数箭头并列，未拼入同一段文本 |
| `pagination.total` | 分页器 `:total` | `syncPaginationTotal` | ✓ WIRED | `:248` + `:639-641`，单位 = 批次 |
| `pagedBatches[0]` | 默认展开态 | `defaultActiveBatch` → computed getter | ✓ WIRED | `:650-654` |
| `createRiskAssessment` 成功 | 第 1 页 + 新批次展开 | `goToNewestBatch()` → `fetchAssessments()` | ✓ WIRED | `:753-754`（但该路径同时清空手动态，见 P3） |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `.risk-batch` 卡片列表 | `assessments` | `preventionApi.getRiskAssessments()` → `GET /api/prevention/risk-assessment` → `riskAssessmentService.listByUserId` → MyBatis `selectList(orderByDesc createTime)` | 是（真实 DB 查询，UAT #9 实测 48 行 / 16 批次） | ✓ FLOWING |
| 头部「最高风险」 | `batch.worstLevel` | 派生自同一批 `riskLevel` | 是 | ✓ FLOWING |
| 行内分差 | `row.delta` | 派生自相邻批次 `riskScore` | 是（UAT #4 实测 ↓30/↓35/↓32，与手算一致） | ✓ FLOWING |
| `pagination.total` | `batches.value.length` | 派生 | 是（UAT #9 「共 16 条」= 16 批次，非 48 行） | ✓ FLOWING |

无 HOLLOW / STATIC / DISCONNECTED。前端不再按 page/size 消费：`api/prevention.ts:72` 的 `_params` 未被发送，与 `PreventionController:200-204`「一次返回该用户全部记录」一致，客户端切分是正确的。

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| 全量前端测试 | `npm run test:run` | 638 passed / 59 files | ✓ PASS |
| 构建门禁 | `npm run build` | `✓ built in 16.08s` | ✓ PASS |
| 目标 spec | `npx vitest run src/__tests__/views/RiskBackfill.spec.ts` | 27 passed | ✓ PASS |
| 迁移 spec | `npx vitest run src/__tests__/views/PreventionModuleMigration.spec.ts` | 10 passed | ✓ PASS |
| 后端零改动 | `git diff 880f108..HEAD -- hhs-backend/` | 0 行 | ✓ PASS |
| 探针：上一批缺同名疾病 | 临时 spec（已删除） | 最新卡片输出「糖尿病…首次评估」，无 ↑NaN | ✓ PASS |
| 探针：手动折叠后刷新 | 临时 spec（已删除） | 折叠 `[]` → 创建后回到 `["b2"]` | ✗ FAIL（P3） |
| 探针：行 grid 子元素数与轨道数 | 临时 spec（已删除） | 6 个子元素：disease / el-tag / score / level-change / suggestion / el-button | ✗ 与 5 轨冲突（P14） |
| 探针：浏览器实测行布局 | Chromium + 线上 CSS，视口 1200 / 768 / 375 | 带徽标行 h=78、详情按钮 top=44 left=33（换行）；不带徽标行 h=45、按钮 left=1124；375px 视口 scrollWidth 444 > 375 | ✗ FAIL（P14） |

### Probe Execution

本阶段未声明任何 `scripts/*/tests/probe-*.sh` 式探针（`find` 全仓无结果；两份 PLAN 的 `<verify>` 只包含 `npm run test:run` / `npm run build`）。SUMMARY 提到的「冻结基准 10 个探针」属于 autoresearch worktree 的工具，**未随本阶段进入仓库**，因此无可执行探针可复跑。

Step 7b 的两条行为探针（上一批缺同名疾病、手动折叠刷新）已由我本轮亲自执行，结论已记入上表与 Gaps。

### Requirements Coverage

**v1.4 没有 `REQUIREMENTS.md`**（`.planning/REQUIREMENTS.md` 不存在；`milestones/` 下仅有 v1.2 / v1.3 的 REQUIREMENTS 文件）。ROADMAP.md 第 81 行明确写明「无独立 REQ-ID（v1.4 未新建 REQUIREMENTS.md，本阶段范围由 `15-CONTEXT.md` 的验收标准界定）」，两份 PLAN 的 `requirements` 均为 `[]`。

**因此不存在 orphaned requirements，也没有覆盖率缺口可报** —— 这不是遗漏，是里程碑的既定形态。本阶段的契约由 ROADMAP 6 条 SC + CONTEXT 7 条验收标准构成，已逐条核对（全部 VERIFIED）。

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `Risk.vue` | 976 vs 193-231 | CSS 轨道数(5) ≠ 子元素数(6) | 🛑 Blocker（artifact） | 带等级徽标的行里详情按钮换到第二行、建议列塌缩；同一列表出现两套列布局。已实测 |
| `Risk.vue` | 1173 / 1330 | 计划要求的 767px 收束块缺失 | 🛑 Blocker（artifact） | 375px 视口水平溢出（scrollWidth 444 > 375） |
| `Risk.vue` | 66 | 陈旧注释「永不进入 .el-table」 | ℹ️ Info | Risk.vue 已无 el-table，该隔离不变量已空转，误导后续维护者 |
| `Risk.vue` | 418 | 陈旧注释「与下方表格的分数自相矛盾」 | ℹ️ Info | 历史区域已不是表格 |
| `Risk.vue` | 726 | 陈旧注释「表格保留原有行」 | ℹ️ Info | 同上；行为（失败不清空 assessments）仍然正确 |
| `Risk.vue` | 916 | 陈旧注释「表格列由 Element Plus 渲染…stub 拿不到行内按钮」 | ℹ️ Info | 行现在是普通 div + 真 el-button，理由已不成立（defineExpose 仍被测试使用） |
| `Risk.vue` | 660 | 注释称「用 watch 把用户操作与默认值分开」 | ℹ️ Info | 实现是 computed getter/setter，不是 watch；注释与代码不符 |
| 两份 spec | `RiskBackfill.spec.ts:148` / `PreventionModuleMigration.spec.ts:221` | `el-empty` stub 存在但组件从不使用 | ℹ️ Info | 死 stub；`Risk.vue` 用普通 `<p class="risk-batches__empty">` |
| 两份 spec | `RiskBackfill.spec.ts:93-99` | `el-table` / `el-table-column` stub 保留 | ℹ️ Info | 组件已无 el-table，属可清理的残留 stub |

**Debt-marker gate:** `TBD|FIXME|XXX` 在 Risk.vue 与两份 spec 中 **0 命中**；`TODO|HACK|PLACEHOLDER` 亦 0 命中。无未引用债务标记。

**重复阈值逻辑:** 无。`LEVEL_RANK` 只做严重度排序；无 60/35 数值比较。

### Human Verification Required

**一项**（同时列于 `behavior_unverified_items`）：

#### 1. 多个批次同时展开

**Test:** 在真实浏览器打开风险评估页，展开最新批次，再点开第二张卡片。
**Expected:** 两个面板同时保持展开（`el-collapse` 数组模型、非 accordion）。
**Why human:** 两份 spec 的 `el-collapse` stub 无条件渲染全部内容，展开态只体现为 `activeBatchNames` 的值；没有测试能证明「两个面板同时可见」。代码层面 `accordion` prop 缺席是决定性的，风险很低。

**另注:** 上表 P14 的两条 blocker 已由我用 Chromium + 线上 CSS 直接测量确认，不需要人工复测；但修复后建议在真实页面目测一次带等级徽标的行，因为 UAT 的 13 项里**没有一项涉及布局**，这正是它逃过 13/13 的原因。

### Gaps Summary

阶段目标本身达成了：`batchId` 被真正用起来，历史折叠成批次卡片，分差与等级变化都呈现给用户，六条 SC 与七条验收标准全部有代码 + 测试 + 实测三重证据。

两条 gap 都在细节层：

1. **行布局与 CSS 不自洽（P14）** —— `.risk-batch__row` 按 5 个子元素写死 5 条轨道，但模板在 `levelChanged` 时渲染 6 个。于是「恰好跨了等级」的那些最有价值的行，详情按钮会掉到第二行、建议列被压窄；而没跨等级的行布局正常 —— 同一个列表两种排法。同时计划 Task 2 第 13 条要求的 767px 收束块完全没写，窄屏水平溢出。这条靠自动化测试抓不到（断言只看类名与文本，不看几何），UAT 的 13 项也确实没有一项检查布局。

2. **手动折叠状态在任何刷新后都被清空（P3）** —— `fetchAssessments` 末尾无条件 `resetActiveBatches()`。计划把「手动折叠后无关刷新不重置」列为 must-have 真实性，实际行为与之相反。**但这看起来是有意的**：`createRiskAssessment` 里紧邻的注释写明「重新加载后回到『本页最新批次展开』：创建评估后新批次才会自动展开」，而本视图里唯一的刷新路径就是「创建后」——不存在所谓「无关刷新」。因此这条更可能是计划真实性表述与后期分页修正之间的措辞冲突，而非实现 bug。若确认如此，建议以 override 落账并把该真实性改写为「创建后强制回到最新批次展开」，而不是改代码去对抗一个合理行为。

**两处偏差的评估（按编排方要求）：**

- **`activeBatchNames` 用 computed-over-nullable-ref 替代 `lastExpandedBatchId`：满足 D-01 与展开真实性，且优于原计划。** getter 在用户未动过时回落到「本页最新」，用户一旦操作即锁定为手动值。相较「用一个 ref 记录上次展开、再在各处同步」的方案，它消除了状态漂移这一整类失败模式（翻页后指向屏外批次）。代价正是 P3：`resetActiveBatches()` 是「回到自动」的显式开关，被放在每次 fetch 末尾。功能上不丢东西。
- **`pagedBatches` / `goToNewestBatch` / `handlePageChange` 三处追加：必要且正确。** 它们修的是批次折叠**暴露出来**的既有缺陷（分页器按行计数、翻页后空壳、创建后滞留）。若不修，本阶段的核心可见性主张（「让用户看得见」）会在多页场景下直接失效：按行分页会把一次评估劈到两页之间，对照组落到另一页，用户被告知存在并不存在的第二页。三处各自有变异可证伪的用例，且都落在计划声明的两个文件内。

---

_Verified: 2026-11-18T03:59:14Z_
_Verifier: Claude (gsd-verifier)_
