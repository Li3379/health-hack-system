import { request } from '@/utils/request'
import type { AlertVO, PageResult } from '@/types/api'

export const alertApi = {
  // 获取预警列表
  getAlerts(params: { page?: number; size?: number; alertType?: string; isRead?: boolean }) {
    return request.get<PageResult<AlertVO>>('/api/alerts', { params })
  },

  // 获取未读数量
  getUnreadCount() {
    return request.get<number>('/api/alerts/unread-count')
  },

  // 获取最近预警
  getRecentAlerts(limit = 5) {
    return request.get<AlertVO[]>('/api/alerts/recent', { params: { limit } })
  },

  // 标记为已读
  markAsRead(id: number) {
    return request.put<boolean>(`/api/alerts/${id}/read`)
  },

  // 确认预警
  acknowledgeAlert(id: number) {
    return request.put<boolean>(`/api/alerts/${id}/acknowledge`)
  },

  // 全部标记为已读
  markAllAsRead() {
    return request.put<number>('/api/alerts/read-all')
  },

  // 获取统计信息
  getStatistics() {
    return request.get<Record<string, any>>('/api/alerts/statistics')
  },

  // 删除预警
  deleteAlert(id: number) {
    return request.delete<void>(`/api/alerts/${id}`)
  }
}
