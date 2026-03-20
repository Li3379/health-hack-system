import { request } from '@/utils/request'
import type { HealthMetricRequest, HealthMetricVO, PageResult } from '@/types/api'

export const healthApi = {
  // 获取健康指标列表
  getMetrics(params: { page?: number; size?: number; userId?: number }) {
    return request.get<PageResult<HealthMetricVO>>('/api/metrics', { params })
  },

  // 创建健康指标
  createMetric(data: HealthMetricRequest) {
    return request.post<HealthMetricVO>('/api/metrics', data)
  },

  // 更新健康指标
  updateMetric(id: number, data: HealthMetricRequest) {
    return request.put<HealthMetricVO>(`/api/metrics/${id}`, data)
  },

  // 删除健康指标
  deleteMetric(id: number) {
    return request.delete<void>(`/api/metrics/${id}`)
  }
}
