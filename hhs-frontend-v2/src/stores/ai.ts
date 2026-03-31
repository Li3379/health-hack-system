import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api/ai'
import type { ChatSessionVO, ConversationVO } from '@/types/api'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  time: string
}

export const useAiStore = defineStore('ai', () => {
  // State
  const sessions = ref<ChatSessionVO[]>([])
  const currentSessionId = ref<string>('')
  const messages = ref<ChatMessage[]>([])
  const remainingCount = ref(0)
  const loading = ref(false)
  const sessionsLoading = ref(false)

  // Getters
  const currentSession = computed(() =>
    sessions.value.find(s => s.sessionId === currentSessionId.value)
  )

  const hasActiveSession = computed(
    () => currentSessionId.value !== '' && messages.value.length > 0
  )

  // Actions
  const createNewSession = () => {
    currentSessionId.value = `session_${Date.now()}`
    messages.value = []
  }

  const loadSessions = async () => {
    sessionsLoading.value = true
    try {
      const res = await aiApi.getSessions()
      sessions.value = res.data
    } catch (error) {
      console.error('加载会话列表失败:', error)
    } finally {
      sessionsLoading.value = false
    }
  }

  const switchSession = async (sessionId: string) => {
    currentSessionId.value = sessionId
    messages.value = []
    loading.value = true
    try {
      const res = await aiApi.getHistory(sessionId)
      messages.value = []
      res.data.history.forEach(conv => {
        messages.value.push({
          role: 'user',
          content: conv.question,
          time: conv.createTime
        })
        messages.value.push({
          role: 'assistant',
          content: conv.answer,
          time: conv.createTime
        })
      })
      remainingCount.value = res.data.remainingCount
    } catch (error) {
      ElMessage.error('加载会话失败')
    } finally {
      loading.value = false
    }
  }

  const sendMessage = async (question: string): Promise<boolean> => {
    if (!question.trim()) return false

    messages.value.push({
      role: 'user',
      content: question,
      time: new Date().toISOString()
    })

    loading.value = true
    try {
      const res = await aiApi.chat({
        sessionId: currentSessionId.value,
        question
      })

      messages.value.push({
        role: 'assistant',
        content: res.data.answer,
        time: new Date().toISOString()
      })

      remainingCount.value = res.data.remainingCount
      currentSessionId.value = res.data.sessionId

      // Refresh sessions list to include new session
      await loadSessions()
      return true
    } catch (error: any) {
      ElMessage.error(error.message || '发送失败')
      // Remove the failed user message
      messages.value.pop()
      return false
    } finally {
      loading.value = false
    }
  }

  const deleteSession = async (sessionId: string): Promise<boolean> => {
    try {
      await aiApi.clearHistory(sessionId)
      sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)

      if (currentSessionId.value === sessionId) {
        createNewSession()
        messages.value = []
      }
      ElMessage.success('会话已删除')
      return true
    } catch (error) {
      ElMessage.error('删除会话失败')
      return false
    }
  }

  const getHistory = async (sessionId?: string): Promise<ConversationVO[]> => {
    try {
      const res = await aiApi.getHistory(sessionId || currentSessionId.value)
      remainingCount.value = res.data.remainingCount
      return res.data.history
    } catch (error) {
      ElMessage.error('获取历史记录失败')
      return []
    }
  }

  const fetchRemainingCount = async () => {
    try {
      const res = await aiApi.getRemainingCount()
      remainingCount.value = res.data
    } catch (error) {
      console.error('获取剩余对话次数失败:', error)
    }
  }

  return {
    // State
    sessions,
    currentSessionId,
    messages,
    remainingCount,
    loading,
    sessionsLoading,
    // Getters
    currentSession,
    hasActiveSession,
    // Actions
    createNewSession,
    loadSessions,
    switchSession,
    sendMessage,
    deleteSession,
    getHistory,
    fetchRemainingCount
  }
})
