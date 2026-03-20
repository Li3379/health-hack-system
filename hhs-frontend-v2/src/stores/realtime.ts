import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RealtimeMetricVO } from '@/types/api'

export const useRealtimeStore = defineStore('realtime', () => {
  const ws = ref<WebSocket | null>(null)
  const connected = ref(false)
  const latestMetrics = ref<RealtimeMetricVO[]>([])

  // 连接 WebSocket
  const connect = (token: string) => {
    const wsUrl = `${import.meta.env.VITE_WS_BASE_URL}/ws/realtime?token=${token}`
    ws.value = new WebSocket(wsUrl)

    ws.value.onopen = () => {
      connected.value = true
      console.log('WebSocket connected')
    }

    ws.value.onmessage = event => {
      try {
        const data = JSON.parse(event.data)
        handleMessage(data)
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error)
      }
    }

    ws.value.onerror = error => {
      console.error('WebSocket error:', error)
    }

    ws.value.onclose = () => {
      connected.value = false
      console.log('WebSocket disconnected')
    }
  }

  // 处理消息
  const handleMessage = (data: any) => {
    if (data.type === 'metric') {
      const metric = data.payload as RealtimeMetricVO
      const index = latestMetrics.value.findIndex(m => m.metricKey === metric.metricKey)
      if (index >= 0) {
        latestMetrics.value[index] = metric
      } else {
        latestMetrics.value.push(metric)
      }
    }
  }

  // 断开连接
  const disconnect = () => {
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
  }

  return {
    connected,
    latestMetrics,
    connect,
    disconnect
  }
})
