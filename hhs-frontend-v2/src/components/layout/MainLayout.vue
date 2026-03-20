<template>
  <el-container class="main-layout">
    <!-- Sidebar -->
    <el-aside :width="sidebarWidth" class="sidebar">
      <!-- Logo -->
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

      <!-- Navigation Menu -->
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
              <el-badge
                v-if="alertStore.unreadCount > 0"
                :value="alertStore.unreadCount"
                class="menu-badge"
              />
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

      <!-- Collapse Toggle -->
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
          <!-- Theme Toggle -->
          <el-tooltip
            :content="themeStore.effectiveTheme === 'dark' ? '切换到亮色模式' : '切换到暗色模式'"
            placement="bottom"
          >
            <button class="theme-toggle" @click="themeStore.toggleTheme()">
              <el-icon :size="20">
                <Sunny v-if="themeStore.effectiveTheme === 'dark'" />
                <Moon v-else />
              </el-icon>
            </button>
          </el-tooltip>

          <!-- User Dropdown -->
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <el-avatar :src="authStore.user?.avatar" :size="36" class="user-avatar">
                {{ authStore.user?.nickname?.[0] || authStore.user?.username?.[0] }}
              </el-avatar>
              <div class="user-details">
                <span class="username">
                  {{ authStore.user?.nickname || authStore.user?.username }}
                </span>
                <span class="user-role">健康用户</span>
              </div>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="push-settings">
                  <el-icon><Bell /></el-icon>
                  推送设置
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
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
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAlertStore } from '@/stores/alert'
import { useThemeStore } from '@/stores/theme'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const alertStore = useAlertStore()
const themeStore = useThemeStore()

const collapsed = ref(false)
const sidebarWidth = computed(() => (collapsed.value ? '64px' : '240px'))

const activeMenu = computed(() => route.path)

const toggleCollapse = () => {
  collapsed.value = !collapsed.value
}

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
  if (command === 'profile') {
    router.push('/user/profile')
  } else if (command === 'push-settings') {
    router.push('/user/push-settings')
  } else if (command === 'logout') {
    authStore.logout()
  }
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
  overflow: hidden;
}

/* Sidebar Styles */
.sidebar {
  background: var(--color-sidebar-bg);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-base);
  overflow: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
}

.logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.logo-icon {
  width: 32px;
  height: 32px;
  color: var(--color-primary-light);
  flex-shrink: 0;
}

.logo-text {
  font-size: 20px;
  font-weight: var(--font-weight-bold);
  color: var(--color-sidebar-text-active);
  white-space: nowrap;
  overflow: hidden;
}

.menu-scrollbar {
  flex: 1;
  overflow: hidden;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  color: var(--color-sidebar-text);
  height: 48px;
  line-height: 48px;
  margin: 4px 8px;
  border-radius: var(--radius-md);
  transition: var(--transition-colors);
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: var(--color-sidebar-hover);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--color-primary) !important;
  color: var(--color-sidebar-text-active) !important;
}

.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--color-primary-lightest);
}

.sidebar-menu :deep(.el-menu) {
  background: transparent;
}

.sidebar-menu :deep(.el-menu--inline .el-menu-item) {
  padding-left: 52px !important;
  height: 40px;
  line-height: 40px;
}

.menu-badge {
  margin-left: 8px;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.collapse-btn {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-sidebar-hover);
  border: none;
  border-radius: var(--radius-md);
  color: var(--color-sidebar-text);
  cursor: pointer;
  transition: var(--transition-colors);
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--color-sidebar-text-active);
}

/* Main Container */
.main-container {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-secondary);
  overflow: hidden;
}

/* Header Styles */
.header {
  height: var(--header-height);
  background: var(--color-bg-primary);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: var(--shadow-xs);
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
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: var(--transition-colors);
}

.mobile-menu-btn:hover {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
}

.page-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.theme-toggle {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-tertiary);
  border: none;
  border-radius: var(--radius-full);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: var(--transition-colors), var(--transition-transform);
}

.theme-toggle:hover {
  background: var(--color-primary);
  color: var(--color-text-inverse);
  transform: rotate(15deg);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 12px 6px 6px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: var(--transition-colors);
}

.user-info:hover {
  background: var(--color-bg-tertiary);
}

.user-avatar {
  background: var(--color-primary);
  color: var(--color-text-inverse);
  font-weight: var(--font-weight-semibold);
}

.user-details {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.username {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.user-role {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.dropdown-arrow {
  color: var(--color-text-tertiary);
  transition: var(--transition-transform);
}

.user-info:hover .dropdown-arrow {
  transform: translateY(2px);
}

/* Main Content */
.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
  background: var(--color-bg-secondary);
}

/* Page Transitions */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* Responsive */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: var(--z-fixed);
    transform: translateX(collapsed.value ? '-100%': '0');
  }

  .mobile-menu-btn {
    display: flex;
  }

  .user-details {
    display: none;
  }

  .header {
    padding: 0 16px;
  }
}
</style>
