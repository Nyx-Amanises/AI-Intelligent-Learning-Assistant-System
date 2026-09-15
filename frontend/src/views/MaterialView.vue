<template>
  <section class="material-library" aria-labelledby="material-library-title">
    <header class="material-library__header">
      <div>
        <p class="material-library__eyebrow">我的内容</p>
        <h1 id="material-library-title" class="page-title">资料库</h1>
        <p class="material-library__description">讲义、笔记与文档，都收在这里。</p>
      </div>
      <div class="material-library__header-actions">
        <el-button @click="textDialogVisible = true">
          <AppIcon name="summary" :size="18" />
          写入文本
        </el-button>
        <el-button type="primary" @click="uploadDialogVisible = true">
          <AppIcon name="upload" :size="18" />
          上传资料
        </el-button>
      </div>
    </header>

    <aside class="material-library__retrieval" aria-labelledby="material-retrieval-title">
      <AppIcon name="search" :size="22" />
      <div>
        <h2 id="material-retrieval-title">向量检索</h2>
        <p>让 AI 按含义查找资料片段。解析资料后，点击“生成向量”建立检索索引。</p>
      </div>
      <RouterLink to="/ai-config#vector-model">配置向量模型<AppIcon name="arrow-up-right" :size="16" /></RouterLink>
    </aside>

    <div class="material-library__filters" role="search" aria-label="筛选学习资料">
      <el-input
        v-model="searchKeyword"
        clearable
        aria-label="按名称搜索资料"
        placeholder="搜索资料名称…"
        class="material-library__search"
        @keyup.enter="autoQuery.runNow"
      >
        <template #prefix><AppIcon name="search" :size="18" /></template>
      </el-input>
      <el-select v-model="filters.materialType" clearable aria-label="资料类型" placeholder="全部类型">
        <el-option v-for="item in materialTypes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.parseStatus" clearable aria-label="解析状态" placeholder="全部状态">
        <el-option label="待解析" value="PENDING" />
        <el-option label="解析中" value="PROCESSING" />
        <el-option label="已解析" value="SUCCESS" />
        <el-option label="解析失败" value="FAILED" />
      </el-select>
      <el-button text class="material-library__reset" :disabled="!hasActiveFilters" @click="resetFilters">重置</el-button>
    </div>

    <div class="material-library__section-heading">
      <h2>{{ hasActiveFilters ? '筛选结果' : '全部资料' }}<span v-if="!tableLoading && !loadError">{{ total }}</span></h2>
      <button type="button" class="material-library__refresh" :disabled="tableLoading" @click="loadMaterials">
        <AppIcon name="refresh" :size="16" />刷新
      </button>
    </div>

    <div v-if="tableLoading" class="material-library__grid" role="status" aria-label="正在加载资料" aria-busy="true">
      <div v-for="item in 6" :key="item" class="material-library-card material-library-card--loading" aria-hidden="true">
        <div class="material-library__skeleton material-library__skeleton--icon"></div>
        <div class="material-library__skeleton material-library__skeleton--title"></div>
        <div class="material-library__skeleton material-library__skeleton--text"></div>
        <div class="material-library__skeleton material-library__skeleton--footer"></div>
      </div>
    </div>
    <div v-else-if="loadError" class="material-library__state" role="alert">
      <span class="material-library__state-icon"><AppIcon name="wrong" :size="32" /></span>
      <h2>资料库暂时没能加载出来</h2>
      <p>{{ loadError }}</p>
      <el-button type="primary" @click="loadMaterials">重新加载</el-button>
    </div>
    <div v-else-if="!materials.length" class="material-library__state">
      <span class="material-library__state-icon"><AppIcon :name="hasActiveFilters ? 'search' : 'folder'" :size="34" /></span>
      <h2>{{ hasActiveFilters ? '没有找到匹配的资料' : '从第一份学习资料开始' }}</h2>
      <p>{{ hasActiveFilters ? '试试其他关键词，或清除筛选条件查看全部资料。' : '上传课程讲义、教材或课堂笔记，把零散内容整理成自己的知识库。' }}</p>
      <el-button v-if="hasActiveFilters" @click="resetFilters">清除筛选</el-button>
      <template v-else>
        <el-button type="primary" @click="uploadDialogVisible = true"><AppIcon name="upload" :size="18" />上传第一份资料</el-button>
        <button type="button" class="material-library__text-link" @click="textDialogVisible = true">也可以直接写入文本 <AppIcon name="arrow-right" :size="16" /></button>
      </template>
    </div>
    <div v-else class="material-library__grid">
      <article v-for="row in materials" :key="row.id" class="material-library-card" :aria-label="row.title">
        <button type="button" class="material-library-card__cover" :data-kind="row.materialType" :aria-label="`查看${row.title}`" @click="viewDetail(row.id)">
          <AppIcon :name="getMaterialIcon(row.materialType)" :size="26" />
          <span>{{ row.materialType === 'MARKDOWN' ? 'MD' : row.materialType }}</span>
        </button>
        <div class="material-library-card__content">
          <div class="material-library-card__top">
            <h3 :title="row.title"><button type="button" @click="viewDetail(row.id)">{{ row.title }}</button></h3>
            <el-dropdown class="material-library-card__menu" trigger="click" :disabled="actionLoadingId === row.id" @command="(command: string) => handleMaterialCommand(command, row)">
              <button type="button" class="material-library-card__more" :disabled="actionLoadingId === row.id" :aria-label="`${row.title}的更多操作`" :aria-busy="actionLoadingId === row.id">
                <span v-if="actionLoadingId === row.id" class="material-library__spinner" aria-label="正在处理"></span>
                <AppIcon v-else name="more-horizontal" :size="20" />
                <span>更多操作</span>
              </button>
            <template #dropdown>
              <el-dropdown-menu class="material-library-menu">
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="parse">{{ row.parseStatus === 'SUCCESS' ? '重新解析资料' : '解析资料' }}</el-dropdown-item>
                <el-dropdown-item command="embedding" :disabled="row.parseStatus !== 'SUCCESS' || row.embeddingStatus === 'RUNNING'">{{ getEmbeddingActionLabel(row) }}</el-dropdown-item>
                <el-dropdown-item command="retrieval">检索预览</el-dropdown-item>
                <el-dropdown-item command="delete" divided class="material-library-menu__danger">删除资料</el-dropdown-item>
              </el-dropdown-menu>
            </template>
            </el-dropdown>
          </div>
          <p class="material-library-card__meta"><span>{{ formatDate(row.createdAt) }}</span><span>{{ formatCharacters(row.totalCharacters) }}</span></p>
          <div class="material-library-card__tags">
            <span v-for="tag in getTags(row.tags).slice(0, 3)" :key="tag" class="material-library-card__tag" :title="tag">{{ tag }}</span>
            <span v-if="getTags(row.tags).length > 3" class="material-library-card__tag" :title="getTags(row.tags).slice(3).join('、')">+{{ getTags(row.tags).length - 3 }}</span>
            <span v-if="!getTags(row.tags).length" class="material-library-card__untagged">{{ formatDifficulty(row.difficultyLevel) }}</span>
          </div>
          <div class="material-library-card__vector">
            <span class="material-library-status" :class="`material-library-status--${getStatusTone(row.embeddingStatus)}`" :title="formatEmbeddingProgress(row)"><i></i>向量{{ formatEmbeddingStatus(row.embeddingStatus) }}</span>
            <RouterLink v-if="row.embeddingStatus === 'RUNNING'" to="/ai-tasks">查看进度</RouterLink>
            <button
              v-else
              type="button"
              :disabled="actionLoadingId === row.id || row.parseStatus !== 'SUCCESS'"
              :title="row.parseStatus === 'SUCCESS' ? `使用当前配置的向量模型${getEmbeddingActionLabel(row)}` : '请先解析这份资料'"
              :aria-label="`为${row.title}${getEmbeddingActionLabel(row)}`"
              @click="generateEmbedding(row)"
            >{{ actionLoadingId === row.id ? '正在处理…' : getEmbeddingActionLabel(row) }}</button>
            <button type="button" :disabled="row.parseStatus !== 'SUCCESS'" :aria-label="`检索${row.title}的内容`" @click="openRetrievalPreview(row)">检索预览</button>
          </div>
        </div>
        <div class="material-library-card__aside">
          <div class="material-library-card__status-row">
            <span class="material-library-status" :class="`material-library-status--${getStatusTone(row.parseStatus)}`"><i></i>{{ formatParseStatus(row.parseStatus) }}</span>
            <span class="material-library-card__summary">{{ formatSummaryStatus(row.summaryStatus) }}</span>
          </div>
          <div class="material-library-card__actions">
            <button type="button" class="material-library-card__open" @click="viewDetail(row.id)">阅读<AppIcon name="arrow-right" :size="16" /></button>
            <button type="button" class="material-library-card__study" :aria-label="`${row.title}的 AI 总结`" @click="goSummary(row.id)">摘要</button>
            <button type="button" class="material-library-card__study" :aria-label="`根据${row.title}生成练习`" @click="goQuiz(row.id)">出题</button>
          </div>
        </div>
      </article>
    </div>

    <footer v-if="!tableLoading && !loadError && total > 0" class="material-library__pagination">
      <div class="material-library__pagination-meta">
        <span>共 {{ total }} 份资料</span>
        <el-select v-model="page.size" aria-label="每页显示数量" @change="changePageSize">
          <el-option v-for="size in [10, 20, 50]" :key="size" :label="`每页 ${size} 份`" :value="size" />
        </el-select>
      </div>
      <div class="material-library__pagination-controls">
        <span class="material-library__page-indicator">{{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }}</span>
        <el-pagination v-model:current-page="page.current" :page-size="page.size" :pager-count="5" layout="prev, pager, next" :total="total" @current-change="loadMaterials" />
      </div>
    </footer>

    <el-dialog v-model="uploadDialogVisible" class="material-library-modal" title="上传学习资料" width="min(560px, calc(100vw - 32px))" :close-on-click-modal="!uploading" :close-on-press-escape="!uploading" :show-close="!uploading" destroy-on-close>
      <div class="material-library-upload">
        <p>添加课程讲义或教材，随后就能整理重点、生成练习。</p>
        <el-upload drag :show-file-list="false" :http-request="uploadMaterial" :before-upload="beforeUpload" :disabled="uploading" accept=".pdf,.docx,.txt">
          <AppIcon name="upload" :size="36" />
          <strong>{{ uploading ? '正在上传，请稍候…' : '点击选择文件，或拖到这里' }}</strong>
          <span>支持 PDF、Word（.docx）和 TXT 文本</span>
          <span v-if="uploading" class="material-library__spinner" role="status" aria-label="上传中"></span>
        </el-upload>
      </div>
      <template #footer><div class="material-library-dialog__footer"><el-button :disabled="uploading" @click="uploadDialogVisible = false">{{ uploading ? '正在上传…' : '取消' }}</el-button></div></template>
    </el-dialog>

    <el-dialog v-model="textDialogVisible" class="material-library-modal" title="写入学习笔记" width="min(720px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top" class="material-library-form">
        <el-form-item label="资料标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="例如：数据库系统原理 第三章事务管理" />
        </el-form-item>
        <div class="material-library-form__grid">
          <el-form-item label="资料类型">
            <el-select v-model="form.materialType" style="width: 100%">
              <el-option label="纯文本笔记" value="TEXT" />
              <el-option label="格式化笔记（Markdown）" value="MARKDOWN" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="form.tags" placeholder="例如：数据库、期末复习、重点章节" />
          </el-form-item>
        </div>
        <el-form-item label="难度等级">
          <el-slider v-model="form.difficultyLevel" :min="1" :max="5" show-stops :format-tooltip="formatDifficulty" aria-label="资料难度" />
        </el-form-item>
        <el-form-item label="资料内容" required>
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
        <div class="material-library-dialog__footer">
          <el-button @click="textDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="createMaterial">保存资料</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="renameDialogVisible" class="material-library-modal" title="重命名资料" width="min(520px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top" class="material-library-form">
        <el-form-item label="资料名称">
          <el-input
            v-model="renameForm.title"
            maxlength="200"
            show-word-limit
            placeholder="请输入新的资料名称"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="material-library-dialog__footer">
          <el-button @click="renameDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="renaming" @click="renameMaterial">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" class="material-library-modal" title="资料详情" size="min(720px, 100vw)" @closed="clearMaterialDeepLink">
      <div class="material-library-detail">
      <template v-if="detailLoading">
        <div class="material-library__state" role="status"><span class="material-library__spinner"></span><p>正在打开资料…</p></div>
      </template>
      <div v-else-if="detailError" class="material-library__state" role="alert"><h2>暂时无法打开这份资料</h2><p>{{ detailError }}</p><el-button @click="viewDetail(detailId)">重试</el-button></div>
      <template v-else-if="detail">
        <div class="material-library-detail__heading">
          <AppIcon :name="getMaterialIcon(detail.materialType)" :size="42" />
          <div><span>{{ formatMaterialType(detail.materialType) }}</span><h2>{{ detail.title }}</h2></div>
        </div>
        <dl class="material-library-detail__meta">
          <div><dt>添加时间</dt><dd>{{ formatDateTime(detail.createdAt) }}</dd></div>
          <div><dt>内容长度</dt><dd>{{ formatCharacters(detail.totalCharacters) }}</dd></div>
          <div><dt>内容解析</dt><dd>{{ formatParseStatus(detail.parseStatus) }}</dd></div>
          <div><dt>学习摘要</dt><dd>{{ formatSummaryStatus(detail.summaryStatus) }}</dd></div>
          <div><dt>向量索引</dt><dd>{{ formatEmbeddingStatus(detail.embeddingStatus) }} · {{ formatEmbeddingProgress(detail) }}</dd></div>
          <div><dt>资料标签</dt><dd>{{ getTags(detail.tags).join('、') || '暂无标签' }}</dd></div>
        </dl>
        <div class="material-library-detail__actions">
          <el-button v-if="detail.parseStatus !== 'SUCCESS'" type="primary" :loading="actionLoadingId === detail.id" @click="parseMaterial(detail.id)">解析这份资料</el-button>
          <el-button v-else type="primary" @click="goSummary(detail.id)"><AppIcon name="summary" :size="16" />生成或查看摘要</el-button>
          <el-button @click="goQuiz(detail.id)"><AppIcon name="quiz" :size="16" />生成练习</el-button>
        </div>
        <section class="material-library-detail__retrieval" aria-labelledby="material-detail-retrieval-title">
          <div class="material-library-detail__retrieval-heading">
            <h3 id="material-detail-retrieval-title">向量检索</h3>
            <RouterLink to="/ai-config#vector-model">配置向量模型<AppIcon name="arrow-up-right" :size="14" /></RouterLink>
          </div>
          <p>{{ detail.parseStatus === 'SUCCESS' ? '生成向量后，可按问题的含义查找这份资料中的相关片段。' : '先解析资料，再生成向量，即可查找相关片段。' }}</p>
          <div class="material-library-detail__actions">
            <el-button :disabled="detail.parseStatus !== 'SUCCESS' || detail.embeddingStatus === 'RUNNING'" :loading="actionLoadingId === detail.id" @click="generateEmbedding(detail)">{{ getEmbeddingActionLabel(detail) }}</el-button>
            <el-button :disabled="detail.parseStatus !== 'SUCCESS'" @click="openRetrievalPreview(detail)"><AppIcon name="search" :size="16" />检索预览</el-button>
            <RouterLink v-if="detail.embeddingStatus === 'RUNNING'" class="material-library-detail__task-link" to="/ai-tasks">查看生成进度</RouterLink>
          </div>
        </section>
        <div v-if="detail.segments?.length" class="material-library-detail__segments">
          <h3>资料内容 <span>{{ detail.segments.length }} 个片段</span></h3>
          <article v-for="segment in detail.segments" :key="segment.id" class="material-library-detail__segment">
            <div class="material-library-detail__segment-meta">{{ formatSegmentLocation(segment) }}</div>
            <h4>{{ segment.sectionTitle || '资料片段' }}</h4>
            <p>{{ segment.contentText }}</p>
          </article>
        </div>
        <div v-else class="material-library-detail__empty">
          解析后可以在这里阅读资料内容，并基于内容生成摘要和练习。
        </div>
      </template>
      </div>
    </el-drawer>

    <el-dialog v-model="retrievalDialogVisible" class="material-library-modal" title="检索资料内容" width="min(860px, calc(100vw - 32px))" destroy-on-close>
      <div class="material-library-form">
      <div class="material-library-form__grid">
        <el-form-item label="资料">
          <el-input :model-value="retrievalForm.materialTitle" disabled />
        </el-form-item>
        <el-form-item label="返回片段数">
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

      <div class="material-library-dialog__footer">
        <el-button :loading="retrievalLoading" type="primary" @click="runRetrievalPreview">开始检索</el-button>
      </div>

      <div v-if="retrievalLoading" class="state-block" role="status">正在查找相关资料片段…</div>
      <div v-else-if="retrievalError" class="material-library-detail__empty" role="alert">{{ retrievalError }}<el-button @click="runRetrievalPreview">重新检索</el-button></div>
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
          没有找到相关内容，可以换个更具体的问题再试试。
        </div>
      </div>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
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
  renameMaterialApi,
  uploadMaterialApi,
  type MaterialPageItem
} from '@/api/modules/material'

const router = useRouter()
const route = useRoute()
const materials = ref<MaterialPageItem[]>([])
const total = ref(0)
const detail = ref<any>(null)
const detailId = ref(0)
const drawerVisible = ref(false)
const uploadDialogVisible = ref(false)
const textDialogVisible = ref(false)
const renameDialogVisible = ref(false)
const retrievalDialogVisible = ref(false)
const tableLoading = ref(false)
const detailLoading = ref(false)
const creating = ref(false)
const renaming = ref(false)
const uploading = ref(false)
const retrievalLoading = ref(false)
const actionLoadingId = ref<number | null>(null)
const retrievalResult = ref<RetrievalPreviewPayload | null>(null)
const loadError = ref('')
const detailError = ref('')
const retrievalError = ref('')
let listRequestVersion = 0
let detailRequestVersion = 0

const materialTypes = [
  { value: 'PDF', label: 'PDF 文档' },
  { value: 'DOCX', label: 'Word 文档' },
  { value: 'TXT', label: '文本文件' },
  { value: 'TEXT', label: '纯文本笔记' },
  { value: 'MARKDOWN', label: '格式化笔记' },
  { value: 'FILE', label: '其他文档' }
]

const readQueryText = (value: unknown) => typeof value === 'string' ? value : ''

const form = reactive({
  title: '',
  materialType: 'TEXT',
  difficultyLevel: 3,
  tags: '',
  contentText: ''
})

const renameForm = reactive({
  id: 0,
  title: ''
})

const filters = reactive({
  keyword: readQueryText(route.query.keyword),
  materialType: '',
  parseStatus: ''
})

const searchKeyword = computed({
  get: () => filters.keyword,
  set: (keyword: string) => {
    filters.keyword = keyword
    if (route.path !== '/materials') return
    const query = { ...route.query }
    if (keyword) query.keyword = keyword
    else delete query.keyword
    void router.replace({ path: route.path, query, hash: route.hash })
  }
})

const page = reactive({
  current: 1,
  size: 10
})

const hasActiveFilters = computed(() => Boolean(filters.keyword.trim() || filters.materialType || filters.parseStatus))

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.materialType, () => filters.parseStatus],
  () => {
    page.current = 1
    return loadMaterials()
  }
)

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

const formatDate = (value?: string) => {
  if (!value) return '日期待更新'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '日期待更新' : date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const formatCharacters = (value?: number) => {
  const count = Number(value)
  return Number.isFinite(count) && count > 0 ? `${Math.floor(count).toLocaleString('zh-CN')} 字` : '字数待统计'
}

const getTags = (value?: string) => Array.from(new Set((value || '').split(/[,，、;；|\n]+/).map((tag) => tag.trim()).filter(Boolean)))

const formatDifficulty = (value: number) => ['入门内容', '基础内容', '进阶内容', '较有挑战', '深入学习'][value - 1] || '学习资料'

const formatMaterialType = (value?: string) => materialTypes.find((item) => item.value === value?.toUpperCase())?.label || '学习文档'

const getMaterialIcon = (value?: string) => {
  const type = (value || '').toUpperCase()
  if (type === 'PDF') return 'file-pdf'
  if (type === 'DOCX' || type === 'DOC') return 'file-word'
  return ['TEXT', 'TXT', 'MARKDOWN'].includes(type) ? 'file-text' : 'file-unknown'
}

const formatParseStatus = (value?: string) => ({
  SUCCESS: '已解析', PENDING: '待解析', PROCESSING: '解析中', RUNNING: '解析中', FAILED: '解析失败'
}[String(value || '').toUpperCase()] || '待解析')

const formatSummaryStatus = (value?: string) => ({
  SUCCESS: '摘要已就绪', PENDING: '尚无摘要', PROCESSING: '摘要生成中', RUNNING: '摘要生成中', FAILED: '摘要生成失败'
}[String(value || '').toUpperCase()] || '尚无摘要')

const getStatusTone = (value?: string) => {
  const status = (value || '').toUpperCase()
  if (status === 'SUCCESS') return 'ready'
  if (['FAILED', 'PARTIAL_FAILED', 'PARSE_FAILED'].includes(status)) return 'failed'
  if (['PROCESSING', 'RUNNING', 'PARSING', 'PARTIAL'].includes(status)) return 'processing'
  return 'pending'
}

const getErrorMessage = (error: unknown, fallback: string) => {
  const message = error instanceof Error ? error.message : ''
  if (/network|timeout|failed to fetch/i.test(message)) return '网络连接暂时不可用，请检查连接后重试。'
  return /[\u3400-\u9fff]/.test(message) ? message : fallback
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

const formatEmbeddingProgress = (row: MaterialPageItem) => {
  const embedded = Number(row.embeddedSegmentCount || 0)
  const total = Number(row.totalSegmentCount || 0)
  if (total > 0) {
    return `已索引 ${embedded} / ${total} 个片段`
  }
  const parseStatus = String(row.parseStatus || '').toUpperCase()
  if (parseStatus === 'PROCESSING') {
    return '解析完成后可生成'
  }
  if (parseStatus === 'FAILED') {
    return '当前资料解析失败'
  }
  if (parseStatus === 'SUCCESS') return '暂无资料片段'
  return '需先解析后生成'
}

const shouldRegenerateEmbedding = (row: MaterialPageItem) =>
  Number(row.embeddedSegmentCount || 0) > 0 || ['SUCCESS', 'PARTIAL', 'PARTIAL_FAILED', 'FAILED'].includes(String(row.embeddingStatus || '').toUpperCase())

const getEmbeddingActionLabel = (row: MaterialPageItem) => shouldRegenerateEmbedding(row) ? '重新生成向量' : '生成向量'

const resetForm = () => {
  form.title = ''
  form.materialType = 'TEXT'
  form.difficultyLevel = 3
  form.tags = ''
  form.contentText = ''
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    searchKeyword.value = ''
    filters.materialType = ''
    filters.parseStatus = ''
    page.current = 1
  })
}

const loadMaterials = async () => {
  const requestVersion = ++listRequestVersion
  tableLoading.value = true
  loadError.value = ''
  try {
    const res = await getMaterialPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword.trim() || undefined,
      materialType: filters.materialType || undefined,
      parseStatus: filters.parseStatus || undefined
    })
    if (requestVersion !== listRequestVersion) return
    materials.value = res.data.data.records || []
    total.value = res.data.data.total || 0
    const lastPage = Math.max(1, Math.ceil(total.value / page.size))
    if (page.current > lastPage) {
      page.current = lastPage
      await loadMaterials()
    }
  } catch (error: any) {
    if (requestVersion === listRequestVersion) loadError.value = getErrorMessage(error, '资料暂时无法加载，请稍后重试。')
  } finally {
    if (requestVersion === listRequestVersion) tableLoading.value = false
  }
}

const changePageSize = () => {
  page.current = 1
  void loadMaterials()
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
    resetFilters()
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, '新增资料失败，请稍后重试。'))
  } finally {
    creating.value = false
  }
}

const parseMaterial = async (id: number) => {
  actionLoadingId.value = id
  try {
    await parseMaterialApi(id)
    ElMessage.success('资料解析成功')
    await loadMaterials()
    if (drawerVisible.value && detailId.value === id) await viewDetail(id)
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, '资料解析失败，请稍后重试。'))
  } finally {
    actionLoadingId.value = null
  }
}

const generateEmbedding = async (row: MaterialPageItem) => {
  if (actionLoadingId.value === row.id) return
  if (row.parseStatus !== 'SUCCESS') {
    ElMessage.warning('请先解析这份资料，再生成向量')
    return
  }
  if (row.embeddingStatus === 'RUNNING') {
    ElMessage.info('向量正在生成，请在任务中心查看进度')
    return
  }

  const forceRegenerate = shouldRegenerateEmbedding(row)
  if (forceRegenerate) {
    try {
      await ElMessageBox.confirm(`将使用当前配置的向量模型，重新生成“${row.title}”的全部向量。已有向量会被更新。`, '重新生成向量', {
        type: 'warning',
        confirmButtonText: '重新生成',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }

  actionLoadingId.value = row.id
  try {
    await submitEmbeddingTaskApi(row.id, { forceRegenerate })
    ElMessage.success('向量索引已开始生成，可在任务中心查看进度')
    await loadMaterials()
    if (drawerVisible.value && detailId.value === row.id) await viewDetail(row.id)
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, '生成向量索引失败，请稍后重试。'))
  } finally {
    actionLoadingId.value = null
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
  try {
    await deleteMaterialApi(id)
    ElMessage.success('资料删除成功')
    await loadMaterials()
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, '删除资料失败，请稍后重试。'))
  } finally {
    actionLoadingId.value = null
  }
}

const viewDetail = async (id: number) => {
  if (!Number.isSafeInteger(id) || id <= 0) return
  const requestVersion = ++detailRequestVersion
  detailId.value = id
  detail.value = null
  detailError.value = ''
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getMaterialDetailApi(id)
    if (requestVersion !== detailRequestVersion) return
    detail.value = res.data.data
    if (!detail.value) detailError.value = '这份资料不存在或已被删除，请返回资料库重新选择。'
  } catch (error: any) {
    if (requestVersion !== detailRequestVersion) return
    detail.value = null
    detailError.value = getErrorMessage(error, '资料详情加载失败，请重试。')
  } finally {
    if (requestVersion === detailRequestVersion) detailLoading.value = false
  }
}

const openRenameDialog = (row: MaterialPageItem) => {
  renameForm.id = row.id
  renameForm.title = row.title
  renameDialogVisible.value = true
}

const renameMaterial = async () => {
  if (!renameForm.id) {
    return
  }
  if (!renameForm.title.trim()) {
    ElMessage.warning('请输入资料名称')
    return
  }

  renaming.value = true
  try {
    await renameMaterialApi(renameForm.id, renameForm.title.trim())
    ElMessage.success('资料名称已更新')
    renameDialogVisible.value = false
    if (detail.value?.id === renameForm.id) {
      detail.value = {
        ...detail.value,
        title: renameForm.title.trim()
      }
    }
    await loadMaterials()
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, '修改资料名称失败，请稍后重试。'))
  } finally {
    renaming.value = false
  }
}

const openRetrievalPreview = (row: MaterialPageItem) => {
  retrievalDialogVisible.value = true
  retrievalResult.value = null
  retrievalError.value = ''
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
  retrievalError.value = ''
  try {
    const res = await previewMaterialRetrievalApi(retrievalForm.materialId, {
      queryText: retrievalForm.queryText.trim(),
      limit: retrievalForm.limit
    })
    retrievalResult.value = res.data.data as RetrievalPreviewPayload
    if (!retrievalResult.value.segments.length) {
      ElMessage.info('没有找到相关内容，可以换个更具体的问题试试')
    }
  } catch (error: any) {
    retrievalResult.value = null
    retrievalError.value = getErrorMessage(error, '暂时无法检索资料，请稍后重试。')
  } finally {
    retrievalLoading.value = false
  }
}

const uploadMaterial = async (options: UploadRequestOptions) => {
  uploading.value = true
  try {
    await uploadMaterialApi(options.file as File)
    ElMessage.success('资料已上传，打开资料后即可解析内容')
    uploadDialogVisible.value = false
    resetFilters()
    options.onSuccess?.({})
  } catch (error: any) {
    options.onError?.(error)
    ElMessage.error(getErrorMessage(error, '资料上传失败，请稍后重试。'))
  } finally {
    uploading.value = false
  }
}

const beforeUpload = (file: File) => {
  if (uploading.value) return false
  if (!/\.(pdf|docx|txt)$/i.test(file.name)) {
    ElMessage.warning('请选择 PDF、Word（.docx）或 TXT 文件')
    return false
  }
  return true
}

const handleMaterialCommand = (command: string, row: MaterialPageItem) => {
  switch (command) {
    case 'rename': openRenameDialog(row); break
    case 'parse': void parseMaterial(row.id); break
    case 'embedding': void generateEmbedding(row); break
    case 'retrieval': openRetrievalPreview(row); break
    case 'delete': void removeMaterial(row.id); break
  }
}

const clearMaterialDeepLink = () => {
  if (route.path !== '/materials' || !route.query.materialId) return
  const query = { ...route.query }
  delete query.materialId
  void router.replace({ path: route.path, query })
}

const goSummary = (id: number) => {
  router.push({ path: '/summary', query: { materialId: String(id) } })
}

const goQuiz = (id: number) => {
  router.push({ path: '/quiz', query: { materialId: String(id) } })
}

watch(() => route.query.keyword, (value) => {
  if (route.path !== '/materials') return
  const keyword = readQueryText(value)
  if (keyword !== filters.keyword) {
    autoQuery.runAfterMutation(() => { filters.keyword = keyword; page.current = 1 })
  }
})

watch(() => route.query.materialId, (value) => {
  if (route.path !== '/materials') return
  const rawId = readQueryText(value)
  if (!rawId) return
  const id = Number(rawId)
  if (!/^[1-9]\d*$/.test(rawId) || !Number.isSafeInteger(id)) {
    ElMessage.warning('资料链接无效，请从资料库重新选择')
    return
  }
  void viewDetail(id)
}, { immediate: true })

watch(() => route.query.action, (value) => {
  if (route.path !== '/materials' || value !== 'upload') return
  uploadDialogVisible.value = true
  const query = { ...route.query }
  delete query.action
  void router.replace({ path: route.path, query })
}, { immediate: true })

onUnmounted(() => {
  listRequestVersion += 1
  detailRequestVersion += 1
})

void loadMaterials()
</script>

<style scoped>
.material-library { width: 100%; max-width: 1180px; min-width: 0; margin: 0 auto; }
.material-library__header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 38px; }
.material-library__eyebrow { margin: 0 0 10px; color: var(--muted); font-size: 13px; font-weight: 500; letter-spacing: .12em; }
.material-library__header .page-title { font-size: clamp(28px, 3vw, 36px); font-weight: 600; letter-spacing: -.035em; }
.material-library__description { margin: 12px 0 0; color: var(--muted); font-size: 15px; line-height: 1.8; }
.material-library__header-actions { display: flex; gap: 10px; flex: 0 0 auto; }
.material-library :deep(.el-button) { min-height: 44px; }
.material-library__header-actions :deep(.el-button) { margin: 0; padding-inline: 22px; }
.material-library :deep(.el-button > span), .material-library-detail :deep(.el-button > span) { gap: 7px; }
.material-library__retrieval { display: flex; align-items: center; gap: 16px; margin-bottom: 28px; padding: 20px 22px; border-radius: 12px; background: var(--brand-light); color: var(--brand); }
.material-library__retrieval > .app-icon { flex: 0 0 auto; }
.material-library__retrieval > div { flex: 1; min-width: 0; }
.material-library__retrieval h2 { margin: 0 0 5px; color: var(--text); font-size: 15px; font-weight: 600; }
.material-library__retrieval p { margin: 0; color: var(--text-secondary); font-size: 13px; line-height: 1.8; }
.material-library__retrieval a, .material-library-detail__retrieval a { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 44px; border-radius: 6px; color: var(--brand); font-size: 13px; text-decoration: none; }
.material-library__retrieval > a { flex: 0 0 auto; }
.material-library__retrieval a:hover, .material-library-detail__retrieval a:hover { color: var(--brand-hover); text-decoration: underline; text-underline-offset: 4px; }
.material-library__filters { display: grid; grid-template-columns: minmax(180px, 1fr) 158px 150px 64px; gap: 12px; align-items: center; padding-bottom: 26px; border-bottom: 1px solid var(--line); }
.material-library__filters :deep(.el-select), .material-library__filters :deep(.el-input) { min-width: 0; width: 100%; }
.material-library__filters :deep(.el-input__wrapper), .material-library__filters :deep(.el-select__wrapper) { min-height: 46px; background: transparent; box-shadow: none; border-radius: 999px; padding-inline: 16px; }
.material-library__filters :deep(.el-input__wrapper) { background: var(--bg-secondary); }
.material-library__filters :deep(.el-select__wrapper:hover) { background: var(--bg-secondary); }
.material-library__filters :deep(.el-input__wrapper.is-focus), .material-library__filters :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 2px var(--brand); }
.material-library__search :deep(.el-input__prefix) { margin-right: 6px; color: var(--muted); }
.material-library__reset { margin: 0; }
.material-library__section-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin: 26px 0 6px; }
.material-library__section-heading h2 { display: flex; align-items: baseline; gap: 12px; margin: 0; color: var(--text); font-size: 18px; font-weight: 600; }
.material-library__section-heading h2 span { color: var(--muted); font-size: 14px; font-weight: 400; font-variant-numeric: tabular-nums; }
.material-library__refresh { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 44px; padding: 0 10px; border: 0; border-radius: 999px; background: transparent; color: var(--muted); font: inherit; font-size: 14px; cursor: pointer; }
.material-library__refresh:hover { color: var(--brand); background: var(--bg-secondary); }
.material-library__refresh:disabled { cursor: wait; opacity: .55; }
.material-library__grid { display: grid; grid-template-columns: minmax(0, 1fr); }
.material-library-card { display: grid; grid-template-columns: 74px minmax(0, 1fr) 224px; align-items: center; gap: 24px; min-width: 0; padding: 26px 0; border-bottom: 1px solid var(--line); }
.material-library-card__cover { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; width: 74px; height: 104px; padding: 14px 6px 12px 10px; border: 0; border-left: 5px solid #bacbbc; border-radius: 2px 7px 7px 2px; background: #e5eae0; color: #506651; font: inherit; cursor: pointer; box-shadow: 2px 3px 0 #eeeee6; transition: background .18s; }
.material-library-card__cover[data-kind="PDF"] { background: #ede1d7; color: #956952; border-left-color: #d3baaa; }
.material-library-card__cover[data-kind="DOC"], .material-library-card__cover[data-kind="DOCX"], .material-library-card__cover[data-kind="WORD"] { background: #e1e8e9; color: #577477; border-left-color: #b7cbd0; }
.material-library-card__cover span { max-width: 100%; color: inherit; font-size: 10px; letter-spacing: .02em; text-align: center; overflow-wrap: anywhere; }
.material-library-card__cover :deep(.app-icon path[stroke]) { stroke: currentColor; }
.material-library-card__cover :deep(.app-icon path[fill-opacity]), .material-library-card__cover :deep(.app-icon rect) { fill: currentColor; }
.material-library-card__cover:hover { filter: brightness(.97); }
.material-library-card__file { display: grid; place-items: center; flex: 0 0 auto; width: 50px; height: 54px; border-radius: 10px; background: var(--bg); }
.material-library-card__content, .material-library-card__aside { min-width: 0; }
.material-library-card__top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.material-library-card__menu { flex-shrink: 0; }
.material-library-card__more { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 44px; padding: 0 12px; border: 1px solid color-mix(in srgb, var(--brand) 28%, var(--line)); border-radius: 8px; background: var(--brand-light); color: var(--brand); font: inherit; font-size: 13px; font-weight: 600; line-height: 1; white-space: nowrap; cursor: pointer; transition: background .18s, border-color .18s, color .18s; }
.material-library-card__more:hover:not(:disabled), .material-library-card__more[aria-expanded="true"] { border-color: var(--brand); background: color-mix(in srgb, var(--brand) 10%, var(--brand-light)); color: var(--brand-hover); }
.material-library-card__more:disabled { cursor: wait; opacity: .6; }
.material-library-card h3 { min-width: 0; margin: 0; color: var(--text); font-size: 18px; font-weight: 600; line-height: 1.65; overflow-wrap: anywhere; }
.material-library-card h3 button { display: block; max-width: 100%; min-height: 44px; padding: 5px 0; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.material-library-card h3 button:hover { color: var(--brand); }
.material-library-card__meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 12px; margin: 2px 0 10px; color: var(--muted); font-size: 13px; line-height: 1.65; font-variant-numeric: tabular-nums; }
.material-library-card__meta span + span::before { content: '·'; margin-right: 12px; }
.material-library-card__tags { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 12px; min-height: 22px; }
.material-library-card__tag { max-width: 14em; overflow: hidden; color: var(--muted); font-size: 12px; line-height: 1.6; text-overflow: ellipsis; white-space: nowrap; }
.material-library-card__tag::before { content: '#'; margin-right: 2px; opacity: .65; }
.material-library-card__untagged { color: var(--muted); font-size: 12px; }
.material-library-card__vector { display: flex; align-items: center; flex-wrap: wrap; gap: 0 16px; margin-top: 7px; }
.material-library-card__vector button, .material-library-card__vector a { display: inline-flex; align-items: center; min-height: 44px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--brand); font: inherit; font-size: 12px; text-decoration: none; cursor: pointer; }
.material-library-card__vector button:hover, .material-library-card__vector a:hover { color: var(--brand-hover); text-decoration: underline; text-underline-offset: 4px; }
.material-library-card__vector button:disabled { color: var(--muted); opacity: .6; cursor: not-allowed; text-decoration: none; }
.material-library-card__status-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 10px; margin-bottom: 14px; }
.material-library-status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; line-height: 1.5; white-space: nowrap; }
.material-library-status i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.material-library-status--ready { color: var(--brand); }
.material-library-status--pending { color: var(--muted); }
.material-library-status--processing { color: #956931; }
.material-library-status--failed { color: var(--red); }
.material-library-card__summary { color: var(--muted); font-size: 12px; }
.material-library-card__actions { display: grid; grid-template-columns: minmax(0, 1fr) 50px 50px; align-items: center; gap: 4px; }
.material-library-card__actions button { min-width: 0; min-height: 44px; padding: 0 8px; border: 0; border-radius: 999px; font: inherit; font-size: 14px; font-weight: 500; cursor: pointer; transition: background .18s ease, color .18s ease; }
.material-library-card__open { display: inline-flex; align-items: center; justify-content: center; gap: 10px; background: var(--brand-soft); color: var(--brand); }
.material-library-card__open:hover { background: #d8e3d7; }
.material-library-card__study { color: var(--muted); background: transparent; }
.material-library-card__study:hover { color: var(--brand); background: var(--bg-secondary); }
.material-library__state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; min-height: 370px; padding: 52px 24px; text-align: center; }
.material-library__state-icon { display: grid; place-items: center; width: 72px; height: 72px; border-radius: 50%; background: var(--brand-soft); color: var(--brand); }
.material-library__state h2 { margin: 4px 0 0; font-size: 21px; font-weight: 500; color: var(--text); line-height: 1.5; }
.material-library__state p { max-width: 430px; margin: 0 0 5px; color: var(--muted); font-size: 15px; line-height: 1.9; overflow-wrap: anywhere; }
.material-library__text-link { display: inline-flex; align-items: center; gap: 6px; min-height: 44px; border: 0; border-radius: 8px; background: transparent; color: var(--muted); font: inherit; font-size: 14px; cursor: pointer; }
.material-library__text-link:hover { color: var(--brand); }
.material-library-card--loading { grid-template-columns: 74px minmax(0, 1fr) 224px; min-height: 150px; gap: 14px 24px; }
.material-library__skeleton { border-radius: 6px; background: color-mix(in srgb, var(--muted) 10%, var(--panel)); animation: materialLibraryPulse 1.7s ease-in-out infinite; }
.material-library__skeleton--icon { width: 74px; height: 104px; grid-row: 1 / 3; }
.material-library__skeleton--title { width: 76%; height: 22px; align-self: end; }
.material-library__skeleton--text { width: 50%; height: 16px; grid-column: 2; align-self: start; }
.material-library__skeleton--footer { height: 44px; width: 100%; grid-row: 1 / 3; grid-column: 3; }
.material-library__spinner { display: inline-block; width: 18px; height: 18px; flex: 0 0 auto; border: 2px solid var(--line); border-top-color: var(--brand); border-radius: 50%; animation: materialLibrarySpin .8s linear infinite; }
@keyframes materialLibraryPulse { 50% { opacity: .45; } }
@keyframes materialLibrarySpin { to { transform: rotate(360deg); } }
.material-library__pagination { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 18px; padding-top: 26px; }
.material-library__pagination-meta { display: flex; align-items: center; gap: 16px; color: var(--muted); font-size: 13px; }
.material-library__pagination-meta :deep(.el-select) { width: 132px; }
.material-library__pagination-meta :deep(.el-select__wrapper) { min-height: 44px; background: transparent; box-shadow: none; }
.material-library__pagination-controls { display: flex; align-items: center; gap: 12px; min-width: 0; }
.material-library__page-indicator { display: none; color: var(--muted); font-size: 13px; font-variant-numeric: tabular-nums; }
.material-library__pagination :deep(.el-pagination) { gap: 3px; }
.material-library__pagination :deep(.el-pager li), .material-library__pagination :deep(.btn-prev), .material-library__pagination :deep(.btn-next) { min-width: 44px; height: 44px; border-radius: 50%; background: transparent; }
.material-library__pagination :deep(.el-pager li.is-active) { color: var(--brand); background: var(--brand-soft); }

.material-library-upload > p { margin: 0 0 20px; color: var(--muted); font-size: 14px; line-height: 1.8; }
.material-library-upload :deep(.el-upload) { display: block; width: 100%; }
.material-library-upload :deep(.el-upload-dragger) { padding: 36px 18px; border: 1px dashed color-mix(in srgb, var(--brand) 35%, var(--line)); border-radius: 14px; background: color-mix(in srgb, var(--brand) 3%, var(--panel)); }
.material-library-upload .app-icon { margin: 0 auto 18px; color: var(--brand); }
.material-library-upload strong { display: block; color: var(--text); font-size: 16px; font-weight: 600; line-height: 1.7; }
.material-library-upload span:not(.material-library__spinner) { display: block; margin-top: 10px; color: var(--muted); font-size: 12px; }
.material-library-upload .material-library__spinner { margin-top: 18px; }
.material-library-form__grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.material-library-form :deep(.el-input__wrapper), .material-library-form :deep(.el-select__wrapper), .material-library-form :deep(.el-input-number) { min-height: 44px; }
.material-library-dialog__footer { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
.material-library-dialog__footer :deep(.el-button) { min-height: 44px; margin: 0; }
.material-library-detail { min-width: 0; }
.material-library-detail__heading { display: flex; align-items: flex-start; gap: 16px; }
.material-library-detail__heading > div { min-width: 0; }
.material-library-detail__heading span { color: var(--muted); font-size: 12px; }
.material-library-detail__heading h2 { margin: 8px 0 0; color: var(--text); font-size: 23px; font-weight: 650; line-height: 1.5; overflow-wrap: anywhere; }
.material-library-detail__meta { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 24px 0; padding: 20px; border: 1px solid var(--line); border-radius: 12px; background: var(--bg); }
.material-library-detail__meta div { min-width: 0; }
.material-library-detail__meta dt { margin-bottom: 7px; color: var(--muted); font-size: 12px; }
.material-library-detail__meta dd { margin: 0; color: var(--text); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.material-library-detail__actions { display: flex; flex-wrap: wrap; gap: 10px; }
.material-library-detail :deep(.el-button) { min-height: 44px; margin: 0; }
.material-library-detail__retrieval { margin-top: 24px; padding: 18px 20px; border-radius: 12px; background: var(--bg); }
.material-library-detail__retrieval-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0 14px; }
.material-library-detail__retrieval h3 { margin: 0; color: var(--text); font-size: 15px; font-weight: 600; }
.material-library-detail__retrieval p { margin: 6px 0 16px; color: var(--muted); font-size: 13px; line-height: 1.8; }
.material-library-detail__task-link { padding-inline: 8px; }
.material-library-detail__segments { margin-top: 30px; }
.material-library-detail__segments > h3 { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 0 0 16px; font-size: 16px; color: var(--text); }
.material-library-detail__segments > h3 span { color: var(--muted); font-size: 12px; font-weight: 400; }
.material-library-detail__segment { padding: 20px 0; border-top: 1px solid var(--line); }
.material-library-detail__segment-meta { color: var(--muted); font-size: 12px; }
.material-library-detail__segment h4 { margin: 10px 0; color: var(--text); font-size: 15px; font-weight: 600; }
.material-library-detail__segment p { margin: 0; color: var(--text); font-size: 14px; line-height: 1.95; overflow-wrap: anywhere; white-space: pre-wrap; }
.material-library-detail__empty { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; margin-top: 24px; padding: 22px; border: 1px dashed var(--line); border-radius: 12px; color: var(--muted); font-size: 14px; line-height: 1.8; }
:global(.material-library-menu .el-dropdown-menu__item) { min-height: 44px; font-size: 13px; }
:global(.material-library-menu .material-library-menu__danger) { color: color-mix(in srgb, var(--red) 70%, var(--text)); }
:global(.material-library-modal .el-dialog__headerbtn), :global(.material-library-modal .el-drawer__close-btn) { min-width: 44px; min-height: 44px; }
.material-library button:focus-visible, .material-library a:focus-visible, .material-library-detail a:focus-visible, .material-library-upload :deep(.el-upload:focus-visible) { outline: 2px solid var(--brand); outline-offset: 3px; }

@media (max-width: 920px) {
  .material-library__header { flex-wrap: wrap; }
  .material-library__filters { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 64px; }
  .material-library__search { grid-column: 1 / -1; }
  .material-library-card { grid-template-columns: 64px minmax(0, 1fr); gap: 14px 20px; }
  .material-library-card__cover { width: 64px; height: 90px; }
  .material-library-card__aside { grid-column: 2; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px 20px; }
  .material-library-card__status-row { justify-content: flex-start; margin-bottom: 0; }
  .material-library-card__actions { flex: 0 0 214px; margin-left: auto; }
  .material-library-card--loading { grid-template-columns: 64px minmax(0, 1fr); }
  .material-library__skeleton--icon { width: 64px; height: 90px; }
  .material-library__skeleton--footer { display: none; }
}
@media (max-width: 640px) {
  .material-library__header { gap: 24px; margin-bottom: 28px; }
  .material-library__header-actions { width: 100%; }
  .material-library__header-actions :deep(.el-button) { flex: 1; padding-inline: 14px; }
  .material-library__description { font-size: 14px; }
  .material-library__retrieval { align-items: flex-start; flex-wrap: wrap; gap: 8px 12px; padding: 18px; }
  .material-library__retrieval > .app-icon { margin-top: 2px; }
  .material-library__retrieval > div { flex-basis: calc(100% - 34px); }
  .material-library__retrieval > a { margin-left: 34px; }
  .material-library__filters { gap: 10px 4px; padding-bottom: 18px; }
  .material-library__filters :deep(.el-select__wrapper) { padding-inline: 10px; }
  .material-library__section-heading { margin-top: 18px; }
  .material-library-card { padding-block: 24px; gap: 16px; }
  .material-library-card__top { flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
  .material-library-card__top h3 { flex-basis: 100%; }
  .material-library-card h3 { font-size: 17px; }
  .material-library-card__meta { font-size: 12px; gap: 4px 8px; }
  .material-library-card__meta span + span::before { margin-right: 8px; }
  .material-library-card__aside { grid-column: 1 / -1; }
  .material-library-card__actions { flex-basis: 190px; }
  .material-library__state { min-height: 330px; padding-inline: 12px; }
  .material-library__state h2 { font-size: 19px; }
  .material-library__pagination { gap: 12px; }
  .material-library__pagination-controls { margin-left: auto; }
  .material-library__pagination :deep(.el-pager) { display: none; }
  .material-library__page-indicator { display: block; }
  .material-library-form__grid { grid-template-columns: 1fr; gap: 0; }
  .material-library-form :deep(.el-input__inner), .material-library-form :deep(.el-textarea__inner) { font-size: 16px; }
  .material-library-detail__meta { grid-template-columns: 1fr; gap: 18px; }
  .material-library-detail__heading h2 { font-size: 20px; }
}

@media (prefers-reduced-motion: reduce) {
  .material-library-card, .material-library-card__actions button, .material-library-card__more { transition: none; }
  .material-library__skeleton, .material-library__spinner { animation: none; }
}
</style>
