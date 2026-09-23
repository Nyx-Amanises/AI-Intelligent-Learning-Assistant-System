/**
 * 知识点掌握度的等级、阈值与配色，统一供「学习统计」「知识掌握」页面使用。
 * 阈值与后端一致：已掌握 ≥ 85%，基本掌握 70% – 84%，待巩固 50% – 69%，薄弱 < 50%。
 */
export type MasteryLevel = 'MASTERED' | 'GOOD' | 'WEAK' | 'RISK'

export interface MasteryLevelMeta {
  level: MasteryLevel
  label: string
  /** 该等级的最低掌握度（含） */
  min: number
  /** 图表填充色，使用主题变量以适配护眼主题 */
  color: string
}

/** 按掌握度从高到低排列 */
export const MASTERY_LEVELS: readonly MasteryLevelMeta[] = [
  { level: 'MASTERED', label: '已掌握', min: 85, color: 'var(--brand)' },
  { level: 'GOOD', label: '基本掌握', min: 70, color: 'color-mix(in srgb, var(--brand) 55%, var(--panel))' },
  { level: 'WEAK', label: '待巩固', min: 50, color: 'var(--accent)' },
  { level: 'RISK', label: '薄弱', min: 0, color: 'var(--red)' }
]

/** 未知等级时的兜底颜色 */
export const MASTERY_FALLBACK_COLOR = 'var(--muted)'

/** 低于该掌握度的知识点建议优先复习 */
export const MASTERY_REVIEW_THRESHOLD = 70

const findLevel = (level: string) => MASTERY_LEVELS.find((item) => item.level === String(level || '').toUpperCase())

export const masteryLevelByPercent = (percent: number): MasteryLevel => {
  const value = Number(percent) || 0
  return (MASTERY_LEVELS.find((item) => value >= item.min) || MASTERY_LEVELS[MASTERY_LEVELS.length - 1]).level
}

export const masteryColor = (level: string) => findLevel(level)?.color || MASTERY_FALLBACK_COLOR

export const masteryColorByPercent = (percent: number) => masteryColor(masteryLevelByPercent(percent))

export const masteryLabel = (level: string) => findLevel(level)?.label || '未知'
