<template>
  <Transition name="bubble-fade">
    <div
      v-if="visible && tip"
      class="floating-bubble"
      :style="bubbleStyle"
      role="alert"
      aria-live="polite"
      @mouseenter="pauseTimer"
      @mouseleave="resumeTimer"
      @click="handleClick"
    >
      <div class="bubble-accent"></div>
      <div class="bubble-content">
        <div class="bubble-header">
          <el-icon :size="14" class="bubble-icon"><ChatDotRound /></el-icon>
          <span class="bubble-label">AI 建议</span>
          <el-button
            class="bubble-close"
            text
            circle
            size="small"
            @click.stop="handleDismiss"
            aria-label="关闭"
          >
            <el-icon :size="12"><Close /></el-icon>
          </el-button>
        </div>
        <p class="bubble-title">{{ tip.title }}</p>
        <p class="bubble-text">{{ tip.content }}</p>
        <span class="bubble-action">点击查看 ></span>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { watch, computed, onUnmounted, type CSSProperties } from 'vue'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import type { HealthTip } from '@/stores/floatingAi'

const props = defineProps<{
  tip: HealthTip | null
  visible: boolean
  ballPosition: { x: number; y: number }
}>()

const emit = defineEmits<{
  dismiss: []
  view: [tip: HealthTip]
}>()

const bubbleStyle = computed<CSSProperties>(() => {
  // Position bubble above the ball (56px ball size)
  return {
    position: 'fixed',
    bottom: `${window.innerHeight - props.ballPosition.y + 12}px`,
    right: `${window.innerWidth - props.ballPosition.x - 56}px`
  }
})

const SHOW_DURATION = 8000
let timer: ReturnType<typeof setTimeout> | null = null

function startTimer() {
  clearTimer()
  timer = setTimeout(() => handleDismiss(), SHOW_DURATION)
}

function clearTimer() {
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
}

function pauseTimer() {
  clearTimer()
}

function resumeTimer() {
  startTimer()
}

function handleDismiss() {
  clearTimer()
  emit('dismiss')
}

function handleClick() {
  if (!props.tip) return
  clearTimer()
  emit('view', props.tip)
}

// Auto-start timer when bubble becomes visible
watch(
  () => props.visible,
  (val) => {
    if (val) {
      startTimer()
    } else {
      clearTimer()
    }
  },
  { immediate: true }
)

onUnmounted(() => {
  clearTimer()
  })
</script>

<style scoped>
.floating-bubble {
  width: 264px;
  border-radius: var(--radius-lg);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.15),
    0 1px 0 rgba(255, 255, 255, 0.06) inset;
  display: flex;
  overflow: hidden;
  cursor: pointer;
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  transition: box-shadow 0.25s ease, transform 0.25s var(--ease-spring);
}

.floating-bubble:hover {
  box-shadow:
    0 12px 40px rgba(0, 0, 0, 0.2),
    0 1px 0 rgba(255, 255, 255, 0.08) inset;
  transform: translateY(-2px);
}

[data-theme='dark'] .floating-bubble {
  box-shadow:
    0 12px 40px rgba(0, 0, 0, 0.4),
    0 1px 0 rgba(255, 255, 255, 0.04) inset;
}

.bubble-accent {
  width: 3px;
  flex-shrink: 0;
  background: linear-gradient(180deg, var(--accent-cool), var(--accent-warm));
}

.bubble-content {
  flex: 1;
  padding: 10px 12px;
  min-width: 0;
}

.bubble-header {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}

.bubble-icon {
  color: var(--accent-cool);
  flex-shrink: 0;
}

.bubble-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--accent-cool);
  flex: 1;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.bubble-close {
  flex-shrink: 0;
  color: var(--text-4);
  width: 20px;
  height: 20px;
  transition: color 0.15s, transform 0.2s var(--ease-spring);
}

.bubble-close:hover {
  color: var(--text-2);
  transform: scale(1.1);
}

.bubble-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  margin: 0 0 2px;
}

.bubble-text {
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bubble-action {
  font-size: 10px;
  color: var(--accent-cool);
  margin-top: 6px;
  display: block;
  font-weight: 500;
  transition: transform 0.2s var(--ease-spring);
}

.floating-bubble:hover .bubble-action {
  transform: translateX(2px);
}

/* Transitions */
.bubble-fade-enter-active {
  animation: bubbleIn 0.35s var(--ease-spring);
}

.bubble-fade-leave-active {
  animation: bubbleOut 0.2s ease-in;
}

@keyframes bubbleIn {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.92);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes bubbleOut {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  to {
    opacity: 0;
    transform: translateY(-4px) scale(0.92);
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .floating-bubble {
    animation: none !important;
    transition-duration: 0.01ms !important;
  }

  .floating-bubble:hover {
    transform: none;
  }
}
</style>
