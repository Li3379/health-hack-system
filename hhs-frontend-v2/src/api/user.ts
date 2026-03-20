import { request } from '@/utils/request'
import type { UserProfileVO, UpdateProfileRequest, ChangePasswordRequest } from '@/types/api'

export const userApi = {
  // 获取个人资料
  getProfile() {
    return request.get<UserProfileVO>('/api/user/profile')
  },

  // 更新个人资料
  updateProfile(data: UpdateProfileRequest) {
    return request.put<void>('/api/user/profile', data)
  },

  // 修改密码
  changePassword(data: ChangePasswordRequest) {
    return request.put<void>('/api/user/password', data)
  },

  // 上传头像
  uploadAvatar(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.upload<{ url: string }>('/api/upload/avatar', formData)
  }
}
