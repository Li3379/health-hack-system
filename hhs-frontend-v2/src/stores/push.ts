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
import { ERROR_MESSAGES } from '@/utils/errorMessages'

export const usePushStore = defineStore('push', () => {
  const configs = ref<PushConfig[]>([])
  const history = ref<PushHistory[]>([])
  const stats = ref<PushStats | null>(null)
  const loading = ref(false)
  const testing = ref<string | null>(null)
  /** 配置加载失败的原因；视图据此渲染错误态而不是空列表（WR-09）。 */
  const loadError = ref<string | null>(null)

  /**
   * Load all push configurations.
   *
   * 页面级加载失败走**错误状态块**，不走 toast（D-02：两者分工固定，不混用）。
   * 因此这里始终 `silent: true` —— 只分类、只记录 `loadError`，由视图渲染
   * ErrorState 承载呈现与重试入口。曾经按 `options.silent === true` 放行 toast，
   * 结果前台调用（PushSettings.vue）同时弹出常驻 toast + 错误块，同一失败两次呈现、
   * 两个重试入口（UAT 后验证发现的 D-02 回归）。
   */
  async function loadConfigs() {
    loading.value = true
    loadError.value = null
    try {
      const res = await getPushConfigs()
      if (res.code === 200) {
        configs.value = res.data
      }
    } catch (error) {
      const err = showError(error, 'server.unavailable', { silent: true })
      loadError.value = err.message || ERROR_MESSAGES['server.unavailable']
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
      // 成功反馈由调用方拥有（开关/保存各有上下文文案），此处不再额外弹一条，
      // 否则一次切换会出现两条堆叠的成功提示（WR-07）。
      return true
    } catch (error) {
      // store 层唯一出口：调用方只读布尔返回值，不重复弹提示（T-11-21）。
      showError(error, 'push.saveFailed')
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
  async function loadHistory(limit = 20, options: { silent?: boolean } = {}) {
    try {
      const res = await getPushHistory(limit)
      if (res.code === 200) {
        history.value = res.data
      }
    } catch (error) {
      // 历史是次要区块：默认静默，但把失败交回调用者可决定是否提示（WR-09）。
      showError(error, 'server.unavailable', { silent: options.silent !== false })
    }
  }

  /**
   * Load push statistics
   */
  async function loadStats(options: { silent?: boolean } = {}) {
    try {
      const res = await getPushStats()
      if (res.code === 200) {
        stats.value = res.data
      }
    } catch (error) {
      showError(error, 'server.unavailable', { silent: options.silent !== false })
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
    loadError,
    loadConfigs,
    saveConfig,
    testChannel,
    loadHistory,
    loadStats,
    getConfig
  }
})
