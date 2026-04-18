import http from '@/api/http'

export interface PracticeAnswerPayload {
  questionId: number
  userAnswer: string
}

export const startPracticeApi = (questionSetId: number) =>
  http.post('/practice/start', { questionSetId })

export const submitPracticeApi = (sessionId: number, answers: PracticeAnswerPayload[]) =>
  http.post('/practice/submit', { sessionId, answers })

export const getPracticePageApi = (params: Record<string, unknown>) =>
  http.get('/practice/page', { params })

export const getPracticeDetailApi = (sessionId: number) => http.get(`/practice/${sessionId}`)

export const renamePracticeApi = (sessionId: number, sessionName: string) =>
  http.put(`/practice/${sessionId}/name`, { sessionName })

export const waitPracticeReviewApi = (sessionId: number, timeoutMs = 60000) =>
  http.get(`/practice/${sessionId}/review-status`, {
    params: { timeoutMs }
  })

export const deletePracticeApi = (sessionId: number) => http.delete(`/practice/${sessionId}`)
