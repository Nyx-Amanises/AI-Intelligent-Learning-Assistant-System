<template>
  <section class="practice-view">
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">练习与复盘</div>
        <h1 class="page-title">我的练习</h1>
        <p class="page-desc">专注完成每一道题，也把每一次答错变成进步的起点。</p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="historyLoading" @click="loadHistory">刷新记录</el-button>
        <el-button type="primary" @click="router.push('/quiz')">选择题集</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-tabs" aria-label="练习视图">
          <button type="button" class="workspace-tab" :class="{ 'workspace-tab--active': activeTab === 'current' }" :aria-pressed="activeTab === 'current'" @click="activeTab = 'current'">当前练习</button>
          <button type="button" class="workspace-tab" :class="{ 'workspace-tab--active': activeTab === 'history' }" :aria-pressed="activeTab === 'history'" @click="activeTab = 'history'">历史记录 <span class="practice-tab-count">{{ total }}</span></button>
        </div>
        <span v-if="practiceDetail && activeTab === 'current'" class="practice-status">{{ formatSessionStatus(practiceDetail.sessionStatus) }}</span>
      </div>

      <div v-if="activeTab === 'history'" class="workspace-body">
        <div v-if="historyLoading" class="practice-loading" aria-live="polite"><el-skeleton :rows="5" animated /><span>正在加载练习记录…</span></div>
        <template v-else-if="historyRecords.length">
          <div class="practice-history-list">
            <article v-for="item in historyRecords" :key="item.id" class="practice-record">
              <div class="practice-record__main">
                <div class="practice-record__heading"><h2>{{ item.sessionName }}</h2><span class="practice-status" :class="{ 'practice-status--muted': item.sessionStatus !== 'SUBMITTED' }">{{ formatSessionStatus(item.sessionStatus) }}</span></div>
                <p>{{ item.totalQuestions || 0 }} 道题<span>·</span>{{ item.submitTime ? '提交于 ' + formatDateTime(item.submitTime) : (item.sessionStatus === 'SUBMITTED' ? '提交时间未记录' : '尚未提交') }}</p>
              </div>
              <div class="practice-record__stats">
                <div><strong>{{ item.sessionStatus === 'SUBMITTED' ? Number(item.accuracyRate || 0).toFixed(0) + '%' : '—' }}</strong><span>正确率</span></div>
                <div><strong>{{ item.sessionStatus === 'SUBMITTED' ? item.obtainedScore || 0 : '—' }}</strong><span>得分</span></div>
              </div>
              <div class="practice-record__actions">
                <el-button type="primary" plain @click="openPracticeFromHistory(item.id)">{{ item.sessionStatus === 'SUBMITTED' ? '查看结果' : '继续练习' }}</el-button>
                <el-dropdown trigger="click" @command="handleHistoryAction($event, item)">
                  <el-button text :loading="actionLoadingId === item.id" aria-label="练习更多操作" class="practice-more-button">···</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">修改名称</el-dropdown-item>
                      <el-dropdown-item v-if="item.sessionStatus === 'SUBMITTED'" command="restart">再练一次</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除记录</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </article>
          </div>
          <div class="workspace-pagination">
            <div class="workspace-pagination__meta">共 {{ total }} 次练习</div>
            <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="loadHistory" @size-change="loadHistory" />
          </div>
        </template>
        <div v-else class="learning-empty">
          <div class="learning-empty__icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 8h8M8 12h8M8 16h4" stroke-linecap="round" /></svg></div>
          <h3>你的练习记录，从这里开始</h3><p>选择一套题集完成练习，成绩与答案都会保存在这里。</p>
          <el-button type="primary" @click="router.push('/quiz')">去选择题集</el-button>
        </div>
      </div>

      <div v-else class="workspace-body">
        <div v-if="detailLoading" class="practice-loading" aria-live="polite"><el-skeleton :rows="6" animated /><span>正在准备练习…</span></div>
        <template v-else-if="practiceDetail">
          <div class="practice-paper-layout">
            <div class="practice-paper-main">
              <div class="page-card practice-paper-hero">
                <div class="practice-paper-hero__head">
                  <div class="practice-paper-hero__intro">
                    <h2 class="practice-paper-hero__title">{{ practiceDetail.sessionName }}</h2>
                    <div class="practice-paper-hero__meta"><span>{{ practiceDetail.totalQuestions }} 道题</span><span>满分 {{ practiceDetail.totalScore || 0 }} 分</span><el-button link @click="openRenameDialog({ id: practiceDetail.sessionId, sessionName: practiceDetail.sessionName })">修改名称</el-button></div>
                    <p class="practice-start-time">{{ isSubmittedPractice() ? '提交于 ' + formatDateTime(practiceDetail.submitTime) : '开始于 ' + formatDateTime(practiceDetail.startTime) }}</p>
                  </div>
                  <div class="practice-paper-hero__score">
                    <strong>{{ isSubmittedPractice() ? practiceDetail.obtainedScore || 0 : answeredCount }}</strong>
                    <span>{{ isSubmittedPractice() ? (hasPendingAiReview ? '当前得分' : '本次得分') : '/ ' + practiceDetail.totalQuestions + ' 已答' }}</span>
                  </div>
                </div>
                <div v-if="!isSubmittedPractice()" class="practice-progress">
                  <div><span>作答进度</span><strong>{{ completionPercentage }}%</strong></div>
                  <el-progress :percentage="completionPercentage" :show-text="false" :stroke-width="6" />
                  <p>答案会自动保存在此浏览器，离开后可继续作答。</p>
                </div>
                <div v-else class="practice-completed-note">{{ hasPendingAiReview ? '客观题已批改，简答题评分完成后会自动更新。' : '练习已完成。回顾下方解析，巩固还不熟悉的知识点。' }}</div>
              </div>

              <el-alert v-if="hasPendingAiReview" :title="pendingReviewText" type="info" :closable="false" show-icon />

              <div v-for="(section, sectionIndex) in groupedSections" :key="section.type" class="page-card practice-paper-section">
                <div class="practice-paper-section__title"><span>{{ toSectionLabel(sectionIndex) }}、{{ section.label }}</span><small>{{ section.items.length }} 道题</small></div>
                <article v-for="answer in section.items" :id="'question-' + answer.questionId" :key="answer.questionId" class="practice-paper-question">
                  <div class="practice-paper-question__head">
                    <div class="practice-paper-question__index">第 {{ answer.globalIndex }} 题</div>
                    <span class="practice-question-score">{{ getQuestionTypeLabel(answer.questionType) }}</span>
                  </div>
                  <h3 :id="'stem-' + answer.questionId" class="practice-paper-question__stem">{{ answer.stemText }}</h3>
                  <el-radio-group v-if="isChoiceQuestion(answer)" v-model="answerForm[answer.questionId]" class="choice-group practice-paper-choice-group" :disabled="isSubmittedPractice()" :aria-labelledby="'stem-' + answer.questionId">
                    <el-radio v-for="option in buildOptions(answer)" :key="option.value" :value="option.value" border class="choice-option practice-paper-choice">{{ option.label }}</el-radio>
                  </el-radio-group>
                  <el-input v-else v-model="answerForm[answer.questionId]" :disabled="isSubmittedPractice()" type="textarea" :rows="5" :aria-labelledby="'stem-' + answer.questionId" placeholder="用自己的话写下理解，也可以列出关键步骤…" />

                  <div v-if="isSubmittedPractice()" class="practice-paper-result" :class="{ 'practice-paper-result--correct': answer.isCorrect, 'practice-paper-result--pending': isPendingReview(answer) }">
                    <div class="practice-feedback-heading"><span>{{ questionResultLabel(answer) }}</span><strong>{{ isPendingReview(answer) ? '等待评分' : (answer.obtainedScore ?? answer.aiScore ?? 0) + ' 分' }}</strong></div>
                    <div class="practice-review-answer"><span>我的答案</span><p>{{ formatAnswer(answer, answer.userAnswer) }}</p></div>
                    <div class="practice-review-answer practice-review-answer--reference"><span>{{ isShortQuestion(answer) ? '参考答案' : '正确答案' }}</span><p>{{ formatAnswer(answer, answer.referenceAnswer || answer.correctAnswer) }}</p></div>
                    <div v-if="isShortQuestion(answer) && answer.reviewComment" class="practice-review-analysis"><strong>AI 评语</strong><p>{{ answer.reviewComment }}</p></div>
                    <div class="practice-review-analysis"><strong>答案解析</strong><p>{{ answer.answerAnalysis || '这道题暂时没有解析。' }}</p></div>
                    <details v-if="isShortQuestion(answer) && answer.sourceSegments?.length" class="practice-review-sources">
                      <summary>查看资料依据</summary>
                      <div class="rag-reference-list rag-reference-list--compact">
                        <div v-for="segment in answer.sourceSegments" :key="segment.segmentId + '-' + (segment.segmentNo || 0)" class="rag-reference-item">
                          <div class="rag-reference-item__title">{{ segment.sectionTitle || '资料摘录 ' + (segment.segmentNo || segment.segmentId) }}</div>
                          <div class="rag-reference-item__meta">{{ buildSegmentMeta(segment) }}</div>
                          <div class="summary-block">{{ segment.contentText }}</div>
                        </div>
                      </div>
                    </details>
                  </div>
                </article>
              </div>

              <div class="page-card practice-submit-card">
                <div><h3>{{ isSubmittedPractice() ? '让复习形成闭环' : '准备好提交了吗？' }}</h3><p>{{ isSubmittedPractice() ? '回顾错题，或再练一次，看看自己进步了多少。' : (unansweredCount ? '还有 ' + unansweredCount + ' 道题未作答，提交前再检查一下。' : '所有题目已作答，提交后即可查看成绩与解析。') }}</p></div>
                <div class="practice-submit-actions">
                  <el-button v-if="!isSubmittedPractice()" type="primary" :loading="submitting" @click="submitPractice">提交练习</el-button>
                  <template v-else>
                    <el-button @click="router.push('/wrong-questions')">查看错题本</el-button>
                    <el-button type="primary" :loading="restarting" @click="restartPractice(practiceDetail.questionSetId)">再练一次</el-button>
                  </template>
                </div>
              </div>
            </div>

            <aside class="page-card practice-paper-sidebar" aria-label="题目导航">
              <div class="practice-paper-sidebar__title">答题卡 <span>{{ answeredCount }} / {{ practiceDetail.totalQuestions }}</span></div>
              <p class="practice-sidebar-hint">点击题号，快速定位题目。</p>
              <div v-for="section in groupedSections" :key="section.type" class="practice-paper-sidebar__group">
                <div class="practice-paper-sidebar__group-title">{{ section.sidebarTitle }}</div>
                <div class="practice-paper-sidebar__grid">
                  <button v-for="answer in section.items" :key="answer.questionId" type="button" class="practice-paper-sidebar__item"
                    :class="{
                      'practice-paper-sidebar__item--active': activeQuestionId === answer.questionId,
                      'practice-paper-sidebar__item--done': Boolean(answerForm[answer.questionId]?.trim()),
                      'practice-paper-sidebar__item--correct': isSubmittedPractice() && answer.isCorrect,
                      'practice-paper-sidebar__item--wrong': isSubmittedPractice() && !answer.isCorrect && !isPendingReview(answer)
                    }"
                    :aria-label="'第 ' + answer.globalIndex + ' 题，' + questionNavStatus(answer)"
                    :aria-current="activeQuestionId === answer.questionId ? 'step' : undefined"
                    @click="scrollToQuestion(answer.questionId)">{{ answer.globalIndex }}</button>
                </div>
              </div>
              <div class="practice-sidebar-legend"><span><i></i>{{ isSubmittedPractice() ? '待评分' : '未作答' }}</span><span><i class="is-done"></i>{{ isSubmittedPractice() ? '答对' : '已作答' }}</span><span v-if="isSubmittedPractice()"><i class="is-wrong"></i>待复习</span></div>
            </aside>
          </div>
        </template>
        <div v-else class="learning-empty">
          <div class="learning-empty__icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M5 4h14v17H5zM9 2v4M15 2v4M9 11h6M9 15h4" stroke-linecap="round" stroke-linejoin="round" /></svg></div>
          <h3>给自己留一段专注学习的时间</h3><p>{{ total ? '在历史记录中继续未完成的练习，或选择一套新题集。' : '从一套小练习开始，检验理解，再有针对性地复习。' }}</p>
          <div class="practice-empty-actions"><el-button v-if="total" @click="activeTab = 'history'">查看历史记录</el-button><el-button type="primary" @click="router.push('/quiz')">选择题集</el-button></div>
        </div>
      </div>
    </div>

    <el-dialog v-model="renameDialogVisible" title="修改练习名称" width="min(520px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top"><el-form-item label="练习名称"><el-input v-model="renameForm.sessionName" maxlength="200" show-word-limit placeholder="输入便于日后查找的名称" @keyup.enter="renamePracticeSession" /></el-form-item></el-form>
      <template #footer><el-button @click="renameDialogVisible = false">取消</el-button><el-button type="primary" :loading="renaming" @click="renamePracticeSession">保存名称</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { submitPracticeReviewTaskApi, type AiTaskDetail } from '@/api/modules/ai'
import {
  deletePracticeApi,
  getPracticeDetailApi,
  getPracticePageApi,
  renamePracticeApi,
  startPracticeApi,
  submitPracticeApi
} from '@/api/modules/practice'
import { isAiTaskSuccess, isAiTaskTerminal, waitForAiTask } from '@/utils/aiTask'

const route = useRoute()
const router = useRouter()
const historyRecords = ref<any[]>([])
const total = ref(0)
const practiceDetail = ref<any>(null)
const answerForm = ref<Record<number, string>>({})
const historyLoading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const restarting = ref(false)
const renaming = ref(false)
const activeTab = ref<'current' | 'history'>('current')
const actionLoadingId = ref<number | null>(null)
const activeQuestionId = ref<number | null>(null)
const currentReviewTask = ref<AiTaskDetail | null>(null)
const reviewTaskWaiting = ref(false)
const renameDialogVisible = ref(false)
let reviewTaskWaitAborted = false
const page = reactive({
  current: 1,
  size: 10
})

const PRACTICE_DRAFT_KEY_PREFIX = 'ai-learning-assistant:practice-draft:'
const PRACTICE_ACTIVE_DRAFT_KEY = 'ai-learning-assistant:practice-draft:active'

const renameForm = reactive({
  sessionId: 0,
  sessionName: ''
})

const questionTypeOrder: Record<string, number> = {
  SINGLE: 1,
  MULTI: 2,
  JUDGE: 3,
  SHORT: 4,
  SHORT_ANSWER: 4
}

const hasPendingAiReview = computed(() =>
  Boolean(
    practiceDetail.value?.sessionStatus === 'SUBMITTED' &&
      practiceDetail.value?.answers?.some(
        (item: any) => String(item.reviewMode || '').toUpperCase() === 'AI_PENDING'
      )
  )
)

const pendingReviewText = computed(() => {
  if (!hasPendingAiReview.value) {
    return ''
  }
  if (reviewTaskWaiting.value && currentReviewTask.value?.id) {
    return '客观题已批改，AI 正在认真评阅你的简答题。'
  }
  return '客观题已批改，简答题正在等待评分。结果会自动更新。'
})

const groupedSections = computed(() => {
  const answers = (practiceDetail.value?.answers || []).map((item: any, index: number) => ({
    ...item,
    globalIndex: index + 1
  }))

  const groups = new Map<string, any[]>()
  answers.forEach((item: any) => {
    const type = String(item.questionType || 'OTHER').toUpperCase()
    if (!groups.has(type)) {
      groups.set(type, [])
    }
    groups.get(type)?.push(item)
  })

  let questionIndex = 0
  return Array.from(groups.entries())
    .sort((a, b) => (questionTypeOrder[a[0]] || 99) - (questionTypeOrder[b[0]] || 99))
    .map(([type, items]) => ({
      type,
      label: toChineseTypeTitle(type),
      sidebarTitle: `${toChineseTypeTitle(type)}（${items.length}题）`,
      items: items.map((item) => ({ ...item, globalIndex: ++questionIndex }))
    }))
})

const answeredCount = computed(() =>
  (practiceDetail.value?.answers || []).filter((item: any) => String(answerForm.value[item.questionId] || '').trim()).length
)
const unansweredCount = computed(() => Math.max(0, (practiceDetail.value?.answers?.length || 0) - answeredCount.value))
const completionPercentage = computed(() => {
  const count = practiceDetail.value?.answers?.length || 0
  return count ? Math.round(answeredCount.value / count * 100) : 0
})
const formatSessionStatus = (value?: string) => {
  const statuses: Record<string, string> = { SUBMITTED: '已完成', IN_PROGRESS: '进行中', STARTED: '进行中', CREATED: '待开始', COMPLETED: '已完成' }
  return statuses[value || ''] || '进行中'
}
const questionResultLabel = (answer: any) => {
  if (isPendingReview(answer)) return 'AI 评分中'
  if (answer.isCorrect) return '回答正确'
  return Number(answer.obtainedScore ?? answer.aiScore ?? 0) > 0 ? '还可以更完整' : '需要再复习'
}
const questionNavStatus = (answer: any) =>
  isSubmittedPractice() ? questionResultLabel(answer) : (answerForm.value[answer.questionId]?.trim() ? '已作答' : '未作答')

const handleHistoryAction = (command: string, item: any) => {
  if (command === 'rename') openRenameDialog(item)
  if (command === 'restart') void restartPractice(item.questionSetId || practiceDetail.value?.questionSetId)
  if (command === 'delete') void removePractice(item.id, practiceDetail.value?.sessionId === item.id)
}

const syncAnswerForm = () => {
  const form: Record<number, string> = {}
  ;(practiceDetail.value?.answers || []).forEach((item: any) => {
    form[item.questionId] = item.userAnswer || ''
  })
  answerForm.value = form
}

const getPracticeDraftKey = (sessionId: number | string) => `${PRACTICE_DRAFT_KEY_PREFIX}${sessionId}`

const isSubmittedPractice = () => String(practiceDetail.value?.sessionStatus || '').toUpperCase() === 'SUBMITTED'

const isCurrentPracticeDraftable = () => Boolean(practiceDetail.value?.sessionId && !isSubmittedPractice())

const readPracticeDraft = (sessionId: number | string) => {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const rawDraft = window.localStorage.getItem(getPracticeDraftKey(sessionId))
    if (!rawDraft) {
      return null
    }

    const parsedDraft = JSON.parse(rawDraft)
    if (!parsedDraft || typeof parsedDraft !== 'object') {
      return null
    }

    const draft: Record<number, string> = {}
    Object.entries(parsedDraft as Record<string, unknown>).forEach(([questionId, value]) => {
      const normalizedId = Number(questionId)
      if (Number.isFinite(normalizedId) && value !== undefined && value !== null) {
        draft[normalizedId] = String(value)
      }
    })
    return draft
  } catch {
    return null
  }
}

const readActivePracticeDraftSessionId = () => {
  if (typeof window === 'undefined') {
    return 0
  }

  const sessionId = Number(window.localStorage.getItem(PRACTICE_ACTIVE_DRAFT_KEY))
  return Number.isFinite(sessionId) ? sessionId : 0
}

const clearPracticeDraft = (sessionId?: number | string) => {
  const targetSessionId = sessionId ?? practiceDetail.value?.sessionId
  if (typeof window === 'undefined' || !targetSessionId) {
    return
  }
  window.localStorage.removeItem(getPracticeDraftKey(targetSessionId))
  if (window.localStorage.getItem(PRACTICE_ACTIVE_DRAFT_KEY) === String(targetSessionId)) {
    window.localStorage.removeItem(PRACTICE_ACTIVE_DRAFT_KEY)
  }
}

const savePracticeDraft = () => {
  if (typeof window === 'undefined' || !isCurrentPracticeDraftable()) {
    return
  }

  const sessionId = practiceDetail.value.sessionId
  const hasAnswer = Object.values(answerForm.value).some((value) => String(value || '').trim())
  if (!hasAnswer) {
    clearPracticeDraft(sessionId)
    return
  }

  window.localStorage.setItem(getPracticeDraftKey(sessionId), JSON.stringify(answerForm.value))
  window.localStorage.setItem(PRACTICE_ACTIVE_DRAFT_KEY, String(sessionId))
}

const restorePracticeDraft = () => {
  const sessionId = practiceDetail.value?.sessionId
  if (!sessionId || isSubmittedPractice()) {
    return
  }

  const draft = readPracticeDraft(sessionId)
  if (!draft || !Object.keys(draft).length) {
    return
  }

  answerForm.value = {
    ...answerForm.value,
    ...draft
  }
}

watch(
  answerForm,
  () => {
    savePracticeDraft()
  },
  { deep: true }
)

const toSectionLabel = (index: number) => ['一', '二', '三', '四', '五'][index] || String(index + 1)

const toChineseTypeTitle = (type?: string) => {
  switch ((type || '').toUpperCase()) {
    case 'JUDGE':
      return '判断题'
    case 'MULTI':
      return '多选题'
    case 'SINGLE':
      return '单选题'
    case 'SHORT':
    case 'SHORT_ANSWER':
      return '简答题'
    default:
      return '题目'
  }
}

const getQuestionTypeLabel = (type?: string) => {
  switch ((type || '').toUpperCase()) {
    case 'SINGLE':
      return '单选题'
    case 'MULTI':
      return '多选题'
    case 'JUDGE':
      return '判断题'
    case 'SHORT':
    case 'SHORT_ANSWER':
      return '简答题'
    default:
      return type || '题目'
  }
}

const isChoiceQuestion = (answer: any) => ['SINGLE', 'JUDGE', 'MULTI'].includes(String(answer.questionType || '').toUpperCase())

const isShortQuestion = (answer: any) =>
  ['SHORT', 'SHORT_ANSWER'].includes(String(answer.questionType || '').toUpperCase())

const isPendingReview = (answer: any) => String(answer.reviewMode || '').toUpperCase() === 'AI_PENDING'

const isOptionItem = (item: any): item is { value: string; label: string } => Boolean(item)

const buildOptions = (answer: any) => {
  if (String(answer.questionType || '').toUpperCase() === 'JUDGE') {
    return [
      { value: '正确', label: 'A. 正确' },
      { value: '错误', label: 'B. 错误' }
    ]
  }

  return [
    answer.optionA ? { value: 'A', label: `A. ${answer.optionA}` } : null,
    answer.optionB ? { value: 'B', label: `B. ${answer.optionB}` } : null,
    answer.optionC ? { value: 'C', label: `C. ${answer.optionC}` } : null,
    answer.optionD ? { value: 'D', label: `D. ${answer.optionD}` } : null
  ].filter(isOptionItem)
}

const formatAnswer = (answer: any, value?: string) => {
  if (!value) {
    return '未作答'
  }
  if (['SINGLE', 'MULTI'].includes(String(answer.questionType || '').toUpperCase())) {
    const option = buildOptions(answer).find((item: any) => item.value === value)
    return option ? option.label : value
  }
  return value
}

const formatDateTime = (value?: string) => {
  if (!value) {
    return '未知时间'
  }
  return value.replace('T', ' ').slice(0, 19)
}

const buildSegmentMeta = (segment: any) => {
  const parts: string[] = []
  if (segment?.pageNo) {
    parts.push(`第 ${segment.pageNo} 页`)
  }
  if (segment?.segmentNo) {
    parts.push(`段落 #${segment.segmentNo}`)
  }
  return parts.join(' · ') || '资料摘录'
}

const resetReviewTaskWaiting = () => {
  reviewTaskWaitAborted = true
  reviewTaskWaiting.value = false
}

const ensurePracticeReviewTask = async (sessionId: number) => {
  if (reviewTaskWaiting.value) {
    return
  }

  reviewTaskWaiting.value = true
  reviewTaskWaitAborted = false
  try {
    const submitRes = await submitPracticeReviewTaskApi(sessionId)
    if (reviewTaskWaitAborted) {
      return
    }
    const submittedTask = submitRes.data.data as AiTaskDetail
    currentReviewTask.value = submittedTask
    const taskId = submittedTask.id

    const finishedTask = await waitForAiTask(taskId)
    if (reviewTaskWaitAborted) {
      return
    }
    currentReviewTask.value = finishedTask
    if (!isAiTaskTerminal(finishedTask.status)) {
      ElMessage.info('简答题评分仍在处理中，可稍后回到本页查看结果')
      return
    }
    if (!isAiTaskSuccess(finishedTask.status)) {
      throw new Error(finishedTask.errorMessage || '简答题评分任务执行失败')
    }

    await Promise.all([loadPracticeDetail(sessionId, true), loadHistory()])
    if (!reviewTaskWaitAborted) {
      ElMessage.success('简答题 AI 评分已完成')
    }
  } catch (error: any) {
    if (!reviewTaskWaitAborted) {
      ElMessage.error(error.message || '简答题评分任务失败')
    }
  } finally {
    reviewTaskWaiting.value = false
  }
}

const scrollToQuestion = (questionId: number) => {
  activeQuestionId.value = questionId
  const target = document.getElementById(`question-${questionId}`)
  target?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' })
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getPracticePageApi({ current: page.current, size: page.size })
    historyRecords.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载练习记录失败')
  } finally {
    historyLoading.value = false
  }
}

const loadPracticeDetail = async (sessionId: number, silent = false) => {
  detailLoading.value = true
  try {
    const res = await getPracticeDetailApi(sessionId)
    practiceDetail.value = res.data.data
    syncAnswerForm()
    if (isSubmittedPractice()) {
      clearPracticeDraft(sessionId)
    } else {
      restorePracticeDraft()
    }
    activeQuestionId.value = practiceDetail.value?.answers?.[0]?.questionId || null
    router.replace({ path: '/practice', query: { sessionId: String(sessionId) } })
    if (hasPendingAiReview.value) {
      if (currentReviewTask.value?.bizId !== sessionId) {
        currentReviewTask.value = null
      }
      if (!silent) {
        void ensurePracticeReviewTask(sessionId)
      }
    } else {
      currentReviewTask.value = null
    }
  } catch (error: any) {
    practiceDetail.value = null
    ElMessage.error(error.message || '加载练习详情失败')
  } finally {
    detailLoading.value = false
  }
}

const openPracticeFromHistory = async (sessionId: number) => {
  resetReviewTaskWaiting()
  await loadPracticeDetail(sessionId)
  activeTab.value = 'current'
}

const openRenameDialog = (item: { id: number; sessionName: string }) => {
  renameForm.sessionId = item.id
  renameForm.sessionName = item.sessionName
  renameDialogVisible.value = true
}

const renamePracticeSession = async () => {
  if (!renameForm.sessionId) {
    return
  }
  if (!renameForm.sessionName.trim()) {
    ElMessage.warning('请输入练习名称')
    return
  }

  renaming.value = true
  try {
    await renamePracticeApi(renameForm.sessionId, renameForm.sessionName.trim())
    ElMessage.success('练习名称已更新')
    renameDialogVisible.value = false
    if (practiceDetail.value?.sessionId === renameForm.sessionId) {
      practiceDetail.value = {
        ...practiceDetail.value,
        sessionName: renameForm.sessionName.trim()
      }
    }
    await loadHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '修改练习名称失败')
  } finally {
    renaming.value = false
  }
}

const restartPractice = async (questionSetId?: number) => {
  if (restarting.value) return
  if (!questionSetId) {
    ElMessage.warning('当前缺少题集信息，无法重新练习')
    return
  }
  restarting.value = true
  try {
    const res = await startPracticeApi(questionSetId)
    ElMessage.success('新的练习已开始')
    page.current = 1
    resetReviewTaskWaiting()
    await loadHistory()
    await loadPracticeDetail(res.data.data.sessionId)
    activeTab.value = 'current'
  } catch (error: any) {
    ElMessage.error(error.message || '重新开始练习失败')
  } finally {
    restarting.value = false
  }
}

const removePractice = async (sessionId: number, clearCurrent = false) => {
  try {
    await ElMessageBox.confirm('删除后这次练习记录和答案将不可恢复，确定继续吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  actionLoadingId.value = sessionId
  try {
    await deletePracticeApi(sessionId)
    ElMessage.success('练习记录已删除')
    if (clearCurrent && practiceDetail.value?.sessionId === sessionId) {
      practiceDetail.value = null
      router.replace({ path: '/practice' })
      activeTab.value = 'history'
    }
    if (!clearCurrent && historyRecords.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    await loadHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '删除练习失败')
  } finally {
    actionLoadingId.value = null
  }
}

const submitPractice = async () => {
  if (submitting.value) return
  if (!practiceDetail.value?.sessionId) {
    return
  }

  if (unansweredCount.value > 0) {
    try {
      await ElMessageBox.confirm(
        `还有 ${unansweredCount.value} 道题未作答。提交后将不能修改答案，确定提交吗？`,
        '检查你的练习',
        { type: 'warning', confirmButtonText: '仍然提交', cancelButtonText: '继续作答' }
      )
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    const sessionId = practiceDetail.value.sessionId
    const answers = (practiceDetail.value.answers || []).map((item: any) => ({
      questionId: item.questionId,
      userAnswer: answerForm.value[item.questionId] || ''
    }))

    const res = await submitPracticeApi(sessionId, answers)
    clearPracticeDraft(sessionId)
    practiceDetail.value = res.data.data
    syncAnswerForm()
    if (hasPendingAiReview.value) {
      await loadHistory()
      ElMessage.success('练习已提交，简答题正在等待 AI 评分')
      void ensurePracticeReviewTask(practiceDetail.value.sessionId)
      return
    }
    ElMessage.success('练习提交成功')
    await loadHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '提交练习失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadHistory()
  const sessionId = Number(route.query.sessionId)
  if (sessionId) {
    await loadPracticeDetail(sessionId)
    activeTab.value = 'current'
  } else if (readActivePracticeDraftSessionId()) {
    await loadPracticeDetail(readActivePracticeDraftSessionId())
    activeTab.value = 'current'
  } else if (historyRecords.value.length) {
    activeTab.value = 'history'
  }
})

onUnmounted(() => {
  savePracticeDraft()
  resetReviewTaskWaiting()
})
</script>

<style scoped>
.learning-eyebrow { margin-bottom: 8px; color: var(--brand); font-size: 12px; font-weight: 650; letter-spacing: .12em; }
.practice-tab-count { margin-left: 6px; opacity: .7; font-size: 12px; font-variant-numeric: tabular-nums; }
.practice-status { display: inline-flex; align-items: center; width: fit-content; padding: 4px 9px; border-radius: 5px; background: var(--brand-light, #edf4ef); color: var(--brand); font-size: 12px; white-space: nowrap; }
.practice-status--muted { background: var(--bg); color: var(--muted); border: 1px solid var(--line); }
.practice-loading { display: grid; gap: 24px; padding: 24px; color: var(--muted); font-size: 13px; }
.practice-history-list { display: grid; }
.practice-record { display: grid; grid-template-columns: minmax(0, 1fr) 170px auto; align-items: center; gap: 24px; padding: 24px 0; border-bottom: 1px solid var(--line); }
.practice-record:first-child { padding-top: 4px; }
.practice-record:last-child { border-bottom: 0; }
.practice-record__main { min-width: 0; }
.practice-record__heading { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.practice-record__heading h2 { margin: 0; color: var(--text); font-size: 16px; font-weight: 600; line-height: 1.65; overflow-wrap: anywhere; }
.practice-record__main p { display: flex; flex-wrap: wrap; gap: 10px; margin: 9px 0 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.practice-record__stats { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.practice-record__stats > div { display: grid; gap: 6px; }
.practice-record__stats strong { color: var(--text); font-size: 20px; font-weight: 550; font-variant-numeric: tabular-nums; }
.practice-record__stats span { color: var(--muted); font-size: 12px; }
.practice-record__actions { display: flex; align-items: center; gap: 6px; }
.practice-more-button { font-size: 21px; letter-spacing: .06em; }
.practice-view .practice-paper-layout { display: grid; grid-template-columns: minmax(0, 1fr) 240px; gap: 22px; align-items: start; }
.practice-paper-main { min-width: 0; }
.practice-view .practice-paper-hero { padding: 0; border-radius: 12px; overflow: hidden; box-shadow: none; }
.practice-paper-hero__head { padding: 26px; gap: 20px; }
.practice-paper-hero__intro { min-width: 0; }
.practice-paper-hero__title { font-size: 22px; line-height: 1.5; font-weight: 650; color: var(--text); overflow-wrap: anywhere; }
.practice-paper-hero__meta { align-items: center; gap: 16px; margin-top: 10px; color: var(--muted); font-size: 13px; }
.practice-paper-hero__meta :deep(.el-button) { height: auto; padding: 0; font-size: 12px; }
.practice-start-time { margin: 12px 0 0; color: var(--muted); font-size: 12px; }
.practice-paper-hero__score { color: var(--brand); min-width: 82px; padding-top: 4px; }
.practice-paper-hero__score strong { font-size: 38px; font-weight: 550; font-variant-numeric: tabular-nums; }
.practice-paper-hero__score span { margin-top: 10px; color: var(--muted); font-size: 12px; white-space: nowrap; }
.practice-progress { padding: 0 26px 22px; }
.practice-progress > div:first-child { display: flex; justify-content: space-between; margin-bottom: 10px; color: var(--muted); font-size: 12px; }
.practice-progress strong { color: var(--brand); font-weight: 600; }
.practice-progress p { margin: 10px 0 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
.practice-completed-note { padding: 15px 26px; background: var(--brand-light, #edf4ef); color: var(--brand); font-size: 13px; line-height: 1.8; }
.practice-view .practice-paper-section { padding: 26px; border-radius: 12px; box-shadow: none; }
.practice-paper-section__title { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; padding-bottom: 18px; border-bottom: 1px solid var(--line); color: var(--text); font-size: 16px; }
.practice-paper-section__title small { color: var(--muted); font-size: 12px; font-weight: 400; white-space: nowrap; }
.practice-paper-question { scroll-margin-top: 30px; }
.practice-paper-question + .practice-paper-question { margin-top: 30px; padding-top: 28px; border-top: 1px solid var(--line); }
.practice-paper-question__head { align-items: center; }
.practice-paper-question__index { color: var(--brand); font-size: 12px; font-weight: 600; }
.practice-question-score { color: var(--muted); font-size: 12px; }
.practice-paper-question__stem { margin: 12px 0 16px; color: var(--text); font-size: 16px; line-height: 1.85; font-weight: 550; white-space: pre-wrap; overflow-wrap: anywhere; }
.practice-paper-choice-group { display: grid; gap: 10px; margin-top: 0; }
.practice-paper-choice-group :deep(.el-radio.is-bordered) { width: 100%; height: auto; min-height: 48px; margin: 0; padding: 13px 15px; border-color: var(--line); border-radius: 8px; background: var(--panel); white-space: normal; align-items: flex-start; }
.practice-paper-choice-group :deep(.el-radio__input) { margin-top: 4px; }
.practice-paper-choice-group :deep(.el-radio__label) { line-height: 1.75; white-space: normal; overflow-wrap: anywhere; color: var(--text); font-size: 14px; padding-left: 11px; }
.practice-paper-choice-group :deep(.el-radio.is-checked) { border-color: var(--brand); background: var(--brand-light, #edf4ef); }
.practice-paper-choice-group :deep(.el-radio.is-disabled .el-radio__label) { color: var(--text); }
.practice-view .practice-paper-result { margin-top: 20px; padding: 18px; border: 1px solid var(--line); border-left: 3px solid var(--el-color-warning); border-radius: 8px; background: var(--bg); font-size: 13px; }
.practice-view .practice-paper-result--correct { border-left-color: var(--brand); }
.practice-view .practice-paper-result--pending { border-left-color: var(--muted); }
.practice-feedback-heading { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 16px; color: var(--text); font-weight: 600; }
.practice-paper-result--correct .practice-feedback-heading { color: var(--brand); }
.practice-review-answer { display: grid; grid-template-columns: 65px minmax(0, 1fr); gap: 14px; margin-top: 12px; line-height: 1.8; }
.practice-review-answer > span { color: var(--muted); }
.practice-review-answer p { margin: 0; color: var(--text); white-space: pre-wrap; overflow-wrap: anywhere; }
.practice-review-answer--reference p { color: var(--brand); }
.practice-review-analysis { margin-top: 16px; padding-top: 15px; border-top: 1px solid var(--line); color: var(--text); line-height: 1.85; }
.practice-review-analysis strong { font-size: 12px; }
.practice-review-analysis p { margin: 7px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.practice-review-sources { margin-top: 16px; }
.practice-review-sources summary { color: var(--brand); cursor: pointer; }
.practice-review-sources .rag-reference-list { margin-top: 12px; }
.practice-view .practice-paper-sidebar { top: 22px; padding: 20px; border-radius: 12px; box-shadow: none; max-height: calc(100vh - 60px); }
.practice-paper-sidebar__title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; color: var(--text); font-size: 15px; }
.practice-paper-sidebar__title span { font-size: 12px; font-weight: 400; color: var(--muted); }
.practice-sidebar-hint { margin: 0 0 22px; color: var(--muted); font-size: 12px; line-height: 1.7; }
.practice-paper-sidebar__group-title { font-size: 12px; font-weight: 500; color: var(--muted); }
.practice-paper-sidebar__grid { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.practice-paper-sidebar__item { height: 40px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); color: var(--muted); font: inherit; font-size: 13px; }
.practice-paper-sidebar__item--done, .practice-paper-sidebar__item--correct { border-color: var(--brand); background: var(--brand-light, #edf4ef); color: var(--brand); }
.practice-paper-sidebar__item--wrong { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }
.practice-paper-sidebar__item--active { box-shadow: 0 0 0 2px var(--brand); }
.practice-paper-sidebar__item:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
.practice-sidebar-legend { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.practice-sidebar-legend > span { display: flex; align-items: center; gap: 5px; }
.practice-sidebar-legend i { width: 9px; height: 9px; border: 1px solid var(--line); border-radius: 2px; }
.practice-sidebar-legend .is-done { border-color: var(--brand); background: var(--brand-light, #edf4ef); }
.practice-sidebar-legend .is-wrong { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); }
.practice-view .practice-submit-card { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 20px; padding: 22px 26px; border-radius: 12px; box-shadow: none; }
.practice-submit-card h3 { margin: 0; color: var(--text); font-size: 15px; }
.practice-submit-card p { margin: 8px 0 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.practice-submit-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.practice-submit-actions :deep(.el-button + .el-button) { margin: 0; }
.learning-empty { display: grid; justify-items: center; align-content: center; min-height: 350px; padding: 40px 20px; text-align: center; }
.learning-empty__icon { display: grid; place-items: center; width: 64px; height: 64px; margin-bottom: 20px; border: 1px solid var(--line); border-radius: 16px; color: var(--brand); background: var(--bg); }
.learning-empty__icon svg { width: 29px; height: 29px; }
.learning-empty h3 { margin: 0 0 10px; color: var(--text); font-size: 18px; font-weight: 600; }
.learning-empty p { max-width: 430px; margin: 0 0 24px; color: var(--muted); font-size: 14px; line-height: 1.8; }
.practice-empty-actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.practice-empty-actions :deep(.el-button + .el-button) { margin: 0; }
@media (max-width: 1150px) {
  .practice-view .practice-paper-layout { grid-template-columns: minmax(0, 1fr); }
  .practice-view .practice-paper-sidebar { position: static; grid-row: 1; max-height: none; }
  .practice-paper-sidebar__grid { display: flex; flex-wrap: wrap; }
  .practice-paper-sidebar__item { width: 42px; }
  .practice-paper-sidebar__group { display: inline-block; margin-right: 22px; vertical-align: top; }
  .practice-paper-sidebar__group + .practice-paper-sidebar__group { margin-top: 0; }
  .practice-sidebar-legend { margin-top: 18px; }
  .practice-record { grid-template-columns: minmax(0, 1fr) auto; gap: 18px; }
  .practice-record__main { grid-column: 1 / -1; }
}
@media (max-width: 600px) {
  .practice-view .practice-paper-section { padding: 19px 16px; }
  .practice-paper-hero__head { padding: 20px 16px; gap: 12px; }
  .practice-paper-hero__title { font-size: 18px; }
  .practice-paper-hero__meta { gap: 10px; }
  .practice-paper-hero__score { min-width: 60px; }
  .practice-paper-hero__score strong { font-size: 32px; }
  .practice-progress { padding: 0 16px 18px; }
  .practice-completed-note { padding: 14px 16px; }
  .practice-paper-question__stem { font-size: 15px; }
  .practice-view .practice-paper-result { padding: 14px; }
  .practice-review-answer { grid-template-columns: 1fr; gap: 4px; }
  .practice-record__stats { gap: 14px; }
  .practice-record__stats strong { font-size: 19px; }
  .practice-record__actions { gap: 2px; }
  .practice-view .practice-submit-card { padding: 20px 16px; }
}
</style>
