<template>
  <section class="learning-page summary-view" aria-labelledby="summary-view-title">
    <header class="page-header">
      <div>
        <h1 id="summary-view-title" class="page-title">学习总结</h1>
        <p class="page-desc">把资料整理成总结、考试重点或提纲，阅读时可以随时对照原文。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button type="primary" @click="openGenerateDialog"><AppIcon name="plus" :size="16" />生成总结</el-button>
      </div>
    </header>

    <div class="workspace-panel">
      <div class="workspace-toolbar workspace-toolbar--filter">
        <div class="filter-bar" role="search" aria-label="筛选总结">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索资料名称或总结内容"
            aria-label="搜索资料名称或总结内容"
          >
            <template #prefix><AppIcon name="search" :size="16" /></template>
          </el-input>
          <el-select
            v-model="filterMaterialId"
            clearable
            filterable
            placeholder="全部资料"
            aria-label="按资料筛选"
            :loading="materialsLoading"
          >
            <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <el-select v-model="filterSummaryType" clearable placeholder="全部类型" aria-label="按类型筛选">
            <el-option v-for="mode in summaryModes" :key="mode.value" :label="mode.title" :value="mode.value" />
          </el-select>
          <el-button v-if="hasActiveFilters" text @click="resetFilters">清除筛选</el-button>
        </div>
        <el-button text :loading="historyLoading" aria-label="刷新总结列表" @click="loadSummaryHistory()">
          <AppIcon v-if="!historyLoading" name="refresh" :size="16" /><span class="workspace-toolbar__refresh-label"
            >刷新</span
          >
        </el-button>
      </div>

      <p v-if="taskStatusText" class="workspace-progress" role="status">
        <span class="workspace-progress__spinner" aria-hidden="true" />{{ taskStatusText }}
      </p>
      <div v-if="historyLoading" class="record-list" role="status" aria-label="正在加载总结">
        <div v-for="i in 4" :key="i" class="list-row record-row record-row--loading" aria-hidden="true">
          <span class="skeleton record-row__sk-lead" />
          <div class="record-row__main">
            <span class="skeleton record-row__sk-title" />
            <span class="skeleton record-row__sk-meta" />
            <span class="skeleton summary-row__sk-excerpt" />
          </div>
          <span class="skeleton record-row__sk-actions" />
        </div>
      </div>
      <EmptyState v-else-if="loadError" variant="error" title="总结列表没有加载出来" :description="loadError">
        <el-button type="primary" @click="loadSummaryHistory()">重新加载</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!pagedSummaryHistory.length && filteredMaterialHasNoSummary"
        icon="summary"
        title="这份资料还没有总结"
        description="生成一份总结，把重点先理一遍。"
      >
        <el-button type="primary" @click="openGenerateDialog">生成总结</el-button>
        <el-button @click="resetFilters">查看全部总结</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!pagedSummaryHistory.length && summaryHistory.length"
        icon="search"
        title="没有找到匹配的总结"
        description="换个关键词，或者清除筛选条件再看看。"
      >
        <el-button @click="resetFilters">清除筛选</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!pagedSummaryHistory.length"
        icon="summary"
        title="还没有学习总结"
        :description="
          materials.length
            ? '选一份已解析的资料，生成总结、考试重点或提纲。'
            : '先在资料库上传一份资料，解析完成后就能生成总结。'
        "
      >
        <el-button v-if="materials.length" type="primary" @click="openGenerateDialog">生成总结</el-button>
        <el-button v-else type="primary" @click="router.push('/materials')">去资料库</el-button>
      </EmptyState>
      <ul v-else class="record-list">
        <li v-for="item in pagedSummaryHistory" :key="item.recordId" class="list-row record-row summary-row">
          <span
            class="summary-row__icon"
            :class="'summary-row__icon--' + summaryTypeIcon(item.summaryType).tone"
            aria-hidden="true"
          >
            <AppIcon :name="summaryTypeIcon(item.summaryType).icon" :size="18" />
          </span>
          <div class="record-row__main">
            <h2 class="record-row__title">
              <button v-if="isSummaryReady(item)" type="button" @click="openSummaryDialog(item)">
                {{ item.materialTitle || '未命名资料' }}
              </button>
              <span v-else>{{ item.materialTitle || '未命名资料' }}</span>
              <StatusPill
                v-if="!isSummaryReady(item)"
                :tone="statusTone(getSummaryStatus(item))"
                :label="formatSummaryStatus(getSummaryStatus(item))"
              />
              <GenerationModeBadge v-else-if="item.generationMode === 'MOCK'" :mode="item.generationMode" />
            </h2>
            <p class="summary-row__excerpt">{{ buildCardExcerpt(item) }}</p>
            <p class="record-row__meta">
              <span>{{ formatSummaryType(item.summaryType) }}</span>
              <time :datetime="item.createdAt" :title="formatDateTime(item.createdAt)">{{
                formatRelativeDay(item.createdAt)
              }}</time>
              <span v-if="isSummaryReady(item) && item.sourceSegments?.length"
                >引用 {{ item.sourceSegments.length }} 处原文</span
              >
            </p>
          </div>
          <div class="record-row__actions" role="group" :aria-label="`${item.materialTitle || '这份总结'}的操作`">
            <el-button v-if="isSummaryReady(item)" size="small" @click="openSummaryDialog(item)">阅读</el-button>
            <el-button v-else-if="getSummaryStatus(item) === 'FAILED'" size="small" @click="router.push('/ai-tasks')"
              >查看原因</el-button
            >
            <el-button size="small" text @click="previewMaterial(item)">原文</el-button>
            <el-button
              size="small"
              text
              :disabled="getSummaryStatus(item) === 'GENERATING'"
              @click="quickGenerateForMaterial(item.materialId)"
              >重新生成</el-button
            >
          </div>
        </li>
      </ul>

      <footer v-if="!historyLoading && !loadError && filteredSummaryHistory.length" class="workspace-pagination">
        <span class="workspace-pagination__meta">{{
          hasActiveFilters
            ? `找到 ${filteredSummaryHistory.length} 份总结`
            : `共 ${filteredSummaryHistory.length} 份总结`
        }}</span>
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          :total="filteredSummaryHistory.length"
        />
      </footer>
    </div>

    <el-dialog
      v-model="generateDialogVisible"
      title="生成总结"
      width="min(680px, calc(100vw - 32px))"
      destroy-on-close
      class="summary-dialog"
    >
      <EmptyState
        v-if="!materialsLoading && !materialsError && !materials.length"
        variant="compact"
        icon="folder"
        title="还没有可用的资料"
        description="资料上传并解析完成后，才能生成总结。"
      >
        <el-button type="primary" @click="router.push('/materials')">去资料库</el-button>
      </EmptyState>
      <el-form v-else label-position="top">
        <p v-if="materialsError" class="summary-form-note summary-form-note--error" role="alert">
          资料列表没有加载出来：{{ materialsError }}
          <el-button link type="primary" :loading="materialsLoading" @click="loadMaterials">重试</el-button>
        </p>
        <el-form-item label="资料">
          <el-select
            v-model="materialId"
            filterable
            style="width: 100%"
            placeholder="选择一份已解析的资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="item.title + ' · ' + formatCharacters(item.totalCharacters)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="summaryType" class="summary-mode-options">
            <el-radio v-for="mode in summaryModes" :key="mode.value" :value="mode.value" border>
              <strong>{{ mode.title }}</strong
              ><span>{{ mode.description }}</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <p class="summary-form-note">
          一般需要几十秒。留在这个页面，生成好会自动打开；离开页面也会继续生成，进度可以在任务中心查看。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="generating"
          :disabled="!materialId || !materials.length"
          @click="generateSummary"
          >开始生成</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="summaryDialogVisible"
      :title="activeSummary?.materialTitle || '学习总结'"
      width="min(900px, calc(100vw - 32px))"
      top="5vh"
      :close-on-click-modal="false"
      destroy-on-close
      class="summary-dialog summary-reader"
    >
      <template #header="{ titleId, titleClass }">
        <p class="summary-reader__eyebrow">
          <span>{{ formatSummaryType(activeSummary?.summaryType) }}</span>
          <span>{{ formatDateTime(activeSummary?.createdAt) }}</span>
          <GenerationModeBadge :mode="activeSummary?.generationMode" />
        </p>
        <h2 :id="titleId" :class="[titleClass, 'dialog-title', 'summary-reader__title']">
          {{ activeSummary?.materialTitle || '学习总结' }}
        </h2>
      </template>
      <article class="summary-reader__body">
        <p v-if="activeSummary?.generationMode === 'MOCK'" class="summary-reader__mock">
          这份总结由演示模式生成，只用来体验流程，内容不一定准确。
        </p>
        <LearningNoteContent :text="activeSummary?.summaryText" />
      </article>
      <details class="summary-sources">
        <summary>
          引用的原文 <span>{{ activeSummary?.sourceSegments?.length || 0 }} 处</span>
        </summary>
        <SourceSegmentList :segments="activeSummary?.sourceSegments" empty-text="这份总结没有记录引用的原文。" />
      </details>
      <template #footer>
        <el-button @click="previewMaterial(activeSummary)">查看原文</el-button>
        <el-button type="primary" @click="goQuiz(activeSummary?.materialId)">根据这份资料出题</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewDialogVisible"
      title="原文"
      width="min(900px, calc(100vw - 32px))"
      top="5vh"
      destroy-on-close
      class="summary-dialog"
    >
      <template #header="{ titleId, titleClass }">
        <h2 :id="titleId" :class="[titleClass, 'dialog-title']">
          {{ selectedMaterialDetail?.title || previewTitle || '原文' }}
        </h2>
        <div v-if="selectedMaterialDetail" class="dialog-subtitle">
          原文 · {{ formatCharacters(selectedMaterialDetail.totalCharacters) }}
        </div>
      </template>
      <div v-if="detailLoading" class="state-block" role="status">正在打开原文…</div>
      <EmptyState v-else-if="detailError" variant="error" title="原文没有加载出来" :description="detailError">
        <el-button @click="loadMaterialDetail(previewMaterialId)">重试</el-button>
      </EmptyState>
      <template v-else-if="selectedMaterialDetail">
        <div v-if="selectedMaterialDetail.segments?.length">
          <article
            v-for="segment in selectedMaterialDetail.segments.slice(0, PREVIEW_SEGMENT_LIMIT)"
            :key="segment.id"
            class="summary-preview__segment"
          >
            <div class="summary-preview__meta">{{ formatSegmentMeta(segment) }}</div>
            <h3 v-if="segment.sectionTitle">{{ segment.sectionTitle }}</h3>
            <p>{{ segment.contentText }}</p>
          </article>
          <p v-if="selectedMaterialDetail.segments.length > PREVIEW_SEGMENT_LIMIT" class="summary-form-note">
            这里只显示前 {{ PREVIEW_SEGMENT_LIMIT }} 段，完整原文可以在资料库里阅读。
          </p>
        </div>
        <div v-else class="state-block empty">这份资料还没有可以阅读的原文。</div>
      </template>
      <template #footer>
        <el-button @click="previewDialogVisible = false">关闭</el-button>
        <el-button
          v-if="selectedMaterialDetail"
          type="primary"
          @click="router.push({ path: '/materials', query: { materialId: String(selectedMaterialDetail.id) } })"
          >在资料库中打开</el-button
        >
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getAllSummaryHistoryApi, submitSummaryTaskApi, type AiTaskDetail } from '@/api/modules/ai'
import { getMaterialDetailApi, type MaterialPageItem } from '@/api/modules/material'
import { isAiTaskSuccess, isAiTaskTerminal, parseAiTaskResult, waitForAiTaskUntilTerminal } from '@/utils/aiTask'
import { getErrorMessage } from '@/utils/errorMessage'
import {
  buildExcerpt,
  formatCharacters,
  formatDateTime,
  formatRelativeDay,
  formatSegmentMeta,
  type SourceSegmentLike
} from '@/utils/format'
import { formatSummaryStatus, formatSummaryType, statusTone } from '@/utils/labels'
import { useAssistantContext } from '@/composables/useAssistantContext'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useParsedMaterials } from '@/composables/useParsedMaterials'
import { usePolling } from '@/composables/usePolling'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import GenerationModeBadge from '@/components/GenerationModeBadge.vue'
import LearningNoteContent from '@/components/LearningNoteContent.vue'
import SourceSegmentList from '@/components/SourceSegmentList.vue'
import StatusPill from '@/components/StatusPill.vue'
import type { GenerationMode } from '@/utils/generationMode'

/** 总结历史列表项（/ai/summary-history）。 */
interface SummaryHistoryItem {
  recordId: number
  materialId: number
  materialTitle?: string
  summaryType?: string
  generationMode?: GenerationMode
  summaryText?: string
  status?: string
  createdAt?: string
  sourceSegments?: SourceSegmentLike[]
}

/** 总结任务成功后 resultJson 里的内容，字段与历史项一致。 */
type SummaryTaskResult = Partial<SummaryHistoryItem>

interface MaterialSegment {
  id: number
  segmentNo?: number
  pageNo?: number
  sectionTitle?: string
  contentText?: string
}

interface MaterialDetail extends MaterialPageItem {
  segments?: MaterialSegment[]
}

const KEYWORD_DEBOUNCE_MS = 300
const PREVIEW_SEGMENT_LIMIT = 8

const route = useRoute()
const router = useRouter()
const assistantContext = useAssistantContext()
const { materials, loading: materialsLoading, error: materialsError, load: loadMaterials } = useParsedMaterials()

const summaryModes = [
  { value: 'STANDARD', title: '标准总结', description: '提炼核心概念和主要结论' },
  { value: 'EXAM', title: '考试重点', description: '整理复习重点和易错点' },
  { value: 'OUTLINE', title: '结构提纲', description: '按章节层级列出要点' }
]

/** 列表左侧的类型图标：标准总结、考试重点、结构提纲各用一种颜色。 */
const SUMMARY_TYPE_ICONS: Record<string, { icon: string; tone: string }> = {
  STANDARD: { icon: 'summary', tone: 'brand' },
  EXAM: { icon: 'target', tone: 'accent' },
  OUTLINE: { icon: 'list', tone: 'blue' }
}
const summaryTypeIcon = (type?: string) =>
  SUMMARY_TYPE_ICONS[String(type || '').toUpperCase()] || SUMMARY_TYPE_ICONS.STANDARD

const materialId = ref<number>()
const summaryType = ref('STANDARD')
const filterMaterialId = ref<number>()
const filterSummaryType = ref('')
const keyword = ref('')
const debouncedKeyword = ref('')
const selectedMaterialDetail = ref<MaterialDetail | null>(null)
const summaryHistory = ref<SummaryHistoryItem[]>([])
const activeSummary = ref<SummaryHistoryItem | null>(null)
const summaryDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const generateDialogVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const previewMaterialId = ref(0)
const previewTitle = ref('')
const generating = ref(false)
const historyLoading = ref(false)
const loadError = ref('')
const currentTask = ref<AiTaskDetail | null>(null)
let viewDisposed = false
let keywordTimer: number | undefined
const pagination = reactive({
  current: 1,
  size: 10
})

const getSummaryStatus = (item?: SummaryHistoryItem | null) =>
  String(item?.status || (item?.summaryText ? 'SUCCESS' : '')).toUpperCase()
const isSummaryReady = (item?: SummaryHistoryItem | null) => getSummaryStatus(item) === 'SUCCESS'

const CARD_HINTS: Record<string, string> = {
  GENERATING: '正在生成，完成后就可以阅读。',
  FAILED: '这次没有生成成功，可以在任务中心查看原因，或者重新生成。',
  CANCELLED: '这次生成已取消，可以重新生成。'
}
const buildCardExcerpt = (item: SummaryHistoryItem) =>
  CARD_HINTS[getSummaryStatus(item)] || buildExcerpt(item.summaryText, 160)

const filteredSummaryHistory = computed(() => {
  const search = debouncedKeyword.value.trim().toLowerCase()
  return summaryHistory.value.filter((item) => {
    const matchesMaterial = !filterMaterialId.value || item.materialId === filterMaterialId.value
    const matchesType = !filterSummaryType.value || item.summaryType === filterSummaryType.value
    const matchesKeyword =
      !search || `${item.materialTitle || ''} ${item.summaryText || ''}`.toLowerCase().includes(search)
    return matchesMaterial && matchesType && matchesKeyword
  })
})

const hasActiveFilters = computed(() =>
  Boolean(keyword.value.trim() || filterMaterialId.value || filterSummaryType.value)
)

/** 只按资料筛选、且这份资料一份总结都没有（比如从资料库点“总结”进来）。 */
const filteredMaterialHasNoSummary = computed(
  () =>
    Boolean(filterMaterialId.value) &&
    !keyword.value.trim() &&
    !filterSummaryType.value &&
    !summaryHistory.value.some((item) => item.materialId === filterMaterialId.value)
)

const pagedSummaryHistory = computed(() => {
  const start = (pagination.current - 1) * pagination.size
  return filteredSummaryHistory.value.slice(start, start + pagination.size)
})

const hasGeneratingSummary = () => summaryHistory.value.some((item) => getSummaryStatus(item) === 'GENERATING')

/** 学习助手应关注的资料：优先正在查看的对话框，其次筛选条件、进行中的任务、列表首项。 */
const preferredAssistantMaterialId = computed(() => {
  if (summaryDialogVisible.value && activeSummary.value?.materialId) return activeSummary.value.materialId
  if (previewDialogVisible.value && selectedMaterialDetail.value?.id) return selectedMaterialDetail.value.id
  if (generateDialogVisible.value && materialId.value) return materialId.value
  if (filterMaterialId.value) return filterMaterialId.value
  if (currentTask.value?.bizId) return Number(currentTask.value.bizId)
  return (
    pagedSummaryHistory.value[0]?.materialId || filteredSummaryHistory.value[0]?.materialId || materials.value[0]?.id
  )
})

const taskStatusText = computed(() => {
  if (!generating.value || !currentTask.value || isAiTaskTerminal(currentTask.value.status)) return ''
  const materialLabel = materials.value.find((item) => item.id === currentTask.value?.bizId)?.title
  return `正在生成${materialLabel ? `“${materialLabel}”的` : ''}总结，完成后会自动打开。`
})

// 关键词做 300ms 防抖，避免每次击键都对所有总结正文做全文匹配。
watch(keyword, (value) => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    debouncedKeyword.value = value
  }, KEYWORD_DEBOUNCE_MS)
})

watch([filterMaterialId, filterSummaryType, debouncedKeyword], () => {
  pagination.current = 1
})

// 只有真实存在的正整数 id 才会写入路由，数据尚未加载时会清掉过期的上下文。
watch(
  preferredAssistantMaterialId,
  (value) => {
    const id = Number(value)
    void assistantContext.sync({ materialId: Number.isInteger(id) && id > 0 ? id : undefined })
  },
  { immediate: true }
)

const historyRequest = useLatestRequest()

/** `silent` 为 true 时不显示骨架屏、不改动错误态，供轮询与后台刷新使用。 */
const loadSummaryHistory = async (silent = false) => {
  if (viewDisposed) return
  const requestId = historyRequest.begin()
  if (!silent) {
    historyLoading.value = true
    loadError.value = ''
  }
  try {
    const res = await getAllSummaryHistoryApi()
    if (viewDisposed || !historyRequest.isLatest(requestId)) return
    summaryHistory.value = res.data.data || []
    loadError.value = ''
  } catch (error) {
    if (!viewDisposed && historyRequest.isLatest(requestId) && !silent) {
      loadError.value = getErrorMessage(error, '请稍后重试。')
    }
  } finally {
    if (!viewDisposed && historyRequest.isLatest(requestId)) historyLoading.value = false
  }
}

usePolling(hasGeneratingSummary, () => loadSummaryHistory(true), 3000)

const materialDetailRequest = useLatestRequest()

const loadMaterialDetail = async (id: number) => {
  if (!id) return
  const requestId = materialDetailRequest.begin()
  detailLoading.value = true
  detailError.value = ''
  selectedMaterialDetail.value = null
  try {
    const res = await getMaterialDetailApi(id)
    if (!materialDetailRequest.isLatest(requestId)) return
    selectedMaterialDetail.value = res.data.data
    if (!selectedMaterialDetail.value) detailError.value = '这份资料不存在或已经删除了。'
  } catch (error) {
    if (!materialDetailRequest.isLatest(requestId)) return
    detailError.value = getErrorMessage(error, '请稍后重试。')
  } finally {
    if (materialDetailRequest.isLatest(requestId)) detailLoading.value = false
  }
}

const resetFilters = () => {
  window.clearTimeout(keywordTimer)
  filterMaterialId.value = undefined
  filterSummaryType.value = ''
  keyword.value = ''
  debouncedKeyword.value = ''
  pagination.current = 1
}

const openGenerateDialog = () => {
  if (filterMaterialId.value && materials.value.some((item) => item.id === filterMaterialId.value)) {
    materialId.value = filterMaterialId.value
  } else if (!materialId.value && materials.value.length === 1) {
    materialId.value = materials.value[0].id
  }
  generateDialogVisible.value = true
}

const openSummaryDialog = (item: SummaryHistoryItem) => {
  if (!isSummaryReady(item)) return
  activeSummary.value = item
  summaryDialogVisible.value = true
}

/** 先打开对话框再加载，加载中和失败时都在对话框里显示状态。 */
const previewMaterial = (item?: SummaryHistoryItem | null) => {
  if (!item?.materialId) return
  previewMaterialId.value = item.materialId
  previewTitle.value = item.materialTitle || ''
  previewDialogVisible.value = true
  void loadMaterialDetail(item.materialId)
}

/** 读完总结直接去出题：题集页会选好这份资料并打开生成对话框。 */
const goQuiz = (id?: number) => {
  if (!id) return
  summaryDialogVisible.value = false
  void router.push({ path: '/quiz', query: { materialId: String(id), generate: '1' } })
}

const quickGenerateForMaterial = (id: number) => {
  materialId.value = id
  generateDialogVisible.value = true
}

const generateSummary = async () => {
  if (generating.value) return
  if (!materialId.value) {
    ElMessage.warning('请先选择资料')
    return
  }

  generating.value = true
  currentTask.value = null
  const generatingMaterialId = materialId.value
  try {
    const submitRes = await submitSummaryTaskApi(generatingMaterialId, {
      summaryType: summaryType.value,
      saveAsNote: true
    })
    const submittedTask = submitRes.data.data as AiTaskDetail
    if (viewDisposed) return
    currentTask.value = submittedTask
    generateDialogVisible.value = false
    ElMessage.success('已开始生成总结')
    // 后台刷新即可：保留用户当前的筛选条件，不闪骨架屏。
    await loadSummaryHistory(true)

    // 总结慢时持续等待，直到任务结束或用户离开本页；偶发网络错误会自动重试。
    const finishedTask = await waitForAiTaskUntilTerminal(submittedTask.id, {
      shouldStop: () => viewDisposed,
      onProgress: (task) => {
        currentTask.value = task
      }
    })
    if (viewDisposed || !finishedTask || !isAiTaskTerminal(finishedTask.status)) return
    currentTask.value = finishedTask
    await loadSummaryHistory(true)
    if (viewDisposed) return
    if (!isAiTaskSuccess(finishedTask.status)) {
      throw new Error(finishedTask.errorMessage || '总结没有生成成功')
    }

    const taskResult = parseAiTaskResult<SummaryTaskResult>(finishedTask)
    const created =
      (taskResult?.recordId ? summaryHistory.value.find((item) => item.recordId === taskResult.recordId) : null) ||
      taskResult
    if (created) {
      openSummaryDialog({
        ...created,
        recordId: created.recordId ?? 0,
        materialId: created.materialId || generatingMaterialId,
        materialTitle: created.materialTitle || materials.value.find((item) => item.id === generatingMaterialId)?.title
      })
    }
    ElMessage.success(created?.generationMode === 'MOCK' ? '总结已生成（演示模式）' : '总结已生成')
  } catch (error) {
    if (!viewDisposed) {
      ElMessage.error(getErrorMessage(error, '总结没有生成成功，可以在任务中心查看原因。'))
      if (currentTask.value && !isAiTaskTerminal(currentTask.value.status)) await loadSummaryHistory(true)
    }
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadMaterials(), loadSummaryHistory()])
  if (viewDisposed) return
  const queryId = Number(route.query.materialId)
  if (queryId) {
    filterMaterialId.value = queryId
    if (materials.value.some((item) => item.id === queryId)) materialId.value = queryId
  }
})

onUnmounted(() => {
  viewDisposed = true
  window.clearTimeout(keywordTimer)
})
</script>

<style scoped>
.summary-row__icon {
  display: grid;
  flex: 0 0 40px;
  place-items: center;
  height: 40px;
  border-radius: 11px;
  background: var(--brand-light);
  color: var(--brand);
}
.summary-row__icon--accent {
  background: var(--accent-soft);
  color: var(--accent);
}
.summary-row__icon--blue {
  background: var(--blue-soft);
  color: var(--blue);
}
.summary-row__excerpt {
  display: -webkit-box;
  margin: 2px 0 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}
.summary-row__sk-excerpt {
  display: block;
  width: min(520px, 85%);
  height: 12px;
  margin-top: 10px;
}
.summary-mode-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: stretch;
  gap: 10px;
  width: 100%;
}
.summary-mode-options .el-radio.is-bordered {
  align-items: flex-start;
  height: auto;
  margin: 0;
  padding: 14px 12px;
  border-radius: var(--radius);
  white-space: normal;
}
.summary-mode-options .el-radio.is-bordered.is-checked {
  background: var(--brand-light);
}
.summary-mode-options :deep(.el-radio__input) {
  margin-top: 3px;
}
.summary-mode-options :deep(.el-radio__label) {
  min-width: 0;
  padding-left: 8px;
}
.summary-mode-options strong {
  display: block;
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}
.summary-mode-options span {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}
.summary-form-note {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.summary-form-note--error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--red-soft);
  color: var(--red);
}
.summary-reader__eyebrow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}
.summary-reader__eyebrow > span:first-child {
  color: var(--brand);
  font-weight: 600;
}
.summary-reader__title {
  margin-top: 6px;
  overflow-wrap: anywhere;
}
.summary-reader__body {
  padding: 4px 0 20px;
}
.summary-reader__mock {
  margin: 0 0 16px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--text-secondary);
  font-size: 13px;
}
.summary-sources {
  padding-top: 16px;
  border-top: 1px solid var(--line);
}
.summary-sources summary {
  padding-bottom: 12px;
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.summary-sources summary span {
  margin-left: 4px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
.summary-preview__segment {
  padding: 16px 0;
  border-top: 1px solid var(--line);
}
.summary-preview__segment:first-child {
  padding-top: 0;
  border-top: 0;
}
.summary-preview__meta {
  color: var(--muted);
  font-size: 12px;
}
.summary-preview__segment h3 {
  margin: 4px 0 6px;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.summary-preview__segment p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.9;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
@media (max-width: 760px) {
  .summary-mode-options {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
