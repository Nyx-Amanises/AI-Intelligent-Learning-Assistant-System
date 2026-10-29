import type { SourceSegmentLike } from '@/utils/format'
import { formatQuestionType, isChoiceType, isShortAnswerType } from '@/utils/labels'

/**
 * 练习页三个子组件共用的类型与纯函数：题目分组、选项构造、答案格式化。
 */

export interface PracticeAnswer {
  questionId: number
  questionType?: string
  stemText?: string
  optionA?: string
  optionB?: string
  optionC?: string
  optionD?: string
  userAnswer?: string
  correctAnswer?: string
  referenceAnswer?: string
  isCorrect?: number | boolean
  obtainedScore?: number | null
  aiScore?: number | null
  reviewMode?: string
  reviewComment?: string
  answerAnalysis?: string
  sourceSegments?: SourceSegmentLike[]
}

export interface PracticeDetail {
  sessionId: number
  sessionName: string
  sessionStatus?: string
  questionSetId?: number
  materialId?: number
  questionIds?: number[] | null
  totalQuestions?: number
  totalScore?: number
  obtainedScore?: number
  startTime?: string
  submitTime?: string
  answers?: PracticeAnswer[]
}

export interface PracticeHistoryItem {
  id: number
  sessionName: string
  sessionStatus?: string
  questionSetId?: number
  questionIds?: number[] | null
  totalQuestions?: number
  obtainedScore?: number
  accuracyRate?: number
  submitTime?: string
}

export type IndexedAnswer = PracticeAnswer & { globalIndex: number }

export interface PracticeSection {
  type: string
  label: string
  sidebarTitle: string
  items: IndexedAnswer[]
}

export type AnswerForm = Record<number, string>

export interface AnswerOption {
  value: string
  label: string
}

/** 多选题答案按字母排序，以英文逗号分隔（如 "A,C"），后端按选项集合全对判分。 */
export const MULTI_ANSWER_SEPARATOR = ','

type OptionQuestion = Pick<PracticeAnswer, 'questionType' | 'optionA' | 'optionB' | 'optionC' | 'optionD'>

const QUESTION_TYPE_ORDER: Record<string, number> = {
  SINGLE: 1,
  MULTIPLE: 2,
  MULTI: 2,
  JUDGE: 3,
  SHORT: 4,
  SHORT_ANSWER: 4
}

const upper = (value?: string | null) => String(value || '').toUpperCase()

export const isMultiQuestion = (answer: Pick<PracticeAnswer, 'questionType'>) =>
  ['MULTIPLE', 'MULTI'].includes(upper(answer.questionType))
export const isChoiceQuestion = (answer: PracticeAnswer) => isChoiceType(answer.questionType)
export const isShortQuestion = (answer: PracticeAnswer) => isShortAnswerType(answer.questionType)
export const isPendingReview = (answer: PracticeAnswer) => upper(answer.reviewMode) === 'AI_PENDING'
export const isSubmittedStatus = (status?: string | null) => upper(status) === 'SUBMITTED'

export const hasAnswerValue = (value?: string | null) => Boolean(String(value || '').trim())

export const splitMultiAnswer = (value?: string | null) =>
  String(value || '')
    .split(MULTI_ANSWER_SEPARATOR)
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)

export const joinMultiAnswer = (values: string[]) =>
  [...new Set(values.map((value) => value.trim().toUpperCase()).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b))
    .join(MULTI_ANSWER_SEPARATOR)

export const answerIncludesOption = (answer: OptionQuestion, value: string | undefined, optionValue: string) =>
  isMultiQuestion(answer) ? splitMultiAnswer(value).includes(optionValue) : String(value || '').trim() === optionValue

export const buildOptions = (answer: OptionQuestion): AnswerOption[] => {
  if (upper(answer.questionType) === 'JUDGE') {
    return [
      { value: '正确', label: 'A. 正确' },
      { value: '错误', label: 'B. 错误' }
    ]
  }
  return (['A', 'B', 'C', 'D'] as const)
    .map((key): AnswerOption | null => {
      const text = answer[`option${key}`]
      return text ? { value: key, label: `${key}. ${text}` } : null
    })
    .filter((item): item is AnswerOption => item !== null)
}

/** 把存储的答案转成可读文本：选择题显示"A. xxx"，多选题按分隔符逐项展示。 */
export const formatAnswer = (answer: OptionQuestion, value?: string | null) => {
  if (!hasAnswerValue(value)) return '未作答'
  const text = String(value)
  const type = upper(answer.questionType)
  if (type !== 'SINGLE' && !isMultiQuestion(answer)) return text
  const options = buildOptions(answer)
  const labelOf = (key: string) => options.find((item) => item.value === key.trim())?.label || key
  return isMultiQuestion(answer) ? splitMultiAnswer(text).map(labelOf).join(' / ') : labelOf(text)
}

/** 按题型分组并只编一次全局题号（按分组后的顺序）。 */
export const groupAnswers = (answers: PracticeAnswer[] = []): PracticeSection[] => {
  const groups = new Map<string, PracticeAnswer[]>()
  answers.forEach((item) => {
    const type = isMultiQuestion(item) ? 'MULTIPLE' : upper(item.questionType) || 'OTHER'
    if (!groups.has(type)) groups.set(type, [])
    groups.get(type)?.push(item)
  })

  let questionIndex = 0
  return Array.from(groups.entries())
    .sort((a, b) => (QUESTION_TYPE_ORDER[a[0]] || 99) - (QUESTION_TYPE_ORDER[b[0]] || 99))
    .map(([type, items]) => {
      const label = formatQuestionType(type)
      return {
        type,
        label,
        sidebarTitle: `${label}（${items.length}题）`,
        items: items.map((item) => ({ ...item, globalIndex: ++questionIndex }))
      }
    })
}

export const countAnswered = (answers: PracticeAnswer[] = [], form: AnswerForm) =>
  answers.filter((item) => hasAnswerValue(form[item.questionId])).length

export const questionResultLabel = (answer: PracticeAnswer) => {
  if (isPendingReview(answer)) return 'AI 评分中'
  if (answer.isCorrect) return '回答正确'
  return Number(answer.obtainedScore ?? answer.aiScore ?? 0) > 0 ? '还可以更完整' : '需要再复习'
}

export const questionNavStatus = (answer: PracticeAnswer, form: AnswerForm, submitted: boolean) =>
  submitted ? questionResultLabel(answer) : hasAnswerValue(form[answer.questionId]) ? '已作答' : '未作答'

export const toSectionLabel = (index: number) => ['一', '二', '三', '四', '五'][index] || String(index + 1)
