import http from '@/api/http'

export interface SummaryPayload {
  modelName?: string
  summaryType?: string
  saveAsNote?: boolean
  temperature?: number
}

export const generateSummaryApi = (id: number, data: SummaryPayload) =>
  http.post(`/ai/material/${id}/summary`, data)
