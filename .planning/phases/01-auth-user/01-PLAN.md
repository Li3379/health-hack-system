---
name: Phase 1 Plan - 认证与用户模块
description: 完成用户登录、注册和个人资料管理功能
wave: 1
depends_on: []
files_modified:
  - hhs-frontend/src/api/auth.js
  - hhs-frontend/src/api/user.js
  - hhs-frontend/src/stores/auth.js
  - hhs-frontend/src/views/Login.vue
  - hhs-frontend/src/views/Register.vue
  - hhs-frontend/src/views/Profile.vue
  - hhs-frontend/src/router/index.js
autonomous: true
requirements:
  - AUTH-01
  - AUTH-02
  - AUTH-03
  - USER-01
  - USER-02
  - USER-03
  - USER-04
---

# Phase 1: 认证与用户模块 - 执行计划

## 目标
完成用户登录、注册和个人资料管理功能

## Wave 1: 核心认证功能 (可并行)

### Task 1.1: API 层与 Pinia Store
**文件:**
- `hhs-frontend/src/api/auth.js` - 认证 API (register, login, logout)
- `hhs-frontend/src/api/user.js` - 用户 API (profile, password, avatar)
- `hhs-frontend/src/stores/auth.js` - Pinia store (token, user info, actions)

**具体任务:**
1. 实现 auth.js: register(email, password), login(email, password), logout()
2. 实现 user.js: getProfile(), updateProfile(data), updatePassword(oldPwd, newPwd), uploadAvatar(file)
3. 实现 auth store: token 存取、user 信息存取、isAuthenticated 状态、login/logout/refreshToken 动作
4. 添加 JWT 过期检查和自动刷新逻辑

**依赖:** 无
**验证:** API 函数可正确调用，store 状态正确更新

---

### Task 1.2: 登录注册页面
**文件:**
- `hhs-frontend/src/views/Login.vue` - 登录页面
- `hhs-frontend/src/views/Register.vue` - 注册页面

**具体任务:**
1. Login.vue: 邮箱/密码表单、Element Plus 表单验证、登录按钮、注册跳转链接
2. Register.vue: 邮箱/密码/确认密码表单、表单验证、注册按钮、登录跳转链接
3. 登录成功后存储 token 并跳转首页
4. 注册成功后自动登录或跳转登录页

**依赖:** Task 1.1 (API 和 store)
**验证:** 可成功注册和登录，错误信息正确显示

---

### Task 1.3: 个人资料页面
**文件:**
- `hhs-frontend/src/views/Profile.vue` - 个人资料页面
- `hhs-frontend/src/components/AvatarUpload.vue` - 头像上传组件

**具体任务:**
1. Profile.vue: 获取并显示用户资料、表单编辑功能
2. AvatarUpload.vue: el-upload 组件、头像预览、裁剪功能
3. 修改密码功能: 旧密码、新密码、确认密码表单
4. 页面加载时获取用户资料，保存时调用更新 API

**依赖:** Task 1.1 (API 和 store)
**验证:** 可查看和修改资料、可上传头像、可修改密码

---

### Task 1.4: 路由与状态保持
**文件:**
- `hhs-frontend/src/router/index.js` - 路由配置

**具体任务:**
1. 配置登录、注册、个人资料路由
2. 添加路由守卫: 未登录跳转登录页
3. 实现页面刷新后从 localStorage 恢复 token 和用户状态

**依赖:** Task 1.1, Task 1.2, Task 1.3
**验证:** 刷新页面保持登录状态，未登录无法访问需认证页面

---

## Must-Haves (目标逆向验证)

| # | Must-Have | 验证方式 |
|---|-----------|----------|
| 1 | 用户可以成功注册新账户 | 注册页面提交后返回成功，用户可登录 |
| 2 | 用户可以登录系统并保持会话 | 登录后跳转首页，显示用户信息 |
| 3 | JWT 令牌可以自动续期 | localStorage 存储 token，刷新页面保持登录 |
| 4 | 用户可以查看个人资料 | Profile 页面显示用户邮箱、头像等信息 |
| 5 | 用户可以修改个人资料 | 修改资料后刷新页面数据已更新 |
| 6 | 用户可以上传和更换头像 | 头像上传后页面显示新头像 |
| 7 | 用户可以修改密码 | 修改密码后使用新密码可登录 |
| 8 | 登出功能正常工作 | 登出后跳转登录页，刷新页面保持登出状态 |

---

## 执行顺序

```
Wave 1 (并行):
  Task 1.1: API + Store
  ├─ auth.js
  ├─ user.js
  └─ auth store

  Task 1.2: Login + Register
  ├─ Login.vue
  └─ Register.vue

  Task 1.3: Profile
  ├─ Profile.vue
  └─ AvatarUpload.vue

Task 1.4: 路由整合 (依赖上述所有)
```

---

## 注意事项

1. JWT 存储在 localStorage，key 命名为 `hhs_token`
2. 用户信息存储在 localStorage，key 命名为 `hhs_user`
3. Token 过期时间 7 天，前端不主动刷新，由路由守卫检查
4. 头像上传使用 FormData 格式
5. 所有表单使用 Element Plus 内置验证规则
