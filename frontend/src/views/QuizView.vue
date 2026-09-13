<template>
  <section class="quiz-view">
    <div class="page-header">
      <div>
        <div class="learning-eyebrow">把知识变成掌握</div>
        <h1 class="page-title">练习题集</h1>
        <p class="page-desc">从你的学习资料中生成练习，用一次小测检验理解。</p>
      </div>
      <el-button type="primary" :loading="generating" @click="generateDialogVisible = true">
        <svg class="button-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
        生成题集
      </el-button>
    </div>

    <div class="workspace-panel">
      <div class="quiz-library-heading">
        <div><h2>我的题集 <span>{{ total }}</span></h2><p>选一套题，开始今天的专注练习。</p></div>
        <el-button :loading="questionSetLoading" @click="loadQuestionSets">刷新</el-button>
      </div>
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar quiz-filter-bar">
          <el-input v-model="filters.keyword" clearable placeholder="搜索题集名称" aria-label="搜索题集" class="workspace-filter-bar__search" />
          <el-select v-model="filters.status" clearable placeholder="全部状态" aria-label="筛选题集状态">
            <el-option label="可练习" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
          <el-select v-model="filters.difficultyLevel" clearable placeholder="全部难度" aria-label="筛选难度">
            <el-option v-for="level in 5" :key="level" :label="formatDifficulty(level)" :value="level" />
          </el-select>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <el-alert v-if="taskStatusText" :title="taskStatusText" type="info" :closable="false" show-icon class="quiz-notice" />
        <div v-if="questionSetLoading" class="quiz-loading" aria-live="polite"><el-skeleton :rows="5" animated /><span>正在加载题集…</span></div>
        <div v-else-if="!questionSets.length" class="learning-empty">
          <div class="learning-empty__icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="4" y="3" width="16" height="18" rx="2" /><path d="m8 9 1.5 1.5L12 8M14 9h3M8 15h9" stroke-linecap="round" stroke-linejoin="round" /></svg></div>
          <h3>{{ hasFilters ? '没有找到匹配的题集' : '用一套练习，巩固刚学过的知识' }}</h3>
          <p>{{ hasFilters ? '换个关键词或难度，看看其他题集。' : '选择资料，自由组合单选、判断和简答题，找到适合自己的练习节奏。' }}</p>
          <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
          <el-button v-else-if="materials.length" type="primary" :loading="generating" @click="generateDialogVisible = true">生成第一套题</el-button>
          <el-button v-else type="primary" @click="router.push('/materials')">先添加学习资料</el-button>
        </div>
        <div v-else class="quiz-card-grid">
          <article v-for="item in questionSets" :key="item.id" class="quiz-set-card">
            <div class="quiz-set-card__top">
              <span class="quiz-set-card__category">学习自测</span>
              <span class="quiz-status" :class="{ 'quiz-status--disabled': item.status === 'DISABLED' }">{{ formatSetStatus(item.status) }}</span>
            </div>
            <button type="button" class="quiz-set-card__title" @click="viewQuestionSet(item)">{{ item.title }}</button>
            <p class="quiz-set-card__date">创建于 {{ formatDateTime(item.createdAt).slice(0, 10) }}</p>
            <div class="quiz-set-card__stats">
              <div><strong>{{ item.questionCount }}</strong><span>道题目</span></div>
              <div><strong>{{ item.totalScore }}</strong><span>总分</span></div>
              <div><strong class="quiz-set-card__difficulty">{{ formatDifficulty(item.difficultyLevel) }}</strong><span>练习难度</span></div>
            </div>
            <div class="quiz-set-card__actions">
              <div class="quiz-set-card__secondary">
                <el-button link @click="viewQuestionSet(item)">预览题目</el-button>
                <el-dropdown trigger="click" @command="removeQuestionSet(item.id)">
                  <el-button text :loading="actionLoadingId === item.id" aria-label="题集更多操作" class="quiz-more-button">···</el-button>
                  <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete">删除题集</el-dropdown-item></el-dropdown-menu></template>
                </el-dropdown>
              </div>
              <el-button type="primary" plain :loading="startingQuestionSetId === item.id" :disabled="item.status === 'DISABLED' || (startingQuestionSetId !== null && startingQuestionSetId !== item.id)" @click="startPractice(item.id)">
                {{ item.status === 'DISABLED' ? '暂不可练习' : '开始练习' }} <span v-if="item.status !== 'DISABLED'" class="quiz-action-arrow" aria-hidden="true">→</span>
              </el-button>
            </div>
          </article>
        </div>

        <div v-if="total" class="workspace-pagination">
          <div class="workspace-pagination__meta">共 {{ total }} 套题集</div>
          <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="loadQuestionSets" @size-change="loadQuestionSets" />
        </div>
      </div>
    </div>

    <el-dialog v-model="generateDialogVisible" title="生成一套专属练习" width="min(680px, calc(100vw - 32px))" destroy-on-close class="quiz-generate-dialog">
      <template #header><div class="dialog-title">生成一套专属练习</div><div class="dialog-subtitle">根据学习资料，定制题型、题量与难度。</div></template>
      <div v-if="!materialsLoading && !materials.length" class="learning-empty learning-empty--compact">
        <h3>先准备一份学习资料</h3>
        <p>上传并解析资料后，就能围绕它生成练习。</p>
        <el-button type="primary" @click="router.push('/materials')">前往资料库</el-button>
      </div>
      <el-form v-else label-position="top">
        <el-form-item label="学习资料">
          <el-select v-model="form.materialId" filterable style="width: 100%" placeholder="选择练习的资料" :loading="materialsLoading">
            <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题集名称"><el-input v-model="form.questionSetTitle" maxlength="200" show-word-limit placeholder="给这次练习起个名字" /></el-form-item>
        <div class="quiz-form-heading"><strong>题型组合</strong><span>共 {{ totalQuestionCount }} 道 · 最多 20 道</span></div>
        <div class="quiz-count-grid">
          <el-form-item label="单选题"><el-input-number v-model="form.singleCount" :min="0" :max="20" aria-label="单选题数量" controls-position="right" /></el-form-item>
          <el-form-item label="判断题"><el-input-number v-model="form.judgeCount" :min="0" :max="20" aria-label="判断题数量" controls-position="right" /></el-form-item>
          <el-form-item label="简答题"><el-input-number v-model="form.shortAnswerCount" :min="0" :max="20" aria-label="简答题数量" controls-position="right" /></el-form-item>
        </div>
        <el-alert v-if="totalQuestionCount > 20 || totalQuestionCount <= 0" title="请选择 1 至 20 道题，再开始生成。" type="warning" :closable="false" show-icon class="quiz-notice" />
        <el-form-item label="练习难度" class="quiz-difficulty-field">
          <el-slider v-model="form.difficultyLevel" :min="1" :max="5" :marks="difficultyMarks" :format-tooltip="formatDifficulty" show-stops />
        </el-form-item>
        <details class="quiz-advanced">
          <summary>更多设置</summary>
          <el-form-item label="生成模型（选填）"><el-input v-model="form.modelName" :placeholder="aiConfig.defaultModel ? '默认使用 ' + aiConfig.defaultModel : '使用已配置的默认模型'" /></el-form-item>
          <el-button link type="primary" @click="router.push('/ai-config')">管理 AI 设置</el-button>
        </details>
        <p v-if="aiConfig.mockMode" class="quiz-mode-note">当前为演示模式，生成的题目仅用于体验。<el-button link type="primary" @click="router.push('/ai-config')">配置 AI</el-button></p>
        <el-alert v-else-if="!aiConfig.enabled" title="AI 功能当前已关闭，请在 AI 设置中启用后生成。" type="warning" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" :disabled="!form.materialId || !materials.length || totalQuestionCount < 1 || totalQuestionCount > 20" @click="generateQuestionSet">生成 {{ totalQuestionCount }} 道题</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="题集预览" size="min(720px, 100vw)" :close-on-click-modal="false" class="quiz-preview-drawer">
      <div v-if="detailLoading" class="state-block">正在加载题集…</div>
      <template v-else-if="questionSetDetail">
        <div class="quiz-preview-heading">
          <h2>{{ questionSetDetail.title }}</h2>
          <p><span>{{ questionSetDetail.questionCount }} 道题</span><span>满分 {{ questionSetDetail.totalScore }} 分</span><span>{{ formatSetStatus(questionSetDetail.status) }}</span></p>
        </div>
        <div class="quiz-preview-questions">
          <article v-for="(question, index) in questionSetDetail.questions" :key="question.id" class="quiz-preview-question">
            <div class="quiz-preview-question__meta"><span>{{ formatQuestionType(question.questionType) }}</span><span>{{ question.score }} 分</span></div>
            <h3>{{ question.sortNo || index + 1 }}. {{ question.stemText }}</h3>
            <div v-if="question.optionA" class="option-list">
              <div>A. {{ question.optionA }}</div><div v-if="question.optionB">B. {{ question.optionB }}</div><div v-if="question.optionC">C. {{ question.optionC }}</div><div v-if="question.optionD">D. {{ question.optionD }}</div>
            </div>
            <details class="quiz-answer">
              <summary>查看参考答案与解析</summary>
              <div class="analysis-box"><p><strong>参考答案</strong>{{ question.correctAnswer || '暂无参考答案' }}</p><p><strong>解析</strong>{{ question.answerAnalysis || '暂无解析' }}</p><p v-if="question.knowledgePoint"><strong>知识点</strong>{{ question.knowledgePoint }}</p></div>
            </details>
          </article>
        </div>
        <details v-if="questionSetDetail.sourceSegments?.length" class="quiz-answer quiz-references">
          <summary>本次练习的资料出处</summary>
          <div class="rag-reference-list">
            <div v-for="segment in questionSetDetail.sourceSegments" :key="segment.segmentId + '-' + (segment.segmentNo || 0)" class="rag-reference-item">
              <div class="rag-reference-item__title">{{ segment.sectionTitle || '资料摘录 ' + (segment.segmentNo || segment.segmentId) }}</div>
              <div class="rag-reference-item__meta">{{ buildSegmentMeta(segment) }}</div>
              <div class="summary-block">{{ segment.contentText }}</div>
            </div>
          </div>
        </details>
      </template>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭预览</el-button>
        <el-button v-if="questionSetDetail" type="primary" :loading="startingQuestionSetId === questionSetDetail.id" :disabled="questionSetDetail.status === 'DISABLED'" @click="startPractice(questionSetDetail.id)">开始练习</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getAiConfigApi, submitQuestionGenerateTaskApi, type AiTaskDetail } from '@/api/modules/ai'
import { getMaterialPageApi } from '@/api/modules/material'
import { startPracticeApi } from '@/api/modules/practice'
import { deleteQuestionSetApi, getQuestionSetDetailApi, getQuestionSetPageApi } from '@/api/modules/question'
import { isAiTaskSuccess, isAiTaskTerminal, parseAiTaskResult, waitForAiTask } from '@/utils/aiTask'
import { useAutoListQuery } from '@/composables/useAutoListQuery'

const route = useRoute()
const router = useRouter()
const ASSISTANT_QUESTION_SET_QUERY_KEY = 'assistantQuestionSetId'
const ASSISTANT_MATERIAL_QUERY_KEY = 'assistantMaterialId'
type AssistantQuestionContext = {
  questionSetId?: number
  materialId?: number
}

const materials = ref<any[]>([])
const questionSets = ref<any[]>([])
const total = ref(0)
const questionSetDetail = ref<any>(null)
const materialsLoading = ref(false)
const questionSetLoading = ref(false)
const generating = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const configLoading = ref(false)
const generateDialogVisible = ref(false)
const actionLoadingId = ref<number | null>(null)
const startingQuestionSetId = ref<number | null>(null)
const currentTask = ref<AiTaskDetail | null>(null)
const pendingQuestionContext = ref<AssistantQuestionContext | null>(null)

const aiConfig = ref({
  enabled: true,
  mockMode: true,
  defaultModel: '',
  apiKeyConfigured: false,
  apiKeyPreview: ''
})

const form = reactive({
  materialId: undefined as number | undefined,
  questionSetTitle: '',
  modelName: '',
  singleCount: 3,
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
const difficultyMarks: Record<number, string> = { 1: '入门', 2: '基础', 3: '适中', 4: '进阶', 5: '挑战' }
const formatDifficulty = (level?: number) => difficultyMarks[level || 3] || '适中'
const formatSetStatus = (value?: string) => value === 'DISABLED' ? '已停用' : '可练习'
const formatQuestionType = (value?: string) => {
  const labels: Record<string, string> = { SINGLE: '单选题', MULTI: '多选题', JUDGE: '判断题', SHORT: '简答题', SHORT_ANSWER: '简答题' }
  return labels[value || ''] || '练习题'
}

const page = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.status, () => filters.difficultyLevel],
  () => {
    page.current = 1
    return loadQuestionSets()
  }
)

const totalQuestionCount = computed(
  () => Number(form.singleCount || 0) + Number(form.judgeCount || 0) + Number(form.shortAnswerCount || 0)
)

const preferredAssistantQuestionContext = computed<AssistantQuestionContext>(() => {
  if (drawerVisible.value && questionSetDetail.value?.id) {
    return {
      questionSetId: questionSetDetail.value.id,
      materialId: questionSetDetail.value.materialId
    }
  }
  if (drawerVisible.value && pendingQuestionContext.value?.questionSetId) {
    return pendingQuestionContext.value
  }
  if (questionSets.value[0]?.id) {
    return {
      questionSetId: questionSets.value[0].id,
      materialId: questionSets.value[0].materialId
    }
  }
  if (currentTask.value?.bizId) {
    return {
      materialId: Number(currentTask.value.bizId)
    }
  }
  if (generateDialogVisible.value && form.materialId) {
    return {
      materialId: form.materialId
    }
  }
  return {}
})

watch(drawerVisible, (visible) => {
  if (!visible) {
    pendingQuestionContext.value = null
  }
})

watch(
  () => form.materialId,
  () => {
    syncDefaultQuestionSetTitle()
  }
)

watch(generateDialogVisible, (visible) => {
  if (visible) {
    syncDefaultQuestionSetTitle()
  }
})

watch(
  () => [
    preferredAssistantQuestionContext.value.questionSetId,
    preferredAssistantQuestionContext.value.materialId
  ],
  ([questionSetId, materialId]) => {
    void syncAssistantQuestionContext(questionSetId, materialId)
  },
  { immediate: true }
)

const taskStatusText = computed(() => {
  if (!currentTask.value) {
    return ''
  }
  const materialLabel = materials.value.find((item) => item.id === currentTask.value?.bizId)?.title
  if (generating.value) {
    return `正在生成题集${materialLabel ? `：${materialLabel}` : ''}，完成后将自动打开预览。`
  }
  if (!isAiTaskTerminal(currentTask.value.status)) {
    return '题集仍在生成中，可以稍后刷新，或到任务中心查看进度。'
  }
  return ''
})

const formatDateTime = (value?: string) => {
  if (!value) {
    return '未知时间'
  }
  return value.replace('T', ' ').slice(0, 19)
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

const buildDefaultQuestionSetTitle = (materialId?: number) => {
  if (!materialId) {
    return 'AI练习题'
  }
  const material = materials.value.find((item) => item.id === materialId)
  if (!material?.title) {
    return 'AI练习题'
  }
  return `${material.title} - AI练习题`
}

const syncDefaultQuestionSetTitle = () => {
  const nextAutoTitle = buildDefaultQuestionSetTitle(form.materialId)
  if (!form.questionSetTitle.trim() || form.questionSetTitle === lastAutoQuestionSetTitle.value) {
    form.questionSetTitle = nextAutoTitle
  }
  lastAutoQuestionSetTitle.value = nextAutoTitle
}

async function syncAssistantQuestionContext(questionSetId?: number, materialId?: number) {
  const currentQuestionSetId = Number(route.query[ASSISTANT_QUESTION_SET_QUERY_KEY] || 0) || undefined
  const currentMaterialId = Number(route.query[ASSISTANT_MATERIAL_QUERY_KEY] || 0) || undefined
  if (currentQuestionSetId === questionSetId && currentMaterialId === materialId) {
    return
  }

  const nextQuery = { ...route.query }
  if (questionSetId) {
    nextQuery[ASSISTANT_QUESTION_SET_QUERY_KEY] = String(questionSetId)
  } else {
    delete nextQuery[ASSISTANT_QUESTION_SET_QUERY_KEY]
  }
  if (materialId) {
    nextQuery[ASSISTANT_MATERIAL_QUERY_KEY] = String(materialId)
  } else {
    delete nextQuery[ASSISTANT_MATERIAL_QUERY_KEY]
  }
  await router.replace({ path: route.path, query: nextQuery })
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.status = ''
    filters.difficultyLevel = undefined
    page.current = 1
  })
}

const loadAiConfig = async () => {
  configLoading.value = true
  try {
    const res = await getAiConfigApi()
    aiConfig.value = res.data.data
  } catch (error: any) {
    ElMessage.error(error.message || '加载 AI 配置失败')
  } finally {
    configLoading.value = false
  }
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

const loadQuestionSets = async () => {
  questionSetLoading.value = true
  try {
    const res = await getQuestionSetPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      difficultyLevel: filters.difficultyLevel
    })
    questionSets.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载题集失败')
  } finally {
    questionSetLoading.value = false
  }
}

const generateQuestionSet = async () => {
  if (!form.materialId) {
    ElMessage.warning('请先选择资料')
    return
  }
  if (totalQuestionCount.value <= 0) {
    ElMessage.warning('请至少配置一种题型并设置数量')
    return
  }
  if (totalQuestionCount.value > 20) {
    ElMessage.warning('题目总数最多为 20 道')
    return
  }

  generating.value = true
  try {
    const submitRes = await submitQuestionGenerateTaskApi(form.materialId, {
      modelName: form.modelName.trim() || undefined,
      title: form.questionSetTitle.trim() || undefined,
      questionCount: totalQuestionCount.value,
      singleCount: form.singleCount,
      judgeCount: form.judgeCount,
      shortAnswerCount: form.shortAnswerCount,
      difficultyLevel: form.difficultyLevel
    })
    const submittedTask = submitRes.data.data as AiTaskDetail
    currentTask.value = submittedTask
    const taskId = submittedTask.id
    generateDialogVisible.value = false
    ElMessage.success('AI 出题任务已提交')

    const finishedTask = await waitForAiTask(taskId)
    currentTask.value = finishedTask
    if (!isAiTaskTerminal(finishedTask.status)) {
      ElMessage.info('题集任务仍在处理中，可稍后刷新列表查看结果')
      return
    }
    if (!isAiTaskSuccess(finishedTask.status)) {
      throw new Error(finishedTask.errorMessage || 'AI 出题任务执行失败')
    }

    page.current = 1
    await loadQuestionSets()
    const taskResult = parseAiTaskResult<any>(finishedTask)
    if (taskResult) {
      questionSetDetail.value = taskResult
      pendingQuestionContext.value = {
        questionSetId: taskResult.id,
        materialId: taskResult.materialId || form.materialId
      }
      drawerVisible.value = true
    }
    ElMessage.success(aiConfig.value.mockMode ? '演示题集已生成' : '题集已生成')
  } catch (error: any) {
    ElMessage.error(error.message || '题集任务失败')
  } finally {
    generating.value = false
  }
}

const viewQuestionSet = async (item: any) => {
  drawerVisible.value = true
  detailLoading.value = true
  questionSetDetail.value = null
  pendingQuestionContext.value = {
    questionSetId: item.id,
    materialId: item.materialId
  }
  try {
    const res = await getQuestionSetDetailApi(item.id)
    questionSetDetail.value = res.data.data
  } catch (error: any) {
    questionSetDetail.value = null
    pendingQuestionContext.value = null
    ElMessage.error(error.message || '加载题集详情失败')
  } finally {
    detailLoading.value = false
  }
}

const startPractice = async (questionSetId: number) => {
  if (startingQuestionSetId.value !== null) return
  startingQuestionSetId.value = questionSetId
  try {
    const res = await startPracticeApi(questionSetId)
    ElMessage.success('练习已开始')
    router.push({ path: '/practice', query: { sessionId: String(res.data.data.sessionId) } })
  } catch (error: any) {
    ElMessage.error(error.message || '开始练习失败')
  } finally {
    startingQuestionSetId.value = null
  }
}

const removeQuestionSet = async (id: number) => {
  try {
    await ElMessageBox.confirm('删除题集后，关联的练习记录也会一起删除，确定继续吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

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
  } catch (error: any) {
    ElMessage.error(error.message || '删除题集失败')
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadAiConfig(), loadMaterials(), loadQuestionSets()])
  const queryId = Number(route.query.materialId)
  if (queryId && materials.value.some((item) => item.id === queryId)) {
    form.materialId = queryId
  }
  syncDefaultQuestionSetTitle()
})
</script>

<style scoped>
.learning-eyebrow { margin-bottom: 8px; color: var(--brand); font-size: 12px; font-weight: 650; letter-spacing: .12em; }
.button-icon { width: 16px; height: 16px; margin-right: 7px; }
.quiz-library-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 24px 24px 0; }
.quiz-library-heading h2 { display: flex; align-items: center; gap: 9px; margin: 0; font-size: 17px; }
.quiz-library-heading h2 span { min-width: 24px; padding: 2px 7px; color: var(--muted); border: 1px solid var(--line); border-radius: 6px; font-size: 12px; font-weight: 500; text-align: center; }
.quiz-library-heading p { margin: 7px 0 0; color: var(--muted); font-size: 13px; }
.quiz-view .quiz-filter-bar { display: grid; grid-template-columns: minmax(200px, 1.6fr) minmax(120px, .75fr) minmax(120px, .75fr) auto; width: 100%; margin-bottom: 0; }
.quiz-view .workspace-toolbar { margin-top: 6px; }
.quiz-notice { margin-bottom: 20px; }
.quiz-loading { display: grid; gap: 24px; padding: 24px; color: var(--muted); font-size: 13px; }
.quiz-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.quiz-set-card { display: flex; flex-direction: column; min-width: 0; padding: 23px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); transition: border-color .2s; }
.quiz-set-card:hover { border-color: var(--brand); }
.quiz-set-card__top { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.quiz-set-card__category { font-size: 12px; color: var(--muted); }
.quiz-status { display: inline-flex; align-items: center; gap: 6px; color: var(--brand); font-size: 12px; }
.quiz-status::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.quiz-status--disabled { color: var(--muted); }
.quiz-set-card__title { width: fit-content; max-width: 100%; margin: 18px 0 0; padding: 0; border: 0; background: none; color: var(--text); font: inherit; font-size: 18px; font-weight: 650; line-height: 1.6; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.quiz-set-card__title:hover { color: var(--brand); }
.quiz-set-card__title:focus-visible { outline: 2px solid var(--brand); outline-offset: 5px; border-radius: 3px; }
.quiz-set-card__date { margin: 7px 0 24px; color: var(--muted); font-size: 12px; }
.quiz-set-card__stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: auto; padding: 15px 0; background: var(--bg); border-radius: 8px; }
.quiz-set-card__stats > div { display: grid; gap: 7px; padding: 0 17px; }
.quiz-set-card__stats > div + div { border-left: 1px solid var(--line); }
.quiz-set-card__stats strong { color: var(--text); font-size: 24px; line-height: 1.2; font-weight: 600; font-variant-numeric: tabular-nums; }
.quiz-set-card__stats strong.quiz-set-card__difficulty { font-size: 19px; line-height: 1.5; }
.quiz-set-card__stats span { font-size: 12px; color: var(--muted); }
.quiz-set-card__actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 20px; }
.quiz-set-card__secondary { display: flex; align-items: center; gap: 5px; }
.quiz-set-card__secondary :deep(.el-button) { min-height: 36px; }
.quiz-more-button { font-size: 21px; letter-spacing: .06em; }
.quiz-action-arrow { margin-left: 10px; }
.learning-empty { display: grid; justify-items: center; align-content: center; min-height: 340px; padding: 40px 20px; text-align: center; }
.learning-empty__icon { display: grid; place-items: center; width: 64px; height: 64px; margin-bottom: 20px; border: 1px solid var(--line); border-radius: 16px; color: var(--brand); background: var(--bg); }
.learning-empty__icon svg { width: 29px; height: 29px; }
.learning-empty h3 { margin: 0 0 10px; color: var(--text); font-size: 18px; font-weight: 600; }
.learning-empty p { max-width: 440px; margin: 0 0 24px; color: var(--muted); font-size: 14px; line-height: 1.8; }
.learning-empty--compact { min-height: 200px; }
.quiz-form-heading { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin: 4px 0 14px; font-size: 14px; }
.quiz-form-heading span { color: var(--muted); font-size: 12px; }
.quiz-count-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.quiz-count-grid :deep(.el-input-number) { width: 100%; }
.quiz-difficulty-field { padding: 0 12px 18px; }
.quiz-advanced { border-top: 1px solid var(--line); padding-top: 16px; margin-bottom: 16px; }
.quiz-advanced summary { padding-bottom: 16px; color: var(--muted); font-size: 13px; cursor: pointer; }
.quiz-mode-note { padding: 12px 14px; border-radius: 8px; background: var(--bg); color: var(--muted); font-size: 12px; line-height: 1.8; }
.quiz-mode-note :deep(.el-button) { font-size: 12px; margin-left: 6px; }
.quiz-preview-heading { padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.quiz-preview-heading h2 { margin: 0; font-size: 21px; line-height: 1.6; overflow-wrap: anywhere; color: var(--text); }
.quiz-preview-heading p { display: flex; flex-wrap: wrap; gap: 16px; margin: 12px 0 0; font-size: 13px; color: var(--muted); }
.quiz-preview-questions { display: grid; gap: 18px; margin-top: 24px; }
.quiz-preview-question { padding: 20px; border: 1px solid var(--line); border-radius: 10px; }
.quiz-preview-question__meta { display: flex; justify-content: space-between; color: var(--brand); font-size: 12px; }
.quiz-preview-question h3 { margin: 14px 0; color: var(--text); font-size: 16px; font-weight: 600; line-height: 1.85; overflow-wrap: anywhere; }
.quiz-preview-question .option-list { color: var(--text); font-size: 14px; line-height: 1.8; }
.quiz-preview-question .option-list > div { padding: 9px 12px; background: var(--bg); border-radius: 6px; }
.quiz-answer { margin-top: 20px; font-size: 13px; }
.quiz-answer summary { color: var(--brand); cursor: pointer; line-height: 1.8; }
.quiz-answer .analysis-box { border-radius: 8px; }
.quiz-answer p { margin: 8px 0; color: var(--text); white-space: pre-wrap; overflow-wrap: anywhere; }
.quiz-answer p strong { display: block; margin-bottom: 3px; }
.quiz-references { border-top: 1px solid var(--line); padding-top: 20px; }
.quiz-references .rag-reference-list { margin-top: 16px; }
@media (max-width: 1000px) {
  .quiz-card-grid { grid-template-columns: 1fr; }
  .quiz-view .quiz-filter-bar { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 600px) {
  .quiz-library-heading { padding: 18px 16px 0; align-items: flex-start; }
  .quiz-library-heading p { max-width: 220px; line-height: 1.7; }
  .quiz-view .quiz-filter-bar { grid-template-columns: minmax(0, 1fr); }
  .quiz-set-card { padding: 18px; }
  .quiz-set-card__stats > div { padding: 0 12px; }
  .quiz-count-grid { grid-template-columns: 1fr; gap: 0; }
  .quiz-set-card__actions { flex-wrap: wrap; }
}
</style>
