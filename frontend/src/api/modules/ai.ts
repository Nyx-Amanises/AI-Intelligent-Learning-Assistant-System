import http from '@/api/http'

export interface SummaryPayload {
  modelName?: string
  summaryType?: string
  saveAsNote?: boolean
  temperature?: number
}

export interface QuestionGeneratePayload {
  modelName?: string
  questionCount?: number
  difficultyLevel?: number
}

export interface AiConfigPayload {
  enabled: boolean
  mockMode: boolean
  baseUrl: string
  chatPath: string
  apiKey?: string
  defaultModel: string
}

export const generateSummaryApi = (id: number, data: SummaryPayload) =>
  http.post(`/ai/material/${id}/summary`, data)

export const getLatestSummaryApi = (id: number) => http.get(`/ai/material/${id}/latest-summary`)

export const getSummaryHistoryApi = (id: number) => http.get(`/ai/material/${id}/summary-history`)

export const getAllSummaryHistoryApi = () => http.get('/ai/summary-history')

export const generateQuestionSetApi = (id: number, data: QuestionGeneratePayload) =>
  http.post(`/ai/material/${id}/question-set`, data)

export const getAiConfigApi = () => http.get('/ai/config')

export const updateAiConfigApi = (data: AiConfigPayload) => http.put('/ai/config', data)
