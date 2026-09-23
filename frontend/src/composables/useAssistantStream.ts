import { computed, nextTick, onBeforeUnmount, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  type AssistantChatReply,
  type AssistantMessage,
  type AssistantRelevantMemory,
  type AssistantSessionDetail,
  type AssistantStreamEvent,
  type AssistantStreamTracePayload,
  type AssistantToolCall,
  streamAssistantMessageApi
} from '@/api/modules/assistant'
import { getErrorMessage } from '@/utils/errorMessage'
import { buildLocalDateTime, isUserMessage, toTimeValue } from '@/utils/assistantFormat'
import { mergeUniqueById } from '@/composables/useAssistantSessions'

export interface PendingTurnState {
  sessionId: number
  optimisticUserId: number
  actualUserId?: number
  assistantMessageId: number
}

export interface AssistantStreamOptions {
  sessionDetail: Ref<AssistantSessionDetail | null>
  activeSessionId: Ref<number | null>
  sessionPage: { current: number }
  loadSessionPage: (selectFallback: boolean) => Promise<void>
  loadSessionDetail: (sessionId: number) => Promise<void>
  createSession: (
    bindCurrentPage: boolean,
    silent?: boolean,
    allowWhileSending?: boolean
  ) => Promise<AssistantSessionDetail | null>
  /** 首条消息自动建会话时是否绑定当前页面。 */
  shouldBindCurrentPage: () => boolean
  /** 请求消息列表滚动到底部；`force` 表示忽略"用户正在上翻"的判定。 */
  requestScroll: (force?: boolean) => void
}

const EMPTY_TOOL_CALLS: AssistantToolCall[] = []
const EMPTY_MEMORIES: AssistantRelevantMemory[] = []

/** 发送消息与流式回复：乐观消息、增量渲染、停止 / 超时收尾、工具调用与记忆轨迹。 */
export const useAssistantStream = (options: AssistantStreamOptions) => {
  const { sessionDetail, activeSessionId, sessionPage } = options

  const draftMessage = ref('')
  const draftCount = computed(() => draftMessage.value.length)
  const sendingMessage = ref(false)
  const pendingTurn = ref<PendingTurnState | null>(null)
  const toolCallMap = ref<Record<number, AssistantToolCall[]>>({})
  const memoryMap = ref<Record<number, AssistantRelevantMemory[]>>({})
  /** 供读屏软件播报的整轮结果（避免逐字播报流式内容）。 */
  const liveAnnouncement = ref('')
  let streamAbortController: AbortController | null = null

  /** 正在流式生成的助手消息 id；没有生成中的回复时为 null。 */
  const streamingMessageId = computed(() =>
    sendingMessage.value && pendingTurn.value ? pendingTurn.value.assistantMessageId : null
  )

  /**
   * 助手消息 id -> 该轮对应的工具调用（挂在它前面最近一条用户消息上）。
   * 在消息列表或工具调用映射变化时只计算一次，模板里不再逐条回溯扫描。
   */
  const relatedToolCallMap = computed(() => {
    const map = new Map<number, AssistantToolCall[]>()
    const messages = sessionDetail.value?.messages || []
    let lastUserMessageId: number | undefined
    for (const message of messages) {
      if (isUserMessage(message.role)) {
        lastUserMessageId = message.id
        continue
      }
      const toolCalls = lastUserMessageId ? toolCallMap.value[lastUserMessageId] : undefined
      if (toolCalls?.length) {
        map.set(message.id, toolCalls)
      }
    }
    return map
  })

  const getRelatedToolCalls = (messageId: number) => relatedToolCallMap.value.get(messageId) || EMPTY_TOOL_CALLS

  const getMemories = (messageId?: number) => (messageId ? memoryMap.value[messageId] || EMPTY_MEMORIES : EMPTY_MEMORIES)

  const ingestToolCalls = (toolCalls: AssistantToolCall[]) => {
    if (!toolCalls.length) {
      return
    }
    const nextMap = { ...toolCallMap.value }
    toolCalls.forEach((toolCall) => {
      const messageId = Number(toolCall.messageId || 0)
      if (!messageId) {
        return
      }
      const currentCalls = nextMap[messageId] ? [...nextMap[messageId]] : []
      const existingIndex = currentCalls.findIndex((item) => item.id === toolCall.id)
      if (existingIndex >= 0) {
        currentCalls[existingIndex] = toolCall
      } else {
        currentCalls.push(toolCall)
      }
      nextMap[messageId] = currentCalls.sort((a, b) => toTimeValue(a.createdAt) - toTimeValue(b.createdAt))
    })
    toolCallMap.value = nextMap
  }

  const resetStreamState = () => {
    draftMessage.value = ''
    pendingTurn.value = null
    toolCallMap.value = {}
    memoryMap.value = {}
  }

  const stopStreaming = () => {
    streamAbortController?.abort()
  }

  /** 在切换会话、新建会话等操作前中止正在进行的回复，避免旧回复写入新界面。 */
  const abortStreamingIfNeeded = () => {
    if (sendingMessage.value) {
      stopStreaming()
    }
  }

  // ---- 乐观消息辅助 ----

  const updateSessionMessages = (updater: (messages: AssistantMessage[]) => AssistantMessage[]) => {
    if (!sessionDetail.value) {
      return
    }
    sessionDetail.value = {
      ...sessionDetail.value,
      messages: updater(sessionDetail.value.messages)
    }
  }

  const replaceMessage = (fromId: number, nextMessage: AssistantMessage) => {
    let replaced = false
    updateSessionMessages((messages) => {
      const nextMessages = messages.map((message) => {
        if (message.id !== fromId) {
          return message
        }
        replaced = true
        return {
          ...message,
          ...nextMessage
        }
      })
      return replaced ? nextMessages : [...nextMessages, nextMessage]
    })
  }

  const updateMessage = (messageId: number, updater: (message: AssistantMessage) => AssistantMessage) => {
    updateSessionMessages((messages) =>
      messages.map((message) => (message.id === messageId ? updater(message) : message))
    )
  }

  const removeMessagesById = (messageIds: number[]) => {
    const removableIds = new Set(messageIds)
    updateSessionMessages((messages) => messages.filter((message) => !removableIds.has(message.id)))
  }

  const moveToolCallPreview = (fromMessageId: number, toMessageId: number) => {
    if (fromMessageId === toMessageId) {
      return
    }
    const previewToolCalls = toolCallMap.value[fromMessageId]
    if (!previewToolCalls?.length) {
      return
    }
    const nextMap = { ...toolCallMap.value }
    nextMap[toMessageId] = previewToolCalls
    delete nextMap[fromMessageId]
    toolCallMap.value = nextMap
  }

  const clearPendingPreviewArtifacts = (state: PendingTurnState | null) => {
    if (!state) {
      return
    }
    const nextToolCallMap = { ...toolCallMap.value }
    delete nextToolCallMap[state.optimisticUserId]
    if (state.actualUserId) {
      delete nextToolCallMap[state.actualUserId]
    }
    toolCallMap.value = nextToolCallMap

    const nextMemoryMap = { ...memoryMap.value }
    delete nextMemoryMap[state.assistantMessageId]
    memoryMap.value = nextMemoryMap
  }

  const buildOptimisticMessage = (
    id: number,
    role: 'USER' | 'ASSISTANT',
    contentText: string
  ): AssistantMessage => ({
    id,
    role,
    messageType: 'TEXT',
    contentText,
    createdAt: buildLocalDateTime()
  })

  const decoratePreviewToolCalls = (toolCalls: AssistantToolCall[], seed: number) =>
    toolCalls.map((toolCall, index) => {
      const rawId = Number((toolCall as Partial<AssistantToolCall>).id || 0)
      return {
        ...toolCall,
        id: rawId > 0 ? rawId : -(Math.abs(seed) * 1000 + index + 1)
      }
    })

  // ---- 流式事件 ----

  const applyStreamSessionEvent = (event: Extract<AssistantStreamEvent, { type: 'session' }>) => {
    const currentPendingTurn = pendingTurn.value
    if (!currentPendingTurn || !sessionDetail.value || sessionDetail.value.id !== event.sessionId) {
      return
    }

    sessionDetail.value = {
      ...sessionDetail.value,
      title: event.sessionTitle || sessionDetail.value.title
    }
    activeSessionId.value = event.sessionId

    if (event.userMessage) {
      replaceMessage(currentPendingTurn.optimisticUserId, event.userMessage)
      moveToolCallPreview(currentPendingTurn.optimisticUserId, event.userMessage.id)
      pendingTurn.value = {
        ...currentPendingTurn,
        actualUserId: event.userMessage.id
      }
    }
  }

  const applyStreamTraceEvent = (tracePayload: AssistantStreamTracePayload) => {
    const currentPendingTurn = pendingTurn.value
    if (!currentPendingTurn) {
      return
    }

    updateMessage(currentPendingTurn.assistantMessageId, (message) => ({
      ...message,
      modelName: tracePayload.modelName || message.modelName,
      reasoningJson: tracePayload.reasoningJson || message.reasoningJson,
      toolPlanJson: tracePayload.toolPlanJson || message.toolPlanJson
    }))

    if (tracePayload.usedMemories?.length) {
      memoryMap.value = {
        ...memoryMap.value,
        [currentPendingTurn.assistantMessageId]: tracePayload.usedMemories
      }
    }

    if (tracePayload.toolCalls?.length) {
      const messageId = currentPendingTurn.actualUserId || currentPendingTurn.optimisticUserId
      toolCallMap.value = {
        ...toolCallMap.value,
        [messageId]: decoratePreviewToolCalls(tracePayload.toolCalls, currentPendingTurn.assistantMessageId)
      }
    }
  }

  const appendAssistantDelta = (delta: string) => {
    const currentPendingTurn = pendingTurn.value
    if (!currentPendingTurn || !delta) {
      return
    }
    updateMessage(currentPendingTurn.assistantMessageId, (message) => ({
      ...message,
      contentText: `${message.contentText || ''}${delta}`
    }))
    void nextTick(() => options.requestScroll())
  }

  const renderAssistantError = (message: string) => {
    const currentPendingTurn = pendingTurn.value
    if (!currentPendingTurn) {
      return
    }
    const errorText = `这次调用失败了：${message || '助手流式生成失败'}`
    updateMessage(currentPendingTurn.assistantMessageId, (assistantMessage) => ({
      ...assistantMessage,
      contentText: errorText
    }))
    void nextTick(() => options.requestScroll())
  }

  const applyReply = (reply: AssistantChatReply) => {
    if (!sessionDetail.value || sessionDetail.value.id !== reply.sessionId) {
      sessionDetail.value = {
        id: reply.sessionId,
        title: reply.sessionTitle || '新对话',
        messages: [reply.userMessage, reply.assistantMessage],
        recentToolCalls: reply.toolCalls || []
      } as AssistantSessionDetail
    } else {
      sessionDetail.value = {
        ...sessionDetail.value,
        title: reply.sessionTitle || sessionDetail.value.title,
        messages: mergeUniqueById(sessionDetail.value.messages, [reply.userMessage, reply.assistantMessage]),
        recentToolCalls: mergeUniqueById(sessionDetail.value.recentToolCalls, reply.toolCalls || []),
        lastMessageAt: reply.assistantMessage.createdAt || sessionDetail.value.lastMessageAt
      }
    }

    ingestToolCalls(reply.toolCalls || [])

    if (reply.assistantMessage?.id && reply.usedMemories?.length) {
      memoryMap.value = {
        ...memoryMap.value,
        [reply.assistantMessage.id]: reply.usedMemories
      }
    }
    void nextTick(() => options.requestScroll())
  }

  /**
   * 停止或超时后收尾：保留已经生成的部分并标注状态；一个字都没生成时直接移除空气泡。
   */
  const finishInterruptedTurn = (status: 'stopped' | 'timeout') => {
    const currentPendingTurn = pendingTurn.value
    pendingTurn.value = null
    liveAnnouncement.value = status === 'timeout' ? '助手长时间没有响应，已停止等待' : '已停止生成'
    if (!currentPendingTurn) {
      return
    }
    const assistantMessage = sessionDetail.value?.messages.find(
      (message) => message.id === currentPendingTurn.assistantMessageId
    )
    if (!assistantMessage?.contentText) {
      removeMessagesById([currentPendingTurn.assistantMessageId])
      return
    }
    updateMessage(currentPendingTurn.assistantMessageId, (message) => ({
      ...message,
      localStatus: status
    }))
  }

  const sendMessage = async () => {
    const contentText = draftMessage.value.trim()
    if (!contentText) {
      ElMessage.warning('先输入你的问题再发送')
      return
    }
    if (sendingMessage.value) {
      return
    }

    const previousDraft = draftMessage.value
    let shouldRestoreDraft = true
    let detail = sessionDetail.value
    let controller: AbortController | null = null

    try {
      if (!detail?.id) {
        detail = await options.createSession(options.shouldBindCurrentPage(), true, true)
      }

      if (!detail?.id) {
        throw new Error('创建会话失败')
      }

      sendingMessage.value = true
      const optimisticUserId = -Date.now()
      const assistantMessageId = optimisticUserId - 1
      pendingTurn.value = {
        sessionId: detail.id,
        optimisticUserId,
        assistantMessageId
      }
      shouldRestoreDraft = false

      sessionDetail.value = {
        ...detail,
        messages: [
          ...detail.messages,
          buildOptimisticMessage(optimisticUserId, 'USER', contentText),
          buildOptimisticMessage(assistantMessageId, 'ASSISTANT', '')
        ],
        lastMessageAt: buildLocalDateTime()
      }

      draftMessage.value = ''
      liveAnnouncement.value = ''
      void nextTick(() => options.requestScroll(true))
      streamAbortController?.abort()
      controller = new AbortController()
      streamAbortController = controller

      let streamErrorMessage = ''
      let finalReply: AssistantChatReply | null = null

      await streamAssistantMessageApi({
        sessionId: detail.id,
        data: { contentText },
        signal: controller.signal,
        onEvent: (event) => {
          switch (event.type) {
            case 'session':
              applyStreamSessionEvent(event)
              break
            case 'trace':
              applyStreamTraceEvent(event)
              break
            case 'delta':
              appendAssistantDelta(event.delta)
              break
            case 'done':
              finalReply = event.reply
              break
            case 'error':
              streamErrorMessage = event.message || '助手流式生成失败'
              renderAssistantError(streamErrorMessage)
              break
          }
        }
      })

      if (streamErrorMessage) {
        pendingTurn.value = null
        await options.loadSessionPage(false)
        return
      }
      if (!finalReply) {
        throw new Error('助手没有返回完整结果')
      }

      const completedPendingTurn = pendingTurn.value
      clearPendingPreviewArtifacts(completedPendingTurn)
      removeMessagesById(
        [completedPendingTurn?.optimisticUserId, completedPendingTurn?.assistantMessageId].filter(
          (value): value is number => Number.isFinite(value)
        )
      )
      pendingTurn.value = null
      applyReply(finalReply)
      liveAnnouncement.value = '学习助手已回复完成'
      sessionPage.current = 1
      await options.loadSessionPage(false)
      if (sessionDetail.value?.messages.some((message) => message.id < 0)) {
        // 之前被停止的回复只存在于本地，这里用服务端记录把它们换成正式消息。
        await options.loadSessionDetail(sessionDetail.value.id)
      }
    } catch (error: any) {
      if (error?.name === 'AbortError' || error?.name === 'TimeoutError') {
        finishInterruptedTurn(error?.name === 'TimeoutError' ? 'timeout' : 'stopped')
        if (error?.name === 'TimeoutError') {
          ElMessage.warning('助手长时间没有响应，已停止等待')
        }
        return
      }

      if (shouldRestoreDraft) {
        draftMessage.value = previousDraft
      }

      const currentPendingTurn = pendingTurn.value
      if (currentPendingTurn) {
        clearPendingPreviewArtifacts(currentPendingTurn)
        removeMessagesById([currentPendingTurn.assistantMessageId])
        if (!currentPendingTurn.actualUserId) {
          removeMessagesById([currentPendingTurn.optimisticUserId])
        }
      }

      if (detail?.id) {
        try {
          await options.loadSessionDetail(detail.id)
        } catch {
          // 忽略详情同步失败，保留当前界面并直接提示错误。
        }
      }

      ElMessage.error(getErrorMessage(error, '发送消息失败'))
    } finally {
      pendingTurn.value = null
      if (streamAbortController === controller) {
        streamAbortController = null
      }
      sendingMessage.value = false
    }
  }

  onBeforeUnmount(() => {
    streamAbortController?.abort()
    pendingTurn.value = null
  })

  return {
    draftMessage,
    draftCount,
    sendingMessage,
    pendingTurn,
    liveAnnouncement,
    toolCallMap,
    memoryMap,
    streamingMessageId,
    relatedToolCallMap,
    getRelatedToolCalls,
    getMemories,
    ingestToolCalls,
    resetStreamState,
    sendMessage,
    stopStreaming,
    abortStreamingIfNeeded
  }
}
