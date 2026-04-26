import axios from 'axios'
import { useUserStore } from '@/stores/user'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')

export const resolveApiUrl = (path: string) => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalizedPath}`
}

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 160000
})

http.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result && typeof result.code === 'number' && result.code !== 200) {
      return Promise.reject(new Error(result.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const message =
      error?.response?.data?.message ||
      error?.message ||
      '网络异常，请稍后重试'
    return Promise.reject(new Error(message))
  }
)

export default http
