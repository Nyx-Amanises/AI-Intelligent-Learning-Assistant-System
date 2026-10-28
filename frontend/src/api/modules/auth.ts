import http from '@/api/http'

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  password: string
  confirmPassword: string
  nickname: string
  email?: string
}

export interface UpdateProfilePayload {
  nickname: string
  email?: string | null
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export const loginApi = (data: LoginPayload) => http.post('/auth/login', data)

export const registerApi = (data: RegisterPayload) => http.post('/auth/register', data)

/** 修改密码成功后旧 token 全部失效，响应 data.token 是当前设备需要换上的新 token。 */
export const changePasswordApi = (data: ChangePasswordPayload) => http.put('/auth/password', data)

export const getProfileApi = () => http.get('/user/profile')

export const updateProfileApi = (data: UpdateProfilePayload) => http.put('/user/profile', data)

export const uploadAvatarApi = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/user/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
