import http from '@/api/http'

export interface WrongQuestionItem {
  answerId: number
  sessionId: number
  questionId: number
  questionSetId?: number
  materialId?: number
  materialTitle?: string
  questionSetTitle?: string
  sessionName?: string
  questionType: string
  stemText: string
  optionA?: string
  optionB?: string
  optionC?: string
  optionD?: string
  correctAnswer?: string
  userAnswer?: string
  isCorrect?: number
  obtainedScore?: number
  fullScore?: number
  reviewMode?: string
  reviewComment?: string
  answerAnalysis?: string
  knowledgePoint?: string
  difficultyLevel?: number
  answerTime?: string
  createdAt?: string
}

export interface WrongQuestionPagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: WrongQuestionItem[]
}

export const getWrongQuestionPageApi = (params: Record<string, unknown>) =>
  http.get('/wrong-questions/page', { params })

export const getWrongQuestionDetailApi = (answerId: number) =>
  http.get(`/wrong-questions/${answerId}`)

export const removeWrongQuestionApi = (answerId: number) =>
  http.delete(`/wrong-questions/${answerId}`)
