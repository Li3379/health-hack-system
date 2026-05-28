import { request } from '@/utils/request'

export interface HealthGoal {
  id: number
  userId: number
  title: string
  description: string
  metricKey: string
  targetValue: number
  currentValue: number
  unit: string
  status: string
  startDate: string
  endDate: string
  createdAt: string
  updatedAt: string
}

export interface CreateGoalRequest {
  title: string
  description?: string
  metricKey: string
  targetValue: number
  unit: string
  startDate: string
  endDate: string
}

export interface GoalProgress {
  id: number
  goalId: number
  value: number
  note: string
  recordedAt: string
}

export interface AddProgressRequest {
  value: number
  note?: string
}

export const goalsApi = {
  /** Create a new health goal */
  create(data: CreateGoalRequest) {
    return request.post<HealthGoal>('/api/goals', data)
  },

  /** List all health goals for the current user */
  list() {
    return request.get<HealthGoal[]>('/api/goals')
  },

  /** Get a single goal by ID */
  get(id: number) {
    return request.get<HealthGoal>(`/api/goals/${id}`)
  },

  /** Add progress to a goal */
  addProgress(id: number, data: AddProgressRequest) {
    return request.post<GoalProgress>(`/api/goals/${id}/progress`, data)
  },

  /** Delete a goal */
  remove(id: number) {
    return request.delete<void>(`/api/goals/${id}`)
  }
}
