<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 出题</h1>
        <p class="page-desc">
          题集生成改为统一走任务中心，生成完成后自动刷新列表并打开详情。
        </p>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="toolbar" style="margin-bottom: 0">
          <el-button type="primary" :loading="generating" @click="generateDialogVisible = true">
            生成题集
          </el-button>
          <el-button :loading="questionSetLoading" @click="loadQuestionSets">刷新列表</el-button>
          <el-button link type="primary" @click="router.push('/ai-config')">前往 AI 配置</el-button>
        </div>
        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">{{ aiConfig.mockMode ? 'Mock 模式' : '真实接口模式' }}</span>
          <span class="workspace-chip workspace-chip--brand">共 {{ total }} 套题</span>
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

        <div class="page-card workspace-card">
          <div class="workspace-filter-bar">
            <el-input
              v-model="filters.keyword"
              clearable
              placeholder="按题集标题搜索"
              class="workspace-filter-bar__search"
            />
            <el-select v-model="filters.status" clearable placeholder="题集状态">
              <el-option label="ACTIVE" value="ACTIVE" />
              <el-option label="DISABLED" value="DISABLED" />
            </el-select>
            <el-select v-model="filters.difficultyLevel" clearable placeholder="难度等级">
              <el-option v-for="level in 5" :key="level" :label="`难度 ${level}`" :value="level" />
            </el-select>
            <el-button type="primary" plain @click="searchQuestionSets">查询</el-button>
            <el-button @click="resetFilters">重置条件</el-button>
          </div>

          <div v-if="questionSetLoading" class="state-block">正在加载题集列表...</div>
          <div v-else-if="!questionSets.length" class="state-block empty">
            还没有符合条件的题集，先生成一套试试。
          </div>
          <div v-else class="workspace-table">
            <div class="workspace-table__head workspace-table__head--quiz">
              <span>题集标题</span>
              <span>题量</span>
              <span>总分</span>
              <span>难度</span>
              <span>状态</span>
              <span>操作</span>
            </div>

            <div
              v-for="item in questionSets"
              :key="item.id"
              class="workspace-table__row workspace-table__row--quiz"
            >
              <div class="workspace-table__title">
                <strong>{{ item.title }}</strong>
                <span>#{{ item.id }} · {{ formatDateTime(item.createdAt) }}</span>
              </div>
              <span>{{ item.questionCount }}</span>
              <span>{{ item.totalScore }}</span>
              <span>{{ item.difficultyLevel }}</span>
              <span>{{ item.status }}</span>
              <div class="workspace-action-row">
                <el-button link @click="viewQuestionSet(item.id)">查看</el-button>
                <el-button link type="success" @click="startPractice(item.id)">开始练习</el-button>
                <el-button link type="primary" @click="startPractice(item.id)">再练一次</el-button>
                <el-button
                  link
                  type="danger"
                  :loading="actionLoadingId === item.id"
                  @click="removeQuestionSet(item.id)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <div class="workspace-pagination">
            <div class="workspace-pagination__meta">
              第 {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页
            </div>
            <el-pagination
              v-model:current-page="page.current"
              v-model:page-size="page.size"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="total"
              @current-change="loadQuestionSets"
              @size-change="loadQuestionSets"
            />
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="generateDialogVisible" title="生成题集" width="720px" destroy-on-close>
      <div class="detail-meta-grid" style="margin-bottom: 18px">
        <div class="detail-meta-item">
          <span>AI 开关</span>
          <strong>{{ aiConfig.enabled ? '已启用' : '已关闭' }}</strong>
        </div>
        <div class="detail-meta-item">
          <span>默认模型</span>
          <strong>{{ aiConfig.defaultModel || '未设置' }}</strong>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item label="选择资料">
          <el-select
            v-model="form.materialId"
            filterable
            style="width: 100%"
            placeholder="请选择已解析资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · 难度${item.difficultyLevel || 3} · #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="模型名称">
            <el-input v-model="form.modelName" placeholder="留空则使用当前默认模型" />
          </el-form-item>
          <el-form-item label="题目总数">
            <el-input :model-value="`${totalQuestionCount} 道`" readonly />
          </el-form-item>
        </div>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="单选题">
            <el-input-number v-model="form.singleCount" :min="0" :max="20" style="width: 100%" />
          </el-form-item>
          <el-form-item label="判断题">
            <el-input-number v-model="form.judgeCount" :min="0" :max="20" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="简答题">
          <el-input-number v-model="form.shortAnswerCount" :min="0" :max="20" style="width: 100%" />
        </el-form-item>
        <el-alert
          :title="`默认组合是单选 3 道、判断 1 道、简答 1 道。当前共 ${totalQuestionCount} 道。`"
          :type="totalQuestionCount > 20 || totalQuestionCount <= 0 ? 'warning' : 'info'"
          :closable="false"
          show-icon
          style="margin-bottom: 18px"
        />
        <el-form-item label="难度等级">
          <el-slider v-model="form.difficultyLevel" :min="1" :max="5" show-stops />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="generateDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="generating" @click="generateQuestionSet">
            生成题集
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="题集详情" size="52%" :close-on-click-modal="false">
      <div v-if="detailLoading" class="state-block">正在加载题集详情...</div>
      <template v-else-if="questionSetDetail">
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span>标题</span>
            <strong>{{ questionSetDetail.title }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>题量</span>
            <strong>{{ questionSetDetail.questionCount }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>总分</span>
            <strong>{{ questionSetDetail.totalScore }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>状态</span>
            <strong>{{ questionSetDetail.status }}</strong>
          </div>
        </div>
        <div v-if="questionSetDetail.sourceSegments?.length" class="summary-dialog__content" style="margin-top: 18px">
          <div class="summary-dialog__content-title">本次出题引用资料</div>
          <div class="rag-reference-list">
            <div
              v-for="segment in questionSetDetail.sourceSegments"
              :key="`${segment.segmentId}-${segment.segmentNo || 0}`"
              class="rag-reference-item"
            >
              <div class="rag-reference-item__title">
                {{ segment.sectionTitle || `资料片段 #${segment.segmentNo || segment.segmentId}` }}
              </div>
              <div class="rag-reference-item__meta">{{ buildSegmentMeta(segment) }}</div>
              <div class="summary-block">{{ segment.contentText }}</div>
            </div>
          </div>
        </div>
        <div class="list-stack" style="margin-top: 18px">
          <div v-for="question in questionSetDetail.questions" :key="question.id" class="section-card">
            <div class="section-card__title">{{ question.sortNo }}. {{ question.stemText }}</div>
            <div class="section-card__meta">
              {{ question.questionType }} · {{ question.knowledgePoint || '未标注知识点' }} ·
              {{ question.score }} 分
            </div>
            <div v-if="question.optionA" class="option-list">
              <div>A. {{ question.optionA }}</div>
              <div>B. {{ question.optionB }}</div>
              <div v-if="question.optionC">C. {{ question.optionC }}</div>
              <div v-if="question.optionD">D. {{ question.optionD }}</div>
            </div>
            <div class="analysis-box">
              <div><strong>参考答案：</strong>{{ question.correctAnswer }}</div>
              <div><strong>解析：</strong>{{ question.answerAnalysis || '暂无解析' }}</div>
            </div>
          </div>
        </div>
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

const route = useRoute()
const router = useRouter()
const ASSISTANT_QUESTION_SET_QUERY_KEY = 'assistantQuestionSetId'
const ASSISTANT_MATERIAL_QUERY_KEY = 'assistantMaterialId'
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
const currentTask = ref<AiTaskDetail | null>(null)

const aiConfig = ref({
  enabled: true,
  mockMode: true,
  defaultModel: '',
  apiKeyConfigured: false,
  apiKeyPreview: ''
})

const form = reactive({
  materialId: undefined as number | undefined,
  modelName: '',
  singleCount: 3,
  judgeCount: 1,
  shortAnswerCount: 1,
  difficultyLevel: 3
})

const filters = reactive({
  keyword: '',
  status: '',
  difficultyLevel: undefined as number | undefined
})
const page = reactive({
  current: 1,
  size: 10
})

const totalQuestionCount = computed(
  () => Number(form.singleCount || 0) + Number(form.judgeCount || 0) + Number(form.shortAnswerCount || 0)
)

watch(drawerVisible, (visible) => {
  if (!visible) {
    void syncAssistantQuestionContext()
    return
  }
  if (questionSetDetail.value?.id) {
    void syncAssistantQuestionContext(questionSetDetail.value.id, questionSetDetail.value.materialId)
  }
})

const taskStatusText = computed(() => {
  if (!currentTask.value) {
    return ''
  }
  const materialLabel = materials.value.find((item) => item.id === currentTask.value?.bizId)?.title
  if (generating.value) {
    return `任务 #${currentTask.value.id} 正在生成题集${materialLabel ? `：${materialLabel}` : ''}`
  }
  if (!isAiTaskTerminal(currentTask.value.status)) {
    return `任务 #${currentTask.value.id} 仍在处理中，可稍后刷新列表查看结果`
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
  if (segment?.score !== undefined && segment?.score !== null) {
    parts.push(`相似度 ${Number(segment.score).toFixed(4)}`)
  }
  return parts.join(' · ') || '资料摘录'
}

const syncAssistantQuestionContext = async (questionSetId?: number, materialId?: number) => {
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
  filters.keyword = ''
  filters.status = ''
  filters.difficultyLevel = undefined
  page.current = 1
  loadQuestionSets()
}

const searchQuestionSets = () => {
  page.current = 1
  loadQuestionSets()
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
    const res = await getMaterialPageApi({ current: 1, size: 100 })
    materials.value = (res.data.data.records || []).filter((item: any) => item.parseStatus === 'SUCCESS')
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
      drawerVisible.value = true
      void syncAssistantQuestionContext(taskResult.id, taskResult.materialId || form.materialId)
    }
    ElMessage.success(aiConfig.value.mockMode ? 'Mock 题集生成完成' : 'AI 出题完成')
  } catch (error: any) {
    ElMessage.error(error.message || '题集任务失败')
  } finally {
    generating.value = false
  }
}

const viewQuestionSet = async (id: number) => {
  drawerVisible.value = true
  detailLoading.value = true
  void syncAssistantQuestionContext(id)
  try {
    const res = await getQuestionSetDetailApi(id)
    questionSetDetail.value = res.data.data
    void syncAssistantQuestionContext(res.data.data?.id, res.data.data?.materialId)
  } catch (error: any) {
    questionSetDetail.value = null
    void syncAssistantQuestionContext()
    ElMessage.error(error.message || '加载题集详情失败')
  } finally {
    detailLoading.value = false
  }
}

const startPractice = async (questionSetId: number) => {
  try {
    const res = await startPracticeApi(questionSetId)
    ElMessage.success('练习已开始')
    router.push({ path: '/practice', query: { sessionId: String(res.data.data.sessionId) } })
  } catch (error: any) {
    ElMessage.error(error.message || '开始练习失败')
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
      void syncAssistantQuestionContext()
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
})
</script>
