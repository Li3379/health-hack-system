<template>
  <div
    ref="ballRef"
    class="floating-ball"
    :class="{
      'is-dragging': isDragging,
      'has-unread': hasUnread,
      'is-active': isActive,
      'is-3d': isWebGLReady
    }"
    :style="ballStyle"
    @pointerdown="onPointerDown"
    @click="handleClick"
    @keydown.enter="handleClick"
    @keydown.space.prevent="handleClick"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
    role="button"
    tabindex="0"
    aria-label="AI 健康助手"
  >
    <!-- WebGL Canvas for 3D rendering -->
    <canvas ref="canvasRef" class="ball-canvas" />

    <!-- Fallback inner for mobile/no WebGL -->
    <div class="ball-inner" :class="{ 'is-hidden': isWebGLReady }">
      <el-icon :size="24" class="ball-icon">
        <ChatDotRound />
      </el-icon>
    </div>

    <!-- Unread badge -->
    <transition name="badge-pop">
      <span v-if="hasUnread" class="unread-badge">
        {{ displayCount }}
      </span>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, type CSSProperties } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import { useDraggable } from '@/composables/useDraggable'
import { useWebGLBall } from '@/composables/useWebGLBall'

const props = defineProps<{
  unreadCount: number
  isActive: boolean
}>()

const emit = defineEmits<{
  click: []
  'position-change': [pos: { x: number; y: number }]
}>()

const ballRef = ref<HTMLElement>()
const canvasRef = ref<HTMLCanvasElement>()

const { position, isDragging, hasMoved, onPointerDown } = useDraggable(ballRef, {
  storageKey: 'hhs-floating-ball-position',
  initialValue: { x: window.innerWidth - 80, y: window.innerHeight - 140 },
  boundaryPadding: 12,
  dragThreshold: 5
})

const { isReady: isWebGLReady, updateHover, clearHover, setActive, setDragging } = useWebGLBall(canvasRef)

// Emit position changes to parent
watch(position, (pos) => {
  emit('position-change', { ...pos })
}, { immediate: true, deep: true })

// Sync active state to WebGL
watch(() => props.isActive, (active) => {
  setActive(active)
}, { immediate: true })

// Sync dragging state to WebGL
watch(isDragging, (dragging) => {
  setDragging(dragging)
})

const hasUnread = computed(() => props.unreadCount > 0)

const displayCount = computed(() =>
  props.unreadCount > 99 ? '99+' : props.unreadCount
)

const ballStyle = computed<CSSProperties>(() => ({
  transform: `translate(${position.value.x}px, ${position.value.y}px)`
}))

function handleClick() {
  // Only fire click if user didn't drag
  if (!hasMoved.value) {
    emit('click')
  }
}

function onMouseMove(e: MouseEvent) {
  if (!ballRef.value) return
  const rect = ballRef.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const mouseX = (e.clientX - centerX) / (rect.width / 2)
  const mouseY = (e.clientY - centerY) / (rect.height / 2)
  updateHover(mouseX, mouseY)
}

function onMouseLeave() {
  clearHover()
}
</script>

<style scoped>
.floating-ball {
  position: fixed;
  top: 0;
  left: 0;
  z-index: var(--z-fixed);
  width: 56px;
  height: 56px;
  border-radius: var(--radius-full);
  cursor: grab;
  user-select: none;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
  outline: none;
  transition: transform 0.1s ease;
}

.floating-ball:focus-visible {
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.5);
}

.floating-ball.is-dragging {
  cursor: grabbing;
  transition: none;
}

.floating-ball.is-dragging .ball-inner:not(.is-hidden) {
  transform: scale(1.08);
  box-shadow: 0 8px 32px rgba(37, 99, 235, 0.45);
}

/* WebGL Canvas */
.ball-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: var(--radius-full);
  pointer-events: none;
}

/* Fallback inner for mobile/no WebGL */
.ball-inner {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, #4f8eff, #2563eb);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.ball-inner.is-hidden {
  opacity: 0;
  pointer-events: none;
}

.floating-ball:not(.is-dragging):not(.is-3d) .ball-inner:not(.is-hidden) {
  animation: breathe 3s ease-in-out infinite;
}

.floating-ball.is-active .ball-inner:not(.is-hidden) {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 4px 20px rgba(37, 99, 235, 0.5);
}

.floating-ball:hover .ball-inner:not(.is-hidden) {
  transform: scale(1.05);
  box-shadow: 0 6px 24px rgba(37, 99, 235, 0.4);
}

.ball-icon {
  color: #ffffff;
}

@keyframes breathe {
  0%, 100% {
    box-shadow: 0 4px 16px rgba(37, 99, 235, 0.35);
  }
  50% {
    box-shadow: 0 4px 24px rgba(37, 99, 235, 0.55);
  }
}

/* Unread badge */
.unread-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: var(--radius-full);
  background: var(--color-danger);
  color: #ffffff;
  font-size: 11px;
  font-weight: var(--font-weight-semibold);
  line-height: 20px;
  text-align: center;
  border: 2px solid var(--color-bg-primary);
  pointer-events: none;
  z-index: 1;
}

.badge-pop-enter-active {
  animation: badgePop 0.3s ease-out;
}

.badge-pop-leave-active {
  animation: badgePop 0.2s ease-in reverse;
}

@keyframes badgePop {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

/* Mobile: no drag, fixed position */
@media (max-width: 768px) {
  .floating-ball {
    position: fixed;
    right: 20px;
    bottom: 24px;
    left: auto;
    top: auto;
    transform: none !important;
  }

  .floating-ball:not(.is-dragging) .ball-inner:not(.is-hidden) {
    animation: breathe 3s ease-in-out infinite;
  }
}

/* Dark mode */
[data-theme='dark'] .ball-inner:not(.is-hidden) {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.3);
}

[data-theme='dark'] .unread-badge {
  border-color: var(--color-bg-primary);
}
</style>
