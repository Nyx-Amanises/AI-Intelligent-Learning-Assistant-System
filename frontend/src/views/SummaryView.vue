<template>
  <section class="learning-page summary-view">
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">我的内容</div>
        <h1 class="page-title">学习总结</h1>
        <p class="page-desc">读过的内容，整理成自己的理解。</p>
      </div>
      <el-button type="primary" @click="openGenerateDialog">
        <AppIcon name="plus" :size="17" />
        生成总结
      </el-button>
    </div>

    <div class="workspace-panel">
      <ListSectionHeading title="全部总结" :count="summaryHistory.length" :loading="historyLoading" @refresh="loadSummaryHistory()" />
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--summary">
          <el-input v-model="keyword" clearable placeholder="搜索资料名称或总结内容" aria-label="搜索总结" class="workspace-filter-bar__search">
            <template #prefix><AppIcon name="search" :size="18" /></template>
          </el-input>
          <el-select v-model="filterMaterialId" clearable filterable placeholder="全部资料" aria-label="筛选资料" :loading="materialsLoading">
            <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <el-select v-model="filterSummaryType" clearable placeholder="全部类型" aria-label="筛选总结类型">
            <el-option v-for="mode in summaryModes" :key="mode.value" :label="mode.title" :value="mode.value" />
          </el-select>
          <el-button text @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <el-alert v-if="taskStatusText" :title="taskStatusText" type="info" :closable="false" show-icon class="generation-notice" />
        <div v-if="historyLoading" class="summary-loading" aria-live="polite">
          <el-skeleton :rows="5" animated />
          <span>正在整理你的总结…</span>
        </div>
        <EmptyState v-else-if="loadError" variant="error" icon="summary" title="总结暂时没能加载" :description="loadError">
          <el-button type="primary" @click="loadSummaryHistory()">重新加载</el-button>
        </EmptyState>
        <EmptyState
          v-else-if="!pagedSummaryHistory.length"
          icon="summary"
          :title="summaryHistory.length ? '没有找到匹配的总结' : '让第一份资料，变成一份清晰的总结'"
          :description="summaryHistory.length ? '试试其他关键词，或清除筛选条件。' : '选择一份已解析的资料，AI 会为你提炼重点，并保留原文出处。'"
        >
          <el-button v-if="summaryHistory.length" @click="resetFilters">清除筛选</el-button>
          <el-button v-else-if="materials.length" type="primary" @click="openGenerateDialog">生成第一份总结</el-button>
          <el-button v-else type="primary" @click="router.push('/materials')">前往资料库</el-button>
        </EmptyState>
        <div v-else class="summary-card-grid">
          <article v-for="(item, index) in pagedSummaryHistory" :key="item.recordId" class="summary-note-card">
            <span class="summary-note-card__number" aria-hidden="true">{{ formatIndex((pagination.current - 1) * pagination.size + index + 1) }}</span>
            <div class="summary-note-card__content">
              <div class="summary-note-card__top">
                <span class="summary-type">{{ formatSummaryType(item.summaryType) }}</span>
                <StatusPill :tone="statusTone(getSummaryStatus(item))" :label="formatSummaryStatus(getSummaryStatus(item))" />
                <span class="summary-note-card__date">{{ formatDate(item.createdAt) }}</span>
              </div>
              <button type="button" class="summary-note-card__title" :disabled="!isSummaryReady(item)" @click="openSummaryDialog(item)">{{ item.materialTitle || '未命名资料' }}</button>
              <p class="summary-note-card__excerpt">{{ buildCardExcerpt(item) }}</p>
            </div>
            <div class="summary-note-card__actions">
              <el-button v-if="isSummaryReady(item)" link type="primary" @click="openSummaryDialog(item)">阅读总结 <AppIcon name="arrow-right" :size="16" /></el-button>
              <el-button v-else-if="getSummaryStatus(item) === 'GENERATING'" link type="primary" loading disabled>生成中</el-button>
              <el-button v-else link type="primary" @click="router.push('/ai-tasks')">查看任务</el-button>
              <div>
                <el-button link @click="previewMaterial(item)">原始资料</el-button>
                <el-button link :disabled="getSummaryStatus(item) === 'GENERATING'" @click="quickGenerateForMaterial(item.materialId)">重新生成</el-button>
              </div>
            </div>
          </article>
        </div>

        <div v-if="filteredSummaryHistory.length" class="workspace-pagination">
          <div class="workspace-pagination__meta">共 {{ filteredSummaryHistory.length }} 份总结</div>
          <el-pagination v-model:current-page="pagination.current" v-model:page-size="pagination.size" :page-sizes="[5, 10, 20, 50]" layout="sizes, prev, pager, next" :total="filteredSummaryHistory.length" />
        </div>
      </div>
    </div>

    <el-dialog v-model="generateDialogVisible" title="生成 AI 总结" width="min(680px, calc(100vw - 32px))" destroy-on-close class="summary-dialog">
      <template #header="{ titleId, titleClass }">
        <h2 :id="titleId" :class="[titleClass, 'dialog-title']">生成 AI 总结</h2>
        <div class="dialog-subtitle">选好资料与整理方式，把复杂内容变得易于理解。</div>
      </template>
      <EmptyState v-if="!materialsLoading && !materialsError && !materials.length" variant="compact" icon="folder" title="先准备一份学习资料" description="上传并解析资料后，就可以生成总结了。">
        <el-button type="primary" @click="router.push('/materials')">前往资料库</el-button>
      </EmptyState>
      <el-form v-else label-position="top">
        <p v-if="materialsError" class="summary-form-note summary-form-note--error" role="alert">
          资料列表加载失败：{{ materialsError }}
          <el-button link type="primary" :loading="materialsLoading" @click="loadMaterials">重试</el-button>
        </p>
        <el-form-item label="选择资料">
          <el-select v-model="materialId" filterable style="width: 100%" placeholder="选择要整理的资料" :loading="materialsLoading">
            <el-option v-for="item in materials" :key="item.id" :label="item.title + ' · ' + formatCharacters(item.totalCharacters)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="你想怎样整理？">
          <el-radio-group v-model="summaryType" class="summary-mode-options">
            <el-radio v-for="mode in summaryModes" :key="mode.value" :value="mode.value" border>
              <strong>{{ mode.title }}</strong><span>{{ mode.description }}</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <p class="summary-form-note">生成后会自动打开总结，你也可以在任务中心查看进度。</p>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" :disabled="!materialId || !materials.length" @click="generateSummary">开始生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="summaryDialogVisible" :title="activeSummary?.materialTitle || '学习总结'" width="min(900px, calc(100vw - 32px))" top="5vh" :close-on-click-modal="false" destroy-on-close class="summary-dialog summary-reader">
      <template #header="{ titleId, titleClass }">
        <span class="summary-type">{{ formatSummaryType(activeSummary?.summaryType) }}</span>
        <h2 :id="titleId" :class="[titleClass, 'dialog-title', 'summary-reader__title']">{{ activeSummary?.materialTitle || '学习总结' }}</h2>
        <div class="dialog-subtitle">{{ formatDateTime(activeSummary?.createdAt) }}</div>
      </template>
      <article class="summary-reader__body">
        <LearningNoteContent :text="activeSummary?.summaryText" />
      </article>
      <details class="summary-sources">
        <summary>引用资料 <span>{{ activeSummary?.sourceSegments?.length || 0 }} 处</span></summary>
        <SourceSegmentList :segments="activeSummary?.sourceSegments" empty-text="这份总结暂未记录引用资料。" />
      </details>
      <template #footer>
        <el-button @click="previewMaterial(activeSummary)">查看原始资料</el-button>
        <el-button type="primary" @click="summaryDialogVisible = false">完成阅读</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialogVisible" title="原始资料" width="min(900px, calc(100vw - 32px))" top="5vh" :close-on-click-modal="false" destroy-on-close class="summary-dialog">
      <template #header="{ titleId, titleClass }">
        <h2 :id="titleId" :class="[titleClass, 'dialog-title']">原始资料</h2>
        <div class="dialog-subtitle">{{ selectedMaterialDetail?.title || '资料预览' }}</div>
      </template>
      <div v-if="detailLoading" class="state-block">正在读取资料内容…</div>
      <template v-else-if="selectedMaterialDetail">
        <div class="summary-material-meta">
          <span>{{ formatCharacters(selectedMaterialDetail.totalCharacters) }}</span>
          <span>{{ formatParseStatus(selectedMaterialDetail.parseStatus) }}</span>
          <span v-if="selectedMaterialDetail.tags">{{ selectedMaterialDetail.tags }}</span>
        </div>
        <div v-if="selectedMaterialDetail.segments?.length" class="summary-preview-list">
          <div v-for="segment in selectedMaterialDetail.segments.slice(0, PREVIEW_SEGMENT_LIMIT)" :key="segment.id" class="summary-preview-card">
            <div class="summary-preview-card__label">段落 {{ segment.segmentNo || segment.id }}</div>
            <div class="summary-block">{{ segment.contentText }}</div>
          </div>
          <p v-if="selectedMaterialDetail.segments.length > PREVIEW_SEGMENT_LIMIT" class="summary-form-note">此处预览前 {{ PREVIEW_SEGMENT_LIMIT }} 个段落，完整内容可在资料库查看。</p>
        </div>
        <div v-else class="state-block empty">这份资料还没有可预览的内容。</div>
      </template>
      <template #footer><el-button @click="previewDialogVisible = false">关闭预览</el-button></template>
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
import { buildExcerpt, formatCharacters, formatDate, formatDateTime, formatIndex, type SourceSegmentLike } from '@/utils/format'
import { formatParseStatus, formatSummaryStatus, formatSummaryType, statusTone } from '@/utils/labels'
import { useAssistantContext } from '@/composables/useAssistantContext'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useParsedMaterials } from '@/composables/useParsedMaterials'
import { usePolling } from '@/composables/usePolling'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import LearningNoteContent from '@/components/LearningNoteContent.vue'
import ListSectionHeading from '@/components/ListSectionHeading.vue'
import SourceSegmentList from '@/components/SourceSegmentList.vue'
import StatusPill from '@/components/StatusPill.vue'

/** 总结历史列表项（/ai/summary-history）。 */
interface SummaryHistoryItem {
  recordId: number
  materialId: number
  materialTitle?: string
  summaryType?: string
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
  { value: 'STANDARD', title: '标准总结', description: '提炼核心概念与主要结论' },
  { value: 'EXAM', title: '考试重点', description: '聚焦高频考点与易错内容' },
  { value: 'OUTLINE', title: '结构提纲', description: '按层级梳理知识脉络' }
]

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
  GENERATING: '正在整理资料，完成后即可阅读总结。',
  FAILED: '生成未完成，可到任务中心查看原因并重试。',
  CANCELLED: '本次生成已取消，可以重新生成总结。'
}
const buildCardExcerpt = (item: SummaryHistoryItem) =>
  CARD_HINTS[getSummaryStatus(item)] || buildExcerpt(item.summaryText, 160)

const filteredSummaryHistory = computed(() => {
  const search = debouncedKeyword.value.trim().toLowerCase()
  return summaryHistory.value.filter((item) => {
    const matchesMaterial = !filterMaterialId.value || item.materialId === filterMaterialId.value
    const matchesType = !filterSummaryType.value || item.summaryType === filterSummaryType.value
    const matchesKeyword = !search || `${item.materialTitle || ''} ${item.summaryText || ''}`.toLowerCase().includes(search)
    return matchesMaterial && matchesType && matchesKeyword
  })
})

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
  return pagedSummaryHistory.value[0]?.materialId || filteredSummaryHistory.value[0]?.materialId || materials.value[0]?.id
})

const taskStatusText = computed(() => {
  if (!generating.value || !currentTask.value || isAiTaskTerminal(currentTask.value.status)) return ''
  const materialLabel = materials.value.find((item) => item.id === currentTask.value?.bizId)?.title
  return `正在整理总结${materialLabel ? `：${materialLabel}` : ''}，完成后将自动打开。`
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
      loadError.value = getErrorMessage(error, '总结列表暂时无法加载，请稍后重试。')
    }
  } finally {
    if (!viewDisposed && historyRequest.isLatest(requestId)) historyLoading.value = false
  }
}

usePolling(hasGeneratingSummary, () => loadSummaryHistory(true), 3000)

const materialDetailRequest = useLatestRequest()

const loadMaterialDetail = async (id: number) => {
  const requestId = materialDetailRequest.begin()
  detailLoading.value = true
  try {
    const res = await getMaterialDetailApi(id)
    if (!materialDetailRequest.isLatest(requestId)) return
    selectedMaterialDetail.value = res.data.data
  } catch (error) {
    if (!materialDetailRequest.isLatest(requestId)) return
    selectedMaterialDetail.value = null
    ElMessage.error(getErrorMessage(error, '加载资料详情失败'))
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

const previewMaterial = async (item?: SummaryHistoryItem | null) => {
  if (!item?.materialId) return
  await loadMaterialDetail(item.materialId)
  if (viewDisposed) return
  previewDialogVisible.value = true
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
    ElMessage.success('AI 总结任务已提交')
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
      throw new Error(finishedTask.errorMessage || 'AI 总结任务执行失败')
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
    ElMessage.success('AI 总结生成完成')
  } catch (error) {
    if (!viewDisposed) {
      ElMessage.error(getErrorMessage(error, 'AI 总结任务失败'))
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
.summary-view .workspace-panel { background: transparent; border: 0; border-radius: 0; overflow: visible; }
.summary-view .workspace-filter-bar--summary { display: grid; grid-template-columns: minmax(200px, 1.6fr) minmax(150px, .8fr) minmax(130px, .7fr) auto; gap: 12px; width: 100%; margin-bottom: 0; }
.summary-view .workspace-toolbar { margin: 0; padding: 0 0 26px; border-bottom: 1px solid var(--line); background: transparent; }
.summary-view .workspace-filter-bar :deep(.el-input__wrapper), .summary-view .workspace-filter-bar :deep(.el-select__wrapper) { min-height: 46px; padding-inline: 16px; border-radius: 999px; background: transparent; box-shadow: none; }
.summary-view .workspace-filter-bar :deep(.el-input__wrapper) { background: var(--bg-secondary); }
.summary-view .workspace-filter-bar :deep(.el-select__wrapper:hover) { background: var(--bg-secondary); }
.summary-view .workspace-filter-bar :deep(.el-input__wrapper.is-focus), .summary-view .workspace-filter-bar :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 2px var(--brand); }
.summary-view .workspace-body { padding: 0; }
.generation-notice { margin-top: 20px; }
.summary-loading { display: grid; gap: 24px; padding: 40px 0; color: var(--muted); font-size: 14px; }
.summary-card-grid { display: grid; grid-template-columns: minmax(0, 1fr); }
.summary-note-card { display: grid; grid-template-columns: 46px minmax(0, 1fr) 184px; gap: 26px; align-items: start; min-width: 0; padding: 30px 0; border-bottom: 1px solid var(--line); }
.summary-note-card__number { padding-top: 4px; color: var(--muted); font-size: 25px; font-weight: 400; font-variant-numeric: tabular-nums; letter-spacing: -.04em; }
.summary-note-card__content { min-width: 0; }
.summary-note-card__top { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 18px; }
.summary-type { display: inline-flex; color: var(--brand); font-size: 12px; font-weight: 500; letter-spacing: .04em; }
.summary-note-card__date { font-size: 12px; color: var(--muted); }
.summary-note-card__title { width: fit-content; max-width: 100%; min-height: 44px; margin: 9px 0 0; padding: 4px 0; border: 0; background: none; color: var(--text); font: inherit; font-size: 20px; font-weight: 600; line-height: 1.6; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.summary-note-card__title:hover:not(:disabled) { color: var(--brand); }
.summary-note-card__title:disabled { cursor: default; }
.summary-note-card__title:focus-visible { outline: 2px solid var(--brand); outline-offset: 5px; border-radius: 3px; }
.summary-note-card__excerpt { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin: 8px 0 0; overflow: hidden; color: var(--muted); font-size: 15px; line-height: 1.85; overflow-wrap: anywhere; }
.summary-note-card__actions { display: flex; flex-direction: column; align-items: flex-end; justify-content: center; gap: 3px; align-self: center; }
.summary-note-card__actions > div { display: flex; gap: 14px; }
.summary-note-card__actions :deep(.el-button + .el-button) { margin-left: 0; }
.summary-note-card__actions :deep(.el-button) { min-height: 44px; font-size: 13px; }
.summary-note-card__actions > :deep(.el-button) { font-size: 15px; }
.summary-note-card__actions :deep(.el-button span) { gap: 6px; }
.summary-view .workspace-pagination { border-top: 0; margin-top: 0; padding-top: 26px; }
.summary-view .workspace-pagination :deep(.el-pager li), .summary-view .workspace-pagination :deep(.btn-prev), .summary-view .workspace-pagination :deep(.btn-next) { min-width: 44px; height: 44px; border-radius: 50%; background: transparent; }
.summary-view .workspace-pagination :deep(.el-pager li.is-active) { color: var(--brand); background: var(--brand-soft); }
.dialog-title { margin: 0; }
.summary-mode-options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; width: 100%; }
.summary-mode-options :deep(.el-radio.is-bordered) { height: auto; min-height: 100px; margin: 0; padding: 16px 12px; border-radius: 9px; white-space: normal; align-items: flex-start; }
.summary-mode-options :deep(.el-radio__input) { margin-top: 3px; }
.summary-mode-options :deep(.el-radio__label) { min-width: 0; padding-left: 8px; }
.summary-mode-options strong { display: block; margin-bottom: 7px; font-size: 14px; }
.summary-mode-options span { display: block; color: var(--muted); font-size: 12px; line-height: 1.7; }
.summary-mode-options :deep(.is-checked) { background: var(--brand-light); }
.summary-form-note { margin: 12px 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.summary-form-note--error { margin-top: 0; padding: 10px 14px; border-radius: 8px; color: var(--red); background: var(--red-soft); }
.summary-form-note--error :deep(.el-button) { margin-left: 6px; font-size: 12px; }
.summary-reader__title { margin-top: 13px; overflow-wrap: anywhere; }
.summary-reader__body { padding: 10px 0 24px; }
.summary-sources { padding-top: 20px; border-top: 1px solid var(--line); }
.summary-sources summary { padding-bottom: 18px; color: var(--text); font-size: 14px; font-weight: 600; cursor: pointer; }
.summary-sources summary span { margin-left: 6px; color: var(--muted); font-size: 12px; font-weight: 400; }
.summary-material-meta { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; color: var(--muted); font-size: 13px; }
.summary-preview-card { padding: 24px 0; border: 0; border-top: 1px solid var(--line); border-radius: 0; background: transparent; }
@media (max-width: 1000px) {
  .summary-note-card { grid-template-columns: 40px minmax(0, 1fr); gap: 12px 22px; }
  .summary-note-card__actions { grid-column: 2; flex-direction: row; align-items: center; justify-content: space-between; gap: 10px; }
  .summary-view .workspace-filter-bar--summary { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; }
  .summary-view .workspace-filter-bar__search { grid-column: 1 / -1; }
}
@media (max-width: 600px) {
  .summary-view .page-header { flex-direction: column; align-items: stretch; gap: 24px; }
  .summary-view .page-header > .el-button { align-self: flex-start; }
  .summary-view .workspace-filter-bar--summary { gap: 10px 4px; }
  .summary-view .workspace-filter-bar :deep(.el-select__wrapper) { padding-inline: 10px; }
  .summary-view .workspace-toolbar { padding-bottom: 20px; }
  .summary-note-card { grid-template-columns: 28px minmax(0, 1fr); padding-block: 24px; gap: 12px 14px; }
  .summary-note-card__number { font-size: 20px; }
  .summary-note-card__title { font-size: 18px; }
  .summary-note-card__excerpt { font-size: 14px; }
  .summary-note-card__actions { flex-wrap: wrap; }
  .summary-note-card__actions > div { gap: 12px; }
  .summary-mode-options { grid-template-columns: 1fr; }
  .summary-mode-options :deep(.el-radio.is-bordered) { min-height: 75px; }
}
</style>
