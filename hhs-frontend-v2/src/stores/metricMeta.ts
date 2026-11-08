import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { FALLBACK_METRIC_META } from '@/constants/metricMeta'
import { toCanonicalKey } from '@/utils/metricNormalizer'
import { metricMetaApi } from '@/api/metric-meta'
import type { MetricDefinitionVO, MetricMetaMap } from '@/types/api'

export const useMetricMetaStore = defineStore('metricMeta', () => {
  // 初始状态使用本地静态常量进行深拷贝，确保 100% 离线可用与首屏零延迟
  const metadataMap = ref<MetricMetaMap>(JSON.parse(JSON.stringify(FALLBACK_METRIC_META)))
  const isLoaded = ref(false)
  const loading = ref(false)

  // 全量指标列表
  const allDefinitions = computed<MetricDefinitionVO[]>(() => Object.values(metadataMap.value))

  // 医学健康指标 (16 项)
  const healthMetrics = computed<MetricDefinitionVO[]>(() =>
    allDefinitions.value.filter(d => d.category === 'HEALTH')
  )

  // 保健生活方式指标 (7 项)
  const wellnessMetrics = computed<MetricDefinitionVO[]>(() =>
    allDefinitions.value.filter(d => d.category === 'WELLNESS')
  )

  // 可配置告警阈值的医学健康指标 (D-03/D-04：仅 alertable === true 进入向导第 1 步选择器，
  // 后端权威判定，前端不维护白名单；当前 fallback 数据下为 6 项)
  const alertableHealthMetrics = computed<MetricDefinitionVO[]>(() =>
    healthMetrics.value.filter(d => d.alertable)
  )

  // 可配置告警阈值的保健指标（当前 fallback 数据下为空数组，空组由调用方按 D-04 隐藏）
  const alertableWellnessMetrics = computed<MetricDefinitionVO[]>(() =>
    wellnessMetrics.value.filter(d => d.alertable)
  )

  /**
   * 根据标准 key 或任意别名获取指标定义
   * 优先查询当前响应式字典，未命中回退至本地静态常量
   */
  const getDefinition = (key: string): MetricDefinitionVO | undefined => {
    if (!key || typeof key !== 'string') {
      return undefined
    }

    const trimmed = key.trim()
    const canonicalKey = toCanonicalKey(trimmed)
    if (metadataMap.value[canonicalKey]) {
      return metadataMap.value[canonicalKey]
    }

    // 检查动态扩展指标的别名
    for (const def of Object.values(metadataMap.value)) {
      if (def.aliases && (def.aliases.includes(trimmed) || def.aliases.includes(canonicalKey))) {
        return def
      }
    }

    return FALLBACK_METRIC_META[canonicalKey]
  }

  /**
   * 异步拉取后端权威元数据并增量更新
   * 静默处理网络异常与离线超时，绝不阻断前端渲染 (per D-01)
   *
   * `silent` 只决定请求是否抑制展示（D-09），store 的降级逻辑与 reject 语义均不变（D-10）：
   * 失败仍由本函数的 catch 兜底到本地静态字典，不 resolve 假空值。
   */
  const fetchMetadata = async (options: { silent?: boolean } = {}): Promise<void> => {
    if (loading.value) {
      return
    }

    try {
      loading.value = true
      const res = await metricMetaApi.getMetadata(
        options.silent ? { silent: true } : undefined
      )
      if (res && res.data) {
        metadataMap.value = {
          ...metadataMap.value,
          ...res.data
        }
        isLoaded.value = true
      }
    } catch (error) {
      // 离线弱网静默降级至静态常量字典，无需抛出异常
      if (import.meta.env.DEV && !import.meta.env.VITEST) {
        console.warn('Metric metadata fetch failed, using fallback static metadata', error)
      }
    } finally {
      loading.value = false
    }
  }

  return {
    metadataMap,
    isLoaded,
    loading,
    allDefinitions,
    healthMetrics,
    wellnessMetrics,
    alertableHealthMetrics,
    alertableWellnessMetrics,
    getDefinition,
    fetchMetadata
  }
})
