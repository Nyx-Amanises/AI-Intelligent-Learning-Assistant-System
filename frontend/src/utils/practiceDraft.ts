import { readStorage, readStorageJson } from '@/utils/storage'

/**
 * 未提交练习的本地草稿。练习页负责写入和清理，首页只读取，用来显示“接着上次”的练习。
 * 草稿按题目 id 保存答案：{ [questionId]: answer }。
 */
export const PRACTICE_DRAFT_KEY_PREFIX = 'ai-learning-assistant:practice-draft:'
export const PRACTICE_ACTIVE_DRAFT_KEY = 'ai-learning-assistant:practice-draft:active'

export const getPracticeDraftKey = (sessionId: number | string) => `${PRACTICE_DRAFT_KEY_PREFIX}${sessionId}`

/** 最近一次留有草稿的练习 id，没有时返回 0。 */
export const readActiveDraftSessionId = () => {
  const sessionId = Number(readStorage(PRACTICE_ACTIVE_DRAFT_KEY))
  return Number.isSafeInteger(sessionId) && sessionId > 0 ? sessionId : 0
}

/** 草稿里的答案，按题目 id 索引。 */
export const readDraftAnswers = (sessionId: number): Record<number, string> => {
  const draft = readStorageJson<Record<string, unknown> | null>(getPracticeDraftKey(sessionId), null)
  if (!draft || typeof draft !== 'object') return {}
  return Object.fromEntries(
    Object.entries(draft)
      .filter(([questionId, value]) => Number.isFinite(Number(questionId)) && value !== null && value !== undefined)
      .map(([questionId, value]) => [Number(questionId), String(value)])
  )
}
