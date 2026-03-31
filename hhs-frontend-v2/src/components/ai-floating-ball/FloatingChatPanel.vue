<template>
  <Transition name="panel-slide">
    <div v-if="modelValue" class="chat-panel" :style="panelStyle" @keydown.escape="handleClose">
      <!-- Header -->
      <div class="panel-header">
        <div class="panel-title-area">
          <div class="panel-avatar">
            <el-icon :size="16"><ChatDotRound /></el-icon>
          </div>
          <div class="panel-title-text">
            <h4 class="panel-title">AI 健康顾问</h4>
            <span class="panel-remaining">今日剩余 {{ store.remainingCount }} 次</span>
          </div>
        </div>
        <div class="panel-actions">
          <el-tooltip content="在新页面打开" placement="top">
            <el-button text circle size="small" @click="openFullPage">
              <el-icon :size="16"><FullScreen /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="清空对话" placement="top">
            <el-button text circle size="small" @click="handleClear">
              <el-icon :size="16"><Delete /></el-icon>
            </el-button>
          </el-tooltip>
          <el-button text circle size="small" @click="handleClose">
            <el-icon :size="16"><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- Messages -->
      <div ref="messagesRef" class="panel-messages">
        <!-- Empty state -->
        <div v-if="store.messages.length === 0 && !store.loading" class="panel-empty">
          <div class="empty-icon">
            <el-icon :size="28"><ChatDotRound /></el-icon>
          </div>
          <p class="empty-title">你好，我是小健</p>
          <p class="empty-desc">有什么健康问题可以问我</p>
          <div class="quick-actions">
            <button
              v-for="item in quickQuestions"
              :key="item.text"
              class="quick-btn"
              @click="handleQuickQuestion(item.text)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <!-- Message list -->
        <div v-else class="message-list">
          <div
            v-for="msg in store.messages"
            :key="msg.id"
            class="msg-item"
            :class="msg.role"
          >
            <div v-if="msg.role === 'assistant'" class="msg-avatar ai">
              <el-icon :size="12"><ChatDotRound /></el-icon>
            </div>
            <div class="msg-bubble" :class="msg.role">
              <div v-if="msg.role === 'assistant'" class="msg-text" v-html="renderMarkdown(msg.content)"></div>
              <div v-else class="msg-text">{{ msg.content }}</div>
            </div>
          </div>

          <!-- Typing indicator -->
          <div v-if="store.loading" class="msg-item assistant">
            <div class="msg-avatar ai">
              <el-icon :size="12"><ChatDotRound /></el-icon>
            </div>
            <div class="msg-bubble assistant">
              <div class="typing-dots">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Input -->
      <div class="panel-input">
        <div class="input-row">
          <el-input
            ref="inputRef"
            v-model="inputText"
            placeholder="输入健康问题..."
            :maxlength="500"
            clearable
            @keydown.enter.exact="handleSend"
          />
          <el-button
            type="primary"
            circle
            :loading="store.loading"
            :disabled="!inputText.trim()"
            @click="handleSend"
          >
            <el-icon v-if="!store.loading"><Promotion /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, type CSSProperties } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Close,
  Delete,
  FullScreen,
  Promotion
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { useFloatingAiStore } from '@/stores/floatingAi'

marked.setOptions({ breaks: true, gfm: true })

const props = defineProps<{
  modelValue: boolean
  ballPosition: { x: number; y: number }
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const router = useRouter()
const store = useFloatingAiStore()

const BALL_SIZE = 56
const PANEL_HEIGHT = 520
const PANEL_GAP = 12

const panelStyle = computed<CSSProperties>(() => {
  // Default: panel appears above the ball, right-aligned
  const right = window.innerWidth - props.ballPosition.x - BALL_SIZE
  const bottom = window.innerHeight - props.ballPosition.y + PANEL_GAP

  // If not enough space above, show below the ball
  const topSpace = props.ballPosition.y
  if (topSpace < PANEL_HEIGHT + PANEL_GAP + BALL_SIZE) {
    return {
      position: 'fixed',
      bottom: `${window.innerHeight - props.ballPosition.y - BALL_SIZE - PANEL_GAP - PANEL_HEIGHT}px`,
      right: `${right}px`
    }
  }

  return {
    position: 'fixed',
    bottom: `${bottom}px`,
    right: `${right}px`
  }
})

const inputText = ref('')
const messagesRef = ref<HTMLElement>()
const inputRef = ref()

const quickQuestions = [
  { label: '血压偏高怎么办', text: '我最近血压偏高，有什么建议吗？' },
  { label: '血糖控制', text: '请帮我制定一个血糖控制计划' },
  { label: '运动建议', text: '根据我的身体状况，推荐适合的运动' },
  { label: '饮食规划', text: '请给我一些健康饮食的建议' }
]

function renderMarkdown(content: string): string {
  try {
    const raw = marked.parse(content) as string
    return DOMPurify.sanitize(raw)
  } catch {
    return content
  }
}

function handleClose() {
  emit('update:modelValue', false)
}

function openFullPage() {
  handleClose()
  router.push('/ai/chat')
}

async function handleClear() {
  if (store.messages.length === 0) return
  try {
    await ElMessageBox.confirm('确定清空当前对话？', '清空对话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    store.clearMessages()
  } catch {
    // cancelled
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || store.loading) return

  inputText.value = ''
  const success = await store.sendMessage(text)
  if (!success) {
    inputText.value = text
  }
  scrollToBottom()
}

function handleQuickQuestion(text: string) {
  inputText.value = text
  handleSend()
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// Auto-scroll on new messages
watch(
  () => store.messages.length,
  () => scrollToBottom()
)

// Focus input when panel opens
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      nextTick(() => {
        inputRef.value?.focus()
        scrollToBottom()
        // Auto-send pending question from suggestion bubble click
        if (store.pendingQuestion) {
          inputText.value = store.pendingQuestion
          store.pendingQuestion = ''
          handleSend()
        }
      })
    }
  }
)
</script>

<style scoped>
.chat-panel {
  width: 380px;
  height: 520px;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

/* Header */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-bg-secondary);
  flex-shrink: 0;
}

.panel-title-area {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.panel-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, #4f8eff, #2563eb);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.panel-title-text {
  display: flex;
  flex-direction: column;
}

.panel-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
  line-height: 1.2;
}

.panel-remaining {
  font-size: 10px;
  color: var(--color-text-tertiary);
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.panel-actions .el-button {
  color: var(--color-text-tertiary);
}

.panel-actions .el-button:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-tertiary);
}

/* Messages */
.panel-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: var(--spacing-xl);
}

.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.1), rgba(37, 99, 235, 0.05));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  margin-bottom: var(--spacing-md);
}

.empty-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-xs);
}

.empty-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin: 0 0 var(--spacing-lg);
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  justify-content: center;
}

.quick-btn {
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
  background: var(--color-bg-primary);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  cursor: pointer;
  transition: var(--transition-colors);
}

.quick-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: rgba(37, 99, 235, 0.05);
}

/* Message items */
.message-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.msg-item {
  display: flex;
  gap: var(--spacing-sm);
  animation: fadeInMsg 0.2s ease-out;
}

.msg-item.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
}

.msg-avatar.ai {
  background: linear-gradient(135deg, #4f8eff, #2563eb);
}

.msg-bubble {
  max-width: 80%;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  word-break: break-word;
}

.msg-bubble.assistant {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
  border-radius: var(--radius-md) var(--radius-md) var(--radius-md) 2px;
}

.msg-bubble.user {
  background: var(--color-primary);
  color: white;
  border-radius: var(--radius-md) var(--radius-md) 2px var(--radius-md);
}

.msg-text :deep(p) {
  margin: 0 0 var(--spacing-xs);
}

.msg-text :deep(p:last-child) {
  margin-bottom: 0;
}

.msg-text :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: var(--font-size-xs);
}

.msg-bubble.user .msg-text :deep(code) {
  background: rgba(255, 255, 255, 0.15);
}

/* Typing dots */
.typing-dots {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 4px 0;
}

.typing-dots .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-lighter);
  animation: typingBounce 1.4s ease-in-out infinite;
}

.typing-dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typingBounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

@keyframes fadeInMsg {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Input area */
.panel-input {
  padding: var(--spacing-md);
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-primary);
  flex-shrink: 0;
}

.input-row {
  display: flex;
  gap: var(--spacing-sm);
  align-items: center;
}

.input-row :deep(.el-input__wrapper) {
  border-radius: var(--radius-full);
}

/* Panel transition */
.panel-slide-enter-active {
  animation: panelIn 0.25s ease-out;
}

.panel-slide-leave-active {
  animation: panelOut 0.2s ease-in;
}

@keyframes panelIn {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(16px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@keyframes panelOut {
  from {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
  to {
    opacity: 0;
    transform: scale(0.9) translateY(16px);
  }
}

/* Mobile: full-width bottom sheet */
@media (max-width: 768px) {
  .chat-panel {
    position: fixed;
    bottom: 0;
    right: 0;
    left: 0;
    width: 100%;
    height: 70vh;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  }

  .panel-slide-enter-active {
    animation: sheetIn 0.3s ease-out;
  }

  .panel-slide-leave-active {
    animation: sheetOut 0.2s ease-in;
  }

  @keyframes sheetIn {
    from {
      transform: translateY(100%);
    }
    to {
      transform: translateY(0);
    }
  }

  @keyframes sheetOut {
    from {
      transform: translateY(0);
    }
    to {
      transform: translateY(100%);
    }
  }
}

/* Dark mode */
[data-theme='dark'] .chat-panel {
  background: var(--color-surface);
  border-color: var(--color-border);
}

[data-theme='dark'] .panel-header {
  background: var(--color-bg-tertiary);
}

[data-theme='dark'] .msg-bubble.assistant {
  background: var(--color-bg-tertiary);
}

[data-theme='dark'] .panel-input {
  background: var(--color-surface);
}
</style>
