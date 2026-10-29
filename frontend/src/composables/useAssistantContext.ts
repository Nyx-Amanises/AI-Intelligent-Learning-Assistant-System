import { useRoute, useRouter } from 'vue-router'

export const ASSISTANT_MATERIAL_QUERY_KEY = 'assistantMaterialId'
export const ASSISTANT_QUESTION_SET_QUERY_KEY = 'assistantQuestionSetId'
export const ASSISTANT_PRACTICE_QUERY_KEY = 'assistantPracticeSessionId'

/**
 * 把当前页面正在查看的资料 / 题集 / 练习写进路由 query，学习助手据此提供"按当前页面创建会话"。
 * 使用 replace，不产生历史记录；值未变化时不触发路由更新。
 */
export const useAssistantContext = () => {
  const route = useRoute()
  const router = useRouter()
  const ownerPath = route.path

  const readId = (key: string) => Number(route.query[key] || 0) || undefined

  const sync = async (context: { materialId?: number; questionSetId?: number; practiceSessionId?: number }) => {
    if (route.path !== ownerPath) return
    const nextMaterialId = context.materialId || undefined
    const nextQuestionSetId = context.questionSetId || undefined
    const nextPracticeId = context.practiceSessionId || undefined
    if (
      readId(ASSISTANT_MATERIAL_QUERY_KEY) === nextMaterialId &&
      readId(ASSISTANT_QUESTION_SET_QUERY_KEY) === nextQuestionSetId &&
      readId(ASSISTANT_PRACTICE_QUERY_KEY) === nextPracticeId
    ) {
      return
    }
    const nextQuery = { ...route.query }
    if (nextMaterialId) nextQuery[ASSISTANT_MATERIAL_QUERY_KEY] = String(nextMaterialId)
    else delete nextQuery[ASSISTANT_MATERIAL_QUERY_KEY]
    if (nextQuestionSetId) nextQuery[ASSISTANT_QUESTION_SET_QUERY_KEY] = String(nextQuestionSetId)
    else delete nextQuery[ASSISTANT_QUESTION_SET_QUERY_KEY]
    if (nextPracticeId) nextQuery[ASSISTANT_PRACTICE_QUERY_KEY] = String(nextPracticeId)
    else delete nextQuery[ASSISTANT_PRACTICE_QUERY_KEY]
    await router.replace({ path: route.path, query: nextQuery })
  }

  return { sync }
}
