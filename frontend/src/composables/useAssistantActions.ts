import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import {
  decideAssistantActionApi,
  getAssistantPendingActionApi,
  type AssistantAction,
  type AssistantActionDecision
} from '@/api/modules/assistant'
import { getErrorMessage } from '@/utils/errorMessage'
import { toTimeValue } from '@/utils/assistantFormat'

export const useAssistantActions = (options: { sessionId: Ref<number | null>; sending: Ref<boolean> }) => {
  const action = ref<AssistantAction | null>(null)
  const loading = ref(false)
  const error = ref('')
  const pendingDecisions = ref<Record<string, AssistantActionDecision>>({})
  const completedBySession = new Map<number, AssistantAction>()
  let requestSequence = 0
  let mounted = true

  const busy = computed(() => (action.value ? pendingDecisions.value[action.value.actionId] || null : null))
  const normalize = (value: AssistantAction): AssistantAction => ({
    ...value,
    tasks: Array.isArray(value.tasks) ? value.tasks : [],
    taskIds: Array.isArray(value.taskIds) ? value.taskIds : []
  })

  const refresh = async () => {
    const sessionId = options.sessionId.value
    if (!sessionId || busy.value) return
    const sequence = ++requestSequence
    loading.value = true
    error.value = ''
    try {
      const response = await getAssistantPendingActionApi(sessionId)
      if (!mounted || sequence !== requestSequence || options.sessionId.value !== sessionId) return
      const data = response.data.data as AssistantAction | null
      if (data && data.sessionId !== sessionId) throw new Error('任务预览与当前会话不一致，请重新加载。')
      action.value = data ? normalize(data) : completedBySession.get(sessionId) || null
    } catch (cause) {
      if (!mounted || sequence !== requestSequence || options.sessionId.value !== sessionId) return
      error.value = getErrorMessage(cause, '任务确认状态暂时无法加载，请重试。')
    } finally {
      if (sequence === requestSequence) loading.value = false
    }
  }

  const decide = async (decision: AssistantActionDecision) => {
    const snapshot = action.value
    const sessionId = options.sessionId.value
    if (
      !snapshot ||
      !sessionId ||
      snapshot.sessionId !== sessionId ||
      busy.value ||
      loading.value ||
      options.sending.value
    )
      return
    if (snapshot.status !== 'PENDING') return
    const expiry = toTimeValue(snapshot.expiresAt)
    if (expiry && expiry <= Date.now()) {
      action.value = { ...snapshot, status: 'EXPIRED' }
      return
    }
    requestSequence += 1
    error.value = ''
    pendingDecisions.value = { ...pendingDecisions.value, [snapshot.actionId]: decision }
    try {
      // 任务参数只由已保存的预览决定；客户端仅提交动作版本和决定。
      const response = await decideAssistantActionApi(sessionId, snapshot.actionId, snapshot.revision, decision)
      const result = normalize(response.data.data as AssistantAction)
      if (result.sessionId !== sessionId || result.actionId !== snapshot.actionId)
        throw new Error('未能核对本次任务结果，请重新加载状态。')
      completedBySession.set(sessionId, result)
      if (!mounted || options.sessionId.value !== sessionId || action.value?.actionId !== snapshot.actionId) return
      requestSequence += 1
      action.value = result
      loading.value = false
      error.value = ''
    } catch (cause) {
      if (!mounted || options.sessionId.value !== sessionId || action.value?.actionId !== snapshot.actionId) return
      error.value = `${getErrorMessage(cause, '未能取得处理结果。')} 可重试本次操作，或重新加载确认状态。`
    } finally {
      const next = { ...pendingDecisions.value }
      delete next[snapshot.actionId]
      pendingDecisions.value = next
    }
  }

  watch(
    options.sessionId,
    (sessionId) => {
      requestSequence += 1
      loading.value = false
      error.value = ''
      action.value = sessionId ? completedBySession.get(sessionId) || null : null
      if (sessionId) void refresh()
    },
    { immediate: true }
  )
  watch(options.sending, (sending, wasSending) => {
    if (wasSending && !sending) void refresh()
  })
  onBeforeUnmount(() => {
    mounted = false
    requestSequence += 1
  })

  return { action, loading, error, busy, refresh, decide }
}
