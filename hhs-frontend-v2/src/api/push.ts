import { request } from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface PushConfig {
  id: number
  channelType: string
  channelLabel: string
  configKey: string
  configValue: string
  enabled: boolean
  offlineSupported: boolean
  isConfigured: boolean
  /** 服务端是否具备该渠道的投递能力（对应后端 PushChannelManager.hasChannel） */
  availability: boolean
  /**
   * 该渠道最近一次投递的结果（SUCCESS / FAILED / SKIPPED），从未尝试过时为 null。
   *
   * `availability` 只说明「服务端注册了这个渠道」（EMAIL 仅看 spring.mail.host 非空），
   * 完全不说明凭据是否可用。页面曾经只凭 availability 就写「支持离线推送」，
   * 等于承诺一件系统做不到的事。这个字段带上证据：最近一次到底成没成。
   */
  deliveryStatus?: 'SUCCESS' | 'FAILED' | 'SKIPPED' | null
  /** 最近一次投递的时间；deliveryStatus 为 null 时也为 null。 */
  lastDeliveryAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface PushHistory {
  id: number
  alertId: number
  channelType: string
  channelLabel: string
  status: string
  message: string
  pushedAt: string
}

export interface PushStats {
  period: string
  channels: Array<{
    channelType: string
    status: string
    count: number
  }>
}

export interface UpdatePushConfigRequest {
  channelType?: string
  configKey?: string
  configValue?: string
  enabled?: boolean
}

/**
 * Get all push configurations
 */
export function getPushConfigs(): Promise<ApiResponse<PushConfig[]>> {
  return request.get('/api/push/config')
}

/**
 * Get specific channel configuration
 */
export function getPushConfig(channelType: string): Promise<ApiResponse<PushConfig>> {
  return request.get(`/api/push/config/${channelType}`)
}

/**
 * Update push configuration
 */
export function updatePushConfig(
  channelType: string,
  data: UpdatePushConfigRequest
): Promise<ApiResponse<PushConfig>> {
  return request.put(`/api/push/config/${channelType}`, data)
}

/**
 * Delete push configuration
 */
export function deletePushConfig(channelType: string): Promise<ApiResponse<void>> {
  return request.delete(`/api/push/config/${channelType}`)
}

/**
 * Test push channel
 */
export function testPushChannel(channelType: string): Promise<
  ApiResponse<{
    success: boolean
    channel: string
    message: string
    timestamp: string
  }>
> {
  return request.post(`/api/push/config/${channelType}/test`)
}

/**
 * Get push history
 */
export function getPushHistory(limit = 20): Promise<ApiResponse<PushHistory[]>> {
  return request.get('/api/push/history', { params: { limit } })
}

/**
 * Get push statistics
 */
export function getPushStats(): Promise<ApiResponse<PushStats>> {
  return request.get('/api/push/stats')
}
