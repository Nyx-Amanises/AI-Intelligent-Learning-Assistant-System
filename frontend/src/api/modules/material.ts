import http from '@/api/http'

export interface MaterialPageItem {
  id: number
  title: string
  materialType: string
  parseStatus: string
  summaryStatus: string
  difficultyLevel: number
  tags?: string
  description?: string
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
  description?: string
  contentText: string
}

/** 编辑资料信息：标签、说明传空串表示清空；不传难度则保持原值。 */
export interface MaterialUpdatePayload {
  title: string
  tags?: string
  difficultyLevel?: number
  description?: string
}

export const createTextMaterialApi = (data: MaterialForm) => http.post('/material/text', data)

export const updateMaterialApi = (id: number, data: MaterialUpdatePayload) => http.put(`/material/${id}`, data)

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
