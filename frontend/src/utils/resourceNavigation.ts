import type { LocationQuery } from 'vue-router'

/** 从资料阅读返回找资料时，仅恢复目录自身的查询条件。 */
export const resourceReturnPath = (query: LocationQuery): string => {
  const params = new URLSearchParams()
  for (const [source, target] of [
    ['resourceKeyword', 'keyword'],
    ['resourceSubject', 'subject'],
    ['resourceKind', 'resourceKind'],
    ['resourceAccessMode', 'accessMode'],
    ['resourceTab', 'tab'],
    ['resourcePage', 'page'],
    ['resourceStatus', 'status'],
    ['resourceFrom', 'from'],
    ['knowledgePoint', 'knowledgePoint'],
    ['practiceMaterialId', 'practiceMaterialId']
  ]) {
    const value = query[source]
    if (typeof value === 'string' && value) params.set(target, value)
  }
  const search = params.toString()
  return `/resources${search ? `?${search}` : ''}`
}

export const knowledgeResourceRoute = (knowledgePoint: string, materialId?: number) => ({
  path: '/resources',
  query: {
    knowledgePoint: knowledgePoint.trim(),
    from: 'mastery',
    ...(Number.isSafeInteger(materialId) && Number(materialId) > 0 ? { practiceMaterialId: String(materialId) } : {})
  }
})
