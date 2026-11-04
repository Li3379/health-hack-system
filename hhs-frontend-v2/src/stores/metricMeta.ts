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
   */
  const fetchMetadata = async (): Promise<void> => {
    if (loading.value) {
      return
    }

    try {
      loading.value = true
      const res = await metricMetaApi.getMetadata()
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
    getDefinition,
    fetchMetadata
  }
})
