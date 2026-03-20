import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { storage } from '@/utils/storage'
import type { LoginRequest, RegisterRequest, UserVO } from '@/types/api'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(storage.getToken())
  const user = ref<UserVO | null>(storage.getUser())

  const isAuthenticated = computed(() => !!token.value)

  // 登录
  const login = async (data: LoginRequest) => {
    try {
      const res = await authApi.login(data)
      token.value = res.data.token
      user.value = res.data.user
      storage.setToken(res.data.token)
      storage.setUser(res.data.user)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } catch (error) {
      console.error('Login failed:', error)
      throw error
    }
  }

  // 注册
  const register = async (data: RegisterRequest) => {
    try {
      await authApi.register(data)
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (error) {
      console.error('Register failed:', error)
      throw error
    }
  }

  // 登出
  const logout = () => {
    token.value = null
    user.value = null
    storage.clear()
    router.push('/login')
    ElMessage.info('已退出登录')
  }

  // 更新用户信息
  const updateUser = (newUser: UserVO) => {
    user.value = newUser
    storage.setUser(newUser)
  }

  // 获取最新用户信息
  const fetchUserInfo = async () => {
    try {
      const res = await userApi.getProfile()
      user.value = res.data.profile
      storage.setUser(res.data.profile)
    } catch (error) {
      console.error('Failed to fetch user info:', error)
      throw error
    }
  }

  return {
    token,
    user,
    isAuthenticated,
    login,
    register,
    logout,
    updateUser,
    fetchUserInfo
  }
})
