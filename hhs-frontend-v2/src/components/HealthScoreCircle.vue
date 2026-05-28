<template>
  <div class="health-score-circle" :style="{ width: size + 'px', height: size + 'px' }">
    <!-- Outer Glow Layer -->
    <div class="outer-glow" :class="'glow-' + colorClass"></div>
    
    <!-- SVG Circle -->
    <svg :width="size" :height="size" class="score-svg" viewBox="0 0 180 180">
      <!-- Background Circle -->
      <circle
        cx="90"
        cy="90"
        :r="radius"
        fill="none"
        stroke="rgba(255,255,255,0.1)"
        :stroke-width="strokeWidth"
        class="bg-circle"
      />
      <!-- Progress Circle -->
      <circle
        cx="90"
        cy="90"
        :r="radius"
        fill="none"
        :stroke="progressColor"
        :stroke-width="strokeWidth"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        class="progress-circle"
        :class="{ animated: animated }"
      />
    </svg>

    <!-- Center Content -->
    <div class="center-content">
      <div class="score-value">
        <span class="score-number">{{ displayScore }}</span>
        <span v-if="showUnit" class="score-unit">分</span>
      </div>
      <div v-if="level" class="score-level">
        {{ level }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'

interface Props {
  score: number
  maxScore?: number
  size?: number
  strokeWidth?: number
  animated?: boolean
  showUnit?: boolean
  level?: string
  color?: string
  strokeLinecap?: 'butt' | 'round' | 'square'
}

const props = withDefaults(defineProps<Props>(), {
  maxScore: 100,
  size: 140,
  strokeWidth: 8,
  animated: true,
  showUnit: true,
  strokeLinecap: 'round'
})

const displayScore = ref(0)

const radius = computed(() => (props.size - props.strokeWidth) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

const progressColor = computed(() => {
  if (props.color) return props.color
  const percentage = (props.score / props.maxScore) * 100
  if (percentage >= 90) return '#22c55e'
  if (percentage >= 75) return '#84cc16'
  if (percentage >= 60) return '#eab308'
  return '#ef4444'
})

const colorClass = computed(() => {
  const percentage = (props.score / props.maxScore) * 100
  if (percentage >= 90) return 'excellent'
  if (percentage >= 75) return 'good'
  if (percentage >= 60) return 'fair'
  return 'poor'
})

const dashOffset = computed(() => {
  const progress = displayScore.value / props.maxScore
  return circumference.value * (1 - progress)
})

const animateScore = () => {
  if (!props.animated) {
    displayScore.value = props.score
    return
  }
  const duration = 1200
  const startTime = Date.now()
  const startScore = displayScore.value
  const endScore = props.score

  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)
    const easeProgress = 1 - Math.pow(1 - progress, 3)
    displayScore.value = Math.round(startScore + (endScore - startScore) * easeProgress)
    if (progress < 1) requestAnimationFrame(animate)
  }
  requestAnimationFrame(animate)
}

watch(() => props.score, () => { animateScore() })
onMounted(() => { animateScore() })
</script>

<style scoped>
.health-score-circle {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.outer-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 95%;
  height: 95%;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  animation: glow-pulse 2.5s ease-in-out infinite;
  pointer-events: none;
}

.glow-excellent {
  background: radial-gradient(circle, rgba(34,197,94,0.25) 0%, rgba(34,197,94,0.10) 35%, rgba(34,197,94,0.03) 60%, transparent 80%);
}

.glow-good {
  background: radial-gradient(circle, rgba(132,204,22,0.25) 0%, rgba(132,204,22,0.10) 35%, rgba(132,204,22,0.03) 60%, transparent 80%);
}

.glow-fair {
  background: radial-gradient(circle, rgba(234,179,8,0.30) 0%, rgba(234,179,8,0.12) 35%, rgba(234,179,8,0.04) 60%, transparent 80%);
}

.glow-poor {
  background: radial-gradient(circle, rgba(239,68,68,0.25) 0%, rgba(239,68,68,0.10) 35%, rgba(239,68,68,0.03) 60%, transparent 80%);
}

@keyframes glow-pulse {
  0%   { opacity: 0.5; transform: translate(-50%, -50%) scale(0.96); }
  50%  { opacity: 0.9; transform: translate(-50%, -50%) scale(1.02); }
  100% { opacity: 0.5; transform: translate(-50%, -50%) scale(0.96); }
}

.score-svg {
  position: relative;
  z-index: 1;
  transform: rotate(-90deg);
}

.bg-circle {
  stroke: var(--score-circle-track, rgba(255, 255, 255, 0.1));
}

.progress-circle {
  transition:
    stroke-dashoffset 1.5s cubic-bezier(0.22, 1, 0.36, 1),
    stroke 0.5s ease;
  filter: drop-shadow(0 0 6px currentColor);
}

.progress-circle.animated {
  animation: circle-entrance 1.6s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes circle-entrance {
  from { stroke-dashoffset: v-bind('circumference'); }
}

.center-content {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.score-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  color: var(--text-1);
}

.score-number {
  font-size: 38px;
  font-weight: 800;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.score-unit {
  font-size: 13px;
  font-weight: 500;
  opacity: 0.5;
}

.score-level {
  font-size: 13px;
  font-weight: 500;
  margin-top: 2px;
  opacity: 0.7;
}
</style>