import { request } from '@/utils/request'

export interface UserReminder {
  id: number
  userId: number
  title: string
  description: string
  reminderType: string
  cronExpression: string
  isActive: boolean
  lastTriggeredAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateReminderRequest {
  title: string
  description?: string
  reminderType: string
  cronExpression: string
}

export const remindersApi = {
  /** Create a new reminder */
  create(data: CreateReminderRequest) {
    return request.post<UserReminder>('/api/reminders', data)
  },

  /** List all reminders for the current user */
  list() {
    return request.get<UserReminder[]>('/api/reminders')
  },

  /** Toggle a reminder's active state */
  toggle(id: number) {
    return request.put<UserReminder>(`/api/reminders/${id}/toggle`)
  },

  /** Delete a reminder */
  remove(id: number) {
    return request.delete<void>(`/api/reminders/${id}`)
  }
}
