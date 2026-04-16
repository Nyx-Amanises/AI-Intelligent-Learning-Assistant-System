import http from '@/api/http'

export const getQuestionSetPageApi = (params: Record<string, unknown>) =>
  http.get('/question-set/page', { params })

export const getQuestionSetDetailApi = (id: number) => http.get(`/question-set/${id}`)
