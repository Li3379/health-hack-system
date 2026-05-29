<template>
  <div class="activity-rings-wrapper">
    <svg
      :width="size"
      :height="size"
      :viewBox="`0 0 ${viewBox} ${viewBox}`"
      class="activity-rings-svg"
    >
      <!-- Background tracks -->
      <circle
        v-for="ring in rings"
        :key="'track-' + ring.id"
        :cx="center"
        :cy="center"
        :r="ring.radius"
        fill="none"
        :stroke="ring.trackColor"
        :stroke-width="strokeWidth"
      />
      <!-- Progress arcs -->
      <circle
        v-for="(ring, index) in rings"
        :key="'progress-' + ring.id"
        :ref="(el) => { if (el) ringRefs[index] = el as SVGCircleElement }"
        :cx="center"
        :cy="center"
        :r="ring.radius"
        fill="none"
        :stroke="ring.color"
        :stroke-width="strokeWidth"
        stroke-linecap="round"
        :stroke-dasharray="ring.circumference"
        :stroke-dashoffset="ring.offset"
        class="ring-progress"
        :transform="`rotate(-90 ${center} ${center})`"
      />
      <!-- Center text -->
      <text
        :x="center"
        :y="center - 6"
        text-anchor="middle"
        dominant-baseline="central"
        class="ring-percent-text"
      >
        {{ averagePercent }}%
      </text>
      <text
        :x="center"
        :y="center + 16"
        text-anchor="middle"
        dominant-baseline="central"
        class="ring-label-text"
      >
        综合完成度
      </text>
    </svg>
    <!-- Legend -->
    <div class="ring-legend">
      <div v-for="ring in rings" :key="'legend-' + ring.id" class="legend-item">
        <span class="legend-dot" :style="{ background: ring.color }"></span>
        <span class="legend-name">{{ ring.label }}</span>
        <span class="legend-value">{{ clamp(ring.percent) }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { gsap, DUR, EASE } from '@/composables/useGsap'

const props = withDefaults(defineProps<{
  exercisePercent: number
  sleepPercent: number
  habitsPercent: number
  size?: number
}>(), {
  size: 200,
})

const viewBox = 200
const center = viewBox / 2
const strokeWidth = 12

const EXERCISE_RADIUS = 88
const SLEEP_RADIUS = 68
const HABITS_RADIUS = 48

const EXERCISE_COLOR = '#FF2D55'
const SLEEP_COLOR = '#5AC8FA'
const HABITS_COLOR = '#4CD964'

const clamp = (v: number): number => Math.max(0, Math.min(100, Math.round(v)))

const mounted = ref(false)
const ringRefs = ref<SVGCircleElement[]>([])
let ringTweens: gsap.core.Tween[] = []

const rings = computed(() => {
  const items = [
    { id: 'exercise', radius: EXERCISE_RADIUS, color: EXERCISE_COLOR, trackColor: 'rgba(255, 45, 85, 0.15)', percent: props.exercisePercent, label: '运动' },
    { id: 'sleep', radius: SLEEP_RADIUS, color: SLEEP_COLOR, trackColor: 'rgba(90, 200, 250, 0.15)', percent: props.sleepPercent, label: '睡眠' },
    { id: 'habits', radius: HABITS_RADIUS, color: HABITS_COLOR, trackColor: 'rgba(76, 217, 100, 0.15)', percent: props.habitsPercent, label: '习惯' },
  ]

  return items.map((item) => {
    const circumference = 2 * Math.PI * item.radius
    const pct = clamp(item.percent)
    const offset = mounted.value
      ? circumference * (1 - pct / 100)
      : circumference
    return { ...item, circumference, offset }
  })
})

const averagePercent = computed(() => {
  const vals = [props.exercisePercent, props.sleepPercent, props.habitsPercent]
  const avg = vals.reduce((sum, v) => sum + clamp(v), 0) / vals.length
  return Math.round(avg)
})

const animateRings = () => {
  ringTweens.forEach(t => t.kill())
  ringTweens = []

  if (!mounted.value || !ringRefs.value.length) return

  ringRefs.value.forEach((circle, i) => {
    const ring = rings.value[i]
    if (!ring) return
    const targetOffset = ring.offset
    const tween = gsap.fromTo(circle,
      { attr: { 'stroke-dashoffset': ring.circumference } },
      {
        attr: { 'stroke-dashoffset': targetOffset },
        duration: DUR.dramatic,
        ease: EASE.power4,
        delay: i * 0.15,
      }
    )
    ringTweens.push(tween)
  })
}

onMounted(() => {
  requestAnimationFrame(() => {
    mounted.value = true
    animateRings()
  })
})

watch(() => [props.exercisePercent, props.sleepPercent, props.habitsPercent], () => {
  if (mounted.value) animateRings()
})

onUnmounted(() => {
  ringTweens.forEach(t => t.kill())
})
</script>

<style scoped>
.activity-rings-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.activity-rings-svg {
  display: block;
}

.ring-progress {
  /* GSAP handles animation; no CSS transition needed */
}

.ring-percent-text {
  font-size: 28px;
  font-weight: 700;
  fill: var(--text-1, #ebecef);
  font-family: var(--font-ui, system-ui, sans-serif);
}

.ring-label-text {
  font-size: 12px;
  fill: var(--text-3, #8d909c);
  font-family: var(--font-ui, system-ui, sans-serif);
}

.ring-legend {
  display: flex;
  gap: 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-name {
  font-size: 13px;
  color: var(--text-2, #c6c9d2);
}

.legend-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1, #ebecef);
  font-variant-numeric: tabular-nums;
}
</style>
