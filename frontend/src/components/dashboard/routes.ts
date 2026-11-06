/** 首页各卡片共用的跳转地址，查询参数与对应页面读取的参数保持一致。 */
export const uploadRoute = { path: '/materials', query: { action: 'upload' } }

export const materialRoute = (id: number) => ({ path: '/materials', query: { materialId: String(id) } })

export const summaryRoute = (materialId: number) => ({ path: '/summary', query: { materialId: String(materialId) } })

/** 打开题集页并直接弹出出题对话框。 */
export const quizRoute = (materialId: number) => ({
  path: '/quiz',
  query: { materialId: String(materialId), generate: '1' }
})

export const practiceRoute = (sessionId: number) => ({ path: '/practice', query: { sessionId: String(sessionId) } })

export const knowledgePracticeRoute = (materialId: number, knowledgePoint: string) => ({
  path: '/quiz',
  query: { materialId: String(materialId), knowledgePoint, generate: '1' }
})

export const wrongQuestionsRoute = (knowledgePoint: string, materialId?: number) => ({
  path: '/wrong-questions',
  query: { knowledgePoint, ...(materialId ? { materialId: String(materialId) } : {}) }
})
