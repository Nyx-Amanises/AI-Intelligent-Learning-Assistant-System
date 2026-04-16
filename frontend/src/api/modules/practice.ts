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
