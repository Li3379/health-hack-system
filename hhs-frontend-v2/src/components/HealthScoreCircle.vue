<template>
  <div class="health-score-circle" :style="{ width: size + 'px', height: size + 'px' }">
    <!-- SVG Circle -->
    <svg :width="size" :height="size" class="score-svg">
      <!-- Background Circle -->
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        :stroke="backgroundColor"
        :stroke-width="strokeWidth"
        class="bg-circle"
      />
      <!-- Progress Circle -->
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        :stroke="progressColor"
        :stroke-width="strokeWidth"
        :stroke-linecap="strokeLinecap"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        class="progress-circle"
        :class="{ animated: animated }"
      />
    </svg>

    <!-- Center Content -->
    <div class="center-content">
      <div class="score-value" :style="{ color: progressColor }">
        <span class="score-number">{{ displayScore }}</span>
        <span v-if="showUnit" class="score-unit">分</span>
      </div>
      <div v-if="level" class="score-level" :style="{ color: progressColor }">
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
  backgroundColor?: string
  strokeLinecap?: 'butt' | 'round' | 'square'
}

const props = withDefaults(defineProps<Props>(), {
  maxScore: 100,
  size: 180,
  strokeWidth: 12,
  animated: true,
  showUnit: true,
  backgroundColor: 'var(--color-bg-tertiary)',
  strokeLinecap: 'round'
})

const displayScore = ref(0)

// Computed
const radius = computed(() => (props.size - props.strokeWidth) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

const progressColor = computed(() => {
  if (props.color) return props.color

  const percentage = (props.score / props.maxScore) * 100
  if (percentage >= 90) return 'var(--color-health-excellent)'
  if (percentage >= 75) return 'var(--color-health-good)'
  if (percentage >= 60) return 'var(--color-health-fair)'
  return 'var(--color-health-poor)'
})

const dashOffset = computed(() => {
  const progress = displayScore.value / props.maxScore
  return circumference.value * (1 - progress)
})

// Animate score counting
const animateScore = () => {
  if (!props.animated) {
    displayScore.value = props.score
    return
  }

  const duration = 1000
  const startTime = Date.now()
  const startScore = displayScore.value
  const endScore = props.score

  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)

    // Easing function (ease-out-cubic)
    const easeProgress = 1 - Math.pow(1 - progress, 3)

    displayScore.value = Math.round(startScore + (endScore - startScore) * easeProgress)

    if (progress < 1) {
      requestAnimationFrame(animate)
    }
  }

  requestAnimationFrame(animate)
}

// Watch for score changes
watch(
  () => props.score,
  () => {
    animateScore()
  }
)

onMounted(() => {
  animateScore()
})
</script>

<style scoped>
.health-score-circle {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.score-svg {
  transform: rotate(-90deg);
}

.bg-circle {
  transition: stroke 0.3s ease;
}

.progress-circle {
  transition:
    stroke-dashoffset 1s ease-out,
    stroke 0.3s ease;
}

.progress-circle.animated {
  animation: circle-entrance 1s ease-out;
}

@keyframes circle-entrance {
  from {
    stroke-dashoffset: v-bind('circumference');
  }
}

.center-content {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.score-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.score-number {
  font-size: 48px;
  font-weight: var(--font-weight-bold);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.score-unit {
  font-size: 16px;
  font-weight: var(--font-weight-medium);
  opacity: 0.7;
}

.score-level {
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  margin-top: 4px;
}
</style>
