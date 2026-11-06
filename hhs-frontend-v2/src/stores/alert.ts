import { defineStore } from 'pinia'
import { ref } from 'vue'
import { alertApi } from '@/api/alert'
import type { AlertVO } from '@/types/api'

export const useAlertStore = defineStore('alert', () => {
  const unreadCount = ref(0)
  const recentAlerts = ref<AlertVO[]>([])

  // 获取未读数量
  // silent 只透传"失败不展示"，不改变 store 行为：计数失败时保持原值（D-10）。
  const fetchUnreadCount = async (options: { silent?: boolean } = {}): Promise<void> => {
    try {
      const res = await alertApi.getUnreadCount(
        options.silent ? { silent: true } : undefined
      )
      unreadCount.value = res.data
    } catch (error) {
      // silent: 后台计数失败保持原值，由调用方决定是否提示
    }
  }

  // 获取最近预警
  const fetchRecentAlerts = async (limit = 5) => {
    try {
      const res = await alertApi.getRecentAlerts(limit)
      recentAlerts.value = res.data
    } catch (error) {
      // recent alerts fetch failed
    }
  }

  // 标记为已读
  const markAsRead = async (id: number) => {
    try {
      await alertApi.markAsRead(id)
      unreadCount.value = Math.max(0, unreadCount.value - 1)
      await fetchRecentAlerts()
    } catch (error) {
      // mark as read failed
    }
  }

  // 全部标记为已读
  const markAllAsRead = async () => {
    try {
      await alertApi.markAllAsRead()
      unreadCount.value = 0
      await fetchRecentAlerts()
    } catch (error) {
      // mark all as read failed
    }
  }

  return {
    unreadCount,
    recentAlerts,
    fetchUnreadCount,
    fetchRecentAlerts,
    markAsRead,
    markAllAsRead
  }
})
