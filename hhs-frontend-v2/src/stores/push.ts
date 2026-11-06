import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PushConfig, PushHistory, PushStats } from '@/api/push'
import {
  getPushConfigs,
  updatePushConfig,
  testPushChannel,
  getPushHistory,
  getPushStats
} from '@/api/push'
import { ElMessage } from 'element-plus'
import { showError } from '@/utils/showError'
import { ApiError } from '@/utils/appError'

export const usePushStore = defineStore('push', () => {
  const configs = ref<PushConfig[]>([])
  const history = ref<PushHistory[]>([])
  const stats = ref<PushStats | null>(null)
  const loading = ref(false)
  const testing = ref<string | null>(null)

  /**
   * Load all push configurations
   */
  async function loadConfigs() {
    loading.value = true
    try {
      const res = await getPushConfigs()
      if (res.code === 200) {
        configs.value = res.data
      }
    } catch (error) {
      // config load failed
    } finally {
      loading.value = false
    }
  }

  /**
   * Update a push configuration
   */
  async function saveConfig(
    channelType: string,
    data: { configValue?: string; enabled?: boolean }
  ) {
    try {
      // 拦截器已保证 code !== 200 会 reject，成功路径只可能是 200。
      const res = await updatePushConfig(channelType, data)
      // Update local state
      const index = configs.value.findIndex(c => c.channelType === channelType)
      if (index !== -1) {
        configs.value[index] = res.data
      }
      ElMessage.success('配置已保存')
      return true
    } catch (error) {
      // store 层唯一出口：调用方只读布尔返回值，不重复弹提示（T-11-21）。
      showError(error, 'profile.saveFailed')
      return false
    }
  }

  /**
   * Test a push channel
   */
  async function testChannel(channelType: string): Promise<boolean> {
    testing.value = channelType
    try {
      const res = await testPushChannel(channelType)
      if (res.data.success) {
        ElMessage.success(`${res.data.channel} 推送测试成功`)
        return true
      }
      // 通道自身汇报失败（HTTP 200 + data.success=false）是可到达的分支，
      // 仍走 store 的唯一错误出口，保证提示来源单一；后端中文优先（D-05）。
      showError(new ApiError(res.message || '', 'business'), 'server.unavailable')
      return false
    } catch (error) {
      showError(error, 'server.unavailable')
      return false
    } finally {
      testing.value = null
    }
  }

  /**
   * Load push history
   */
  async function loadHistory(limit = 20) {
    try {
      const res = await getPushHistory(limit)
      if (res.code === 200) {
        history.value = res.data
      }
    } catch (error) {
      // history load failed
    }
  }

  /**
   * Load push statistics
   */
  async function loadStats() {
    try {
      const res = await getPushStats()
      if (res.code === 200) {
        stats.value = res.data
      }
    } catch (error) {
      // stats load failed
    }
  }

  /**
   * Get config by channel type
   */
  function getConfig(channelType: string): PushConfig | undefined {
    return configs.value.find(c => c.channelType === channelType)
  }

  return {
    configs,
    history,
    stats,
    loading,
    testing,
    loadConfigs,
    saveConfig,
    testChannel,
    loadHistory,
    loadStats,
    getConfig
  }
})
