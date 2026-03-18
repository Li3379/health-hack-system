import { request } from '@/utils/request'
import type { DeviceConnectionVO, SyncResultVO, SyncHistoryVO, PlatformInfo, PageResult } from '@/types/api'

export const deviceApi = {
  // 获取设备连接列表
  getConnections() {
    return request.get<DeviceConnectionVO[]>('/api/device/connections')
  },

  // 获取支持的平台列表
  getPlatforms() {
    return request.get<PlatformInfo[]>('/api/device/platforms')
  },

  // 连接设备（获取OAuth授权URL）
  connect(platform: string) {
    return request.post<string>(`/api/device/connect/${platform}`)
  },

  // 断开设备连接
  disconnect(platform: string) {
    return request.delete<void>(`/api/device/disconnect/${platform}`)
  },

  // 同步指定设备
  sync(platform: string) {
    return request.post<SyncResultVO>(`/api/device/sync/${platform}`)
  },

  // 同步所有设备
  syncAll() {
    return request.post<SyncResultVO[]>('/api/device/sync/all')
  },

  // 获取同步历史
  getSyncHistory(params: { page?: number; size?: number }) {
    return request.get<PageResult<SyncHistoryVO>>('/api/device/sync/history', { params })
  }
}