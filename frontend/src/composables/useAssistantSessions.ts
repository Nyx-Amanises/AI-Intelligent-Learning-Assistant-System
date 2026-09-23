import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type AssistantSessionCreatePayload,
  type AssistantSessionDetail,
  type AssistantSessionPageItem,
  createAssistantSessionApi,
  deleteAssistantSessionApi,
  getAssistantSessionDetailApi,
  getAssistantSessionPageApi,
  renameAssistantSessionApi,
  updateAssistantSessionPinnedApi
} from '@/api/modules/assistant'
import { getErrorMessage } from '@/utils/errorMessage'
import { useLatestRequest } from '@/composables/useLatestRequest'

export type AssistantSessionCommand = 'rename' | 'pin' | 'unpin' | 'delete'

export interface AssistantSessionsOptions {
  /** 返回"按当前页面创建"时要使用的载荷；不可绑定时返回 null。 */
  resolveBoundPayload: () => AssistantSessionCreatePayload | null
  /** 切换 / 新建 / 删除会话前调用，用于中止正在进行的流式回复。 */
  beforeSwitch: () => void
  /** 判断某个会话是否正有回复在生成。 */
  isStreamingSession: (sessionId: number) => boolean
  /** 会话详情加载完成后的回调（用于把工具调用记录并入轨迹映射）。 */
  onDetailLoaded: (detail: AssistantSessionDetail) => void
  /** 选中 / 新建会话后的回调（移动端用于收起侧栏）。 */
  afterSelect: () => void
}

export const normalizeSessionDetail = (detail: AssistantSessionDetail): AssistantSessionDetail => ({
  ...detail,
  messages: detail.messages || [],
  recentToolCalls: detail.recentToolCalls || []
})

export const mergeUniqueById = <T extends { id: number }>(current: T[], incoming: T[]) => {
  const merged = [...current]
  incoming.forEach((item) => {
    const index = merged.findIndex((currentItem) => currentItem.id === item.id)
    if (index >= 0) {
      merged[index] = item
    } else {
      merged.push(item)
    }
  })
  return merged
}

/** 助手会话列表与当前会话详情的加载、创建、重命名、置顶、删除。 */
export const useAssistantSessions = (options: AssistantSessionsOptions) => {
  const sessionPage = reactive({
    current: 1,
    size: 100,
    total: 0,
    pages: 0,
    records: [] as AssistantSessionPageItem[]
  })
  const sessionPageLoading = ref(false)
  const creatingBlank = ref(false)
  const deletingSessionId = ref<number | null>(null)
  const renamingSessionId = ref<number | null>(null)
  const pinningSessionId = ref<number | null>(null)
  const activeSessionId = ref<number | null>(null)
  const sessionDetail = ref<AssistantSessionDetail | null>(null)
  const sessionDetailRequest = useLatestRequest()

  const sessionActionBusy = (sessionId: number) =>
    deletingSessionId.value === sessionId ||
    renamingSessionId.value === sessionId ||
    pinningSessionId.value === sessionId

  /** 用服务端返回的会话对象同步当前详情与列表项。 */
  const syncSessionDetail = (detail: AssistantSessionDetail) => {
    const normalizedDetail = normalizeSessionDetail(detail)
    if (sessionDetail.value?.id === normalizedDetail.id) {
      sessionDetail.value = {
        ...sessionDetail.value,
        ...normalizedDetail
      }
    }
    sessionPage.records = sessionPage.records.map((item) =>
      item.id === normalizedDetail.id
        ? {
            ...item,
            title: normalizedDetail.title,
            status: normalizedDetail.status,
            pinned: normalizedDetail.pinned,
            currentContextType: normalizedDetail.currentContextType,
            currentContextId: normalizedDetail.currentContextId,
            lastMessageAt: normalizedDetail.lastMessageAt,
            createdAt: normalizedDetail.createdAt
          }
        : item
    )
    return normalizedDetail
  }

  const resetSessionState = () => {
    activeSessionId.value = null
    sessionDetail.value = null
  }

  const loadSessionDetail = async (sessionId: number) => {
    const requestId = sessionDetailRequest.begin()
    try {
      const res = await getAssistantSessionDetailApi(sessionId, 30)
      if (!sessionDetailRequest.isLatest(requestId)) return
      const detail = normalizeSessionDetail(res.data.data as AssistantSessionDetail)
      activeSessionId.value = sessionId
      sessionDetail.value = detail
      options.onDetailLoaded(detail)
    } catch (error: any) {
      if (!sessionDetailRequest.isLatest(requestId)) return
      ElMessage.error(getErrorMessage(error, '加载助手会话详情失败'))
    }
  }

  const selectSession = async (sessionId: number) => {
    if (sessionDetail.value?.id === sessionId) {
      activeSessionId.value = sessionId
      options.afterSelect()
      return
    }
    options.beforeSwitch()
    await loadSessionDetail(sessionId)
    options.afterSelect()
  }

  const loadSessionPage = async (selectFallback: boolean) => {
    sessionPageLoading.value = true
    try {
      const res = await getAssistantSessionPageApi({
        current: sessionPage.current,
        size: sessionPage.size
      })
      const data = res.data.data
      sessionPage.current = data.current || sessionPage.current
      sessionPage.size = data.size || sessionPage.size
      sessionPage.total = data.total || 0
      sessionPage.pages = data.pages || 0
      sessionPage.records = data.records || []

      if (!sessionPage.records.length) {
        if (selectFallback) {
          resetSessionState()
        }
        return
      }

      const existsInPage = activeSessionId.value
        ? sessionPage.records.some((item) => item.id === activeSessionId.value)
        : false

      if (existsInPage) {
        if (!sessionDetail.value || sessionDetail.value.id !== activeSessionId.value) {
          await loadSessionDetail(activeSessionId.value as number)
        }
        return
      }

      if (selectFallback) {
        await selectSession(sessionPage.records[0].id)
      }
    } catch (error: any) {
      ElMessage.error(getErrorMessage(error, '加载助手会话失败'))
    } finally {
      sessionPageLoading.value = false
    }
  }

  /**
   * 创建会话。`bindCurrentPage` 为 true 且当前页面可绑定时带上页面上下文；
   * `allowWhileSending` 供发送流程使用：首条消息前新建会话时不中止自身。
   */
  const createSession = async (bindCurrentPage: boolean, silent = false, allowWhileSending = false) => {
    if (!allowWhileSending) {
      options.beforeSwitch()
    }
    const boundPayload = bindCurrentPage ? options.resolveBoundPayload() : null
    if (!bindCurrentPage) {
      creatingBlank.value = true
    }
    try {
      const res = await createAssistantSessionApi(boundPayload || {})
      const detail = normalizeSessionDetail(res.data.data as AssistantSessionDetail)
      activeSessionId.value = detail.id
      sessionDetail.value = detail
      sessionPage.current = 1
      await loadSessionPage(false)
      options.afterSelect()
      if (!silent) {
        ElMessage.success(boundPayload ? '已按当前页面创建会话' : '新会话已创建')
      }
      return detail
    } catch (error: any) {
      ElMessage.error(getErrorMessage(error, '创建助手会话失败'))
      return null
    } finally {
      if (!bindCurrentPage) {
        creatingBlank.value = false
      }
    }
  }

  const renameSession = async (item: AssistantSessionPageItem) => {
    let nextTitle = ''
    try {
      const result = await ElMessageBox.prompt('请输入新的对话名称', '重命名对话', {
        inputValue: item.title || '新对话',
        inputPlaceholder: '请输入新的对话名称',
        inputValidator: (value) => {
          const title = String(value || '').trim()
          if (!title) {
            return '会话名称不能为空'
          }
          if (title.length > 200) {
            return '会话名称不能超过 200 个字符'
          }
          return true
        },
        confirmButtonText: '保存',
        cancelButtonText: '取消'
      })
      nextTitle = String(result.value || '').trim()
    } catch (error) {
      if (error === 'cancel' || error === 'close') {
        return
      }
      ElMessage.error('打开重命名窗口失败')
      return
    }

    if (!nextTitle || nextTitle === (item.title || '').trim()) {
      return
    }

    renamingSessionId.value = item.id
    try {
      const res = await renameAssistantSessionApi(item.id, nextTitle)
      syncSessionDetail(res.data.data as AssistantSessionDetail)
      await loadSessionPage(false)
      ElMessage.success('会话名称已更新')
    } catch (error: any) {
      ElMessage.error(getErrorMessage(error, '修改会话名称失败'))
    } finally {
      renamingSessionId.value = null
    }
  }

  const updateSessionPinned = async (item: AssistantSessionPageItem, pinned: boolean) => {
    pinningSessionId.value = item.id
    try {
      const res = await updateAssistantSessionPinnedApi(item.id, pinned)
      syncSessionDetail(res.data.data as AssistantSessionDetail)
      await loadSessionPage(false)
      ElMessage.success(pinned ? '已置顶聊天' : '已取消置顶')
    } catch (error: any) {
      ElMessage.error(getErrorMessage(error, '修改置顶状态失败'))
    } finally {
      pinningSessionId.value = null
    }
  }

  const removeSession = async (sessionId: number) => {
    try {
      await ElMessageBox.confirm('删除后该会话中的聊天记录、工具调用和上下文都将一起移除，确定继续吗？', '删除确认', {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }

    if (options.isStreamingSession(sessionId)) {
      options.beforeSwitch()
    }
    deletingSessionId.value = sessionId
    try {
      await deleteAssistantSessionApi(sessionId)
      if (sessionPage.records.length === 1 && sessionPage.current > 1) {
        sessionPage.current -= 1
      }
      if (activeSessionId.value === sessionId) {
        resetSessionState()
      }
      await loadSessionPage(true)
      ElMessage.success('会话已删除')
    } catch (error: any) {
      ElMessage.error(getErrorMessage(error, '删除会话失败'))
    } finally {
      deletingSessionId.value = null
    }
  }

  const handleSessionCommand = (item: AssistantSessionPageItem, command: AssistantSessionCommand) => {
    if (command === 'rename') {
      void renameSession(item)
      return
    }
    if (command === 'pin' || command === 'unpin') {
      void updateSessionPinned(item, command === 'pin')
      return
    }
    if (command === 'delete') {
      void removeSession(item.id)
    }
  }

  return {
    sessionPage,
    sessionPageLoading,
    creatingBlank,
    activeSessionId,
    sessionDetail,
    sessionActionBusy,
    syncSessionDetail,
    resetSessionState,
    loadSessionPage,
    loadSessionDetail,
    selectSession,
    createSession,
    renameSession,
    updateSessionPinned,
    removeSession,
    handleSessionCommand
  }
}
