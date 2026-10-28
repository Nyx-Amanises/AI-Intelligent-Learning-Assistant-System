/**
 * 各类状态与枚举的中文文案，全站共用一份，避免同一状态在不同页面叫法不一。
 */

const upper = (value?: string | null) => String(value || '').toUpperCase()

// ---------- 题目 ----------

export const QUESTION_TYPE_LABELS: Record<string, string> = {
  SINGLE: '单选题',
  MULTIPLE: '多选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  SHORT: '简答题',
  SHORT_ANSWER: '简答题'
}

export const formatQuestionType = (value?: string | null, fallback = '题目') =>
  QUESTION_TYPE_LABELS[upper(value)] || fallback

/** 逗号分隔的多个题型：`单选题 / 判断题`。 */
export const formatQuestionTypes = (value?: string | null, fallback = '题型未记录') => {
  const labels = String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => formatQuestionType(item, item))
  return Array.from(new Set(labels)).join(' / ') || fallback
}

export const isShortAnswerType = (value?: string | null) => ['SHORT', 'SHORT_ANSWER'].includes(upper(value))
export const isChoiceType = (value?: string | null) => ['SINGLE', 'MULTIPLE', 'MULTI', 'JUDGE'].includes(upper(value))

export const DIFFICULTY_LABELS: Record<number, string> = { 1: '入门', 2: '基础', 3: '适中', 4: '进阶', 5: '挑战' }

export const formatDifficulty = (level?: number | null, fallback = '适中') =>
  DIFFICULTY_LABELS[Number(level) || 0] || fallback

// ---------- 资料 ----------

export const MATERIAL_TYPE_LABELS: Record<string, string> = {
  PDF: 'PDF',
  DOCX: 'Word',
  DOC: 'Word',
  WORD: 'Word',
  TEXT: '文本',
  TXT: '文本',
  MARKDOWN: 'Markdown'
}

export const formatMaterialType = (value?: string | null, fallback = '学习文档') =>
  MATERIAL_TYPE_LABELS[upper(value)] || fallback

/** 资料类型对应的 AppIcon 名称。 */
export const materialTypeIcon = (value?: string | null) => {
  const type = upper(value)
  if (type === 'PDF') return 'file-pdf'
  if (type === 'DOCX' || type === 'DOC' || type === 'WORD') return 'file-word'
  return ['TEXT', 'TXT', 'MARKDOWN'].includes(type) ? 'file-text' : 'file-unknown'
}

export const PARSE_STATUS_LABELS: Record<string, string> = {
  SUCCESS: '已解析',
  PENDING: '待解析',
  PROCESSING: '解析中',
  PARSING: '解析中',
  RUNNING: '解析中',
  FAILED: '解析失败'
}

export const formatParseStatus = (value?: string | null, fallback = '待解析') =>
  PARSE_STATUS_LABELS[upper(value)] || fallback

// ---------- 总结 ----------

export const SUMMARY_TYPE_LABELS: Record<string, string> = {
  STANDARD: '标准总结',
  EXAM: '考试重点',
  OUTLINE: '结构提纲'
}

export const formatSummaryType = (value?: string | null) => SUMMARY_TYPE_LABELS[upper(value)] || '标准总结'

export const SUMMARY_STATUS_LABELS: Record<string, string> = {
  GENERATING: '生成中',
  SUCCESS: '已生成',
  FAILED: '生成失败',
  CANCELLED: '已取消'
}

export const formatSummaryStatus = (value?: string | null, fallback = '暂不可阅读') =>
  SUMMARY_STATUS_LABELS[upper(value)] || fallback

// ---------- 题集 ----------

export const QUESTION_SET_STATUS_LABELS: Record<string, string> = {
  GENERATING: '生成中',
  PUBLISHED: '可练习',
  FAILED: '生成失败',
  CANCELLED: '已取消',
  DISABLED: '已停用',
  DRAFT: '草稿'
}

export const formatQuestionSetStatus = (value?: string | null, fallback = '暂不可练习') =>
  QUESTION_SET_STATUS_LABELS[upper(value)] || fallback

// ---------- 练习 ----------

export const PRACTICE_STATUS_LABELS: Record<string, string> = {
  SUBMITTED: '已完成',
  COMPLETED: '已完成',
  IN_PROGRESS: '进行中',
  STARTED: '进行中',
  CREATED: '待开始'
}

export const formatPracticeStatus = (value?: string | null, fallback = '进行中') =>
  PRACTICE_STATUS_LABELS[upper(value)] || fallback

export const REVIEW_MODE_LABELS: Record<string, string> = {
  AI: 'AI 评分',
  AI_PENDING: 'AI 评分中',
  RULE: '自动判分'
}

export const formatReviewMode = (value?: string | null, fallback = '自动评分') =>
  REVIEW_MODE_LABELS[upper(value)] || fallback

// ---------- AI 任务 ----------

export const TASK_TYPE_LABELS: Record<string, string> = {
  SUMMARY: 'AI 总结',
  QUESTION_GENERATE: 'AI 出题',
  PRACTICE_REVIEW: '简答题评分',
  EMBEDDING: '向量索引'
}

export const formatTaskType = (value?: string | null) => TASK_TYPE_LABELS[upper(value)] || value || '未知任务'

export const TASK_STATUS_LABELS: Record<string, string> = {
  PENDING: '等待中',
  RUNNING: '执行中',
  SUCCESS: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}

export const formatTaskStatus = (value?: string | null) => TASK_STATUS_LABELS[upper(value)] || value || '未知状态'

/** 统一的状态语义色调，供 StatusPill 使用。 */
export type StatusTone = 'brand' | 'success' | 'processing' | 'failed' | 'muted'

export const statusTone = (value?: string | null): StatusTone => {
  const status = upper(value)
  if (['SUCCESS', 'PUBLISHED', 'SUBMITTED', 'COMPLETED'].includes(status)) return 'success'
  if (['FAILED', 'PARTIAL_FAILED', 'PARSE_FAILED'].includes(status)) return 'failed'
  if (['PENDING', 'PROCESSING', 'RUNNING', 'PARSING', 'PARTIAL', 'GENERATING', 'AI_PENDING'].includes(status))
    return 'processing'
  if (['CANCELLED', 'DISABLED', 'DRAFT', 'CREATED'].includes(status)) return 'muted'
  return 'brand'
}
