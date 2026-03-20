import { defineStore } from 'pinia'
import { ref } from 'vue'
import { alertApi } from '@/api/alert'
import type { AlertVO } from '@/types/api'

export const useAlertStore = defineStore('alert', () => {
  const unreadCount = ref(0)
  const recentAlerts = ref<AlertVO[]>([])

  // 获取未读数量
  const fetchUnreadCount = async () => {
    try {
      const res = await alertApi.getUnreadCount()
      unreadCount.value = res.data
    } catch (error) {
      console.error('Failed to fetch unread count:', error)
    }
  }

  // 获取最近预警
  const fetchRecentAlerts = async (limit = 5) => {
    try {
      const res = await alertApi.getRecentAlerts(limit)
      recentAlerts.value = res.data
    } catch (error) {
      console.error('Failed to fetch recent alerts:', error)
    }
  }

  // 标记为已读
  const markAsRead = async (id: number) => {
    try {
      await alertApi.markAsRead(id)
      unreadCount.value = Math.max(0, unreadCount.value - 1)
      await fetchRecentAlerts()
    } catch (error) {
      console.error('Failed to mark as read:', error)
    }
  }

  // 全部标记为已读
  const markAllAsRead = async () => {
    try {
      await alertApi.markAllAsRead()
      unreadCount.value = 0
      await fetchRecentAlerts()
    } catch (error) {
      console.error('Failed to mark all as read:', error)
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
