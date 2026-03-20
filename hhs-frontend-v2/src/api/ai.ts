import { request } from '@/utils/request'
import type {
  AIChatRequest,
  AIChatResponse,
  AIHistoryResponse,
  ChatSessionVO,
  ChatContextVO,
  AIClassifyRequest,
  AIClassifyResponse
} from '@/types/api'

export const aiApi = {
  // AI 对话
  chat(data: AIChatRequest) {
    return request.post<AIChatResponse>('/api/ai/chat', data)
  },

  // 获取对话历史
  getHistory(sessionId: string) {
    return request.get<AIHistoryResponse>('/api/ai/chat/history', {
      params: { sessionId }
    })
  },

  // 清空对话历史
  clearHistory(sessionId: string) {
    return request.delete<void>(`/api/ai/chat/history/${sessionId}`)
  },

  // 获取会话列表
  getSessions() {
    return request.get<ChatSessionVO[]>('/api/ai/chat/sessions')
  },

  // AI 智能分类
  classify(data: AIClassifyRequest) {
    return request.post<AIClassifyResponse>('/api/ai/classify', data)
  },

  // 获取会话消息（与 getHistory 等价）
  getSessionMessages(sessionId: string) {
    return request.get<AIHistoryResponse>(`/api/ai/chat/sessions/${sessionId}/messages`)
  },

  // 获取会话上下文
  getSessionContext(sessionId: string) {
    return request.get<ChatContextVO>(`/api/ai/chat/sessions/${sessionId}/context`)
  }
}
