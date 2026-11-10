import { defineStore } from 'pinia'
import { ref } from 'vue'
import { wellnessApi } from '@/api/wellness'
import { normalizeApiError } from '@/utils/appError'
import type { WellnessMetricVO, WellnessSummaryVO, WellnessTrendVO } from '@/types/api'

// D-02：store 不是 UI 层。两个加载器都**上抛**，让调用点能区分「加载失败」与
// 「确实没有数据」—— `fetchSummary` 旧代码在失败后保留上一次的数据，仪表盘读起来
// 像一个真实的零值；`fetchTrend` 同形。呈现权归 `views/wellness/Dashboard.vue`
// 的两个 `ErrorState`（摘要块 + 图表区块），该文件因此零 `showError`。
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
      // 上抛而非静默：静默会让一次失败的加载继续显示旧数据，用户读到的是
      // 一个不存在的「当前值」。Dashboard.vue 接住并置 summaryFailed。
      throw normalizeApiError(error)
    } finally {
      loading.value = false
    }
  }

  const fetchLatest = async () => {
    try {
      const res = await wellnessApi.getLatest()
      latestMetrics.value = res.data
    } catch (error) {
      // latest metrics fetch failed
    }
  }

  const fetchTrend = async (metricKey: string, startDate: string, endDate: string) => {
    chartLoading.value = true
    try {
      const res = await wellnessApi.getTrend(metricKey, startDate, endDate)
      trendData.value = res.data
    } catch (error) {
      // 上抛：Dashboard.vue 把 trendFailed 置真并在图表容器内渲染状态块，
      // 已渲染的摘要因此保持可见（局部降级而非整页失效）。
      throw normalizeApiError(error)
    } finally {
      chartLoading.value = false
    }
  }

  // `addMetric` 已删除（14-12 Task 2）：全树搜索证明它没有任何调用方。
  // 证据：`grep -rn "addMetric" src/` 只命中 `stores/wellness.ts` 自身、
  // `api/realtime.ts` 的同名不同物，以及 `MonitorInlineError.spec.ts` 里
  // 对 `realtimeApi.addMetric` 的 mock —— 没有一处调用本 store 的这个 action。
  // 一个带 toast 的死 action 既是残留也是负债：它让「store 内不得弹错误 toast」
  // 的检查永远需要一条例外。删除是保行为的（无人调用），也消除了那条例外。

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
    fetchTrend
  }
})
