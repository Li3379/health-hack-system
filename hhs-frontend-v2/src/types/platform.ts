/**
 * Platform status enum values.
 * Must match backend PlatformStatus enum.
 */
export type PlatformStatus =
  | 'AVAILABLE'
  | 'REQUIRES_MINI_PROGRAM'
  | 'REQUIRES_APP'
  | 'COMING_SOON'
  | 'NOT_CONFIGURED'
  | 'UNAVAILABLE'

/**
 * Platform capability enum values.
 * Must match backend PlatformCapability enum.
 */
export type PlatformCapability =
  | 'WEB_OAUTH'
  | 'REALTIME_SYNC'
  | 'HISTORICAL_DATA'
  | 'MINI_PROGRAM'
  | 'NATIVE_APP'

/**
 * Platform metadata response from API.
 */
export interface PlatformMetadata {
  platform: string
  displayName: string
  status: PlatformStatus
  capabilities: PlatformCapability[]
  supportedDataTypes: string[]
  unavailableReason?: string
  guideUrl?: string
  icon?: string
  isConnectable?: boolean
}

/**
 * Platform availability response from API.
 */
export interface PlatformAvailability {
  platform: string
  displayName: string
  status: PlatformStatus
  statusLabel: string
  connectable: boolean
  unavailableReason?: string
  guideUrl?: string
  supportedDataTypes: string[]
}

/**
 * OAuth prepare response from API.
 */
export interface OAuthPrepareResponse {
  platform: string
  authUrl: string
  callbackPath: string
  ttlSeconds: number
}

/**
 * Config status response from API.
 */
export interface ConfigStatus {
  encryptionReady: boolean
  anyPlatformAvailable: boolean
  availablePlatforms: string[]
  platformsRequiringAction: string[]
}

/**
 * Status display configuration.
 */
export const STATUS_CONFIG: Record<
  PlatformStatus,
  {
    label: string
    tagType: 'success' | 'warning' | 'info' | 'danger'
    color: string
  }
> = {
  AVAILABLE: { label: '可用', tagType: 'success', color: '#67c23a' },
  REQUIRES_MINI_PROGRAM: { label: '需要小程序', tagType: 'warning', color: '#e6a23c' },
  REQUIRES_APP: { label: '需要应用', tagType: 'warning', color: '#e6a23c' },
  COMING_SOON: { label: '即将支持', tagType: 'info', color: '#909399' },
  NOT_CONFIGURED: { label: '未配置', tagType: 'danger', color: '#f56c6c' },
  UNAVAILABLE: { label: '暂不可用', tagType: 'danger', color: '#f56c6c' }
}

/**
 * Platform display names.
 */
export const PLATFORM_NAMES: Record<string, string> = {
  huawei: '华为运动健康',
  xiaomi: '小米运动',
  wechat: '微信运动',
  apple: 'Apple Health'
}

/**
 * Platform icons.
 */
export const PLATFORM_ICONS: Record<string, string> = {
  huawei: 'H',
  xiaomi: 'M',
  wechat: 'W',
  apple: 'A'
}

/**
 * Platform colors.
 */
export const PLATFORM_COLORS: Record<string, string> = {
  huawei: '#cf0a2c',
  xiaomi: '#ff6700',
  wechat: '#07c160',
  apple: '#000000'
}

/**
 * Data type display names.
 */
export const DATA_TYPE_NAMES: Record<string, string> = {
  heart_rate: '心率',
  steps: '步数',
  sleep: '睡眠',
  blood_pressure: '血压',
  blood_glucose: '血糖',
  spo2: '血氧'
}
