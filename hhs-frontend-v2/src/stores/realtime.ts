import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RealtimeMetricVO } from '@/types/api'

export const useRealtimeStore = defineStore('realtime', () => {
  const ws = ref<WebSocket | null>(null)
  const connected = ref(false)
  const latestMetrics = ref<RealtimeMetricVO[]>([])
  const connecting = ref(false)
  const errorMessage = ref<string | null>(null)

  // 连接 WebSocket
  const connect = (token: string) => {
    // 防止重复连接
    if (ws.value && (ws.value.readyState === WebSocket.CONNECTING || ws.value.readyState === WebSocket.OPEN)) {
      console.log('WebSocket already connected or connecting, skipping...')
      return
    }

    // 清理之前的连接
    disconnect()

    // 检查环境变量
    const wsBaseUrl = import.meta.env.VITE_WS_BASE_URL
    if (!wsBaseUrl) {
      console.error('VITE_WS_BASE_URL is not configured')
      errorMessage.value = 'WebSocket 地址未配置'
      return
    }

    const wsUrl = `${wsBaseUrl}/ws/realtime?token=${token}`
    console.log('Connecting to WebSocket:', wsUrl)
    
    connecting.value = true
    errorMessage.value = null
    
    try {
      ws.value = new WebSocket(wsUrl)

      ws.value.onopen = () => {
        connected.value = true
        connecting.value = false
        errorMessage.value = null
        console.log('WebSocket connected successfully')
      }

      ws.value.onmessage = event => {
        try {
          const data = JSON.parse(event.data)
          console.log('WebSocket message received:', data.type || data)
          handleMessage(data)
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error)
        }
      }

      ws.value.onerror = error => {
        console.error('WebSocket error:', error)
        connecting.value = false
        errorMessage.value = 'WebSocket 连接错误'
      }

      ws.value.onclose = (event) => {
        connected.value = false
        connecting.value = false
        console.log('WebSocket disconnected, code:', event.code, 'reason:', event.reason)
        
        // 如果是非正常关闭，记录错误
        if (event.code !== 1000 && event.code !== 1001) {
          errorMessage.value = `连接已断开 (${event.code}: ${event.reason || '未知原因'})`
        }
      }
    } catch (error) {
      console.error('Failed to create WebSocket:', error)
      connecting.value = false
      errorMessage.value = '创建 WebSocket 连接失败'
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
    } else if (data.type === 'pong') {
      // 心跳响应，忽略
      console.debug('Received pong from server')
    } else if (data.type === 'alert') {
      // 预警消息
      console.log('Received alert:', data.data)
    }
  }

  // 断开连接
  const disconnect = () => {
    if (ws.value) {
      // 移除事件监听器，防止 onclose 触发时修改状态
      ws.value.onopen = null
      ws.value.onmessage = null
      ws.value.onerror = null
      ws.value.onclose = null
      
      if (ws.value.readyState === WebSocket.OPEN || ws.value.readyState === WebSocket.CONNECTING) {
        ws.value.close(1000, 'User disconnect')
      }
      ws.value = null
    }
    connected.value = false
    connecting.value = false
  }

  // 发送心跳
  const sendPing = () => {
    if (ws.value && ws.value.readyState === WebSocket.OPEN) {
      ws.value.send(JSON.stringify({ type: 'ping' }))
    }
  }

  return {
    connected,
    connecting,
    errorMessage,
    latestMetrics,
    connect,
    disconnect,
    sendPing
  }
})