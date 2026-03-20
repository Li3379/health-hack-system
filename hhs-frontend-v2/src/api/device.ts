import { request } from '@/utils/request'
import type {
  DeviceConnectionVO,
  SyncResultVO,
  SyncHistoryVO,
  PlatformInfo,
  PageResult
} from '@/types/api'
import type {
  PlatformMetadata,
  PlatformAvailability,
  OAuthPrepareResponse,
  ConfigStatus
} from '@/types/platform'

// 平台配置状态类型
export interface PlatformConfigStatus {
  platform: string
  platformName: string
  configured: boolean
  oauthReady: boolean
  lastTestTime?: string
  testResult?: string
  missingConfig?: string[]
  enabled: boolean
  supportedDataTypes?: string[]
  guideUrl?: string
}

// 配置状态响应类型
export interface ConfigStatusResponse {
  platforms: PlatformConfigStatus[]
  encryptionKeyConfigured: boolean
}

// 配置就绪状态响应类型
export interface ConfigReadyResponse {
  ready: boolean
  encryptionReady: boolean
  anyPlatformReady: boolean
  unconfiguredPlatforms: string[]
}

// 管理员配置请求类型
export interface AdminConfigRequest {
  platform: string
  clientId: string
  clientSecret: string
  authUrl?: string
  tokenUrl?: string
  redirectUri?: string
  scopes?: string[]
  enabled?: boolean
}

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
  },

  // ========== 配置状态 API ==========

  // 获取所有平台的配置状态
  getConfigStatus() {
    return request.get<ConfigStatusResponse>('/api/device/config/status')
  },

  // 获取指定平台的配置状态
  getPlatformConfigStatus(platform: string) {
    return request.get<PlatformConfigStatus>(`/api/device/config/status/${platform}`)
  },

  // 检查设备同步就绪状态
  checkConfigReady() {
    return request.get<ConfigReadyResponse>('/api/device/config/ready')
  },

  // ========== 管理员配置 API ==========

  // 获取所有平台配置状态（管理员）
  adminGetAllStatus() {
    return request.get<PlatformConfigStatus[]>('/api/admin/device/config/status')
  },

  // 保存平台配置
  adminSaveConfig(data: AdminConfigRequest) {
    return request.post<void>('/api/admin/device/config', data)
  },

  // 测试平台配置
  adminTestConfig(platform: string) {
    return request.post<PlatformConfigStatus>(`/api/admin/device/config/test/${platform}`)
  },

  // 删除平台配置
  adminDeleteConfig(platform: string) {
    return request.delete<void>(`/api/admin/device/config/${platform}`)
  },

  // 初始化默认配置
  adminInitDefaults() {
    return request.post<void>('/api/admin/device/config/init')
  },

  // ========== 平台元数据 API ==========

  // 获取所有平台元数据
  getPlatformMetadata() {
    return request.get<PlatformMetadata[]>('/api/device/platforms/metadata')
  },

  // 获取指定平台可用性
  getPlatformAvailability(platform: string) {
    return request.get<PlatformAvailability>(`/api/device/platforms/${platform}/availability`)
  },

  // 获取配置状态
  getSystemConfigStatus() {
    return request.get<ConfigStatus>('/api/device/platforms/config/status')
  },

  // 准备 OAuth 连接
  prepareOAuthConnect(platform: string) {
    return request.post<OAuthPrepareResponse>(`/api/device/platforms/connect/${platform}/prepare`)
  }
}
