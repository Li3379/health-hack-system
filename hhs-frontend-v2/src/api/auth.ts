import { request } from '@/utils/request'
import type { LoginRequest, RegisterRequest, AuthResponse } from '@/types/api'

export const authApi = {
  // 登录
  login(data: LoginRequest) {
    return request.post<AuthResponse>('/api/auth/login', data)
  },

  // 注册
  register(data: RegisterRequest) {
    return request.post<void>('/api/auth/register', data)
  }
}
