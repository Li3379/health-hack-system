<template>
  <el-container class="main-layout">
    <!-- Sidebar -->
    <el-aside :width="sidebarWidth" class="sidebar">
      <div class="logo">
        <div class="logo-icon">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="8" fill="currentColor" fill-opacity="0.2" />
            <path
              d="M16 6C10.48 6 6 10.48 6 16s4.48 10 10 10 10-4.48 10-10S21.52 6 16 6zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"
              fill="currentColor"
            />
            <path
              d="M16 10c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm0 10c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4z"
              fill="currentColor"
              fill-opacity="0.7"
            />
            <circle cx="16" cy="16" r="2" fill="currentColor" />
          </svg>
        </div>
        <span v-show="!collapsed" class="logo-text">HHS</span>
      </div>

      <el-scrollbar class="menu-scrollbar">
        <el-menu
          :default-active="activeMenu"
          router
          :collapse="collapsed"
          :collapse-transition="false"
          class="sidebar-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <template #title>仪表盘</template>
          </el-menu-item>

          <el-sub-menu index="health">
            <template #title>
              <el-icon><DataLine /></el-icon>
              <span>健康管理</span>
            </template>
            <el-menu-item index="/health/metrics">健康指标</el-menu-item>
            <el-menu-item index="/health/alerts">
              <span>健康预警</span>
              <el-badge v-if="alertStore.unreadCount > 0" :value="alertStore.unreadCount" class="menu-badge" />
            </el-menu-item>
            <el-menu-item index="/health/thresholds">阈值设置</el-menu-item>
            <el-menu-item index="/health/score">健康评分</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="/data-input">
            <el-icon><Upload /></el-icon>
            <template #title>智能录入</template>
          </el-menu-item>

          <el-menu-item index="/ai/chat">
            <el-icon><ChatDotRound /></el-icon>
            <template #title>AI 顾问</template>
          </el-menu-item>

          <el-sub-menu index="wellness">
            <template #title>
              <el-icon><Sunny /></el-icon>
              <span>保健中心</span>
            </template>
            <el-menu-item index="/wellness/dashboard">保健仪表盘</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="prevention">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>预防保健</span>
            </template>
            <el-menu-item index="/prevention/profile">健康档案</el-menu-item>
            <el-menu-item index="/prevention/metrics">保健指标</el-menu-item>
            <el-menu-item index="/prevention/risk">风险评估</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="/screening/list">
            <el-icon><Files /></el-icon>
            <template #title>体检报告</template>
          </el-menu-item>

          <el-menu-item index="/realtime/monitor">
            <el-icon><Monitor /></el-icon>
            <template #title>实时监控</template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>

      <div class="sidebar-footer">
        <button class="collapse-btn" @click="toggleCollapse">
          <el-icon :size="18">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
        </button>
      </div>
    </el-aside>

    <!-- Main Content -->
    <el-container class="main-container">
      <!-- Header -->
      <el-header class="header">
        <div class="header-left">
          <button class="mobile-menu-btn" @click="toggleCollapse">
            <el-icon :size="20"><Expand /></el-icon>
          </button>
          <h3 class="page-title">{{ pageTitle }}</h3>
        </div>

        <div class="header-right">
          <el-dropdown trigger="click" @command="handleThemeChange">
            <button class="theme-toggle">
              <el-icon :size="18">
                <Sunny v-if="themeStore.effectiveTheme === 'light'" />
                <Moon v-else />
              </el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light" :class="{ 'is-active': themeStore.effectiveTheme === 'light' }">
                  <el-icon><Sunny /></el-icon>浅色模式
                </el-dropdown-item>
                <el-dropdown-item command="dark" :class="{ 'is-active': themeStore.effectiveTheme === 'dark' }">
                  <el-icon><Moon /></el-icon>深色模式
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <el-avatar :src="authStore.user?.avatar" :size="32" class="user-avatar">
                {{ authStore.user?.nickname?.[0] || authStore.user?.username?.[0] }}
              </el-avatar>
              <div class="user-details">
                <span class="username">{{ authStore.user?.nickname || authStore.user?.username }}</span>
                <span class="user-role">健康用户</span>
              </div>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="push-settings">
                  <el-icon><Bell /></el-icon>推送设置
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main Content Area -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
    <AiFloatingBall />
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAlertStore } from '@/stores/alert'
import { useThemeStore } from '@/stores/theme'
import {
  HomeFilled, DataLine, Upload, ChatDotRound, Sunny, Document,
  Files, Fold, Expand, ArrowDown, User, Bell, SwitchButton, Moon
} from '@element-plus/icons-vue'
import type { Theme } from '@/stores/theme'
import AiFloatingBall from '@/components/ai-floating-ball/AiFloatingBall.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const alertStore = useAlertStore()
const themeStore = useThemeStore()

const collapsed = ref(false)
const sidebarWidth = computed(() => (collapsed.value ? '64px' : '240px'))
const activeMenu = computed(() => route.path)

const toggleCollapse = () => { collapsed.value = !collapsed.value }

const pageTitle = computed(() => {
  const titles: Record<string, string> = {
    '/dashboard': '仪表盘',
    '/health/metrics': '健康指标',
    '/health/alerts': '健康预警',
    '/health/thresholds': '阈值设置',
    '/health/score': '健康评分',
    '/data-input': '智能录入',
    '/ai/chat': 'AI 健康顾问',
    '/wellness/dashboard': '保健仪表盘',
    '/prevention/profile': '健康档案',
    '/prevention/metrics': '保健指标',
    '/prevention/risk': '风险评估',
    '/screening/list': '体检报告',
    '/realtime/monitor': '实时监控',
    '/user/profile': '个人中心',
    '/user/push-settings': '推送设置'
  }
  return titles[route.path] || '健康管理系统'
})

const handleCommand = (command: string) => {
  if (command === 'profile') router.push('/user/profile')
  else if (command === 'push-settings') router.push('/user/push-settings')
  else if (command === 'logout') authStore.logout()
}

const handleThemeChange = (theme: Theme) => {
  themeStore.setTheme(theme)
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
  overflow: hidden;
}

/* Sidebar - Dark Editorial */
.sidebar {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  display: flex;
  flex-direction: column;
  transition: width 0.3s var(--ease);
  overflow: hidden;
  border-right: 1px solid var(--glass-border);
  box-shadow: var(--shadow-md);
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid var(--glass-border);
  background: var(--glass-bg);
}
.logo-icon {
  width: 32px;
  height: 32px;
  color: var(--accent-cool);
  flex-shrink: 0;
}
.logo-text {
  font: 600 18px/1 var(--font-ui);
  color: var(--text-1);
  letter-spacing: -0.01em;
}

.menu-scrollbar { flex: 1; overflow: hidden; }

.sidebar-menu {
  border-right: none;
  background: transparent;
}
.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  color: var(--text-3);
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: var(--radius-md);
  font: 400 14px/1 var(--font-ui);
  transition: all var(--dur-spring-fast) var(--ease-spring-soft);
}
.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: var(--surface-hover);
  color: var(--text-1);
  transform: translateX(4px);
}
.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--accent-cool-soft) !important;
  color: var(--accent-cool) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 2px 8px rgba(var(--accent-cool-rgb), 0.2);
}
.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--accent-cool);
}
.sidebar-menu :deep(.el-menu) {
  background: transparent;
}
.sidebar-menu :deep(.el-menu--inline .el-menu-item) {
  padding-left: 52px !important;
  height: 38px;
  line-height: 38px;
}
.menu-badge { margin-left: 8px; }

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid var(--glass-border);
  background: var(--glass-bg);
}
.collapse-btn {
  width: 100%;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-2);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  color: var(--text-3);
  cursor: pointer;
  transition: all var(--dur-spring-fast) var(--ease-spring-soft);
}
.collapse-btn:hover {
  background: var(--surface-hover);
  color: var(--text-1);
  border-color: var(--glass-border-strong);
  transform: scale(1.02);
}
.collapse-btn:active {
  transform: scale(0.98);
}

/* Main Container */
.main-container {
  display: flex;
  flex-direction: column;
  background: var(--bg);
  overflow: hidden;
  position: relative;
}

/* Header */
.header {
  height: 64px;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  border-bottom: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: relative;
  z-index: 10;
  box-shadow: var(--shadow-sm);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.mobile-menu-btn {
  display: none;
  padding: 8px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-3);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);
}
.mobile-menu-btn:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}
.page-title {
  font: 600 16px/1 var(--font-ui);
  color: var(--text-1);
  letter-spacing: -0.01em;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* Theme Toggle */
.theme-toggle {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--glass-bg);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-full);
  color: var(--text-3);
  cursor: pointer;
  transition: all var(--dur-spring-fast) var(--ease-spring-soft);
}
.theme-toggle:hover {
  background: var(--glass-bg-hover);
  color: var(--text-1);
  border-color: var(--glass-border-strong);
  transform: rotate(15deg) scale(1.1);
}
.theme-toggle:active {
  transform: rotate(15deg) scale(0.95);
}

/* User Info */
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px 4px 4px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--dur-spring-fast) var(--ease-spring-soft);
}
.user-info:hover {
  background: var(--surface-hover);
  transform: translateY(-2px);
}
.user-info:active {
  transform: translateY(0) scale(0.98);
}
.user-avatar {
  background: var(--surface-3);
  color: var(--text-2);
  font-weight: 600;
  font-size: 13px;
}
.user-details {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.username {
  font: 500 13px/1 var(--font-ui);
  color: var(--text-1);
}
.user-role {
  font: 400 11px/1 var(--font-mono);
  color: var(--text-4);
  letter-spacing: 0.04em;
}
.dropdown-arrow {
  color: var(--text-4);
  transition: transform var(--dur-fast) var(--ease);
}
.user-info:hover .dropdown-arrow {
  transform: translateY(2px);
}

/* Main Content */
.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
  background: var(--bg);
}

/* Page Transitions */
.fade-slide-enter-active {
  transition: opacity var(--dur-slow) var(--ease-cinema),
              transform var(--dur-slow) var(--ease-cinema);
}
.fade-slide-leave-active {
  transition: opacity var(--dur-fast) var(--ease),
              transform var(--dur-fast) var(--ease);
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* Responsive */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 1030;
    transform: translateX(-100%);
  }
  .mobile-menu-btn { display: flex; }
  .user-details { display: none; }
  .header { padding: 0 16px; }
}
</style>
