<template>
  <section class="learning-page quiz-view" aria-labelledby="quiz-view-title">
    <header class="page-header">
      <div>
        <h1 id="quiz-view-title" class="page-title">题集</h1>
        <p class="page-desc">根据资料出题，可以按知识点、题型和难度来出。交卷后自动批改，答错的题会进错题本。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button type="primary" :disabled="generating" @click="openNewGeneration"
          ><AppIcon name="plus" :size="16" />生成题集</el-button
        >
      </div>
    </header>

    <div class="workspace-panel">
      <div class="workspace-toolbar workspace-toolbar--filter">
        <div class="filter-bar" role="search" aria-label="筛选题集">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索题集名称"
            aria-label="搜索题集名称"
            @keyup.enter="autoQuery.runNow"
          >
            <template #prefix><AppIcon name="search" :size="16" /></template>
          </el-input>
          <el-select v-model="filters.status" clearable placeholder="全部状态" aria-label="按状态筛选">
            <el-option
              v-for="status in FILTERABLE_STATUSES"
              :key="status"
              :label="formatQuestionSetStatus(status)"
              :value="status"
            />
          </el-select>
          <el-select v-model="filters.difficultyLevel" clearable placeholder="全部难度" aria-label="按难度筛选">
            <el-option v-for="level in 5" :key="level" :label="formatDifficulty(level)" :value="level" />
          </el-select>
          <el-button v-if="hasFilters" text @click="resetFilters">清除筛选</el-button>
        </div>
        <el-button text :loading="questionSetLoading" aria-label="刷新题集列表" @click="loadQuestionSets()">
          <AppIcon v-if="!questionSetLoading" name="refresh" :size="16" /><span class="workspace-toolbar__refresh-label"
            >刷新</span
          >
        </el-button>
      </div>

      <p v-if="taskStatusText" class="workspace-progress" role="status">
        <span class="workspace-progress__spinner" aria-hidden="true" />{{ taskStatusText }}
      </p>
      <div v-if="questionSetLoading" class="record-list" role="status" aria-label="正在加载题集">
        <div v-for="i in 4" :key="i" class="list-row record-row record-row--loading" aria-hidden="true">
          <span class="skeleton record-row__sk-lead" />
          <div class="record-row__main">
            <span class="skeleton record-row__sk-title" />
            <span class="skeleton record-row__sk-meta" />
          </div>
          <span class="skeleton record-row__sk-actions" />
        </div>
      </div>
      <EmptyState v-else-if="loadError" variant="error" title="题集列表没有加载出来" :description="loadError">
        <el-button type="primary" @click="loadQuestionSets()">重新加载</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!questionSets.length && hasFilters"
        icon="search"
        title="没有找到匹配的题集"
        description="换个关键词，或者清除筛选条件再看看。"
      >
        <el-button @click="resetFilters">清除筛选</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!questionSets.length"
        icon="quiz"
        title="还没有题集"
        :description="
          materials.length
            ? '选一份资料，设置题型、题量和难度，就能生成一套题。'
            : '先在资料库上传一份资料，解析完成后就能出题。'
        "
      >
        <el-button v-if="materials.length" type="primary" :disabled="generating" @click="openNewGeneration"
          >生成题集</el-button
        >
        <el-button v-else type="primary" @click="router.push('/materials')">去资料库</el-button>
      </EmptyState>
      <ul v-else class="record-list">
        <li v-for="item in questionSets" :key="item.id" class="list-row record-row quiz-row">
          <span
            class="quiz-row__count"
            :class="{ 'is-muted': !canPreviewQuestionSet(item.status) }"
            :title="
              canPreviewQuestionSet(item.status) ? `${item.questionCount} 道题` : `计划 ${item.questionCount} 道题`
            "
          >
            <b>{{ item.questionCount ?? '–' }}</b
            >题
          </span>
          <div class="record-row__main">
            <h2 class="record-row__title">
              <button v-if="canPreviewQuestionSet(item.status)" type="button" @click="viewQuestionSet(item)">
                {{ item.title }}
              </button>
              <span v-else>{{ item.title }}</span>
              <StatusPill
                v-if="item.status !== 'PUBLISHED'"
                :tone="statusTone(item.status)"
                :label="formatQuestionSetStatus(item.status)"
              />
              <GenerationModeBadge v-else-if="item.generationMode === 'MOCK'" :mode="item.generationMode" />
            </h2>
            <p v-if="CARD_HINTS[item.status || '']" class="record-row__desc">{{ CARD_HINTS[item.status || ''] }}</p>
            <p class="record-row__meta">
              <span v-if="canPreviewQuestionSet(item.status)">满分 {{ item.totalScore }} 分</span>
              <span>{{ formatDifficulty(item.difficultyLevel) }}</span>
              <time :datetime="item.createdAt" :title="formatDateTime(item.createdAt)">{{
                formatRelativeDay(item.createdAt)
              }}</time>
            </p>
          </div>
          <div class="record-row__actions" role="group" :aria-label="`${item.title}的操作`">
            <el-button
              v-if="canPracticeQuestionSet(item.status)"
              size="small"
              type="primary"
              plain
              :loading="startingQuestionSetId === item.id"
              :disabled="startingQuestionSetId !== null && startingQuestionSetId !== item.id"
              @click="startPractice(item.id)"
              >开始练习</el-button
            >
            <el-button v-else-if="item.status === 'GENERATING'" size="small" text @click="router.push('/ai-tasks')"
              >查看进度</el-button
            >
            <el-button v-else-if="item.status === 'FAILED'" size="small" @click="router.push('/ai-tasks')"
              >查看原因</el-button
            >
            <el-button v-if="canPreviewQuestionSet(item.status)" size="small" text @click="viewQuestionSet(item)"
              >预览</el-button
            >
            <el-button
              v-if="canPreviewQuestionSet(item.status)"
              size="small"
              text
              :disabled="generating"
              @click="openRegeneration(item)"
              >重新生成</el-button
            >
            <el-dropdown trigger="click" :disabled="item.status === 'GENERATING'" @command="removeQuestionSet(item.id)">
              <IconMenuButton
                :label="`${item.title}的更多操作`"
                :loading="actionLoadingId === item.id"
                :disabled="item.status === 'GENERATING'"
              />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete" class="menu-item--danger">删除题集</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </li>
      </ul>

      <footer v-if="!questionSetLoading && !loadError && total > 0" class="workspace-pagination">
        <span class="workspace-pagination__meta">{{ hasFilters ? `找到 ${total} 套题集` : `共 ${total} 套题集` }}</span>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          :total="total"
          @current-change="loadQuestionSets()"
          @size-change="loadQuestionSets()"
        />
      </footer>
    </div>

    <el-dialog
      v-model="generateDialogVisible"
      :title="regenerationSourceId ? '重新生成题集' : '生成题集'"
      width="min(680px, calc(100vw - 32px))"
      destroy-on-close
      class="quiz-generate-dialog"
      top="6vh"
      :close-on-click-modal="!generating"
      :close-on-press-escape="!generating"
      :show-close="!generating"
    >
      <p v-if="practiceEntryNotice" class="quiz-inline-note quiz-inline-note--error" role="alert">
        {{ practiceEntryNotice }}
      </p>
      <div v-if="regenerationLoading" class="state-block" aria-live="polite">正在读取上次的出题设置…</div>
      <EmptyState
        v-else-if="regenerationError"
        variant="error"
        title="没有读到上次的出题设置"
        :description="regenerationError"
      >
        <el-button @click="openNewGeneration">改为生成新题集</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!regenerationSourceId && !materialsLoading && !materialsError && !materials.length"
        variant="compact"
        icon="folder"
        title="还没有可用的资料"
        description="资料上传并解析完成后，才能出题。"
      >
        <el-button type="primary" @click="router.push('/materials')">去资料库</el-button>
      </EmptyState>
      <el-form v-else label-position="top" :disabled="generating">
        <p v-if="regenerationSourceId" class="quiz-inline-note" role="note">
          会根据资料现在的内容出一套新题，原来的题集和练习记录都会保留。
          {{ regenerationConfig?.notice }}
        </p>
        <p
          v-if="regenerationSourceId && !materialsLoading && !materialsError && !hasSelectedMaterial"
          class="quiz-inline-note quiz-inline-note--error"
          role="alert"
        >
          原来的资料已经删除或还没解析完成，没法重新生成。
          <el-button link type="primary" @click="openNewGeneration">换一份资料生成</el-button>
        </p>
        <p v-if="materialsError" class="quiz-inline-note quiz-inline-note--error" role="alert">
          资料列表没有加载出来：{{ materialsError }}
          <el-button link type="primary" :loading="materialsLoading" @click="loadMaterials">重试</el-button>
        </p>
        <el-form-item label="资料">
          <el-select
            v-model="form.materialId"
            filterable
            style="width: 100%"
            placeholder="选择一份已解析的资料"
            aria-label="选择资料"
            :loading="materialsLoading"
            :disabled="Boolean(regenerationSourceId)"
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
          <el-form-item label="知识点">
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
              placeholder="选择知识点，也可以输入名称后按 Enter 添加"
              aria-label="知识点"
              style="width: 100%"
              @update:model-value="updateKnowledgePoints"
            >
              <el-option v-for="point in knowledgePointOptions" :key="point" :label="point" :value="point" />
            </el-select>
          </el-form-item>
          <p class="quiz-scope-help">
            用资料里出现过的名称，每个知识点至少出一道题。最多 {{ MAX_KNOWLEDGE_POINTS }} 个，每个不超过
            {{ MAX_KNOWLEDGE_POINT_LENGTH }} 字。
          </p>
          <p v-if="knowledgePointsError" class="quiz-scope-help quiz-inline-note--error" role="alert">
            {{ knowledgePointsError }}，也可以直接输入。
            <el-button link type="primary" :loading="knowledgePointsLoading" @click="loadKnowledgePoints"
              >重新加载</el-button
            >
          </p>
          <p
            v-else-if="form.materialId && !knowledgePointsLoading && !knowledgePointOptions.length"
            class="quiz-scope-help"
          >
            没有推荐的知识点，可以输入资料里的概念或章节名称，按 Enter 添加。
          </p>
          <p v-if="knowledgeScopeError" class="quiz-scope-help quiz-inline-note--error" role="alert">
            {{ knowledgeScopeError }}
          </p>
        </div>
        <el-form-item label="题集名称"
          ><el-input v-model="form.questionSetTitle" maxlength="200" show-word-limit placeholder="给这次练习起个名字"
        /></el-form-item>
        <div class="quiz-form-heading">
          <strong>题型和题量</strong><span>共 {{ totalQuestionCount }} 道，最多 {{ MAX_QUESTION_COUNT }} 道</span>
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
        <p v-if="form.multipleCount" class="quiz-scope-help">多选题全部选对才得分，少选、多选或选错都不得分。</p>
        <el-alert
          v-if="!isQuestionCountValid"
          :title="`题目数量要在 1 到 ${MAX_QUESTION_COUNT} 道之间。`"
          type="warning"
          :closable="false"
          show-icon
          class="quiz-notice"
        />
        <el-form-item label="难度" class="quiz-difficulty-field">
          <el-slider
            v-model="form.difficultyLevel"
            :min="1"
            :max="5"
            :marks="DIFFICULTY_LABELS"
            :format-tooltip="formatDifficulty"
            :format-value-text="formatDifficulty"
            aria-label="难度"
            show-stops
          />
        </el-form-item>
        <details class="quiz-advanced">
          <summary>更多设置</summary>
          <el-form-item label="模型（选填）"
            ><el-input
              v-model="form.modelName"
              :placeholder="aiConfig.defaultModel ? '不填就用默认模型 ' + aiConfig.defaultModel : '不填就用默认模型'"
          /></el-form-item>
          <el-button link type="primary" @click="router.push('/ai-config')">去 AI 配置</el-button>
        </details>
        <el-checkbox
          v-if="regenerationConfig?.parameterMode === 'LEGACY_REVIEW'"
          v-model="regenerationReviewed"
          class="quiz-regeneration-confirm"
        >
          我已核对出题范围和模型
        </el-checkbox>
        <p v-if="regenerationConfig?.parameterMode === 'LEGACY_REVIEW'" class="quiz-scope-help">
          这套题集生成得比较早，没有保存出题范围和模型。现在默认用整份资料和默认模型，核对或修改后请勾选确认。
        </p>
        <p v-if="aiConfigError" class="quiz-inline-note quiz-inline-note--error">
          AI 配置没有读取到：{{ aiConfigError }}
          <el-button link type="primary" :loading="aiConfigLoading" @click="loadAiConfig">重试</el-button>
        </p>
        <p v-else-if="aiConfig.mockMode" class="quiz-inline-note">
          现在是演示模式，题目只用来体验流程。<el-button link type="primary" @click="router.push('/ai-config')"
            >去配置模型</el-button
          >
        </p>
        <el-alert
          v-else-if="!aiConfig.enabled"
          title="AI 功能已关闭，在 AI 配置里打开后才能出题。"
          type="warning"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button :disabled="generating" @click="generateDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="generating"
          :disabled="
            !hasSelectedMaterial ||
            !isQuestionCountValid ||
            Boolean(knowledgeScopeError) ||
            regenerationLoading ||
            Boolean(regenerationError) ||
            !regenerationCanSubmit
          "
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
      <div v-if="detailLoading" class="state-block" role="status">正在打开题集…</div>
      <EmptyState v-else-if="detailError" variant="error" title="题集没有打开" :description="detailError">
        <el-button @click="previewTarget && viewQuestionSet(previewTarget)">重试</el-button>
      </EmptyState>
      <template v-else-if="questionSetDetail">
        <div class="quiz-preview-heading">
          <h2>{{ questionSetDetail.title }}</h2>
          <p>
            <span>{{ questionSetDetail.questionCount }} 道题</span
            ><span>满分 {{ questionSetDetail.totalScore }} 分</span
            ><span>{{ formatQuestionSetStatus(questionSetDetail.status) }}</span>
            <GenerationModeBadge :mode="questionSetDetail.generationMode" />
          </p>
        </div>
        <p v-if="questionSetDetail.generationMode === 'MOCK'" class="quiz-inline-note">
          这套题由演示模式生成，只用来体验作答和复盘，题目和答案不一定准确。
        </p>
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
            <p v-if="isMultiQuestion(question)" class="quiz-preview-question__note">多选题，全部选对才得分。</p>
            <div v-if="question.optionA" class="option-list">
              <div>A. {{ question.optionA }}</div>
              <div v-if="question.optionB">B. {{ question.optionB }}</div>
              <div v-if="question.optionC">C. {{ question.optionC }}</div>
              <div v-if="question.optionD">D. {{ question.optionD }}</div>
            </div>
            <details class="quiz-answer">
              <summary>答案和解析</summary>
              <div class="analysis-box">
                <p>
                  <strong>参考答案</strong
                  >{{ question.correctAnswer ? formatAnswer(question, question.correctAnswer) : '没有记录参考答案' }}
                </p>
                <p><strong>解析</strong>{{ question.answerAnalysis || '没有解析' }}</p>
                <p v-if="question.knowledgePoint"><strong>知识点</strong>{{ question.knowledgePoint }}</p>
              </div>
            </details>
          </article>
        </div>
        <details
          v-if="questionSetDetail.sourceMode === 'GENERATION_SNAPSHOT' && questionSetDetail.sourceSegments?.length"
          class="quiz-answer quiz-references"
        >
          <summary>出题依据</summary>
          <p>这些是出题时保存下来的原文片段，题目就是根据它们出的。</p>
          <SourceSegmentList :segments="questionSetDetail.sourceSegments" compact />
        </details>
        <p v-else class="quiz-inline-note quiz-references" role="note">
          {{
            questionSetDetail.sourceMode === 'GENERATION_SNAPSHOT'
              ? '这套题集没有记录出题依据。'
              : '这套题集生成得比较早，没有保存出题时用到的原文。'
          }}
        </p>
      </template>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button
          v-if="questionSetDetail && canPreviewQuestionSet(questionSetDetail.status)"
          :disabled="generating"
          @click="openRegeneration(questionSetDetail)"
          >重新生成</el-button
        >
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
import {
  deleteQuestionSetApi,
  getQuestionSetDetailApi,
  getQuestionSetPageApi,
  getQuestionRegenerationConfigApi,
  regenerateQuestionSetApi,
  type QuestionRegenerationConfig
} from '@/api/modules/question'
import { isAiTaskSuccess, isAiTaskTerminal, parseAiTaskResult, waitForAiTaskUntilTerminal } from '@/utils/aiTask'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime, formatRelativeDay, type SourceSegmentLike } from '@/utils/format'
import { knowledgePointPracticeReason, knowledgePracticeUnavailableReason } from '@/utils/knowledgePractice'
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
import GenerationModeBadge from '@/components/GenerationModeBadge.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import SourceSegmentList from '@/components/SourceSegmentList.vue'
import StatusPill from '@/components/StatusPill.vue'
import { formatAnswer, isMultiQuestion } from '@/components/practice/practiceHelpers'
import type { GenerationMode } from '@/utils/generationMode'

/** 题集分页列表项（/question-set/page）。 */
interface QuestionSetItem {
  id: number
  materialId?: number
  generationMode?: GenerationMode
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
  GENERATING: '正在出题，完成后就可以预览和练习。',
  FAILED: '这次没有生成成功，可以在任务中心查看原因。',
  CANCELLED: '这次生成已取消。'
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
const {
  materials,
  loading: materialsLoading,
  loaded: materialsLoaded,
  error: materialsError,
  load: loadMaterials
} = useParsedMaterials()
const practiceEntryActive = ref(initialGenerateRequest.open)

const questionSets = ref<QuestionSetItem[]>([])
const total = ref(0)
const questionSetDetail = ref<QuestionSetDetail | null>(null)
const questionSetLoading = ref(false)
const loadError = ref('')
const generating = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const previewTarget = ref<QuestionSetItem | null>(null)
const drawerVisible = ref(false)
const generateDialogVisible = ref(false)
const regenerationSourceId = ref<number | null>(null)
const regenerationConfig = ref<QuestionRegenerationConfig | null>(null)
const regenerationLoading = ref(false)
const regenerationError = ref('')
const regenerationReviewed = ref(false)
let regenerationRequestId = 0
const regenerationCanSubmit = computed(
  () => !regenerationSourceId.value || regenerationConfig.value?.parameterMode === 'SAVED' || regenerationReviewed.value
)
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
const practiceEntryNotice = computed(() => {
  if (!practiceEntryActive.value || regenerationSourceId.value) return ''
  if (!hasSelectedMaterial.value) {
    return (
      knowledgePracticeUnavailableReason(
        { ...initialGenerateRequest, knowledgePoint: initialGenerateRequest.knowledgePoint || '资料练习' },
        {
          materials: materials.value,
          loaded: materialsLoaded.value,
          loading: materialsLoading.value,
          error: materialsError.value
        }
      ) || '请先选一份可用的资料，再设置知识点。'
    )
  }
  if (form.scope === 'KNOWLEDGE' && !form.knowledgePoints.length && initialGenerateRequest.knowledgePoint) {
    const reason = knowledgePointPracticeReason(initialGenerateRequest.knowledgePoint)
    if (reason) return `${reason} 请在下面手动设置知识点。`
  }
  return ''
})
const knowledgeScopeError = computed(() => {
  if (form.scope !== 'KNOWLEDGE') return ''
  if (!form.knowledgePoints.length) return '至少选择或输入 1 个知识点。'
  if (form.knowledgePoints.length > MAX_KNOWLEDGE_POINTS) return `最多选 ${MAX_KNOWLEDGE_POINTS} 个知识点。`
  if (form.knowledgePoints.some((point) => point.trim() === '未标注知识点'))
    return '“未标注知识点”只是占位名称，请换成具体的知识点。'
  if (form.knowledgePoints.some((point) => !point.trim() || point.length > MAX_KNOWLEDGE_POINT_LENGTH)) {
    return `知识点名称不能为空，每个最多 ${MAX_KNOWLEDGE_POINT_LENGTH} 字。`
  }
  if (form.knowledgePoints.length > totalQuestionCount.value)
    return '题目数量不能少于知识点数量，多加几道题或减少知识点。'
  return ''
})

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
      knowledgePointsError.value = getErrorMessage(error, '推荐的知识点没有加载出来')
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
  return `正在根据${materialLabel ? `“${materialLabel}”` : '资料'}出题，完成后会自动打开预览。`
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
  if (!visible) {
    regenerationRequestId++
    regenerationLoading.value = false
    return
  }
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
  const suffix = form.scope === 'KNOWLEDGE' ? `${form.knowledgePoints.join('、') || '知识点'}专项练习` : '练习题'
  return (material?.title ? `${material.title} - ${suffix}` : suffix).slice(0, 200)
}

const syncDefaultQuestionSetTitle = () => {
  if (regenerationSourceId.value) return
  const nextAutoTitle = buildDefaultQuestionSetTitle(form.materialId)
  if (!form.questionSetTitle.trim() || form.questionSetTitle === lastAutoQuestionSetTitle.value) {
    form.questionSetTitle = nextAutoTitle
  }
  lastAutoQuestionSetTitle.value = nextAutoTitle
}

const openNewGeneration = async () => {
  if (generating.value) return
  practiceEntryActive.value = false
  regenerationRequestId++
  const wasRegeneration = Boolean(regenerationSourceId.value)
  regenerationSourceId.value = null
  regenerationConfig.value = null
  regenerationLoading.value = false
  regenerationError.value = ''
  regenerationReviewed.value = false
  drawerVisible.value = false
  if (wasRegeneration) {
    Object.assign(form, {
      materialId: undefined,
      questionSetTitle: '',
      modelName: '',
      singleCount: 2,
      multipleCount: 1,
      judgeCount: 1,
      shortAnswerCount: 1,
      difficultyLevel: 3,
      scope: 'MATERIAL',
      knowledgePoints: []
    })
    await nextTick()
  }
  syncDefaultQuestionSetTitle()
  generateDialogVisible.value = true
}

const openRegeneration = async (item: QuestionSetItem) => {
  if (generating.value || !canPreviewQuestionSet(item.status)) return
  const requestId = ++regenerationRequestId
  regenerationSourceId.value = item.id
  regenerationConfig.value = null
  regenerationLoading.value = true
  regenerationError.value = ''
  regenerationReviewed.value = false
  drawerVisible.value = false
  generateDialogVisible.value = true
  try {
    const response = await getQuestionRegenerationConfigApi(item.id)
    if (viewDisposed || requestId !== regenerationRequestId || !generateDialogVisible.value) return
    const config = response.data.data as QuestionRegenerationConfig
    if (
      !config?.request ||
      config.sourceQuestionSetId !== item.id ||
      !['SAVED', 'LEGACY_REVIEW'].includes(config.parameterMode)
    )
      throw new Error('上次的出题设置不完整，请稍后重试。')
    form.materialId = config.materialId
    await nextTick()
    if (viewDisposed || requestId !== regenerationRequestId || !generateDialogVisible.value) return
    const request = config.request
    Object.assign(form, {
      questionSetTitle: `${config.title || item.title} - 重新生成`.slice(0, 200),
      modelName: request.modelName || '',
      singleCount: request.singleCount ?? 0,
      multipleCount: request.multipleCount ?? 0,
      judgeCount: request.judgeCount ?? 0,
      shortAnswerCount: request.shortAnswerCount ?? 0,
      difficultyLevel: request.difficultyLevel ?? 3,
      scope: request.knowledgePoints?.length ? 'KNOWLEDGE' : 'MATERIAL',
      knowledgePoints: [...(request.knowledgePoints || [])]
    })
    regenerationConfig.value = config
  } catch (error) {
    if (!viewDisposed && requestId === regenerationRequestId)
      regenerationError.value = getErrorMessage(error, '请稍后重试，或者改为生成新题集。')
  } finally {
    if (!viewDisposed && requestId === regenerationRequestId) regenerationLoading.value = false
  }
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
      loadError.value = getErrorMessage(error, '请稍后重试。')
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
  if (generating.value || regenerationLoading.value || regenerationError.value || !regenerationCanSubmit.value) return
  if (!form.materialId || !hasSelectedMaterial.value) {
    ElMessage.warning('请先选择资料')
    return
  }
  if (knowledgeScopeError.value) {
    ElMessage.warning(knowledgeScopeError.value)
    return
  }
  if (totalQuestionCount.value <= 0) {
    ElMessage.warning('至少设置一种题型的数量')
    return
  }
  if (totalQuestionCount.value > MAX_QUESTION_COUNT) {
    ElMessage.warning(`一次最多出 ${MAX_QUESTION_COUNT} 道题`)
    return
  }

  generating.value = true
  currentTask.value = null
  const generatingMaterialId = form.materialId
  const sourceQuestionSetId = regenerationSourceId.value
  try {
    const payload = {
      modelName: form.modelName.trim() || undefined,
      title: form.questionSetTitle.trim() || undefined,
      questionCount: totalQuestionCount.value,
      singleCount: form.singleCount,
      multipleCount: form.multipleCount,
      judgeCount: form.judgeCount,
      shortAnswerCount: form.shortAnswerCount,
      difficultyLevel: form.difficultyLevel,
      knowledgePoints: form.scope === 'KNOWLEDGE' ? [...form.knowledgePoints] : undefined
    }
    const submitRes = sourceQuestionSetId
      ? await regenerateQuestionSetApi(sourceQuestionSetId, {
          ...payload,
          modelName: form.modelName.trim(),
          knowledgePoints: form.scope === 'KNOWLEDGE' ? [...form.knowledgePoints] : [],
          configurationReviewed: regenerationConfig.value?.parameterMode === 'SAVED' || regenerationReviewed.value
        })
      : await submitQuestionGenerateTaskApi(generatingMaterialId, payload)
    const submittedTask = submitRes.data.data as AiTaskDetail
    if (viewDisposed) return
    currentTask.value = submittedTask
    generateDialogVisible.value = false
    ElMessage.success('已开始出题')
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
      throw new Error(finishedTask.errorMessage || '题集没有生成成功')
    }

    const taskResult = parseAiTaskResult<QuestionSetDetail>(finishedTask)
    if (taskResult) {
      questionSetDetail.value = taskResult
      detailError.value = ''
      pendingQuestionContext.value = {
        questionSetId: taskResult.id,
        materialId: taskResult.materialId || generatingMaterialId
      }
      drawerVisible.value = true
    }
    ElMessage.success(taskResult?.generationMode === 'MOCK' ? '题集已生成（演示模式）' : '题集已生成')
  } catch (error) {
    if (!viewDisposed) {
      ElMessage.error(getErrorMessage(error, '题集没有生成成功，可以在任务中心查看原因。'))
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
  previewTarget.value = item
  detailError.value = ''
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
    detailError.value = getErrorMessage(error, '请稍后重试。')
  } finally {
    if (questionSetDetailRequest.isLatest(requestId)) detailLoading.value = false
  }
}

const startPractice = async (questionSetId: number) => {
  if (startingQuestionSetId.value !== null) return
  startingQuestionSetId.value = questionSetId
  try {
    const res = await startPracticeApi(questionSetId)
    router.push({ path: '/practice', query: { sessionId: String(res.data.data.sessionId) } })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '练习没有开始，请稍后重试。'))
  } finally {
    startingQuestionSetId.value = null
  }
}

const removeQuestionSet = async (id: number) => {
  const confirmed = await confirmDanger('删除后，这套题集和它的练习记录都无法恢复。确定删除吗？')
  if (!confirmed) return

  actionLoadingId.value = id
  try {
    await deleteQuestionSetApi(id)
    ElMessage.success('题集已删除')
    if (questionSetDetail.value?.id === id) {
      drawerVisible.value = false
      questionSetDetail.value = null
      pendingQuestionContext.value = null
    }
    await loadQuestionSets()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '没有删除成功，请稍后重试。'))
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
    if (hasSelectedMaterial.value && !knowledgePointPracticeReason(initialGenerateRequest.knowledgePoint))
      updateKnowledgePoints([initialGenerateRequest.knowledgePoint])
  }
  if (initialGenerateRequest.open) {
    generateDialogVisible.value = true
  }
  syncDefaultQuestionSetTitle()
})

onUnmounted(() => {
  viewDisposed = true
  knowledgePointsRequestId++
  regenerationRequestId++
})
</script>

<style scoped>
.quiz-row__count {
  display: grid;
  flex: 0 0 40px;
  place-content: center;
  height: 40px;
  border-radius: 11px;
  background: var(--brand-light);
  color: var(--brand);
  font-size: 10px;
  line-height: 1.1;
  text-align: center;
}
.quiz-row__count b {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.quiz-row__count.is-muted {
  background: var(--bg-secondary);
  color: var(--muted);
}
.quiz-inline-note {
  margin: 0 0 16px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--bg);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.quiz-inline-note .el-button {
  margin-left: 4px;
  font-size: 13px;
}
.quiz-inline-note--error {
  background: var(--red-soft);
  color: var(--red);
}
.quiz-knowledge-scope {
  margin-bottom: 18px;
}
.quiz-knowledge-scope .el-form-item {
  margin-bottom: 6px;
}
.quiz-scope-help {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.quiz-scope-help.quiz-inline-note--error {
  margin: 4px 0 0;
  padding: 0;
  background: none;
  color: var(--red);
}
.quiz-form-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 4px 0 10px;
}
.quiz-form-heading strong {
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
}
.quiz-form-heading span {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.quiz-count-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 12px;
}
.quiz-count-grid .el-form-item {
  margin-bottom: 8px;
}
.quiz-count-grid .el-input-number {
  width: 100%;
}
.quiz-count-grid + .quiz-scope-help {
  margin: 0 0 12px;
}
.quiz-generate-dialog .el-alert {
  margin-bottom: 16px;
}
.quiz-difficulty-field {
  margin-top: 8px;
}
.quiz-advanced {
  margin: 0 0 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.quiz-advanced summary {
  padding-bottom: 12px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.quiz-regeneration-confirm {
  height: auto;
  margin-top: 4px;
}
.quiz-regeneration-confirm :deep(.el-checkbox__label) {
  white-space: normal;
}
.quiz-preview-heading {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.quiz-preview-heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.quiz-preview-heading p {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.quiz-preview-heading + .quiz-inline-note {
  margin: 16px 0 0;
}
.quiz-preview-question {
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}
.quiz-preview-question__meta {
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 12px;
}
.quiz-preview-question__meta > span:first-child {
  color: var(--brand);
  font-weight: 500;
}
.quiz-preview-question h3 {
  margin: 6px 0 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.75;
  overflow-wrap: anywhere;
}
.quiz-preview-question__note {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.quiz-preview-question .option-list {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.75;
}
.quiz-answer {
  margin-top: 12px;
  font-size: 13px;
}
.quiz-answer summary {
  width: fit-content;
  color: var(--brand);
  font-weight: 500;
  cursor: pointer;
}
.quiz-answer .analysis-box {
  margin-top: 8px;
}
.quiz-answer p {
  margin: 6px 0;
  color: var(--text-secondary);
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.quiz-answer p strong {
  display: block;
  color: var(--text);
  font-weight: 600;
}
details.quiz-references {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}
details.quiz-references > p {
  margin: 8px 0 12px;
  color: var(--muted);
}
p.quiz-references {
  margin-top: 20px;
}
@media (max-width: 640px) {
  .quiz-count-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
