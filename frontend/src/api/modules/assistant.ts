import http, { handleAuthExpired, resolveApiUrl } from '@/api/http'
import { useUserStore } from '@/stores/user'

export interface AssistantSessionCreatePayload {
  title?: string
  contextType?: string
  contextId?: number
  materialId?: number
  questionSetId?: number
  practiceSessionId?: number
}

export interface AssistantMessageSendPayload {
  contentText: string
  modelName?: string
  contextType?: string
  contextId?: number
  materialId?: number
  questionSetId?: number
  practiceSessionId?: number
}

export interface AssistantMessage {
  id: number
  role: string
  messageType?: string
  contentText?: string
  reasoningJson?: string
  toolPlanJson?: string
  modelName?: string
  tokenInput?: number
  tokenOutput?: number
  createdAt?: string
}

export interface AssistantToolCall {
  id: number
  messageId?: number
  toolName?: string
  toolArgsJson?: string
  toolResultJson?: string
  status?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

export interface AssistantRelevantMemory {
  id: number
  memoryScope?: string
  memoryType?: string
  topicName?: string
  summaryText?: string
}

export interface AssistantSessionPageItem {
  id: number
  title: string
  status?: string
  pinned?: number
  currentContextType?: string
  currentContextId?: number
  lastMessagePreview?: string
  lastMessageAt?: string
  createdAt?: string
}

export interface AssistantSessionPagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: AssistantSessionPageItem[]
}

export interface AssistantSessionDetail {
  id: number
  title: string
  status?: string
  pinned?: number
  currentContextType?: string
  currentContextId?: number
  currentMaterialId?: number
  currentQuestionSetId?: number
  currentPracticeSessionId?: number
  lastMessageAt?: string
  createdAt?: string
  updatedAt?: string
  messages: AssistantMessage[]
  recentToolCalls: AssistantToolCall[]
}

export interface AssistantChatReply {
  sessionId: number
  sessionTitle?: string
  userMessage: AssistantMessage
  assistantMessage: AssistantMessage
  toolCalls: AssistantToolCall[]
  usedMemories: AssistantRelevantMemory[]
}

export interface AssistantStreamTracePayload {
  modelName?: string
  reasoningJson?: string
  toolPlanJson?: string
  toolCalls?: AssistantToolCall[]
  usedMemories?: AssistantRelevantMemory[]
}

export type AssistantStreamEvent =
  | {
      type: 'session'
      sessionId: number
      sessionTitle?: string
      userMessage?: AssistantMessage
    }
  | ({
      type: 'trace'
    } & AssistantStreamTracePayload)
  | {
      type: 'delta'
      delta: string
    }
  | {
      type: 'done'
      reply: AssistantChatReply
    }
  | {
      type: 'error'
      message: string
    }

export interface AssistantStreamOptions {
  sessionId: number
  data: AssistantMessageSendPayload
  signal?: AbortSignal
  onEvent: (event: AssistantStreamEvent) => void
}

export const createAssistantSessionApi = (data: AssistantSessionCreatePayload) =>
  http.post('/assistant/sessions', data)

export const getAssistantSessionPageApi = (params: Record<string, unknown>) =>
  http.get('/assistant/sessions/page', { params })

export const getAssistantSessionDetailApi = (sessionId: number, messageLimit = 30) =>
  http.get(`/assistant/sessions/${sessionId}`, {
    params: { messageLimit }
  })

export const sendAssistantMessageApi = (sessionId: number, data: AssistantMessageSendPayload) =>
  http.post(`/assistant/sessions/${sessionId}/messages`, data)

export const deleteAssistantSessionApi = (sessionId: number) =>
  http.delete(`/assistant/sessions/${sessionId}`)

export const streamAssistantMessageApi = async (options: AssistantStreamOptions) => {
  const userStore = useUserStore()
  const response = await fetch(resolveApiUrl(`/assistant/sessions/${options.sessionId}/messages/stream`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {})
    },
    body: JSON.stringify(options.data),
    signal: options.signal
  })

  if (!response.ok && response.status === 401) {
    const rawText = await response.text()
    let message = '\u6d41\u5f0f\u8bf7\u6c42\u5931\u8d25'

    try {
      const parsed = JSON.parse(rawText)
      message = parsed.message || message
    } catch {
      message = rawText || message
    }

    throw handleAuthExpired(message)
  }

  if (!response.ok) {
    const rawText = await response.text()
    try {
      const parsed = JSON.parse(rawText)
      throw new Error(parsed.message || '流式请求失败')
    } catch {
      throw new Error(rawText || '流式请求失败')
    }
  }

  if (!response.body) {
    throw new Error('浏览器未返回流式响应体')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const processEventBlock = (block: string) => {
    const dataLines = block
      .split('\n')
      .map((line) => line.trimEnd())
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim())

    if (!dataLines.length) {
      return
    }

    const payloadText = dataLines.join('\n')
    if (!payloadText) {
      return
    }

    const payload = JSON.parse(payloadText) as AssistantStreamEvent
    options.onEvent(payload)
  }

  const flushBuffer = () => {
    buffer = buffer.replace(/\r\n/g, '\n')
    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex >= 0) {
      const block = buffer.slice(0, separatorIndex).trim()
      buffer = buffer.slice(separatorIndex + 2)
      if (block) {
        processEventBlock(block)
      }
      separatorIndex = buffer.indexOf('\n\n')
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    flushBuffer()
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    processEventBlock(buffer.trim())
  }
}
