import { waitAiTaskApi, type AiTaskDetail } from '@/api/modules/ai'

const TERMINAL_STATUSES = new Set(['SUCCESS', 'FAILED', 'CANCELLED'])

export const normalizeAiTaskStatus = (status?: string) => (status || '').trim().toUpperCase()

export const isAiTaskTerminal = (status?: string) => TERMINAL_STATUSES.has(normalizeAiTaskStatus(status))

export const isAiTaskSuccess = (status?: string) => normalizeAiTaskStatus(status) === 'SUCCESS'

export const parseAiTaskResult = <T>(task?: Pick<AiTaskDetail, 'resultJson'> | null): T | null => {
  if (!task?.resultJson) {
    return null
  }
  return JSON.parse(task.resultJson) as T
}

export const waitForAiTask = async (taskId: number, timeoutMs = 120000) => {
  const res = await waitAiTaskApi(taskId, timeoutMs)
  return res.data.data as AiTaskDetail
}
