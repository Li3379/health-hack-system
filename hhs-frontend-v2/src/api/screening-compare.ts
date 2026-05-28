import { request } from '@/utils/request'

export interface ScreeningSummary {
  id: number
  reportName: string
  reportDate: string
  institution: string
}

export interface ComparisonMetric {
  metricKey: string
  metricDisplayName: string
  valueA: number | null
  valueB: number | null
  unitA?: string
  unitB?: string
  referenceRangeA?: string
  referenceRangeB?: string
  changePercent: number | null
  status: 'improved' | 'worsened' | 'stable'
}

export interface ComparisonSummary {
  improvedCount: number
  worsenedCount: number
  stableCount: number
}

export interface ScreeningComparisonVO {
  screeningA: ScreeningSummary
  screeningB: ScreeningSummary
  comparisons: ComparisonMetric[]
  summary: ComparisonSummary
}

export const screeningCompareApi = {
  /** Compare two screening reports */
  compare(idA: number, idB: number) {
    return request.post<ScreeningComparisonVO>('/api/screening/compare', {
      screeningIdA: idA,
      screeningIdB: idB
    })
  }
}
