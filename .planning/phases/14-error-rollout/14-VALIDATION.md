---
phase: "14"
slug: "error-rollout"
status: draft
nyquist_compliant: true
wave_0_complete: false
created: "2026-11-10"
---

# Phase 14 — Validation Strategy

> 全站收尾排查与回归验证的逐需求验证契约。
> 来源：`14-RESEARCH.md` §Validation Architecture + `14-UI-SPEC.md` Appendix A-2。

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5 + Mockito（`spring-boot-starter-test`）；Surefire 3.0.0；Failsafe + Testcontainers（本环境不可用，见 WINDOWS #3） |
| **Framework (frontend)** | Vitest 4.1.7 + @vue/test-utils（`happy-dom`, `globals: true`） |
| **Config file** | `hhs-backend/pom.xml`；`hhs-frontend-v2/vitest.config.ts` |
| **Quick run (backend)** | `mvn -f hhs-backend/pom.xml test -Dtest=<Class>` |
| **Quick run (frontend)** | `npm --prefix hhs-frontend-v2 run test:run -- <spec>` |
| **Full suite (backend)** | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` |
| **Full suite (frontend)** | `npm --prefix hhs-frontend-v2 run test:run` |
| **Lint / 类型** | `npm --prefix hhs-frontend-v2 run lint:check` + `npm --prefix hhs-frontend-v2 run build` + `mvn -f hhs-backend/pom.xml checkstyle:check` |
| **基线（2026-11-10 实测）** | 前端 **445 tests / 37 files**；后端 **845 tests / 0 failures** |
| **E2E** | `npx playwright test e2e/quick-input.spec.ts`（需后端 + MySQL 3307 + Redis 6380 运行） |

> ⚠ **不要**把 `format:check` 加入门禁 —— 它当前已在 98 个文件上失败（既有状态，非本阶段引入）。
> ⚠ `*IntegrationTest` 归 Failsafe，Surefire 不执行；本阶段新增测试一律用 `*Test`/`*.spec.ts` 命名。

---

## Sampling Rate

- **After every task commit:** 该任务所属语言的单类 / 单 spec 命令
- **After every plan wave:** `mvn -f hhs-backend/pom.xml clean verify -DskipITs` + `npm --prefix hhs-frontend-v2 run test:run`
- **Before `/gsd:verify-work`:** 后端 ≥845 全绿 + 前端 ≥445 全绿 + `checkstyle:check` + `lint:check` + `build` 全过
- **Max feedback latency:** 90 秒

---

## Per-Task Verification Map

| Req ID | Behavior | Test Type | Automated Command | File Exists | 预期失败信号 | Status |
|--------|----------|-----------|-------------------|-------------|-------------|--------|
| A-1 前置 | 两个新 `ErrorKind`（`forbidden` / `notFound`）插入在第 3 步(401)之后、第 4 步(断网)之前 | unit | `npm … -- src/__tests__/utils/appError.spec.ts` | ⚠️ 需扩展 | 插入位置错误 → 新 kind 永不产出 | ⬜ |
| A-2 ① | envelope 403（HTTP 200 + code 403）被分类为 `forbidden`，**不是** `business` | unit | 同上 | ❌ W0 | `expected 'forbidden' but got 'business'` | ⬜ |
| A-2 ② | envelope 404（`health.profile_not_found`）被分类为 `notFound` | unit | 同上 | ❌ W0 | `expected 'notFound' but got 'business'` | ⬜ |
| A-2 ③ | 真实 HTTP 403 被分类为 `forbidden`（状态块通道可达） | unit | 同上 | ❌ W0 | 状态块永不渲染 | ⬜ |
| A-2 ④ | `Profile.vue` / `profileCompleteness.ts` 的 `status`/`code` 分支不回归 | unit | `npm … -- src/__tests__/stores/profileCompleteness.spec.ts` | ✅ 存在 | 既有用例转红 | ⬜ |
| ERR-03 | 13 个待接入表单的服务端 `fieldErrors` 内联到对应 `el-form-item` + 中文标签 | unit (component) | 各表单 spec | ❌ W0 | `el-form-item__error` 不存在 / 显示英文 key | ⬜ |
| ERR-03 | `FIELD_LABELS` 覆盖 27 项（22 + mood 5），精确断言同步更新 | unit | `npm … -- src/__tests__/utils/errorMessages.spec.ts` | ✅ 存在（需扩断言） | 未覆盖字段显示原始 key | ⬜ |
| ERR-05 | 校验/服务端报错后不清空输入、弹窗保持打开 | unit (component) | 同上各表单 spec | ❌ W0 | 输入被清空 / 弹窗关闭 | ⬜ |
| ERR-07 | 校验 → 内联（零 toast）；网络 → toast 带重试；403/404 → 状态块（零 toast） | unit | `npm … -- src/__tests__/utils/showError.spec.ts` | ⚠️ 需扩展 | 校验失败弹 toast / 403 弹 toast | ⬜ |
| ERR-07 | `ErrorState` 支持 `scope="page"` + `actions[]`，403 与 404 五通道可辨 | unit (component) | `npm … -- src/__tests__/components/ErrorState.spec.ts` | ✅ 存在（14 it / 26 expect，需扩） | 五通道任一缺失 | ⬜ |
| ERR-08 | 8 处手写 cancel 守卫 + 1 处 `CanceledError` + 3 处静默吞错全部清理 | unit | 各所属组件 spec | ❌ W0 | `AbortError` 仍弹红提示 / 真失败被静默吞 | ⬜ |
| ERR-08 | `CANCEL_SENTINELS` / `AbortError` 短路对所有入口生效 | unit | `npm … -- src/__tests__/utils/appError.spec.ts` | ✅ 存在 | cancel 被归为 error | ⬜ |
| D-01 | 58 处迁移为 `showError`；12 处不可达 `else` 分支**删除**；5 处客户端校验不弹 | 结构性 | `grep -rn "ElMessage.error" src/ \| grep -v __tests__ \| grep -v utils/showError` | ❌ W0 | 用户可见路径残留 > 0 | ⬜ |
| D-02 | store 内错误一律上抛（8 处），success toast 保留 3 处 | 结构性 | 同上（仅 stores 范围） | ❌ W0 | store 内仍有 `ElMessage.error` | ⬜ |
| F4 | `showError` 的 `needsVNode` 覆盖 `notFound`/`forbidden` + `presentStates`（否则落 4000ms 分支） | unit | `npm … -- src/__tests__/utils/showError.spec.ts` | ⚠️ 需扩展 | `duration` 为 4000 而非 0 | ⬜ |
| 回归 | 后端全量 | integration | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` | ✅ | < 845 或有失败 | ⬜ |
| 回归 | 前端全量 | unit | `npm --prefix hhs-frontend-v2 run test:run` | ✅ | < 445 或有失败 | ⬜ |
| 回归 | E2E 成功文案契约 | E2E | `npx playwright test e2e/quick-input.spec.ts` | ✅ | 失败（需后端运行） | ⬜ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `hhs-frontend-v2/src/__tests__/utils/appError.spec.ts` 扩展 — A-2 的 ①②③ 三条分类断言
- [ ] `hhs-frontend-v2/src/__tests__/components/ErrorState.spec.ts` 扩展 — `scope="page"` + `actions[]` + 五通道
- [ ] `hhs-frontend-v2/src/__tests__/utils/showError.spec.ts` 扩展 — F4 的 `duration: 0` 断言
- [ ] `hhs-frontend-v2/src/__tests__/utils/errorMessages.spec.ts` 扩展 — 字典 18→44、`FIELD_LABELS` 17→44（含 mood 5）
- [ ] 13 个待接入表单的组件 spec — ERR-03/05（由实现该表单的任务创建）
- [ ] 框架安装：**无需** —— 两端基建就绪

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 停后端后逐页点保存/加载，每个动作恰好一条中文可行动提示 | 成功标准 3 | 需真实网络失败与真实轮询节奏 | 停 `8082` → 依次访问列表页/表单页/详情页 → 数 `.el-message` 条数（每动作应恰 1）→ 恢复后端 |
| 断网 60 秒轮询不产生重复 toast | 成功标准 3 | 同上，依赖真实心跳间隔 | DevTools 切 Offline → 停留 60 秒 → 确认 toast ≤ 1（去重生效） |
| 403 全页解释页的真实观感 | ERR-07 | 页面几何/占比需人眼判断 | 以普通账号访问 `/settings/device-platform` → 确认全页解释页（非 toast）、有「返回首页」 |
| 404 内容区解释块 | ERR-07 | 同上 | 访问一个不存在的资源 id → 确认内容区 `ErrorState`（非全页）、有「返回列表」/「重新加载」 |
| 错误的输入框描边真的可见 | ERR-03 | `theme.css` 曾用 `!important` 抹掉 EP 的错误描边，需真实渲染确认 | 提交一个必填留空的表单 → 确认红框描边出现（不只是红字） |
| 日期范围选择器出错且聚焦时仍有焦点指示 | ERR-03 / WCAG 2.4.7 | `.is-active` 通道需真实控件 | 让 daterange 出错并聚焦 → 确认有焦点光晕（不是只有红边） |
| route-level 404 catch-all | 研究决议 3 | 需真实路由跳转 | 手输 `/no-such-page` → 确认渲染 404 解释页而非白屏 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-11-10（research 阶段收口）

---

## 门禁命令完整清单（供 14-xx 最终回归计划直接使用）

```bash
# 后端
mvn -f hhs-backend/pom.xml clean verify -DskipITs     # 期望 ≥845 tests, 0 failures
mvn -f hhs-backend/pom.xml checkstyle:check           # 期望 0 violations

# 前端
npm --prefix hhs-frontend-v2 run test:run             # 期望 ≥445 tests / 37 files
npm --prefix hhs-frontend-v2 run lint:check           # 期望 0 errors
npm --prefix hhs-frontend-v2 run build                # 期望 green（含 vue-tsc -b）

# 结构性（D-01/D-02 残留归零）
grep -rn "ElMessage\.error" hhs-frontend-v2/src/ --include="*.vue" --include="*.ts" \
  | grep -v "__tests__" | grep -v "utils/showError"   # 期望 0（用户可见路径）

# E2E（需 docker compose up -d 且 8082 UP）
npx --prefix hhs-frontend-v2 playwright test e2e/quick-input.spec.ts
```

**不要**加入 `format:check`（当前 98 文件已失败，既有状态）。
