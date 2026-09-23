import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { AssistantSessionCreatePayload } from '@/api/modules/assistant'

/**
 * 当前页面可绑定到助手会话的上下文。
 * SummaryView / QuizView 会把正在查看的资料或题集写进路由 query，
 * 这里读出来生成"按当前页面创建会话"所需的载荷。
 */
export interface CurrentPageBinding {
  bindable: boolean
  label: string
  helperText: string
  payload: AssistantSessionCreatePayload
}

export const ASSISTANT_MATERIAL_QUERY_KEY = 'assistantMaterialId'
export const ASSISTANT_QUESTION_SET_QUERY_KEY = 'assistantQuestionSetId'

const GENERIC_BINDING: CurrentPageBinding = {
  bindable: false,
  label: '通用对话',
  helperText: '整理资料、解答问题，陪你把知识学扎实。',
  payload: {}
}

const resolveRouteLabel = (path: string) => {
  switch (path) {
    case '/dashboard':
      return '首页'
    case '/materials':
      return '资料管理'
    case '/summary':
      return 'AI 总结'
    case '/quiz':
      return 'AI 出题'
    case '/practice':
      return '练习记录'
    case '/ai-tasks':
      return '任务中心'
    case '/ai-config':
      return 'AI 配置'
    default:
      return '当前页面'
  }
}

const toNumeric = (value: unknown): number | undefined => {
  if (Array.isArray(value)) {
    return toNumeric(value[0])
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

export const useAssistantPageBinding = () => {
  const route = useRoute()

  const resolveContextQueryId = (...keys: string[]) => {
    for (const key of keys) {
      const resolved = toNumeric(route.query[key])
      if (resolved) {
        return resolved
      }
    }
    return undefined
  }

  const currentBinding = computed<CurrentPageBinding>(() => {
    const questionSetId = resolveContextQueryId(ASSISTANT_QUESTION_SET_QUERY_KEY)
    const materialId = resolveContextQueryId(ASSISTANT_MATERIAL_QUERY_KEY)
    const pageLabel = resolveRouteLabel(route.path)

    if (questionSetId) {
      return {
        bindable: true,
        label: `题集 #${questionSetId}`,
        helperText: `已关联「${pageLabel}」页面正在查看的题集，从这里发起的对话会带上这套题的上下文。`,
        payload: {
          contextType: 'QUESTION_SET',
          questionSetId,
          ...(materialId ? { materialId } : {})
        }
      }
    }

    if (materialId) {
      return {
        bindable: true,
        label: `资料 #${materialId}`,
        helperText: `已关联「${pageLabel}」页面正在查看的资料，从这里发起的对话会带上这份资料的上下文。`,
        payload: {
          contextType: 'MATERIAL',
          materialId
        }
      }
    }

    return GENERIC_BINDING
  })

  return { currentBinding }
}
