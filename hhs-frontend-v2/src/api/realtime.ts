import { request } from '@/utils/request'
import type { RealtimeMetricRequest, RealtimeMetricVO, MetricTrendVO } from '@/types/api'

export const realtimeApi = {
  // 添加实时指标
  addMetric(data: RealtimeMetricRequest) {
    return request.post<RealtimeMetricVO>('/api/realtime/metrics', data)
  },

  // 获取最新指标
  getLatestMetrics() {
    return request.get<RealtimeMetricVO[]>('/api/realtime/metrics')
  },

  // 获取指标趋势
  getMetricTrend(metricKey: string, hours = 24) {
    return request.get<MetricTrendVO>('/api/realtime/metrics/trend', {
      params: { metricKey, hours }
    })
  }
}
