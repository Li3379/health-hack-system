# 前端架构详解

> 本文档详细描述 HHS 前端的技术架构、组件设计和状态管理。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4 | 前端框架 |
| TypeScript | 5.5 | 类型系统 |
| Vite | 5 | 构建工具 |
| Element Plus | 2.5 | UI 组件库 |
| Pinia | 2.1 | 状态管理 |
| Vue Router | 4.x | 路由管理 |
| ECharts | 5.4 | 图表可视化 |
| Axios | 1.x | HTTP 客户端 |

## 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                           Vue Application                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                      Views Layer                          │    │
│  │  Dashboard │ Health │ DataInput │ AI │ Prevention │ ...  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   Components Layer                        │    │
│  │  MainLayout │ HealthScoreCircle │ PlatformStatusTag │ ...│    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                     Stores Layer                          │    │
│  │    auth │ alert │ realtime │ ai │ wellness │ push       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                      API Layer                            │    │
│  │   auth │ health │ alert │ realtime │ ai │ device │ ...  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                      Utils Layer                          │    │
│  │            request │ storage │ format                    │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## 目录结构

```
hhs-frontend-v2/
├── src/
│   ├── api/                    # API 调用层
│   │   ├── auth.ts            # 认证 API
│   │   ├── health.ts          # 健康指标 API
│   │   ├── alert.ts           # 预警 API
│   │   ├── realtime.ts        # 实时监控 API
│   │   ├── ai.ts              # AI 对话 API
│   │   ├── ai-parse.ts        # AI 解析 API
│   │   ├── device.ts          # 设备同步 API
│   │   ├── ocr.ts             # OCR API
│   │   ├── score.ts           # 健康评分 API
│   │   ├── threshold.ts       # 阈值配置 API
│   │   ├── push.ts            # 推送配置 API
│   │   ├── user.ts            # 用户 API
│   │   ├── wellness.ts        # 保健指标 API
│   │   ├── prevention.ts      # 预防保健 API
│   │   └── screening.ts       # 筛查 API
│   │
│   ├── components/             # 通用组件
│   │   ├── layout/
│   │   │   └── MainLayout.vue # 主布局组件
│   │   ├── HealthScoreCircle.vue # 健康评分环形图
│   │   └── device/
│   │       └── PlatformStatusTag.vue # 设备平台状态标签
│   │
│   ├── views/                  # 页面组件
│   │   ├── auth/              # 认证页面
│   │   │   ├── Login.vue
│   │   │   └── Register.vue
│   │   ├── dashboard/         # 仪表盘
│   │   │   └── Index.vue
│   │   ├── health/            # 健康管理
│   │   │   ├── Metrics.vue    # 指标列表
│   │   │   ├── Alerts.vue     # 预警列表
│   │   │   ├── Thresholds.vue # 阈值配置
│   │   │   └── Score.vue      # 健康评分
│   │   ├── data-input/        # 数据录入
│   │   │   ├── Index.vue
│   │   │   └── components/
│   │   │       ├── QuickInput.vue      # 快速录入
│   │   │       ├── QuickInputDialog.vue
│   │   │       ├── AIInput.vue         # AI 智能录入
│   │   │       ├── AiHistory.vue       # AI 历史
│   │   │       ├── OcrInput.vue        # OCR 录入
│   │   │       ├── OcrHistory.vue      # OCR 历史
│   │   │       ├── DeviceSync.vue      # 设备同步
│   │   │       ├── DeviceConfigWizard.vue
│   │   │       └── RecentRecords.vue   # 最近记录
│   │   ├── ai/                # AI 模块
│   │   │   └── Chat.vue       # AI 对话
│   │   ├── realtime/          # 实时监控
│   │   │   └── Monitor.vue
│   │   ├── prevention/        # 预防保健
│   │   │   ├── Profile.vue
│   │   │   ├── Metrics.vue
│   │   │   └── Risk.vue
│   │   ├── screening/         # 筛查管理
│   │   │   ├── List.vue
│   │   │   └── Detail.vue
│   │   ├── wellness/          # 保健指标
│   │   │   └── Dashboard.vue
│   │   ├── user/              # 用户中心
│   │   │   ├── Profile.vue
│   │   │   └── PushSettings.vue
│   │   ├── settings/          # 系统设置
│   │   │   └── DevicePlatformConfig.vue
│   │   └── oauth/             # OAuth 回调
│   │       └── OAuthCallback.vue
│   │
│   ├── stores/                 # Pinia 状态管理
│   │   ├── auth.ts            # 认证状态
│   │   ├── alert.ts           # 预警状态
│   │   ├── realtime.ts        # 实时监控状态
│   │   ├── ai.ts              # AI 对话状态
│   │   ├── wellness.ts        # 保健指标状态
│   │   ├── push.ts            # 推送配置状态
│   │   └── theme.ts           # 主题状态
│   │
│   ├── router/                 # 路由配置
│   │   └── index.ts
│   │
│   ├── types/                  # TypeScript 类型
│   │   ├── api.ts             # API 类型定义
│   │   └── platform.ts        # 平台类型定义
│   │
│   ├── utils/                  # 工具函数
│   │   ├── request.ts         # Axios 封装
│   │   ├── storage.ts         # 本地存储
│   │   └── format.ts          # 格式化工具
│   │
│   ├── App.vue                 # 根组件
│   └── main.ts                 # 入口文件
│
├── public/                     # 静态资源
├── index.html                  # HTML 模板
├── vite.config.ts              # Vite 配置
├── tsconfig.json               # TypeScript 配置
└── package.json                # 依赖配置
```

## 路由结构

```typescript
const routes: RouteRecordRaw[] = [
  // 公开页面
  { path: '/login', component: Login, meta: { requiresAuth: false } },
  { path: '/register', component: Register, meta: { requiresAuth: false } },
  
  // 需要认证的页面
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: Dashboard },
      
      // 健康管理
      { path: 'health/metrics', component: HealthMetrics },
      { path: 'health/alerts', component: Alerts },
      { path: 'health/thresholds', component: Thresholds },
      { path: 'health/score', component: HealthScore },
      
      // 数据录入
      { path: 'data-input', component: DataInput },
      
      // AI 对话
      { path: 'ai/chat', component: AIChat },
      
      // 实时监控
      { path: 'realtime/monitor', component: RealtimeMonitor },
      
      // 预防保健
      { path: 'prevention/profile', component: PreventionProfile },
      { path: 'prevention/metrics', component: PreventionMetrics },
      { path: 'prevention/risk', component: RiskAssessment },
      
      // 筛查管理
      { path: 'screening/list', component: ScreeningList },
      { path: 'screening/detail/:id', component: ScreeningDetail },
      
      // 保健指标
      { path: 'wellness/dashboard', component: WellnessDashboard },
      
      // 用户中心
      { path: 'user/profile', component: UserProfile },
      { path: 'user/push-settings', component: PushSettings },
      
      // 系统设置
      { path: 'settings/device-platform', component: DevicePlatformConfig, 
        meta: { requiresAdmin: true } },
    ]
  },
  
  // OAuth 回调
  { path: '/oauth/callback/:platform', component: OAuthCallback }
]
```

## 状态管理

### Auth Store

```typescript
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(storage.getToken())
  const user = ref<UserVO | null>(storage.getUser())
  const isAuthenticated = computed(() => !!token.value)

  // 登录
  const login = async (data: LoginRequest) => { ... }
  
  // 注册
  const register = async (data: RegisterRequest) => { ... }
  
  // 登出
  const logout = () => { ... }
  
  // 更新用户信息
  const updateUser = (newUser: UserVO) => { ... }
  
  // 获取最新用户信息
  const fetchUserInfo = async () => { ... }

  return { token, user, isAuthenticated, login, register, logout, updateUser, fetchUserInfo }
})
```

### Alert Store

预警状态管理，包含预警列表、未读数量等。

### Realtime Store

实时监控状态，WebSocket 连接管理。

### AI Store

AI 对话会话管理，历史记录缓存。

## API 层设计

### 请求封装

```typescript
// utils/request.ts
import axios from 'axios'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
})

// 请求拦截器：添加 Token
request.interceptors.request.use(config => {
  const token = storage.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：处理错误
request.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      // Token 过期，跳转登录
      router.push('/login')
    }
    return Promise.reject(error)
  }
)
```

### API 模块示例

```typescript
// api/health.ts
export const healthApi = {
  getMetrics(params: { page?: number; size?: number }) {
    return request.get<PageResult<HealthMetricVO>>('/api/metrics', { params })
  },

  createMetric(data: HealthMetricRequest) {
    return request.post<HealthMetricVO>('/api/metrics', data)
  },

  updateMetric(id: number, data: HealthMetricRequest) {
    return request.put<HealthMetricVO>(`/api/metrics/${id}`, data)
  },

  deleteMetric(id: number) {
    return request.delete<void>(`/api/metrics/${id}`)
  }
}
```

## 组件设计原则

### 1. Composition API + script setup

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const loading = ref(false)

const displayName = computed(() => authStore.user?.nickname || '用户')

onMounted(async () => {
  loading.value = true
  // 初始化逻辑
  loading.value = false
})
</script>
```

### 2. Props 和 Emits 类型定义

```typescript
interface Props {
  metricKey: string
  value: number
  unit?: string
}

interface Emits {
  (e: 'update', value: number): void
  (e: 'delete'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
```

### 3. 异步组件加载

```typescript
// 路由懒加载
component: () => import('@/views/health/Metrics.vue')
```

## 主题系统

支持亮色/暗色主题切换：

```typescript
// stores/theme.ts
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(storage.getTheme() === 'dark')

  const toggleTheme = () => {
    isDark.value = !isDark.value
    document.documentElement.classList.toggle('dark', isDark.value)
    storage.setTheme(isDark.value ? 'dark' : 'light')
  }

  return { isDark, toggleTheme }
})
```

## 图表组件

使用 ECharts 进行数据可视化：

- 健康指标趋势图
- 健康评分环形图
- 实时监控仪表盘

## 性能优化

### 1. 路由懒加载

```typescript
component: () => import('@/views/health/Metrics.vue')
```

### 2. 组件按需导入

```typescript
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
```

### 3. 本地存储缓存

- Token 持久化
- 用户信息缓存
- 主题偏好存储

## 错误处理

### 全局错误处理

```typescript
// main.ts
app.config.errorHandler = (err, vm, info) => {
  console.error('Global error:', err)
  ElMessage.error('系统错误，请稍后重试')
}
```

### API 错误处理

```typescript
try {
  await healthApi.createMetric(data)
  ElMessage.success('保存成功')
} catch (error) {
  if (error.response?.data?.message) {
    ElMessage.error(error.response.data.message)
  } else {
    ElMessage.error('操作失败，请重试')
  }
}
```

## 环境变量

```env
# .env.development
VITE_API_BASE_URL=http://localhost:8082
VITE_WS_URL=ws://localhost:8082/ws

# .env.production
VITE_API_BASE_URL=
VITE_WS_URL=wss://api.example.com/ws
```