<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 总结</h1>
        <p class="page-desc">
          直接进入总结列表，支持筛选、分页、预览资料和再次生成，生成过程统一走任务中心。
        </p>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--summary">
          <el-select
            v-model="filterMaterialId"
            clearable
            filterable
            placeholder="按资料筛选"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · #${item.id}`"
              :value="item.id"
            />
          </el-select>
          <el-select v-model="filterSummaryType" clearable placeholder="总结类型">
            <el-option label="标准总结" value="STANDARD" />
            <el-option label="考试重点" value="EXAM" />
            <el-option label="结构提纲" value="OUTLINE" />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="按资料名称或总结内容搜索"
            class="workspace-filter-bar__search"
          />
          <el-button @click="resetFilters">重置条件</el-button>
        </div>

        <div class="toolbar" style="margin-bottom: 0">
          <el-button :loading="historyLoading" @click="loadSummaryHistory">刷新列表</el-button>
          <el-button type="primary" :loading="generating" @click="openGenerateDialog">
            生成 AI 总结
          </el-button>
        </div>
      </div>

      <div class="workspace-body">
        <el-alert
          v-if="taskStatusText"
          :title="taskStatusText"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />

        <div v-if="historyLoading" class="state-block">正在加载总结列表...</div>
        <div v-else-if="!pagedSummaryHistory.length" class="state-block empty">
          当前筛选条件下还没有总结记录。
        </div>
        <div v-else class="workspace-table">
          <div class="workspace-table__head workspace-table__head--summary-list">
            <span>资料 / 总结</span>
            <span>类型</span>
            <span>模型</span>
            <span>生成时间</span>
            <span>操作</span>
          </div>

          <div
            v-for="item in pagedSummaryHistory"
            :key="item.recordId"
            class="workspace-table__row workspace-table__row--summary-list"
          >
            <div class="workspace-table__title">
              <strong>{{ item.materialTitle || `资料 #${item.materialId}` }}</strong>
              <span>#{{ item.recordId }} · {{ buildExcerpt(item.summaryText, 54) }}</span>
            </div>
            <span>{{ formatSummaryType(item.summaryType) }}</span>
            <span>{{ item.modelName || '未记录模型' }}</span>
            <span>{{ formatDateTime(item.createdAt) }}</span>
            <div class="workspace-action-row workspace-action-row--fit">
              <el-button link type="primary" @click="openSummaryDialog(item)">查看内容</el-button>
              <el-button link @click="previewMaterial(item)">查看资料</el-button>
              <el-button link type="success" @click="quickGenerateForMaterial(item.materialId)">
                再生成
              </el-button>
            </div>
          </div>
        </div>

        <div class="workspace-pagination">
          <div class="workspace-pagination__meta">共 {{ filteredSummaryHistory.length }} 条记录</div>
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="filteredSummaryHistory.length"
          />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="generateDialogVisible"
      width="720px"
      destroy-on-close
      class="summary-dialog"
    >
      <template #header>
        <div class="dialog-title-row">
          <div>
            <div class="dialog-title">生成 AI 总结</div>
            <div class="dialog-subtitle">
              选择资料和总结类型后提交任务，生成完成会自动回到列表并打开结果。
            </div>
          </div>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="选择资料">
          <el-select
            v-model="materialId"
            filterable
            style="width: 100%"
            placeholder="请选择资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · ${item.totalCharacters || 0}字 · #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="总结类型">
          <el-select v-model="summaryType" style="width: 100%">
            <el-option label="标准总结" value="STANDARD" />
            <el-option label="考试重点" value="EXAM" />
            <el-option label="结构提纲" value="OUTLINE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="generateDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="generating" @click="generateSummary">
            生成 AI 总结
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="summaryDialogVisible"
      width="920px"
      top="5vh"
      destroy-on-close
      class="summary-dialog"
    >
      <template #header>
        <div class="dialog-title-row">
          <div>
            <div class="dialog-title">总结内容</div>
            <div class="dialog-subtitle">
              {{ activeSummary?.materialTitle || `资料 #${activeSummary?.materialId || '--'}` }}
              · {{ formatSummaryType(activeSummary?.summaryType) }}
              · {{ formatDateTime(activeSummary?.createdAt) }}
            </div>
          </div>
        </div>
      </template>

      <div class="summary-dialog__meta">
        <div class="detail-meta-item">
          <span>资料名称</span>
          <strong>{{ activeSummary?.materialTitle || `资料 #${activeSummary?.materialId || '--'}` }}</strong>
        </div>
        <div class="detail-meta-item">
          <span>总结类型</span>
          <strong>{{ formatSummaryType(activeSummary?.summaryType) }}</strong>
        </div>
        <div class="detail-meta-item">
          <span>生成模型</span>
          <strong>{{ activeSummary?.modelName || '未记录模型' }}</strong>
        </div>
        <div class="detail-meta-item">
          <span>生成时间</span>
          <strong>{{ formatDateTime(activeSummary?.createdAt) }}</strong>
        </div>
      </div>

      <div class="summary-dialog__content">
        <div class="summary-dialog__content-title">总结正文</div>
        <div class="summary-block">{{ activeSummary?.summaryText }}</div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="previewDialogVisible"
      width="920px"
      top="5vh"
      destroy-on-close
      class="summary-dialog"
    >
      <template #header>
        <div class="dialog-title-row">
          <div>
            <div class="dialog-title">资料预览</div>
            <div class="dialog-subtitle">{{ selectedMaterialDetail?.title || '未选择资料' }}</div>
          </div>
        </div>
      </template>

      <div v-if="detailLoading" class="state-block">正在读取资料内容...</div>
      <template v-else-if="selectedMaterialDetail">
        <div class="summary-dialog__meta">
          <div class="detail-meta-item">
            <span>资料编号</span>
            <strong>#{{ selectedMaterialDetail.id }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>解析状态</span>
            <strong>{{ selectedMaterialDetail.parseStatus }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>字数</span>
            <strong>{{ selectedMaterialDetail.totalCharacters || 0 }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>标签</span>
            <strong>{{ selectedMaterialDetail.tags || '未设置' }}</strong>
          </div>
        </div>

        <div v-if="selectedMaterialDetail.segments?.length" class="summary-preview-list">
          <div
            v-for="segment in selectedMaterialDetail.segments.slice(0, 8)"
            :key="segment.id"
            class="summary-preview-card"
          >
            <div class="summary-preview-card__label">片段 {{ segment.segmentNo || segment.id }}</div>
            <div class="summary-block">{{ segment.contentText }}</div>
          </div>
        </div>
        <div v-else class="state-block empty">这份资料暂时还没有可预览的解析片段。</div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { getAllSummaryHistoryApi, submitSummaryTaskApi, type AiTaskDetail } from '@/api/modules/ai'
import { getMaterialDetailApi, getMaterialPageApi } from '@/api/modules/material'
import { isAiTaskSuccess, isAiTaskTerminal, parseAiTaskResult, waitForAiTask } from '@/utils/aiTask'

const route = useRoute()
const materialId = ref<number>()
const summaryType = ref('STANDARD')
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

const taskStatusText = computed(() => {
  if (!currentTask.value) {
    return ''
  }
  const materialLabel = materials.value.find((item) => item.id === currentTask.value?.bizId)?.title
  if (generating.value) {
    return `任务 #${currentTask.value.id} 正在生成总结${materialLabel ? `：${materialLabel}` : ''}`
  }
  if (!isAiTaskTerminal(currentTask.value.status)) {
    return `任务 #${currentTask.value.id} 仍在处理中，可稍后刷新列表查看结果`
  }
  return ''
})

watch([filterMaterialId, filterSummaryType, keyword], () => {
  pagination.current = 1
})

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

const loadMaterials = async () => {
  materialsLoading.value = true
  try {
    const res = await getMaterialPageApi({ current: 1, size: 100 })
    materials.value = (res.data.data.records || []).filter((item: any) => item.parseStatus === 'SUCCESS')
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

const openGenerateDialog = () => {
  generateDialogVisible.value = true
}

const openSummaryDialog = (item: any) => {
  activeSummary.value = item
  summaryDialogVisible.value = true
}

const previewMaterial = async (item: any) => {
  await loadMaterialDetail(item.materialId)
  previewDialogVisible.value = true
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
  }
})
</script>
