import { defineStore } from 'pinia'
import { ref } from 'vue'
import { wellnessApi } from '@/api/wellness'
import type { WellnessMetricVO, WellnessSummaryVO, WellnessTrendVO } from '@/types/api'
import { ElMessage } from 'element-plus'

export const useWellnessStore = defineStore('wellness', () => {
  // State
  const summary = ref<WellnessSummaryVO | null>(null)
  const latestMetrics = ref<Record<string, WellnessMetricVO>>({})
  const trendData = ref<WellnessTrendVO | null>(null)
  const loading = ref(false)
  const chartLoading = ref(false)

  // Actions
  const fetchSummary = async (days = 7) => {
    loading.value = true
    try {
      const res = await wellnessApi.getSummary(days)
      summary.value = res.data
    } catch (error) {
      console.error('Failed to fetch wellness summary:', error)
      ElMessage.error('获取保健数据摘要失败')
    } finally {
      loading.value = false
    }
  }

  const fetchLatest = async () => {
    try {
      const res = await wellnessApi.getLatest()
      latestMetrics.value = res.data
    } catch (error) {
      console.error('Failed to fetch latest wellness metrics:', error)
    }
  }

  const fetchTrend = async (metricKey: string, startDate: string, endDate: string) => {
    chartLoading.value = true
    try {
      const res = await wellnessApi.getTrend(metricKey, startDate, endDate)
      trendData.value = res.data
    } catch (error) {
      console.error('Failed to fetch wellness trend:', error)
      ElMessage.error('获取趋势数据失败')
    } finally {
      chartLoading.value = false
    }
  }

  const addMetric = async (data: { metricKey: string; value: number; recordDate: string; unit?: string; notes?: string }) => {
    try {
      await wellnessApi.createMetric(data)
      ElMessage.success('添加成功')
      // Refresh data after adding
      await fetchSummary()
      await fetchLatest()
      return true
    } catch (error) {
      console.error('Failed to add wellness metric:', error)
      ElMessage.error('添加失败')
      return false
    }
  }

  return {
    // State
    summary,
    latestMetrics,
    trendData,
    loading,
    chartLoading,
    // Actions
    fetchSummary,
    fetchLatest,
    fetchTrend,
    addMetric
  }
})