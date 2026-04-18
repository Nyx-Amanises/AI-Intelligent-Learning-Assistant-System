import http from '@/api/http'

export interface MaterialPageItem {
  id: number
  title: string
  materialType: string
  parseStatus: string
  summaryStatus: string
  difficultyLevel: number
  tags?: string
  totalCharacters?: number
  embeddingStatus?: string
  embeddedSegmentCount?: number
  totalSegmentCount?: number
  lastStudyTime?: string
  createdAt?: string
}

export interface MaterialForm {
  title: string
  materialType: string
  difficultyLevel: number
  tags?: string
  contentText: string
}

export const createTextMaterialApi = (data: MaterialForm) => http.post('/material/text', data)

export const renameMaterialApi = (id: number, title: string) =>
  http.put(`/material/${id}/title`, { title })

export const getMaterialPageApi = (params: Record<string, unknown>) => http.get('/material/page', { params })

export const getMaterialDetailApi = (id: number) => http.get(`/material/${id}`)

export const parseMaterialApi = (id: number) => http.post(`/material/${id}/parse`)

export const deleteMaterialApi = (id: number) => http.delete(`/material/${id}`)

export const uploadMaterialApi = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/material/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
