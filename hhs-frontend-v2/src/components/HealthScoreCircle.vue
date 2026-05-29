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
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { gsap, DUR, EASE } from '@/composables/useGsap'

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
let scoreTween: gsap.core.Tween | null = null

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
  scoreTween?.kill()
  if (!props.animated) {
    displayScore.value = props.score
    return
  }
  const obj = { val: displayScore.value }
  scoreTween = gsap.to(obj, {
    val: props.score,
    duration: DUR.dramatic,
    ease: EASE.power4,
    onUpdate() {
      displayScore.value = Math.round(obj.val)
    },
  })
}

watch(() => props.score, () => { animateScore() })
onMounted(() => { animateScore() })
onUnmounted(() => { scoreTween?.kill() })
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
  /* GSAP handles animation; keep stroke transition for color changes */
  transition: stroke 0.5s ease;
  filter: drop-shadow(0 0 6px currentColor);
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