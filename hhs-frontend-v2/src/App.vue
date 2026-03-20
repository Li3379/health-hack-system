<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useAlertStore } from '@/stores/alert'

const authStore = useAuthStore()
const alertStore = useAlertStore()

onMounted(() => {
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
