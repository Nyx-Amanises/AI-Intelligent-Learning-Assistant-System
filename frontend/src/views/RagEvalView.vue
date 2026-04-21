<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">RAG 评测集</h1>
        <p class="page-desc">
          用人工标注样本批量验证检索质量，观察 Hit@K、Recall@K、MRR 和耗时，后续调分段、混合检索、重排时就有指标可对比。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button type="primary" @click="openDatasetDialog">新建评测集</el-button>
        <el-button type="success" plain @click="openCmrcDialog">导入 CMRC2018</el-button>
        <el-button :loading="datasetLoading" @click="loadDatasets">刷新列表</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--rag-eval">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="按评测集名称或说明搜索"
            class="workspace-filter-bar__search"
          />
          <el-select
            v-model="filters.materialId"
            clearable
            filterable
            placeholder="关联资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · #${item.id}`"
              :value="item.id"
            />
          </el-select>
          <el-button @click="resetFilters">重置条件</el-button>
        </div>

        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">共 {{ datasetTotal }} 个评测集</span>
          <span class="workspace-chip workspace-chip--brand">当前样本 {{ selectedDataset?.sampleCount || 0 }} 条</span>
        </div>
      </div>

      <div class="workspace-body rag-eval-layout">
        <div class="page-card workspace-card">
          <div class="workspace-card__header workspace-card__header--spaced">
            <div>
              <h3>评测集列表</h3>
              <p>先选择一份资料建立评测集，再录入问题和标准相关片段。</p>
            </div>
          </div>

          <div v-if="datasetLoading" class="state-block">正在加载评测集...</div>
          <div v-else-if="!datasets.length" class="state-block empty">
            还没有评测集。可以先为生物学必修 1 新建一个评测集。
          </div>
          <div v-else class="workspace-table">
            <div class="workspace-table__head workspace-table__head--rag-eval">
              <span>评测集</span>
              <span>资料</span>
              <span>样本</span>
              <span>最近运行</span>
              <span>操作</span>
            </div>

            <div
              v-for="item in datasets"
              :key="item.id"
              class="workspace-table__row workspace-table__row--rag-eval"
              :class="{ 'workspace-table__row--active': selectedDataset?.id === item.id }"
            >
              <div class="workspace-table__title">
                <strong>{{ item.name }}</strong>
                <span>#{{ item.id }} · {{ item.description || '暂无说明' }}</span>
              </div>
              <div class="task-meta-stack">
                <strong>{{ item.materialTitle || `资料 #${item.materialId}` }}</strong>
                <span>资料 ID #{{ item.materialId }}</span>
              </div>
              <span>{{ item.sampleCount || 0 }} 条</span>
              <div class="task-meta-stack">
                <strong>{{ item.lastRunId ? `#${item.lastRunId}` : '未运行' }}</strong>
                <span>{{ formatDateTime(item.lastRunAt) }}</span>
              </div>
              <div class="workspace-action-row workspace-action-row--fit">
                <el-button link type="primary" @click="selectDataset(item)">配置样本</el-button>
                <el-button link type="success" :disabled="!item.sampleCount" @click="runDataset(item)">运行评测</el-button>
                <el-button link type="info" :disabled="!item.lastRunId" @click="loadRun(item.lastRunId!)">查看结果</el-button>
                <el-button link type="danger" @click="removeDataset(item)">删除</el-button>
              </div>
            </div>
          </div>

          <div class="workspace-pagination">
            <div class="workspace-pagination__meta">
              第 {{ datasetPage.current }} / {{ Math.max(1, Math.ceil(datasetTotal / datasetPage.size)) }} 页
            </div>
            <el-pagination
              v-model:current-page="datasetPage.current"
              v-model:page-size="datasetPage.size"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="datasetTotal"
              @current-change="loadDatasets"
              @size-change="loadDatasets"
            />
          </div>
        </div>

        <div class="page-card workspace-card rag-eval-side">
          <div class="workspace-card__header workspace-card__header--spaced">
            <div>
              <h3>样本标注</h3>
              <p v-if="selectedDataset">
                当前评测集：{{ selectedDataset.name }}，基于 {{ selectedDataset.materialTitle || `资料 #${selectedDataset.materialId}` }}。
              </p>
              <p v-else>选择左侧评测集后，在这里维护人工标注样本。</p>
            </div>
            <div class="toolbar" style="margin-bottom: 0">
              <el-button :disabled="!selectedDataset" @click="openSegmentDrawer">资料分段参考</el-button>
              <el-button :disabled="!selectedDataset" @click="openBatchDialog">批量导入</el-button>
              <el-button type="primary" :disabled="!selectedDataset" @click="openSampleDialog()">新增样本</el-button>
            </div>
          </div>

          <div v-if="!selectedDataset" class="state-block empty">
            先从左侧选择一个评测集。
          </div>
          <div v-else-if="sampleLoading" class="state-block">正在加载评测样本...</div>
          <div v-else-if="!samples.length" class="state-block empty">
            还没有样本。建议先打开“资料分段参考”，选 20-50 个问题做第一版评测集。
          </div>
          <div v-else class="rag-sample-list">
            <article v-for="sample in samples" :key="sample.id" class="rag-sample-card">
              <div class="rag-sample-card__head">
                <div>
                  <strong>{{ sample.queryText }}</strong>
                  <span>#{{ sample.id }} · {{ sample.tag || '未分类' }} · 难度 {{ sample.difficulty || 3 }}</span>
                </div>
                <div class="workspace-action-row workspace-action-row--fit">
                  <el-button link type="primary" @click="openSampleDialog(sample)">编辑</el-button>
                  <el-button link type="danger" @click="removeSample(sample)">删除</el-button>
                </div>
              </div>
              <div class="rag-sample-card__meta">
                <span>标准段落：{{ formatNumberList(sample.expectedSegmentIds) }}</span>
                <span>标准页码：{{ formatNumberList(sample.expectedPageNos) }}</span>
                <span>关键词：{{ sample.expectedKeywords || '未填写' }}</span>
              </div>
              <p v-if="sample.note" class="rag-sample-card__note">{{ sample.note }}</p>
            </article>
          </div>
        </div>
      </div>
    </div>

    <div v-if="lastRun" class="workspace-panel rag-run-panel">
      <div class="workspace-toolbar">
        <div>
          <h3 class="rag-run-title">最近评测结果 #{{ lastRun.id }}</h3>
          <p class="page-desc">状态 {{ formatRunStatus(lastRun.status) }} · 样本 {{ lastRun.evaluatedSamples }}/{{ lastRun.totalSamples }} · 失败 {{ lastRun.failedSamples }}</p>
        </div>
        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">Limit {{ lastRun.retrievalLimit }}</span>
          <span class="workspace-chip workspace-chip--brand">MRR {{ formatMetric(lastRun.mrr) }}</span>
        </div>
      </div>

      <div class="workspace-body">
        <div class="rag-metric-grid">
          <div class="summary-overview-card">
            <span>Hit@1 / 3 / 5</span>
            <strong>{{ formatMetric(lastRun.hitAt1) }} / {{ formatMetric(lastRun.hitAt3) }} / {{ formatMetric(lastRun.hitAt5) }}</strong>
          </div>
          <div class="summary-overview-card">
            <span>Recall@1 / 3 / 5</span>
            <strong>{{ formatMetric(lastRun.recallAt1) }} / {{ formatMetric(lastRun.recallAt3) }} / {{ formatMetric(lastRun.recallAt5) }}</strong>
          </div>
          <div class="summary-overview-card">
            <span>平均耗时</span>
            <strong>{{ formatLatency(lastRun.avgLatencyMs) }}</strong>
          </div>
        </div>

        <div class="workspace-table rag-run-table">
          <div class="workspace-table__head workspace-table__head--rag-run">
            <span>问题</span>
            <span>标准</span>
            <span>实际召回</span>
            <span>命中排名</span>
            <span>指标</span>
          </div>
          <div
            v-for="item in lastRun.items"
            :key="item.id"
            class="workspace-table__row workspace-table__row--rag-run"
          >
            <div class="workspace-table__title">
              <strong>{{ item.queryText }}</strong>
              <span>样本 #{{ item.sampleId }} · {{ item.latencyMs || 0 }}ms</span>
            </div>
            <div class="task-meta-stack">
              <span>段落 {{ formatNumberList(item.expectedSegmentIds) }}</span>
              <span>页码 {{ formatNumberList(item.expectedPageNos) }}</span>
            </div>
            <div class="task-meta-stack">
              <span>段落 {{ formatNumberList(item.retrievedSegmentIds) }}</span>
              <span>页码 {{ formatNumberList(item.retrievedPageNos) }}</span>
            </div>
            <strong :class="item.hitRank ? 'rag-hit-rank--hit' : 'rag-hit-rank--miss'">
              {{ item.hitRank ? `第 ${item.hitRank} 名` : '未命中' }}
            </strong>
            <div class="task-meta-stack">
              <span>R@1 {{ formatMetric(item.recallAt1) }}</span>
              <span>R@3 {{ formatMetric(item.recallAt3) }} · R@5 {{ formatMetric(item.recallAt5) }}</span>
              <span v-if="item.errorMessage" class="rag-error-text">{{ item.errorMessage }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="datasetDialogVisible" title="新建 RAG 评测集" width="640px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="关联资料">
          <el-select
            v-model="datasetForm.materialId"
            filterable
            style="width: 100%"
            placeholder="请选择已完成 Embedding 的资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · ${formatEmbeddingProgress(item)} · #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="评测集名称">
          <el-input v-model="datasetForm.name" maxlength="120" show-word-limit placeholder="例如：生物必修1 RAG 检索评测集" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="datasetForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明评测范围、标注原则或版本"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="datasetDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creatingDataset" @click="createDataset">创建</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="cmrcDialogVisible" title="导入 CMRC2018 公开评测集" width="720px" destroy-on-close>
      <el-alert
        title="支持 CMRC2018 原始 JSON，以及包含 context/question/answers 字段的 JSON 或 JSONL。导入后会自动创建资料、分段、评测集和样本。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-position="top">
        <el-form-item label="数据文件">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :show-file-list="true"
            accept=".json,.jsonl,.txt"
            :on-change="handleCmrcFileChange"
            :on-remove="clearCmrcFile"
          >
            <div class="cmrc-upload-box">
              <strong>{{ cmrcFile?.name || '点击或拖拽 CMRC2018 JSON 文件到这里' }}</strong>
              <span>建议先用 dev/trial 小集，确认流程后再导入更大的 split。</span>
            </div>
          </el-upload>
        </el-form-item>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="Split 名称">
            <el-input v-model="cmrcForm.splitName" placeholder="dev / trial / train" />
          </el-form-item>
          <el-form-item label="最大样本数">
            <el-input-number v-model="cmrcForm.maxSamples" :min="1" :max="5000" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="资料名称">
          <el-input v-model="cmrcForm.materialTitle" maxlength="120" placeholder="留空则自动生成" />
        </el-form-item>
        <el-form-item label="评测集名称">
          <el-input v-model="cmrcForm.datasetName" maxlength="120" placeholder="留空则自动生成" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="cmrcForm.submitEmbeddingTask">
            导入后自动提交 Embedding 任务
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="cmrcDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="importingCmrc" @click="importCmrcDataset">开始导入</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="sampleDialogVisible" :title="editingSampleId ? '编辑评测样本' : '新增评测样本'" width="760px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="检索问题">
          <el-input
            v-model="sampleForm.queryText"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：细胞膜的主要成分是什么？"
          />
        </el-form-item>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="标准相关段落 ID">
            <el-input v-model="sampleForm.expectedSegmentIdsText" placeholder="例如：12,13,18" />
          </el-form-item>
          <el-form-item label="标准相关页码">
            <el-input v-model="sampleForm.expectedPageNosText" placeholder="例如：18,19" />
          </el-form-item>
        </div>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="标签">
            <el-input v-model="sampleForm.tag" placeholder="例如：概念 / 实验 / 流程 / 区别" />
          </el-form-item>
          <el-form-item label="难度">
            <el-slider v-model="sampleForm.difficulty" :min="1" :max="5" show-stops />
          </el-form-item>
        </div>
        <el-form-item label="期望关键词">
          <el-input v-model="sampleForm.expectedKeywords" maxlength="500" placeholder="例如：磷脂双分子层、蛋白质、糖类" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="sampleForm.note"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="可记录标注依据、容易混淆的位置等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: space-between">
          <el-button :disabled="!selectedDataset" @click="openSegmentDrawer">查看分段 ID</el-button>
          <div>
            <el-button @click="sampleDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingSample" @click="saveSample">保存样本</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialogVisible" title="批量导入评测样本" width="820px" destroy-on-close>
      <el-alert
        title="粘贴 JSON 数组即可批量导入。至少填写 queryText，并填写 expectedSegmentIds 或 expectedPageNos 其中一个。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
      />
      <el-input
        v-model="batchJsonText"
        type="textarea"
        :rows="14"
        :placeholder="batchPlaceholder"
      />
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="batchDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingSample" @click="importBatchSamples">导入样本</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="runDialogVisible" title="运行批量评测" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="检索返回条数 Limit">
          <el-input-number v-model="runForm.limit" :min="1" :max="20" style="width: 100%" />
        </el-form-item>
      </el-form>
      <el-alert
        title="运行时会逐条调用当前 RAG 检索接口，因此需要 Qdrant、Embedding 配置和资料向量都可用。"
        type="warning"
        :closable="false"
        show-icon
      />
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="runDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="runningEval" @click="confirmRunDataset">开始评测</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="segmentDrawerVisible" title="资料分段参考" size="58%">
      <div v-if="segmentLoading" class="state-block">正在加载资料分段...</div>
      <template v-else-if="materialDetail">
        <div class="detail-meta-grid" style="margin-bottom: 18px">
          <div class="detail-meta-item">
            <span>资料</span>
            <strong>{{ materialDetail.title }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>分段数</span>
            <strong>{{ materialDetail.segments?.length || 0 }}</strong>
          </div>
        </div>
        <div class="rag-segment-list">
          <article
            v-for="segment in materialDetail.segments || []"
            :key="segment.id"
            class="rag-segment-card"
          >
            <div class="rag-segment-card__head">
              <strong>#{{ segment.id }} · {{ segment.sectionTitle || '未命名片段' }}</strong>
              <span>{{ formatSegmentLocation(segment) }}</span>
            </div>
            <p>{{ segment.contentText }}</p>
          </article>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  getMaterialDetailApi,
  getMaterialPageApi,
  type MaterialPageItem
} from '@/api/modules/material'
import {
  addRagEvalSamplesApi,
  createRagEvalDatasetApi,
  deleteRagEvalDatasetApi,
  deleteRagEvalSampleApi,
  getRagEvalDatasetPageApi,
  getRagEvalRunApi,
  getRagEvalSamplesApi,
  importCmrc2018Api,
  runRagEvalDatasetApi,
  updateRagEvalSampleApi,
  type CmrcImportResult,
  type RagEvalDataset,
  type RagEvalDatasetPagePayload,
  type RagEvalRun,
  type RagEvalSample,
  type RagEvalSampleForm
} from '@/api/modules/ragEval'
import { useAutoListQuery } from '@/composables/useAutoListQuery'

interface MaterialSegment {
  id: number
  segmentNo?: number
  pageNo?: number
  sectionTitle?: string
  contentText?: string
}

interface MaterialDetail {
  id: number
  title: string
  segments?: MaterialSegment[]
}

const datasets = ref<RagEvalDataset[]>([])
const materials = ref<MaterialPageItem[]>([])
const samples = ref<RagEvalSample[]>([])
const selectedDataset = ref<RagEvalDataset | null>(null)
const lastRun = ref<RagEvalRun | null>(null)
const materialDetail = ref<MaterialDetail | null>(null)

const datasetLoading = ref(false)
const materialsLoading = ref(false)
const sampleLoading = ref(false)
const segmentLoading = ref(false)
const creatingDataset = ref(false)
const savingSample = ref(false)
const runningEval = ref(false)
const datasetDialogVisible = ref(false)
const sampleDialogVisible = ref(false)
const batchDialogVisible = ref(false)
const runDialogVisible = ref(false)
const segmentDrawerVisible = ref(false)
const cmrcDialogVisible = ref(false)
const editingSampleId = ref<number | null>(null)
const pendingRunDataset = ref<RagEvalDataset | null>(null)
const batchJsonText = ref('')
const cmrcFile = ref<File | null>(null)
const datasetTotal = ref(0)
const importingCmrc = ref(false)

const filters = reactive({
  keyword: '',
  materialId: undefined as number | undefined
})

const datasetPage = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.materialId],
  () => {
    datasetPage.current = 1
    return loadDatasets()
  }
)

const datasetForm = reactive({
  materialId: undefined as number | undefined,
  name: '',
  description: ''
})

const sampleForm = reactive({
  queryText: '',
  expectedSegmentIdsText: '',
  expectedPageNosText: '',
  expectedKeywords: '',
  tag: '',
  difficulty: 3,
  note: ''
})

const runForm = reactive({
  limit: 5
})

const cmrcForm = reactive({
  splitName: 'dev',
  maxSamples: 500,
  materialTitle: '',
  datasetName: '',
  submitEmbeddingTask: false
})

const batchPlaceholder = `[
  {
    "queryText": "细胞膜的主要成分是什么？",
    "expectedSegmentIds": [12, 13],
    "expectedPageNos": [18],
    "tag": "概念",
    "difficulty": 2,
    "expectedKeywords": "脂质,蛋白质"
  }
]`

const selectedMaterial = computed(() =>
  materials.value.find((item) => item.id === datasetForm.materialId)
)

const formatDateTime = (value?: string) => {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ').slice(0, 19)
}

const formatMetric = (value?: number) => {
  if (typeof value !== 'number') {
    return '--'
  }
  return `${(value * 100).toFixed(1)}%`
}

const formatLatency = (value?: number) => {
  if (typeof value !== 'number') {
    return '--'
  }
  return `${Math.round(value)} ms`
}

const formatRunStatus = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SUCCESS':
      return '成功'
    case 'PARTIAL_FAILED':
      return '部分失败'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '运行中'
    default:
      return value || '未知'
  }
}

const formatNumberList = (values?: number[]) => {
  if (!values || !values.length) {
    return '--'
  }
  return values.join(', ')
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

const formatEmbeddingProgress = (row: MaterialPageItem) => {
  const embedded = Number(row.embeddedSegmentCount || 0)
  const total = Number(row.totalSegmentCount || 0)
  if (total > 0) {
    return `Embedding ${embedded}/${total}`
  }
  return row.parseStatus || '未解析'
}

const parseNumberList = (text: string) => {
  if (!text.trim()) {
    return []
  }
  const values = text
    .split(/[,，\s]+/)
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0)
  return Array.from(new Set(values))
}

const buildSamplePayload = (): RagEvalSampleForm | null => {
  const queryText = sampleForm.queryText.trim()
  const expectedSegmentIds = parseNumberList(sampleForm.expectedSegmentIdsText)
  const expectedPageNos = parseNumberList(sampleForm.expectedPageNosText)
  if (!queryText) {
    ElMessage.warning('请填写检索问题')
    return null
  }
  if (!expectedSegmentIds.length && !expectedPageNos.length) {
    ElMessage.warning('请至少填写标准相关段落 ID 或标准相关页码')
    return null
  }
  return {
    queryText,
    expectedSegmentIds,
    expectedPageNos,
    expectedKeywords: sampleForm.expectedKeywords.trim() || undefined,
    tag: sampleForm.tag.trim() || undefined,
    difficulty: sampleForm.difficulty,
    sourceType: 'HUMAN',
    note: sampleForm.note.trim() || undefined
  }
}

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

const loadDatasets = async () => {
  datasetLoading.value = true
  try {
    const res = await getRagEvalDatasetPageApi({
      current: datasetPage.current,
      size: datasetPage.size,
      keyword: filters.keyword || undefined,
      materialId: filters.materialId || undefined
    })
    const data = res.data.data as RagEvalDatasetPagePayload
    datasets.value = data.records || []
    datasetTotal.value = data.total || 0
    if (selectedDataset.value) {
      const freshSelected = datasets.value.find((item) => item.id === selectedDataset.value?.id)
      if (freshSelected) {
        selectedDataset.value = freshSelected
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载评测集失败')
  } finally {
    datasetLoading.value = false
  }
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.materialId = undefined
    datasetPage.current = 1
  })
}

const openDatasetDialog = () => {
  datasetForm.materialId = undefined
  datasetForm.name = ''
  datasetForm.description = ''
  datasetDialogVisible.value = true
  if (!materials.value.length) {
    void loadMaterials()
  }
}

const openCmrcDialog = () => {
  cmrcFile.value = null
  cmrcForm.splitName = 'dev'
  cmrcForm.maxSamples = 500
  cmrcForm.materialTitle = ''
  cmrcForm.datasetName = ''
  cmrcForm.submitEmbeddingTask = false
  cmrcDialogVisible.value = true
}

const handleCmrcFileChange = (uploadFile: UploadFile) => {
  cmrcFile.value = uploadFile.raw || null
}

const clearCmrcFile = () => {
  cmrcFile.value = null
}

const importCmrcDataset = async () => {
  if (!cmrcFile.value) {
    ElMessage.warning('请先选择 CMRC2018 数据文件')
    return
  }
  importingCmrc.value = true
  try {
    const res = await importCmrc2018Api({
      file: cmrcFile.value,
      materialTitle: cmrcForm.materialTitle.trim() || undefined,
      datasetName: cmrcForm.datasetName.trim() || undefined,
      splitName: cmrcForm.splitName.trim() || undefined,
      maxSamples: cmrcForm.maxSamples,
      submitEmbeddingTask: cmrcForm.submitEmbeddingTask
    })
    const result = res.data.data as CmrcImportResult
    ElMessage.success(
      `已导入 ${result.sampleCount} 条样本、${result.segmentCount} 个分段${result.embeddingTaskId ? `，Embedding 任务 #${result.embeddingTaskId}` : ''}`
    )
    cmrcDialogVisible.value = false
    datasetPage.current = 1
    await loadMaterials()
    await loadDatasets()
    selectedDataset.value = {
      id: result.datasetId,
      materialId: result.materialId,
      materialTitle: result.materialTitle,
      name: result.datasetName,
      status: 'ACTIVE',
      sampleCount: result.sampleCount
    }
    lastRun.value = null
    await loadSamples()
  } catch (error: any) {
    ElMessage.error(error.message || '导入 CMRC2018 失败')
  } finally {
    importingCmrc.value = false
  }
}

const createDataset = async () => {
  if (!datasetForm.materialId) {
    ElMessage.warning('请选择关联资料')
    return
  }
  if (!datasetForm.name.trim()) {
    ElMessage.warning('请填写评测集名称')
    return
  }

  creatingDataset.value = true
  try {
    const res = await createRagEvalDatasetApi({
      materialId: datasetForm.materialId,
      name: datasetForm.name.trim(),
      description: datasetForm.description.trim() || undefined
    })
    const dataset = res.data.data as RagEvalDataset
    ElMessage.success('评测集已创建')
    datasetDialogVisible.value = false
    selectedDataset.value = {
      ...dataset,
      materialTitle: dataset.materialTitle || selectedMaterial.value?.title
    }
    await loadDatasets()
    await loadSamples()
  } catch (error: any) {
    ElMessage.error(error.message || '创建评测集失败')
  } finally {
    creatingDataset.value = false
  }
}

const selectDataset = async (dataset: RagEvalDataset) => {
  selectedDataset.value = dataset
  lastRun.value = null
  await loadSamples()
}

const removeDataset = async (dataset: RagEvalDataset) => {
  try {
    await ElMessageBox.confirm('删除后会同时删除该评测集的样本和历史运行记录，确定继续吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteRagEvalDatasetApi(dataset.id)
    ElMessage.success('评测集已删除')
    if (selectedDataset.value?.id === dataset.id) {
      selectedDataset.value = null
      samples.value = []
      lastRun.value = null
    }
    await loadDatasets()
  } catch (error: any) {
    ElMessage.error(error.message || '删除评测集失败')
  }
}

const loadSamples = async () => {
  if (!selectedDataset.value) {
    return
  }
  sampleLoading.value = true
  try {
    const res = await getRagEvalSamplesApi(selectedDataset.value.id)
    samples.value = res.data.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载评测样本失败')
  } finally {
    sampleLoading.value = false
  }
}

const resetSampleForm = () => {
  editingSampleId.value = null
  sampleForm.queryText = ''
  sampleForm.expectedSegmentIdsText = ''
  sampleForm.expectedPageNosText = ''
  sampleForm.expectedKeywords = ''
  sampleForm.tag = ''
  sampleForm.difficulty = 3
  sampleForm.note = ''
}

const openSampleDialog = (sample?: RagEvalSample) => {
  if (!selectedDataset.value) {
    return
  }
  resetSampleForm()
  if (sample) {
    editingSampleId.value = sample.id
    sampleForm.queryText = sample.queryText
    sampleForm.expectedSegmentIdsText = formatNumberList(sample.expectedSegmentIds)
    sampleForm.expectedPageNosText = formatNumberList(sample.expectedPageNos)
    sampleForm.expectedKeywords = sample.expectedKeywords || ''
    sampleForm.tag = sample.tag || ''
    sampleForm.difficulty = sample.difficulty || 3
    sampleForm.note = sample.note || ''
  }
  sampleDialogVisible.value = true
}

const saveSample = async () => {
  if (!selectedDataset.value) {
    return
  }
  const payload = buildSamplePayload()
  if (!payload) {
    return
  }

  savingSample.value = true
  try {
    if (editingSampleId.value) {
      await updateRagEvalSampleApi(selectedDataset.value.id, editingSampleId.value, payload)
      ElMessage.success('评测样本已更新')
    } else {
      await addRagEvalSamplesApi(selectedDataset.value.id, [payload])
      ElMessage.success('评测样本已添加')
    }
    sampleDialogVisible.value = false
    await loadSamples()
    await loadDatasets()
  } catch (error: any) {
    ElMessage.error(error.message || '保存评测样本失败')
  } finally {
    savingSample.value = false
  }
}

const removeSample = async (sample: RagEvalSample) => {
  if (!selectedDataset.value) {
    return
  }
  try {
    await ElMessageBox.confirm('确定删除这条评测样本吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteRagEvalSampleApi(selectedDataset.value.id, sample.id)
    ElMessage.success('评测样本已删除')
    await loadSamples()
    await loadDatasets()
  } catch (error: any) {
    ElMessage.error(error.message || '删除评测样本失败')
  }
}

const openBatchDialog = () => {
  batchJsonText.value = batchPlaceholder
  batchDialogVisible.value = true
}

const importBatchSamples = async () => {
  if (!selectedDataset.value) {
    return
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(batchJsonText.value)
  } catch {
    ElMessage.error('JSON 格式不正确')
    return
  }
  if (!Array.isArray(parsed)) {
    ElMessage.error('请粘贴 JSON 数组')
    return
  }
  const samplesToImport = parsed.map((item) => item as RagEvalSampleForm)
  if (!samplesToImport.length) {
    ElMessage.warning('没有可导入的样本')
    return
  }

  savingSample.value = true
  try {
    await addRagEvalSamplesApi(selectedDataset.value.id, samplesToImport)
    ElMessage.success(`已导入 ${samplesToImport.length} 条样本`)
    batchDialogVisible.value = false
    await loadSamples()
    await loadDatasets()
  } catch (error: any) {
    ElMessage.error(error.message || '批量导入失败')
  } finally {
    savingSample.value = false
  }
}

const runDataset = (dataset: RagEvalDataset) => {
  pendingRunDataset.value = dataset
  runForm.limit = 5
  runDialogVisible.value = true
}

const confirmRunDataset = async () => {
  if (!pendingRunDataset.value) {
    return
  }
  runningEval.value = true
  try {
    const res = await runRagEvalDatasetApi(pendingRunDataset.value.id, {
      limit: runForm.limit
    })
    lastRun.value = res.data.data as RagEvalRun
    runDialogVisible.value = false
    selectedDataset.value = pendingRunDataset.value
    ElMessage.success('评测运行完成')
    await loadDatasets()
    await loadSamples()
  } catch (error: any) {
    ElMessage.error(error.message || '运行评测失败')
  } finally {
    runningEval.value = false
  }
}

const loadRun = async (runId: number) => {
  try {
    const res = await getRagEvalRunApi(runId)
    lastRun.value = res.data.data as RagEvalRun
    ElMessage.success('评测结果已加载')
  } catch (error: any) {
    ElMessage.error(error.message || '加载评测结果失败')
  }
}

const openSegmentDrawer = async () => {
  if (!selectedDataset.value) {
    return
  }
  segmentDrawerVisible.value = true
  segmentLoading.value = true
  try {
    const res = await getMaterialDetailApi(selectedDataset.value.materialId)
    materialDetail.value = res.data.data as MaterialDetail
  } catch (error: any) {
    materialDetail.value = null
    ElMessage.error(error.message || '加载资料分段失败')
  } finally {
    segmentLoading.value = false
  }
}

void loadMaterials()
void loadDatasets()
</script>
