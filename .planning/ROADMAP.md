# Roadmap: HHS Frontend Reconstruction

**Created:** 2026-03-02
**Phases:** 5

## Phase Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | 认证与用户模块 | Complete | 2026-03-02 | 8/8 |
| 2 | 健康数据模块 | Pending | - | 0/13 |
| 3 | AI 与评分模块 | Pending | - | 0/8 |
| 4 | 预防与体检模块 | Pending | - | 0/13 |
| 5 | 实时与集成测试 | Pending | - | 0/2 |

## Phase Details

### Phase 1: 认证与用户模块

**Goal:** 完成用户登录、注册和个人资料管理功能

**Requirements:**
- AUTH-01: 用户可以注册新账户
- AUTH-02: 用户可以登录系统
- AUTH-03: JWT 令牌自动管理
- USER-01: 查看个人资料
- USER-02: 修改个人资料
- USER-03: 修改密码
- USER-04: 上传头像

**Success Criteria:**
1. 用户可以成功注册新账户
2. 用户可以登录系统并保持会话
3. JWT 令牌可以自动续期
4. 用户可以查看和修改个人资料
5. 用户可以上传和更换头像
6. 用户可以修改密码
7. 登录状态在页面刷新后保持
8. 登出功能正常工作

---

### Phase 2: 健康数据模块

**Goal:** 完成健康指标管理、预警通知和个人阈值设置

**Requirements:**
- METR-01: 添加健康指标
- METR-02: 查看指标列表
- METR-03: 修改健康指标
- METR-04: 删除健康指标
- METR-05: 图表展示趋势
- ALERT-01: 查看预警列表
- ALERT-02: 查看未读数量
- ALERT-03: 标记已读
- ALERT-04: 确认预警
- ALERT-05: 一键已读所有
- THRESH-01: 查看阈值
- THRESH-02: 添加/修改阈值
- THRESH-03: 删除阈值

**Success Criteria:**
1. 用户可以添加血糖、血压、心率数据
2. 用户可以查看健康指标列表
3. 用户可以修改和删除健康数据
4. 健康数据图表正确展示
5. 预警列表正确显示
6. 未读预警数量正确计算
7. 预警标记已读功能正常
8. 个人阈值设置正确保存和生效

---

### Phase 3: AI 与评分模块

**Goal:** 完成健康评分和 AI 智能顾问功能

**Requirements:**
- SCORE-01: 查看健康评分
- SCORE-02: 查看评分详情
- SCORE-03: 查看评分历史
- SCORE-04: 重新计算评分
- AI-01: AI 健康咨询对话
- AI-02: 查看对话历史
- AI-03: 删除对话会话
- AI-04: 健康分类建议

**Success Criteria:**
1. 健康评分正确显示
2. 评分 breakdown 详情正确
3. 评分历史趋势正确
4. AI 对话功能正常工作
5. 对话历史正确保存和显示
6. 可以创建和删除对话会话

---

### Phase 4: 预防与体检模块

**Goal:** 完成预防保健档案和体检报告 OCR 功能

**Requirements:**
- PREV-01: 填写健康档案
- PREV-02: 查看健康档案
- PREV-03: 更新健康档案
- PREV-04: 风险评估
- PREV-05: 查看评估历史
- PREV-06: 添加预防指标
- PREV-07: 查看指标趋势
- SCREEN-01: 上传体检报告
- SCREEN-02: 查看报告列表
- SCREEN-03: 查看报告详情
- SCREEN-04: 删除报告
- SCREEN-05: 触发 OCR
- SCREEN-06: 查看 OCR 结果

**Success Criteria:**
1. 健康档案可以正确填写和保存
2. 风险评估功能正常
3. 体检报告可以上传
4. OCR 识别功能正常工作
5. 识别结果正确显示

---

### Phase 5: 实时与集成测试

**Goal:** 完成 WebSocket 实时推送和全面前后端联调

**Requirements:**
- REALT-01: WebSocket 实时数据
- REALT-02: 实时图表展示

**Success Criteria:**
1. WebSocket 连接成功
2. 实时数据正确推送和显示
3. 前后端所有 API 联调零错误
4. 所有模块功能正常运行

---

## State

See: .planning/STATE.md

---
*Created: 2026-03-02*
