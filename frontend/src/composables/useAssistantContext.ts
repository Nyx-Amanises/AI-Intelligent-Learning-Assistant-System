import { useRoute, useRouter } from 'vue-router'

export const ASSISTANT_MATERIAL_QUERY_KEY = 'assistantMaterialId'
export const ASSISTANT_QUESTION_SET_QUERY_KEY = 'assistantQuestionSetId'

/**
 * 把当前页面正在查看的资料 / 题集写进路由 query，学习助手据此提供"按当前页面创建会话"。
 * 使用 replace，不产生历史记录；值未变化时不触发路由更新。
 */
export const useAssistantContext = () => {
  const route = useRoute()
  const router = useRouter()

  const readId = (key: string) => Number(route.query[key] || 0) || undefined

  const sync = async (context: { materialId?: number; questionSetId?: number }) => {
    const nextMaterialId = context.materialId || undefined
    const nextQuestionSetId = context.questionSetId || undefined
    if (
      readId(ASSISTANT_MATERIAL_QUERY_KEY) === nextMaterialId &&
      readId(ASSISTANT_QUESTION_SET_QUERY_KEY) === nextQuestionSetId
    ) {
      return
    }
    const nextQuery = { ...route.query }
    if (nextMaterialId) nextQuery[ASSISTANT_MATERIAL_QUERY_KEY] = String(nextMaterialId)
    else delete nextQuery[ASSISTANT_MATERIAL_QUERY_KEY]
    if (nextQuestionSetId) nextQuery[ASSISTANT_QUESTION_SET_QUERY_KEY] = String(nextQuestionSetId)
    else delete nextQuery[ASSISTANT_QUESTION_SET_QUERY_KEY]
    await router.replace({ path: route.path, query: nextQuery })
  }

  return { sync }
}
