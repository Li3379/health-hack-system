import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api/ai'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
}

export interface HealthTip {
  id: string
  title: string
  content: string
  question: string // 点击后预填到对话框的问题
}

// AI 健康建议库
const HEALTH_TIPS: HealthTip[] = [
  { id: 'tip-1', title: '血压管理小贴士', content: '每天监测血压，保持低盐饮食，有助于稳定血压水平。', question: '请给我一些控制血压的日常建议' },
  { id: 'tip-2', title: '血糖控制提醒', content: '规律进餐、适量运动是稳定血糖的关键，避免空腹剧烈运动。', question: '如何通过饮食和运动控制血糖？' },
  { id: 'tip-3', title: '运动健康建议', content: '每周至少150分钟中等强度有氧运动，如快走、游泳或骑自行车。', question: '请根据我的健康状况推荐适合的运动方案' },
  { id: 'tip-4', title: '饮食均衡指南', content: '每餐保证蔬果占一半，优质蛋白占四分之一，全谷物占四分之一。', question: '请帮我制定一份健康的日常饮食计划' },
  { id: 'tip-5', title: '睡眠质量提升', content: '保持规律作息，睡前1小时远离电子屏幕，卧室温度控制在18-22°C。', question: '如何改善睡眠质量？我经常失眠' },
  { id: 'tip-6', title: '饮水健康提醒', content: '成年人每天建议饮水1500-2000ml，少量多次，不要等口渴再喝。', question: '每天喝多少水合适？有什么饮水建议？' },
  { id: 'tip-7', title: '心率健康知识', content: '静息心率60-100次/分为正常范围，长期运动者可低于60次/分。', question: '我的心率是否正常？如何保持健康心率？' },
  { id: 'tip-8', title: '体重管理建议', content: 'BMI保持在18.5-23.9之间为健康范围，每周减重不建议超过0.5kg。', question: '请帮我制定一个科学的体重管理计划' },
  { id: 'tip-9', title: '心理健康关怀', content: '每天花10分钟冥想或深呼吸练习，有助于缓解压力和焦虑情绪。', question: '最近压力比较大，有什么减压建议吗？' },
  { id: 'tip-10', title: '体检报告解读', content: '定期体检很重要，拿到报告后可咨询AI帮你解读各项指标含义。', question: '请帮我解读一下最近的体检报告' },
]

export const useFloatingAiStore = defineStore('floatingAi', () => {
  // ---- Ball State ----
  const panelOpen = ref(false)

  // ---- AI Suggestion Bubble ----
  const currentTip = ref<HealthTip | null>(null)
  const bubbleVisible = ref(false)
  const lastTipIndex = ref(-1)

  // ---- Chat State ----
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const sessionId = ref('')
  const remainingCount = ref(0)
  const pendingQuestion = ref('')

  // ---- Panel Actions ----
  function togglePanel() {
    panelOpen.value = !panelOpen.value
    if (panelOpen.value) {
      bubbleVisible.value = false
      if (!sessionId.value) {
        sessionId.value = `session_${Date.now()}`
      }
    }
  }

  function closePanel() {
    panelOpen.value = false
  }

  // ---- Suggestion Rotation ----
  function showNextTip() {
    if (panelOpen.value) return // 面板打开时不轮播

    // 随机选一条，避免连续重复
    let idx: number
    do {
      idx = Math.floor(Math.random() * HEALTH_TIPS.length)
    } while (idx === lastTipIndex.value && HEALTH_TIPS.length > 1)

    lastTipIndex.value = idx
    currentTip.value = { ...HEALTH_TIPS[idx] }
    bubbleVisible.value = true
  }

  function hideBubble() {
    bubbleVisible.value = false
    currentTip.value = null
  }

  // ---- Chat Actions ----
  async function sendMessage(question: string): Promise<boolean> {
    if (!question.trim() || loading.value) return false

    const userMessage: ChatMessage = {
      id: `msg-${Date.now()}-user`,
      role: 'user',
      content: question.trim(),
      time: new Date().toISOString()
    }
    messages.value = [...messages.value, userMessage]

    loading.value = true
    try {
      const res = await aiApi.chat({
        sessionId: sessionId.value,
        question: question.trim()
      })

      const assistantMessage: ChatMessage = {
        id: `msg-${Date.now()}-ai`,
        role: 'assistant',
        content: res.data.answer,
        time: new Date().toISOString()
      }
      messages.value = [...messages.value, assistantMessage]

      remainingCount.value = res.data.remainingCount
      sessionId.value = res.data.sessionId

      return true
    } catch (error: any) {
      ElMessage.error(error.message || 'AI 对话失败')
      messages.value = messages.value.slice(0, -1)
      return false
    } finally {
      loading.value = false
    }
  }

  function clearMessages() {
    messages.value = []
    sessionId.value = `session_${Date.now()}`
  }

  return {
    // State
    panelOpen,
    currentTip,
    bubbleVisible,
    messages,
    loading,
    sessionId,
    remainingCount,
    pendingQuestion,
    // Getters
    // Actions
    togglePanel,
    closePanel,
    showNextTip,
    hideBubble,
    sendMessage,
    clearMessages
  }
})
