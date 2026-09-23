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

const sleep = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms))

/**
 * 一直等到任务进入终态（成功/失败/取消）。
 *
 * 后端单次长轮询有时长上限，评分、总结等慢任务可能需要多轮；期间偶发的网络错误会自动重试，
 * 连续失败三次才向调用方抛出。`shouldStop` 返回 true 时提前结束并返回最近一次拿到的任务。
 */
export const waitForAiTaskUntilTerminal = async (
  taskId: number,
  options: { shouldStop?: () => boolean; onProgress?: (task: AiTaskDetail) => void; timeoutMs?: number } = {}
) => {
  const { shouldStop = () => false, onProgress, timeoutMs } = options
  let latest: AiTaskDetail | null = null
  let failures = 0
  while (!shouldStop()) {
    try {
      latest = await waitForAiTask(taskId, timeoutMs)
      failures = 0
      onProgress?.(latest)
      if (isAiTaskTerminal(latest.status)) {
        return latest
      }
    } catch (error) {
      failures += 1
      if (failures >= 3) {
        throw error
      }
      await sleep(3000)
    }
  }
  return latest
}
