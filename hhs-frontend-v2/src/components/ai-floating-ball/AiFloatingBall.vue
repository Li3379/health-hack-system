<template>
  <div class="ai-floating-ball-container">
    <FloatingBubble
      :tip="store.currentTip"
      :visible="store.bubbleVisible"
      :ball-position="ballPosition"
      @dismiss="store.hideBubble"
      @view="handleViewTip"
    />
    <FloatingChatPanel v-model="store.panelOpen" :ball-position="ballPosition" />
    <FloatingBallButton
      :unread-count="alertStore.unreadCount"
      :is-active="store.panelOpen"
      @click="store.togglePanel"
      @position-change="onPositionUpdate"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useFloatingAiStore, type HealthTip } from '@/stores/floatingAi'
import { useAlertStore } from '@/stores/alert'
import FloatingBallButton from './FloatingBallButton.vue'
import FloatingBubble from './FloatingBubble.vue'
import FloatingChatPanel from './FloatingChatPanel.vue'

const store = useFloatingAiStore()
const alertStore = useAlertStore()

// Shared ball position - sync from FloatingBallButton via callback
const ballPosition = ref({ x: window.innerWidth - 80, y: window.innerHeight - 140 })

function onPositionUpdate(pos: { x: number; y: number }) {
  ballPosition.value = { ...pos }
}

const ROTATION_INTERVAL = 30000
const INITIAL_DELAY = 5000
let rotationTimer: ReturnType<typeof setInterval> | null = null
let initialTimer: ReturnType<typeof setTimeout> | null = null

function startRotation() {
  stopRotation()
  initialTimer = setTimeout(() => {
    store.showNextTip()
    rotationTimer = setInterval(() => {
      store.showNextTip()
    }, ROTATION_INTERVAL)
  }, INITIAL_DELAY)
}

function stopRotation() {
  if (rotationTimer !== null) {
    clearInterval(rotationTimer)
    rotationTimer = null
  }
  if (initialTimer !== null) {
    clearTimeout(initialTimer)
    initialTimer = null
  }
}

function handleViewTip(tip: HealthTip) {
  stopRotation()
  store.pendingQuestion = tip.question
  store.togglePanel()
}

onMounted(() => {
  startRotation()
})

onUnmounted(() => {
  stopRotation()
})
</script>

<style scoped>
.ai-floating-ball-container {
  position: relative;
  z-index: var(--z-fixed);
}
</style>
