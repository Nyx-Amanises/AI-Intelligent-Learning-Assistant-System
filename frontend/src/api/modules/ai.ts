import http from '@/api/http'

export interface SummaryPayload {
  modelName?: string
  summaryType?: string
  saveAsNote?: boolean
  temperature?: number
}

export interface QuestionGeneratePayload {
  modelName?: string
  title?: string
  questionCount?: number
  singleCount?: number
  judgeCount?: number
  shortAnswerCount?: number
  difficultyLevel?: number
}

export interface AiConfigPayload {
  enabled: boolean
  mockMode: boolean
  chatProviderType?: string
  baseUrl: string
  chatPath: string
  apiKey?: string
  defaultModel: string
  embeddingProviderType?: string
  embeddingBaseUrl?: string
  embeddingPath?: string
  embeddingApiKey?: string
  defaultEmbeddingModel?: string
}

export interface AiTaskDetail {
  id: number
  userId: number
  taskType: string
  bizType?: string
  bizId?: number
  bizTitle?: string
  status: string
  progressRate: number
  retryCount: number
  priority: number
  modelName?: string
  payloadJson?: string
  resultJson?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface AiTaskPageItem {
  id: number
  taskType: string
  bizType?: string
  bizId?: number
  bizTitle?: string
  status: string
  progressRate: number
  retryCount: number
  priority: number
  modelName?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

export interface AiTaskPagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: AiTaskPageItem[]
}

export interface EmbeddingTaskPayload {
  modelName?: string
  forceRegenerate?: boolean
}

export interface RetrievalPreviewSegment {
  segmentId: number
  segmentNo?: number
  pageNo?: number
  sectionTitle?: string
  contentText?: string
  keywords?: string
  score?: number
}

export interface RetrievalPreviewPayload {
  materialId: number
  queryText: string
  limit: number
  hitCount: number
  segments: RetrievalPreviewSegment[]
}

export const generateSummaryApi = (id: number, data: SummaryPayload) =>
  http.post(`/ai/material/${id}/summary`, data)

export const getLatestSummaryApi = (id: number) => http.get(`/ai/material/${id}/latest-summary`)

export const getSummaryHistoryApi = (id: number) => http.get(`/ai/material/${id}/summary-history`)

export const getAllSummaryHistoryApi = () => http.get('/ai/summary-history')

export const generateQuestionSetApi = (id: number, data: QuestionGeneratePayload) =>
  http.post(`/ai/material/${id}/question-set`, data)

export const submitSummaryTaskApi = (id: number, data: SummaryPayload) =>
  http.post(`/ai/tasks/material/${id}/summary`, data)

export const submitQuestionGenerateTaskApi = (id: number, data: QuestionGeneratePayload) =>
  http.post(`/ai/tasks/material/${id}/question-set`, data)

export const submitPracticeReviewTaskApi = (sessionId: number) =>
  http.post(`/ai/tasks/practice/${sessionId}/review`)

export const submitEmbeddingTaskApi = (materialId: number, data: EmbeddingTaskPayload = {}) =>
  http.post(`/ai/tasks/material/${materialId}/embedding`, data)

export const previewMaterialRetrievalApi = (materialId: number, params: Record<string, unknown>) =>
  http.get(`/rag/material/${materialId}/retrieve-preview`, { params })

export const getAiTaskPageApi = (params: Record<string, unknown>) =>
  http.get('/ai/tasks/page', { params })

export const getAiTaskDetailApi = (taskId: number) => http.get(`/ai/tasks/${taskId}`)

export const waitAiTaskApi = (taskId: number, timeoutMs = 120000) =>
  http.get(`/ai/tasks/${taskId}/wait`, {
    params: { timeoutMs }
  })

export const dispatchAiTaskApi = (taskId: number) => http.post(`/ai/tasks/${taskId}/dispatch`)

export const retryAiTaskApi = (taskId: number) => http.post(`/ai/tasks/${taskId}/retry`)

export const deleteAiTaskApi = (taskId: number) => http.delete(`/ai/tasks/${taskId}`)

export const getAiConfigApi = () => http.get('/ai/config')

export const updateAiConfigApi = (data: AiConfigPayload) => http.put('/ai/config', data)
