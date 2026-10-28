<template>
  <section class="learning-page material-library" aria-labelledby="material-library-title">
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">我的内容</div>
        <h1 id="material-library-title" class="page-title">资料库</h1>
        <p class="page-desc">讲义、笔记与文档，都收在这里。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button @click="textDialogVisible = true">
          <AppIcon name="summary" :size="18" />
          写入文本
        </el-button>
        <el-button type="primary" @click="uploadDialogVisible = true">
          <AppIcon name="upload" :size="18" />
          上传资料
        </el-button>
      </div>
    </div>

    <p class="material-library__hint">
      <AppIcon name="search" :size="16" />
      <span>上传后系统会自动建立向量索引，AI 可按含义查找资料片段；也可在卡片菜单中管理向量与预览检索。</span>
      <RouterLink to="/ai-config#vector-model">配置向量模型<AppIcon name="arrow-up-right" :size="14" /></RouterLink>
    </p>

    <div class="material-library__filters" role="search" aria-label="筛选学习资料">
      <el-input
        v-model="searchKeyword"
        clearable
        aria-label="按名称、标签或说明搜索资料"
        placeholder="搜索资料名称、标签或说明…"
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
      <el-button text class="material-library__reset" :disabled="!hasActiveFilters" @click="resetFilters"
        >重置</el-button
      >
    </div>

    <div class="material-library__section-heading">
      <ListSectionHeading
        :title="hasActiveFilters ? '筛选结果' : '全部资料'"
        :count="tableLoading || loadError ? undefined : total"
        :loading="tableLoading"
        @refresh="loadMaterials"
      />
    </div>

    <div v-if="tableLoading" class="material-library__grid" role="status" aria-label="正在加载资料" aria-busy="true">
      <div v-for="i in 3" :key="i" class="material-library-card material-library-card--loading">
        <div class="material-library__skeleton material-library__skeleton--icon"></div>
        <div class="material-library__skeleton material-library__skeleton--title"></div>
        <div class="material-library__skeleton material-library__skeleton--text"></div>
        <div class="material-library__skeleton material-library__skeleton--footer"></div>
      </div>
    </div>
    <EmptyState v-else-if="loadError" variant="error" icon="refresh" title="资料加载失败" :description="loadError">
      <el-button type="primary" @click="loadMaterials">重新加载</el-button>
    </EmptyState>
    <EmptyState
      v-else-if="!materialRows.length && hasActiveFilters"
      icon="search"
      title="没有找到匹配的资料"
      description="可以尝试更换关键词或重置筛选条件。"
    >
      <el-button @click="resetFilters">清空筛选条件</el-button>
    </EmptyState>
    <EmptyState
      v-else-if="!materialRows.length"
      icon="book-open"
      title="开启你的学习之旅"
      description="你可以上传课程资料、教材文档，或是直接记下复习笔记。AI 会自动为你梳理核心要点、出题自测，帮你掌握每一个知识点。"
    >
      <el-button type="primary" @click="uploadDialogVisible = true"
        ><AppIcon name="upload" :size="18" />上传第一份资料</el-button
      >
      <button type="button" class="material-library__text-link" @click="textDialogVisible = true">
        也可以直接写入文本 <AppIcon name="arrow-right" :size="16" />
      </button>
    </EmptyState>
    <div v-else class="material-library__grid">
      <article v-for="row in materialRows" :key="row.id" class="material-library-card" :aria-label="row.title">
        <button
          type="button"
          class="material-library-card__cover"
          :data-kind="row.materialType"
          :aria-label="`查看${row.title}`"
          @click="viewDetail(row.id)"
        >
          <AppIcon :name="materialTypeIcon(row.materialType)" :size="26" />
          <span>{{ row.materialType === 'MARKDOWN' ? 'MD' : row.materialType }}</span>
        </button>
        <div class="material-library-card__content">
          <h3 :title="row.title">
            <button type="button" @click="viewDetail(row.id)">{{ row.title }}</button>
          </h3>
          <p v-if="row.description" class="material-library-card__desc" :title="row.description">
            {{ row.description }}
          </p>
          <p class="material-library-card__meta">
            <span>{{ formatDate(row.createdAt) }}</span>
            <span>{{ formatCharacters(row.totalCharacters) }}</span>
            <StatusPill v-if="isProcessing(row)" tone="processing" :label="getProcessingText(row)" />
            <StatusPill v-else-if="row.parseStatus === 'FAILED'" tone="failed" label="解析失败" />
            <StatusPill v-else-if="row.parseStatus === 'PENDING'" tone="muted" label="待解析" />
          </p>
          <div v-if="row.tagList.length" class="material-library-card__tags">
            <span v-for="tag in row.tagList.slice(0, 3)" :key="tag" class="material-library-card__tag" :title="tag">{{
              tag
            }}</span>
            <span
              v-if="row.tagList.length > 3"
              class="material-library-card__tag"
              :title="row.tagList.slice(3).join('、')"
              >+{{ row.tagList.length - 3 }}</span
            >
          </div>
        </div>
        <div class="material-library-card__aside" role="group" :aria-label="`${row.title}的操作`">
          <div class="material-library-card__actions">
            <button type="button" class="material-library-card__open" @click="viewDetail(row.id)">
              阅读<AppIcon name="arrow-right" :size="16" />
            </button>
            <button
              type="button"
              class="material-library-card__study"
              :class="{ 'is-loading': row.summaryStatus === 'RUNNING' }"
              :disabled="row.parseStatus !== 'SUCCESS' || row.summaryStatus === 'RUNNING'"
              :aria-label="`${row.title}的 AI 摘要`"
              @click="goSummary(row.id)"
            >
              <span
                v-if="row.summaryStatus === 'RUNNING'"
                class="material-library__spinner material-library__spinner--inline"
              ></span>
              {{ row.summaryStatus === 'RUNNING' ? '摘要生成中' : '摘要' }}
            </button>
            <button
              type="button"
              class="material-library-card__study"
              :disabled="row.parseStatus !== 'SUCCESS'"
              :aria-label="`根据${row.title}生成练习`"
              @click="goQuiz(row.id)"
            >
              出题
            </button>
            <el-dropdown
              class="material-library-card__menu"
              trigger="click"
              :disabled="actionLoadingId === row.id"
              @command="(command: string) => handleMaterialCommand(command, row)"
            >
              <IconMenuButton :label="`${row.title}的更多操作`" :loading="actionLoadingId === row.id" />
              <template #dropdown>
                <el-dropdown-menu class="material-library-menu">
                  <el-dropdown-item command="retrieval" :disabled="row.parseStatus !== 'SUCCESS'"
                    >检索预览</el-dropdown-item
                  >
                  <el-dropdown-item
                    command="embedding"
                    :disabled="row.parseStatus !== 'SUCCESS' || row.embeddingStatus === 'RUNNING'"
                    >{{ getEmbeddingActionLabel(row) }}</el-dropdown-item
                  >
                  <el-dropdown-item command="edit" divided>编辑信息</el-dropdown-item>
                  <el-dropdown-item command="parse">{{
                    row.parseStatus === 'SUCCESS' ? '重新解析资料' : '解析资料'
                  }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided class="material-library-menu__danger"
                    >删除资料</el-dropdown-item
                  >
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </article>
    </div>

    <div v-if="!tableLoading && !loadError && total > 0" class="workspace-pagination">
      <div class="workspace-pagination__meta">共 {{ total }} 份资料</div>
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        :total="total"
        @current-change="loadMaterials"
        @size-change="loadMaterials"
      />
    </div>

    <el-dialog
      v-model="uploadDialogVisible"
      class="material-library-modal"
      title="上传学习资料"
      width="min(560px, calc(100vw - 32px))"
      :close-on-click-modal="!uploading"
      :close-on-press-escape="!uploading"
      :show-close="!uploading"
      destroy-on-close
    >
      <div class="material-library-upload">
        <p>添加课程讲义或教材，随后就能整理重点、生成练习。</p>
        <el-upload
          drag
          :show-file-list="false"
          :http-request="uploadMaterial"
          :before-upload="beforeUpload"
          :disabled="uploading"
          accept=".pdf,.docx,.txt"
        >
          <AppIcon name="upload" :size="36" />
          <strong>{{ uploading ? '正在上传，请稍候…' : '点击选择文件，或拖到这里' }}</strong>
          <span>支持 PDF、Word（.docx）和 TXT 文本</span>
          <span v-if="uploading" class="material-library__spinner" role="status" aria-label="上传中"></span>
        </el-upload>
      </div>
      <template #footer
        ><div class="material-library-dialog__footer">
          <el-button :disabled="uploading" @click="uploadDialogVisible = false">{{
            uploading ? '正在上传…' : '取消'
          }}</el-button>
        </div></template
      >
    </el-dialog>

    <el-dialog
      v-model="textDialogVisible"
      class="material-library-modal"
      title="写入学习笔记"
      width="min(720px, calc(100vw - 32px))"
      destroy-on-close
    >
      <el-form label-position="top" class="material-library-form">
        <el-form-item label="资料标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="例如：数据库系统原理 第三章事务管理" />
        </el-form-item>
        <div class="material-library-form__grid">
          <el-form-item label="资料类型">
            <el-select v-model="form.materialType" class="material-library-form__full">
              <el-option label="纯文本笔记" value="TEXT" />
              <el-option label="格式化笔记（Markdown）" value="MARKDOWN" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="form.tags" placeholder="例如：数据库、期末复习、重点章节" />
          </el-form-item>
        </div>
        <el-form-item label="难度等级">
          <el-slider
            v-model="form.difficultyLevel"
            :min="1"
            :max="5"
            show-stops
            :format-tooltip="formatDifficulty"
            aria-label="资料难度"
          />
        </el-form-item>
        <el-form-item label="资料说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="选填，可记录来源、版本或用途"
          />
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

    <el-dialog
      v-model="editDialogVisible"
      class="material-library-modal"
      title="编辑资料信息"
      width="min(620px, calc(100vw - 32px))"
      destroy-on-close
    >
      <el-form label-position="top" class="material-library-form" @submit.prevent="saveMaterialInfo">
        <el-form-item label="资料标题" required :error="editTitleError">
          <el-input
            v-model="editForm.title"
            maxlength="200"
            show-word-limit
            placeholder="请输入资料标题"
            @input="editTitleError = ''"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input
            v-model="editForm.tags"
            maxlength="255"
            placeholder="多个标签用逗号或顿号分隔，例如：期末复习、第三章"
          />
        </el-form-item>
        <el-form-item label="难度等级">
          <el-slider
            v-model="editForm.difficultyLevel"
            :min="1"
            :max="5"
            show-stops
            :format-tooltip="formatDifficulty"
            aria-label="资料难度"
          />
        </el-form-item>
        <el-form-item label="资料说明">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可记录来源、版本或用途，例如：人教版生物必修 1 第 3 章课件，仅用于个人复习"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="material-library-dialog__footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingInfo" @click="saveMaterialInfo">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-model="drawerVisible"
      class="material-library-modal"
      title="资料详情"
      size="min(720px, 100vw)"
      @closed="clearMaterialDeepLink"
    >
      <div class="material-library-detail">
        <div v-if="detailLoading" class="material-library-detail__loading" role="status">
          <span class="material-library__spinner"></span>
          <p>正在打开资料…</p>
        </div>
        <EmptyState
          v-else-if="detailError"
          variant="error"
          icon="refresh"
          title="暂时无法打开这份资料"
          :description="detailError"
        >
          <el-button @click="viewDetail(detailId)">重试</el-button>
        </EmptyState>
        <template v-else-if="detail">
          <div class="material-library-detail__heading">
            <AppIcon :name="materialTypeIcon(detail.materialType)" :size="42" />
            <div>
              <span>{{ formatMaterialType(detail.materialType) }}</span>
              <h2>{{ detail.title }}</h2>
            </div>
          </div>
          <dl class="material-library-detail__meta">
            <div>
              <dt>添加时间</dt>
              <dd>{{ formatDateTime(detail.createdAt) }}</dd>
            </div>
            <div>
              <dt>内容长度</dt>
              <dd>{{ formatCharacters(detail.totalCharacters) }}</dd>
            </div>
            <div>
              <dt>内容解析</dt>
              <dd>{{ formatParseStatus(detail.parseStatus) }}</dd>
            </div>
            <div>
              <dt>学习摘要</dt>
              <dd>{{ formatSummaryStatus(detail.summaryStatus) }}</dd>
            </div>
            <div>
              <dt>向量索引</dt>
              <dd>{{ formatEmbeddingStatus(detail.embeddingStatus) }} · {{ formatEmbeddingProgress(detail) }}</dd>
            </div>
            <div>
              <dt>资料标签</dt>
              <dd>{{ getTags(detail.tags).join('、') || '暂无标签' }}</dd>
            </div>
            <div>
              <dt>难度等级</dt>
              <dd>{{ formatDifficulty(detail.difficultyLevel) }}</dd>
            </div>
            <div class="material-library-detail__meta-wide">
              <dt>资料说明</dt>
              <dd :class="{ 'is-empty': !detail.description }">
                {{ detail.description || '暂无说明，可在“编辑信息”中补充来源、版本或备注。' }}
              </dd>
            </div>
          </dl>
          <div class="material-library-detail__actions">
            <el-button
              v-if="detail.parseStatus !== 'SUCCESS'"
              type="primary"
              :loading="actionLoadingId === detail.id"
              @click="parseMaterial(detail.id)"
              >解析这份资料</el-button
            >
            <el-button v-else type="primary" @click="goSummary(detail.id)"
              ><AppIcon name="summary" :size="16" />生成或查看摘要</el-button
            >
            <el-button @click="goQuiz(detail.id)"><AppIcon name="quiz" :size="16" />生成练习</el-button>
            <el-button @click="openEditDialog(detail)">编辑信息</el-button>
          </div>
          <section class="material-library-detail__retrieval" aria-labelledby="material-detail-retrieval-title">
            <div class="material-library-detail__retrieval-heading">
              <h3 id="material-detail-retrieval-title">向量检索</h3>
              <RouterLink to="/ai-config#vector-model"
                >配置向量模型<AppIcon name="arrow-up-right" :size="14"
              /></RouterLink>
            </div>
            <p>
              {{
                detail.parseStatus === 'SUCCESS'
                  ? '生成向量后，可按问题的含义查找这份资料中的相关片段。'
                  : '先解析资料，再生成向量，即可查找相关片段。'
              }}
            </p>
            <div class="material-library-detail__actions">
              <el-button
                :disabled="detail.parseStatus !== 'SUCCESS' || detail.embeddingStatus === 'RUNNING'"
                :loading="actionLoadingId === detail.id"
                @click="generateEmbedding(detail)"
                >{{ getEmbeddingActionLabel(detail) }}</el-button
              >
              <el-button :disabled="detail.parseStatus !== 'SUCCESS'" @click="openRetrievalPreview(detail)"
                ><AppIcon name="search" :size="16" />检索预览</el-button
              >
              <RouterLink
                v-if="detail.embeddingStatus === 'RUNNING'"
                class="material-library-detail__task-link"
                to="/ai-tasks"
                >查看生成进度</RouterLink
              >
            </div>
          </section>
          <div v-if="detail.segments?.length" class="material-library-detail__segments">
            <h3>
              资料内容 <span>{{ detail.segments.length }} 个片段</span>
            </h3>
            <article v-for="segment in detail.segments" :key="segment.id" class="material-library-detail__segment">
              <div class="material-library-detail__segment-meta">{{ formatSegmentMeta(segment) }}</div>
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

    <el-dialog
      v-model="retrievalDialogVisible"
      class="material-library-modal"
      title="检索资料内容"
      width="min(860px, calc(100vw - 32px))"
      destroy-on-close
    >
      <div class="material-library-form">
        <div class="material-library-form__grid">
          <el-form-item label="资料">
            <el-input :model-value="retrievalForm.materialTitle" disabled />
          </el-form-item>
          <el-form-item label="返回片段数">
            <el-input-number v-model="retrievalForm.limit" :min="1" :max="10" class="material-library-form__full" />
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
        <div v-else-if="retrievalError" class="material-library-detail__empty" role="alert">
          {{ retrievalError }}<el-button @click="runRetrievalPreview">重新检索</el-button>
        </div>
        <div v-else-if="retrievalResult" class="list-stack material-library-retrieval__result">
          <div class="soft-text">共命中 {{ retrievalResult.hitCount }} 条</div>
          <div v-for="segment in retrievalResult.segments" :key="segment.segmentId" class="section-card">
            <div class="summary-history-card__top">
              <div>
                <div class="section-card__title">
                  {{ segment.sectionTitle || `第 ${segment.segmentNo || '--'} 段` }}
                </div>
                <div class="section-card__meta">
                  {{ formatSegmentMeta(segment) }} · 相似度 {{ formatRetrievalScore(segment.score) }}
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
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import ListSectionHeading from '@/components/ListSectionHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import { usePolling } from '@/composables/usePolling'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatCharacters, formatDate, formatDateTime, formatSegmentMeta } from '@/utils/format'
import { formatDifficulty, formatMaterialType, formatParseStatus, materialTypeIcon } from '@/utils/labels'
import { previewMaterialRetrievalApi, submitEmbeddingTaskApi, type RetrievalPreviewPayload } from '@/api/modules/ai'
import {
  createTextMaterialApi,
  deleteMaterialApi,
  getMaterialDetailApi,
  getMaterialPageApi,
  parseMaterialApi,
  updateMaterialApi,
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
const editDialogVisible = ref(false)
const retrievalDialogVisible = ref(false)
const tableLoading = ref(false)
const detailLoading = ref(false)
const creating = ref(false)
const savingInfo = ref(false)
const uploading = ref(false)
const retrievalLoading = ref(false)
const actionLoadingId = ref<number | null>(null)
const retrievalResult = ref<RetrievalPreviewPayload | null>(null)
const loadError = ref('')
const detailError = ref('')
const retrievalError = ref('')
const editTitleError = ref('')
const editTargetId = ref<number | null>(null)
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

const readQueryText = (value: unknown) => (typeof value === 'string' ? value : '')

const form = reactive({
  title: '',
  materialType: 'TEXT',
  difficultyLevel: 3,
  tags: '',
  description: '',
  contentText: ''
})

const editForm = reactive({
  title: '',
  tags: '',
  difficultyLevel: 3,
  description: ''
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

const getTags = (value?: string) =>
  Array.from(
    new Set(
      (value || '')
        .split(/[,，、;；|\n]+/)
        .map((tag) => tag.trim())
        .filter(Boolean)
    )
  )

/** 每行只解析一次标签，模板里直接用 row.tagList。 */
const materialRows = computed(() => materials.value.map((row) => ({ ...row, tagList: getTags(row.tags) })))

const formatSummaryStatus = (value?: string) =>
  ({
    SUCCESS: '摘要已就绪',
    PENDING: '尚无摘要',
    PROCESSING: '摘要生成中',
    RUNNING: '摘要生成中',
    FAILED: '摘要生成失败'
  })[String(value || '').toUpperCase()] || '尚无摘要'

const isProcessing = (row: MaterialPageItem) => {
  return row.parseStatus === 'PROCESSING' || row.summaryStatus === 'RUNNING' || row.embeddingStatus === 'RUNNING'
}

const getProcessingText = (row: MaterialPageItem) => {
  if (row.parseStatus === 'PROCESSING') {
    return '解析中…'
  }
  if (row.summaryStatus === 'RUNNING') {
    return '摘要生成中…'
  }
  if (row.embeddingStatus === 'RUNNING') {
    return '向量索引中…'
  }
  return '处理中…'
}

const formatRetrievalScore = (value?: number) => {
  if (typeof value !== 'number') {
    return '--'
  }
  return value.toFixed(4)
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
  Number(row.embeddedSegmentCount || 0) > 0 ||
  ['SUCCESS', 'PARTIAL', 'PARTIAL_FAILED', 'FAILED'].includes(String(row.embeddingStatus || '').toUpperCase())

const getEmbeddingActionLabel = (row: MaterialPageItem) =>
  shouldRegenerateEmbedding(row) ? '重新生成向量' : '生成向量'

const resetForm = () => {
  form.title = ''
  form.materialType = 'TEXT'
  form.difficultyLevel = 3
  form.tags = ''
  form.description = ''
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

const loadMaterials = async (silentOrEvent?: boolean | unknown) => {
  const silent = silentOrEvent === true
  const requestVersion = ++listRequestVersion
  if (!silent) tableLoading.value = true
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
  } catch (error) {
    if (requestVersion === listRequestVersion && !silent)
      loadError.value = getErrorMessage(error, '资料暂时无法加载，请稍后重试。')
  } finally {
    if (requestVersion === listRequestVersion && !silent) tableLoading.value = false
  }
}

// 有资料在解析 / 生成摘要 / 建索引时，每 3 秒静默刷新一次列表，全部完成后自动停止。
const hasProcessingMaterials = computed(() => materials.value.some((item) => isProcessing(item)))
usePolling(hasProcessingMaterials, () => loadMaterials(true), 3000)

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
      description: form.description.trim(),
      contentText: form.contentText.trim()
    })
    ElMessage.success('资料保存成功，系统正在自动生成摘要…')
    resetForm()
    textDialogVisible.value = false
    await loadMaterials()
  } catch (error) {
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
  } catch (error) {
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
    const confirmed = await confirmDanger(
      `将使用当前配置的向量模型，重新生成“${row.title}”的全部向量。已有向量会被更新。`,
      { title: '重新生成向量', confirmText: '重新生成' }
    )
    if (!confirmed) return
  }

  actionLoadingId.value = row.id
  try {
    await submitEmbeddingTaskApi(row.id, { forceRegenerate })
    ElMessage.success('向量索引已开始生成，可在任务中心查看进度')
    await loadMaterials()
    if (drawerVisible.value && detailId.value === row.id) await viewDetail(row.id)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '生成向量索引失败，请稍后重试。'))
  } finally {
    actionLoadingId.value = null
  }
}

const removeMaterial = async (id: number) => {
  if (!(await confirmDanger('删除后该资料及其解析分段将不可恢复，确定继续吗？'))) return

  actionLoadingId.value = id
  try {
    await deleteMaterialApi(id)
    ElMessage.success('资料删除成功')
    await loadMaterials()
  } catch (error) {
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
  } catch (error) {
    if (requestVersion !== detailRequestVersion) return
    detail.value = null
    detailError.value = getErrorMessage(error, '资料详情加载失败，请重试。')
  } finally {
    if (requestVersion === detailRequestVersion) detailLoading.value = false
  }
}

const openEditDialog = (row: MaterialPageItem) => {
  editTargetId.value = row.id
  editForm.title = row.title
  editForm.tags = getTags(row.tags).join('、')
  editForm.difficultyLevel = row.difficultyLevel || 3
  editForm.description = row.description || ''
  editTitleError.value = ''
  editDialogVisible.value = true
}

const saveMaterialInfo = async () => {
  const id = editTargetId.value
  if (!id || savingInfo.value) return
  const title = editForm.title.trim()
  if (!title) {
    editTitleError.value = '资料标题不能为空'
    return
  }
  const payload = {
    title,
    tags: editForm.tags.trim(),
    difficultyLevel: editForm.difficultyLevel,
    description: editForm.description.trim()
  }
  savingInfo.value = true
  try {
    await updateMaterialApi(id, payload)
    ElMessage.success('资料信息已更新')
    editDialogVisible.value = false
    if (detail.value?.id === id) {
      detail.value = { ...detail.value, ...payload, tags: getTags(payload.tags).join(',') }
    }
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '保存资料信息失败，请稍后重试。'))
  } finally {
    savingInfo.value = false
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
  } catch (error) {
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
    ElMessage.success('资料上传成功，系统正在自动解析并生成摘要…')
    uploadDialogVisible.value = false
    options.onSuccess?.({})
    await loadMaterials()
  } catch (error) {
    options.onError?.(error as Parameters<UploadRequestOptions['onError']>[0])
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
    case 'edit':
      openEditDialog(row)
      break
    case 'parse':
      void parseMaterial(row.id)
      break
    case 'embedding':
      void generateEmbedding(row)
      break
    case 'retrieval':
      openRetrievalPreview(row)
      break
    case 'delete':
      void removeMaterial(row.id)
      break
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

watch(
  () => route.query.keyword,
  (value) => {
    if (route.path !== '/materials') return
    const keyword = readQueryText(value)
    if (keyword !== filters.keyword) {
      autoQuery.runAfterMutation(() => {
        filters.keyword = keyword
        page.current = 1
      })
    }
  }
)

watch(
  () => route.query.materialId,
  (value) => {
    if (route.path !== '/materials') return
    const rawId = readQueryText(value)
    if (!rawId) return
    const id = Number(rawId)
    if (!/^[1-9]\d*$/.test(rawId) || !Number.isSafeInteger(id)) {
      ElMessage.warning('资料链接无效，请从资料库重新选择')
      return
    }
    void viewDetail(id)
  },
  { immediate: true }
)

watch(
  () => route.query.action,
  (value) => {
    if (route.path !== '/materials' || value !== 'upload') return
    uploadDialogVisible.value = true
    const query = { ...route.query }
    delete query.action
    void router.replace({ path: route.path, query })
  },
  { immediate: true }
)

onUnmounted(() => {
  listRequestVersion += 1
  detailRequestVersion += 1
})

void loadMaterials()
</script>

<style scoped>
.material-library :deep(.el-button) {
  min-height: 44px;
}
.toolbar--flush :deep(.el-button) {
  margin: 0;
  padding-inline: 22px;
}
.material-library :deep(.el-button > span),
.material-library-detail :deep(.el-button > span) {
  gap: 7px;
}
.material-library__hint {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 10px;
  margin: 0 0 22px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.material-library__hint > .app-icon {
  flex: 0 0 auto;
  color: var(--brand);
}
.material-library__hint > span {
  flex: 1 1 320px;
  min-width: 0;
}
.material-library__hint a,
.material-library-detail__retrieval a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  border-radius: 6px;
  color: var(--brand);
  font-size: 13px;
  text-decoration: none;
}
.material-library__hint a:hover,
.material-library-detail__retrieval a:hover {
  color: var(--brand-hover);
  text-decoration: underline;
  text-underline-offset: 4px;
}
.material-library__filters {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 158px 150px 64px;
  gap: 12px;
  align-items: center;
  padding-bottom: 26px;
  border-bottom: 1px solid var(--line);
}
.material-library__filters :deep(.el-select),
.material-library__filters :deep(.el-input) {
  min-width: 0;
  width: 100%;
}
.material-library__filters :deep(.el-input__wrapper),
.material-library__filters :deep(.el-select__wrapper) {
  min-height: 46px;
  background: transparent;
  box-shadow: none;
  border-radius: 999px;
  padding-inline: 16px;
}
.material-library__filters :deep(.el-input__wrapper) {
  background: var(--bg-secondary);
}
.material-library__filters :deep(.el-select__wrapper:hover) {
  background: var(--bg-secondary);
}
.material-library__filters :deep(.el-input__wrapper.is-focus),
.material-library__filters :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 2px var(--brand);
}
.material-library__search :deep(.el-input__prefix) {
  margin-right: 6px;
  color: var(--muted);
}
.material-library__reset {
  margin: 0;
}
.material-library__section-heading {
  margin: 26px 0 6px;
}
.material-library__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}
.material-library-card {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px;
  min-width: 0;
  padding: 20px 18px;
  border-radius: var(--radius);
  border: 1px solid transparent;
  border-bottom: 1px solid var(--line);
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.material-library-card:hover {
  background: var(--panel-hover);
  border-color: var(--line);
  box-shadow: var(--shadow);
  transform: translateY(-2px);
}
.material-library-card__cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 74px;
  height: 104px;
  padding: 14px 6px 12px 10px;
  border: 0;
  border-left: 5px solid color-mix(in srgb, var(--brand) 35%, var(--panel));
  border-radius: 2px 8px 8px 2px;
  background: var(--brand-light);
  color: var(--brand);
  font: inherit;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: all 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}
.material-library-card__cover:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.material-library-card__cover[data-kind='PDF'] {
  background: var(--accent-soft);
  color: var(--accent);
  border-left-color: color-mix(in srgb, var(--accent) 45%, var(--panel));
}
.material-library-card__cover[data-kind='DOC'],
.material-library-card__cover[data-kind='DOCX'],
.material-library-card__cover[data-kind='WORD'] {
  background: var(--blue-soft);
  color: var(--blue);
  border-left-color: color-mix(in srgb, var(--blue) 40%, var(--panel));
}
.material-library-card__cover span {
  max-width: 100%;
  color: inherit;
  font-size: 10px;
  letter-spacing: 0.02em;
  text-align: center;
  overflow-wrap: anywhere;
}
.material-library-card__cover :deep(.app-icon path[stroke]) {
  stroke: currentColor;
}
.material-library-card__cover :deep(.app-icon path[fill-opacity]),
.material-library-card__cover :deep(.app-icon rect) {
  fill: currentColor;
}
.material-library-card__content,
.material-library-card__aside {
  min-width: 0;
}
.material-library-card__aside {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.material-library-card__menu {
  flex-shrink: 0;
}
.material-library-card h3 {
  min-width: 0;
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.material-library-card h3 button {
  display: block;
  max-width: 100%;
  min-height: 44px;
  padding: 5px 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}
.material-library-card h3 button:hover {
  color: var(--brand);
}
.material-library-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
  margin: 2px 0 10px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65;
  font-variant-numeric: tabular-nums;
}
.material-library-card__meta span + span:not(.status-pill)::before {
  content: '·';
  margin-right: 12px;
}
.material-library-card__desc {
  margin: 0 0 4px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.65;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.material-library-card__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
  margin-top: 10px;
}
.material-library-card__tag {
  max-width: 14em;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.material-library-card__tag::before {
  content: '#';
  margin-right: 2px;
  opacity: 0.65;
}
.material-library-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.material-library-card__actions button {
  min-width: 0;
  min-height: 40px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.12s ease;
}
.material-library-card__actions button:active {
  transform: scale(0.97);
}
.material-library-card__open {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}
.material-library-card__open:hover {
  background: var(--brand);
  color: #fff;
  box-shadow: var(--shadow-md);
}
.material-library-card__study {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  background: transparent;
  padding: 0 12px;
}
.material-library-card__study:hover:not(:disabled) {
  color: var(--brand);
  background: var(--bg-secondary);
}
.material-library-card__study:disabled {
  color: var(--line);
  cursor: not-allowed;
  opacity: 0.6;
}
.material-library-card__study.is-loading {
  color: var(--brand);
  background: var(--brand-light);
}
.material-library__text-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.material-library-card--loading {
  min-height: 150px;
  gap: 14px 24px;
}
.material-library__skeleton {
  border-radius: 6px;
  background: color-mix(in srgb, var(--muted) 10%, var(--panel));
  animation: materialLibraryPulse 1.7s ease-in-out infinite;
}
.material-library__skeleton--icon {
  width: 74px;
  height: 104px;
  grid-row: 1 / 3;
}
.material-library__skeleton--title {
  width: 76%;
  height: 22px;
  align-self: end;
}
.material-library__skeleton--text {
  width: 50%;
  height: 16px;
  grid-column: 2;
  align-self: start;
}
.material-library__skeleton--footer {
  height: 40px;
  width: 220px;
  justify-self: end;
  grid-row: 1 / 3;
  grid-column: 3;
}
.material-library__spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 2px solid var(--line);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: materialLibrarySpin 0.8s linear infinite;
}
.material-library__spinner--inline {
  width: 12px;
  height: 12px;
  margin-right: 4px;
  border-width: 1.5px;
}
@keyframes materialLibraryPulse {
  50% {
    opacity: 0.45;
  }
}
@keyframes materialLibrarySpin {
  to {
    transform: rotate(360deg);
  }
}
.material-library .workspace-pagination {
  padding-top: 26px;
}

.material-library-upload > p {
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.8;
}
.material-library-upload :deep(.el-upload) {
  display: block;
  width: 100%;
}
.material-library-upload :deep(.el-upload-dragger) {
  padding: 36px 18px;
  border: 1px dashed color-mix(in srgb, var(--brand) 35%, var(--line));
  border-radius: 14px;
  background: color-mix(in srgb, var(--brand) 3%, var(--panel));
}
.material-library-upload .app-icon {
  margin: 0 auto 18px;
  color: var(--brand);
}
.material-library-upload strong {
  display: block;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.7;
}
.material-library-upload span:not(.material-library__spinner) {
  display: block;
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
}
.material-library-upload .material-library__spinner {
  margin-top: 18px;
}
.material-library-form__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.material-library-form__full {
  width: 100%;
}
.material-library-form :deep(.el-input__wrapper),
.material-library-form :deep(.el-select__wrapper),
.material-library-form :deep(.el-input-number) {
  min-height: 44px;
}
.material-library-dialog__footer {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}
.material-library-dialog__footer :deep(.el-button) {
  min-height: 44px;
  margin: 0;
}
.material-library-retrieval__result {
  margin-top: 18px;
}
.material-library-detail {
  min-width: 0;
}
.material-library-detail__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 320px;
  color: var(--muted);
  font-size: 14px;
}
.material-library-detail__loading p {
  margin: 0;
}
.material-library-detail__heading {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.material-library-detail__heading > div {
  min-width: 0;
}
.material-library-detail__heading span {
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__heading h2 {
  margin: 8px 0 0;
  color: var(--text);
  font-size: 23px;
  font-weight: 650;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.material-library-detail__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin: 24px 0;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg);
}
.material-library-detail__meta div {
  min-width: 0;
}
.material-library-detail__meta dt {
  margin-bottom: 7px;
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__meta dd {
  margin: 0;
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.material-library-detail__meta .material-library-detail__meta-wide {
  grid-column: 1 / -1;
}
.material-library-detail__meta dd.is-empty {
  color: var(--muted);
}
.material-library-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.material-library-detail :deep(.el-button) {
  min-height: 44px;
  margin: 0;
}
.material-library-detail__retrieval {
  margin-top: 24px;
  padding: 18px 20px;
  border-radius: 12px;
  background: var(--bg);
}
.material-library-detail__retrieval-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0 14px;
}
.material-library-detail__retrieval h3 {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.material-library-detail__retrieval p {
  margin: 6px 0 16px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}
.material-library-detail__task-link {
  padding-inline: 8px;
}
.material-library-detail__segments {
  margin-top: 30px;
}
.material-library-detail__segments > h3 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--text);
}
.material-library-detail__segments > h3 span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
.material-library-detail__segment {
  padding: 20px 0;
  border-top: 1px solid var(--line);
}
.material-library-detail__segment-meta {
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__segment h4 {
  margin: 10px 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.material-library-detail__segment p {
  margin: 0;
  color: var(--text);
  font-size: 14px;
  line-height: 1.95;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.material-library-detail__empty {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-top: 24px;
  padding: 22px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.8;
}
:global(.material-library-menu .el-dropdown-menu__item) {
  min-height: 44px;
  font-size: 13px;
}
:global(.material-library-menu .material-library-menu__danger) {
  color: color-mix(in srgb, var(--red) 70%, var(--text));
}
:global(.material-library-modal .el-dialog__headerbtn),
:global(.material-library-modal .el-drawer__close-btn) {
  min-width: 44px;
  min-height: 44px;
}
.material-library button:focus-visible,
.material-library a:focus-visible,
.material-library-detail a:focus-visible,
.material-library-upload :deep(.el-upload:focus-visible) {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}

@media (max-width: 920px) {
  .material-library__filters {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 64px;
  }
  .material-library__search {
    grid-column: 1 / -1;
  }
  .material-library-card {
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 14px 20px;
  }
  .material-library-card__cover {
    width: 64px;
    height: 90px;
  }
  .material-library-card__aside {
    grid-column: 2;
  }
  .material-library-card--loading {
    grid-template-columns: 64px minmax(0, 1fr);
  }
  .material-library__skeleton--icon {
    width: 64px;
    height: 90px;
  }
  .material-library__skeleton--footer {
    display: none;
  }
}
@media (max-width: 640px) {
  .material-library .toolbar--flush {
    width: 100%;
  }
  .material-library .toolbar--flush :deep(.el-button) {
    flex: 1;
    padding-inline: 14px;
  }
  .material-library__hint {
    margin-bottom: 18px;
  }
  .material-library__hint a {
    margin-left: 26px;
  }
  .material-library__filters {
    gap: 10px 4px;
    padding-bottom: 18px;
  }
  .material-library__filters :deep(.el-select__wrapper) {
    padding-inline: 10px;
  }
  .material-library__section-heading {
    margin-top: 18px;
  }
  .material-library-card {
    padding-block: 24px;
    gap: 16px;
  }
  .material-library-card h3 {
    font-size: 17px;
  }
  .material-library-card__meta {
    font-size: 12px;
    gap: 4px 8px;
  }
  .material-library-card__meta span + span:not(.status-pill)::before {
    margin-right: 8px;
  }
  .material-library-card__aside {
    grid-column: 1 / -1;
  }
  .material-library-form__grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .material-library-form :deep(.el-input__inner),
  .material-library-form :deep(.el-textarea__inner) {
    font-size: 16px;
  }
  .material-library-detail__meta {
    grid-template-columns: 1fr;
    gap: 18px;
  }
  .material-library-detail__heading h2 {
    font-size: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .material-library-card,
  .material-library-card__cover,
  .material-library-card__actions button {
    transition: none;
  }
  .material-library-card:hover,
  .material-library-card__cover:hover,
  .material-library-card__actions button:active {
    transform: none;
  }
  .material-library__skeleton,
  .material-library__spinner {
    animation: none;
  }
}
</style>
