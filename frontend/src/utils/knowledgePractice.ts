export interface KnowledgePracticeTarget {
  materialId?: number
  knowledgePoint: string
}

interface PracticeMaterialState {
  materials: { id: number; parseStatus: string }[]
  loaded: boolean
  loading: boolean
  error: string
}

export const knowledgePointPracticeReason = (knowledgePoint: string) => {
  const point = knowledgePoint.trim()
  if (!point || point === '未标注知识点') return '这条记录未标注具体知识点，可先查看错题。'
  if (point.length > 80) return '知识点名称超过 80 字，请在练习页手动选择具体知识点。'
  return ''
}

export const knowledgePracticeUnavailableReason = (target: KnowledgePracticeTarget, state: PracticeMaterialState) => {
  const pointReason = knowledgePointPracticeReason(target.knowledgePoint)
  if (pointReason) return pointReason
  if (!Number.isInteger(target.materialId) || Number(target.materialId) <= 0)
    return '未关联学习资料，暂时无法生成专项练习。'
  if (state.loading) return '正在确认资料是否可用…'
  if (state.error) return '资料列表加载失败，重新加载后可继续练习。'
  if (!state.loaded) return '正在确认资料是否可用…'
  if (
    !state.materials.some(
      (material) => material.id === target.materialId && String(material.parseStatus || '').toUpperCase() === 'SUCCESS'
    )
  )
    return '原资料已删除或尚未解析完成，暂时无法生成专项练习。'
  return ''
}
