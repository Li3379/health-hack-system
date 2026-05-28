import { request } from '@/utils/request'

export interface TrendDataVO {
  metricKey: string
  metricDisplayName: string
  unit: string
  dates: string[]
  values: number[]
}

export interface WeeklyStatsVO {
  currentHealthMetricsCount: number
  previousHealthMetricsCount: number
  healthMetricsChangePercent: number
  currentWellnessMetricsCount: number
  previousWellnessMetricsCount: number
  wellnessMetricsChangePercent: number
  currentDeviceSyncCount: number
  previousDeviceSyncCount: number
  deviceSyncChangePercent: number
  currentAiRecognizeCount: number
  previousAiRecognizeCount: number
  aiRecognizeChangePercent: number
  currentAvgScore: number
  previousAvgScore: number
  avgScoreChangePercent: number
}

export interface MonthlyStatsVO {
  currentHealthMetricsCount: number
  previousHealthMetricsCount: number
  healthMetricsChangePercent: number
  currentWellnessMetricsCount: number
  previousWellnessMetricsCount: number
  wellnessMetricsChangePercent: number
  currentDeviceSyncCount: number
  previousDeviceSyncCount: number
  deviceSyncChangePercent: number
  currentAiRecognizeCount: number
  previousAiRecognizeCount: number
  aiRecognizeChangePercent: number
  currentAvgScore: number
  previousAvgScore: number
  avgScoreChangePercent: number
  activeDays: number
}

export interface DashboardSummaryVO {
  healthScore: number
  healthScoreLevel: string
  todayHealthMetricsCount: number
  todayWellnessMetricsCount: number
  weekHealthMetricsCount: number
  weekWellnessMetricsCount: number
  unreadAlertsCount: number
  highSeverityAlertsCount: number
  totalDeviceSyncCount: number
  totalAiRecognizeCount: number
  todayDeviceSyncCount: number
  todayAiRecognizeCount: number
  weekActiveDays: number
}

export const statsApi = {
  /** Get weekly stats with week-over-week comparison */
  getWeekly() {
    return request.get<WeeklyStatsVO>('/api/stats/weekly')
  },

  /** Get monthly stats with month-over-month comparison */
  getMonthly() {
    return request.get<MonthlyStatsVO>('/api/stats/monthly')
  },

  /** Get time-series trend data for sparklines */
  getTrends(metric: string, days: number = 7) {
    return request.get<TrendDataVO[]>('/api/stats/trends', { params: { metric, days } })
  },

  /** Get dashboard summary */
  getSummary() {
    return request.get<DashboardSummaryVO>('/api/stats/summary')
  },
}
