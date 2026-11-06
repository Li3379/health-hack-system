<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useAlertStore } from '@/stores/alert'
import { useMetricMetaStore } from '@/stores/metricMeta'

const authStore = useAuthStore()
const alertStore = useAlertStore()
const metricMetaStore = useMetricMetaStore()

onMounted(() => {
  // 静默预加载指标元数据（公共接口，无需登录，保障离线与首屏一致性）。
  // silent: 启动期后台请求，失败时由 store 的静态兜底承担，不应弹任何提示。
  metricMetaStore.fetchMetadata({ silent: true })

  // 如果已登录，获取未读预警数量
  if (authStore.isAuthenticated) {
    // silent: 未读数属后台轮询，失败时徽标保持原值，同样不打扰用户。
    alertStore.fetchUnreadCount({ silent: true })
  }
})
</script>

<style>
#app {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}
</style>
