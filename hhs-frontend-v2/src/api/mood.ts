import { request } from '@/utils/request'

export interface MoodEntry {
  id: number
  userId: number
  moodScore: number
  energyLevel: number | null
  stressLevel: number | null
  sleepQuality: number | null
  notes: string | null
  recordedAt: string
  createdAt: string
}

export interface CreateMoodRequest {
  moodScore: number
  energyLevel?: number
  stressLevel?: number
  sleepQuality?: number
  notes?: string
}

export interface MoodInsights {
  avgMoodScore: number
  avgEnergyLevel: number
  avgStressLevel: number
  avgSleepQuality: number
  totalEntries: number
  trend: string
  recentEntries: MoodEntry[]
}

export const moodApi = {
  /** Record a new mood entry */
  create(data: CreateMoodRequest) {
    return request.post<MoodEntry>('/api/mood', data)
  },

  /** List mood entries with optional date range */
  list(params?: { startDate?: string; endDate?: string }) {
    return request.get<MoodEntry[]>('/api/mood', { params })
  },

  /** Get mood insights and averages */
  getInsights(days?: number) {
    return request.get<MoodInsights>('/api/mood/insights', { params: { days } })
  },

  /** Delete a mood entry */
  remove(id: number) {
    return request.delete<void>(`/api/mood/${id}`)
  }
}
