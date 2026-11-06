import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { normalizeApiError } from '@/utils/appError'
import { isHandling401, markHandling401 } from '@/utils/authSession'
import type { ApiResponse } from '@/types/api'

// 保持既有对外契约：`import { resetAuthLatch } from '@/utils/request'` 仍可用。
export { resetAuthLatch } from '@/utils/authSession'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    // D-14: 固定中文，避免英文浏览器拿到后端英文报错
    config.headers['Accept-Language'] = 'zh-CN'
    // 如果是 FormData，移除默认的 Content-Type 让浏览器自动设置
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：只翻译不展示（Translate-Don't-Display）。
// 本文件不引用任何 UI 组件 —— 所有用户可见提示由调用点或 logout 负责。
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>): any => {
    const res = response.data

    // 后端返回 code 200 表示成功
    if (res.code === 200) {
      return res
    }

    // 信封业务失败：构造"假 response"形状，使 HTTP-200-code≠200 与 HTTP-400
    // 走同一个归一化函数。status 必须写成真实 HTTP 状态 200，不可写成 res.code ——
    // 写成 res.code 会让信封里的 500 回退值冒充 HTTP 状态，5xx 分支可能抢先命中。
    return Promise.reject(normalizeApiError({ response: { status: 200, data: res } }))
  },
  error => {
    const apiError = normalizeApiError(error)

    // 取消不产生任何提示（D-12）
    if (apiError.kind === 'cancel') {
      return Promise.reject(apiError)
    }

    // 并发 401 闩锁（D-11）：只有首条触发 logout，其余静默 reject。
    // 401 链路唯一的用户可见提示来自 logout 自身，此处不得再加第二条。
    if (apiError.kind === 'auth' && !isHandling401()) {
      markHandling401()
      // 会话过期而非用户主动退出：提示「登录已过期，请重新登录」（WR-14）。
      useAuthStore().logout({ reason: 'expired' })
    }

    // 无条件 reject（D-10）。这里刻意不声明 `const silent = ...` ——
    // 本文件不再展示任何东西，silent 的唯一作用是抑制展示，
    // 而"拦截器根本不展示"这一事实已自动满足；声明一个无人读取的局部变量
    // 会在 noUnusedLocals 下触发 TS6133 构建失败。
    return Promise.reject(apiError)
  }
)

// 封装请求方法
export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.get(url, config)
  },

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.post(url, data, config)
  },

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.put(url, data, config)
  },

  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.delete(url, config)
  },

  upload<T = any>(
    url: string,
    formData: FormData,
    config?: AxiosRequestConfig
  ): Promise<ApiResponse<T>> {
    return service.post(url, formData, config)
  }
}

export default service
