<template>
  <div class="ai-chat-page">
    <!-- Sidebar: Session List -->
    <aside class="chat-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="sidebar-brand" v-show="!sidebarCollapsed">
          <el-icon :size="20"><ChatDotRound /></el-icon>
          <span>AI 健康顾问</span>
        </div>
        <el-button
          :icon="sidebarCollapsed ? Expand : Fold"
          text
          circle
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
          v-for="session in aiStore.sessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: aiStore.currentSessionId === session.sessionId }"
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
          <el-icon :size="32" color="var(--color-text-tertiary)"><ChatDotRound /></el-icon>
          <p>暂无会话记录</p>
        </div>
      </el-scrollbar>

      <!-- Sidebar Footer -->
      <div v-show="!sidebarCollapsed" class="sidebar-footer">
        <div class="remaining-badge">
          <el-icon><Clock /></el-icon>
          <span>今日剩余 {{ aiStore.remainingCount }} 次</span>
        </div>
        <el-button text size="small" @click="showHistory">
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
        <div v-if="aiStore.messages.length === 0 && !aiStore.loading" class="welcome-section">
          <div class="welcome-hero">
            <div class="welcome-avatar">
              <el-icon :size="36"><ChatDotRound /></el-icon>
            </div>
            <h1 class="welcome-title">你好，我是小健</h1>
            <p class="welcome-subtitle">您的 AI 健康顾问，基于您的健康数据提供个性化建议</p>
          </div>

          <!-- Quick Suggestion Cards -->
          <div class="suggestion-grid">
            <div
              v-for="suggestion in quickSuggestions"
              :key="suggestion.text"
              class="suggestion-card"
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
              <el-icon class="suggestion-arrow" color="var(--color-text-tertiary)">
                <ArrowRight />
              </el-icon>
            </div>
          </div>

          <!-- Health Insights (Placeholder) -->
          <div class="health-insights">
            <div class="insight-header">
              <el-icon color="var(--color-primary)"><TrendCharts /></el-icon>
              <span>健康洞察</span>
            </div>
            <div class="insight-cards">
              <div class="insight-card">
                <el-icon :size="20" color="var(--color-success)"><Sunrise /></el-icon>
                <span class="insight-label">今日血压</span>
                <span class="insight-value insight-placeholder">暂无数据</span>
              </div>
              <div class="insight-card">
                <el-icon :size="20" color="var(--color-warning)"><Odometer /></el-icon>
                <span class="insight-label">血糖水平</span>
                <span class="insight-value insight-placeholder">暂无数据</span>
              </div>
              <div class="insight-card">
                <el-icon :size="20" color="var(--color-danger)"><Sunset /></el-icon>
                <span class="insight-label">心率</span>
                <span class="insight-value insight-placeholder">暂无数据</span>
              </div>
            </div>
            <p class="insight-hint">添加健康数据后，这里将展示您的实时指标，帮助 AI 提供更精准的建议</p>
          </div>
        </div>

        <!-- Message List -->
        <div v-else class="message-list">
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

          <!-- Loading indicator -->
          <div v-if="aiStore.loading" class="message-item assistant">
            <div class="message-avatar">
              <div class="avatar-ai">
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
        <div v-if="attachmentPreview" class="attachment-preview">
          <div class="attachment-item">
            <el-icon><Picture /></el-icon>
            <span>{{ attachmentPreview }}</span>
            <el-button text circle size="small" @click="attachmentPreview = null">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
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
              <el-icon color="var(--color-primary)"><User /></el-icon>
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
import { ref, onMounted, nextTick, computed } from 'vue'
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
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'
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
    return marked.parse(content) as string
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

onMounted(async () => {
  await aiStore.loadSessions()
  aiStore.createNewSession()
})
</script>

<style scoped>
/* ============================================
   Layout
   ============================================ */
.ai-chat-page {
  display: flex;
  height: calc(100vh - var(--header-height));
  background: var(--color-bg-secondary);
  overflow: hidden;
}

/* ============================================
   Sidebar
   ============================================ */
.chat-sidebar {
  width: 280px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-slow);
  flex-shrink: 0;
  overflow: hidden;
}

.chat-sidebar.collapsed {
  width: 60px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--spacing-md);
  border-bottom: 1px solid var(--color-border-light);
  min-height: 56px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-base);
  color: var(--color-primary);
}

.new-session-btn {
  margin: var(--spacing-md);
  width: calc(100% - var(--spacing-md) * 2);
  border-radius: var(--radius-lg);
}

.session-list-scroll {
  flex: 1;
  overflow: hidden;
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  margin: 2px var(--spacing-sm);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: var(--transition-colors);
}

.session-item:hover {
  background: var(--color-bg-tertiary);
}

.session-item.active {
  background: rgba(37, 99, 235, 0.08);
  border-left: 3px solid var(--color-primary);
}

.session-item.active .session-title {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.session-icon {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.session-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.session-title {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.session-delete {
  opacity: 0;
  transition: opacity var(--transition-fast);
  flex-shrink: 0;
}

.session-item:hover .session-delete {
  opacity: 1;
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-2xl) var(--spacing-md);
}

.session-empty p {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.sidebar-footer {
  padding: var(--spacing-md);
  border-top: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.remaining-badge {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  background: var(--color-bg-tertiary);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-full);
}

/* ============================================
   Main Chat Area
   ============================================ */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--color-bg-primary);
}

/* ============================================
   Messages Area
   ============================================ */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg);
  scroll-behavior: smooth;
}

/* ============================================
   Welcome Section
   ============================================ */
.welcome-section {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--spacing-xl) 0;
  animation: fadeInUp 0.4s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.welcome-hero {
  text-align: center;
  margin-bottom: var(--spacing-2xl);
}

.welcome-avatar {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-2xl);
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary));
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--spacing-lg);
  color: white;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.25);
}

.welcome-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-sm);
}

.welcome-subtitle {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  margin: 0;
}

/* Suggestion Cards */
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.suggestion-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: var(--transition-shadow), var(--transition-transform), var(--transition-colors);
}

.suggestion-card:hover {
  border-color: var(--color-primary-lighter);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.suggestion-card:active {
  transform: translateY(0);
}

.suggestion-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.suggestion-content {
  flex: 1;
  min-width: 0;
}

.suggestion-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.suggestion-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.suggestion-arrow {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}

.suggestion-card:hover .suggestion-arrow {
  opacity: 1;
  transform: translateX(4px);
}

/* Health Insights */
.health-insights {
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-xl);
  padding: var(--spacing-lg);
}

.insight-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.insight-cards {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.insight-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-md);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-lg);
  text-align: center;
}

.insight-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.insight-value {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
}

.insight-placeholder {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-normal);
  color: var(--color-text-tertiary);
}

.insight-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin: 0;
  text-align: center;
}

/* ============================================
   Message List
   ============================================ */
.message-list {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

.message-item {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
  animation: fadeInUp 0.3s ease-out;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-item.user .message-header {
  justify-content: flex-end;
}

.message-item.user .message-text {
  background: var(--color-primary);
  color: white;
  border-radius: var(--radius-lg) var(--radius-sm) var(--radius-lg) var(--radius-lg);
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-user {
  background: var(--color-primary-lighter);
  color: white;
  font-weight: var(--font-weight-semibold);
}

.avatar-ai {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary));
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.message-body {
  flex: 1;
  min-width: 0;
  max-width: 70%;
}

.message-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.message-sender {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

.message-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.message-text {
  padding: var(--spacing-md) var(--spacing-lg);
  border-radius: var(--radius-sm) var(--radius-lg) var(--radius-lg) var(--radius-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  word-wrap: break-word;
}

/* Markdown styles inside messages */
.message-text :deep(p) {
  margin: 0 0 var(--spacing-sm) 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: var(--spacing-sm) 0;
  padding-left: var(--spacing-lg);
}

.message-text :deep(li) {
  margin-bottom: var(--spacing-xs);
}

.message-text :deep(code) {
  background: var(--color-bg-tertiary);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.message-text :deep(pre) {
  background: var(--color-bg-tertiary);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: var(--spacing-sm) 0;
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-text :deep(blockquote) {
  border-left: 3px solid var(--color-primary-lighter);
  padding-left: var(--spacing-md);
  margin: var(--spacing-sm) 0;
  color: var(--color-text-secondary);
}

.message-text :deep(strong) {
  font-weight: var(--font-weight-semibold);
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3) {
  margin: var(--spacing-md) 0 var(--spacing-sm);
  font-weight: var(--font-weight-semibold);
}

.message-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--spacing-sm) 0;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid var(--color-border);
  padding: var(--spacing-xs) var(--spacing-sm);
  text-align: left;
}

.message-text :deep(th) {
  background: var(--color-bg-tertiary);
  font-weight: var(--font-weight-medium);
}

/* User message overrides for dark code blocks */
.message-item.user .message-text :deep(code) {
  background: rgba(255, 255, 255, 0.15);
}

.message-item.user .message-text :deep(pre) {
  background: rgba(255, 255, 255, 0.1);
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
}

.typing-indicator .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary-lighter);
  animation: typingBounce 1.4s ease-in-out infinite;
}

.typing-indicator .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typingBounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-8px);
    opacity: 1;
  }
}

/* ============================================
   Input Area
   ============================================ */
.input-area {
  border-top: 1px solid var(--color-border-light);
  background: var(--color-surface);
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-lg);
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-sm);
}

.toolbar-left {
  display: flex;
  gap: var(--spacing-xs);
}

.toolbar-btn {
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  width: 32px;
  height: 32px;
}

.toolbar-btn:hover {
  color: var(--color-primary);
  background: var(--color-bg-tertiary);
}

.toolbar-btn.active {
  color: var(--color-danger);
  background: rgba(239, 68, 68, 0.1);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.3);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(239, 68, 68, 0);
  }
}

.char-count {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.char-count.warn {
  color: var(--color-warning);
}

.input-row {
  display: flex;
  gap: var(--spacing-sm);
  align-items: flex-end;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: var(--radius-lg);
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.input-row :deep(.el-textarea__inner:focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.send-btn {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-lg);
  flex-shrink: 0;
}

/* Attachment Preview */
.attachment-preview {
  margin-top: var(--spacing-sm);
}

.attachment-item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

/* ============================================
   History Dialog
   ============================================ */
.history-card {
  border: 1px solid var(--color-border-light);
}

.history-card :deep(.el-card__body) {
  padding: var(--spacing-md);
}

.history-question {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

.history-answer {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
}

.history-answer :deep(p) {
  margin: 0 0 var(--spacing-sm) 0;
}

.history-answer :deep(p:last-child) {
  margin-bottom: 0;
}

.history-answer :deep(ul),
.history-answer :deep(ol) {
  margin: var(--spacing-sm) 0;
  padding-left: var(--spacing-lg);
}

/* ============================================
   Responsive
   ============================================ */
@media (max-width: 1024px) {
  .suggestion-grid {
    grid-template-columns: 1fr;
  }

  .insight-cards {
    flex-direction: column;
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
    padding: var(--spacing-md);
  }

  .input-area {
    padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  }

  .suggestion-grid {
    grid-template-columns: 1fr;
    gap: var(--spacing-sm);
  }

  .message-body {
    max-width: 85%;
  }

  .welcome-section {
    padding: var(--spacing-md) 0;
  }
}

/* ============================================
   Dark Mode Adjustments
   ============================================ */
[data-theme='dark'] .suggestion-card:hover {
  border-color: var(--color-primary);
}

[data-theme='dark'] .message-item.user .message-text {
  background: var(--color-primary-dark);
}

[data-theme='dark'] .insight-card {
  background: var(--color-bg-tertiary);
}
</style>
