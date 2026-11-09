import http from '@/api/http'

export type NoteSourceType = 'GENERAL' | 'MATERIAL' | 'RESOURCE' | 'QUESTION'
export interface NoteContext {
  sourceType: NoteSourceType
  sourceId?: number | null
  practiceSessionId?: number | null
  pageNumber?: number | null
  sourceTitle?: string | null
  sourceExcerpt?: string | null
}
export interface PersonalNote extends NoteContext {
  id: number
  clientKey: string
  title: string
  contentJson: string
  contentText: string
  notebookId: number | null
  notebookName?: string | null
  favorite: boolean
  version: number
  createdAt: string
  updatedAt: string
}
export interface NoteNotebook {
  id: number
  name: string
  color: string
  noteCount: number
  createdAt: string
  updatedAt: string
}
export interface NotePageQuery {
  keyword?: string
  notebookId?: number
  sourceType?: NoteSourceType
  sourceGroup?: 'READING' | 'QUESTION'
  sourceId?: number
  favorite?: boolean
  pageNum?: number
  pageSize?: number
}
export interface NotePagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: PersonalNote[]
}
export interface NoteWritePayload {
  title?: string
  contentJson?: string
  contentText?: string
  notebookId?: number | null
  favorite?: boolean
  version?: number
}
interface NoteResponse<T> {
  code: number
  message: string
  data: T
}
const requestOptions = { timeout: 15000 }
export const getNotesApi = (params: NotePageQuery = {}) =>
  http.get<NoteResponse<NotePagePayload>>('/notes/page', { params, ...requestOptions })
export const getNoteApi = (id: number) => http.get<NoteResponse<PersonalNote>>(`/notes/${id}`, requestOptions)
export const createNoteApi = (data: NoteWritePayload & NoteContext & { clientKey: string }) =>
  http.post<NoteResponse<PersonalNote>>('/notes', data, requestOptions)
export const updateNoteApi = (id: number, data: NoteWritePayload) =>
  http.put<NoteResponse<PersonalNote>>(`/notes/${id}`, data, requestOptions)
export const deleteNoteApi = (id: number) => http.delete(`/notes/${id}`, requestOptions)
export const getNotebooksApi = () => http.get<NoteResponse<NoteNotebook[]>>('/notes/notebooks', requestOptions)
export const createNotebookApi = (data: { name: string; color: string }) =>
  http.post<NoteResponse<NoteNotebook>>('/notes/notebooks', data, requestOptions)
export const updateNotebookApi = (id: number, data: { name: string; color: string }) =>
  http.put<NoteResponse<NoteNotebook>>(`/notes/notebooks/${id}`, data, requestOptions)
export const deleteNotebookApi = (id: number) => http.delete(`/notes/notebooks/${id}`, requestOptions)

export const isNoteImageUrl = (url: unknown): url is string =>
  typeof url === 'string' &&
  /^\/api\/notes\/images\/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\.(?:png|jpg)$/.test(url)
export const uploadNoteImageApi = (file: File, signal?: AbortSignal) => {
  const data = new FormData()
  data.append('file', file)
  return http.post<NoteResponse<{ url: string; width: number; height: number }>>('/notes/images', data, {
    timeout: 30000,
    signal
  })
}
export const getNoteImageApi = async (url: string, signal?: AbortSignal) => {
  if (!isNoteImageUrl(url)) throw new Error('图片地址无效')
  const response = await http.get<Blob>(url.slice(4), { responseType: 'blob', timeout: 15000, signal })
  if (!['image/png', 'image/jpeg'].includes(response.data.type)) throw new Error('图片暂时无法读取')
  return response.data
}
