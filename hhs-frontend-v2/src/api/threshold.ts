import { request } from '@/utils/request'
import type { UserThreshold, UserThresholdRequest, PageResult } from '@/types/api'

export const thresholdApi = {
  // 获取阈值列表
  getThresholds(params: { page?: number; size?: number; userId?: number }) {
    return request.get<PageResult<UserThreshold>>('/api/thresholds', { params })
  },

  // 获取用户所有阈值
  getByUserId(userId: number) {
    return request.get<UserThreshold[]>(`/api/thresholds/user/${userId}`)
  },

  // 根据指标获取阈值
  getByMetricKey(userId: number, metricKey: string) {
    return request.get<UserThreshold>('/api/thresholds/by-key', {
      params: { userId, metricKey }
    })
  },

  // 创建阈值
  createThreshold(data: UserThresholdRequest) {
    return request.post<UserThreshold>('/api/thresholds', data)
  },

  // 更新阈值
  updateThreshold(id: number, data: UserThresholdRequest) {
    return request.put<UserThreshold>(`/api/thresholds/${id}`, data)
  },

  // 删除阈值
  deleteThreshold(id: number) {
    return request.delete<void>(`/api/thresholds/${id}`)
  }
}
