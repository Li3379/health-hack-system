import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { metricMetaApi } from '@/api/metric-meta'
import type { ApiResponse, MetricMetaMap } from '@/types/api'

vi.mock('@/api/metric-meta', () => ({
  metricMetaApi: {
    getMetadata: vi.fn()
  }
}))

describe('useMetricMetaStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('should initialize immediately with 23 fallback metric definitions', () => {
    const store = useMetricMetaStore()

    expect(store.allDefinitions.length).toBe(23)
    expect(store.healthMetrics.length).toBe(16)
    expect(store.wellnessMetrics.length).toBe(7)
    expect(store.isLoaded).toBe(false)
  })

  it('exposes exactly the six D-19 alertable health metrics and no alertable wellness metrics', () => {
    const store = useMetricMetaStore()

    expect(store.alertableHealthMetrics.length).toBe(6)
    // 集合相等断言（而非仅计数）——T-12-17：防止 fallback 与后端六项集合漂移
    expect([...store.alertableHealthMetrics.map(d => d.canonicalKey)].sort()).toEqual(
      ['bloodGlucose', 'bodyTemperature', 'bmi', 'diastolicBP', 'heartRate', 'systolicBP'].sort()
    )
    expect(store.alertableWellnessMetrics.length).toBe(0)
  })

  it('resolves aliases onto the alertable flag and mirrors the alert_rule seed recommendations', () => {
    const store = useMetricMetaStore()

    // 别名解析仍落在 bloodGlucose 上，且带 alertable 标志
    expect(store.getDefinition('glucose')?.alertable).toBe(true)
    expect(store.getDefinition('glucose')?.canonicalKey).toBe('bloodGlucose')

    // 推荐值镜像 alert_rule 种子（heartRate 100/120/50/40）
    const heartRate = store.getDefinition('heartRate')
    expect(heartRate?.recommended).toEqual({
      warningLow: 50,
      warningHigh: 100,
      criticalLow: 40,
      criticalHigh: 120
    })

    // 非告警指标（weight 四列全 NULL、bloodOxygen 无种子规则）保持 false（D-19/D-20）
    expect(store.getDefinition('weight')?.alertable).toBe(false)
    expect(store.getDefinition('spo2')?.alertable).toBe(false)
    // 非告警指标的 recommended 为空对象（无推荐四值）
    expect(store.getDefinition('weight')?.recommended).toEqual({})
  })

  it('should correctly lookup definitions using canonical keys and aliases', () => {
    const store = useMetricMetaStore()

    const heartRateByCanonical = store.getDefinition('heartRate')
    expect(heartRateByCanonical).toBeDefined()
    expect(heartRateByCanonical?.displayName).toBe('心率')
    expect(heartRateByCanonical?.unit).toBe('次/分')

    const heartRateByAlias = store.getDefinition('heart_rate')
    expect(heartRateByAlias).toBeDefined()
    expect(heartRateByAlias?.canonicalKey).toBe('heartRate')

    const sbp = store.getDefinition('sbp')
    expect(sbp).toBeDefined()
    expect(sbp?.canonicalKey).toBe('systolicBP')
    expect(sbp?.displayName).toBe('收缩压')
    expect(sbp?.unit).toBe('mmHg')

    const dbp = store.getDefinition('diastolic_bp')
    expect(dbp).toBeDefined()
    expect(dbp?.canonicalKey).toBe('diastolicBP')
    expect(dbp?.displayName).toBe('舒张压')

    const temp = store.getDefinition('temp')
    expect(temp).toBeDefined()
    expect(temp?.canonicalKey).toBe('bodyTemperature')
    expect(temp?.displayName).toBe('体温')

    const spo2 = store.getDefinition('spo2')
    expect(spo2).toBeDefined()
    expect(spo2?.canonicalKey).toBe('bloodOxygen')
    expect(spo2?.displayName).toBe('血氧')
  })

  it('should return undefined for unknown metric keys', () => {
    const store = useMetricMetaStore()

    expect(store.getDefinition('non_existent_key')).toBeUndefined()
    expect(store.getDefinition('')).toBeUndefined()
  })

  it('should update metadataMap and set isLoaded to true on successful fetchMetadata', async () => {
    const mockData: MetricMetaMap = {
      heartRate: {
        canonicalKey: 'heartRate',
        aliases: ['heart_rate'],
        displayName: '静息心率 (更新)',
        category: 'HEALTH',
        unit: 'bpm',
        precision: 0,
        step: 1,
        hardMin: 20,
        hardMax: 300,
        normalMin: 60,
        normalMax: 100,
        placeholder: '例如: 72',
        guideHint: '已更新的心率说明',
        alertable: true,
        recommended: { warningLow: 50, warningHigh: 100, criticalLow: 40, criticalHigh: 120 }
      }
    }

    const mockResponse: ApiResponse<MetricMetaMap> = {
      code: 200,
      message: 'success',
      data: mockData
    }

    vi.mocked(metricMetaApi.getMetadata).mockResolvedValue(mockResponse)

    const store = useMetricMetaStore()
    await store.fetchMetadata()

    expect(metricMetaApi.getMetadata).toHaveBeenCalledTimes(1)
    expect(store.isLoaded).toBe(true)
    expect(store.getDefinition('heartRate')?.displayName).toBe('静息心率 (更新)')
    expect(store.getDefinition('heartRate')?.unit).toBe('bpm')
    // Other metrics should remain intact from fallback
    expect(store.getDefinition('systolicBP')?.displayName).toBe('收缩压')
  })

  it('should gracefully handle network error and remain on fallback metadata', async () => {
    vi.mocked(metricMetaApi.getMetadata).mockRejectedValue(new Error('Network timeout'))

    const store = useMetricMetaStore()
    // Should not throw
    await store.fetchMetadata()

    expect(store.isLoaded).toBe(false)
    expect(store.allDefinitions.length).toBe(23)
    expect(store.getDefinition('heartRate')?.displayName).toBe('心率')
  })

  it('should pass { silent: true } to the API when fetchMetadata opts in', async () => {
    vi.mocked(metricMetaApi.getMetadata).mockRejectedValue(new Error('Network timeout'))

    const store = useMetricMetaStore()
    await store.fetchMetadata({ silent: true })

    expect(metricMetaApi.getMetadata).toHaveBeenCalledTimes(1)
    expect(metricMetaApi.getMetadata).toHaveBeenCalledWith({ silent: true })
    // silent 只抑制展示：store 降级逻辑不变，仍回落到本地静态字典。
    expect(store.isLoaded).toBe(false)
    expect(store.getDefinition('heartRate')?.displayName).toBe('心率')
  })

  it('should pass undefined to the API when fetchMetadata is called without options', async () => {
    vi.mocked(metricMetaApi.getMetadata).mockResolvedValue({
      code: 200,
      message: 'success',
      data: {}
    })

    const store = useMetricMetaStore()
    await store.fetchMetadata()

    expect(metricMetaApi.getMetadata).toHaveBeenCalledTimes(1)
    expect(metricMetaApi.getMetadata).toHaveBeenCalledWith(undefined)
  })
})
