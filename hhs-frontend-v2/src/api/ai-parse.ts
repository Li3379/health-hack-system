import { request } from '@/utils/request'

/**
 * AI解析历史记录项
 */
export interface AiParseHistoryItem {
  id: number
  inputText: string
  inputType: string
  inputTypeDisplay: string
  metricsCount: number
  confirmed: boolean
  createTime: string
}

/**
 * AI解析历史列表响应
 */
export interface AiParseHistoryResponse {
  total: number
  page: number
  size: number
  records: AiParseHistoryItem[]
}

/**
 * 解析出的单个指标
 */
export interface ParsedMetric {
  metricKey: string
  metricName: string
  value: number
  unit: string
  confidence: number
  category: 'HEALTH' | 'WELLNESS'
  recordDate: string
  selected: boolean
}

/**
 * AI解析记录详情
 */
export interface AiParseDetail {
  metrics: ParsedMetric[]
  summary: string
  warnings: string[]
  parseHistoryId: number
}

export const aiParseApi = {
  /**
   * 获取AI解析历史记录
   */
  getHistory(page: number = 1, size: number = 10) {
    return request.get<AiParseHistoryResponse>('/api/ai/history', {
      params: { page, size }
    })
  },

  /**
   * 获取AI解析记录详情
   */
  getHistoryDetail(id: number) {
    return request.get<AiParseDetail>(`/api/ai/history/${id}`)
  },

  /**
   * 删除AI解析记录
   */
  deleteHistory(id: number) {
    return request.delete<boolean>(`/api/ai/history/${id}`)
  },

  /**
   * 获取今日剩余解析次数
   */
  getRemainingCount() {
    return request.get<number>('/api/ai/parse-metrics/remaining')
  }
}
