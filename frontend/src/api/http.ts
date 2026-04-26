import axios from 'axios'
import router from '@/router'
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

const AUTH_EXPIRED_MESSAGE = '\u767b\u5f55\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55'
const DEFAULT_REQUEST_ERROR = '\u8bf7\u6c42\u5931\u8d25'
const DEFAULT_NETWORK_ERROR = '\u7f51\u7edc\u5f02\u5e38\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5'
let authRedirecting = false

const isAuthEndpoint = (url?: string) =>
  Boolean(url && ['/auth/login', '/auth/register'].some((path) => url.includes(path)))

export const handleAuthExpired = (message = AUTH_EXPIRED_MESSAGE) => {
  const userStore = useUserStore()
  const currentRoute = router.currentRoute.value
  const redirect = currentRoute.fullPath

  userStore.logout()

  if (currentRoute.path !== '/login' && !authRedirecting) {
    authRedirecting = true
    void router
      .replace({
        path: '/login',
        query: redirect && redirect !== '/login' ? { redirect } : undefined
      })
      .finally(() => {
        authRedirecting = false
      })
  }

  return new Error(message)
}

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
      if (result.code === 401 && !isAuthEndpoint(response.config.url)) {
        return Promise.reject(handleAuthExpired(result.message || AUTH_EXPIRED_MESSAGE))
      }
      return Promise.reject(new Error(result.message || DEFAULT_REQUEST_ERROR))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    const url = error?.config?.url
    const message = error?.response?.data?.message || error?.message || DEFAULT_NETWORK_ERROR

    if (status === 401 && !isAuthEndpoint(url)) {
      return Promise.reject(handleAuthExpired(message || AUTH_EXPIRED_MESSAGE))
    }

    return Promise.reject(new Error(message))
  }
)

export default http
