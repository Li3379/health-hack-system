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
   * 最近一次保存失败的归一化错误，**连同它的通道**。
   *
   * 为什么需要它：`saveConfig` 自己调用 `showError` 呈现失败（D-02 的单一出口），
   * 只返回布尔值 —— 调用方因此拿不到 `fieldErrors`，无法做字段级投影。把归一化后的
   * `ApiError` 留在这里，视图就能在「呈现权仍归 store」的前提下补上内联投影，
   * 而不必第二次调用 `showError`（那会变成同一次失败的两条提示）。
   *
   * 带上 `channelType` 是因为本页每个通道各有一个表单，而 `PushConfigRequest` 的
   * 字段名恒为 `configValue`：只存 `ApiError` 会让 A 通道的失败标到 B 通道的输入框上。
   * 与 `loadError` 同形：一个 ref，由 store 写、视图只读。
   */
  const saveError = ref<{ channelType: string; error: ApiError } | null>(null)

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
    // 上一次保存的投影不进入本次尝试；只清记录，不碰任何输入（ERR-05）。
    saveError.value = null
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
      // `presentStates`：保存是用户主动发起的动作，而本 store 不渲染任何状态块，
      // 因此 `forbidden` / `notFound` 没有落点 —— 不 opt-in 会把「无权修改」或
      // 「配置已被删除」变成完全静默（14-05 的相位级规则：无块则每处必传）。
      const err = showError(error, 'push.saveFailed', { presentStates: true })
      // 保留归一化错误供视图做字段级投影（不新增第二条提示）。
      saveError.value = { channelType, error: err }
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
    saveError,
    loadConfigs,
    saveConfig,
    testChannel,
    loadHistory,
    loadStats,
    getConfig
  }
})
