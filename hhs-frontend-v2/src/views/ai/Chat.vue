<template>
  <div class="ai-chat-page">
    <!-- Ambient background particles -->
    <canvas ref="particleCanvasRef" class="ambient-particles"></canvas>

    <!-- Sidebar: Session List -->
    <aside class="chat-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div v-show="!sidebarCollapsed" class="sidebar-brand">
          <div class="brand-icon-wrap">
            <el-icon :size="18"><ChatDotRound /></el-icon>
          </div>
          <span>AI 健康顾问</span>
        </div>
        <el-button
          :icon="sidebarCollapsed ? Expand : Fold"
          text
          circle
          class="sidebar-toggle-btn"
          @click="sidebarCollapsed = !sidebarCollapsed"
        />
      </div>

      <el-button
        v-show="!sidebarCollapsed"
        type="primary"
        class="new-session-btn"
        @click="handleNewSession"
      >
        <el-icon><Plus /></el-icon>
        新建对话
      </el-button>

      <el-scrollbar v-show="!sidebarCollapsed" class="session-list-scroll">
        <div
          v-for="(session, idx) in aiStore.sessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: aiStore.currentSessionId === session.sessionId }"
          :style="{ animationDelay: `${idx * 40}ms` }"
          @click="handleSwitchSession(session.sessionId)"
        >
          <el-icon class="session-icon"><ChatLineSquare /></el-icon>
          <div class="session-info">
            <div class="session-title">{{ session.summary || '新会话' }}</div>
            <div class="session-meta">{{ formatRelativeTime(session.lastMessageAt) }}</div>
          </div>
          <el-button
            class="session-delete"
            type="danger"
            size="small"
            text
            @click.stop="handleDeleteSession(session.sessionId)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div v-if="aiStore.sessions.length === 0" class="session-empty">
          <div class="empty-icon-wrap">
            <el-icon :size="28"><ChatDotRound /></el-icon>
          </div>
          <p>暂无会话记录</p>
          <span class="empty-hint">点击上方按钮开始新对话</span>
        </div>
      </el-scrollbar>

      <!-- Sidebar Footer -->
      <div v-show="!sidebarCollapsed" class="sidebar-footer">
        <div class="remaining-badge">
          <div class="badge-pulse"></div>
          <el-icon><Clock /></el-icon>
          <span>今日剩余 <strong>{{ aiStore.remainingCount }}</strong> 次</span>
        </div>
        <el-button text size="small" class="history-btn" @click="showHistory">
          <el-icon><Clock /></el-icon>
          历史记录
        </el-button>
      </div>
    </aside>

    <!-- Main Chat Area -->
    <main class="chat-main">
      <!-- Messages Area -->
      <div ref="messagesContainerRef" class="messages-area">
        <!-- Empty State / Welcome -->
        <Transition name="welcome-fade" appear>
          <div v-if="aiStore.messages.length === 0 && !aiStore.loading" class="welcome-section">
            <!-- Floating orbs decoration -->
            <div class="welcome-orbs">
              <div class="orb orb-1"></div>
              <div class="orb orb-2"></div>
              <div class="orb orb-3"></div>
            </div>

            <div class="welcome-hero">
              <div class="welcome-avatar-container">
                <div class="avatar-ring-outer"></div>
                <div class="avatar-ring-inner"></div>
                <div class="welcome-avatar">
                  <svg class="avatar-svg" viewBox="0 0 48 48" fill="none">
                    <path d="M24 4C12.954 4 4 12.954 4 24s8.954 20 20 20 20-8.954 20-20S35.046 4 24 4z" fill="url(#avatarGrad)" opacity="0.15"/>
                    <path d="M16 20a2 2 0 114 0 2 2 0 01-4 0zM28 20a2 2 0 114 0 2 2 0 01-4 0zM15 28c0 0 2.5 4 9 4s9-4 9-4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                    <defs><linearGradient id="avatarGrad" x1="4" y1="4" x2="44" y2="44"><stop stop-color="#5EEAD4"/><stop offset="1" stop-color="#FB923C"/></linearGradient></defs>
                  </svg>
                </div>
              </div>
              <h1 class="welcome-title">你好，我是<span class="title-highlight">小健</span></h1>
              <p class="welcome-subtitle">您的 AI 健康顾问，基于您的健康数据提供个性化建议</p>
            </div>

            <!-- Quick Suggestion Cards -->
            <div class="suggestion-grid">
              <div
                v-for="(suggestion, idx) in quickSuggestions"
                :key="suggestion.text"
                class="suggestion-card"
                :style="{ animationDelay: `${150 + idx * 80}ms` }"
                @click="handleSuggestionClick(suggestion.text)"
              >
                <div class="suggestion-icon" :style="{ background: suggestion.bgColor }">
                  <el-icon :size="18" :color="suggestion.iconColor">
                    <component :is="suggestion.icon" />
                  </el-icon>
                </div>
                <div class="suggestion-content">
                  <div class="suggestion-title">{{ suggestion.title }}</div>
                  <div class="suggestion-desc">{{ suggestion.desc }}</div>
                </div>
                <el-icon class="suggestion-arrow" color="var(--text-3)">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>

            <!-- Health Insights -->
            <div class="health-insights">
              <div class="insight-header">
                <div class="insight-header-icon">
                  <el-icon><TrendCharts /></el-icon>
                </div>
                <span>健康洞察</span>
                <span class="insight-badge">实时</span>
              </div>
              <div class="insight-cards">
                <div class="insight-card">
                  <div class="insight-card-glow" style="--glow-color: var(--success)"></div>
                  <div class="insight-icon-wrap" style="--icon-bg: rgba(16, 185, 129, 0.12)">
                    <el-icon :size="20" color="var(--success)"><Sunrise /></el-icon>
                  </div>
                  <span class="insight-label">今日血压</span>
                  <span v-if="bloodPressure" class="insight-value">{{ bloodPressure.systolic }}/<small>{{ bloodPressure.diastolic }}</small> <span class="insight-unit">mmHg</span></span>
                  <span v-else class="insight-value insight-placeholder">暂无数据</span>
                </div>
                <div class="insight-card">
                  <div class="insight-card-glow" style="--glow-color: var(--warning)"></div>
                  <div class="insight-icon-wrap" style="--icon-bg: rgba(245, 158, 11, 0.12)">
                    <el-icon :size="20" color="var(--warning)"><Odometer /></el-icon>
                  </div>
                  <span class="insight-label">血糖水平</span>
                  <span v-if="bloodGlucose !== null" class="insight-value">{{ bloodGlucose }} <span class="insight-unit">mmol/L</span></span>
                  <span v-else class="insight-value insight-placeholder">暂无数据</span>
                </div>
                <div class="insight-card">
                  <div class="insight-card-glow" style="--glow-color: var(--error)"></div>
                  <div class="insight-icon-wrap" style="--icon-bg: rgba(239, 68, 68, 0.12)">
                    <el-icon :size="20" color="var(--error)"><Sunset /></el-icon>
                  </div>
                  <span class="insight-label">心率</span>
                  <span v-if="heartRate !== null" class="insight-value">{{ heartRate }} <span class="insight-unit">次/分</span></span>
                  <span v-else class="insight-value insight-placeholder">暂无数据</span>
                </div>
              </div>
              <p v-if="!bloodPressure && bloodGlucose === null && heartRate === null" class="insight-hint">
                <el-icon><Warning /></el-icon>
                添加健康数据后，这里将展示您的实时指标，帮助 AI 提供更精准的建议
              </p>
            </div>
          </div>
        </Transition>

        <!-- Message List -->
        <div v-if="aiStore.messages.length > 0 || aiStore.loading" class="message-list">
          <TransitionGroup name="msg-stagger" tag="div">
            <div
              v-for="(msg, index) in aiStore.messages"
              :key="index"
              class="message-item"
              :class="msg.role"
            >
              <div class="message-avatar">
                <el-avatar
                  v-if="msg.role === 'user'"
                  :size="36"
                  :src="authStore.user?.avatar"
                  class="avatar-user"
                >
                  {{ authStore.user?.nickname?.[0] || 'U' }}
                </el-avatar>
                <div v-else class="avatar-ai">
                  <div class="avatar-ai-glow"></div>
                  <el-icon :size="18"><ChatDotRound /></el-icon>
                </div>
              </div>
              <div class="message-body">
                <div class="message-header">
                  <span class="message-sender">{{ msg.role === 'user' ? '你' : '小健' }}</span>
                  <span class="message-time">{{ formatChatTime(msg.time) }}</span>
                </div>
                <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
              </div>
            </div>
          </TransitionGroup>

          <!-- Loading indicator -->
          <Transition name="msg-stagger">
            <div v-if="aiStore.loading" class="message-item assistant">
              <div class="message-avatar">
                <div class="avatar-ai">
                  <div class="avatar-ai-glow"></div>
                  <el-icon :size="18"><ChatDotRound /></el-icon>
                </div>
              </div>
              <div class="message-body">
                <div class="message-header">
                  <span class="message-sender">小健</span>
                </div>
                <div class="message-text typing-indicator">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                </div>
              </div>
            </div>
          </Transition>
        </div>
      </div>

      <!-- Input Area -->
      <div class="input-area">
        <div class="input-toolbar">
          <div class="toolbar-left">
            <el-tooltip content="语音输入" placement="top">
              <el-button
                class="toolbar-btn"
                :class="{ active: isRecording }"
                circle
                @click="toggleVoiceInput"
              >
                <el-icon><Microphone /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="上传图片" placement="top">
              <el-button class="toolbar-btn" circle @click="handleImageUpload">
                <el-icon><Picture /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="上传文件" placement="top">
              <el-button class="toolbar-btn" circle @click="handleFileUpload">
                <el-icon><Document /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
          <div class="toolbar-right">
            <span class="char-count" :class="{ warn: inputMessage.length > 1800 }">
              {{ inputMessage.length }} / 2000
            </span>
          </div>
        </div>
        <div class="input-row">
          <div class="input-wrapper">
            <el-input
              ref="inputRef"
              v-model="inputMessage"
              type="textarea"
              :rows="2"
              :maxlength="2000"
              resize="none"
              placeholder="输入您的健康问题，Ctrl + Enter 发送..."
              @keydown.enter.exact="handleEnterKey"
            />
            <div class="input-glow"></div>
          </div>
          <el-button
            type="primary"
            class="send-btn"
            :loading="aiStore.loading"
            :disabled="!inputMessage.trim()"
            @click="handleSendMessage"
          >
            <el-icon v-if="!aiStore.loading"><Promotion /></el-icon>
          </el-button>
        </div>
        <!-- Attachment preview area (placeholder) -->
        <Transition name="slide-up">
          <div v-if="attachmentPreview" class="attachment-preview">
            <div class="attachment-item">
              <el-icon><Picture /></el-icon>
              <span>{{ attachmentPreview }}</span>
              <el-button text circle size="small" @click="attachmentPreview = null">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>
        </Transition>
      </div>
    </main>

    <!-- History Dialog -->
    <el-dialog
      v-model="historyVisible"
      title="历史对话记录"
      width="720px"
      :close-on-click-modal="false"
      class="history-dialog"
    >
      <el-timeline v-if="historyConversations.length > 0">
        <el-timeline-item
          v-for="conv in historyConversations"
          :key="conv.id"
          :timestamp="formatDateTime(conv.createTime)"
          placement="top"
        >
          <el-card shadow="never" class="history-card">
            <div class="history-question">
              <el-icon color="var(--accent-cool)"><User /></el-icon>
              <span>{{ conv.question }}</span>
            </div>
            <div class="history-answer" v-html="renderMarkdown(conv.answer)"></div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无历史记录" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Delete,
  ChatDotRound,
  ChatLineSquare,
  Expand,
  Fold,
  Clock,
  ArrowRight,
  TrendCharts,
  Sunrise,
  Sunset,
  Odometer,
  Microphone,
  Picture,
  Document,
  Promotion,
  Close,
  User,
  Warning,
  FirstAidKit,
  DataAnalysis,
  Lightning
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'
import { healthApi } from '@/api/health'
import { formatDateTime, formatRelativeTime } from '@/utils/format'
import type { ConversationVO } from '@/types/api'

// Configure marked
marked.setOptions({
  breaks: true,
  gfm: true
})

const authStore = useAuthStore()
const aiStore = useAiStore()

// Refs
const historyVisible = ref(false)
const inputMessage = ref('')
const messagesContainerRef = ref<HTMLElement>()
const inputRef = ref()
const historyConversations = ref<ConversationVO[]>([])
const sidebarCollapsed = ref(false)
const isRecording = ref(false)
const attachmentPreview = ref<string | null>(null)
const particleCanvasRef = ref<HTMLCanvasElement>()
let particleAnimId = 0

// Health insights data
const bloodPressure = ref<{ systolic: number; diastolic: number } | null>(null)
const bloodGlucose = ref<number | null>(null)
const heartRate = ref<number | null>(null)

// Quick suggestions for empty state
const quickSuggestions = computed(() => [
  {
    title: '血压偏高怎么办',
    desc: '了解血压管理建议',
    text: '我最近血压偏高，有什么建议吗？',
    icon: Warning,
    bgColor: 'rgba(239, 68, 68, 0.1)',
    iconColor: '#ef4444'
  },
  {
    title: '血糖控制指导',
    desc: '血糖管理方案',
    text: '请帮我制定一个血糖控制计划',
    icon: DataAnalysis,
    bgColor: 'rgba(245, 158, 11, 0.1)',
    iconColor: '#f59e0b'
  },
  {
    title: '运动健康建议',
    desc: '个性化运动方案',
    text: '根据我的身体状况，推荐适合的运动',
    icon: Lightning,
    bgColor: 'rgba(16, 185, 129, 0.1)',
    iconColor: '#10b981'
  },
  {
    title: '日常饮食规划',
    desc: '营养均衡建议',
    text: '请给我一些健康饮食的建议',
    icon: FirstAidKit,
    bgColor: 'rgba(37, 99, 235, 0.1)',
    iconColor: '#2563eb'
  }
])

// Methods
const renderMarkdown = (content: string): string => {
  try {
    const raw = marked.parse(content) as string
    return DOMPurify.sanitize(raw)
  } catch {
    return content
  }
}

const formatChatTime = (time: string): string => {
  return formatDateTime(time, 'HH:mm')
}

const handleNewSession = () => {
  aiStore.createNewSession()
  ElMessage.success('已创建新会话')
}

const handleSwitchSession = async (sessionId: string) => {
  await aiStore.switchSession(sessionId)
  scrollToBottom()
}

const handleDeleteSession = async (sessionId: string) => {
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？删除后无法恢复。', '删除会话', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await aiStore.deleteSession(sessionId)
  } catch {
    // User cancelled
  }
}

const handleSuggestionClick = (text: string) => {
  inputMessage.value = text
  handleSendMessage()
}

const handleEnterKey = (e: KeyboardEvent) => {
  if (e.ctrlKey) {
    handleSendMessage()
  }
}

const handleSendMessage = async () => {
  if (!inputMessage.value.trim() || aiStore.loading) return

  const question = inputMessage.value.trim()
  inputMessage.value = ''
  attachmentPreview.value = null

  const success = await aiStore.sendMessage(question)
  if (success) {
    scrollToBottom()
  }
}

const showHistory = async () => {
  historyConversations.value = await aiStore.getHistory()
  historyVisible.value = true
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainerRef.value) {
    messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight
  }
}

// Placeholder features
const toggleVoiceInput = () => {
  ElMessage.info('语音输入功能即将上线，敬请期待')
}

const handleImageUpload = () => {
  ElMessage.info('图片上传功能即将上线，敬请期待')
  // Placeholder: show attachment preview
  attachmentPreview.value = 'health_report.png'
}

const handleFileUpload = () => {
  ElMessage.info('文件上传功能即将上线，敬请期待')
  // Placeholder: show attachment preview
  attachmentPreview.value = '体检报告.pdf'
}

const fetchHealthInsights = async () => {
  try {
    const today = new Date().toISOString().split('T')[0]
    const res = await healthApi.getMetrics({ page: 1, size: 100, startDate: today })
    const metrics = res.data?.records ?? []

    // Get latest blood pressure (systolic/diastolic)
    const systolic = metrics.find(m => m.metricKey === 'systolicBP')
    const diastolic = metrics.find(m => m.metricKey === 'diastolicBP')
    if (systolic && diastolic) {
      bloodPressure.value = { systolic: systolic.value, diastolic: diastolic.value }
    }

    // Get latest blood glucose
    const glucose = metrics.find(m => m.metricKey === 'glucose')
    if (glucose) {
      bloodGlucose.value = glucose.value
    }

    // Get latest heart rate
    const hr = metrics.find(m => m.metricKey === 'heartRate')
    if (hr) {
      heartRate.value = hr.value
    }
  } catch {
    // keep null values
  }
}

// Ambient particle background
const initParticles = () => {
  const canvas = particleCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const dpr = window.devicePixelRatio || 1
  const resize = () => {
    const parent = canvas.parentElement
    if (!parent) return
    const rect = parent.getBoundingClientRect()
    canvas.width = rect.width * dpr
    canvas.height = rect.height * dpr
    canvas.style.width = rect.width + 'px'
    canvas.style.height = rect.height + 'px'
    ctx.scale(dpr, dpr)
  }
  resize()
  window.addEventListener('resize', resize)

  interface Particle {
    x: number; y: number; vx: number; vy: number; r: number; o: number; color: string
  }
  const particles: Particle[] = []
  const colors = ['94,234,212', '251,146,60', '139,92,246']
  const count = Math.min(35, Math.floor(window.innerWidth / 40))
  for (let i = 0; i < count; i++) {
    const w = canvas.width / dpr, h = canvas.height / dpr
    particles.push({
      x: Math.random() * w, y: Math.random() * h,
      vx: (Math.random() - 0.5) * 0.3, vy: (Math.random() - 0.5) * 0.3,
      r: Math.random() * 2 + 1, o: Math.random() * 0.3 + 0.05,
      color: colors[Math.floor(Math.random() * colors.length)]
    })
  }

  const animate = () => {
    const w = canvas.width / dpr, h = canvas.height / dpr
    ctx.clearRect(0, 0, w, h)
    for (const p of particles) {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0 || p.x > w) p.vx *= -1
      if (p.y < 0 || p.y > h) p.vy *= -1
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(${p.color},${p.o})`
      ctx.fill()
    }
    // Draw connections
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x
        const dy = particles[i].y - particles[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < 120) {
          ctx.beginPath()
          ctx.moveTo(particles[i].x, particles[i].y)
          ctx.lineTo(particles[j].x, particles[j].y)
          ctx.strokeStyle = `rgba(94,234,212,${0.06 * (1 - dist / 120)})`
          ctx.lineWidth = 0.5
          ctx.stroke()
        }
      }
    }
    particleAnimId = requestAnimationFrame(animate)
  }
  animate()
}

onMounted(async () => {
  await aiStore.loadSessions()
  aiStore.createNewSession()
  await aiStore.fetchRemainingCount()
  await fetchHealthInsights()
  initParticles()
})

onUnmounted(() => {
  cancelAnimationFrame(particleAnimId)
})
</script>

<style scoped>
/* ============================================
   AI Chat Page - Immersive Health-Tech Design
   ============================================ */
.ai-chat-page {
  position: relative;
  display: flex;
  height: calc(100vh - var(--header-height));
  background: var(--bg);
  overflow: hidden;
}

/* ---- Ambient Particle Canvas ---- */
.ambient-particles {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.6;
}

[data-theme='light'] .ambient-particles {
  opacity: 0.3;
}

/* ============================================
   Sidebar - Glassmorphism Enhanced
   ============================================ */
.chat-sidebar {
  position: relative;
  z-index: 10;
  width: 280px;
  background: var(--glass-bg);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-right: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  transition: width 0.35s var(--ease-cinema);
  flex-shrink: 0;
  overflow: hidden;
  box-shadow:
    4px 0 24px rgba(0, 0, 0, 0.12),
    1px 0 0 rgba(255, 255, 255, 0.04) inset;
}

[data-theme='dark'] .chat-sidebar {
  box-shadow:
    4px 0 32px rgba(0, 0, 0, 0.4),
    1px 0 0 rgba(255, 255, 255, 0.03) inset;
}

.chat-sidebar.collapsed {
  width: 60px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-4);
  border-bottom: 1px solid var(--glass-border);
  min-height: 56px;
  background: rgba(255, 255, 255, 0.02);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  font-weight: 600;
  font-size: 15px;
  color: var(--accent-cool);
}

.brand-icon-wrap {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(94, 234, 212, 0.15), rgba(251, 146, 60, 0.1));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-cool);
  border: 1px solid rgba(94, 234, 212, 0.2);
}

.sidebar-toggle-btn {
  color: var(--text-3);
  transition: color 0.2s, transform 0.3s var(--ease-spring);
}

.sidebar-toggle-btn:hover {
  color: var(--text-1);
  transform: scale(1.1);
}

.new-session-btn {
  margin: var(--sp-4);
  width: calc(100% - var(--sp-4) * 2);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--accent-cool), #2dd4bf);
  border: none;
  font-weight: 600;
  height: 40px;
  box-shadow: 0 4px 16px rgba(94, 234, 212, 0.25);
  transition: transform 0.25s var(--ease-spring), box-shadow 0.25s ease;
}

.new-session-btn:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 8px 24px rgba(94, 234, 212, 0.35);
}

.new-session-btn:active {
  transform: translateY(0) scale(0.98);
}

.session-list-scroll {
  flex: 1;
  overflow: hidden;
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px var(--sp-4);
  margin: 2px var(--sp-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  animation: sessionSlideIn 0.3s var(--ease-cinema) backwards;
  transition:
    background 0.15s ease,
    transform 0.25s var(--ease-spring-soft),
    border-color 0.2s ease;
  border-left: 3px solid transparent;
}

@keyframes sessionSlideIn {
  from { opacity: 0; transform: translateX(-12px); }
  to { opacity: 1; transform: translateX(0); }
}

.session-item:hover {
  background: rgba(94, 234, 212, 0.06);
  transform: translateX(3px);
}

.session-item.active {
  background: rgba(94, 234, 212, 0.1);
  border-left-color: var(--accent-cool);
  backdrop-filter: blur(8px);
}

.session-item.active .session-title {
  color: var(--accent-cool);
  font-weight: 600;
}

.session-icon {
  color: var(--text-4);
  flex-shrink: 0;
  transition: color 0.2s;
}

.session-item:hover .session-icon {
  color: var(--text-3);
}

.session-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.session-title {
  font-size: 13px;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s;
}

.session-meta {
  font-size: 11px;
  color: var(--text-4);
  margin-top: 2px;
}

.session-delete {
  opacity: 0;
  transition: opacity 0.15s, transform 0.2s var(--ease-spring);
  flex-shrink: 0;
  transform: scale(0.8);
}

.session-item:hover .session-delete {
  opacity: 1;
  transform: scale(1);
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-8) var(--sp-4);
}

.empty-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: rgba(94, 234, 212, 0.06);
  border: 1px dashed rgba(94, 234, 212, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-4);
}

.session-empty p {
  font-size: 13px;
  color: var(--text-3);
}

.empty-hint {
  font-size: 11px;
  color: var(--text-4);
}

.sidebar-footer {
  padding: var(--sp-4);
  border-top: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  background: rgba(255, 255, 255, 0.02);
}

.remaining-badge {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: 12px;
  color: var(--text-2);
  background: rgba(94, 234, 212, 0.06);
  backdrop-filter: blur(8px);
  padding: 6px 12px;
  border-radius: var(--radius-full);
  border: 1px solid rgba(94, 234, 212, 0.15);
}

.remaining-badge strong {
  color: var(--accent-cool);
  font-weight: 700;
}

.badge-pulse {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-cool);
  animation: pulseDot 2s ease-in-out infinite;
}

@keyframes pulseDot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

.history-btn {
  color: var(--text-3);
  font-size: 12px;
  transition: color 0.2s;
}

.history-btn:hover {
  color: var(--accent-cool);
}

/* ============================================
   Main Chat Area
   ============================================ */
.chat-main {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: transparent;
}

/* ============================================
   Messages Area
   ============================================ */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-6);
  scroll-behavior: smooth;
  position: relative;
}

/* ============================================
   Welcome Section - Immersive 3D
   ============================================ */
.welcome-section {
  position: relative;
  max-width: 740px;
  margin: 0 auto;
  padding: var(--sp-8) var(--sp-6);
  background: var(--glass-bg);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  box-shadow:
    0 8px 40px rgba(0, 0, 0, 0.2),
    0 1px 0 rgba(255, 255, 255, 0.06) inset;
  overflow: hidden;
}

[data-theme='dark'] .welcome-section {
  box-shadow:
    0 12px 48px rgba(0, 0, 0, 0.5),
    0 1px 0 rgba(255, 255, 255, 0.04) inset;
}

/* Welcome transition */
.welcome-fade-enter-active {
  transition: opacity 0.6s var(--ease-cinema), transform 0.6s var(--ease-cinema);
}
.welcome-fade-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.97);
}

/* Floating orbs */
.welcome-orbs {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.15;
  animation: orbFloat 8s ease-in-out infinite;
}

[data-theme='light'] .orb {
  opacity: 0.08;
}

.orb-1 {
  width: 200px; height: 200px;
  background: var(--accent-cool);
  top: -40px; right: -40px;
  animation-delay: 0s;
}
.orb-2 {
  width: 150px; height: 150px;
  background: var(--accent-warm);
  bottom: -30px; left: -30px;
  animation-delay: -3s;
}
.orb-3 {
  width: 120px; height: 120px;
  background: #8b5cf6;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -5s;
}

@keyframes orbFloat {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(15px, -10px) scale(1.05); }
  66% { transform: translate(-10px, 8px) scale(0.95); }
}

.welcome-hero {
  position: relative;
  text-align: center;
  margin-bottom: var(--sp-7);
  padding: var(--sp-6) var(--sp-6) var(--sp-5);
}

/* 3D Avatar with rings */
.welcome-avatar-container {
  position: relative;
  width: 88px;
  height: 88px;
  margin: 0 auto var(--sp-5);
}

.avatar-ring-outer {
  position: absolute;
  inset: -8px;
  border-radius: 50%;
  border: 2px solid rgba(94, 234, 212, 0.15);
  animation: ringRotate 12s linear infinite;
}

.avatar-ring-outer::before {
  content: '';
  position: absolute;
  top: -3px;
  left: 50%;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-cool);
  box-shadow: 0 0 12px rgba(94, 234, 212, 0.6);
}

.avatar-ring-inner {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 1.5px dashed rgba(251, 146, 60, 0.12);
  animation: ringRotate 8s linear infinite reverse;
}

@keyframes ringRotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.welcome-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(94, 234, 212, 0.12), rgba(251, 146, 60, 0.08));
  border: 1.5px solid rgba(94, 234, 212, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-cool);
  box-shadow:
    0 8px 32px rgba(94, 234, 212, 0.15),
    0 0 0 4px rgba(94, 234, 212, 0.05);
  position: relative;
  z-index: 1;
}

.avatar-svg {
  width: 48px;
  height: 48px;
}

.welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-1);
  margin: 0 0 var(--sp-3);
  letter-spacing: -0.5px;
}

.title-highlight {
  background: linear-gradient(135deg, var(--accent-cool), var(--accent-warm));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: 15px;
  color: var(--text-3);
  margin: 0;
  line-height: 1.6;
}

/* ---- Suggestion Cards - 3D Glass ---- */
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-3);
  margin-bottom: var(--sp-6);
  padding: var(--sp-4);
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
}

.suggestion-card {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-4);
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  perspective: 800px;
  transform-style: preserve-3d;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  animation: cardFadeIn 0.4s var(--ease-cinema) backwards;
  transition:
    border-color 0.15s ease,
    transform 0.35s var(--ease-spring),
    box-shadow 0.35s ease,
    background 0.15s ease;
}

@keyframes cardFadeIn {
  from { opacity: 0; transform: translateY(12px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.suggestion-card:hover {
  border-color: rgba(94, 234, 212, 0.3);
  background: var(--glass-bg-hover);
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(94, 234, 212, 0.1) inset;
  transform: translateY(-4px) rotateX(2deg) scale(1.02);
}

.suggestion-card:active {
  transform: translateY(0) scale(0.98);
  transition-duration: 0.1s;
}

.suggestion-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.35s var(--ease-spring);
}

.suggestion-card:hover .suggestion-icon {
  transform: translateZ(12px) scale(1.08);
}

.suggestion-content {
  flex: 1;
  min-width: 0;
}

.suggestion-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 2px;
}

.suggestion-desc {
  font-size: 12px;
  color: var(--text-4);
}

.suggestion-arrow {
  flex-shrink: 0;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.2s, transform 0.25s var(--ease-spring);
}

.suggestion-card:hover .suggestion-arrow {
  opacity: 1;
  transform: translateX(0);
}

/* ---- Health Insights - 3D Metric Cards ---- */
.health-insights {
  position: relative;
  background: var(--glass-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.insight-header {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: var(--sp-4);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--border);
}

.insight-header-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(94, 234, 212, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-cool);
}

.insight-badge {
  margin-left: auto;
  font-size: 10px;
  font-weight: 600;
  color: var(--accent-cool);
  background: rgba(94, 234, 212, 0.1);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  border: 1px solid rgba(94, 234, 212, 0.15);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.insight-cards {
  display: flex;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.insight-card {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-4) var(--sp-3);
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  text-align: center;
  overflow: hidden;
  perspective: 600px;
  transition: transform 0.35s var(--ease-spring), box-shadow 0.35s ease;
}

.insight-card:hover {
  transform: translateY(-3px) rotateX(2deg);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

[data-theme='dark'] .insight-card {
  background: var(--surface-2);
}

.insight-card-glow {
  position: absolute;
  top: -20px;
  right: -20px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: var(--glow-color);
  filter: blur(30px);
  opacity: 0.12;
  pointer-events: none;
}

.insight-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--icon-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.35s var(--ease-spring);
}

.insight-card:hover .insight-icon-wrap {
  transform: translateZ(8px) scale(1.1);
}

.insight-label {
  font-size: 11px;
  color: var(--text-3);
  font-weight: 500;
}

.insight-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.insight-value small {
  font-size: 14px;
  font-weight: 600;
}

.insight-unit {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-4);
  margin-left: 2px;
}

.insight-placeholder {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-4);
}

.insight-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  font-size: 12px;
  color: var(--text-4);
  margin: var(--sp-3) 0 0;
  text-align: center;
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--border);
}

/* ============================================
   Message List - Enhanced 3D Bubbles
   ============================================ */
.message-list {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

/* Message stagger transition */
.msg-stagger-enter-active {
  transition: opacity 0.35s var(--ease-cinema), transform 0.4s var(--ease-spring);
}
.msg-stagger-enter-from {
  opacity: 0;
  transform: translateY(16px) scale(0.97);
}

.message-item {
  display: flex;
  gap: var(--sp-4);
  margin-bottom: var(--sp-6);
  animation: msgFadeIn 0.4s var(--ease-cinema);
}

@keyframes msgFadeIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-item.user .message-header {
  justify-content: flex-end;
}

.message-item.user .message-text {
  background: linear-gradient(135deg, var(--accent-cool), #2dd4bf);
  color: #0a0b0e;
  border: 1px solid rgba(94, 234, 212, 0.3);
  border-radius: var(--radius-lg) var(--radius-sm) var(--radius-lg) var(--radius-lg);
  box-shadow:
    0 4px 16px rgba(94, 234, 212, 0.2),
    0 1px 0 rgba(255, 255, 255, 0.15) inset;
}

[data-theme='light'] .message-item.user .message-text {
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: white;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-user {
  background: linear-gradient(135deg, var(--accent-cool), var(--accent-warm));
  color: white;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(94, 234, 212, 0.2);
}

.avatar-ai {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(94, 234, 212, 0.15), rgba(251, 146, 60, 0.1));
  border: 1.5px solid rgba(94, 234, 212, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-cool);
}

.avatar-ai-glow {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(94, 234, 212, 0.15), transparent 70%);
  animation: aiGlow 3s ease-in-out infinite;
}

@keyframes aiGlow {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

.message-body {
  flex: 1;
  min-width: 0;
  max-width: 70%;
}

.message-header {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-1);
}

.message-sender {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
}

.message-time {
  font-size: 11px;
  color: var(--text-4);
}

.message-text {
  padding: var(--sp-4) var(--sp-5);
  border-radius: var(--radius-sm) var(--radius-lg) var(--radius-lg) var(--radius-lg);
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--glass-border);
  color: var(--text-1);
  font-size: 14px;
  line-height: 1.7;
  word-wrap: break-word;
  box-shadow:
    0 2px 12px rgba(0, 0, 0, 0.08),
    0 1px 0 rgba(255, 255, 255, 0.04) inset;
  transition: box-shadow 0.3s ease, transform 0.3s var(--ease-spring);
}

.message-text:hover {
  box-shadow:
    0 4px 20px rgba(0, 0, 0, 0.12),
    0 1px 0 rgba(255, 255, 255, 0.06) inset;
}

[data-theme='dark'] .message-text {
  box-shadow:
    0 2px 16px rgba(0, 0, 0, 0.25),
    0 1px 0 rgba(255, 255, 255, 0.03) inset;
}

/* Markdown styles inside messages */
.message-text :deep(p) {
  margin: 0 0 var(--sp-3) 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: var(--sp-3) 0;
  padding-left: var(--sp-5);
}

.message-text :deep(li) {
  margin-bottom: var(--sp-1);
}

.message-text :deep(code) {
  background: var(--surface-3);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 12px;
}

.message-text :deep(pre) {
  background: var(--surface-2);
  padding: var(--sp-4);
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: var(--sp-3) 0;
  border: 1px solid var(--border);
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-text :deep(blockquote) {
  border-left: 3px solid var(--accent-cool);
  padding-left: var(--sp-4);
  margin: var(--sp-3) 0;
  color: var(--text-2);
}

.message-text :deep(strong) {
  font-weight: 600;
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3) {
  margin: var(--sp-4) 0 var(--sp-3);
  font-weight: 600;
  color: var(--text-1);
}

.message-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--sp-3) 0;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid var(--border);
  padding: var(--sp-2) var(--sp-3);
  text-align: left;
}

.message-text :deep(th) {
  background: var(--surface-2);
  font-weight: 600;
}

/* User message overrides */
.message-item.user .message-text :deep(code) {
  background: rgba(0, 0, 0, 0.15);
  color: inherit;
}

.message-item.user .message-text :deep(pre) {
  background: rgba(0, 0, 0, 0.1);
  border-color: rgba(255, 255, 255, 0.1);
}

.message-item.user .message-text :deep(blockquote) {
  border-left-color: rgba(255, 255, 255, 0.4);
  color: rgba(10, 11, 14, 0.7);
}

[data-theme='light'] .message-item.user .message-text :deep(blockquote) {
  color: rgba(255, 255, 255, 0.7);
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: var(--sp-4) var(--sp-5);
}

.typing-indicator .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-cool);
  animation: typingBounce 1.4s ease-in-out infinite;
}

.typing-indicator .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typingBounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.3;
  }
  30% {
    transform: translateY(-8px);
    opacity: 1;
  }
}

/* ============================================
   Input Area - Glassmorphism Enhanced
   ============================================ */
.input-area {
  position: relative;
  z-index: 5;
  border-top: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  padding: var(--sp-4) var(--sp-6) var(--sp-5);
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.08);
}

[data-theme='dark'] .input-area {
  box-shadow: 0 -4px 32px rgba(0, 0, 0, 0.3);
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-3);
}

.toolbar-left {
  display: flex;
  gap: var(--sp-1);
}

.toolbar-btn {
  border: none;
  background: transparent;
  color: var(--text-4);
  width: 32px;
  height: 32px;
  transition:
    color 0.15s ease,
    background 0.15s ease,
    transform 0.25s var(--ease-spring);
}

.toolbar-btn:hover {
  color: var(--accent-cool);
  background: rgba(94, 234, 212, 0.08);
  transform: scale(1.1);
}

.toolbar-btn:active {
  transform: scale(0.9);
}

.toolbar-btn.active {
  color: var(--error);
  background: rgba(239, 68, 68, 0.1);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.3); }
  50% { box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
}

.char-count {
  font-size: 11px;
  color: var(--text-4);
  font-variant-numeric: tabular-nums;
}

.char-count.warn {
  color: var(--warning);
}

.input-row {
  display: flex;
  gap: var(--sp-3);
  align-items: flex-end;
}

.input-wrapper {
  flex: 1;
  position: relative;
}

.input-glow {
  position: absolute;
  inset: 0;
  border-radius: var(--radius-lg);
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.3s;
  box-shadow: 0 0 0 2px rgba(94, 234, 212, 0.2), 0 0 20px rgba(94, 234, 212, 0.1);
}

.input-wrapper:focus-within .input-glow {
  opacity: 1;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: var(--radius-lg);
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid var(--border);
  background: var(--surface-1);
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-1);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-row :deep(.el-textarea__inner:focus) {
  border-color: var(--accent-cool);
  box-shadow: 0 0 0 3px rgba(94, 234, 212, 0.1);
}

.input-row :deep(.el-textarea__inner::placeholder) {
  color: var(--text-4);
}

.send-btn {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-lg);
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--accent-cool), #2dd4bf);
  border: none;
  box-shadow: 0 4px 16px rgba(94, 234, 212, 0.25);
  transition:
    transform 0.25s var(--ease-spring-bounce),
    box-shadow 0.25s ease;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.08);
  box-shadow: 0 6px 24px rgba(94, 234, 212, 0.35);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.send-btn:disabled {
  opacity: 0.4;
  background: var(--surface-3);
  box-shadow: none;
}

/* Slide-up transition */
.slide-up-enter-active {
  transition: opacity 0.2s, transform 0.25s var(--ease-spring);
}
.slide-up-leave-active {
  transition: opacity 0.15s, transform 0.2s ease;
}
.slide-up-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* Attachment Preview */
.attachment-preview {
  margin-top: var(--sp-3);
}

.attachment-item {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 6px 12px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--text-2);
}

/* ============================================
   History Dialog
   ============================================ */
.history-card {
  border: 1px solid var(--border);
  background: var(--surface-1);
}

.history-card :deep(.el-card__body) {
  padding: var(--sp-4);
}

.history-question {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
  font-weight: 600;
  color: var(--text-1);
  font-size: 14px;
}

.history-answer {
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.7;
}

.history-answer :deep(p) {
  margin: 0 0 var(--sp-3) 0;
}

.history-answer :deep(p:last-child) {
  margin-bottom: 0;
}

.history-answer :deep(ul),
.history-answer :deep(ol) {
  margin: var(--sp-3) 0;
  padding-left: var(--sp-5);
}

/* ============================================
   Responsive Design
   ============================================ */
@media (max-width: 1024px) {
  .suggestion-grid {
    grid-template-columns: 1fr;
  }

  .insight-cards {
    flex-direction: column;
  }

  .insight-card {
    flex-direction: row;
    text-align: left;
    gap: var(--sp-3);
  }
}

@media (max-width: 768px) {
  .chat-sidebar {
    display: none;
  }

  .ai-chat-page {
    height: calc(100vh - var(--header-height));
  }

  .messages-area {
    padding: var(--sp-4);
  }

  .input-area {
    padding: var(--sp-3) var(--sp-4) var(--sp-4);
  }

  .suggestion-grid {
    grid-template-columns: 1fr;
    gap: var(--sp-2);
    padding: var(--sp-3);
  }

  .suggestion-card {
    padding: var(--sp-3);
  }

  .message-body {
    max-width: 85%;
  }

  .welcome-section {
    padding: var(--sp-5) var(--sp-4);
    margin: 0 var(--sp-2);
  }

  .welcome-title {
    font-size: 22px;
  }

  .welcome-avatar-container {
    width: 72px;
    height: 72px;
  }

  .insight-value {
    font-size: 18px;
  }

  .message-text {
    padding: var(--sp-3) var(--sp-4);
    font-size: 13px;
  }
}

/* ============================================
   Reduced Motion Support
   ============================================ */
@media (prefers-reduced-motion: reduce) {
  .ambient-particles {
    display: none;
  }

  .orb,
  .avatar-ring-outer,
  .avatar-ring-inner,
  .avatar-ai-glow,
  .badge-pulse {
    animation: none !important;
  }

  .suggestion-card,
  .insight-card,
  .message-item,
  .session-item {
    animation: none !important;
    transition-duration: 0.01ms !important;
  }

  .suggestion-card:hover,
  .insight-card:hover,
  .message-text:hover {
    transform: none !important;
  }
}
</style>
