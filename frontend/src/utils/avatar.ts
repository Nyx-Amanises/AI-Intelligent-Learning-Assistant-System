import { API_BASE_URL } from '@/api/http'

export function resolveAvatarUrl(url?: string | null) {
  if (!url) return ''
  if (/^(https?:|data:|blob:)/i.test(url)) return url
  const origin = API_BASE_URL.replace(/\/api\/?$/, '')
  if (url.startsWith('/api/')) return origin + url
  return url.startsWith('/') ? url : origin + '/api/' + url.replace(/^\/+/, '')
}
