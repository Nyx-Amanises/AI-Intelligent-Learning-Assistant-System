<template>
  <section class="wrong-view">
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">温故知新</div>
        <h1 class="page-title">错题本</h1>
        <p class="page-desc">回看答错的原因，补上知识的缺口，让下一次更有把握。</p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="loading" @click="loadWrongQuestions">刷新</el-button>
        <el-button type="primary" @click="router.push('/quiz')">开始新练习</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="wrong-library-heading"><h2>待巩固的题目 <span>{{ total }}</span></h2><p>练习中的错题与低分简答题，会自动收集在这里。</p></div>
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--wrong">
          <el-input v-model="filters.keyword" clearable placeholder="搜索题目、知识点或资料" aria-label="搜索错题" class="workspace-filter-bar__search" />
          <el-select v-model="filters.materialId" clearable filterable placeholder="全部资料" aria-label="筛选资料" :loading="materialsLoading">
            <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <el-select v-model="filters.questionType" clearable placeholder="全部题型" aria-label="筛选题型">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="简答题" value="SHORT_ANSWER" />
            <el-option label="简答题（早期题集）" value="SHORT" />
          </el-select>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <div v-if="loading" class="wrong-loading" aria-live="polite"><el-skeleton :rows="5" animated /><span>正在加载错题…</span></div>
        <div v-else-if="!wrongQuestions.length" class="learning-empty">
          <div class="learning-empty__icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M4 4h6a3 3 0 0 1 3 3v14a3 3 0 0 0-3-3H4V4ZM20 4h-4a3 3 0 0 0-3 3v14a3 3 0 0 1 3-3h4V4Z" stroke-linejoin="round" /></svg></div>
          <h3>{{ hasFilters ? '没有找到匹配的错题' : '这里暂时没有需要复习的错题' }}</h3>
          <p>{{ hasFilters ? '试试其他关键词、资料或题型。' : '完成练习后，需要巩固的题目会保存在这里，方便随时回顾。' }}</p>
          <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
          <el-button v-else type="primary" @click="router.push('/quiz')">去做一次练习</el-button>
        </div>
        <div v-else class="wrong-question-list">
          <article v-for="item in wrongQuestions" :key="item.answerId" class="wrong-question-card">
            <div class="wrong-question-card__top"><div><span class="wrong-type">{{ formatQuestionType(item.questionType) }}</span><span class="wrong-difficulty">{{ formatDifficulty(item.difficultyLevel) }}</span></div><span class="wrong-score">{{ item.obtainedScore ?? 0 }} <small>/ {{ item.fullScore || 0 }} 分</small></span></div>
            <button type="button" class="wrong-question-card__stem" @click="openDetail(item)">{{ item.stemText }}</button>
            <p v-if="item.knowledgePoint" class="wrong-question-card__knowledge">知识点 · {{ item.knowledgePoint }}</p>
            <div class="wrong-question-card__footer">
              <div class="wrong-question-card__source"><span :title="item.materialTitle">{{ item.materialTitle || '未关联资料' }}</span><small>{{ formatDateTime(item.answerTime || item.createdAt).slice(0, 10) }}</small></div>
              <div class="wrong-question-card__actions">
                <el-button type="primary" plain @click="openDetail(item)">查看解析</el-button>
                <el-dropdown trigger="click" @command="handleQuestionAction($event, item)">
                  <el-button text :loading="removingAnswerId === item.answerId" aria-label="错题更多操作" class="wrong-more-button">···</el-button>
                  <template #dropdown><el-dropdown-menu><el-dropdown-item command="practice">查看原练习</el-dropdown-item><el-dropdown-item command="remove" divided>移出错题本</el-dropdown-item></el-dropdown-menu></template>
                </el-dropdown>
              </div>
            </div>
          </article>
        </div>

        <div v-if="total" class="workspace-pagination">
          <div class="workspace-pagination__meta">共 {{ total }} 道错题</div>
          <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="loadWrongQuestions" @size-change="loadWrongQuestions" />
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="错题解析" size="min(700px, 100vw)" class="wrong-detail-drawer">
      <div v-if="detailLoading" class="state-block">正在加载解析…</div>
      <template v-else-if="detail">
        <div class="wrong-detail-context"><span>{{ detail.materialTitle || '未关联资料' }}</span><span>{{ detail.sessionName || '练习回顾' }}</span></div>
        <div class="wrong-detail-question">
          <div class="wrong-detail-question__meta"><span class="wrong-type">{{ formatQuestionType(detail.questionType) }}</span><span class="wrong-score">{{ detail.obtainedScore ?? 0 }} <small>/ {{ detail.fullScore || 0 }} 分</small></span></div>
          <h2>{{ detail.stemText }}</h2>
          <div v-if="hasOptions(detail)" class="wrong-detail-options">
            <div v-for="option in buildOptions(detail)" :key="option.value" :class="{ 'wrong-option--correct': detail.correctAnswer === option.value, 'wrong-option--selected': detail.userAnswer === option.value && detail.correctAnswer !== option.value }">
              <span>{{ option.value }}. {{ option.label }}</span>
              <small v-if="detail.correctAnswer === option.value">正确答案</small><small v-else-if="detail.userAnswer === option.value">我的选择</small>
            </div>
          </div>
        </div>

        <div class="wrong-answer-comparison">
          <div><span>我的答案</span><p>{{ formatAnswerText(detail, detail.userAnswer) }}</p></div>
          <div class="wrong-answer-comparison__reference"><span>参考答案</span><p>{{ formatAnswerText(detail, detail.correctAnswer) }}</p></div>
        </div>
        <div v-if="detail.answerAnalysis" class="wrong-analysis"><h3>为什么这样回答？</h3><p>{{ detail.answerAnalysis }}</p></div>
        <div v-if="detail.reviewComment" class="wrong-analysis"><h3>评分反馈 <span>{{ formatReviewMode(detail.reviewMode) }}</span></h3><p>{{ detail.reviewComment }}</p></div>
        <div v-if="detail.knowledgePoint" class="wrong-knowledge"><span>建议复习的知识点</span><p>{{ detail.knowledgePoint }}</p></div>
      </template>
      <div v-else class="state-block empty">暂时无法读取这道题，请关闭后重试。</div>
      <template #footer>
        <template v-if="detail && !detailLoading"><el-button :loading="removingAnswerId === detail.answerId" @click="removeWrongQuestion(detail, true)">移出错题本</el-button><el-button type="primary" @click="goPractice(detail.sessionId)">查看原练习</el-button></template>
        <el-button v-else @click="drawerVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import {
  getWrongQuestionDetailApi,
  getWrongQuestionPageApi,
  removeWrongQuestionApi,
  type WrongQuestionItem,
  type WrongQuestionPagePayload
} from '@/api/modules/wrongQuestion'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const materialsLoading = ref(false)
const removingAnswerId = ref<number | null>(null)
const wrongQuestions = ref<WrongQuestionItem[]>([])
const materials = ref<MaterialPageItem[]>([])
const detail = ref<WrongQuestionItem | null>(null)
const total = ref(0)

const filters = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  materialId: typeof route.query.materialId === 'string' && Number(route.query.materialId)
    ? Number(route.query.materialId)
    : undefined as number | undefined,
  questionType: typeof route.query.questionType === 'string' ? route.query.questionType : ''
})

const hasFilters = computed(() => Boolean(filters.keyword || filters.materialId || filters.questionType))
const formatDifficulty = (value?: number) => {
  const labels: Record<number, string> = { 1: '入门', 2: '基础', 3: '适中', 4: '进阶', 5: '挑战' }
  return labels[value || 3] || '适中'
}
const buildOptions = (item: WrongQuestionItem) =>
  [
    { value: 'A', label: item.optionA },
    { value: 'B', label: item.optionB },
    { value: 'C', label: item.optionC },
    { value: 'D', label: item.optionD }
  ].filter((option) => Boolean(option.label))
const formatAnswerText = (item: WrongQuestionItem, value?: string) => {
  if (!value) return '未作答'
  const option = buildOptions(item).find((entry) => entry.value === value)
  return option ? `${option.value}. ${option.label}` : value
}
const handleQuestionAction = (command: string, item: WrongQuestionItem) => {
  if (command === 'practice') goPractice(item.sessionId)
  if (command === 'remove') void removeWrongQuestion(item)
}

const page = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.materialId, () => filters.questionType],
  () => {
    page.current = 1
    return loadWrongQuestions()
  }
)

const formatDateTime = (value?: string) => {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ').slice(0, 19)
}

const formatQuestionType = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SINGLE':
      return '单选题'
    case 'JUDGE':
      return '判断题'
    case 'SHORT':
    case 'SHORT_ANSWER':
      return '简答题'
    default:
      return '其他题型'
  }
}

const formatReviewMode = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'AI':
      return 'AI 评分'
    case 'AI_PENDING':
      return 'AI 评分中'
    case 'RULE':
      return '自动判分'
    default:
      return '自动评分'
  }
}

const hasOptions = (item: WrongQuestionItem) =>
  Boolean(item.optionA || item.optionB || item.optionC || item.optionD)

const loadMaterials = async () => {
  materialsLoading.value = true
  try {
    const res = await getMaterialPageApi({
      current: 1,
      size: 50,
      parseStatus: 'SUCCESS'
    })
    materials.value = res.data.data.records || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载资料列表失败')
  } finally {
    materialsLoading.value = false
  }
}

const loadWrongQuestions = async () => {
  loading.value = true
  try {
    const res = await getWrongQuestionPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword || undefined,
      materialId: filters.materialId || undefined,
      questionType: filters.questionType || undefined
    })
    const data = res.data.data as WrongQuestionPagePayload
    wrongQuestions.value = data.records || []
    total.value = data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载错题本失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.materialId = undefined
    filters.questionType = ''
    page.current = 1
  })
}

const openDetail = async (item: WrongQuestionItem) => {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getWrongQuestionDetailApi(item.answerId)
    detail.value = res.data.data as WrongQuestionItem
  } catch (error: any) {
    detail.value = null
    ElMessage.error(error.message || '加载错题详情失败')
  } finally {
    detailLoading.value = false
  }
}

const removeWrongQuestion = async (item: WrongQuestionItem, closeDrawer = false) => {
  try {
    await ElMessageBox.confirm('移出后这道题不会再显示在错题本中，原练习记录仍会保留，确定继续吗？', '移出确认', {
      type: 'warning',
      confirmButtonText: '确认移出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  removingAnswerId.value = item.answerId
  try {
    await removeWrongQuestionApi(item.answerId)
    ElMessage.success('已移出错题本')
    if (closeDrawer || detail.value?.answerId === item.answerId) {
      drawerVisible.value = false
      detail.value = null
    }
    if (wrongQuestions.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    await loadWrongQuestions()
  } catch (error: any) {
    ElMessage.error(error.message || '移出错题本失败')
  } finally {
    removingAnswerId.value = null
  }
}

const goPractice = (sessionId?: number) => {
  if (!sessionId) {
    return
  }
  router.push({ path: '/practice', query: { sessionId: String(sessionId) } })
}

void loadMaterials()
void loadWrongQuestions()
</script>

<style scoped>
.learning-eyebrow { margin-bottom: 8px; color: var(--brand); font-size: 12px; font-weight: 650; letter-spacing: .12em; }
.wrong-library-heading { padding: 24px 24px 0; }
.wrong-library-heading h2 { display: flex; align-items: center; gap: 9px; margin: 0; font-size: 17px; }
.wrong-library-heading h2 span { min-width: 24px; padding: 2px 7px; color: var(--muted); border: 1px solid var(--line); border-radius: 6px; font-size: 12px; font-weight: 500; text-align: center; }
.wrong-library-heading p { margin: 7px 0 0; color: var(--muted); font-size: 13px; line-height: 1.7; }
.wrong-view .workspace-filter-bar--wrong { display: grid; grid-template-columns: minmax(200px, 1.6fr) minmax(150px, 1fr) minmax(130px, .75fr) auto; width: 100%; margin-bottom: 0; }
.wrong-view .workspace-toolbar { margin-top: 6px; }
.wrong-loading { display: grid; gap: 24px; padding: 24px; color: var(--muted); font-size: 13px; }
.wrong-question-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.wrong-question-card { display: flex; flex-direction: column; min-width: 0; padding: 22px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); }
.wrong-question-card__top { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.wrong-question-card__top > div { display: flex; align-items: center; gap: 12px; }
.wrong-type { display: inline-flex; padding: 4px 8px; border-radius: 5px; color: var(--brand); background: var(--brand-light, #edf4ef); font-size: 12px; font-weight: 500; }
.wrong-difficulty { font-size: 12px; color: var(--muted); }
.wrong-score { font-size: 18px; font-weight: 550; font-variant-numeric: tabular-nums; color: var(--text); white-space: nowrap; }
.wrong-score small { color: var(--muted); font-size: 12px; font-weight: 400; }
.wrong-question-card__stem { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; min-height: 78px; max-width: 100%; margin: 18px 0 10px; padding: 0; overflow: hidden; border: 0; background: none; color: var(--text); text-align: left; font: inherit; font-size: 15px; font-weight: 550; line-height: 1.8; overflow-wrap: anywhere; cursor: pointer; }
.wrong-question-card__stem:hover { color: var(--brand); }
.wrong-question-card__stem:focus-visible { outline: 2px solid var(--brand); outline-offset: 5px; border-radius: 3px; }
.wrong-question-card__knowledge { margin: 0 0 20px; color: var(--muted); font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.wrong-question-card__footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: auto; padding-top: 16px; border-top: 1px solid var(--line); }
.wrong-question-card__source { display: grid; gap: 6px; min-width: 0; color: var(--muted); font-size: 12px; }
.wrong-question-card__source span { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.wrong-question-card__source small { font-size: 11px; }
.wrong-question-card__actions { display: flex; align-items: center; flex-shrink: 0; gap: 4px; }
.wrong-more-button { font-size: 21px; letter-spacing: .06em; }
.learning-empty { display: grid; justify-items: center; align-content: center; min-height: 340px; padding: 40px 20px; text-align: center; }
.learning-empty__icon { display: grid; place-items: center; width: 64px; height: 64px; margin-bottom: 20px; border: 1px solid var(--line); border-radius: 16px; color: var(--brand); background: var(--bg); }
.learning-empty__icon svg { width: 29px; height: 29px; }
.learning-empty h3 { margin: 0 0 10px; color: var(--text); font-size: 18px; font-weight: 600; }
.learning-empty p { max-width: 440px; margin: 0 0 24px; color: var(--muted); font-size: 14px; line-height: 1.8; }
.wrong-detail-context { display: grid; gap: 7px; margin-bottom: 22px; color: var(--muted); font-size: 12px; line-height: 1.8; }
.wrong-detail-question__meta { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.wrong-detail-question h2 { margin: 18px 0; font-size: 18px; font-weight: 600; color: var(--text); line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; }
.wrong-detail-options { display: grid; gap: 10px; }
.wrong-detail-options > div { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 8px; color: var(--text); font-size: 14px; line-height: 1.8; }
.wrong-detail-options small { flex-shrink: 0; font-size: 11px; }
.wrong-detail-options .wrong-option--correct { border-color: var(--brand); color: var(--brand); background: var(--brand-light, #edf4ef); }
.wrong-detail-options .wrong-option--selected { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }
.wrong-answer-comparison { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 24px; }
.wrong-answer-comparison > div { padding: 18px; border-radius: 8px; border: 1px solid var(--line); background: var(--bg); }
.wrong-answer-comparison span { color: var(--muted); font-size: 12px; }
.wrong-answer-comparison p { margin: 9px 0 0; color: var(--text); font-size: 14px; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; }
.wrong-answer-comparison .wrong-answer-comparison__reference { background: var(--brand-light, #edf4ef); }
.wrong-answer-comparison__reference p { color: var(--brand); }
.wrong-analysis { margin-top: 24px; padding-top: 22px; border-top: 1px solid var(--line); }
.wrong-analysis h3 { display: flex; align-items: center; gap: 12px; margin: 0; color: var(--text); font-size: 14px; }
.wrong-analysis h3 span { color: var(--muted); font-size: 12px; font-weight: 400; }
.wrong-analysis p { margin: 12px 0 0; color: var(--text); font-size: 14px; line-height: 1.95; white-space: pre-wrap; overflow-wrap: anywhere; }
.wrong-knowledge { margin-top: 26px; padding: 18px; border-radius: 8px; background: var(--bg); }
.wrong-knowledge > span { color: var(--muted); font-size: 12px; }
.wrong-knowledge p { margin: 8px 0 0; color: var(--brand); font-size: 14px; line-height: 1.85; }
@media (max-width: 1050px) {
  .wrong-question-list { grid-template-columns: 1fr; }
  .wrong-view .workspace-filter-bar--wrong { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 600px) {
  .wrong-library-heading { padding: 18px 16px 0; }
  .wrong-view .workspace-filter-bar--wrong { grid-template-columns: minmax(0, 1fr); }
  .wrong-question-card { padding: 18px; }
  .wrong-question-card__footer { flex-wrap: wrap; }
  .wrong-answer-comparison { grid-template-columns: 1fr; }
  .wrong-detail-options > div { flex-wrap: wrap; gap: 5px; }
}
</style>
