import type {
  AssistantCitation,
  AssistantRagEvidence,
  AssistantSessionDetail,
  AssistantSessionPageItem,
  AssistantToolCall
} from '@/api/modules/assistant'

/** 学习助手相关的纯格式化 / 判定函数，不依赖任何组件状态。 */

export const isUserMessage = (role?: string) => String(role || '').toUpperCase() === 'USER'

export const isSessionPinned = (session?: Pick<AssistantSessionPageItem | AssistantSessionDetail, 'pinned'> | null) =>
  Number(session?.pinned || 0) === 1

export const formatDateTime = (value?: string) => {
  if (!value) {
    return '刚刚'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export const formatContextLabel = (
  contextType?: string,
  contextId?: number,
  detail?: Partial<AssistantSessionDetail>
) => {
  const type = String(contextType || '').toUpperCase()
  if (type === 'MATERIAL') {
    return `资料 #${detail?.currentMaterialId || contextId || '--'}`
  }
  if (type === 'QUESTION_SET') {
    return `题集 #${detail?.currentQuestionSetId || contextId || '--'}`
  }
  if (type === 'PRACTICE_SESSION') {
    return `练习 #${detail?.currentPracticeSessionId || contextId || '--'}`
  }
  if (type === 'AI_TASK') {
    return `任务 #${contextId || '--'}`
  }
  return '通用会话'
}

export const formatJson = (value?: string) => {
  if (!value) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

export const toolStatusClass = (status?: string) => {
  switch (String(status || '').toUpperCase()) {
    case 'SUCCESS':
      return 'assistant-inline-chip--success'
    case 'FAILED':
      return 'assistant-inline-chip--danger'
    case 'RUNNING':
    case 'WAITING':
      return 'assistant-inline-chip--warning'
    default:
      return ''
  }
}

/** 把 ISO 时间字符串转成毫秒时间戳，非法值按 0 处理，便于排序。 */
export const toTimeValue = (value?: string) => {
  if (!value) {
    return 0
  }
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

/** 生成与后端格式一致（yyyy-MM-ddTHH:mm:ss）的本地时间，用于乐观消息。 */
export const buildLocalDateTime = () => {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

type LegacyRetrievedSegment = {
  segmentId?: number
  segmentNo?: number
  pageNo?: number
  sectionTitle?: string
  contentText?: string
  score?: number
}

const legacyLocationText = (segment: LegacyRetrievedSegment) =>
  segment.sectionTitle ||
  [segment.pageNo ? `第 ${segment.pageNo} 页` : '', segment.segmentNo ? `第 ${segment.segmentNo} 段` : '']
    .filter(Boolean)
    .join(' · ') ||
  '资料片段'

/**
 * 从本轮工具调用中取出资料问答的检索依据（rag.retrieve 的结果）。
 *
 * 新版结果是带编号引用的对象；早期会话保存的是分段数组，这里一并兼容，只是没有资料名和依据判定。
 */
export const parseRagEvidence = (toolCalls: AssistantToolCall[]): AssistantRagEvidence | null => {
  const call = [...toolCalls]
    .reverse()
    .find(
      (item) =>
        item.toolName === 'rag.retrieve' && String(item.status || '').toUpperCase() === 'SUCCESS' && item.toolResultJson
    )
  if (!call?.toolResultJson) return null

  let parsed: unknown
  try {
    parsed = JSON.parse(call.toolResultJson)
  } catch {
    return null
  }

  if (Array.isArray(parsed)) {
    const segments = parsed as LegacyRetrievedSegment[]
    return {
      evidenceLevel: segments.length ? undefined : 'NONE',
      citations: segments.map<AssistantCitation>((segment, index) => ({
        index: index + 1,
        segmentId: segment.segmentId,
        segmentNo: segment.segmentNo,
        pageNo: segment.pageNo,
        sectionTitle: segment.sectionTitle,
        locationText: legacyLocationText(segment),
        contentText: segment.contentText,
        score: segment.score
      }))
    }
  }

  if (parsed && typeof parsed === 'object' && Array.isArray((parsed as AssistantRagEvidence).citations)) {
    return parsed as AssistantRagEvidence
  }
  return null
}
