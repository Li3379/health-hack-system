import { request } from '@/utils/request'
import type { ApiResponse, MetricMetaMap } from '@/types/api'

/**
 * 指标元数据 API 客户端
 * 对接后端 GET /api/metrics/metadata (带 ETag 与 24h 缓存)
 */
export const metricMetaApi = {
  getMetadata(): Promise<ApiResponse<MetricMetaMap>> {
    return request.get<MetricMetaMap>('/api/metrics/metadata')
  }
}
