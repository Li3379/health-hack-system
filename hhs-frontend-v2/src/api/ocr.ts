import { request } from '@/utils/request'

/**
 * OCR历史记录项
 */
export interface OcrHistoryItem {
  id: number
  ocrType: string
  ocrTypeDisplay: string
  status: string
  metricsCount: number
  confirmed: boolean
  originalFilename: string
  durationMs: number
  createTime: string
  errorMessage?: string
}

/**
 * OCR历史列表响应
 */
export interface OcrHistoryResponse {
  total: number
  page: number
  size: number
  records: OcrHistoryItem[]
}

/**
 * 识别出的单个指标
 */
export interface RecognizedMetric {
  metricKey: string
  name: string
  value: number
  unit: string
  confidence: number
  category: 'HEALTH' | 'WELLNESS'
  recordDate: string
  referenceRange?: string
  abnormal?: boolean
  selected: boolean
}

/**
 * OCR记录详情
 */
export interface OcrRecordDetail {
  status: string
  ocrType: string
  metrics: RecognizedMetric[]
  rawText: string
  summary: string
  errorMessage?: string
  ocrRecordId: number
  usedMockData?: boolean
  warning?: string
}

export const ocrApi = {
  /**
   * 获取OCR历史记录
   */
  getHistory(page: number = 1, size: number = 10) {
    return request.get<OcrHistoryResponse>('/api/ocr/history', {
      params: { page, size }
    })
  },

  /**
   * 获取OCR记录详情
   */
  getRecordDetail(recordId: number) {
    return request.get<OcrRecordDetail>(`/api/ocr/history/${recordId}`)
  },

  /**
   * 删除OCR记录
   */
  deleteRecord(recordId: number) {
    return request.delete<boolean>(`/api/ocr/history/${recordId}`)
  }
}
