<template>
  <div ref="chartRef" class="sparkline-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { resolveCssColor, toRgba } from '@/utils/cssColor'

echarts.use([LineChart, GridComponent, CanvasRenderer])

const props = defineProps<{
  data: number[]
  color?: string
  height?: number
}>()

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null
const handleResize = () => chartInstance?.resize()

/**
 * canvas 只认真实颜色值 —— `var(--accent-cool)40` 会让 `addColorStop` 抛
 * SyntaxError 并导致整张图不渲染。这里把 CSS 变量解析成当前主题的实际颜色，
 * 再自己拼 alpha；变量读不到时 resolveCssColor 兜底，不会抛异常。
 */
const buildSeriesColors = () => {
  const base = resolveCssColor(props.color || 'var(--accent-cool)')
  return {
    line: base,
    areaFrom: toRgba(base, 0.25),
    areaTo: toRgba(base, 0.02),
  }
}

const initChart = () => {
  if (!chartRef.value || !props.data.length) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  const colors = buildSeriesColors()

  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption({
    grid: { left: 0, right: 0, top: 0, bottom: 0 },
    xAxis: { type: 'category', show: false, data: props.data.map((_, i) => i) },
    yAxis: { type: 'value', show: false },
    series: [{
      data: props.data,
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: {
        width: 2,
        color: colors.line,
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: colors.areaFrom },
          { offset: 1, color: colors.areaTo },
        ]),
      },
    }],
  })
}

watch(() => props.data, () => {
  if (!props.data.length) return
  if (!chartInstance) {
    initChart()
    return
  }
  chartInstance.setOption({
    xAxis: { data: props.data.map((_, i) => i) },
    series: [{ data: props.data }],
  })
}, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.sparkline-container {
  width: 100%;
  height: v-bind('(props.height || 32) + "px"');
}
</style>
