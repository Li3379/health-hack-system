import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api/ai'
import { normalizeApiError } from '@/utils/appError'
import type { ChatSessionVO, ConversationVO } from '@/types/api'

// D-02：store 不是 UI 层。失败一律**上抛**（void 返回的加载器）或**返回 falsy**
// （两个调用方都按布尔分支的动作），绝不自己弹错误 toast。呈现权归调用点：
// - `loadSessions` / `switchSession` / `getHistory` / `deleteSession` → throw
// - `sendMessage` → 保留 `false`（抛错会在事件处理器里变成未处理的拒绝，
//   且两个调用方都已正确分支，改写它们换不来任何用户可见收益）
// 删除成功的那一条 success 确认保留：UI-SPEC §5.1 明确允许动作确认类成功提示，
// 它不在「store 内不得出现错误 toast」的禁令范围内。

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
      // 上抛而非静默：静默会让「会话列表加载失败」看起来就是「你还没有会话」。
      // 呈现权归调用点（Chat.vue 的 onMounted 包 try/catch → showError）。
      throw normalizeApiError(error)
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
      // 上抛；Chat.vue 的 handleSwitchSession 接住并呈现（本 plan 同步接线）。
      throw normalizeApiError(error)
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
      // 不抛：两个调用方（Chat.vue / FloatingChatPanel.vue）都读布尔值并就地恢复
      // 用户输入。抛错会在事件处理器里变成未处理的拒绝，且换不来任何用户可见收益。
      // 也不弹 toast —— 气泡消失后弹一条转瞬即逝的横幅比「什么都不说」更糟；
      // 正确的面是就地 retry（见调用点的 inline marker）。
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
      // 上抛：旧代码返回 false，而它唯一的调用方用一个裸 catch 把「删除失败」
      // 和「用户取消」混为一谈，真实失败被报告成取消。调用点已拆成
      // confirm-then-api 两段，因此这里可以安全上抛。
      throw normalizeApiError(error)
    }
  }

  const getHistory = async (sessionId?: string): Promise<ConversationVO[]> => {
    try {
      const res = await aiApi.getHistory(sessionId || currentSessionId.value)
      remainingCount.value = res.data.remainingCount
      return res.data.history
    } catch (error) {
      // 上抛：旧代码在失败时返回一个空数组，让「历史请求失败」与「这段会话
      // 没有历史」完全不可区分 —— 对话框两种情况都渲染成空。
      // 抛出让调用点能说清是哪一种，也让它能把对话框保持关闭。
      throw normalizeApiError(error)
    }  }

  const fetchRemainingCount = async () => {
    try {
      const res = await aiApi.getRemainingCount()
      remainingCount.value = res.data
    } catch (error) {
      // remaining count fetch failed
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
