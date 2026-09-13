<template>
  <section class="summary-view">
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">理解与整理</div>
        <h1 class="page-title">AI 总结</h1>
        <p class="page-desc">把长资料整理成清晰的知识脉络，让每一次复习更有重点。</p>
      </div>
      <el-button type="primary" :loading="generating" @click="openGenerateDialog">
        <svg class="button-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
        生成总结
      </el-button>
    </div>

    <div class="workspace-panel">
      <div class="summary-library-heading">
        <div><h2>我的总结 <span>{{ summaryHistory.length }}</span></h2><p>从核心概念到考试重点，随时回顾已整理的知识。</p></div>
        <el-button :loading="historyLoading" @click="loadSummaryHistory">刷新</el-button>
      </div>
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--summary">
          <el-input v-model="keyword" clearable placeholder="搜索资料名称或总结内容" aria-label="搜索总结" class="workspace-filter-bar__search" />
          <el-select v-model="filterMaterialId" clearable filterable placeholder="全部资料" aria-label="筛选资料" :loading="materialsLoading">
            <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <el-select v-model="filterSummaryType" clearable placeholder="全部类型" aria-label="筛选总结类型">
            <el-option label="标准总结" value="STANDARD" />
            <el-option label="考试重点" value="EXAM" />
            <el-option label="结构提纲" value="OUTLINE" />
          </el-select>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <el-alert v-if="taskStatusText" :title="taskStatusText" type="info" :closable="false" show-icon class="generation-notice" />
        <div v-if="historyLoading" class="summary-loading" aria-live="polite">
          <el-skeleton :rows="5" animated />
          <span>正在整理你的总结…</span>
        </div>
        <div v-else-if="!pagedSummaryHistory.length" class="learning-empty">
          <div class="learning-empty__icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M8 3h8l4 4v14H4V3h4ZM14 3v5h6M8 12h8M8 16h5" stroke-linejoin="round" stroke-linecap="round" /></svg></div>
          <h3>{{ summaryHistory.length ? '没有找到匹配的总结' : '让第一份资料，变成一份清晰的总结' }}</h3>
          <p>{{ summaryHistory.length ? '试试其他关键词，或清除筛选条件。' : '选择一份已解析的资料，AI 会为你提炼重点，并保留原文出处。' }}</p>
          <el-button v-if="summaryHistory.length" @click="resetFilters">清除筛选</el-button>
          <el-button v-else-if="materials.length" type="primary" :loading="generating" @click="openGenerateDialog">生成第一份总结</el-button>
          <el-button v-else type="primary" @click="router.push('/materials')">前往资料库</el-button>
        </div>
        <div v-else class="summary-card-grid">
          <article v-for="item in pagedSummaryHistory" :key="item.recordId" class="summary-note-card">
            <div class="summary-note-card__top">
              <span class="summary-type">{{ formatSummaryType(item.summaryType) }}</span>
              <span class="summary-note-card__date">{{ formatDateTime(item.createdAt).slice(0, 10) }}</span>
            </div>
            <button type="button" class="summary-note-card__title" @click="openSummaryDialog(item)">{{ item.materialTitle || '未命名资料' }}</button>
            <p class="summary-note-card__excerpt">{{ buildExcerpt(item.summaryText, 120) }}</p>
            <div class="summary-note-card__actions">
              <el-button link type="primary" @click="openSummaryDialog(item)">阅读总结 <span aria-hidden="true">→</span></el-button>
              <div>
                <el-button link @click="previewMaterial(item)">原始资料</el-button>
                <el-button link :disabled="generating" @click="quickGenerateForMaterial(item.materialId)">重新生成</el-button>
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
      <template #header>
        <div class="dialog-title">生成 AI 总结</div>
        <div class="dialog-subtitle">选好资料与整理方式，把复杂内容变得易于理解。</div>
      </template>
      <div v-if="!materialsLoading && !materials.length" class="learning-empty learning-empty--compact">
        <h3>先准备一份学习资料</h3>
        <p>上传并解析资料后，就可以生成总结了。</p>
        <el-button type="primary" @click="router.push('/materials')">前往资料库</el-button>
      </div>
      <el-form v-else label-position="top">
        <el-form-item label="选择资料">
          <el-select v-model="materialId" filterable style="width: 100%" placeholder="选择要整理的资料" :loading="materialsLoading">
            <el-option v-for="item in materials" :key="item.id" :label="item.title + ' · ' + (item.totalCharacters || 0) + ' 字'" :value="item.id" />
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
      <template #header>
        <span class="summary-type">{{ formatSummaryType(activeSummary?.summaryType) }}</span>
        <div class="dialog-title summary-reader__title">{{ activeSummary?.materialTitle || '学习总结' }}</div>
        <div class="dialog-subtitle">{{ formatDateTime(activeSummary?.createdAt) }}</div>
      </template>
      <article class="summary-reader__body">
        <LearningNoteContent :text="activeSummary?.summaryText" />
      </article>
      <details class="summary-sources">
        <summary>引用资料 <span>{{ activeSummary?.sourceSegments?.length || 0 }} 处</span></summary>
        <div v-if="activeSummary?.sourceSegments?.length" class="rag-reference-list">
          <div v-for="segment in activeSummary.sourceSegments" :key="segment.segmentId + '-' + (segment.segmentNo || 0)" class="rag-reference-item">
            <div class="rag-reference-item__title">{{ segment.sectionTitle || '资料摘录 ' + (segment.segmentNo || segment.segmentId) }}</div>
            <div class="rag-reference-item__meta">{{ buildSegmentMeta(segment) }}</div>
            <div class="summary-block">{{ segment.contentText }}</div>
          </div>
        </div>
        <p v-else class="summary-form-note">这份总结暂未记录引用资料。</p>
      </details>
      <template #footer>
        <el-button @click="previewMaterial(activeSummary)">查看原始资料</el-button>
        <el-button type="primary" @click="summaryDialogVisible = false">完成阅读</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialogVisible" title="原始资料" width="min(900px, calc(100vw - 32px))" top="5vh" :close-on-click-modal="false" destroy-on-close class="summary-dialog">
      <template #header>
        <div class="dialog-title">原始资料</div>
        <div class="dialog-subtitle">{{ selectedMaterialDetail?.title || '资料预览' }}</div>
      </template>
      <div v-if="detailLoading" class="state-block">正在读取资料内容…</div>
      <template v-else-if="selectedMaterialDetail">
        <div class="summary-material-meta">
          <span>{{ selectedMaterialDetail.totalCharacters || 0 }} 字</span>
          <span>{{ formatParseStatus(selectedMaterialDetail.parseStatus) }}</span>
          <span v-if="selectedMaterialDetail.tags">{{ selectedMaterialDetail.tags }}</span>
        </div>
        <div v-if="selectedMaterialDetail.segments?.length" class="summary-preview-list">
          <div v-for="segment in selectedMaterialDetail.segments.slice(0, 8)" :key="segment.id" class="summary-preview-card">
            <div class="summary-preview-card__label">段落 {{ segment.segmentNo || segment.id }}</div>
            <div class="summary-block">{{ segment.contentText }}</div>
          </div>
          <p v-if="selectedMaterialDetail.segments.length > 8" class="summary-form-note">此处预览前 8 个段落，完整内容可在资料库查看。</p>
        </div>
        <div v-else class="state-block empty">这份资料还没有可预览的内容。</div>
      </template>
      <template #footer><el-button @click="previewDialogVisible = false">关闭预览</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getAllSummaryHistoryApi, submitSummaryTaskApi, type AiTaskDetail } from '@/api/modules/ai'
import { getMaterialDetailApi, getMaterialPageApi } from '@/api/modules/material'
import { isAiTaskSuccess, isAiTaskTerminal, parseAiTaskResult, waitForAiTask } from '@/utils/aiTask'
import LearningNoteContent from '@/components/LearningNoteContent.vue'

const route = useRoute()
const router = useRouter()
const ASSISTANT_MATERIAL_QUERY_KEY = 'assistantMaterialId'
const materialId = ref<number>()
const summaryType = ref('STANDARD')
const summaryModes = [
  { value: 'STANDARD', title: '标准总结', description: '提炼核心概念与主要结论' },
  { value: 'EXAM', title: '考试重点', description: '聚焦高频考点与易错内容' },
  { value: 'OUTLINE', title: '结构提纲', description: '按层级梳理知识脉络' }
]
const filterMaterialId = ref<number>()
const filterSummaryType = ref('')
const keyword = ref('')
const materials = ref<any[]>([])
const selectedMaterialDetail = ref<any>(null)
const summaryHistory = ref<any[]>([])
const activeSummary = ref<any>(null)
const summaryDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const generateDialogVisible = ref(false)
const materialsLoading = ref(false)
const detailLoading = ref(false)
const generating = ref(false)
const historyLoading = ref(false)
const currentTask = ref<AiTaskDetail | null>(null)
const pagination = reactive({
  current: 1,
  size: 10
})

const filteredSummaryHistory = computed(() =>
  summaryHistory.value.filter((item) => {
    const matchesMaterial = !filterMaterialId.value || item.materialId === filterMaterialId.value
    const matchesType = !filterSummaryType.value || item.summaryType === filterSummaryType.value
    const text = `${item.materialTitle || ''} ${item.summaryText || ''}`.toLowerCase()
    const matchesKeyword = !keyword.value || text.includes(keyword.value.toLowerCase())
    return matchesMaterial && matchesType && matchesKeyword
  })
)

const pagedSummaryHistory = computed(() => {
  const start = (pagination.current - 1) * pagination.size
  return filteredSummaryHistory.value.slice(start, start + pagination.size)
})

const preferredAssistantMaterialId = computed(() => {
  if (summaryDialogVisible.value && activeSummary.value?.materialId) {
    return activeSummary.value.materialId as number
  }
  if (previewDialogVisible.value && selectedMaterialDetail.value?.id) {
    return selectedMaterialDetail.value.id as number
  }
  if (generateDialogVisible.value && materialId.value) {
    return materialId.value
  }
  if (filterMaterialId.value) {
    return filterMaterialId.value
  }
  if (currentTask.value?.bizId) {
    return Number(currentTask.value.bizId)
  }
  return (
    pagedSummaryHistory.value[0]?.materialId ||
    filteredSummaryHistory.value[0]?.materialId ||
    materials.value[0]?.id
  )
})

const taskStatusText = computed(() => {
  if (!currentTask.value) {
    return ''
  }
  const materialLabel = materials.value.find((item) => item.id === currentTask.value?.bizId)?.title
  if (generating.value) {
    return `正在整理总结${materialLabel ? `：${materialLabel}` : ''}，完成后将自动打开。`
  }
  if (!isAiTaskTerminal(currentTask.value.status)) {
    return '总结仍在生成中，可以稍后刷新，或到任务中心查看进度。'
  }
  return ''
})

watch([filterMaterialId, filterSummaryType, keyword], () => {
  pagination.current = 1
})

watch(
  preferredAssistantMaterialId,
  (materialIdValue) => {
    void syncAssistantMaterialContext(materialIdValue)
  },
  { immediate: true }
)

const formatSummaryType = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'EXAM':
      return '考试重点'
    case 'OUTLINE':
      return '结构提纲'
    default:
      return '标准总结'
  }
}

const formatDateTime = (value?: string) => {
  if (!value) {
    return '未知时间'
  }
  return value.replace('T', ' ').slice(0, 19)
}

const buildExcerpt = (text?: string, length = 60) => {
  if (!text) {
    return '暂无内容'
  }
  const normalized = text.replace(/\s+/g, ' ').trim()
  return normalized.length > length ? `${normalized.slice(0, length)}...` : normalized
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

const formatParseStatus = (value?: string) => {
  const statuses: Record<string, string> = { SUCCESS: '已解析', PENDING: '等待解析', PROCESSING: '解析中', PARSING: '解析中', FAILED: '解析失败' }
  return statuses[value || ''] || '待处理'
}

const loadMaterials = async () => {
  materialsLoading.value = true
  try {
    const res = await getMaterialPageApi({ current: 1, size: 50, parseStatus: 'SUCCESS' })
    const firstPage = res.data.data
    const records = firstPage.records || []
    const pageCount = Math.ceil(Number(firstPage.total || records.length) / 50)
    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) =>
        getMaterialPageApi({ current: index + 2, size: 50, parseStatus: 'SUCCESS' })
      )
    )
    materials.value = [
      ...records,
      ...remainingPages.flatMap((response) => response.data.data.records || [])
    ].filter((item: any) => item.parseStatus === 'SUCCESS')
  } catch (error: any) {
    ElMessage.error(error.message || '加载资料失败')
  } finally {
    materialsLoading.value = false
  }
}

const loadSummaryHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getAllSummaryHistoryApi()
    summaryHistory.value = res.data.data || []
  } catch (error: any) {
    summaryHistory.value = []
    ElMessage.error(error.message || '加载总结列表失败')
  } finally {
    historyLoading.value = false
  }
}

const loadMaterialDetail = async (id: number) => {
  detailLoading.value = true
  try {
    const res = await getMaterialDetailApi(id)
    selectedMaterialDetail.value = res.data.data
  } catch (error: any) {
    selectedMaterialDetail.value = null
    ElMessage.error(error.message || '加载资料详情失败')
  } finally {
    detailLoading.value = false
  }
}

const resetFilters = () => {
  filterMaterialId.value = undefined
  filterSummaryType.value = ''
  keyword.value = ''
}

async function syncAssistantMaterialContext(targetMaterialId?: number) {
  const currentMaterialId = Number(route.query[ASSISTANT_MATERIAL_QUERY_KEY] || 0) || undefined
  if (currentMaterialId === targetMaterialId) {
    return
  }

  const nextQuery = { ...route.query }
  if (targetMaterialId) {
    nextQuery[ASSISTANT_MATERIAL_QUERY_KEY] = String(targetMaterialId)
  } else {
    delete nextQuery[ASSISTANT_MATERIAL_QUERY_KEY]
  }
  await router.replace({ path: route.path, query: nextQuery })
}

const openGenerateDialog = () => {
  if (filterMaterialId.value && materials.value.some((item) => item.id === filterMaterialId.value)) {
    materialId.value = filterMaterialId.value
  } else if (!materialId.value && materials.value.length === 1) {
    materialId.value = materials.value[0].id
  }
  generateDialogVisible.value = true
}

const openSummaryDialog = (item: any) => {
  activeSummary.value = item
  summaryDialogVisible.value = true
  void syncAssistantMaterialContext(item?.materialId)
}

const previewMaterial = async (item: any) => {
  await loadMaterialDetail(item.materialId)
  previewDialogVisible.value = true
  void syncAssistantMaterialContext(item?.materialId)
}

const quickGenerateForMaterial = (id: number) => {
  materialId.value = id
  generateDialogVisible.value = true
}

const generateSummary = async () => {
  if (!materialId.value) {
    ElMessage.warning('请先选择资料')
    return
  }

  generating.value = true
  try {
    const submitRes = await submitSummaryTaskApi(materialId.value, {
      summaryType: summaryType.value,
      saveAsNote: true
    })
    const submittedTask = submitRes.data.data as AiTaskDetail
    currentTask.value = submittedTask
    const taskId = submittedTask.id
    generateDialogVisible.value = false
    ElMessage.success('AI 总结任务已提交')

    const finishedTask = await waitForAiTask(taskId)
    currentTask.value = finishedTask
    if (!isAiTaskTerminal(finishedTask.status)) {
      ElMessage.info('总结任务仍在处理中，可稍后刷新列表查看结果')
      return
    }
    if (!isAiTaskSuccess(finishedTask.status)) {
      throw new Error(finishedTask.errorMessage || 'AI 总结任务执行失败')
    }

    await loadSummaryHistory()
    const taskResult = parseAiTaskResult<any>(finishedTask)
    const created =
      (taskResult?.recordId
        ? summaryHistory.value.find((item) => item.recordId === taskResult.recordId)
        : null) ||
      taskResult

    if (created) {
      activeSummary.value = created
      summaryDialogVisible.value = true
    }
    ElMessage.success('AI 总结生成完成')
  } catch (error: any) {
    ElMessage.error(error.message || 'AI 总结任务失败')
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadMaterials(), loadSummaryHistory()])
  const queryId = Number(route.query.materialId)
  if (queryId) {
    filterMaterialId.value = queryId
    if (materials.value.some((item) => item.id === queryId)) materialId.value = queryId
  }
})
</script>

<style scoped>
.learning-eyebrow { margin-bottom: 8px; color: var(--brand); font-size: 12px; font-weight: 650; letter-spacing: .12em; }
.button-icon { width: 16px; height: 16px; margin-right: 7px; }
.summary-library-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 24px 24px 0; }
.summary-library-heading h2 { display: flex; align-items: center; gap: 9px; margin: 0; font-size: 17px; }
.summary-library-heading h2 span { min-width: 24px; padding: 2px 7px; color: var(--muted); border: 1px solid var(--line); border-radius: 6px; font-size: 12px; font-weight: 500; text-align: center; }
.summary-library-heading p { margin: 7px 0 0; color: var(--muted); font-size: 13px; }
.summary-view .workspace-filter-bar--summary { display: grid; grid-template-columns: minmax(200px, 1.6fr) minmax(140px, 1fr) minmax(120px, .75fr) auto; width: 100%; margin-bottom: 0; }
.summary-view .workspace-toolbar { margin-top: 6px; }
.generation-notice { margin-bottom: 20px; }
.summary-loading { display: grid; gap: 24px; padding: 24px; color: var(--muted); font-size: 13px; }
.summary-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.summary-note-card { display: flex; flex-direction: column; min-width: 0; padding: 23px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); transition: border-color .2s; }
.summary-note-card:hover { border-color: var(--brand); }
.summary-note-card__top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.summary-type { display: inline-flex; padding: 4px 9px; border-radius: 5px; background: var(--brand-light, #edf4ef); color: var(--brand); font-size: 12px; font-weight: 600; }
.summary-note-card__date { font-size: 12px; color: var(--muted); }
.summary-note-card__title { width: fit-content; max-width: 100%; margin: 18px 0 0; padding: 0; border: 0; background: none; color: var(--text); font: inherit; font-size: 17px; font-weight: 650; line-height: 1.6; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.summary-note-card__title:hover { color: var(--brand); }
.summary-note-card__title:focus-visible { outline: 2px solid var(--brand); outline-offset: 5px; border-radius: 3px; }
.summary-note-card__excerpt { flex: 1; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; min-height: 72px; margin: 10px 0 20px; overflow: hidden; color: var(--muted); font-size: 13px; line-height: 1.85; overflow-wrap: anywhere; }
.summary-note-card__actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 14px; border-top: 1px solid var(--line); }
.summary-note-card__actions > div { display: flex; gap: 12px; }
.summary-note-card__actions :deep(.el-button + .el-button) { margin-left: 0; }
.summary-note-card__actions :deep(.el-button) { min-height: 32px; font-size: 12px; }
.summary-note-card__actions :deep(.el-button span) { gap: 6px; }
.learning-empty { display: grid; justify-items: center; align-content: center; min-height: 340px; padding: 40px 20px; text-align: center; }
.learning-empty__icon { display: grid; place-items: center; width: 64px; height: 64px; margin-bottom: 20px; border: 1px solid var(--line); border-radius: 16px; color: var(--brand); background: var(--bg); }
.learning-empty__icon svg { width: 29px; height: 29px; }
.learning-empty h3 { margin: 0 0 10px; color: var(--text); font-size: 18px; font-weight: 600; }
.learning-empty p { max-width: 430px; margin: 0 0 24px; color: var(--muted); font-size: 14px; line-height: 1.8; }
.learning-empty--compact { min-height: 200px; }
.summary-mode-options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; width: 100%; }
.summary-mode-options :deep(.el-radio.is-bordered) { height: auto; min-height: 100px; margin: 0; padding: 16px 12px; border-radius: 9px; white-space: normal; align-items: flex-start; }
.summary-mode-options :deep(.el-radio__input) { margin-top: 3px; }
.summary-mode-options :deep(.el-radio__label) { min-width: 0; padding-left: 8px; }
.summary-mode-options strong { display: block; margin-bottom: 7px; font-size: 14px; }
.summary-mode-options span { display: block; color: var(--muted); font-size: 12px; line-height: 1.7; }
.summary-mode-options :deep(.is-checked) { background: var(--brand-light, #edf4ef); }
.summary-form-note { margin: 12px 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.summary-reader__title { margin-top: 13px; overflow-wrap: anywhere; }
.summary-reader__body { padding: 10px 0 24px; }
.summary-reader__body .summary-block { color: var(--text); font-size: 15px; line-height: 2; overflow-wrap: anywhere; }
.summary-sources { padding-top: 20px; border-top: 1px solid var(--line); }
.summary-sources summary { padding-bottom: 18px; color: var(--text); font-size: 14px; font-weight: 600; cursor: pointer; }
.summary-sources summary span { margin-left: 6px; color: var(--muted); font-size: 12px; font-weight: 400; }
.summary-material-meta { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; color: var(--muted); font-size: 13px; }
.summary-preview-card { border-radius: 10px; background: var(--bg); }
@media (max-width: 1000px) {
  .summary-card-grid { grid-template-columns: 1fr; }
  .summary-view .workspace-filter-bar--summary { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 600px) {
  .summary-library-heading { padding: 18px 16px 0; align-items: flex-start; }
  .summary-library-heading p { max-width: 230px; line-height: 1.7; }
  .summary-view .workspace-filter-bar--summary { grid-template-columns: minmax(0, 1fr); }
  .summary-note-card { padding: 18px; }
  .summary-note-card__actions { flex-wrap: wrap; }
  .summary-mode-options { grid-template-columns: 1fr; }
  .summary-mode-options :deep(.el-radio.is-bordered) { min-height: 75px; }
}
</style>
