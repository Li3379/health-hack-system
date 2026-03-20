<template>
  <div class="ai-chat-page">
    <el-row :gutter="20">
      <!-- 会话列表 -->
      <el-col :xs="24" :md="6">
        <el-card class="session-card">
          <template #header>
            <div class="header-actions">
              <span>会话列表</span>
              <el-button type="primary" size="small" @click="handleNewSession">
                <el-icon><Plus /></el-icon>
              </el-button>
            </div>
          </template>
          <el-scrollbar height="calc(100vh - 240px)">
            <div
              v-for="session in aiStore.sessions"
              :key="session.sessionId"
              class="session-item"
              :class="{ active: aiStore.currentSessionId === session.sessionId }"
              @click="handleSwitchSession(session.sessionId)"
            >
              <div class="session-header">
                <div class="session-title">{{ session.summary || '新会话' }}</div>
                <el-button
                  type="danger"
                  size="small"
                  text
                  @click.stop="handleDeleteSession(session.sessionId)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="session-meta">
                <span>{{ formatRelativeTime(session.lastMessageAt) }}</span>
              </div>
            </div>
            <el-empty v-if="aiStore.sessions.length === 0" description="暂无会话" />
          </el-scrollbar>
        </el-card>
      </el-col>

      <!-- 聊天区域 -->
      <el-col :xs="24" :md="18">
        <el-card class="chat-card">
          <template #header>
            <div class="header-actions">
              <span>AI 健康顾问</span>
              <div>
                <el-tag type="info">剩余次数: {{ aiStore.remainingCount }}</el-tag>
                <el-button
                  type="primary"
                  size="small"
                  style="margin-left: 12px"
                  @click="showHistory"
                >
                  历史记录
                </el-button>
              </div>
            </div>
          </template>

          <!-- 消息列表 -->
          <el-scrollbar ref="scrollbarRef" height="calc(100vh - 340px)" class="message-list">
            <div
              v-for="(msg, index) in aiStore.messages"
              :key="index"
              class="message-item"
              :class="msg.role"
            >
              <div class="message-avatar">
                <el-avatar v-if="msg.role === 'user'" :src="authStore.user?.avatar">
                  {{ authStore.user?.nickname?.[0] || 'U' }}
                </el-avatar>
                <el-avatar v-else style="background: #409eff">
                  <el-icon><ChatDotRound /></el-icon>
                </el-avatar>
              </div>
              <div class="message-content">
                <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
                <div class="message-time">{{ formatDateTime(msg.time) }}</div>
              </div>
            </div>
            <div v-if="aiStore.loading" class="message-item assistant">
              <div class="message-avatar">
                <el-avatar style="background: #409eff">
                  <el-icon><ChatDotRound /></el-icon>
                </el-avatar>
              </div>
              <div class="message-content">
                <div class="message-text">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  正在思考...
                </div>
              </div>
            </div>
          </el-scrollbar>

          <!-- 输入区域 -->
          <div class="input-area">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="请输入您的健康问题..."
              @keydown.enter.ctrl="handleSendMessage"
            />
            <div class="input-actions">
              <span class="input-tip">Ctrl + Enter 发送</span>
              <el-button
                type="primary"
                :loading="aiStore.loading"
                :disabled="!inputMessage.trim()"
                @click="handleSendMessage"
              >
                发送
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 历史记录对话框 -->
    <el-dialog v-model="historyVisible" title="历史记录" width="800px">
      <el-timeline>
        <el-timeline-item
          v-for="conv in historyConversations"
          :key="conv.id"
          :timestamp="formatDateTime(conv.createTime)"
        >
          <el-card>
            <div class="history-item">
              <div class="history-question">
                <strong>问:</strong>
                {{ conv.question }}
              </div>
              <div class="history-answer" v-html="renderMarkdown(conv.answer)"></div>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, ChatDotRound, Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'
import { formatDateTime, formatRelativeTime } from '@/utils/format'
import type { ConversationVO } from '@/types/api'

// Configure marked options
marked.setOptions({
  breaks: true,
  gfm: true
})

const authStore = useAuthStore()
const aiStore = useAiStore()

const historyVisible = ref(false)
const inputMessage = ref('')
const scrollbarRef = ref()
const historyConversations = ref<ConversationVO[]>([])

const renderMarkdown = (content: string): string => {
  try {
    return marked.parse(content) as string
  } catch {
    return content
  }
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
    await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await aiStore.deleteSession(sessionId)
  } catch {
    // User cancelled
  }
}

const handleSendMessage = async () => {
  if (!inputMessage.value.trim()) return

  const question = inputMessage.value.trim()
  inputMessage.value = ''

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
  if (scrollbarRef.value) {
    const scrollbar = scrollbarRef.value
    scrollbar.setScrollTop(scrollbar.wrapRef.scrollHeight)
  }
}

onMounted(async () => {
  await aiStore.loadSessions()
  aiStore.createNewSession()
})
</script>

<style scoped>
.ai-chat-page {
  padding: 20px;
}

.session-card,
.chat-card {
  height: calc(100vh - 120px);
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.session-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 8px;
  transition: all 0.3s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.session-title {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.session-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  justify-content: space-between;
}

.message-list {
  padding: 20px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-item.user .message-content {
  align-items: flex-end;
}

.message-item.user .message-text {
  background: #409eff;
  color: white;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-text {
  padding: 12px 16px;
  border-radius: 8px;
  background: #f5f7fa;
  color: #303133;
  line-height: 1.6;
  max-width: 70%;
  word-wrap: break-word;
}

.message-text :deep(p) {
  margin: 0 0 8px 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.message-text :deep(code) {
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.message-text :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-time {
  font-size: 12px;
  color: #909399;
}

.input-area {
  padding: 16px;
  border-top: 1px solid #ebeef5;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-tip {
  font-size: 12px;
  color: #909399;
}

.history-item {
  line-height: 1.8;
}

.history-question {
  margin-bottom: 12px;
  color: #303133;
}

.history-answer {
  color: #606266;
}

.history-answer :deep(p) {
  margin: 0 0 8px 0;
}

.history-answer :deep(ul),
.history-answer :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

@media (max-width: 768px) {
  .session-card {
    margin-bottom: 20px;
  }
}
</style>
