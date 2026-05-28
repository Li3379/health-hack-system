<template>
  <div ref="chartRef" class="gauge-chart"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import * as echarts from 'echarts/core'
import { GaugeChart as EGaugeChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useECharts } from '@/composables/useECharts'

echarts.use([EGaugeChart, TooltipComponent, CanvasRenderer])

const props = withDefaults(defineProps<{
  value: number | null | undefined
  title?: string
}>(), {
  title: '心率',
})

const chartRef = ref<HTMLElement>()

/**
 * Build the gauge ECharts option.
 * Color zones are defined in absolute BPM values and converted to percentages
 * of the gauge range (0–200).
 */
const buildOption = (): echarts.EChartsCoreOption => {
  const val = props.value ?? 0
  const isNoData = val === 0 || props.value === null || props.value === undefined

  // Color-stop boundaries as fractions of 0–200 range
  const low = 60 / 200     // 0.30
  const normal = 100 / 200  // 0.50
  const elevated = 120 / 200 // 0.60

  return {
    series: [
      {
        type: 'gauge',
        min: 0,
        max: 200,
        startAngle: 225,
        endAngle: -45,
        splitNumber: 10,
        axisLine: {
          lineStyle: {
            width: 20,
            color: [
              [low, '#409EFF'],          // low (<60): blue
              [normal, '#67C23A'],       // normal (60–100): green
              [elevated, '#E6A23C'],     // elevated (100–120): yellow
              [1, '#F56C6C'],            // high (>120): red
            ],
          },
        },
        pointer: {
          icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
          length: '60%',
          width: 8,
          offsetCenter: [0, '-10%'],
          itemStyle: {
            color: 'auto',
          },
        },
        axisTick: {
          distance: -20,
          length: 6,
          lineStyle: {
            color: '#fff',
            width: 1,
          },
        },
        splitLine: {
          distance: -20,
          length: 14,
          lineStyle: {
            color: '#fff',
            width: 2,
          },
        },
        axisLabel: {
          color: 'inherit',
          distance: 30,
          fontSize: 11,
        },
        detail: {
          valueAnimation: true,
          formatter: isNoData ? '无数据' : '{value}',
          color: 'inherit',
          fontSize: 32,
          fontWeight: 'bold',
          offsetCenter: [0, '70%'],
        },
        title: {
          offsetCenter: [0, '92%'],
          fontSize: 14,
          color: 'inherit',
        },
        data: [
          {
            value: isNoData ? 0 : val,
            name: props.title,
          },
        ],
      },
    ],
  }
}

const { init: initChart, updateOption } = useECharts(chartRef, buildOption)

watch(
  () => props.value,
  () => updateOption(),
)

onMounted(() => {
  initChart()
})
</script>

<style scoped>
.gauge-chart {
  width: 100%;
  height: 320px;
}
</style>
