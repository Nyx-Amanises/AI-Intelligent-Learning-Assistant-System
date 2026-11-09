import http from '@/api/http'

export type ResourceType = 'INTERNAL' | 'EXTERNAL'
export type ResourceStatus = 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN'
export type ResourceKind = 'TEXTBOOK' | 'COURSE_PACK' | 'REVIEW_PACK' | 'KNOWLEDGE_CARD'
export type ResourceAccessMode = 'OPEN_RESOURCE' | 'CATALOG_ONLY'

export interface ResourceVO {
  id: number
  title: string
  summary: string
  subject: string
  knowledgePoints: string[]
  difficultyLevel: number
  estimatedMinutes: number
  resourceType: ResourceType
  resourceKind: ResourceKind
  accessMode?: ResourceAccessMode
  isbn?: string | null
  publisher?: string | null
  educationLevel?: string | null
  publicationYear?: number | null
  languageCode?: string
  coverageNote?: string
  downloadUrl?: string
  coverUrl?: string | null
  documentUrl?: string | null
  pageCount?: number | null
  sourceName: string
  sourceUrl?: string
  author: string
  licenseCode: string
  licenseUrl?: string
  licenseNote: string
  status: ResourceStatus
  contentText?: string
  bookmarked: boolean
  materialId?: number
  recommendationReason?: string
}

export interface ResourcePagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: ResourceVO[]
}

export interface ResourcePageParams {
  keyword?: string
  subject?: string
  resourceKind?: ResourceKind
  accessMode?: ResourceAccessMode
  bookmarkedOnly?: boolean
  current: number
  size: number
}

export interface ResourceAdminPageParams {
  keyword?: string
  status?: ResourceStatus
  resourceKind?: ResourceKind
  accessMode?: ResourceAccessMode
  current: number
  size: number
}

interface ResourceResponse<T> {
  code: number
  message: string
  data: T
}

export const getResourcePageApi = (params: ResourcePageParams) =>
  http.get<ResourceResponse<ResourcePagePayload>>('/resources/page', { params })

export const getResourceDetailApi = (id: number) => http.get<ResourceResponse<ResourceVO>>(`/resources/${id}`)

export const setResourceBookmarkApi = (id: number, bookmarked: boolean) =>
  http.put(`/resources/${id}/bookmark`, { bookmarked })

export const importResourceApi = (id: number) =>
  http.post<ResourceResponse<{ materialId: number }>>(`/resources/${id}/import`)

export const getResourceRecommendationsApi = (params: { knowledgePoint: string; limit?: number }) =>
  http.get<ResourceResponse<ResourceVO[]>>('/resources/recommendations', { params })

export const getResourceAdminPageApi = (params: ResourceAdminPageParams) =>
  http.get<ResourceResponse<ResourcePagePayload>>('/resources/admin/page', { params })

export const getResourceAdminDetailApi = (id: number) =>
  http.get<ResourceResponse<ResourceVO>>(`/resources/admin/${id}`)

export const setResourceStatusApi = (id: number, data: { status: ResourceStatus; reviewNote?: string }) =>
  http.put(`/resources/admin/${id}/status`, data)
