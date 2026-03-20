import { request } from '@/utils/request'
import type { HealthScoreVO, HealthReportVO } from '@/types/api'

export const scoreApi = {
  // 获取健康评分
  getScore() {
    return request.get<HealthScoreVO>('/api/health/score')
  },

  // 重新计算评分
  recalculateScore() {
    return request.post<HealthScoreVO>('/api/health/score/recalculate')
  },

  // 获取评分详情
  getScoreBreakdown() {
    return request.get<HealthScoreVO>('/api/health/score/breakdown')
  },

  // 获取评分历史
  getScoreHistory(days = 30) {
    return request.get<Array<{ date: string; score: number; level: string }>>(
      '/api/health/score/history',
      { params: { days } }
    )
  },

  // ==================== 健康报告 API ====================

  /**
   * 获取最新健康报告（优先缓存）
   * 如果有缓存的报告则直接返回，否则生成新报告
   */
  getReport() {
    return request.get<HealthReportVO>('/api/health/report')
  },

  /**
   * 强制重新生成健康报告
   * 清除缓存并生成新报告
   */
  generateReport() {
    return request.post<HealthReportVO>('/api/health/report/generate')
  },

  /**
   * 获取报告历史列表
   * @param limit 返回数量限制，默认10条
   */
  getReportHistory(limit = 10) {
    return request.get<HealthReportVO[]>('/api/health/report/history', { params: { limit } })
  },

  /**
   * 根据报告ID获取特定报告
   * @param reportId 报告唯一标识
   */
  getReportById(reportId: string) {
    return request.get<HealthReportVO>(`/api/health/report/${reportId}`)
  }
}
