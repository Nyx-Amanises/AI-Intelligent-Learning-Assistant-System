import http from '@/api/http'
import type { QuestionGeneratePayload } from './ai'

export interface QuestionRegenerationConfig {
  sourceQuestionSetId: number
  materialId: number
  title: string
  parameterMode: 'SAVED' | 'LEGACY_REVIEW'
  notice?: string
  request: QuestionGeneratePayload
}

export const getQuestionSetPageApi = (params: Record<string, unknown>) => http.get('/question-set/page', { params })

export const getQuestionSetDetailApi = (id: number) => http.get(`/question-set/${id}`)

export const deleteQuestionSetApi = (id: number) => http.delete(`/question-set/${id}`)

export const getQuestionRegenerationConfigApi = (id: number) => http.get(`/question-set/${id}/regeneration-config`)

export const regenerateQuestionSetApi = (
  id: number,
  data: QuestionGeneratePayload & { configurationReviewed: boolean }
) => http.post(`/question-set/${id}/regenerate`, data)
