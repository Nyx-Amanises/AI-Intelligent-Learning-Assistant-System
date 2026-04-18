<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">资料管理</h1>
        <p class="page-desc">资料页就按后台列表页来做，新增、查询、查看、删除都围绕列表展开。</p>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="toolbar" style="margin-bottom: 0">
          <el-button type="primary" @click="textDialogVisible = true">新增文本资料</el-button>
          <el-upload :show-file-list="false" :http-request="uploadMaterial" :disabled="uploading">
            <el-button :loading="uploading">上传文件</el-button>
          </el-upload>
          <el-button :loading="tableLoading" @click="loadMaterials">刷新列表</el-button>
        </div>
        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">共 {{ total }} 条</span>
        </div>
      </div>

      <div class="workspace-body">
        <div class="page-card workspace-card">
          <div class="workspace-filter-bar">
            <el-input
              v-model="filters.keyword"
              clearable
              placeholder="按资料标题搜索"
              class="workspace-filter-bar__search"
            />
            <el-select v-model="filters.materialType" clearable placeholder="资料类型">
              <el-option label="TEXT" value="TEXT" />
              <el-option label="MARKDOWN" value="MARKDOWN" />
              <el-option label="FILE" value="FILE" />
            </el-select>
            <el-select v-model="filters.parseStatus" clearable placeholder="解析状态">
              <el-option label="PENDING" value="PENDING" />
              <el-option label="SUCCESS" value="SUCCESS" />
              <el-option label="FAILED" value="FAILED" />
            </el-select>
            <el-button type="primary" plain @click="searchMaterials">查询</el-button>
            <el-button @click="resetFilters">重置条件</el-button>
          </div>

          <div v-if="tableLoading" class="state-block">正在加载资料列表...</div>
          <div v-else-if="!materials.length" class="state-block empty">
            没有符合条件的资料，换个筛选条件试试。
          </div>
          <div v-else class="workspace-table">
            <div class="workspace-table__head workspace-table__head--material">
              <span>资料标题</span>
              <span>类型</span>
              <span>解析状态</span>
              <span>总结状态</span>
              <span>Embedding 状态 / 已向量化段数</span>
              <span>字数</span>
              <span>操作</span>
            </div>

            <div
              v-for="row in materials"
              :key="row.id"
              class="workspace-table__row workspace-table__row--material"
            >
              <div class="workspace-table__title">
                <strong>{{ row.title }}</strong>
                <span>#{{ row.id }} · {{ formatDateTime(row.createdAt) }}</span>
              </div>
              <span>{{ row.materialType }}</span>
              <span>{{ row.parseStatus }}</span>
              <span>{{ row.summaryStatus }}</span>
              <div class="material-embedding-cell">
                <strong
                  class="material-embedding-cell__status"
                  :class="`material-embedding-cell__status--${formatEmbeddingTone(row.embeddingStatus)}`"
                >
                  {{ formatEmbeddingStatus(row.embeddingStatus) }}
                </strong>
                <span>{{ formatEmbeddingProgress(row) }}</span>
              </div>
              <span>{{ row.totalCharacters || 0 }}</span>
              <div class="workspace-action-row">
                <el-button
                  link
                  type="primary"
                  :loading="actionLoadingId === row.id && actionType === 'parse'"
                  @click="parseMaterial(row.id)"
                >
                  解析
                </el-button>
                <el-button link @click="viewDetail(row.id)">查看</el-button>
                <el-button
                  link
                  type="primary"
                  :loading="actionLoadingId === row.id && actionType === 'embedding'"
                  @click="generateEmbedding(row)"
                >
                  生成 Embedding
                </el-button>
                <el-button link type="info" @click="openRetrievalPreview(row)">检索预览</el-button>
                <el-button link type="success" @click="goSummary(row.id)">去总结</el-button>
                <el-button link type="warning" @click="goQuiz(row.id)">去出题</el-button>
                <el-button
                  link
                  type="danger"
                  :loading="actionLoadingId === row.id && actionType === 'delete'"
                  @click="removeMaterial(row.id)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <div class="workspace-pagination">
            <div class="workspace-pagination__meta">第 {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页</div>
            <el-pagination
              v-model:current-page="page.current"
              v-model:page-size="page.size"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="total"
              @current-change="loadMaterials"
              @size-change="loadMaterials"
            />
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="textDialogVisible" title="新增文本资料" width="720px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="资料标题">
          <el-input v-model="form.title" maxlength="100" placeholder="例如：数据库系统原理 第三章事务管理" />
        </el-form-item>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="资料类型">
            <el-select v-model="form.materialType" style="width: 100%">
              <el-option label="TEXT" value="TEXT" />
              <el-option label="MARKDOWN" value="MARKDOWN" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="form.tags" placeholder="例如：数据库、期末复习、重点章节" />
          </el-form-item>
        </div>
        <el-form-item label="难度等级">
          <el-slider v-model="form.difficultyLevel" :min="1" :max="5" show-stops />
        </el-form-item>
        <el-form-item label="资料内容">
          <el-input
            v-model="form.contentText"
            type="textarea"
            :rows="10"
            maxlength="10000"
            show-word-limit
            placeholder="请输入课堂笔记、知识点整理或教材摘录内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="textDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="createMaterial">保存资料</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="资料详情" size="52%">
      <template v-if="detailLoading">
        <div class="state-block">正在加载资料详情...</div>
      </template>
      <template v-else-if="detail">
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span>标题</span>
            <strong>{{ detail.title }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>类型</span>
            <strong>{{ detail.materialType }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>解析状态</span>
            <strong>{{ detail.parseStatus }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>总结状态</span>
            <strong>{{ detail.summaryStatus }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>标签</span>
            <strong>{{ detail.tags || '暂无' }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>字符数</span>
            <strong>{{ detail.totalCharacters || 0 }}</strong>
          </div>
        </div>

        <div v-if="detail.segments?.length" class="list-stack" style="margin-top: 18px">
          <div v-for="segment in detail.segments" :key="segment.id" class="section-card">
            <div class="section-card__title">{{ segment.sectionTitle || '未命名片段' }}</div>
            <div class="section-card__meta">{{ formatSegmentLocation(segment) }}</div>
            <div class="summary-block">{{ segment.contentText }}</div>
          </div>
        </div>
        <div v-else class="state-block empty" style="margin-top: 18px">
          这份资料暂时没有解析片段，可以先点击“解析”。
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="retrievalDialogVisible" title="检索预览" width="860px" destroy-on-close>
      <div class="workspace-form-grid workspace-form-grid--compact">
        <el-form-item label="资料">
          <el-input :model-value="retrievalForm.materialTitle" disabled />
        </el-form-item>
        <el-form-item label="召回条数">
          <el-input-number v-model="retrievalForm.limit" :min="1" :max="10" style="width: 100%" />
        </el-form-item>
      </div>
      <el-form-item label="检索问题">
        <el-input
          v-model="retrievalForm.queryText"
          type="textarea"
          :rows="3"
          placeholder="例如：事务的 ACID 特性 / 本章核心知识点 / 死锁的产生条件"
        />
      </el-form-item>

      <div class="toolbar" style="justify-content: flex-end">
        <el-button :loading="retrievalLoading" type="primary" @click="runRetrievalPreview">开始检索</el-button>
      </div>

      <div v-if="retrievalLoading" class="state-block">正在召回相关资料分段...</div>
      <div v-else-if="retrievalResult" class="list-stack" style="margin-top: 18px">
        <div class="soft-text">共命中 {{ retrievalResult.hitCount }} 条</div>
        <div
          v-for="segment in retrievalResult.segments"
          :key="segment.segmentId"
          class="section-card"
        >
          <div class="summary-history-card__top">
            <div>
              <div class="section-card__title">
                {{ segment.sectionTitle || `第 ${segment.segmentNo || '--'} 段` }}
              </div>
              <div class="section-card__meta">
                {{ formatSegmentLocation(segment) }} · 相似度 {{ formatRetrievalScore(segment.score) }}
              </div>
            </div>
          </div>
          <div class="summary-block">{{ segment.contentText || '暂无内容' }}</div>
        </div>
        <div v-if="!retrievalResult.segments.length" class="state-block empty">
          当前检索语句没有召回到内容，可以换个更具体的问题再试试。
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  previewMaterialRetrievalApi,
  submitEmbeddingTaskApi,
  type RetrievalPreviewPayload
} from '@/api/modules/ai'
import {
  createTextMaterialApi,
  deleteMaterialApi,
  getMaterialDetailApi,
  getMaterialPageApi,
  parseMaterialApi,
  uploadMaterialApi,
  type MaterialPageItem
} from '@/api/modules/material'

const router = useRouter()
const materials = ref<MaterialPageItem[]>([])
const total = ref(0)
const detail = ref<any>(null)
const drawerVisible = ref(false)
const textDialogVisible = ref(false)
const retrievalDialogVisible = ref(false)
const tableLoading = ref(false)
const detailLoading = ref(false)
const creating = ref(false)
const uploading = ref(false)
const retrievalLoading = ref(false)
const actionLoadingId = ref<number | null>(null)
const actionType = ref('')
const retrievalResult = ref<RetrievalPreviewPayload | null>(null)

const form = reactive({
  title: '',
  materialType: 'TEXT',
  difficultyLevel: 3,
  tags: '',
  contentText: ''
})

const filters = reactive({
  keyword: '',
  materialType: '',
  parseStatus: ''
})

const page = reactive({
  current: 1,
  size: 10
})

const retrievalForm = reactive({
  materialId: 0,
  materialTitle: '',
  queryText: '',
  limit: 3
})

const formatDateTime = (value?: string) => {
  if (!value) {
    return '未知时间'
  }
  return value.replace('T', ' ').slice(0, 19)
}

const formatRetrievalScore = (value?: number) => {
  if (typeof value !== 'number') {
    return '--'
  }
  return value.toFixed(4)
}

const formatSegmentLocation = (segment: { pageNo?: number; segmentNo?: number }) => {
  const parts = []
  if (segment.pageNo) {
    parts.push(`第 ${segment.pageNo} 页`)
  }
  if (segment.segmentNo) {
    parts.push(`段落 #${segment.segmentNo}`)
  }
  return parts.length ? parts.join(' · ') : '定位信息暂缺'
}

const formatEmbeddingStatus = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SUCCESS':
      return '已完成'
    case 'RUNNING':
      return '生成中'
    case 'PARTIAL':
      return '部分完成'
    case 'PARTIAL_FAILED':
      return '部分失败'
    case 'FAILED':
      return '失败'
    case 'PARSING':
      return '解析中'
    case 'PARSE_FAILED':
      return '解析失败'
    case 'PENDING':
      return '未生成'
    case 'NOT_READY':
    default:
      return '待解析'
  }
}

const formatEmbeddingTone = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SUCCESS':
      return 'success'
    case 'RUNNING':
    case 'PARTIAL':
      return 'running'
    case 'FAILED':
    case 'PARTIAL_FAILED':
    case 'PARSE_FAILED':
      return 'failed'
    case 'PENDING':
      return 'pending'
    case 'PARSING':
      return 'info'
    case 'NOT_READY':
    default:
      return 'default'
  }
}

const formatEmbeddingProgress = (row: MaterialPageItem) => {
  const embedded = Number(row.embeddedSegmentCount || 0)
  const total = Number(row.totalSegmentCount || 0)
  if (total > 0) {
    return `已向量化 ${embedded} / ${total}`
  }
  const parseStatus = String(row.parseStatus || '').toUpperCase()
  if (parseStatus === 'PROCESSING') {
    return '解析完成后可生成'
  }
  if (parseStatus === 'FAILED') {
    return '当前资料解析失败'
  }
  return '需先解析后生成'
}

const resetForm = () => {
  form.title = ''
  form.materialType = 'TEXT'
  form.difficultyLevel = 3
  form.tags = ''
  form.contentText = ''
}

const resetFilters = () => {
  filters.keyword = ''
  filters.materialType = ''
  filters.parseStatus = ''
  page.current = 1
  loadMaterials()
}

const searchMaterials = () => {
  page.current = 1
  loadMaterials()
}

const loadMaterials = async () => {
  tableLoading.value = true
  try {
    const res = await getMaterialPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword || undefined,
      materialType: filters.materialType || undefined,
      parseStatus: filters.parseStatus || undefined
    })
    materials.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载资料失败')
  } finally {
    tableLoading.value = false
  }
}

const createMaterial = async () => {
  if (!form.title.trim() || !form.contentText.trim()) {
    ElMessage.warning('请先填写资料标题和内容')
    return
  }

  creating.value = true
  try {
    await createTextMaterialApi({
      ...form,
      title: form.title.trim(),
      tags: form.tags.trim(),
      contentText: form.contentText.trim()
    })
    ElMessage.success('资料保存成功')
    resetForm()
    textDialogVisible.value = false
    page.current = 1
    await loadMaterials()
  } catch (error: any) {
    ElMessage.error(error.message || '新增资料失败')
  } finally {
    creating.value = false
  }
}

const parseMaterial = async (id: number) => {
  actionLoadingId.value = id
  actionType.value = 'parse'
  try {
    await parseMaterialApi(id)
    ElMessage.success('资料解析成功')
    await loadMaterials()
  } catch (error: any) {
    ElMessage.error(error.message || '资料解析失败')
  } finally {
    actionLoadingId.value = null
    actionType.value = ''
  }
}

const generateEmbedding = async (row: MaterialPageItem) => {
  actionLoadingId.value = row.id
  actionType.value = 'embedding'
  try {
    const res = await submitEmbeddingTaskApi(row.id, {})
    const task = res.data.data
    ElMessage.success(`Embedding 任务已提交，任务 #${task.id}`)
  } catch (error: any) {
    ElMessage.error(error.message || '提交 Embedding 任务失败')
  } finally {
    actionLoadingId.value = null
    actionType.value = ''
  }
}

const removeMaterial = async (id: number) => {
  try {
    await ElMessageBox.confirm('删除后该资料及其解析分段将不可恢复，确定继续吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  actionLoadingId.value = id
  actionType.value = 'delete'
  try {
    await deleteMaterialApi(id)
    ElMessage.success('资料删除成功')
    await loadMaterials()
  } catch (error: any) {
    ElMessage.error(error.message || '删除资料失败')
  } finally {
    actionLoadingId.value = null
    actionType.value = ''
  }
}

const viewDetail = async (id: number) => {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getMaterialDetailApi(id)
    detail.value = res.data.data
  } catch (error: any) {
    detail.value = null
    ElMessage.error(error.message || '加载资料详情失败')
  } finally {
    detailLoading.value = false
  }
}

const openRetrievalPreview = (row: MaterialPageItem) => {
  retrievalDialogVisible.value = true
  retrievalResult.value = null
  retrievalForm.materialId = row.id
  retrievalForm.materialTitle = row.title
  retrievalForm.queryText = ''
  retrievalForm.limit = 3
}

const runRetrievalPreview = async () => {
  if (!retrievalForm.materialId) {
    ElMessage.warning('请先选择资料')
    return
  }
  if (!retrievalForm.queryText.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }

  retrievalLoading.value = true
  try {
    const res = await previewMaterialRetrievalApi(retrievalForm.materialId, {
      queryText: retrievalForm.queryText.trim(),
      limit: retrievalForm.limit
    })
    retrievalResult.value = res.data.data as RetrievalPreviewPayload
    if (!retrievalResult.value.segments.length) {
      ElMessage.info('没有召回到相关内容，可以换个更具体的问题试试')
    }
  } catch (error: any) {
    retrievalResult.value = null
    ElMessage.error(error.message || '检索预览失败')
  } finally {
    retrievalLoading.value = false
  }
}

const uploadMaterial = async (options: UploadRequestOptions) => {
  uploading.value = true
  try {
    await uploadMaterialApi(options.file as File)
    ElMessage.success('资料上传成功')
    await loadMaterials()
    options.onSuccess?.({})
  } catch (error: any) {
    options.onError?.(error)
    ElMessage.error(error.message || '资料上传失败')
  } finally {
    uploading.value = false
  }
}

const goSummary = (id: number) => {
  router.push({ path: '/summary', query: { materialId: String(id) } })
}

const goQuiz = (id: number) => {
  router.push({ path: '/quiz', query: { materialId: String(id) } })
}

loadMaterials()
</script>
