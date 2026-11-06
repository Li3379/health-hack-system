import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { storage } from '@/utils/storage'
import { resetAuthLatch } from '@/utils/authSession'
import type { LoginRequest, RegisterRequest, UserVO } from '@/types/api'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(storage.getToken())
  const user = ref<UserVO | null>(storage.getUser())

  const isAuthenticated = computed(() => !!token.value)

  // 登录
  const login = async (data: LoginRequest) => {
    const res = await authApi.login(data)
    token.value = res.data.token
    user.value = res.data.user
    storage.setToken(res.data.token)
    storage.setUser(res.data.user)
    // 复位 401 闩锁：上一次会话过期时闩锁被置位，若不复位，
    // 下一次会话的首个 401 会变成零提示、零跳转。
    // 登录失败不走这里（await 会先抛出），因此失败登录不会复位，符合预期。
    resetAuthLatch()
    ElMessage.success('登录成功')
    router.push('/dashboard')
  }

  // 注册
  const register = async (data: RegisterRequest) => {
    await authApi.register(data)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
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
    const res = await userApi.getProfile()
    user.value = res.data.profile
    storage.setUser(res.data.profile)
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
