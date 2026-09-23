<template>
  <section class="learning-page practice-view">
    <nav class="practice-navigation" aria-label="练习导航">
      <RouterLink to="/quiz" class="practice-back-link" @click="savePracticeDraft">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
          <path d="m10 5-7 7 7 7M3 12h18" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span>返回题集列表</span>
      </RouterLink>
    </nav>
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">练习与复盘</div>
        <h1 class="page-title">我的练习</h1>
        <p class="page-desc">专注完成每一道题，也把每一次答错变成进步的起点。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button :loading="historyLoading" @click="loadHistory">刷新记录</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-tabs" aria-label="练习视图">
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'current' }"
            :aria-pressed="activeTab === 'current'"
            @click="activeTab = 'current'"
          >
            当前练习
          </button>
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'history' }"
            :aria-pressed="activeTab === 'history'"
            @click="activeTab = 'history'"
          >
            历史记录 <span class="practice-tab-count">{{ total }}</span>
          </button>
        </div>
        <StatusPill
          v-if="practiceDetail && activeTab === 'current'"
          :tone="statusTone(practiceDetail.sessionStatus)"
          :label="formatPracticeStatus(practiceDetail.sessionStatus)"
        />
      </div>

      <div v-if="activeTab === 'history'" class="workspace-body">
        <PracticeHistoryList
          v-model:current="page.current"
          v-model:size="page.size"
          :records="historyRecords"
          :total="total"
          :loading="historyLoading"
          :error="historyError"
          :action-loading-id="actionLoadingId"
          @open="openPracticeFromHistory"
          @reload="loadHistory"
          @page-change="loadHistory"
          @rename="openRenameDialog({ id: $event.id, sessionName: $event.sessionName })"
          @restart="restartPractice($event.questionSetId)"
          @delete="removePractice($event.id)"
        />
      </div>

      <div v-else class="workspace-body">
        <div v-if="detailLoading" class="practice-loading" aria-live="polite">
          <el-skeleton :rows="6" animated /><span>正在准备练习…</span>
        </div>
        <div v-else-if="practiceDetail" class="practice-paper-layout">
          <PracticePaper
            v-model:answer-form="answerForm"
            :practice-detail="practiceDetail"
            :sections="groupedSections"
            :submitted="isSubmitted"
            :has-pending-ai-review="hasPendingAiReview"
            :pending-review-text="pendingReviewText"
            :answered-count="answeredCount"
            :unanswered-count="unansweredCount"
            :completion-percentage="completionPercentage"
            :submitting="submitting"
            :restarting="restarting"
            @submit="submitPractice"
            @back="returnToQuiz"
            @restart="restartPractice(practiceDetail.questionSetId)"
            @rename="openRenameDialog({ id: practiceDetail.sessionId, sessionName: practiceDetail.sessionName })"
          />
          <PracticeAnswerSheet
            :sections="groupedSections"
            :answer-form="answerForm"
            :submitted="isSubmitted"
            :active-question-id="activeQuestionId"
            :answered-count="answeredCount"
            :total-questions="practiceDetail.totalQuestions || 0"
            @select="scrollToQuestion"
          />
        </div>
        <EmptyState
          v-else-if="detailError && detailSessionId"
          variant="error"
          icon="refresh"
          title="练习暂时没能打开"
          :description="detailError"
        >
          <el-button @click="activeTab = 'history'">查看历史记录</el-button>
          <el-button type="primary" @click="loadPracticeDetail(detailSessionId)">重新加载</el-button>
        </EmptyState>
        <EmptyState
          v-else
          icon="practice"
          title="给自己留一段专注学习的时间"
          :description="
            total
              ? '在历史记录中继续未完成的练习，或选择一套新题集。'
              : '从一套小练习开始，检验理解，再有针对性地复习。'
          "
        >
          <el-button v-if="total" @click="activeTab = 'history'">查看历史记录</el-button>
          <el-button type="primary" @click="router.push('/quiz')">选择题集</el-button>
        </EmptyState>
      </div>
    </div>

    <RenameDialog
      v-model="renameDialogVisible"
      title="修改练习名称"
      label="练习名称"
      placeholder="输入便于日后查找的名称"
      :initial-value="renameTarget?.sessionName || ''"
      :loading="renaming"
      @save="renamePracticeSession"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
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
import EmptyState from '@/components/EmptyState.vue'
import RenameDialog from '@/components/RenameDialog.vue'
import StatusPill from '@/components/StatusPill.vue'
import PracticeAnswerSheet from '@/components/practice/PracticeAnswerSheet.vue'
import PracticeHistoryList from '@/components/practice/PracticeHistoryList.vue'
import PracticePaper from '@/components/practice/PracticePaper.vue'
import {
  countAnswered,
  groupAnswers,
  hasAnswerValue,
  isPendingReview,
  isSubmittedStatus,
  type AnswerForm,
  type PracticeDetail,
  type PracticeHistoryItem
} from '@/components/practice/practiceHelpers'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { isAiTaskSuccess, isAiTaskTerminal, waitForAiTaskUntilTerminal } from '@/utils/aiTask'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatPracticeStatus, statusTone } from '@/utils/labels'
import { readStorage, readStorageJson, removeStorage, writeStorage, writeStorageJson } from '@/utils/storage'

const PRACTICE_DRAFT_KEY_PREFIX = 'ai-learning-assistant:practice-draft:'
const PRACTICE_ACTIVE_DRAFT_KEY = 'ai-learning-assistant:practice-draft:active'
const DRAFT_SAVE_INTERVAL = 800

const route = useRoute()
const router = useRouter()
const historyRecords = ref<PracticeHistoryItem[]>([])
const total = ref(0)
const practiceDetail = ref<PracticeDetail | null>(null)
const answerForm = ref<AnswerForm>({})
const historyLoading = ref(false)
const historyError = ref('')
const detailLoading = ref(false)
const detailError = ref('')
const detailSessionId = ref<number | null>(null)
const submitting = ref(false)
const restarting = ref(false)
const renaming = ref(false)
const activeTab = ref<'current' | 'history'>('current')
const actionLoadingId = ref<number | null>(null)
const activeQuestionId = ref<number | null>(null)
const currentReviewTask = ref<AiTaskDetail | null>(null)
const reviewTaskWaiting = ref(false)
const renameDialogVisible = ref(false)
const renameTarget = ref<{ id: number; sessionName: string } | null>(null)
let reviewTaskWaitAborted = false
let isViewActive = true
const page = reactive({ current: 1, size: 10 })

const isSubmitted = computed(() => isSubmittedStatus(practiceDetail.value?.sessionStatus))
const hasPendingAiReview = computed(() =>
  Boolean(isSubmitted.value && practiceDetail.value?.answers?.some((item) => isPendingReview(item)))
)
const pendingReviewText = computed(() => {
  if (!hasPendingAiReview.value) return ''
  return reviewTaskWaiting.value && currentReviewTask.value?.id
    ? '客观题已批改，AI 正在认真评阅你的简答题。'
    : '客观题已批改，简答题正在等待评分。结果会自动更新。'
})
const groupedSections = computed(() => groupAnswers(practiceDetail.value?.answers))
const answeredCount = computed(() => countAnswered(practiceDetail.value?.answers, answerForm.value))
const unansweredCount = computed(() => Math.max(0, (practiceDetail.value?.answers?.length || 0) - answeredCount.value))
const completionPercentage = computed(() => {
  const count = practiceDetail.value?.answers?.length || 0
  return count ? Math.round((answeredCount.value / count) * 100) : 0
})

// ---------- 草稿：本地保存未提交的答案 ----------

const getPracticeDraftKey = (sessionId: number | string) => `${PRACTICE_DRAFT_KEY_PREFIX}${sessionId}`

const isCurrentPracticeDraftable = () => Boolean(practiceDetail.value?.sessionId && !isSubmitted.value)

const readPracticeDraft = (sessionId: number | string): AnswerForm | null => {
  const parsed = readStorageJson<Record<string, unknown> | null>(getPracticeDraftKey(sessionId), null)
  if (!parsed || typeof parsed !== 'object') return null
  const draft: AnswerForm = {}
  Object.entries(parsed).forEach(([questionId, value]) => {
    const normalizedId = Number(questionId)
    if (Number.isFinite(normalizedId) && value !== undefined && value !== null) draft[normalizedId] = String(value)
  })
  return draft
}

const readActivePracticeDraftSessionId = () => {
  const sessionId = Number(readStorage(PRACTICE_ACTIVE_DRAFT_KEY))
  return Number.isFinite(sessionId) ? sessionId : 0
}

const clearPracticeDraft = (sessionId?: number | string) => {
  const targetSessionId = sessionId ?? practiceDetail.value?.sessionId
  if (!targetSessionId) return
  removeStorage(getPracticeDraftKey(targetSessionId))
  if (readStorage(PRACTICE_ACTIVE_DRAFT_KEY) === String(targetSessionId)) removeStorage(PRACTICE_ACTIVE_DRAFT_KEY)
}

let draftSaveTimer: number | undefined

/** 立即把当前答案写入本地草稿（会先取消排队中的延迟写入）。 */
const savePracticeDraft = () => {
  window.clearTimeout(draftSaveTimer)
  draftSaveTimer = undefined
  if (!isCurrentPracticeDraftable() || !practiceDetail.value) return
  const sessionId = practiceDetail.value.sessionId
  if (!Object.values(answerForm.value).some(hasAnswerValue)) {
    clearPracticeDraft(sessionId)
    return
  }
  writeStorageJson(getPracticeDraftKey(sessionId), answerForm.value)
  writeStorage(PRACTICE_ACTIVE_DRAFT_KEY, String(sessionId))
}

/** 作答时节流写入：最多每 800ms 写一次，最后一次改动一定会落盘。 */
const scheduleDraftSave = () => {
  if (draftSaveTimer !== undefined) return
  draftSaveTimer = window.setTimeout(savePracticeDraft, DRAFT_SAVE_INTERVAL)
}

watch(answerForm, scheduleDraftSave, { deep: true })

const restorePracticeDraft = () => {
  const sessionId = practiceDetail.value?.sessionId
  if (!sessionId || isSubmitted.value) return
  const draft = readPracticeDraft(sessionId)
  if (!draft || !Object.keys(draft).length) return
  answerForm.value = { ...answerForm.value, ...draft }
}

const syncAnswerForm = () => {
  const form: AnswerForm = {}
  ;(practiceDetail.value?.answers || []).forEach((item) => {
    form[item.questionId] = item.userAnswer || ''
  })
  answerForm.value = form
}

const returnToQuiz = () => {
  savePracticeDraft()
  void router.push('/quiz')
}

// ---------- 简答题 AI 评分等待 ----------

const resetReviewTaskWaiting = () => {
  reviewTaskWaitAborted = true
  reviewTaskWaiting.value = false
}

const ensurePracticeReviewTask = async (sessionId: number) => {
  if (reviewTaskWaiting.value) return
  reviewTaskWaiting.value = true
  reviewTaskWaitAborted = false
  try {
    const submitRes = await submitPracticeReviewTaskApi(sessionId)
    if (reviewTaskWaitAborted) return
    const submittedTask = submitRes.data.data as AiTaskDetail
    currentReviewTask.value = submittedTask

    // 评分慢时持续等待，直到任务结束或用户离开本页；偶发网络错误会自动重试。
    const finishedTask = await waitForAiTaskUntilTerminal(submittedTask.id, {
      shouldStop: () => reviewTaskWaitAborted,
      onProgress: (task) => {
        currentReviewTask.value = task
      }
    })
    if (reviewTaskWaitAborted || !finishedTask || !isAiTaskTerminal(finishedTask.status)) return
    currentReviewTask.value = finishedTask
    if (!isAiTaskSuccess(finishedTask.status)) {
      throw new Error(finishedTask.errorMessage || '简答题评分任务执行失败')
    }

    await Promise.all([loadPracticeDetail(sessionId, true), loadHistory()])
    if (!reviewTaskWaitAborted) ElMessage.success('简答题 AI 评分已完成')
  } catch (error) {
    if (!reviewTaskWaitAborted) ElMessage.error(getErrorMessage(error, '简答题评分任务失败'))
  } finally {
    reviewTaskWaiting.value = false
  }
}

// ---------- 加载 ----------

const scrollToQuestion = (questionId: number) => {
  activeQuestionId.value = questionId
  const target = document.getElementById(`question-${questionId}`)
  target?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    block: 'start'
  })
}

const loadHistory = async () => {
  historyLoading.value = true
  historyError.value = ''
  try {
    const res = await getPracticePageApi({ current: page.current, size: page.size })
    historyRecords.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error) {
    historyError.value = getErrorMessage(error, '练习记录暂时无法加载，请稍后重试。')
  } finally {
    historyLoading.value = false
  }
}

const practiceDetailRequest = useLatestRequest()

/** 只负责拉取并展示练习详情；地址栏的 sessionId 由调用方通过 syncSessionQuery 同步。 */
const loadPracticeDetail = async (sessionId: number, silent = false) => {
  if (!isViewActive) return
  savePracticeDraft()
  const requestId = practiceDetailRequest.begin()
  detailLoading.value = true
  detailError.value = ''
  detailSessionId.value = sessionId
  try {
    const res = await getPracticeDetailApi(sessionId)
    if (!isViewActive || !practiceDetailRequest.isLatest(requestId)) return
    practiceDetail.value = res.data.data as PracticeDetail
    syncAnswerForm()
    if (isSubmitted.value) clearPracticeDraft(sessionId)
    else restorePracticeDraft()
    activeQuestionId.value = practiceDetail.value?.answers?.[0]?.questionId || null
    if (hasPendingAiReview.value) {
      if (currentReviewTask.value?.bizId !== sessionId) currentReviewTask.value = null
      if (!silent) void ensurePracticeReviewTask(sessionId)
    } else {
      currentReviewTask.value = null
    }
  } catch (error) {
    if (!isViewActive || !practiceDetailRequest.isLatest(requestId)) return
    practiceDetail.value = null
    detailError.value = getErrorMessage(error, '这次练习暂时无法打开，请稍后重试。')
  } finally {
    if (practiceDetailRequest.isLatest(requestId)) detailLoading.value = false
  }
}

const syncSessionQuery = (sessionId?: number | null) => {
  if (route.path !== '/practice') return
  const current = typeof route.query.sessionId === 'string' ? route.query.sessionId : ''
  const next = sessionId ? String(sessionId) : ''
  if (current === next) return
  void router.replace({ path: '/practice', query: next ? { sessionId: next } : {} })
}

const openPracticeFromHistory = async (sessionId: number) => {
  resetReviewTaskWaiting()
  syncSessionQuery(sessionId)
  await loadPracticeDetail(sessionId)
  activeTab.value = 'current'
}

// ---------- 操作 ----------

const openRenameDialog = (item: { id: number; sessionName: string }) => {
  renameTarget.value = item
  renameDialogVisible.value = true
}

const renamePracticeSession = async (sessionName: string) => {
  const target = renameTarget.value
  if (!target) return
  renaming.value = true
  try {
    await renamePracticeApi(target.id, sessionName)
    ElMessage.success('练习名称已更新')
    renameDialogVisible.value = false
    if (practiceDetail.value?.sessionId === target.id) {
      practiceDetail.value = { ...practiceDetail.value, sessionName }
    }
    await loadHistory()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '修改练习名称失败'))
  } finally {
    renaming.value = false
  }
}

const restartPractice = async (questionSetId?: number) => {
  if (restarting.value) return
  if (!questionSetId) {
    ElMessage.warning('这条记录缺少题集信息，无法重新练习')
    return
  }
  restarting.value = true
  try {
    const res = await startPracticeApi(questionSetId)
    ElMessage.success('新的练习已开始')
    page.current = 1
    resetReviewTaskWaiting()
    await loadHistory()
    const sessionId = Number(res.data.data.sessionId)
    syncSessionQuery(sessionId)
    await loadPracticeDetail(sessionId)
    activeTab.value = 'current'
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '重新开始练习失败'))
  } finally {
    restarting.value = false
  }
}

const removePractice = async (sessionId: number) => {
  if (!(await confirmDanger('删除后这次练习记录和答案将不可恢复，确定继续吗？'))) return
  const clearCurrent = practiceDetail.value?.sessionId === sessionId
  actionLoadingId.value = sessionId
  try {
    await deletePracticeApi(sessionId)
    ElMessage.success('练习记录已删除')
    clearPracticeDraft(sessionId)
    if (clearCurrent) {
      resetReviewTaskWaiting()
      practiceDetail.value = null
      detailSessionId.value = null
      syncSessionQuery(null)
      activeTab.value = 'history'
    } else if (historyRecords.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    await loadHistory()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除练习失败'))
  } finally {
    actionLoadingId.value = null
  }
}

const submitPractice = async () => {
  if (submitting.value || !practiceDetail.value?.sessionId) return
  if (unansweredCount.value > 0) {
    const confirmed = await confirmDanger(
      `还有 ${unansweredCount.value} 道题未作答。提交后将不能修改答案，确定提交吗？`,
      { title: '检查你的练习', confirmText: '仍然提交', cancelText: '继续作答' }
    )
    if (!confirmed) return
  }

  submitting.value = true
  try {
    const sessionId = practiceDetail.value.sessionId
    const answers = (practiceDetail.value.answers || []).map((item) => ({
      questionId: item.questionId,
      userAnswer: answerForm.value[item.questionId] || ''
    }))
    const res = await submitPracticeApi(sessionId, answers)
    clearPracticeDraft(sessionId)
    practiceDetail.value = res.data.data as PracticeDetail
    syncAnswerForm()
    await loadHistory()
    if (hasPendingAiReview.value) {
      ElMessage.success('练习已提交，简答题正在等待 AI 评分')
      void ensurePracticeReviewTask(sessionId)
      return
    }
    ElMessage.success('练习提交成功')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '提交练习失败'))
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadHistory()
  if (!isViewActive) return
  const querySessionId = Number(route.query.sessionId)
  const sessionId = querySessionId || readActivePracticeDraftSessionId()
  if (sessionId) {
    syncSessionQuery(sessionId)
    await loadPracticeDetail(sessionId)
    activeTab.value = 'current'
  } else if (historyRecords.value.length) {
    activeTab.value = 'history'
  }
})

onUnmounted(() => {
  isViewActive = false
  savePracticeDraft()
  resetReviewTaskWaiting()
})
</script>

<style scoped>
.practice-navigation {
  display: flex;
  margin-bottom: 18px;
}
.practice-back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.5;
  text-decoration: none;
}
.practice-back-link svg {
  width: 18px;
  height: 18px;
  flex: none;
}
.practice-back-link:hover {
  color: var(--brand);
}
.practice-back-link:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 4px;
  border-radius: 4px;
}
.practice-tab-count {
  margin-left: 6px;
  opacity: 0.7;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.practice-loading {
  display: grid;
  gap: 24px;
  padding: 24px;
  color: var(--muted);
  font-size: 13px;
}
.practice-paper-layout {
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 22px;
}
@media (max-width: 1150px) {
  .practice-paper-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .practice-paper-layout > .practice-paper-sidebar {
    grid-row: 1;
  }
}
@media (max-width: 600px) {
  .practice-navigation {
    margin-bottom: 12px;
  }
  .practice-back-link {
    min-height: 44px;
  }
}
</style>
