import type { AxiosRequestConfig } from 'axios'
import { request } from '@/utils/request'
import type {
  HealthProfileRequest,
  HealthProfileVO,
  HealthMetricRequest,
  HealthMetricVO,
  HealthMetricTrendVO,
  ProfileCompletenessVO,
  RiskAssessmentVO,
  MetricCoverageVO
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

  // 档案完整度：对已登录用户该端点永不 404 —— 无档案时后端返回 200 + score 0 +
  // hasProfile false（13-02）。因此这里透传 `{ silent: true }` 只为让真实的传输层
  // 故障不要在一个纯引导型表面弹红条；store 仍需自己区分「404 合成 0%」与「失败留空」。
  getCompleteness(config?: AxiosRequestConfig) {
    return request.get<ProfileCompletenessVO>('/api/prevention/profile/completeness', config)
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

  // 指标覆盖情况：逐项返回每个指标的取值、参考范围与达标状态。
  getMetricCoverage() {
    return request.get<MetricCoverageVO[]>('/api/prevention/risk-assessment/metric-coverage')
  },

  // 别名，兼容前端调用
  getRiskAssessments(_params?: { page?: number; size?: number }) {
    return request.get<RiskAssessmentVO[]>('/api/prevention/risk-assessment')
  }
}
