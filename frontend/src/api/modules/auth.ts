import http from '@/api/http'

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  password: string
  nickname: string
  email?: string
}

export const loginApi = (data: LoginPayload) => http.post('/auth/login', data)

export const registerApi = (data: RegisterPayload) => http.post('/auth/register', data)

export const getProfileApi = () => http.get('/user/profile')

export const uploadAvatarApi = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/user/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
