<template>
  <section class="learning-page material-library" aria-labelledby="material-library-title">
    <header class="page-header">
      <div>
        <h1 id="material-library-title" class="page-title">资料库</h1>
        <p class="page-desc">上传讲义、教材或笔记，解析完成后可以阅读原文、生成总结和练习题。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button @click="textDialogVisible = true"><AppIcon name="summary" :size="16" />粘贴文本</el-button>
        <el-button type="primary" @click="uploadDialogVisible = true"
          ><AppIcon name="upload" :size="16" />上传资料</el-button
        >
      </div>
    </header>

    <div class="workspace-panel material-library__panel">
      <div class="workspace-toolbar workspace-toolbar--filter">
        <div class="filter-bar" role="search" aria-label="筛选资料">
          <el-input
            v-model="searchKeyword"
            clearable
            aria-label="按标题、标签或说明搜索"
            placeholder="搜索标题、标签或说明"
            @keyup.enter="autoQuery.runNow"
          >
            <template #prefix><AppIcon name="search" :size="16" /></template>
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
          <el-button v-if="hasActiveFilters" text @click="resetFilters">清除筛选</el-button>
        </div>
        <el-button text :loading="tableLoading" aria-label="刷新资料列表" @click="loadMaterials">
          <AppIcon v-if="!tableLoading" name="refresh" :size="16" /><span class="workspace-toolbar__refresh-label"
            >刷新</span
          >
        </el-button>
      </div>

      <div v-if="tableLoading" class="record-list" role="status" aria-label="正在加载资料">
        <div v-for="i in 4" :key="i" class="list-row record-row record-row--loading" aria-hidden="true">
          <span class="skeleton record-row__sk-lead" />
          <div class="record-row__main">
            <span class="skeleton record-row__sk-title" />
            <span class="skeleton record-row__sk-meta" />
          </div>
          <span class="skeleton record-row__sk-actions" />
        </div>
      </div>
      <EmptyState v-else-if="loadError" variant="error" title="资料列表没有加载出来" :description="loadError">
        <el-button type="primary" @click="loadMaterials">重新加载</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!materialRows.length && hasActiveFilters"
        icon="search"
        title="没有找到匹配的资料"
        description="换个关键词，或者清除筛选条件再看看。"
      >
        <el-button @click="resetFilters">清除筛选</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!materialRows.length"
        icon="book-open"
        title="还没有资料"
        description="上传讲义或教材，也可以直接粘贴一段文字。解析完成后就能生成总结和练习题。"
      >
        <el-button type="primary" @click="uploadDialogVisible = true"
          ><AppIcon name="upload" :size="16" />上传资料</el-button
        >
        <el-button @click="textDialogVisible = true">粘贴文本</el-button>
      </EmptyState>
      <ul v-else class="record-list">
        <li v-for="row in materialRows" :key="row.id" class="list-row record-row">
          <MaterialTypeBadge :type="row.materialType" :size="40" />
          <div class="record-row__main">
            <h2 class="record-row__title">
              <button type="button" :title="row.title" @click="viewDetail(row.id)">{{ row.title }}</button>
              <StatusPill v-if="isProcessing(row)" tone="processing" :label="getProcessingText(row)" />
              <StatusPill v-else-if="row.parseStatus === 'FAILED'" tone="failed" label="解析失败" />
              <StatusPill v-else-if="row.parseStatus === 'PENDING'" tone="muted" label="待解析" />
            </h2>
            <p v-if="row.description" class="record-row__desc" :title="row.description">{{ row.description }}</p>
            <p v-if="row.parseStatus === 'FAILED'" class="material-row__error">
              {{ row.parseErrorMessage || '没有记录失败原因，重新解析一次可以看到详情。' }}
            </p>
            <p class="record-row__meta">
              <span class="visually-hidden">{{ formatMaterialType(row.materialType) }}</span>
              <span v-if="row.totalCharacters">{{ formatCharacters(row.totalCharacters) }}</span>
              <time :datetime="row.createdAt" :title="formatDateTime(row.createdAt)"
                >{{ formatRelativeDay(row.createdAt) }}添加</time
              >
              <span v-if="row.summaryStatus === 'SUCCESS'" class="material-row__summary"
                >有总结<GenerationModeBadge
                  v-if="row.summaryGenerationMode === 'MOCK'"
                  :mode="row.summaryGenerationMode"
              /></span>
              <span v-for="tag in row.tagList.slice(0, 3)" :key="tag" class="record-row__chip" :title="tag">{{
                tag
              }}</span>
              <span v-if="row.tagList.length > 3" class="record-row__chip" :title="row.tagList.slice(3).join('、')"
                >+{{ row.tagList.length - 3 }}</span
              >
            </p>
          </div>
          <div class="record-row__actions" role="group" :aria-label="`${row.title}的操作`">
            <el-button size="small" @click="viewDetail(row.id)">打开</el-button>
            <el-button
              size="small"
              text
              :disabled="row.parseStatus !== 'SUCCESS' || row.summaryStatus === 'RUNNING'"
              @click="goSummary(row.id)"
              >总结</el-button
            >
            <el-button size="small" text :disabled="row.parseStatus !== 'SUCCESS'" @click="goQuiz(row.id)"
              >出题</el-button
            >
            <el-dropdown
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
                    row.parseStatus === 'SUCCESS' ? '重新解析' : '解析资料'
                  }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided class="menu-item--danger">删除资料</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </li>
      </ul>

      <footer v-if="!tableLoading && !loadError && total > 0" class="workspace-pagination">
        <span class="workspace-pagination__meta">{{
          hasActiveFilters ? `找到 ${total} 份资料` : `共 ${total} 份资料`
        }}</span>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]"
          :pager-count="5"
          layout="sizes, prev, pager, next"
          :total="total"
          @current-change="loadMaterials"
          @size-change="loadMaterials"
        />
      </footer>
    </div>

    <el-dialog
      v-model="uploadDialogVisible"
      class="material-library-modal"
      title="上传资料"
      width="min(560px, calc(100vw - 32px))"
      :close-on-click-modal="!uploading"
      :close-on-press-escape="!uploading"
      :show-close="!uploading"
      destroy-on-close
    >
      <div class="material-library-upload">
        <el-upload
          drag
          :show-file-list="false"
          :http-request="uploadMaterial"
          :before-upload="beforeUpload"
          :disabled="uploading"
          accept=".pdf,.docx,.txt"
        >
          <span class="material-library-upload__icon"><AppIcon name="upload" :size="24" /></span>
          <strong>{{ uploading ? '正在上传…' : '把文件拖到这里，或点击选择' }}</strong>
          <span>支持 PDF、Word（.docx）和 TXT</span>
          <span v-if="uploading" class="material-library__spinner" role="status" aria-label="上传中"></span>
        </el-upload>
        <p class="material-library-upload__note">扫描版 PDF 里没有可以复制的文字，需要先做 OCR 识别再上传。</p>
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
      title="粘贴文本"
      width="min(720px, calc(100vw - 32px))"
      top="6vh"
      destroy-on-close
    >
      <el-form label-position="top" class="material-library-form">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="例如：数据库系统原理 第三章事务管理" />
        </el-form-item>
        <div class="material-library-form__grid">
          <el-form-item label="格式">
            <el-select v-model="form.materialType" class="material-library-form__full">
              <el-option label="纯文本" value="TEXT" />
              <el-option label="Markdown" value="MARKDOWN" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="form.tags" placeholder="用逗号或顿号分隔，例如：数据库、期末复习" />
          </el-form-item>
        </div>
        <el-form-item label="难度">
          <el-slider
            v-model="form.difficultyLevel"
            :min="1"
            :max="5"
            show-stops
            :marks="DIFFICULTY_LABELS"
            :format-tooltip="formatDifficulty"
            :format-value-text="formatDifficulty"
            aria-label="资料难度"
          />
        </el-form-item>
        <el-form-item label="说明（选填）">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="例如来源、版本或用途"
          />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="form.contentText"
            type="textarea"
            :rows="8"
            maxlength="10000"
            show-word-limit
            placeholder="粘贴课堂笔记、知识点整理或教材摘录"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="material-library-dialog__footer">
          <el-button @click="textDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="createMaterial">保存</el-button>
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
        <el-form-item label="标题" required :error="editTitleError">
          <el-input
            v-model="editForm.title"
            maxlength="200"
            show-word-limit
            placeholder="输入标题"
            @input="editTitleError = ''"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" maxlength="255" placeholder="用逗号或顿号分隔，例如：期末复习、第三章" />
        </el-form-item>
        <el-form-item label="难度">
          <el-slider
            v-model="editForm.difficultyLevel"
            :min="1"
            :max="5"
            show-stops
            :marks="DIFFICULTY_LABELS"
            :format-tooltip="formatDifficulty"
            :format-value-text="formatDifficulty"
            aria-label="资料难度"
          />
        </el-form-item>
        <el-form-item label="说明（选填）">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如来源、版本或用途"
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
          title="这份资料打不开"
          :description="detailError"
        >
          <el-button @click="viewDetail(detailId)">重试</el-button>
        </EmptyState>
        <template v-else-if="detail">
          <div class="material-library-detail__heading">
            <MaterialTypeBadge :type="detail.materialType" :size="44" />
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
              <dt>解析状态</dt>
              <dd>{{ formatParseStatus(detail.parseStatus) }}</dd>
            </div>
            <div>
              <dt>分块方式</dt>
              <dd>{{ formatChunking(detail) }}</dd>
            </div>
            <div>
              <dt>学习总结</dt>
              <dd>
                {{ formatSummaryStatus(detail.summaryStatus) }}
                <GenerationModeBadge v-if="detail.summaryStatus === 'SUCCESS'" :mode="detail.summaryGenerationMode" />
              </dd>
            </div>
            <div>
              <dt>向量索引</dt>
              <dd>{{ formatEmbeddingStatus(detail.embeddingStatus) }} · {{ formatEmbeddingProgress(detail) }}</dd>
            </div>
            <div>
              <dt>标签</dt>
              <dd>{{ getTags(detail.tags).join('、') || '没有标签' }}</dd>
            </div>
            <div>
              <dt>难度</dt>
              <dd>{{ formatDifficulty(detail.difficultyLevel) }}</dd>
            </div>
            <div class="material-library-detail__meta-wide">
              <dt>说明</dt>
              <dd :class="{ 'is-empty': !detail.description }">
                {{ detail.description || '还没有说明，可以在“编辑信息”里补充来源或版本。' }}
              </dd>
            </div>
          </dl>
          <el-alert
            v-if="detail.parseStatus === 'FAILED'"
            :title="detail.parseErrorMessage || '没有记录失败原因，重新解析一次可以看到详情。'"
            type="error"
            :closable="false"
            show-icon
          />
          <div class="material-library-detail__actions">
            <el-button
              v-if="detail.parseStatus !== 'SUCCESS'"
              type="primary"
              :loading="actionLoadingId === detail.id"
              @click="parseMaterial(detail.id)"
              >解析资料</el-button
            >
            <el-button v-else type="primary" @click="goSummary(detail.id)"
              ><AppIcon name="summary" :size="16" />查看总结</el-button
            >
            <el-button @click="goQuiz(detail.id)"><AppIcon name="quiz" :size="16" />出题</el-button>
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
                  ? '生成向量后，可以按问题的意思找到资料里的相关片段。'
                  : '先解析资料，再生成向量。'
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
                >查看进度</RouterLink
              >
            </div>
          </section>
          <div v-if="detail.segments?.length" class="material-library-detail__segments">
            <h3>
              原文 <span>共 {{ detail.segments.length }} 个片段</span>
            </h3>
            <article v-for="segment in detail.segments" :key="segment.id" class="material-library-detail__segment">
              <div class="material-library-detail__segment-meta">{{ formatSegmentMeta(segment) }}</div>
              <h4>{{ segment.sectionTitle || '资料片段' }}</h4>
              <p>{{ segment.contentText }}</p>
            </article>
          </div>
          <div v-else class="material-library-detail__empty">解析后可以在这里阅读原文。</div>
        </template>
      </div>
    </el-drawer>

    <el-dialog
      v-model="parseDialogVisible"
      :title="parseTarget?.parseStatus === 'SUCCESS' ? '重新解析' : '解析资料'"
      width="min(540px, calc(100vw - 32px))"
      :close-on-click-modal="!parsing"
      :close-on-press-escape="!parsing"
      :show-close="!parsing"
    >
      <p class="material-parse__target">{{ parseTarget?.title || '当前资料' }}</p>
      <p class="material-parse__desc">按下面的参数把原文切成片段。调整参数后，可以在检索评测里比较效果。</p>
      <el-form label-position="top">
        <el-form-item label="每段最多字符数（200–4000）">
          <el-input-number v-model="parseForm.chunkSize" :min="200" :max="4000" :step="100" :disabled="parsing" />
        </el-form-item>
        <el-form-item label="相邻两段重叠的字符数（最多 500，且小于每段字符数）">
          <el-input-number
            v-model="parseForm.chunkOverlap"
            :min="0"
            :max="Math.min(500, parseForm.chunkSize - 1)"
            :step="20"
            :disabled="parsing"
          />
        </el-form-item>
      </el-form>
      <p v-if="parseTarget?.parseStatus === 'SUCCESS'" class="material-parse__warning">
        重新解析会替换原有片段并清除向量，之后需要重新生成向量。检索评测里按片段编号标注的样本需要重新核对，已有的评测记录不受影响。
      </p>
      <template #footer>
        <el-button :disabled="parsing" @click="parseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="parsing" @click="confirmParseMaterial">开始解析</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="retrievalDialogVisible"
      class="material-library-modal"
      title="检索预览"
      width="min(860px, calc(100vw - 32px))"
      destroy-on-close
    >
      <div class="material-library-form">
        <div class="material-library-form__grid">
          <el-form-item label="资料">
            <el-input :model-value="retrievalForm.materialTitle" disabled />
          </el-form-item>
          <el-form-item label="返回几个片段">
            <el-input-number v-model="retrievalForm.limit" :min="1" :max="10" class="material-library-form__full" />
          </el-form-item>
        </div>
        <el-form-item label="问题">
          <el-input
            v-model="retrievalForm.queryText"
            type="textarea"
            :rows="3"
            placeholder="例如：事务的 ACID 特性是什么？"
          />
        </el-form-item>

        <div class="material-library-dialog__footer">
          <el-button :loading="retrievalLoading" type="primary" @click="runRetrievalPreview">开始检索</el-button>
        </div>

        <div v-if="retrievalLoading" class="state-block" role="status">正在查找相关片段…</div>
        <div v-else-if="retrievalError" class="material-library-detail__empty" role="alert">
          {{ retrievalError }}<el-button @click="runRetrievalPreview">重新检索</el-button>
        </div>
        <div v-else-if="retrievalResult" class="list-stack material-library-retrieval__result">
          <el-alert
            v-if="retrievalResult.degraded"
            type="warning"
            :closable="false"
            show-icon
            title="向量检索暂时不可用，改用了文本检索"
            :description="retrievalResult.degradeReason || '可以稍后再试，或检查向量模型配置。'"
          />
          <el-alert
            v-else-if="retrievalResult.mockEmbedding"
            type="info"
            :closable="false"
            show-icon
            title="当前是演示向量"
            description="演示向量由文字哈希生成，不代表语义相似度，排序主要取决于字面匹配。"
          />
          <div class="soft-text">
            {{ formatRetrievalMode(retrievalResult) }} · 找到 {{ retrievalResult.hitCount }} 个片段
          </div>
          <div v-for="segment in retrievalResult.segments" :key="segment.segmentId" class="section-card">
            <div class="summary-history-card__top">
              <div>
                <div class="section-card__title">
                  {{ segment.sectionTitle || `第 ${segment.segmentNo || '--'} 段` }}
                </div>
                <div class="section-card__meta">
                  {{ formatSegmentMeta(segment) }} · {{ formatSegmentScores(segment) }}
                </div>
              </div>
            </div>
            <div class="summary-block">{{ segment.contentText || '暂无内容' }}</div>
          </div>
          <div v-if="!retrievalResult.segments.length" class="state-block empty">
            没有找到相关片段，换个更具体的问法试试。
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
import MaterialTypeBadge from '@/components/MaterialTypeBadge.vue'
import StatusPill from '@/components/StatusPill.vue'
import GenerationModeBadge from '@/components/GenerationModeBadge.vue'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import { usePolling } from '@/composables/usePolling'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatCharacters, formatDateTime, formatRelativeDay, formatSegmentMeta } from '@/utils/format'
import { DIFFICULTY_LABELS, formatDifficulty, formatMaterialType, formatParseStatus } from '@/utils/labels'
import {
  previewMaterialRetrievalApi,
  submitEmbeddingTaskApi,
  type RetrievalPreviewPayload,
  type RetrievalPreviewSegment
} from '@/api/modules/ai'
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
const detail = ref<MaterialDetail | null>(null)
const detailId = ref(0)
const drawerVisible = ref(false)
const uploadDialogVisible = ref(false)
const textDialogVisible = ref(false)
const editDialogVisible = ref(false)
const retrievalDialogVisible = ref(false)
const parseDialogVisible = ref(false)
const parsing = ref(false)
const parseTarget = ref<MaterialPageItem | null>(null)
const parseForm = reactive({ chunkSize: 900, chunkOverlap: 140 })
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

interface MaterialDetail extends MaterialPageItem {
  segments?: { id: number; segmentNo?: number; pageNo?: number; sectionTitle?: string; contentText?: string }[]
}

const materialTypes = [
  { value: 'PDF', label: 'PDF' },
  { value: 'DOCX', label: 'Word' },
  { value: 'TXT', label: 'TXT 文件' },
  { value: 'TEXT', label: '纯文本' },
  { value: 'MARKDOWN', label: 'Markdown' },
  { value: 'FILE', label: '其他格式' }
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
    SUCCESS: '已生成',
    PENDING: '还没有',
    PROCESSING: '生成中',
    RUNNING: '生成中',
    FAILED: '生成失败'
  })[String(value || '').toUpperCase()] || '还没有'

const isProcessing = (row: MaterialPageItem) => {
  return row.parseStatus === 'PROCESSING' || row.summaryStatus === 'RUNNING' || row.embeddingStatus === 'RUNNING'
}

const getProcessingText = (row: MaterialPageItem) => {
  if (row.parseStatus === 'PROCESSING') {
    return '解析中…'
  }
  if (row.summaryStatus === 'RUNNING') {
    return '总结生成中…'
  }
  if (row.embeddingStatus === 'RUNNING') {
    return '向量生成中…'
  }
  return '处理中…'
}

const formatScore = (value?: number | null) => (typeof value === 'number' ? value.toFixed(3) : '--')

const formatRetrievalMode = (result: RetrievalPreviewPayload) =>
  result.degraded || result.retrievalMode === 'TEXT' ? '文本检索' : '向量检索 + 词面重排'

/** 文本检索没有向量分，只展示词面匹配度；向量检索同时展示综合、向量和词面三项得分。 */
const formatSegmentScores = (segment: RetrievalPreviewSegment) =>
  segment.semanticScore === null || segment.semanticScore === undefined
    ? `文本匹配度 ${formatScore(segment.lexicalScore)}`
    : `综合 ${formatScore(segment.score)} · 向量 ${formatScore(segment.semanticScore)} · 词面 ${formatScore(segment.lexicalScore)}`

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
    return `${embedded} / ${total} 个片段`
  }
  const parseStatus = String(row.parseStatus || '').toUpperCase()
  if (parseStatus === 'PROCESSING') {
    return '解析完成后可以生成'
  }
  if (parseStatus === 'FAILED') {
    return '解析失败，无法生成'
  }
  if (parseStatus === 'SUCCESS') return '没有片段'
  return '先解析资料'
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
    if (requestVersion === listRequestVersion && !silent) loadError.value = getErrorMessage(error, '请稍后再试。')
  } finally {
    if (requestVersion === listRequestVersion && !silent) tableLoading.value = false
  }
}

// 有资料在解析、生成总结或生成向量时，每 3 秒静默刷新一次列表，全部完成后自动停止。
const hasProcessingMaterials = computed(() => materials.value.some((item) => isProcessing(item)))
usePolling(hasProcessingMaterials, () => loadMaterials(true), 3000)

const createMaterial = async () => {
  if (!form.title.trim() || !form.contentText.trim()) {
    ElMessage.warning('请填写标题和内容')
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
    ElMessage.success('资料已保存')
    resetForm()
    textDialogVisible.value = false
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '资料没有保存成功，请稍后重试。'))
  } finally {
    creating.value = false
  }
}

const formatChunking = (material: MaterialPageItem) =>
  material.chunkStrategy === 'CHAR_WINDOW_V2'
    ? `每段 ${material.chunkSize} 字符，重叠 ${material.chunkOverlap} 字符`
    : material.chunkStrategy === 'ORIGINAL_CONTEXT'
      ? '按原文段落'
      : '未记录'

const parseMaterial = (id: number) => {
  if (parsing.value) return
  parseTarget.value = materials.value.find((item) => item.id === id) || (detail.value?.id === id ? detail.value : null)
  if (!parseTarget.value) return
  parseForm.chunkSize = parseTarget.value.chunkSize || 900
  parseForm.chunkOverlap = parseTarget.value.chunkOverlap ?? 140
  parseDialogVisible.value = true
}

const confirmParseMaterial = async () => {
  if (!parseTarget.value || parsing.value) return
  if (
    !Number.isInteger(parseForm.chunkSize) ||
    !Number.isInteger(parseForm.chunkOverlap) ||
    parseForm.chunkSize < 200 ||
    parseForm.chunkSize > 4000 ||
    parseForm.chunkOverlap < 0 ||
    parseForm.chunkOverlap > 500 ||
    parseForm.chunkOverlap >= parseForm.chunkSize
  ) {
    ElMessage.warning('请检查分段参数：重叠字符数要小于每段字符数')
    return
  }
  const id = parseTarget.value.id
  parsing.value = true
  actionLoadingId.value = id
  try {
    await parseMaterialApi(id, { ...parseForm })
    ElMessage.success('解析完成')
    parseDialogVisible.value = false
    await loadMaterials()
    if (drawerVisible.value && detailId.value === id) await viewDetail(id)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '解析没有完成，请稍后重试。'))
    await loadMaterials()
    if (drawerVisible.value && detailId.value === id) await viewDetail(id)
  } finally {
    parsing.value = false
    actionLoadingId.value = null
  }
}

const generateEmbedding = async (row: MaterialPageItem) => {
  if (actionLoadingId.value === row.id) return
  if (row.parseStatus !== 'SUCCESS') {
    ElMessage.warning('请先解析资料，再生成向量')
    return
  }
  if (row.embeddingStatus === 'RUNNING') {
    ElMessage.info('向量正在生成，可以在任务中心查看进度')
    return
  }

  const forceRegenerate = shouldRegenerateEmbedding(row)
  if (forceRegenerate) {
    const confirmed = await confirmDanger(
      `会用当前配置的向量模型重新生成“${row.title}”的全部向量，原有向量会被替换。`,
      { title: '重新生成向量', confirmText: '重新生成' }
    )
    if (!confirmed) return
  }

  actionLoadingId.value = row.id
  try {
    await submitEmbeddingTaskApi(row.id, { forceRegenerate })
    ElMessage.success('已开始生成向量，可以在任务中心查看进度')
    await loadMaterials()
    if (drawerVisible.value && detailId.value === row.id) await viewDetail(row.id)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '向量没有开始生成，请稍后重试。'))
  } finally {
    actionLoadingId.value = null
  }
}

const removeMaterial = async (id: number) => {
  if (!(await confirmDanger('删除后，资料和解析出的片段都无法恢复。确定删除吗？'))) return

  actionLoadingId.value = id
  try {
    await deleteMaterialApi(id)
    ElMessage.success('资料已删除')
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '没有删除成功，请稍后重试。'))
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
    if (!detail.value) detailError.value = '这份资料不存在或已经删除了。'
  } catch (error) {
    if (requestVersion !== detailRequestVersion) return
    detail.value = null
    detailError.value = getErrorMessage(error, '资料详情没有加载出来，请重试。')
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
    editTitleError.value = '标题不能为空'
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
    ElMessage.error(getErrorMessage(error, '没有保存成功，请稍后重试。'))
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
    ElMessage.warning('请输入问题')
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
      ElMessage.info('没有找到相关片段，换个更具体的问法试试')
    }
  } catch (error) {
    retrievalResult.value = null
    retrievalError.value = getErrorMessage(error, '检索没有完成，请稍后重试。')
  } finally {
    retrievalLoading.value = false
  }
}

const uploadMaterial = async (options: UploadRequestOptions) => {
  uploading.value = true
  try {
    const response = await uploadMaterialApi(options.file as File)
    uploadDialogVisible.value = false
    options.onSuccess?.({})
    await loadMaterials()
    const uploadedId = Number(response.data.data)
    const uploaded = materialRows.value.find((row) => row.id === uploadedId)
    if (uploaded?.parseStatus === 'FAILED') {
      ElMessage.warning(uploaded.parseErrorMessage || '文件已上传，但解析没有成功，打开详情可以看到原因。')
    } else {
      ElMessage.success('上传完成')
    }
  } catch (error) {
    options.onError?.(error as Parameters<UploadRequestOptions['onError']>[0])
    ElMessage.error(getErrorMessage(error, '上传失败，请稍后重试。'))
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
      ElMessage.warning('这个资料链接无效，请从资料库重新打开')
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
.material-row__error {
  margin: 4px 0 0;
  color: var(--red);
  font-size: 13px;
  overflow-wrap: anywhere;
}
.material-row__summary :deep(.generation-mode-badge) {
  margin-left: 6px;
  padding: 0 7px;
  font-size: 11px;
}
.material-library__spinner {
  display: inline-block;
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border: 2px solid var(--line);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: zx-spin 0.8s linear infinite;
}
.material-library-upload :deep(.el-upload),
.material-library-upload :deep(.el-upload-dragger) {
  display: block;
  width: 100%;
}
.material-library-upload :deep(.el-upload-dragger) {
  padding: 36px 20px 32px;
  border: 1px dashed color-mix(in srgb, var(--brand) 35%, var(--line-strong));
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--brand) 3%, var(--panel));
  transition:
    border-color var(--duration-fast) ease,
    background-color var(--duration-fast) ease;
}
.material-library-upload :deep(.el-upload-dragger:hover),
.material-library-upload :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--brand);
  background: var(--brand-light);
}
.material-library-upload__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin: 0 auto 14px;
  border-radius: 14px;
  background: var(--brand-soft);
  color: var(--brand);
}
.material-library-upload strong {
  display: block;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.material-library-upload strong + span {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  font-size: 13px;
}
.material-library-upload .material-library__spinner {
  margin-top: 16px;
}
.material-library-upload__note {
  margin: 12px 2px 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.material-library-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.material-library-form__full {
  width: 100%;
}
.material-library-dialog__footer {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}
.material-library-dialog__footer .el-button {
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
  gap: 14px;
  min-height: 320px;
  color: var(--muted);
  font-size: 14px;
}
.material-library-detail__loading p {
  margin: 0;
}
.material-library-detail__heading {
  display: flex;
  align-items: center;
  gap: 14px;
}
.material-library-detail__heading > div {
  min-width: 0;
}
.material-library-detail__heading span {
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__heading h2 {
  margin: 2px 0 0;
  color: var(--text);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.material-library-detail__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 20px;
  margin: 20px 0;
  padding: 16px 18px;
  border-radius: var(--radius);
  background: var(--bg);
}
.material-library-detail__meta div {
  min-width: 0;
}
.material-library-detail__meta dt {
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__meta dd {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 2px 0 0;
  color: var(--text);
  font-size: 14px;
  overflow-wrap: anywhere;
}
.material-library-detail__meta .material-library-detail__meta-wide {
  grid-column: 1 / -1;
}
.material-library-detail__meta dd.is-empty {
  color: var(--muted);
}
.material-library-detail :deep(.el-alert) {
  margin-bottom: 16px;
}
.material-library-detail__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.material-library-detail__actions .el-button {
  margin: 0;
}
.material-library-detail__retrieval {
  margin-top: 20px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.material-library-detail__retrieval-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 14px;
}
.material-library-detail__retrieval h3 {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.material-library-detail__retrieval p {
  margin: 4px 0 14px;
  color: var(--muted);
  font-size: 13px;
}
.material-library-detail__retrieval a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--brand);
  font-size: 13px;
  text-decoration: none;
}
.material-library-detail__retrieval a:hover {
  color: var(--brand-hover);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.material-library-detail__segments {
  margin-top: 28px;
}
.material-library-detail__segments > h3 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 4px;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
}
.material-library-detail__segments > h3 span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
.material-library-detail__segment {
  padding: 16px 0;
  border-top: 1px solid var(--line);
}
.material-library-detail__segments > h3 + .material-library-detail__segment {
  border-top: 0;
}
.material-library-detail__segment-meta {
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__segment h4 {
  margin: 4px 0 8px;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.material-library-detail__segment p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.9;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.material-library-detail__empty {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
  padding: 20px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  color: var(--muted);
  font-size: 14px;
}
.material-parse__target {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.material-parse__desc {
  margin: 4px 0 18px;
  color: var(--muted);
  font-size: 13px;
}
.material-parse__warning {
  margin: 4px 0 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--text-secondary);
  font-size: 13px;
}
@media (max-width: 760px) {
  .material-library-detail__meta {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 640px) {
  .material-library-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
