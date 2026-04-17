<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 总结</h1>
        <p class="page-desc">继续收成统一的后台工作台风格，把生成、历史和预览都压进列表/工具栏/弹窗这套交互里。</p>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-tabs">
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'generate' }"
            @click="activeTab = 'generate'"
          >
            生成总结
          </button>
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'history' }"
            @click="activeTab = 'history'"
          >
            历史总结
          </button>
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'preview' }"
            @click="activeTab = 'preview'"
          >
            资料预览
          </button>
        </div>

        <div class="toolbar" style="margin-bottom: 0">
          <el-button :loading="refreshing" @click="refreshCurrentMaterial">刷新当前资料</el-button>
          <el-button type="primary" :loading="generating" @click="generateSummary">生成 AI 总结</el-button>
        </div>
      </div>

      <div class="workspace-body summary-workspace">
        <div class="summary-overview-grid">
          <div class="summary-overview-card">
            <span>已选资料</span>
            <strong>{{ currentMaterialTitle }}</strong>
            <em>{{ materialId ? `资料 #${materialId}` : '请先选择资料' }}</em>
          </div>
          <div class="summary-overview-card">
            <span>历史记录</span>
            <strong>{{ summaryHistory.length }}</strong>
            <em>同一资料下已保存的总结条数</em>
          </div>
          <div class="summary-overview-card">
            <span>最近模型</span>
            <strong>{{ latestSummary?.modelName || '未生成' }}</strong>
            <em>{{ latestSummary ? formatDateTime(latestSummary.createdAt) : '暂无最近记录' }}</em>
          </div>
        </div>

        <div v-if="activeTab === 'generate'" class="page-card workspace-card">
          <div class="workspace-card__header workspace-card__header--spaced">
            <div>
              <h3>生成设置</h3>
              <p>资料、总结类型和最近一次结果放在同一块面板里，操作更像后台内容工作台。</p>
            </div>
            <div class="workspace-toolbar__meta">
              <span class="workspace-chip">{{ formatSummaryType(summaryType) }}</span>
              <span class="workspace-chip workspace-chip--brand">
                {{ latestSummary ? '已有历史' : '首次生成' }}
              </span>
            </div>
          </div>

          <div class="workspace-form-grid summary-form-grid">
            <el-form label-position="top" class="workspace-form-grid__main">
              <el-form-item label="选择资料">
                <el-select
                  v-model="materialId"
                  filterable
                  style="width: 100%"
                  placeholder="请选择资料"
                  :loading="materialsLoading"
                  @change="handleMaterialChange"
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
              <div class="toolbar" style="margin-bottom: 0">
                <el-button type="primary" :loading="generating" @click="generateSummary">
                  生成 AI 总结
                </el-button>
                <el-button
                  v-if="latestSummary"
                  @click="openSummaryDialog(latestSummary)"
                >
                  查看最近结果
                </el-button>
              </div>
            </el-form>

            <div class="summary-side-panel">
              <div class="summary-side-panel__title">最近一次总结</div>
              <div v-if="latestSummary" class="summary-record-card">
                <div class="summary-record-card__head">
                  <strong>{{ formatSummaryType(latestSummary.summaryType) }}</strong>
                  <span>{{ formatDateTime(latestSummary.createdAt) }}</span>
                </div>
                <div class="summary-record-card__meta">{{ latestSummary.modelName || '未记录模型' }}</div>
                <div class="summary-record-card__excerpt">
                  {{ buildExcerpt(latestSummary.summaryText) }}
                </div>
                <div class="workspace-action-row workspace-action-row--fit">
                  <el-button link type="primary" @click="openSummaryDialog(latestSummary)">查看内容</el-button>
                  <el-button link @click="activeTab = 'history'">前往历史</el-button>
                </div>
              </div>
              <div v-else class="state-block empty">还没有生成记录，先选择资料后生成一条总结。</div>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'history'" class="page-card workspace-card">
          <div class="workspace-card__header workspace-card__header--spaced">
            <div>
              <h3>历史总结</h3>
              <p>按资料归档的总结历史，统一用后台列表展示，点开后再用弹窗看全文。</p>
            </div>
            <div class="workspace-toolbar__meta">
              <span v-if="materialId" class="workspace-chip">资料 #{{ materialId }}</span>
              <span class="workspace-chip workspace-chip--brand">共 {{ summaryHistory.length }} 条</span>
            </div>
          </div>

          <div v-if="historyLoading" class="state-block">正在加载总结历史...</div>
          <div v-else-if="!summaryHistory.length" class="state-block empty">当前资料还没有总结历史。</div>
          <div v-else class="workspace-table">
            <div class="workspace-table__head workspace-table__head--summary-rich">
              <span>总结信息</span>
              <span>模型</span>
              <span>生成时间</span>
              <span>操作</span>
            </div>

            <div
              v-for="item in summaryHistory"
              :key="item.recordId"
              class="workspace-table__row workspace-table__row--summary-rich"
            >
              <div class="workspace-table__title">
                <strong>{{ formatSummaryType(item.summaryType) }}</strong>
                <span>#{{ item.recordId }} · {{ buildExcerpt(item.summaryText, 46) }}</span>
              </div>
              <span>{{ item.modelName || '未记录模型' }}</span>
              <span>{{ formatDateTime(item.createdAt) }}</span>
              <div class="workspace-action-row workspace-action-row--fit">
                <el-button link type="primary" @click="openSummaryDialog(item)">查看内容</el-button>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="page-card workspace-card">
          <div class="workspace-card__header workspace-card__header--spaced">
            <div>
              <h3>资料预览</h3>
              <p>把正文片段改成后台列表块，方便在同一页核对原文和总结结果。</p>
            </div>
            <div v-if="selectedMaterialDetail" class="workspace-toolbar__meta">
              <span class="workspace-chip">字数 {{ selectedMaterialDetail.totalCharacters || 0 }}</span>
              <span class="workspace-chip">标签 {{ selectedMaterialDetail.tags || '未设置' }}</span>
              <span class="workspace-chip workspace-chip--brand">
                {{ selectedMaterialDetail.parseStatus }}
              </span>
            </div>
          </div>

          <div v-if="detailLoading" class="state-block">正在读取资料内容...</div>
          <template v-else-if="selectedMaterialDetail">
            <div class="summary-preview-header">
              <div>
                <div class="summary-preview-header__label">当前资料</div>
                <strong>{{ selectedMaterialDetail.title }}</strong>
              </div>
              <el-button v-if="latestSummary" @click="openSummaryDialog(latestSummary)">联动查看最近总结</el-button>
            </div>

            <div v-if="selectedMaterialDetail.segments?.length" class="summary-preview-list">
              <div
                v-for="segment in selectedMaterialDetail.segments.slice(0, 8)"
                :key="segment.id"
                class="summary-preview-card"
              >
                <div class="summary-preview-card__label">片段 {{ segment.sortNo || segment.id }}</div>
                <div class="summary-block">{{ segment.contentText }}</div>
              </div>
            </div>
            <div v-else class="state-block empty">这份资料暂时还没有可预览的解析片段。</div>
          </template>
          <div v-else class="state-block empty">先在“生成总结”里选中一份资料，再来这里查看预览。</div>
        </div>
      </div>
    </div>

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
              {{ activeSummary?.modelName || '未记录模型' }} · {{ formatSummaryType(activeSummary?.summaryType) }}
              · {{ formatDateTime(activeSummary?.createdAt) }}
            </div>
          </div>
        </div>
      </template>

      <div class="summary-dialog__meta">
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
        <div class="detail-meta-item">
          <span>记录编号</span>
          <strong>#{{ activeSummary?.recordId || '--' }}</strong>
        </div>
      </div>

      <div class="summary-dialog__content">
        <div class="summary-dialog__content-title">总结正文</div>
        <div class="summary-block">{{ activeSummary?.summaryText }}</div>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { generateSummaryApi, getLatestSummaryApi, getSummaryHistoryApi } from '@/api/modules/ai'
import { getMaterialDetailApi, getMaterialPageApi } from '@/api/modules/material'

const route = useRoute()
const materialId = ref<number>()
const summaryType = ref('STANDARD')
const materials = ref<any[]>([])
const selectedMaterialDetail = ref<any>(null)
const summaryHistory = ref<any[]>([])
const activeSummary = ref<any>(null)
const latestSummary = ref<any>(null)
const summaryDialogVisible = ref(false)
const materialsLoading = ref(false)
const detailLoading = ref(false)
const generating = ref(false)
const historyLoading = ref(false)
const refreshing = ref(false)
const activeTab = ref<'generate' | 'history' | 'preview'>('generate')

const currentMaterialTitle = computed(() => {
  if (selectedMaterialDetail.value?.title) {
    return selectedMaterialDetail.value.title
  }
  return materials.value.find((item) => item.id === materialId.value)?.title || '未选择资料'
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

const loadSummaryHistory = async (id: number) => {
  historyLoading.value = true
  try {
    const res = await getSummaryHistoryApi(id)
    summaryHistory.value = res.data.data || []
  } catch (error: any) {
    summaryHistory.value = []
    ElMessage.error(error.message || '加载总结历史失败')
  } finally {
    historyLoading.value = false
  }
}

const loadLatestSummary = async (id: number) => {
  try {
    const res = await getLatestSummaryApi(id)
    latestSummary.value = res.data.data
  } catch {
    latestSummary.value = null
  }
}

const handleMaterialChange = async (value: number) => {
  await Promise.all([loadMaterialDetail(value), loadSummaryHistory(value), loadLatestSummary(value)])
}

const refreshCurrentMaterial = async () => {
  if (!materialId.value) {
    ElMessage.warning('请先选择资料')
    return
  }
  refreshing.value = true
  try {
    await handleMaterialChange(materialId.value)
    ElMessage.success('当前资料已刷新')
  } finally {
    refreshing.value = false
  }
}

const openSummaryDialog = (item: any) => {
  activeSummary.value = item
  summaryDialogVisible.value = true
}

const generateSummary = async () => {
  if (!materialId.value) {
    ElMessage.warning('请先选择资料')
    return
  }

  generating.value = true
  try {
    const res = await generateSummaryApi(materialId.value, {
      summaryType: summaryType.value,
      saveAsNote: true
    })
    latestSummary.value = res.data.data
    activeSummary.value = res.data.data
    summaryDialogVisible.value = true
    activeTab.value = 'history'
    ElMessage.success('AI 总结生成成功')
    await Promise.all([
      loadMaterialDetail(materialId.value),
      loadSummaryHistory(materialId.value),
      loadLatestSummary(materialId.value)
    ])
  } catch (error: any) {
    ElMessage.error(error.message || '生成总结失败')
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await loadMaterials()
  const queryId = Number(route.query.materialId)
  if (queryId && materials.value.some((item) => item.id === queryId)) {
    materialId.value = queryId
    await handleMaterialChange(queryId)
    activeTab.value = 'preview'
  }
})
</script>
