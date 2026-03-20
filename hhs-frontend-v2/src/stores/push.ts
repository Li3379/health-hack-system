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
      console.error('Failed to load push configs:', error)
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
      const res = await updatePushConfig(channelType, data)
      if (res.code === 200) {
        // Update local state
        const index = configs.value.findIndex(c => c.channelType === channelType)
        if (index !== -1) {
          configs.value[index] = res.data
        }
        ElMessage.success('配置已保存')
        return true
      } else {
        ElMessage.error(res.message || '保存失败')
        return false
      }
    } catch (error) {
      console.error('Failed to save push config:', error)
      ElMessage.error('保存失败')
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
      if (res.code === 200 && res.data.success) {
        ElMessage.success(`${res.data.channel} 推送测试成功`)
        return true
      } else {
        ElMessage.error(res.message || '推送测试失败')
        return false
      }
    } catch (error) {
      console.error('Failed to test push channel:', error)
      ElMessage.error('推送测试失败')
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
      console.error('Failed to load push history:', error)
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
      console.error('Failed to load push stats:', error)
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
