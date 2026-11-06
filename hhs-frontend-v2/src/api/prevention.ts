import type { AxiosRequestConfig } from 'axios'
import { request } from '@/utils/request'
import type {
  HealthProfileRequest,
  HealthProfileVO,
  HealthMetricRequest,
  HealthMetricVO,
  HealthMetricTrendVO,
  RiskAssessmentVO
} from '@/types/api'

export const preventionApi = {
  // 健康档案
  createProfile(data: HealthProfileRequest) {
    return request.post<HealthProfileVO>('/api/prevention/health-profile', data)
  },

  // config 透传调用点的 `{ silent: true }`（D-09）：档案不存在是正常状态，不弹红色提示。
  getProfile(config?: AxiosRequestConfig) {
    return request.get<HealthProfileVO>('/api/prevention/health-profile', config)
  },

  updateProfile(id: number, data: HealthProfileRequest) {
    return request.put<HealthProfileVO>(`/api/prevention/health-profile/${id}`, data)
  },

  // 健康指标
  addMetric(data: HealthMetricRequest) {
    return request.post<HealthMetricVO>('/api/prevention/health-metrics', data)
  },

  listMetrics(params: { metricKey?: string; startDate?: string; endDate?: string }) {
    return request.get<HealthMetricVO[]>('/api/prevention/health-metrics', { params })
  },

  getMetricsTrend(params: { metricKey?: string; startDate?: string; endDate?: string }) {
    return request.get<HealthMetricTrendVO[]>('/api/prevention/health-metrics/trend', { params })
  },

  deleteMetric(id: number) {
    return request.delete<void>(`/api/prevention/health-metrics/${id}`)
  },

  // 风险评估
  createRiskAssessment() {
    return request.post<RiskAssessmentVO[]>('/api/prevention/risk-assessment')
  },

  getRiskAssessment(id: number) {
    return request.get<RiskAssessmentVO>(`/api/prevention/risk-assessment/${id}`)
  },

  listRiskAssessments() {
    return request.get<RiskAssessmentVO[]>('/api/prevention/risk-assessment')
  },

  // 别名，兼容前端调用
  getRiskAssessments(_params?: { page?: number; size?: number }) {
    return request.get<RiskAssessmentVO[]>('/api/prevention/risk-assessment')
  }
}
