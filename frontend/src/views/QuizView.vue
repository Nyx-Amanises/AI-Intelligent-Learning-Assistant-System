<template>
  <section class="learning-page quiz-view">
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">专注练习</div>
        <h1 class="page-title">练习题集</h1>
        <p class="page-desc">选一套题，把理解再加深一点。</p>
      </div>
      <el-button type="primary" @click="generateDialogVisible = true">
        <AppIcon name="plus" :size="17" />
        生成题集
      </el-button>
    </div>

    <div class="workspace-panel">
      <ListSectionHeading title="全部题集" :count="total" :loading="questionSetLoading" @refresh="loadQuestionSets()" />
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar quiz-filter-bar">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索题集名称"
            aria-label="搜索题集"
            class="workspace-filter-bar__search"
          >
            <template #prefix><AppIcon name="search" :size="18" /></template>
          </el-input>
          <el-select v-model="filters.status" clearable placeholder="全部状态" aria-label="筛选题集状态">
            <el-option
              v-for="status in FILTERABLE_STATUSES"
              :key="status"
              :label="formatQuestionSetStatus(status)"
              :value="status"
            />
          </el-select>
          <el-select v-model="filters.difficultyLevel" clearable placeholder="全部难度" aria-label="筛选难度">
            <el-option v-for="level in 5" :key="level" :label="formatDifficulty(level)" :value="level" />
          </el-select>
          <el-button text @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <el-alert
          v-if="taskStatusText"
          :title="taskStatusText"
          type="info"
          :closable="false"
          show-icon
          class="quiz-notice"
        />
        <div v-if="questionSetLoading" class="quiz-loading" aria-live="polite">
          <el-skeleton :rows="5" animated /><span>正在加载题集…</span>
        </div>
        <EmptyState v-else-if="loadError" variant="error" icon="quiz" title="题集暂时没能加载" :description="loadError">
          <el-button type="primary" @click="loadQuestionSets()">重新加载</el-button>
        </EmptyState>
        <EmptyState
          v-else-if="!questionSets.length"
          icon="quiz"
          :title="hasFilters ? '没有找到匹配的题集' : '用一套练习，巩固刚学过的知识'"
          :description="
            hasFilters
              ? '换个关键词或难度，看看其他题集。'
              : '选择资料，自由组合单选、多选、判断和简答题，找到适合自己的练习节奏。'
          "
        >
          <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
          <el-button v-else-if="materials.length" type="primary" @click="generateDialogVisible = true"
            >生成第一套题</el-button
          >
          <el-button v-else type="primary" @click="router.push('/materials')">先添加学习资料</el-button>
        </EmptyState>
        <div v-else class="quiz-card-grid">
          <article v-for="(item, index) in questionSets" :key="item.id" class="quiz-set-card">
            <span class="quiz-set-card__number" aria-hidden="true">{{
              formatIndex((page.current - 1) * page.size + index + 1)
            }}</span>
            <div class="quiz-set-card__content">
              <div class="quiz-set-card__top">
                <span class="quiz-set-card__category">学习自测</span>
                <StatusPill :tone="statusTone(item.status)" :label="formatQuestionSetStatus(item.status)" />
              </div>
              <button
                type="button"
                class="quiz-set-card__title"
                :disabled="!canPreviewQuestionSet(item.status)"
                @click="viewQuestionSet(item)"
              >
                {{ item.title }}
              </button>
              <div class="quiz-set-card__meta">
                <span>{{ canPreviewQuestionSet(item.status) ? '' : '计划 ' }}{{ item.questionCount }} 道题目</span>
                <span v-if="canPreviewQuestionSet(item.status)">满分 {{ item.totalScore }} 分</span>
                <span>{{ formatDifficulty(item.difficultyLevel) }}</span>
                <span class="quiz-set-card__date">{{ formatDate(item.createdAt) }}</span>
              </div>
              <p v-if="CARD_HINTS[item.status || '']" class="quiz-set-card__hint">
                {{ CARD_HINTS[item.status || ''] }}
              </p>
            </div>
            <div class="quiz-set-card__actions">
              <div class="quiz-set-card__secondary">
                <el-button
                  v-if="['FAILED', 'CANCELLED'].includes(item.status || '')"
                  link
                  @click="router.push('/ai-tasks')"
                  >查看任务</el-button
                >
                <el-button v-else link :disabled="!canPreviewQuestionSet(item.status)" @click="viewQuestionSet(item)"
                  >预览题目</el-button
                >
                <el-dropdown
                  trigger="click"
                  :disabled="item.status === 'GENERATING'"
                  @command="removeQuestionSet(item.id)"
                >
                  <IconMenuButton
                    label="题集更多操作"
                    :loading="actionLoadingId === item.id"
                    :disabled="item.status === 'GENERATING'"
                  />
                  <template #dropdown
                    ><el-dropdown-menu
                      ><el-dropdown-item command="delete">删除题集</el-dropdown-item></el-dropdown-menu
                    ></template
                  >
                </el-dropdown>
              </div>
              <el-button
                type="primary"
                plain
                :loading="item.status === 'GENERATING' || startingQuestionSetId === item.id"
                :disabled="
                  !canPracticeQuestionSet(item.status) ||
                  (startingQuestionSetId !== null && startingQuestionSetId !== item.id)
                "
                @click="startPractice(item.id)"
              >
                {{
                  item.status === 'GENERATING'
                    ? '生成中'
                    : canPracticeQuestionSet(item.status)
                      ? '开始练习'
                      : '暂不可练习'
                }}
                <AppIcon v-if="canPracticeQuestionSet(item.status)" name="arrow-right" :size="16" />
              </el-button>
            </div>
          </article>
        </div>

        <div v-if="total" class="workspace-pagination">
          <div class="workspace-pagination__meta">共 {{ total }} 套题集</div>
          <el-pagination
            v-model:current-page="page.current"
            v-model:page-size="page.size"
            :page-sizes="[10, 20, 50]"
            layout="sizes, prev, pager, next"
            :total="total"
            @current-change="loadQuestionSets()"
            @size-change="loadQuestionSets()"
          />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="generateDialogVisible"
      title="生成一套专属练习"
      width="min(680px, calc(100vw - 32px))"
      destroy-on-close
      class="quiz-generate-dialog"
    >
      <template #header="{ titleId, titleClass }">
        <h2 :id="titleId" :class="[titleClass, 'dialog-title']">生成一套专属练习</h2>
        <div class="dialog-subtitle">根据学习资料，定制出题范围、题型、题量与难度。</div>
      </template>
      <EmptyState
        v-if="!materialsLoading && !materialsError && !materials.length"
        variant="compact"
        icon="folder"
        title="先准备一份学习资料"
        description="上传并解析资料后，就能围绕它生成练习。"
      >
        <el-button type="primary" @click="router.push('/materials')">前往资料库</el-button>
      </EmptyState>
      <el-form v-else label-position="top">
        <p v-if="materialsError" class="quiz-inline-note quiz-inline-note--error" role="alert">
          资料列表加载失败：{{ materialsError }}
          <el-button link type="primary" :loading="materialsLoading" @click="loadMaterials">重试</el-button>
        </p>
        <el-form-item label="学习资料">
          <el-select
            v-model="form.materialId"
            filterable
            style="width: 100%"
            placeholder="选择练习的资料"
            aria-label="选择练习的资料"
            :loading="materialsLoading"
          >
            <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="出题范围">
          <el-radio-group v-model="form.scope" aria-label="出题范围">
            <el-radio-button value="MATERIAL">整份资料</el-radio-button>
            <el-radio-button value="KNOWLEDGE">指定知识点</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <div v-if="form.scope === 'KNOWLEDGE'" class="quiz-knowledge-scope">
          <el-form-item label="练习知识点">
            <el-select
              :model-value="form.knowledgePoints"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              :multiple-limit="MAX_KNOWLEDGE_POINTS"
              :loading="knowledgePointsLoading"
              :disabled="!form.materialId"
              placeholder="选择知识点，或输入名称后按 Enter 添加"
              aria-label="练习知识点"
              style="width: 100%"
              @update:model-value="updateKnowledgePoints"
            >
              <el-option v-for="point in knowledgePointOptions" :key="point" :label="point" :value="point" />
            </el-select>
          </el-form-item>
          <p class="quiz-scope-help">
            使用资料中出现的知识点名称；每个知识点至少一道题。最多 {{ MAX_KNOWLEDGE_POINTS }} 个，每个不超过
            {{ MAX_KNOWLEDGE_POINT_LENGTH }} 字。
          </p>
          <p v-if="knowledgePointsError" class="quiz-scope-help quiz-inline-note--error" role="alert">
            {{ knowledgePointsError }}，仍可手动输入。
            <el-button link type="primary" :loading="knowledgePointsLoading" @click="loadKnowledgePoints"
              >重试知识点</el-button
            >
          </p>
          <p
            v-else-if="form.materialId && !knowledgePointsLoading && !knowledgePointOptions.length"
            class="quiz-scope-help"
          >
            暂无推荐知识点，可输入资料中的概念或章节名称，按 Enter 添加。
          </p>
          <p v-if="knowledgeScopeError" class="quiz-scope-help quiz-inline-note--error" role="alert">
            {{ knowledgeScopeError }}
          </p>
        </div>
        <p class="quiz-inline-note quiz-scope-summary" aria-live="polite">本次范围：{{ scopeSummary }}</p>
        <el-form-item label="题集名称"
          ><el-input v-model="form.questionSetTitle" maxlength="200" show-word-limit placeholder="给这次练习起个名字"
        /></el-form-item>
        <div class="quiz-form-heading">
          <strong>题型组合</strong><span>共 {{ totalQuestionCount }} 道 · 最多 {{ MAX_QUESTION_COUNT }} 道</span>
        </div>
        <div class="quiz-count-grid">
          <el-form-item label="单选题"
            ><el-input-number v-model="form.singleCount" :min="0" :max="MAX_QUESTION_COUNT" aria-label="单选题数量"
          /></el-form-item>
          <el-form-item label="多选题"
            ><el-input-number v-model="form.multipleCount" :min="0" :max="MAX_QUESTION_COUNT" aria-label="多选题数量"
          /></el-form-item>
          <el-form-item label="判断题"
            ><el-input-number v-model="form.judgeCount" :min="0" :max="MAX_QUESTION_COUNT" aria-label="判断题数量"
          /></el-form-item>
          <el-form-item label="简答题"
            ><el-input-number
              v-model="form.shortAnswerCount"
              :min="0"
              :max="MAX_QUESTION_COUNT"
              aria-label="简答题数量"
          /></el-form-item>
        </div>
        <p v-if="form.multipleCount" class="quiz-inline-note">多选题：全部选对得分，少选、多选或错选不得分。</p>
        <el-alert
          v-if="!isQuestionCountValid"
          :title="`请选择 1 至 ${MAX_QUESTION_COUNT} 道题，再开始生成。`"
          type="warning"
          :closable="false"
          show-icon
          class="quiz-notice"
        />
        <el-form-item label="练习难度" class="quiz-difficulty-field">
          <el-slider
            v-model="form.difficultyLevel"
            :min="1"
            :max="5"
            :marks="DIFFICULTY_LABELS"
            :format-tooltip="formatDifficulty"
            show-stops
          />
        </el-form-item>
        <details class="quiz-advanced">
          <summary>更多设置</summary>
          <el-form-item label="生成模型（选填）"
            ><el-input
              v-model="form.modelName"
              :placeholder="aiConfig.defaultModel ? '默认使用 ' + aiConfig.defaultModel : '使用已配置的默认模型'"
          /></el-form-item>
          <el-button link type="primary" @click="router.push('/ai-config')">管理 AI 设置</el-button>
        </details>
        <p v-if="aiConfigError" class="quiz-inline-note quiz-inline-note--error">
          AI 设置暂时无法读取：{{ aiConfigError }}
          <el-button link type="primary" :loading="aiConfigLoading" @click="loadAiConfig">重试</el-button>
        </p>
        <p v-else-if="aiConfig.mockMode" class="quiz-inline-note">
          当前为演示模式，生成的题目仅用于体验。<el-button link type="primary" @click="router.push('/ai-config')"
            >配置 AI</el-button
          >
        </p>
        <el-alert
          v-else-if="!aiConfig.enabled"
          title="AI 功能当前已关闭，请在 AI 设置中启用后生成。"
          type="warning"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="generating"
          :disabled="!hasSelectedMaterial || !isQuestionCountValid || Boolean(knowledgeScopeError)"
          @click="generateQuestionSet"
          >生成 {{ totalQuestionCount }} 道题</el-button
        >
      </template>
    </el-dialog>

    <el-drawer
      v-model="drawerVisible"
      title="题集预览"
      size="min(720px, 100vw)"
      :close-on-click-modal="false"
      class="quiz-preview-drawer"
    >
      <div v-if="detailLoading" class="state-block">正在加载题集…</div>
      <template v-else-if="questionSetDetail">
        <div class="quiz-preview-heading">
          <h2>{{ questionSetDetail.title }}</h2>
          <p>
            <span>{{ questionSetDetail.questionCount }} 道题</span
            ><span>满分 {{ questionSetDetail.totalScore }} 分</span
            ><span>{{ formatQuestionSetStatus(questionSetDetail.status) }}</span>
          </p>
        </div>
        <div class="quiz-preview-questions">
          <article
            v-for="(question, index) in questionSetDetail.questions"
            :key="question.id"
            class="quiz-preview-question"
          >
            <div class="quiz-preview-question__meta">
              <span>{{ formatQuestionType(question.questionType, '练习题') }}</span
              ><span>{{ question.score }} 分</span>
            </div>
            <h3>{{ question.sortNo || index + 1 }}. {{ question.stemText }}</h3>
            <p v-if="isMultiQuestion(question)" class="quiz-inline-note">
              多选题：全部选对得分，少选、多选或错选不得分。
            </p>
            <div v-if="question.optionA" class="option-list">
              <div>A. {{ question.optionA }}</div>
              <div v-if="question.optionB">B. {{ question.optionB }}</div>
              <div v-if="question.optionC">C. {{ question.optionC }}</div>
              <div v-if="question.optionD">D. {{ question.optionD }}</div>
            </div>
            <details class="quiz-answer">
              <summary>查看参考答案与解析</summary>
              <div class="analysis-box">
                <p>
                  <strong>参考答案</strong
                  >{{ question.correctAnswer ? formatAnswer(question, question.correctAnswer) : '暂无参考答案' }}
                </p>
                <p><strong>解析</strong>{{ question.answerAnalysis || '暂无解析' }}</p>
                <p v-if="question.knowledgePoint"><strong>知识点</strong>{{ question.knowledgePoint }}</p>
              </div>
            </details>
          </article>
        </div>
        <details
          v-if="questionSetDetail.sourceMode === 'GENERATION_SNAPSHOT' && questionSetDetail.sourceSegments?.length"
          class="quiz-answer quiz-references"
        >
          <summary>出题时的资料依据</summary>
          <p>以下片段保存于本次出题时，反映生成题目所用的资料内容。</p>
          <SourceSegmentList :segments="questionSetDetail.sourceSegments" compact />
        </details>
        <p v-else class="quiz-inline-note quiz-references" role="note">
          {{
            questionSetDetail.sourceMode === 'GENERATION_SNAPSHOT'
              ? '该题集未记录可展示的出题依据。'
              : '该历史题集未保存出题时的资料片段，无法还原原始依据。'
          }}
        </p>
      </template>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭预览</el-button>
        <el-button
          v-if="questionSetDetail"
          type="primary"
          :loading="startingQuestionSetId === questionSetDetail.id"
          :disabled="!canPracticeQuestionSet(questionSetDetail.status)"
          @click="startPractice(questionSetDetail.id)"
          >开始练习</el-button
        >
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  getAiConfigApi,
  getQuestionKnowledgePointsApi,
  submitQuestionGenerateTaskApi,
  type AiConfigResponse,
  type AiTaskDetail
} from '@/api/modules/ai'
import { startPracticeApi } from '@/api/modules/practice'
import { deleteQuestionSetApi, getQuestionSetDetailApi, getQuestionSetPageApi } from '@/api/modules/question'
import { isAiTaskSuccess, isAiTaskTerminal, parseAiTaskResult, waitForAiTaskUntilTerminal } from '@/utils/aiTask'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDate, formatIndex, type SourceSegmentLike } from '@/utils/format'
import {
  DIFFICULTY_LABELS,
  formatDifficulty,
  formatQuestionSetStatus,
  formatQuestionType,
  statusTone
} from '@/utils/labels'
import { useAssistantContext } from '@/composables/useAssistantContext'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useParsedMaterials } from '@/composables/useParsedMaterials'
import { usePolling } from '@/composables/usePolling'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import ListSectionHeading from '@/components/ListSectionHeading.vue'
import SourceSegmentList from '@/components/SourceSegmentList.vue'
import StatusPill from '@/components/StatusPill.vue'
import { formatAnswer, isMultiQuestion } from '@/components/practice/practiceHelpers'

/** 题集分页列表项（/question-set/page）。 */
interface QuestionSetItem {
  id: number
  materialId?: number
  title: string
  status?: string
  questionCount?: number
  totalScore?: number
  difficultyLevel?: number
  createdAt?: string
}

interface QuestionSetQuestion {
  id: number
  sortNo?: number
  questionType?: string
  stemText?: string
  score?: number
  optionA?: string
  optionB?: string
  optionC?: string
  optionD?: string
  correctAnswer?: string
  answerAnalysis?: string
  knowledgePoint?: string
}

/** 题集详情（/question-set/{id}），出题任务成功后的 resultJson 也是同一结构。 */
interface QuestionSetDetail extends QuestionSetItem {
  questions?: QuestionSetQuestion[]
  sourceMode?: 'GENERATION_SNAPSHOT' | 'UNAVAILABLE'
  sourceSegments?: SourceSegmentLike[]
}

interface AssistantQuestionContext {
  questionSetId?: number
  materialId?: number
}

const MAX_QUESTION_COUNT = 20
const MAX_KNOWLEDGE_POINTS = 5
const MAX_KNOWLEDGE_POINT_LENGTH = 80
const FILTERABLE_STATUSES = ['GENERATING', 'PUBLISHED', 'FAILED', 'CANCELLED', 'DISABLED']
const CARD_HINTS: Record<string, string> = {
  GENERATING: '题目正在生成，完成后可预览和练习。',
  FAILED: '生成未完成，可到任务中心查看原因并重试。',
  CANCELLED: '本次生成已取消，可重新生成题集。'
}

const route = useRoute()
// 保存入口参数，异步加载期间助手上下文的路由更新不影响专项练习预填。
const initialGenerateRequest = {
  materialId: Number(route.query.materialId),
  knowledgePoint: typeof route.query.knowledgePoint === 'string' ? route.query.knowledgePoint.trim() : '',
  open: route.query.generate === '1'
}
const router = useRouter()
const assistantContext = useAssistantContext()
const { materials, loading: materialsLoading, error: materialsError, load: loadMaterials } = useParsedMaterials()

const questionSets = ref<QuestionSetItem[]>([])
const total = ref(0)
const questionSetDetail = ref<QuestionSetDetail | null>(null)
const questionSetLoading = ref(false)
const loadError = ref('')
const generating = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const generateDialogVisible = ref(false)
const actionLoadingId = ref<number | null>(null)
const startingQuestionSetId = ref<number | null>(null)
const currentTask = ref<AiTaskDetail | null>(null)
const pendingQuestionContext = ref<AssistantQuestionContext | null>(null)
let viewDisposed = false

const aiConfig = ref<Pick<AiConfigResponse, 'enabled' | 'mockMode' | 'defaultModel'>>({
  enabled: true,
  mockMode: true,
  defaultModel: ''
})
const aiConfigLoading = ref(false)
const aiConfigError = ref('')
const knowledgePointOptions = ref<string[]>([])
const knowledgePointsLoading = ref(false)
const knowledgePointsError = ref('')
let knowledgePointsRequestId = 0

const form = reactive({
  materialId: undefined as number | undefined,
  scope: 'MATERIAL' as 'MATERIAL' | 'KNOWLEDGE',
  knowledgePoints: [] as string[],
  questionSetTitle: '',
  modelName: '',
  singleCount: 2,
  multipleCount: 1,
  judgeCount: 1,
  shortAnswerCount: 1,
  difficultyLevel: 3
})
const lastAutoQuestionSetTitle = ref('')

const filters = reactive({
  keyword: '',
  status: '',
  difficultyLevel: undefined as number | undefined
})
const hasFilters = computed(() => Boolean(filters.keyword || filters.status || filters.difficultyLevel))
const canPracticeQuestionSet = (status?: string) => status === 'PUBLISHED'
const canPreviewQuestionSet = (status?: string) => canPracticeQuestionSet(status) || status === 'DISABLED'

const page = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery([() => filters.keyword, () => filters.status, () => filters.difficultyLevel], () => {
  page.current = 1
  return loadQuestionSets()
})

const totalQuestionCount = computed(
  () =>
    Number(form.singleCount || 0) +
    Number(form.multipleCount || 0) +
    Number(form.judgeCount || 0) +
    Number(form.shortAnswerCount || 0)
)
const isQuestionCountValid = computed(
  () => totalQuestionCount.value >= 1 && totalQuestionCount.value <= MAX_QUESTION_COUNT
)
const hasSelectedMaterial = computed(() => materials.value.some((item) => item.id === form.materialId))
const knowledgeScopeError = computed(() => {
  if (form.scope !== 'KNOWLEDGE') return ''
  if (!form.knowledgePoints.length) return '请至少选择或输入 1 个知识点。'
  if (form.knowledgePoints.length > MAX_KNOWLEDGE_POINTS) return `最多选择 ${MAX_KNOWLEDGE_POINTS} 个知识点。`
  if (form.knowledgePoints.some((point) => !point.trim() || point.length > MAX_KNOWLEDGE_POINT_LENGTH)) {
    return `知识点名称不能为空，且每个不超过 ${MAX_KNOWLEDGE_POINT_LENGTH} 字。`
  }
  if (form.knowledgePoints.length > totalQuestionCount.value)
    return '题目数量不能少于知识点数量，请增加题量或减少知识点。'
  return ''
})
const scopeSummary = computed(() =>
  form.scope === 'MATERIAL' ? '整份资料' : form.knowledgePoints.join('、') || '尚未选择知识点'
)

const updateKnowledgePoints = (points: string[]) => {
  form.knowledgePoints = [...new Set(points.map((point) => point.trim()).filter(Boolean))]
}

const loadKnowledgePoints = async () => {
  const materialId = form.materialId
  const requestId = ++knowledgePointsRequestId
  knowledgePointOptions.value = []
  knowledgePointsError.value = ''
  knowledgePointsLoading.value = Boolean(materialId)
  if (!materialId) return
  try {
    const response = await getQuestionKnowledgePointsApi(materialId)
    if (viewDisposed || requestId !== knowledgePointsRequestId) return
    const points: unknown = response.data.data
    knowledgePointOptions.value = Array.isArray(points)
      ? [
          ...new Set(
            points
              .filter((point): point is string => typeof point === 'string')
              .map((point) => point.trim())
              .filter((point) => point && point.length <= MAX_KNOWLEDGE_POINT_LENGTH)
          )
        ]
      : []
  } catch (error) {
    if (!viewDisposed && requestId === knowledgePointsRequestId)
      knowledgePointsError.value = getErrorMessage(error, '推荐知识点加载失败')
  } finally {
    if (!viewDisposed && requestId === knowledgePointsRequestId) knowledgePointsLoading.value = false
  }
}

/** 学习助手优先关注当前打开的预览或生成表单，再回退至题集列表。 */
const preferredAssistantQuestionContext = computed<AssistantQuestionContext>(() => {
  if (drawerVisible.value && questionSetDetail.value?.id) {
    return { questionSetId: questionSetDetail.value.id, materialId: questionSetDetail.value.materialId }
  }
  if (drawerVisible.value && pendingQuestionContext.value?.questionSetId) {
    return pendingQuestionContext.value
  }
  if (generateDialogVisible.value && form.materialId) {
    return { materialId: form.materialId }
  }
  if (questionSets.value[0]?.id) {
    return { questionSetId: questionSets.value[0].id, materialId: questionSets.value[0].materialId }
  }
  if (currentTask.value?.bizId) {
    return { materialId: Number(currentTask.value.bizId) }
  }
  return {}
})

const taskStatusText = computed(() => {
  if (!generating.value || !currentTask.value || isAiTaskTerminal(currentTask.value.status)) return ''
  const materialLabel = materials.value.find((item) => item.id === currentTask.value?.bizId)?.title
  return `正在生成题集${materialLabel ? `：${materialLabel}` : ''}，完成后将自动打开预览。`
})

watch(drawerVisible, (visible) => {
  if (!visible) pendingQuestionContext.value = null
})

watch(
  () => form.materialId,
  () => {
    form.knowledgePoints = []
    void loadKnowledgePoints()
    syncDefaultQuestionSetTitle()
  }
)

watch([() => form.scope, () => form.knowledgePoints.join('\u0000')], () => syncDefaultQuestionSetTitle())

watch(generateDialogVisible, (visible) => {
  if (!visible) return
  syncDefaultQuestionSetTitle()
  // 首次读取失败时，打开对话框再试一次，避免一直显示过期的提示。
  if (aiConfigError.value && !aiConfigLoading.value) void loadAiConfig()
})

// 只有真实存在的正整数 id 才会写入路由，数据尚未加载时会清掉过期的上下文。
const toPositiveId = (value?: number) => (Number.isInteger(value) && Number(value) > 0 ? Number(value) : undefined)
watch(
  () => [preferredAssistantQuestionContext.value.questionSetId, preferredAssistantQuestionContext.value.materialId],
  ([questionSetId, materialId]) => {
    void assistantContext.sync({ questionSetId: toPositiveId(questionSetId), materialId: toPositiveId(materialId) })
  },
  { immediate: true }
)

const buildDefaultQuestionSetTitle = (materialId?: number) => {
  const material = materialId ? materials.value.find((item) => item.id === materialId) : undefined
  const suffix = form.scope === 'KNOWLEDGE' ? `${form.knowledgePoints.join('、') || '知识点'}专项练习` : 'AI练习题'
  return (material?.title ? `${material.title} - ${suffix}` : suffix).slice(0, 200)
}

const syncDefaultQuestionSetTitle = () => {
  const nextAutoTitle = buildDefaultQuestionSetTitle(form.materialId)
  if (!form.questionSetTitle.trim() || form.questionSetTitle === lastAutoQuestionSetTitle.value) {
    form.questionSetTitle = nextAutoTitle
  }
  lastAutoQuestionSetTitle.value = nextAutoTitle
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.status = ''
    filters.difficultyLevel = undefined
    page.current = 1
  })
}

/** AI 配置只影响生成对话框里的提示，读取失败不弹 toast，改为在对话框内提示。 */
const loadAiConfig = async () => {
  aiConfigLoading.value = true
  try {
    const res = await getAiConfigApi()
    aiConfig.value = res.data.data
    aiConfigError.value = ''
  } catch (error) {
    aiConfigError.value = getErrorMessage(error, '请稍后重试')
  } finally {
    aiConfigLoading.value = false
  }
}

const questionSetRequest = useLatestRequest()

/** `silent` 为 true 时不显示骨架屏、不改动错误态，供轮询与后台刷新使用。 */
const loadQuestionSets = async (silent = false) => {
  if (viewDisposed) return
  const requestId = questionSetRequest.begin()
  if (!silent) {
    questionSetLoading.value = true
    loadError.value = ''
  }
  try {
    const res = await getQuestionSetPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      difficultyLevel: filters.difficultyLevel
    })
    if (viewDisposed || !questionSetRequest.isLatest(requestId)) return
    questionSets.value = res.data.data.records || []
    total.value = res.data.data.total || 0
    loadError.value = ''
  } catch (error) {
    if (!viewDisposed && questionSetRequest.isLatest(requestId) && !silent) {
      loadError.value = getErrorMessage(error, '题集暂时无法加载，请稍后重试。')
    }
  } finally {
    if (!viewDisposed && questionSetRequest.isLatest(requestId)) questionSetLoading.value = false
  }
}

usePolling(
  () => questionSets.value.some((item) => item.status === 'GENERATING'),
  () => loadQuestionSets(true),
  3000
)

const generateQuestionSet = async () => {
  if (generating.value) return
  if (!form.materialId || !hasSelectedMaterial.value) {
    ElMessage.warning('请先选择资料')
    return
  }
  if (knowledgeScopeError.value) {
    ElMessage.warning(knowledgeScopeError.value)
    return
  }
  if (totalQuestionCount.value <= 0) {
    ElMessage.warning('请至少配置一种题型并设置数量')
    return
  }
  if (totalQuestionCount.value > MAX_QUESTION_COUNT) {
    ElMessage.warning(`题目总数最多为 ${MAX_QUESTION_COUNT} 道`)
    return
  }

  generating.value = true
  currentTask.value = null
  const generatingMaterialId = form.materialId
  try {
    const submitRes = await submitQuestionGenerateTaskApi(generatingMaterialId, {
      modelName: form.modelName.trim() || undefined,
      title: form.questionSetTitle.trim() || undefined,
      questionCount: totalQuestionCount.value,
      singleCount: form.singleCount,
      multipleCount: form.multipleCount,
      judgeCount: form.judgeCount,
      shortAnswerCount: form.shortAnswerCount,
      difficultyLevel: form.difficultyLevel,
      knowledgePoints: form.scope === 'KNOWLEDGE' ? [...form.knowledgePoints] : undefined
    })
    const submittedTask = submitRes.data.data as AiTaskDetail
    if (viewDisposed) return
    currentTask.value = submittedTask
    generateDialogVisible.value = false
    ElMessage.success('AI 出题任务已提交')
    // 后台刷新即可：保留用户当前的筛选与页码，不闪骨架屏。
    await loadQuestionSets(true)

    // 出题慢时持续等待，直到任务结束或用户离开本页；偶发网络错误会自动重试。
    const finishedTask = await waitForAiTaskUntilTerminal(submittedTask.id, {
      shouldStop: () => viewDisposed,
      onProgress: (task) => {
        currentTask.value = task
      }
    })
    if (viewDisposed || !finishedTask || !isAiTaskTerminal(finishedTask.status)) return
    currentTask.value = finishedTask
    await loadQuestionSets(true)
    if (viewDisposed) return
    if (!isAiTaskSuccess(finishedTask.status)) {
      throw new Error(finishedTask.errorMessage || 'AI 出题任务执行失败')
    }

    const taskResult = parseAiTaskResult<QuestionSetDetail>(finishedTask)
    if (taskResult) {
      questionSetDetail.value = taskResult
      pendingQuestionContext.value = {
        questionSetId: taskResult.id,
        materialId: taskResult.materialId || generatingMaterialId
      }
      drawerVisible.value = true
    }
    ElMessage.success(aiConfig.value.mockMode ? '演示题集已生成' : '题集已生成')
  } catch (error) {
    if (!viewDisposed) {
      ElMessage.error(getErrorMessage(error, '题集任务失败'))
      if (currentTask.value && !isAiTaskTerminal(currentTask.value.status)) await loadQuestionSets(true)
    }
  } finally {
    generating.value = false
  }
}

const questionSetDetailRequest = useLatestRequest()

const viewQuestionSet = async (item: QuestionSetItem) => {
  if (!canPreviewQuestionSet(item.status)) return
  const requestId = questionSetDetailRequest.begin()
  drawerVisible.value = true
  detailLoading.value = true
  questionSetDetail.value = null
  pendingQuestionContext.value = { questionSetId: item.id, materialId: item.materialId }
  try {
    const res = await getQuestionSetDetailApi(item.id)
    if (viewDisposed || !questionSetDetailRequest.isLatest(requestId)) return
    questionSetDetail.value = res.data.data
  } catch (error) {
    if (viewDisposed || !questionSetDetailRequest.isLatest(requestId)) return
    questionSetDetail.value = null
    pendingQuestionContext.value = null
    ElMessage.error(getErrorMessage(error, '加载题集详情失败'))
  } finally {
    if (questionSetDetailRequest.isLatest(requestId)) detailLoading.value = false
  }
}

const startPractice = async (questionSetId: number) => {
  if (startingQuestionSetId.value !== null) return
  startingQuestionSetId.value = questionSetId
  try {
    const res = await startPracticeApi(questionSetId)
    ElMessage.success('练习已开始')
    router.push({ path: '/practice', query: { sessionId: String(res.data.data.sessionId) } })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '开始练习失败'))
  } finally {
    startingQuestionSetId.value = null
  }
}

const removeQuestionSet = async (id: number) => {
  const confirmed = await confirmDanger('删除题集后，关联的练习记录也会一起删除，确定继续吗？')
  if (!confirmed) return

  actionLoadingId.value = id
  try {
    await deleteQuestionSetApi(id)
    ElMessage.success('题集删除成功')
    if (questionSetDetail.value?.id === id) {
      drawerVisible.value = false
      questionSetDetail.value = null
      pendingQuestionContext.value = null
    }
    await loadQuestionSets()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除题集失败'))
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadAiConfig(), loadMaterials(), loadQuestionSets()])
  if (viewDisposed) return
  const queryId = initialGenerateRequest.materialId
  if (queryId && materials.value.some((item) => item.id === queryId)) {
    form.materialId = queryId
  }
  await nextTick()
  if (initialGenerateRequest.knowledgePoint) {
    form.scope = 'KNOWLEDGE'
    if (hasSelectedMaterial.value) updateKnowledgePoints([initialGenerateRequest.knowledgePoint])
  }
  if (initialGenerateRequest.open) {
    generateDialogVisible.value = true
    if (!hasSelectedMaterial.value) ElMessage.warning('请先选择可用的已解析资料，再设置知识点。')
  }
  syncDefaultQuestionSetTitle()
})

onUnmounted(() => {
  viewDisposed = true
  knowledgePointsRequestId++
})
</script>

<style scoped>
.quiz-knowledge-scope {
  margin-bottom: 16px;
}
.quiz-knowledge-scope :deep(.el-form-item) {
  margin-bottom: 8px;
}
.quiz-scope-help {
  margin: 6px 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.quiz-scope-summary {
  overflow-wrap: anywhere;
}
.quiz-view .workspace-panel {
  background: transparent;
  border: 0;
  border-radius: 0;
  overflow: visible;
}
.quiz-view .quiz-filter-bar {
  display: grid;
  grid-template-columns: minmax(200px, 1.6fr) minmax(130px, 0.65fr) minmax(130px, 0.65fr) auto;
  gap: 12px;
  width: 100%;
  margin-bottom: 0;
}
.quiz-view .workspace-toolbar {
  margin: 0;
  padding: 0 0 26px;
  border-bottom: 1px solid var(--line);
  background: transparent;
}
.quiz-view .workspace-filter-bar :deep(.el-input__wrapper),
.quiz-view .workspace-filter-bar :deep(.el-select__wrapper) {
  min-height: 46px;
  padding-inline: 16px;
  border-radius: 999px;
  background: transparent;
  box-shadow: none;
}
.quiz-view .workspace-filter-bar :deep(.el-input__wrapper) {
  background: var(--bg-secondary);
}
.quiz-view .workspace-filter-bar :deep(.el-select__wrapper:hover) {
  background: var(--bg-secondary);
}
.quiz-view .workspace-filter-bar :deep(.el-input__wrapper.is-focus),
.quiz-view .workspace-filter-bar :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 2px var(--brand);
}
.quiz-view .workspace-body {
  padding: 0;
}
.quiz-notice {
  margin-block: 20px;
}
.quiz-loading {
  display: grid;
  gap: 24px;
  padding: 40px 0;
  color: var(--muted);
  font-size: 14px;
}
.quiz-card-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}
.quiz-set-card {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) 272px;
  gap: 26px;
  align-items: center;
  min-width: 0;
  padding: 24px 20px;
  border-radius: var(--radius);
  border: 1px solid transparent;
  border-bottom: 1px solid var(--line);
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.quiz-set-card:hover {
  background: var(--panel-hover);
  border-color: color-mix(in srgb, var(--brand) 20%, transparent);
  box-shadow: var(--shadow);
  transform: translateY(-2px);
}
.quiz-set-card__number {
  align-self: start;
  padding-top: 4px;
  color: var(--muted);
  font-size: 25px;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
}
.quiz-set-card__content {
  min-width: 0;
}
.quiz-set-card__top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}
.quiz-set-card__category {
  font-size: 12px;
  color: var(--muted);
  letter-spacing: 0.04em;
}
.quiz-set-card__title {
  width: fit-content;
  max-width: 100%;
  min-height: 44px;
  margin: 9px 0 0;
  padding: 4px 0;
  border: 0;
  background: none;
  color: var(--text);
  font: inherit;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.6;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
  transition: color 0.18s ease;
}
.quiz-set-card__title:hover:not(:disabled) {
  color: var(--brand);
}
.quiz-set-card__title:disabled {
  cursor: default;
}
.quiz-set-card__title:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 5px;
  border-radius: 3px;
}
.quiz-set-card__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 9px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.quiz-set-card__meta > span + span::before {
  content: '·';
  margin-right: 14px;
  color: var(--muted);
}
.quiz-set-card__hint {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.quiz-set-card__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}
.quiz-set-card__secondary {
  display: flex;
  align-items: center;
  gap: 4px;
}
.quiz-set-card__secondary :deep(.el-button + .el-button) {
  margin-left: 0;
}
.dialog-title {
  margin: 0;
}
.quiz-inline-note {
  margin: 12px 0 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--bg);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.quiz-inline-note :deep(.el-button) {
  font-size: 12px;
  margin-left: 6px;
}
.quiz-inline-note--error {
  margin: 0 0 12px;
  color: var(--red);
  background: var(--red-soft);
}
.quiz-form-heading {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin: 4px 0 14px;
  font-size: 14px;
}
.quiz-form-heading span {
  color: var(--muted);
  font-size: 12px;
}
.quiz-count-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.quiz-count-grid :deep(.el-input-number) {
  width: 100%;
  height: 46px;
  --el-component-size: 46px;
}
.quiz-count-grid :deep(.el-input-number__increase),
.quiz-count-grid :deep(.el-input-number__decrease) {
  width: 44px;
}
.quiz-count-grid :deep(.el-input__wrapper) {
  padding-inline: 48px;
}
.quiz-difficulty-field {
  padding: 0 12px 18px;
}
.quiz-advanced {
  border-top: 1px solid var(--line);
  padding-top: 16px;
  margin-bottom: 16px;
}
.quiz-advanced summary {
  padding-bottom: 16px;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
}
.quiz-preview-heading {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line);
}
.quiz-preview-heading h2 {
  margin: 0;
  font-size: 21px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  color: var(--text);
}
.quiz-preview-heading p {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--muted);
}
.quiz-preview-questions {
  display: grid;
  gap: 0;
  margin-top: 10px;
}
.quiz-preview-question {
  padding: 28px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
}
.quiz-preview-question__meta {
  display: flex;
  justify-content: space-between;
  color: var(--brand);
  font-size: 12px;
}
.quiz-preview-question h3 {
  margin: 14px 0;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.85;
  overflow-wrap: anywhere;
}
.quiz-preview-question .option-list {
  color: var(--text);
  font-size: 15px;
  line-height: 1.8;
}
.quiz-preview-question .option-list > div {
  padding: 9px 0;
  background: transparent;
  border-radius: 0;
}
.quiz-answer {
  margin-top: 20px;
  font-size: 13px;
}
.quiz-answer summary {
  color: var(--brand);
  cursor: pointer;
  line-height: 1.8;
}
.quiz-answer .analysis-box {
  border-radius: 8px;
}
.quiz-answer p {
  margin: 8px 0;
  color: var(--text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.quiz-answer p strong {
  display: block;
  margin-bottom: 3px;
}
.quiz-references {
  border-top: 1px solid var(--line);
  padding-top: 20px;
}
.quiz-references :deep(.source-segment-list) {
  margin-top: 16px;
}
@media (max-width: 1000px) {
  .quiz-set-card {
    grid-template-columns: 40px minmax(0, 1fr);
    gap: 16px 22px;
  }
  .quiz-set-card__actions {
    grid-column: 2;
    justify-content: space-between;
  }
  .quiz-view .quiz-filter-bar {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  }
  .quiz-view .workspace-filter-bar__search {
    grid-column: 1 / -1;
  }
}
@media (max-width: 600px) {
  .quiz-view .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 24px;
  }
  .quiz-view .page-header > .el-button {
    align-self: flex-start;
  }
  .quiz-view .quiz-filter-bar {
    gap: 10px 4px;
  }
  .quiz-view .workspace-filter-bar :deep(.el-select__wrapper) {
    padding-inline: 10px;
  }
  .quiz-view .workspace-toolbar {
    padding-bottom: 20px;
  }
  .quiz-set-card {
    grid-template-columns: 28px minmax(0, 1fr);
    padding-block: 24px;
    gap: 14px;
  }
  .quiz-set-card__number {
    font-size: 20px;
  }
  .quiz-set-card__title {
    font-size: 18px;
  }
  .quiz-set-card__meta {
    font-size: 12px;
    gap: 6px 10px;
  }
  .quiz-set-card__meta > span + span::before {
    margin-right: 10px;
  }
  .quiz-set-card__date {
    flex-basis: 100%;
  }
  .quiz-set-card__meta > .quiz-set-card__date::before {
    display: none;
  }
  .quiz-set-card__actions > :deep(.el-button) {
    padding-inline: 16px;
  }
  .quiz-count-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .quiz-set-card,
  .quiz-set-card__title {
    transition: none;
  }
  .quiz-set-card:hover {
    transform: none;
  }
}
</style>
