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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
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
