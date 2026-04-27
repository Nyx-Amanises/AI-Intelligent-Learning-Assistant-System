import http from '@/api/http'

export interface MasteryDistributionItem {
  level: string
  label: string
  count: number
  percent: number
}

export interface QuestionTypePerformanceItem {
  questionType: string
  questionTypeLabel: string
  attemptCount: number
  correctCount: number
  wrongCount: number
  accuracyRate: number
  scoreRate: number
}

export interface MaterialPerformanceItem {
  materialId?: number
  materialTitle: string
  practiceCount: number
  attemptCount: number
  wrongCount: number
  accuracyRate: number
  scoreRate: number
}

export interface PracticeTrendPoint {
  sessionId: number
  sessionName: string
  materialId?: number
  materialTitle?: string
  totalQuestions: number
  correctCount: number
  wrongCount: number
  totalScore: number
  obtainedScore: number
  durationSeconds?: number
  accuracyRate: number
  scoreRate: number
  submitTime?: string
}

export interface WeakKnowledgePointItem {
  knowledgePoint: string
  materialId?: number
  materialTitle?: string
  attemptCount: number
  wrongCount: number
  masteryPercent: number
  scoreRate: number
  lastPracticeTime?: string
}

export interface LearningAnalyticsOverviewPayload {
  totalPracticeCount: number
  totalQuestionAttempts: number
  wrongAttemptCount: number
  totalStudySeconds: number
  totalKnowledgePoints: number
  weakKnowledgePointCount: number
  averageAccuracyRate: number
  averageScoreRate: number
  masteryDistribution: MasteryDistributionItem[]
  questionTypePerformance: QuestionTypePerformanceItem[]
  materialPerformance: MaterialPerformanceItem[]
  practiceTrend: PracticeTrendPoint[]
  weakKnowledgePoints: WeakKnowledgePointItem[]
}

export const getLearningAnalyticsOverviewApi = (params: Record<string, unknown>) =>
  http.get('/learning-analytics/overview', { params })
