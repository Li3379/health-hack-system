import { request } from '@/utils/request'
import type { WellnessMetricRequest, WellnessMetricVO, WellnessSummaryVO, WellnessTrendVO, PageResult } from '@/types/api'

export const wellnessApi = {
  // 获取保健指标列表
  getMetrics(params: { page?: number; size?: number; metricKey?: string }) {
    return request.get<PageResult<WellnessMetricVO>>('/api/wellness', { params })
  },

  // 创建保健指标
  createMetric(data: WellnessMetricRequest) {
    return request.post<WellnessMetricVO>('/api/wellness', data)
  },

  // 获取趋势数据
  getTrend(metricKey: string, startDate: string, endDate: string) {
    return request.get<WellnessTrendVO>(`/api/wellness/trend/${metricKey}`, {
      params: { startDate, endDate }
    })
  },

  // 获取仪表盘摘要
  getSummary(days = 7) {
    return request.get<WellnessSummaryVO>('/api/wellness/summary', { params: { days } })
  },

  // 获取最新指标值
  getLatest() {
    return request.get<Record<string, WellnessMetricVO>>('/api/wellness/latest')
  }
}