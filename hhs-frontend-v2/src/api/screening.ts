import { request } from '@/utils/request'
import type { ExaminationReportVO, LabResultVO, OcrStatusVO, PageResult } from '@/types/api'

export const screeningApi = {
  // 上传报告
  uploadReport(
    file: File,
    params?: {
      reportName?: string
      reportType?: string
      institution?: string
      reportDate?: string
    }
  ) {
    const formData = new FormData()
    formData.append('file', file)
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value) formData.append(key, value)
      })
    }
    return request.upload<ExaminationReportVO>('/api/screening/reports/upload', formData)
  },

  // 报告列表
  listReports(params: { page?: number; size?: number }) {
    return request.get<PageResult<ExaminationReportVO>>('/api/screening/reports', { params })
  },

  // 报告详情
  getReport(id: number) {
    return request.get<ExaminationReportVO>(`/api/screening/reports/${id}`)
  },

  // 报告详情（别名）
  getReportDetail(id: number) {
    return request.get<ExaminationReportVO>(`/api/screening/reports/${id}`)
  },

  // 删除报告
  deleteReport(id: number) {
    return request.delete<void>(`/api/screening/reports/${id}`)
  },

  // 触发 OCR
  triggerOcr(id: number) {
    return request.post<void>(`/api/screening/reports/${id}/ocr`)
  },

  // OCR 状态
  getOcrStatus(id: number) {
    return request.get<OcrStatusVO>(`/api/screening/reports/${id}/ocr-status`)
  },

  // 检验指标
  getLabResults(id: number) {
    return request.get<LabResultVO[]>(`/api/screening/reports/${id}/lab-results`)
  }
}
