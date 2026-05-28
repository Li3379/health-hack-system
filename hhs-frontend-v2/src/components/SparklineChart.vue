<template>
  <div ref="chartRef" class="sparkline-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, CanvasRenderer])

const props = defineProps<{
  data: number[]
  color?: string
  height?: number
}>()

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null
const handleResize = () => chartInstance?.resize()

const initChart = () => {
  if (!chartRef.value || !props.data.length) return

  if (chartInstance) {
    chartInstance.dispose()
  }

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
        color: props.color || 'var(--accent-cool)',
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: (props.color || 'var(--accent-cool)') + '40' },
          { offset: 1, color: (props.color || 'var(--accent-cool)') + '05' },
        ]),
      },
    }],
  })
}

watch(() => props.data, () => {
  if (chartInstance && props.data.length) {
    chartInstance.setOption({
      xAxis: { data: props.data.map((_, i) => i) },
      series: [{ data: props.data }],
    })
  }
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
