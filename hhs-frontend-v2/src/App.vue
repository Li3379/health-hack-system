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
  // 静默预加载指标元数据（公共接口，无需登录，保障离线与首屏一致性）
  metricMetaStore.fetchMetadata()

  // 如果已登录，获取未读预警数量
  if (authStore.isAuthenticated) {
    alertStore.fetchUnreadCount()
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
