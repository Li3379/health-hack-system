# Phase 1: 认证与用户模块 - Context

**Gathered:** 2026-03-02
**Status:** Ready for planning

<domain>
## Phase Boundary

完成用户登录、注册和个人资料管理功能。包括：
- 用户注册和登录
- JWT 令牌管理
- 个人资料查看和修改
- 密码修改
- 头像上传

</domain>

<decisions>
## Implementation Decisions

### Authentication Flow
- 标准邮箱/密码注册登录流程
- JWT 令牌存储在 localStorage
- 7天令牌过期自动刷新
- 登录状态页面刷新后保持

### User Profile UI
- Element Plus 表单组件
- 头像上传使用 el-upload 组件
- 头像裁剪使用内置功能

### Error Handling
- 表单验证使用 Element Plus 内置验证
- API 错误信息直接展示
- 加载状态使用 el-skeleton

### Page Layout
- 使用 Element Plus Container 布局
- 侧边栏导航 + 主内容区
- 响应式设计适配移动端

### Claude's Discretion
- 具体的图标选择
- 动画效果细节
- 空状态展示内容

</decisions>

<specifics>
## Specific Ideas

- 登录页面简洁大气
- 头像上传支持预览和裁剪
- 个人信息表单字段根据后端 API 设计

</specifics>

<code_context>
## Existing Code Insights

### Backend API Endpoints
- POST /api/auth/register - 注册
- POST /api/auth/login - 登录
- GET /api/user/profile - 获取资料
- PUT /api/user/profile - 更新资料
- PUT /api/user/password - 修改密码
- POST /api/upload/avatar - 上传头像

### Frontend Tech Stack
- Vue 3.3.7 + Composition API
- Vite 4.5.0
- Element Plus 2.5.6
- Pinia 2.1.7 状态管理

</code_context>

<deferred>
## Deferred Ideas

None — Phase 1 scope clear

</deferred>

---
*Phase: 01-auth-user*
*Context gathered: 2026-03-02*
