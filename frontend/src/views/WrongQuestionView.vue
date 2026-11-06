<template>
  <section class="learning-page wrong-view" aria-labelledby="wrong-view-title">
    <header class="page-header">
      <div>
        <h1 id="wrong-view-title" class="page-title">错题本</h1>
        <p class="page-desc">看看错在哪里，再做一遍。重做会保留原成绩，掌握后可以手动移出错题本。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button type="primary" @click="router.push('/quiz')"><AppIcon name="plus" :size="16" />选择题集</el-button>
      </div>
    </header>

    <div class="workspace-panel">
      <div class="workspace-toolbar workspace-toolbar--filter">
        <div class="filter-bar" role="search" aria-label="筛选错题">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索题目、知识点或资料"
            aria-label="搜索错题"
            @keyup.enter="autoQuery.runNow"
            ><template #prefix><AppIcon name="search" :size="16" /></template
          ></el-input>
          <el-select
            v-model="filters.materialId"
            clearable
            filterable
            placeholder="全部资料"
            aria-label="筛选资料"
            :loading="materialsLoading"
          >
            <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <el-select v-model="filters.questionType" clearable placeholder="全部题型" aria-label="筛选题型">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
          <el-select
            v-model="filters.knowledgePoint"
            clearable
            filterable
            placeholder="全部知识点"
            aria-label="精确筛选知识点"
            :loading="knowledgePointsLoading"
          >
            <el-option
              v-if="selectedKnowledgePointMissing"
              :label="filters.knowledgePoint + '（当前范围无匹配）'"
              :value="filters.knowledgePoint"
            />
            <el-option
              v-for="item in knowledgePoints"
              :key="item.knowledgePoint"
              :label="item.knowledgePoint + '（' + item.wrongCount + ' 道）'"
              :value="item.knowledgePoint"
            />
          </el-select>
          <el-button v-if="hasFilters" text @click="resetFilters">清除筛选</el-button>
        </div>
        <el-button text :loading="loading" aria-label="刷新错题列表" @click="loadWrongQuestions">
          <AppIcon v-if="!loading" name="refresh" :size="16" /><span class="workspace-toolbar__refresh-label"
            >刷新</span
          >
        </el-button>
      </div>
      <p v-if="knowledgePointsError" class="wrong-filter-note" role="alert">
        {{ knowledgePointsError }}，可继续使用其他筛选条件。
        <el-button link type="primary" :loading="knowledgePointsLoading" @click="loadKnowledgePoints"
          >重试知识点</el-button
        >
      </p>
      <p v-if="filters.knowledgePoint" class="wrong-filter-note">
        知识点：{{ filters.knowledgePoint }}
        <el-button link @click="filters.knowledgePoint = ''">清除知识点</el-button>
      </p>
      <div v-if="loading" class="record-list" role="status" aria-label="正在加载错题">
        <div v-for="i in 4" :key="i" class="list-row record-row record-row--loading" aria-hidden="true">
          <span class="skeleton record-row__sk-lead" />
          <div class="record-row__main">
            <span class="skeleton record-row__sk-title" /><span class="skeleton record-row__sk-meta" />
          </div>
          <span class="skeleton record-row__sk-actions" />
        </div>
      </div>
      <EmptyState
        v-else-if="loadError"
        variant="error"
        icon="refresh"
        title="错题列表没有加载出来"
        :description="loadError"
      >
        <el-button type="primary" @click="loadWrongQuestions">重新加载</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!wrongQuestions.length"
        icon="wrong"
        :title="hasFilters ? '没有找到匹配的错题' : '目前没有待复习的错题'"
        :description="
          hasFilters ? '试试其他关键词、资料、题型或知识点。' : '练习中答错的题目会保存在这里，可查看解析或重新作答。'
        "
      >
        <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
        <el-button v-else type="primary" @click="router.push('/quiz')">选择题集</el-button>
      </EmptyState>
      <ul v-else class="record-list">
        <li v-for="item in wrongQuestions" :key="item.answerId" class="list-row record-row wrong-row">
          <span class="wrong-row__icon" aria-hidden="true"><AppIcon name="wrong" :size="19" /></span>
          <div class="record-row__main">
            <h2 class="record-row__title wrong-row__title">
              <button type="button" @click="openDetail(item)">{{ item.stemText }}</button>
            </h2>
            <p class="record-row__desc" :title="item.materialTitle">{{ item.materialTitle || '未关联资料' }}</p>
            <p class="record-row__meta">
              <span>{{ formatQuestionType(item.questionType) }}</span>
              <span>{{ formatDifficulty(item.difficultyLevel) }}</span>
              <span>{{ item.obtainedScore ?? 0 }} / {{ item.fullScore || 0 }} 分</span>
              <time
                :datetime="item.answerTime || item.createdAt"
                :title="formatDateTime(item.answerTime || item.createdAt)"
                >{{ formatRelativeDay(item.answerTime || item.createdAt) }}</time
              >
              <span v-if="item.knowledgePoint" class="record-row__chip" :title="item.knowledgePoint">{{
                item.knowledgePoint
              }}</span>
              <GenerationModeBadge class="record-row__chip" :mode="item.generationMode" />
            </p>
          </div>
          <div class="record-row__actions" role="group" :aria-label="item.stemText + '的操作'">
            <el-button
              size="small"
              type="primary"
              plain
              :loading="redoingAnswerId === item.answerId"
              :disabled="redoingAnswerId !== null || removingAnswerId !== null"
              @click="redoQuestion(item)"
              >重做此题</el-button
            >
            <el-button size="small" text @click="openDetail(item)">查看解析</el-button>
            <el-dropdown trigger="click" @command="handleQuestionAction($event, item)">
              <IconMenuButton :label="item.stemText + '的更多操作'" :loading="removingAnswerId === item.answerId" />
              <template #dropdown
                ><el-dropdown-menu
                  ><el-dropdown-item command="practice">查看原练习</el-dropdown-item
                  ><el-dropdown-item command="remove" divided class="menu-item--danger"
                    >移出错题本</el-dropdown-item
                  ></el-dropdown-menu
                ></template
              >
            </el-dropdown>
          </div>
        </li>
      </ul>

      <div v-if="!loading && !loadError && total" class="workspace-pagination">
        <div class="workspace-pagination__meta">共 {{ total }} 道错题</div>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]"
          :pager-count="5"
          layout="sizes, prev, pager, next"
          :total="total"
          @current-change="loadWrongQuestions"
          @size-change="loadWrongQuestions"
        />
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="错题解析" size="min(720px, 100vw)" class="wrong-detail-drawer">
      <div v-if="detailLoading" class="state-block">正在加载解析…</div>
      <template v-else-if="detail">
        <div class="wrong-detail-context">
          <span>{{ detail.materialTitle || '未关联资料' }}</span
          ><span>{{ detail.sessionName || '练习回顾' }}</span>
        </div>
        <div class="wrong-detail-question">
          <div class="wrong-detail-question__meta">
            <StatusPill tone="brand" hide-dot :label="formatQuestionType(detail.questionType)" />
            <GenerationModeBadge :mode="detail.generationMode" /><span class="wrong-score"
              >{{ detail.obtainedScore ?? 0 }} <small>/ {{ detail.fullScore || 0 }} 分</small></span
            >
          </div>
          <h2>{{ detail.stemText }}</h2>
          <div v-if="hasOptions(detail)" class="wrong-detail-options">
            <div
              v-for="option in buildOptions(detail)"
              :key="option.value"
              :class="{
                'wrong-option--correct': answerIncludesOption(detail, detail.correctAnswer, option.value),
                'wrong-option--selected':
                  answerIncludesOption(detail, detail.userAnswer, option.value) &&
                  !answerIncludesOption(detail, detail.correctAnswer, option.value)
              }"
            >
              <span>{{ option.label }}</span>
              <small v-if="answerIncludesOption(detail, detail.correctAnswer, option.value)">{{
                answerIncludesOption(detail, detail.userAnswer, option.value) ? '正确 · 已选' : '正确 · 未选'
              }}</small
              ><small v-else-if="answerIncludesOption(detail, detail.userAnswer, option.value)">我的选择</small>
            </div>
          </div>
        </div>

        <div class="wrong-answer-comparison">
          <div>
            <span>我的答案</span>
            <p>{{ formatAnswer(detail, detail.userAnswer) }}</p>
          </div>
          <div class="wrong-answer-comparison__reference">
            <span>参考答案</span>
            <p>{{ formatAnswer(detail, detail.correctAnswer) }}</p>
          </div>
        </div>
        <div v-if="detail.answerAnalysis" class="wrong-analysis">
          <h3>答案解析</h3>
          <p>{{ detail.answerAnalysis }}</p>
        </div>
        <div v-if="detail.reviewComment" class="wrong-analysis">
          <h3>
            评分反馈 <span>{{ formatReviewMode(detail.reviewMode) }}</span>
          </h3>
          <p>{{ detail.reviewComment }}</p>
        </div>
        <div v-if="detail.knowledgePoint" class="wrong-knowledge">
          <span>建议复习的知识点</span>
          <p>{{ detail.knowledgePoint }}</p>
        </div>
        <p class="wrong-detail-time">作答于 {{ formatDateTime(detail.answerTime || detail.createdAt) }}</p>
      </template>
      <EmptyState
        v-else
        variant="error"
        title="这道题的解析没有加载出来"
        :description="detailError || '稍后再试一次。'"
      >
        <el-button v-if="detailTarget" type="primary" @click="openDetail(detailTarget)">重新加载</el-button>
      </EmptyState>
      <template #footer>
        <template v-if="detail && !detailLoading"
          ><el-button :loading="removingAnswerId === detail.answerId" @click="removeWrongQuestion(detail, true)"
            >移出错题本</el-button
          ><el-button @click="goPractice(detail.sessionId)">查看原练习</el-button>
          <el-button
            type="primary"
            :loading="redoingAnswerId === detail.answerId"
            :disabled="redoingAnswerId !== null || removingAnswerId !== null"
            @click="redoQuestion(detail)"
            >重做此题</el-button
          ></template
        >
        <el-button v-else @click="drawerVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import GenerationModeBadge from '@/components/GenerationModeBadge.vue'
import { answerIncludesOption, buildOptions, formatAnswer } from '@/components/practice/practiceHelpers'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useParsedMaterials } from '@/composables/useParsedMaterials'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime, formatRelativeDay } from '@/utils/format'
import { formatDifficulty, formatQuestionType, formatReviewMode } from '@/utils/labels'
import {
  getWrongQuestionDetailApi,
  getWrongQuestionPageApi,
  removeWrongQuestionApi,
  redoWrongQuestionApi,
  getWrongQuestionKnowledgePointsApi,
  type WrongQuestionKnowledgePoint,
  type WrongQuestionItem,
  type WrongQuestionPagePayload
} from '@/api/modules/wrongQuestion'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const loadError = ref('')
const detailLoading = ref(false)
const drawerVisible = ref(false)
const removingAnswerId = ref<number | null>(null)
const redoingAnswerId = ref<number | null>(null)
let viewDisposed = false
const wrongQuestions = ref<WrongQuestionItem[]>([])
const detail = ref<WrongQuestionItem | null>(null)
const detailTarget = ref<WrongQuestionItem | null>(null)
const detailError = ref('')
const total = ref(0)
const knowledgePoints = ref<WrongQuestionKnowledgePoint[]>([])
const knowledgePointsLoading = ref(false)
const knowledgePointsError = ref('')
const listRequest = useLatestRequest()
const knowledgePointsRequest = useLatestRequest()
const questionTypeAliases: Record<string, string> = { SHORT: 'SHORT_ANSWER', MULTI: 'MULTIPLE' }
const { materials, loading: materialsLoading, error: materialsError, load: loadMaterials } = useParsedMaterials()

const filters = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  materialId:
    typeof route.query.materialId === 'string' && Number(route.query.materialId)
      ? Number(route.query.materialId)
      : (undefined as number | undefined),
  questionType:
    typeof route.query.questionType === 'string'
      ? questionTypeAliases[route.query.questionType] || route.query.questionType
      : '',
  knowledgePoint: typeof route.query.knowledgePoint === 'string' ? route.query.knowledgePoint : ''
})

const hasFilters = computed(() =>
  Boolean(filters.keyword || filters.materialId || filters.questionType || filters.knowledgePoint)
)
const selectedKnowledgePointMissing = computed(
  () =>
    Boolean(filters.knowledgePoint) &&
    !knowledgePoints.value.some((item) => item.knowledgePoint === filters.knowledgePoint)
)

const hasOptions = (item: WrongQuestionItem) => Boolean(item.optionA || item.optionB || item.optionC || item.optionD)
const handleQuestionAction = (command: string, item: WrongQuestionItem) => {
  if (command === 'practice') goPractice(item.sessionId)
  if (command === 'remove') void removeWrongQuestion(item)
}

const page = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.materialId, () => filters.questionType, () => filters.knowledgePoint],
  () => {
    page.current = 1
    return loadWrongQuestions()
  }
)

// 条件一变就使旧请求失效，包括防抖等待期间到达的响应。
watch(
  [() => filters.keyword, () => filters.materialId, () => filters.questionType, () => filters.knowledgePoint],
  () => {
    listRequest.invalidate()
    loading.value = true
  },
  { flush: 'sync' }
)

watch(
  [() => filters.materialId, () => filters.questionType],
  () => {
    knowledgePointsRequest.invalidate()
    knowledgePoints.value = []
    knowledgePointsLoading.value = true
  },
  { flush: 'sync' }
)

watch([() => filters.materialId, () => filters.questionType], () => {
  void loadKnowledgePoints()
})

const loadKnowledgePoints = async () => {
  if (viewDisposed) return
  const requestId = knowledgePointsRequest.begin()
  knowledgePointsLoading.value = true
  knowledgePointsError.value = ''
  try {
    const response = await getWrongQuestionKnowledgePointsApi({
      materialId: filters.materialId || undefined,
      questionType: filters.questionType || undefined
    })
    if (viewDisposed || !knowledgePointsRequest.isLatest(requestId)) return
    knowledgePoints.value = (response.data.data as WrongQuestionKnowledgePoint[]) || []
  } catch (error) {
    if (!viewDisposed && knowledgePointsRequest.isLatest(requestId))
      knowledgePointsError.value = getErrorMessage(error, '知识点选项暂时无法加载')
  } finally {
    if (!viewDisposed && knowledgePointsRequest.isLatest(requestId)) knowledgePointsLoading.value = false
  }
}

const loadWrongQuestions = async () => {
  if (viewDisposed) return
  const requestId = listRequest.begin()
  loading.value = true
  loadError.value = ''
  try {
    const res = await getWrongQuestionPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword || undefined,
      materialId: filters.materialId || undefined,
      questionType: filters.questionType || undefined,
      knowledgePoint: filters.knowledgePoint || undefined
    })
    const data = res.data.data as WrongQuestionPagePayload
    if (viewDisposed || !listRequest.isLatest(requestId)) return
    wrongQuestions.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    if (!viewDisposed && listRequest.isLatest(requestId))
      loadError.value = getErrorMessage(error, '错题本暂时无法加载，请稍后重试。')
  } finally {
    if (!viewDisposed && listRequest.isLatest(requestId)) loading.value = false
  }
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.materialId = undefined
    filters.questionType = ''
    filters.knowledgePoint = ''
    page.current = 1
  })
}

const detailRequest = useLatestRequest()

const openDetail = async (item: WrongQuestionItem) => {
  const requestId = detailRequest.begin()
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  detailTarget.value = item
  detailError.value = ''
  try {
    const res = await getWrongQuestionDetailApi(item.answerId)
    if (!detailRequest.isLatest(requestId)) return
    detail.value = res.data.data as WrongQuestionItem
  } catch (error) {
    if (!detailRequest.isLatest(requestId)) return
    detail.value = null
    detailError.value = getErrorMessage(error, '暂时无法读取这道题的解析，请稍后重试。')
  } finally {
    if (detailRequest.isLatest(requestId)) detailLoading.value = false
  }
}

const removeWrongQuestion = async (item: WrongQuestionItem, closeDrawer = false) => {
  if (redoingAnswerId.value !== null || removingAnswerId.value !== null) return
  const confirmed = await confirmDanger('移出后这道题不会再显示在错题本中，原练习记录仍会保留，确定继续吗？', {
    title: '移出确认',
    confirmText: '确认移出'
  })
  if (!confirmed) return

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
    await Promise.all([loadWrongQuestions(), loadKnowledgePoints()])
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '移出错题本失败'))
  } finally {
    removingAnswerId.value = null
  }
}

const redoQuestion = async (item: WrongQuestionItem) => {
  if (redoingAnswerId.value !== null || removingAnswerId.value !== null) return
  redoingAnswerId.value = item.answerId
  try {
    const response = await redoWrongQuestionApi(item.answerId)
    if (viewDisposed) return
    const sessionId = Number(response.data.data?.sessionId)
    if (!Number.isSafeInteger(sessionId) || sessionId <= 0) throw new Error('新练习未能打开，请稍后重试。')
    ElMessage.success('已开启此题的独立练习，原成绩与错题保留')
    await router.push({ path: '/practice', query: { sessionId: String(sessionId) } })
  } catch (error) {
    if (!viewDisposed) ElMessage.error(getErrorMessage(error, '重做此题失败，请稍后重试。'))
  } finally {
    if (!viewDisposed) redoingAnswerId.value = null
  }
}

const goPractice = (sessionId?: number) => {
  if (!sessionId) {
    return
  }
  router.push({ path: '/practice', query: { sessionId: String(sessionId) } })
}

void loadMaterials().then(() => {
  if (materialsError.value) ElMessage.error(materialsError.value)
})
void loadWrongQuestions()
void loadKnowledgePoints()
onUnmounted(() => {
  viewDisposed = true
  listRequest.invalidate()
  knowledgePointsRequest.invalidate()
})
</script>

<style scoped>
.wrong-filter-note {
  margin: 0;
  padding: 10px 20px;
  border-bottom: 1px solid var(--line);
  background: var(--panel-hover);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.wrong-row__icon {
  display: grid;
  flex: 0 0 var(--record-lead);
  place-items: center;
  height: var(--record-lead);
  border-radius: var(--radius);
  background: var(--accent-soft);
  color: var(--accent);
}
.wrong-row__title > button:first-child {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.wrong-score {
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.wrong-score small {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
.wrong-detail-context {
  display: grid;
  gap: 7px;
  margin-bottom: 22px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.wrong-detail-question__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.wrong-detail-question h2 {
  margin: 18px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.wrong-detail-options {
  display: grid;
  gap: 10px;
}
.wrong-detail-options > div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--text);
  font-size: 14px;
  line-height: 1.8;
}
.wrong-detail-options small {
  flex-shrink: 0;
  font-size: 11px;
}
.wrong-detail-options .wrong-option--correct {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-light);
}
.wrong-detail-options .wrong-option--selected {
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
}
.wrong-answer-comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 24px;
}
.wrong-answer-comparison > div {
  padding: 18px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--bg);
}
.wrong-answer-comparison span {
  color: var(--muted);
  font-size: 12px;
}
.wrong-answer-comparison p {
  margin: 9px 0 0;
  color: var(--text);
  font-size: 14px;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.wrong-answer-comparison .wrong-answer-comparison__reference {
  background: var(--brand-light);
}
.wrong-answer-comparison__reference p {
  color: var(--brand);
}
.wrong-analysis {
  margin-top: 24px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}
.wrong-analysis h3 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  color: var(--text);
  font-size: 14px;
}
.wrong-analysis h3 span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
.wrong-analysis p {
  margin: 12px 0 0;
  color: var(--text);
  font-size: 14px;
  line-height: 1.95;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.wrong-knowledge {
  margin-top: 26px;
  padding: 18px;
  border-radius: 8px;
  background: var(--bg);
}
.wrong-knowledge > span {
  color: var(--muted);
  font-size: 12px;
}
.wrong-knowledge p {
  margin: 8px 0 0;
  color: var(--brand);
  font-size: 14px;
  line-height: 1.85;
}
.wrong-detail-time {
  margin: 22px 0 0;
  color: var(--muted);
  font-size: 12px;
}
@media (max-width: 760px) {
  .wrong-filter-note {
    padding-inline: 16px;
  }
  .wrong-answer-comparison {
    grid-template-columns: 1fr;
  }
  .wrong-detail-options > div {
    flex-wrap: wrap;
    gap: 5px;
  }
  .wrong-detail-question__meta {
    flex-wrap: wrap;
    gap: 8px;
  }
  .wrong-detail-drawer :deep(.el-drawer__footer) {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .wrong-detail-drawer :deep(.el-drawer__footer .el-button) {
    flex: 1 1 auto;
    margin: 0;
  }
}
</style>
