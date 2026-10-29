import type { AssistantToolCall } from '@/api/modules/assistant'
import { toTimeValue } from '@/utils/assistantFormat'

const TOOL_LABELS: Record<string, string> = {
  'practice.detail': '读取练习作答',
  'practice.list': '查找练习记录',
  'material.list': '查找学习资料',
  'material.search': '搜索学习资料',
  'material.detail': '读取资料信息',
  'material.chapter_outline': '整理资料章节',
  'question_set.list': '查找题集',
  'question_set.detail': '读取题集',
  'rag.retrieve': '检索资料依据',
  'task.list': '查询学习任务',
  'task.get_status': '查询任务进度',
  'task.submit_summary': '提交总结任务',
  'task.submit_question_generate': '提交出题任务'
}
const STATUS_LABELS: Record<string, string> = {
  SUCCESS: '已完成',
  FAILED: '失败',
  ERROR: '失败',
  RUNNING: '进行中',
  WAITING: '等待中',
  PENDING: '等待中',
  CANCELLED: '已取消',
  CANCELED: '已取消',
  SKIPPED: '已跳过'
}
export const readableToolName = (tool?: string) => TOOL_LABELS[tool || ''] || '执行工具操作'
export const readableToolStatus = (status?: string) => STATUS_LABELS[String(status || '').toUpperCase()] || '状态未知'
export const toolFailed = (tool: AssistantToolCall) =>
  ['FAILED', 'ERROR'].includes(String(tool.status || '').toUpperCase())
export const clipToolText = (text: string, limit = 320) => (text.length <= limit ? text : `${text.slice(0, limit)}…`)

const parseObject = (value?: string): Record<string, unknown> => {
  if (!value || value.length > 200000) return {}
  try {
    const parsed: unknown = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? (parsed as Record<string, unknown>) : {}
  } catch {
    return {}
  }
}
const readableValue = (value: unknown) => (typeof value === 'string' || typeof value === 'number' ? String(value) : '')
export const toolQueryObject = (tool: AssistantToolCall) => {
  const args = parseObject(tool.toolArgsJson)
  const parts: string[] = []
  for (const [key, label] of [
    ['practiceSessionId', '练习'],
    ['materialId', '资料'],
    ['questionSetId', '题集'],
    ['taskId', '任务']
  ]) {
    const value = readableValue(args[key])
    if (value) parts.push(`${label} #${value}`)
  }
  const query = readableValue(args.queryText || args.keyword || args.materialQuery || args.query)
  if (query) parts.push(`查询：${clipToolText(query, 100)}`)
  return parts.join(' · ')
}
export const toolDuration = (tool: AssistantToolCall) => {
  const start = toTimeValue(tool.startedAt),
    end = toTimeValue(tool.finishedAt)
  if (!start || !end || end < start) return ''
  const ms = end - start
  return ms < 1000 ? `${ms} 毫秒` : `${Math.round(ms / 100) / 10} 秒`
}
export interface ToolPracticeRecord {
  id: number
  label: string
}
export const toolPracticeRecords = (tool: AssistantToolCall): ToolPracticeRecord[] => {
  if (String(tool.status || '').toUpperCase() !== 'SUCCESS') return []
  const result = parseObject(tool.toolResultJson)
  const records =
    tool.toolName === 'practice.list' && Array.isArray(result.records)
      ? result.records
      : tool.toolName === 'practice.detail'
        ? [{ ...result, id: result.sessionId || parseObject(tool.toolArgsJson).practiceSessionId }]
        : []
  const seen = new Set<number>()
  return records.slice(0, 20).flatMap((record: unknown) => {
    if (!record || typeof record !== 'object') return []
    const item = record as Record<string, unknown>
    const id = Number(item.id)
    if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) return []
    seen.add(id)
    const name = readableValue(item.sessionName).trim()
    return [{ id, label: name ? `${clipToolText(name, 80)} · #${id}` : `练习 #${id}` }]
  })
}
export const toolSummary = (tool: AssistantToolCall) => {
  if (tool.summaryText?.trim()) return clipToolText(tool.summaryText.trim())
  if (toolFailed(tool))
    return tool.errorMessage ? clipToolText(tool.errorMessage) : '操作未完成，可查看错误说明后重试。'
  const result = parseObject(tool.toolResultJson)
  if (Array.isArray(result.records))
    return `找到 ${readableValue(result.total) || result.records.length} 条记录，本次返回 ${result.records.length} 条。`
  if (Array.isArray(result.citations)) return `找到 ${result.citations.length} 条资料依据。`
  if (tool.toolName === 'practice.detail' && result.sessionName) {
    const score =
      result.obtainedScore !== undefined && result.totalScore !== undefined
        ? `，得分 ${result.obtainedScore}/${result.totalScore}`
        : ''
    return clipToolText(`已读取「${readableValue(result.sessionName)}」${score}。`)
  }
  if (String(tool.status || '').toUpperCase() === 'SUCCESS') return '操作已完成，展开详情可查看返回结果。'
  return ['RUNNING', 'WAITING', 'PENDING'].includes(String(tool.status || '').toUpperCase())
    ? '正在处理，请稍候。'
    : '展开详情可查看操作记录。'
}
export const toolDetailText = (value?: string) => {
  if (!value) return '未记录'
  const limit = 12000
  let text = value
  if (value.length <= 200000) {
    try {
      text = JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      /* 旧工具可能返回普通文本，仍按文本显示。 */
    }
  }
  return text.length > limit ? `${text.slice(0, limit)}\n…（结果较长，显示前 ${limit} 个字符）` : text
}
