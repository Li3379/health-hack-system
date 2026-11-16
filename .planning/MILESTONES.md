# Milestones

## v1.3 引导式配置与友好错误反馈 (Shipped: 2026-11-17)

**Phases completed:** 4 phases, 41 plans, 44 tasks

**Key accomplishments:**

- 设备同步与 OAuth 回调链路的 28 处用户可见英文字面量归零，页面/信封不再回显原始异常、provider 描述或 platform 字符串，新增无 Docker 的回调页回归门禁
- Single `classify()` now owns all four-band semantics: a configured warning threshold actually produces a WARNING (FIX-01), proven by a 30-test D-18 boundary matrix that previously could not exist because the class had no tests (FIX-05).
- metricKey aliases canonicalized on both write paths and every threshold/alert-rule read converted to alias-set IN + deterministic selectList pick, so a threshold saved as `glucose` fires for the engine querying `bloodGlucose` — without the `selectOne` TooManyResultsException landmine.
- Idempotent transactional SQL script that dedupes-then-renames all 19 MetricKeyNormalizer alias keys across user_threshold / alert_rule / alert_template / health_alert (D-14 keep-latest), plus canonical schema seeds and a documented code-before-script deployment order.
- Authoritative `@ValidThresholdRange` bean validation on `UserThresholdRequest` (two dot-free field errors per broken pair, registry hard limits) and metadata now carries `alertable` + backend-computed `recommended` for all 23 metrics under a bumped `v1.2-metric-meta` ETag.
- Typed metadata contract (alertable + seed-mirrored recommended on all 23 metrics, grouped store computeds) plus a tested pure `thresholdValidation` module mirroring the backend's ordering and hard-limit rules.
- 五段式阈值分区预览条（严重低/警告低/正常/警告高/严重高）：null 边界渲染 — 并朗读 未设置，数值随 draft 纯 computed 实时更新，新增 warning/normal 两枚双主题 tint token，9 条组件测试钉死契约（THR-04）
- 403 and 404 are now first-class error kinds with a zero-toast state channel, and one real page proves the whole chain from axios response to rendered page block.
- The phase's four structural checks now fail tests instead of requiring a remembered grep, and the full gate is recorded as real output: backend 845/0, frontend 642 across 59 files, build and lint and checkstyle green, E2E success-copy contract intact.

**Audit:** `.planning/milestones/v1.3-MILESTONE-AUDIT.md` — 29/29 requirements, 4/4 phases verified, 26/26 wiring, status `passed`.

**Closeout note:** The audit's integration pass found three real defects and they were fixed before archiving, not deferred:
- `prevention/Profile.vue` save went fully silent when the profile row had been deleted elsewhere (`ee4d534` made the envelope 404 honest → `notFound` → `showError`'s zero-toast default, and that page has no `ErrorState`). Added `presentStates`.
- The same page's create-mode guard tested `err.status === 404` / `err.code`, both permanently false for an HTTP-200 envelope with no `errorCode`, so create mode never auto-opened. Now reads the normalized `kind`.
- Route-level 404 inherited `requiresAuth` from its parent layout record, bouncing unauthenticated unknown URLs to `/login`. Now sets `requiresAuth: false` explicitly, with the test asserting `resolve().meta` rather than the unmerged record.

**Known gaps carried forward:** `FileUploadController:104` still appends `e.getMessage()` to a user-facing 500 (only known English-leak path, ERR-02); `IntelligentAlertService` holds a dormant second copy of threshold band-comparison logic that would fork FIX-01's single source if ever wired; 6 dictionary keys and 5 API methods have no consumers; no request-cancellation wiring exists, so ERR-08's abort path is correct but not exercisable.

---

## v1.2 全站健康与保健指标录入标准化与体验对齐 (Shipped: 2026-11-05)

**Phases completed:** 7 phases, 15 plans, 11 tasks

**Key accomplishments:**

- Immutable single source of truth (SSOT) metric registry for 23 metrics, multi-variant key normalizer, and public HTTP-cached metadata endpoint
- Jakarta class-level physiological limit validation `@ValidMetricRecord`, transparent service query alias expansion with SQL IN clauses, and idempotent batch data cleansing migration patch
- 全面重构健康指标管理页、保健指标管理页与生活方式看板录入弹窗，彻底废除私有硬编码字典，统一接入权威元数据、血压协同、心情打分、四梯度非破坏性校验与危急值二次确认保护。
- 后端 AI 解析与 OCR 体检识别全面接入权威元数据注册中心与化验单扩展注册表，通过通用接口 MetricValueProvider 在全渠道入库 DTO 网关层统一拦截 Tier 4 生理极限。
- CrossChannelConsistencyTest 后端跨渠道一致性测试与 HealthMetrics / WellnessMetrics 前端管理页弹窗测试套件落地，完成手动录入、AI 解析与 OCR 识别三端指标标准与校验界限的端到端自动化质量闭环

---
