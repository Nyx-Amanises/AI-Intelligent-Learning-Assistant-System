import axios from 'axios'
import { useUserStore } from '@/stores/user'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
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
