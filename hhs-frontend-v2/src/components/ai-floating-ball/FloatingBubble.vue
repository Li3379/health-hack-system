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
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  box-shadow: var(--shadow-lg);
  display: flex;
  overflow: hidden;
  cursor: pointer;
  transition: var(--transition-shadow);
}

.floating-bubble:hover {
  box-shadow: var(--shadow-xl);
}

.bubble-accent {
  width: 3px;
  flex-shrink: 0;
  background: var(--color-primary);
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
  color: var(--color-primary);
  flex-shrink: 0;
}

.bubble-label {
  font-size: 10px;
  font-weight: var(--font-weight-medium);
  color: var(--color-primary);
  flex: 1;
}

.bubble-close {
  flex-shrink: 0;
  color: var(--color-text-tertiary);
  width: 20px;
  height: 20px;
}

.bubble-close:hover {
  color: var(--color-text-secondary);
}

.bubble-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 2px;
}

.bubble-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: var(--line-height-normal);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bubble-action {
  font-size: 10px;
  color: var(--color-primary);
  margin-top: 6px;
  display: block;
}

.floating-bubble:hover .bubble-action {
  text-decoration: underline;
}

/* Transitions */
.bubble-fade-enter-active {
  animation: bubbleIn 0.3s ease-out;
}

.bubble-fade-leave-active {
  animation: bubbleOut 0.2s ease-in;
}

@keyframes bubbleIn {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.95);
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
    transform: translateY(-4px) scale(0.95);
  }
}

/* Dark mode */
[data-theme='dark'] .floating-bubble {
  background: var(--color-surface-raised);
  border-color: var(--color-border);
}
</style>
