# Requirements: HHS Frontend Reconstruction

**Defined:** 2026-03-02
**Core Value:** 提供完美的用户体验，实现所有后端 API 功能，确保前后端联调零错误。

## v1 Requirements

### Auth (认证)

- [x] **AUTH-01**: 用户可以注册新账户（邮箱/密码）
- [x] **AUTH-02**: 用户可以登录系统
- [x] **AUTH-03**: JWT 令牌自动续期和登出

### User (用户)

- [x] **USER-01**: 用户可以查看个人资料
- [x] **USER-02**: 用户可以修改个人资料
- [x] **USER-03**: 用户可以修改密码
- [x] **USER-04**: 用户可以上传头像

### Wellness (保健指标) - 新增

- [ ] **WELL-01**: 创建 wellness_metric 数据库表
- [ ] **WELL-02**: 后端实现 /api/wellness REST API 端点
- [ ] **WELL-03**: 前端创建保健指标 Dashboard 页面
- [ ] **WELL-04**: 前端实现保健指标列表页
- [ ] **WELL-05**: 前端实现保健指标图表展示
- [ ] **WELL-06**: 前端实现保健指标 CRUD 操作
- [ ] **WELL-07**: 保健指标数据验证

### Health Metrics (健康指标)

- [ ] **METR-01**: 用户可以添加健康指标（血糖/血压/心率）
- [ ] **METR-02**: 用户可以查看健康指标列表
- [ ] **METR-03**: 用户可以修改健康指标
- [ ] **METR-04**: 用户可以删除健康指标
- [ ] **METR-05**: 健康数据图表展示和趋势分析

### Alerts (健康预警)

- [ ] **ALERT-01**: 用户可以查看健康预警列表
- [ ] **ALERT-02**: 用户可以查看未读预警数量
- [ ] **ALERT-03**: 用户可以标记预警为已读
- [ ] **ALERT-04**: 用户可以确认预警
- [ ] **ALERT-05**: 用户可以一键已读所有预警

### Thresholds (阈值设置)

- [ ] **THRESH-01**: 用户可以查看个人阈值设置
- [ ] **THRESH-02**: 用户可以添加/修改阈值
- [ ] **THRESH-03**: 用户可以删除阈值

### Health Score (健康评分)

- [ ] **SCORE-01**: 用户可以查看当前健康评分
- [ ] **SCORE-02**: 用户可以查看评分详情（ breakdown）
- [ ] **SCORE-03**: 用户可以查看评分历史
- [ ] **SCORE-04**: 用户可以手动重新计算评分

### AI Advisor (AI 健康顾问)

- [ ] **AI-01**: 用户可以与 AI 进行健康咨询对话
- [ ] **AI-02**: 用户可以查看对话历史
- [ ] **AI-03**: 用户可以删除对话会话
- [ ] **AI-04**: 用户可以获取健康分类建议

### Prevention (预防保健)

- [ ] **PREV-01**: 用户可以填写健康档案
- [ ] **PREV-02**: 用户可以查看健康档案
- [ ] **PREV-03**: 用户可以更新健康档案
- [ ] **PREV-04**: 用户可以进行风险评估
- [ ] **PREV-05**: 用户可以查看风险评估历史
- [ ] **PREV-06**: 用户可以添加预防保健指标
- [ ] **PREV-07**: 用户可以查看预防保健指标趋势

### Screening (体检报告)

- [ ] **SCREEN-01**: 用户可以上传体检报告（图片/PDF）
- [ ] **SCREEN-02**: 用户可以查看已上传报告列表
- [ ] **SCREEN-03**: 用户可以查看报告详情
- [ ] **SCREEN-04**: 用户可以删除报告
- [ ] **SCREEN-05**: 用户可以触发 OCR 识别
- [ ] **SCREEN-06**: 用户可以查看 OCR 识别结果

### Realtime (实时数据)

- [ ] **REALT-01**: WebSocket 实时接收健康数据推送
- [ ] **REALT-02**: 实时数据趋势图表展示

## v2 Requirements

- **通知系统**: 邮件/短信通知
- **数据导出**: 导出健康报告 PDF
- **家人共享**: 家庭成员健康数据共享
- **慢病管理**: 糖尿病/高血压专项管理

## Out of Scope

| Feature | Reason |
|---------|--------|
| 现有前端代码修复 | 从零重构 |
| 后端 API 修改 | 完全基于现有后端 |
| 移动端 APP | Web 端优先 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 ~ AUTH-03 | Phase 1 | Pending |
| USER-01 ~ USER-04 | Phase 1 | Complete |
| METR-01 ~ METR-05 | Phase 2 | Pending |
| ALERT-01 ~ ALERT-05 | Phase 2 | Pending |
| THRESH-01 ~ THRESH-03 | Phase 2 | Pending |
| SCORE-01 ~ SCORE-04 | Phase 3 | Pending |
| AI-01 ~ AI-04 | Phase 3 | Pending |
| PREV-01 ~ PREV-07 | Phase 4 | Pending |
| SCREEN-01 ~ SCREEN-06 | Phase 4 | Pending |
| REALT-01 ~ REALT-02 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 39 total
- Mapped to phases: 39
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-02*
*Last updated: 2026-03-02 after initial definition*
