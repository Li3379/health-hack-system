import { request } from '@/utils/request'
import type { HealthMetricRequest, HealthMetricVO, PageResult } from '@/types/api'

export const healthApi = {
  // 获取健康指标列表
  getMetrics(params: {
    page?: number
    size?: number
    metricKey?: string
    startDate?: string
    endDate?: string
  }) {
    return request.get<PageResult<HealthMetricVO>>('/api/metrics', { params })
  },

  /**
   * 最近录入（跨 HEALTH / WELLNESS，统一按 createTime 排序）。
   *
   * 不能用 getMetrics + wellnessApi.getMetrics 各取一页再在浏览器合并：两个接口各自只返回
   * 一个 category、各自截断，且排序字段不同（create_time vs record_date），合并时保健指标
   * 占满名额会把刚录入的健康指标整段挤掉。
   */
  getRecentMetrics(limit = 10) {
    return request.get<HealthMetricVO[]>('/api/metrics/recent', { params: { limit } })
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
