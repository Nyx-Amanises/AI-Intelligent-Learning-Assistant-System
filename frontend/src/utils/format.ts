/**
 * 日期与文本格式化工具，供所有页面共用，保证同一种数据在不同页面显示一致。
 */

const pad = (value: number) => String(value).padStart(2, '0')

const parseDate = (value?: string | number | Date | null) => {
  if (value === null || value === undefined || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** `2026-09-23 14:05`（默认不带秒；`withSeconds` 为 true 时带秒）。 */
export const formatDateTime = (
  value?: string | number | Date | null,
  options: { withSeconds?: boolean; fallback?: string } = {}
) => {
  const { withSeconds = false, fallback = '--' } = options
  const date = parseDate(value)
  if (!date) return fallback
  const base = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  return withSeconds ? `${base}:${pad(date.getSeconds())}` : base
}

/** `2026-09-23`。 */
export const formatDate = (value?: string | number | Date | null, fallback = '--') => {
  const date = parseDate(value)
  if (!date) return fallback
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** `9月23日`，用于首页这类轻量场景。 */
export const formatShortDate = (value?: string | number | Date | null, fallback = '--') => {
  const date = parseDate(value)
  if (!date) return fallback
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date)
}

/** 列表序号：`01`、`02`…… */
export const formatIndex = (index: number) => String(index).padStart(2, '0')

/** 千分位数字。 */
export const formatNumber = (value?: number | null) =>
  Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN') : '0'

/** 字数：`1,234 字`。 */
export const formatCharacters = (value?: number | null) => `${formatNumber(value)} 字`

export interface SourceSegmentLike {
  segmentId?: number | string
  segmentNo?: number
  pageNo?: number
  sectionTitle?: string
  contentText?: string
}

/** 引用片段的位置说明：`第 3 页 · 段落 #12`。 */
export const formatSegmentMeta = (segment?: SourceSegmentLike | null) => {
  const parts: string[] = []
  if (segment?.pageNo) parts.push(`第 ${segment.pageNo} 页`)
  if (segment?.segmentNo) parts.push(`段落 #${segment.segmentNo}`)
  return parts.join(' · ') || '资料摘录'
}

/** 引用片段标题，缺少小节标题时退回"资料摘录 N"。 */
export const formatSegmentTitle = (segment?: SourceSegmentLike | null) =>
  segment?.sectionTitle || `资料摘录 ${segment?.segmentNo || segment?.segmentId || ''}`.trim()

/** 去掉 Markdown 标记后截取摘要。 */
export const buildExcerpt = (text?: string | null, length = 60, fallback = '暂无内容') => {
  if (!text) return fallback
  const normalized = text
    .replace(/^\s{0,3}(?:#{1,6}\s+|>\s*|[-+*]\s+|\d+[.)]\s+)/gm, '')
    .replace(/!?\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/(\*\*|__|~~|`)(.*?)\1/g, '$2')
    .replace(/\s+/g, ' ')
    .trim()
  return normalized.length > length ? `${normalized.slice(0, length)}...` : normalized
}
