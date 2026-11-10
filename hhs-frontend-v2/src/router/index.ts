import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/oauth/callback/:platform',
    name: 'OAuthCallback',
    component: () => import('@/views/oauth/OAuthCallback.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    redirect: '/dashboard',
    component: () => import('@/components/layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue')
      },
      {
        path: 'health/metrics',
        name: 'HealthMetrics',
        component: () => import('@/views/health/Metrics.vue')
      },
      {
        path: 'health/alerts',
        name: 'Alerts',
        component: () => import('@/views/health/Alerts.vue')
      },
      {
        path: 'health/thresholds',
        name: 'Thresholds',
        component: () => import('@/views/health/Thresholds.vue')
      },
      {
        path: 'health/score',
        name: 'HealthScore',
        component: () => import('@/views/health/Score.vue')
      },
      {
        path: 'data-input',
        name: 'DataInput',
        component: () => import('@/views/data-input/Index.vue')
      },
      {
        path: 'ai/chat',
        name: 'AIChat',
        component: () => import('@/views/ai/Chat.vue')
      },
      {
        path: 'prevention/profile',
        name: 'PreventionProfile',
        component: () => import('@/views/prevention/Profile.vue')
      },
      {
        path: 'prevention/metrics',
        name: 'PreventionMetrics',
        component: () => import('@/views/prevention/Metrics.vue')
      },
      {
        path: 'prevention/risk',
        name: 'RiskAssessment',
        component: () => import('@/views/prevention/Risk.vue')
      },
      {
        path: 'screening/list',
        name: 'ScreeningList',
        component: () => import('@/views/screening/List.vue')
      },
      {
        path: 'screening/detail/:id',
        name: 'ScreeningDetail',
        component: () => import('@/views/screening/Detail.vue')
      },
      {
        path: 'realtime/monitor',
        name: 'RealtimeMonitor',
        component: () => import('@/views/realtime/Monitor.vue')
      },
      {
        path: 'wellness/dashboard',
        name: 'WellnessDashboard',
        component: () => import('@/views/wellness/Dashboard.vue')
      },
      {
        path: 'user/profile',
        name: 'UserProfile',
        component: () => import('@/views/user/Profile.vue')
      },
      {
        path: 'user/push-settings',
        name: 'PushSettings',
        component: () => import('@/views/user/PushSettings.vue')
      },
      {
        path: 'settings/device-platform',
        name: 'DevicePlatformConfig',
        component: () => import('@/views/settings/DevicePlatformConfig.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'reminders',
        name: 'Reminders',
        component: () => import('@/views/reminders/Index.vue')
      },
      {
        path: 'goals',
        name: 'HealthGoals',
        component: () => import('@/views/goals/Index.vue')
      },
      {
        path: 'mood',
        name: 'MoodTracker',
        component: () => import('@/views/mood/MoodTracker.vue')
      },
      {
        path: 'screening/compare/:idA/:idB',
        name: 'ScreeningCompare',
        component: () => import('@/views/screening/Compare.vue')
      },
      {
        // 路由级 404（UI-SPEC Contract 3 §3.6）。必须是 children 的最后一项：
        // vue-router 对同级路由按声明顺序匹配，而所有既有路径都更具体，
        // 因此 catch-all 只会吃掉真正未匹配的 URL，不会遮蔽任何已注册路由。
        // 刻意不给 meta.requiresAuth —— 未登录用户访问一个不存在的 URL 应当看到
        // 404，而不是被弹去登录页；同时 beforeEach 不动，全站仍只有 requiresAuth 一个判据（A-3）。
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/NotFound.vue')
      }
    ]
  }
]

// 路由表以只读形式导出，供 RouteNotFound.spec.ts 在 happy-dom 下断言
// 「catch-all 存在且不遮蔽任何既有路由」，无需触发 createWebHistory 的副作用。
export { routes }

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
