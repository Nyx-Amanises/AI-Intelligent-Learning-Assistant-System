<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">练习记录</h1>
        <p class="page-desc">历史记录保留后台列表，当前练习详情改成更接近学习通的作业详情视图。</p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="historyLoading" @click="loadHistory">刷新记录</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-tabs">
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'current' }"
            @click="activeTab = 'current'"
          >
            当前练习
          </button>
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'history' }"
            @click="activeTab = 'history'"
          >
            历史练习
          </button>
        </div>

        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">共 {{ total }} 条记录</span>
          <span v-if="practiceDetail" class="workspace-chip workspace-chip--brand">
            {{ practiceDetail.sessionStatus }}
          </span>
        </div>
      </div>

      <div v-if="activeTab === 'history'" class="workspace-body">
        <div class="page-card workspace-card">
          <div class="workspace-table" v-if="historyRecords.length">
            <div class="workspace-table__head workspace-table__head--practice">
              <span>练习名称</span>
              <span>状态</span>
              <span>正确率</span>
              <span>得分</span>
              <span>操作</span>
            </div>

            <div
              v-for="item in historyRecords"
              :key="item.id"
              class="workspace-table__row workspace-table__row--practice"
            >
              <div class="workspace-table__title workspace-table__title--truncate">
                <strong :title="item.sessionName">{{ item.sessionName }}</strong>
                <span>#{{ item.id }} · {{ item.correctCount || 0 }}/{{ item.totalQuestions || 0 }} 题正确</span>
              </div>
              <span>{{ item.sessionStatus }}</span>
              <span>{{ Number(item.accuracyRate || 0).toFixed(0) }}%</span>
              <span>{{ item.obtainedScore || 0 }}</span>
              <div class="workspace-action-row workspace-action-row--fit">
                <el-button link type="primary" @click="openPracticeFromHistory(item.id)">查看</el-button>
                <el-button
                  link
                  type="success"
                  @click="restartPractice(item.questionSetId || practiceDetail?.questionSetId)"
                >
                  再练一次
                </el-button>
                <el-button
                  link
                  type="danger"
                  :loading="actionLoadingId === item.id"
                  @click="removePractice(item.id)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>
          <div class="workspace-pagination" v-if="historyRecords.length">
            <div class="workspace-pagination__meta">第 {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页</div>
            <el-pagination
              v-model:current-page="page.current"
              v-model:page-size="page.size"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="total"
              @current-change="loadHistory"
              @size-change="loadHistory"
            />
          </div>
          <div v-else-if="historyLoading" class="state-block">正在加载练习记录...</div>
          <div v-else class="state-block empty">暂无练习记录，先去 AI 出题页面生成一套题目吧。</div>
        </div>
      </div>

      <div v-else class="workspace-body">
        <div v-if="detailLoading" class="page-card workspace-card">
          <div class="state-block">正在加载练习详情...</div>
        </div>

        <template v-else-if="practiceDetail">
          <div class="practice-paper-layout">
            <div class="practice-paper-main">
              <div class="page-card practice-paper-hero">
                <div class="practice-paper-hero__head">
                  <div>
                    <h2 class="practice-paper-hero__title">{{ practiceDetail.sessionName }}</h2>
                    <div class="practice-paper-hero__meta">
                      <span>题量: {{ practiceDetail.totalQuestions }}</span>
                      <span>满分: {{ practiceDetail.totalScore || 0 }}</span>
                      <span>状态: {{ practiceDetail.sessionStatus }}</span>
                    </div>
                    <div class="practice-paper-hero__meta">
                      <span>开始时间: {{ formatDateTime(practiceDetail.startTime) }}</span>
                      <span v-if="practiceDetail.submitTime">提交时间: {{ formatDateTime(practiceDetail.submitTime) }}</span>
                    </div>
                  </div>
                  <div class="practice-paper-hero__score">
                    <strong>{{ practiceDetail.obtainedScore || 0 }}</strong>
                    <span>分</span>
                  </div>
                </div>
              </div>

              <el-alert
                v-if="hasPendingAiReview"
                :title="pendingReviewText"
                type="info"
                :closable="false"
                show-icon
              />

              <div
                v-for="(section, sectionIndex) in groupedSections"
                :key="section.type"
                class="page-card practice-paper-section"
              >
                <div class="practice-paper-section__title">
                  {{ toSectionLabel(sectionIndex) }}. {{ section.label }}（共{{ section.items.length }}题）
                </div>

                <div
                  v-for="(answer, index) in section.items"
                  :id="`question-${answer.questionId}`"
                  :key="answer.questionId"
                  class="practice-paper-question"
                >
                  <div class="practice-paper-question__head">
                    <div>
                      <div class="practice-paper-question__index">
                        {{ answer.globalIndex }}. <span class="practice-paper-question__type-tag">({{ section.label }})</span>
                      </div>
                      <div class="practice-paper-question__stem">{{ answer.stemText }}</div>
                    </div>
                    <el-button link type="primary">AI讲解</el-button>
                  </div>

                  <template v-if="isChoiceQuestion(answer)">
                    <el-radio-group
                      v-model="answerForm[answer.questionId]"
                      class="choice-group practice-paper-choice-group"
                      :disabled="practiceDetail.sessionStatus === 'SUBMITTED'"
                    >
                      <el-radio
                        v-for="option in buildOptions(answer)"
                        :key="option.value"
                        :label="option.value"
                        border
                        class="choice-option practice-paper-choice"
                      >
                        {{ option.label }}
                      </el-radio>
                    </el-radio-group>
                  </template>

                  <template v-else>
                    <el-input
                      v-model="answerForm[answer.questionId]"
                      :disabled="practiceDetail.sessionStatus === 'SUBMITTED'"
                      type="textarea"
                      :rows="4"
                      placeholder="请输入你的答案"
                    />
                  </template>

                  <div
                    v-if="practiceDetail.sessionStatus === 'SUBMITTED'"
                    class="practice-paper-result"
                    :class="{ 'practice-paper-result--short': isShortQuestion(answer) }"
                  >
                    <div class="practice-paper-result__top">
                      <div class="practice-paper-result__answer-row">
                        <strong>我的答案:</strong>
                        <span>{{ formatAnswer(answer, answer.userAnswer) }}</span>
                      </div>

                      <template v-if="isShortQuestion(answer)">
                        <div class="practice-paper-result__answer-row practice-paper-result__answer-row--brand">
                          <strong>AI判分:</strong>
                          <span>{{ answer.reviewLabel || `AI 判分：${answer.aiScore || 0}` }}</span>
                        </div>
                        <div class="practice-paper-result__answer-row practice-paper-result__answer-row--success">
                          <strong>人工参考答案:</strong>
                          <span>{{ answer.referenceAnswer || '暂无参考答案' }}</span>
                        </div>
                      </template>

                      <template v-else>
                        <div class="practice-paper-result__answer-row practice-paper-result__answer-row--success">
                          <strong>正确答案:</strong>
                          <span>{{ formatAnswer(answer, answer.referenceAnswer || answer.correctAnswer) }}</span>
                        </div>
                      </template>

                      <div
                        class="practice-paper-result__score"
                        :class="{ 'practice-paper-result__score--pending': isPendingReview(answer) }"
                      >
                        <span>{{ answer.isCorrect ? '✓' : '×' }}</span>
                        <strong>{{ answer.obtainedScore || answer.aiScore || 0 }} 分</strong>
                      </div>
                    </div>

                    <div class="practice-paper-result__analysis">
                      <div v-if="isShortQuestion(answer) && answer.reviewComment" class="practice-paper-result__analysis-row">
                        <strong>AI评语:</strong>
                        <span>{{ answer.reviewComment }}</span>
                      </div>
                      <div class="practice-paper-result__analysis-row">
                        <strong>答案解析:</strong>
                        <span>{{ answer.answerAnalysis || '暂无解析' }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="page-card workspace-card">
                <div class="workspace-card__header">
                  <div>
                    <h3>本次操作</h3>
                  </div>
                </div>
                <div class="toolbar" style="margin-bottom: 0">
                  <el-button
                    v-if="practiceDetail.sessionStatus !== 'SUBMITTED'"
                    type="primary"
                    :loading="submitting"
                    @click="submitPractice"
                  >
                    提交练习
                  </el-button>
                  <el-button type="success" @click="restartPractice(practiceDetail.questionSetId)">再练一次</el-button>
                  <el-button
                    type="danger"
                    plain
                    :loading="actionLoadingId === practiceDetail.sessionId"
                    @click="removePractice(practiceDetail.sessionId, true)"
                  >
                    删除本次练习
                  </el-button>
                </div>
              </div>
            </div>

            <aside class="page-card practice-paper-sidebar">
              <div class="practice-paper-sidebar__title">题目导航</div>
              <div
                v-for="section in groupedSections"
                :key="section.type"
                class="practice-paper-sidebar__group"
              >
                <div class="practice-paper-sidebar__group-title">
                  {{ section.sidebarTitle }}
                </div>
                <div class="practice-paper-sidebar__grid">
                  <button
                    v-for="answer in section.items"
                    :key="answer.questionId"
                    type="button"
                    class="practice-paper-sidebar__item"
                    :class="{
                      'practice-paper-sidebar__item--active': activeQuestionId === answer.questionId,
                      'practice-paper-sidebar__item--done': Boolean(answer.userAnswer),
                      'practice-paper-sidebar__item--correct': practiceDetail.sessionStatus === 'SUBMITTED' && answer.isCorrect
                    }"
                    @click="scrollToQuestion(answer.questionId)"
                  >
                    {{ answer.globalIndex }}
                  </button>
                </div>
              </div>
            </aside>
          </div>
        </template>

        <div v-else class="page-card workspace-card">
          <div class="state-block empty">先到“历史练习”里点一条记录，这里再展示完整答题内容。</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { submitPracticeReviewTaskApi, type AiTaskDetail } from '@/api/modules/ai'
import {
  deletePracticeApi,
  getPracticeDetailApi,
  getPracticePageApi,
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
const activeTab = ref<'current' | 'history'>('current')
const actionLoadingId = ref<number | null>(null)
const activeQuestionId = ref<number | null>(null)
const currentReviewTask = ref<AiTaskDetail | null>(null)
const reviewTaskWaiting = ref(false)
let reviewTaskWaitAborted = false
const page = reactive({
  current: 1,
  size: 10
})

const questionTypeOrder: Record<string, number> = {
  JUDGE: 1,
  MULTI: 2,
  SINGLE: 3,
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
    return `客观题已完成判分，简答题正在由 AI 评分（任务 #${currentReviewTask.value.id}）`
  }
  return '客观题已完成判分，简答题等待任务中心处理'
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

  return Array.from(groups.entries())
    .sort((a, b) => (questionTypeOrder[a[0]] || 99) - (questionTypeOrder[b[0]] || 99))
    .map(([type, items]) => ({
      type,
      label: toChineseTypeTitle(type),
      sidebarTitle: `${toChineseTypeTitle(type)}（${items.length}题）`,
      items
    }))
})

const syncAnswerForm = () => {
  const form: Record<number, string> = {}
  ;(practiceDetail.value?.answers || []).forEach((item: any) => {
    form[item.questionId] = item.userAnswer || ''
  })
  answerForm.value = form
}

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
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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

const restartPractice = async (questionSetId?: number) => {
  if (!questionSetId) {
    ElMessage.warning('当前缺少题集信息，无法重新练习')
    return
  }
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
  if (!practiceDetail.value?.sessionId) {
    return
  }

  submitting.value = true
  try {
    const answers = (practiceDetail.value.answers || []).map((item: any) => ({
      questionId: item.questionId,
      userAnswer: answerForm.value[item.questionId] || ''
    }))

    const res = await submitPracticeApi(practiceDetail.value.sessionId, answers)
    practiceDetail.value = res.data.data
    syncAnswerForm()
    if (hasPendingAiReview.value) {
      await loadHistory()
      ElMessage.success('客观题已完成判分，简答题评分任务已进入任务中心')
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
  } else if (historyRecords.value.length) {
    activeTab.value = 'history'
  }
})

onUnmounted(() => {
  resetReviewTaskWaiting()
})
</script>
