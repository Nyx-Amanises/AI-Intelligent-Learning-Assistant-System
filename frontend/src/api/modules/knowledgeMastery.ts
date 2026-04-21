import http from '@/api/http'

export interface KnowledgeMasteryItem {
  knowledgePoint: string
  materialId?: number
  materialTitle?: string
  attemptCount: number
  uniqueQuestionCount: number
  correctCount: number
  wrongCount: number
  totalScore: number
  obtainedScore: number
  accuracyRate: number
  scoreRate: number
  masteryPercent: number
  masteryLevel: string
  masteryLabel: string
  suggestion: string
  questionTypes?: string
  lastPracticeTime?: string
}

export interface KnowledgeMasteryPagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: KnowledgeMasteryItem[]
}

export interface KnowledgeMasteryOverviewPayload {
  totalKnowledgePoints: number
  totalAttempts: number
  wrongAttempts: number
  masteredCount: number
  goodCount: number
  weakCount: number
  riskCount: number
  averageMasteryPercent: number
  weakestPoints: KnowledgeMasteryItem[]
  page: KnowledgeMasteryPagePayload
}

export const getKnowledgeMasteryOverviewApi = (params: Record<string, unknown>) =>
  http.get('/knowledge-mastery/overview', { params })
