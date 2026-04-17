<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">任务中心</h1>
        <p class="page-desc">
          统一查看 AI 总结、AI 出题、简答题评分和后续 Embedding 任务的执行状态，支持失败重试和业务跳转。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="loading" @click="loadTasks">刷新列表</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--task">
          <el-select v-model="filters.taskType" clearable placeholder="任务类型">
            <el-option label="AI 总结" value="SUMMARY" />
            <el-option label="AI 出题" value="QUESTION_GENERATE" />
            <el-option label="简答题评分" value="PRACTICE_REVIEW" />
            <el-option label="Embedding" value="EMBEDDING" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="任务状态">
            <el-option label="等待中" value="PENDING" />
            <el-option label="执行中" value="RUNNING" />
            <el-option label="已完成" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          <el-button type="primary" plain @click="searchTasks">查询</el-button>
          <el-button @click="resetFilters">重置条件</el-button>
        </div>

        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">共 {{ total }} 条任务</span>
          <span class="workspace-chip">处理中 {{ activeCount }} 条</span>
          <span class="workspace-chip workspace-chip--brand">失败 {{ failedCount }} 条</span>
        </div>
      </div>

      <div class="workspace-body">
        <el-alert
          v-if="activeCount > 0"
          title="当前存在处理中任务。这里会自动同步最新状态；如果是旧的等待中任务长时间不动，可以直接重新派发。"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />

        <div v-if="loading" class="state-block">正在加载任务列表...</div>
        <div v-else-if="!tasks.length" class="state-block empty">当前条件下还没有任务记录。</div>
        <div v-else class="workspace-table">
          <div class="workspace-table__head workspace-table__head--task">
            <span>任务</span>
            <span>业务对象</span>
            <span>状态</span>
            <span>模型</span>
            <span>创建时间</span>
            <span>操作</span>
          </div>

          <div
            v-for="item in tasks"
            :key="item.id"
            class="workspace-table__row workspace-table__row--task"
          >
            <div class="workspace-table__title">
              <strong>{{ formatTaskType(item.taskType) }}</strong>
              <span>任务 #{{ item.id }} · 优先级 {{ item.priority }}</span>
            </div>

            <div class="task-meta-stack">
              <strong>{{ formatBizLabel(item) }}</strong>
              <span>{{ formatBizHint(item) }}</span>
            </div>

            <div class="task-status-cell">
              <div
                class="task-status-pill"
                :class="`task-status-pill--${statusVisualClass(item.status)}`"
              >
                <span class="task-status-pill__text">{{ formatStatus(item.status) }}</span>
                <span v-if="isAnimatedStatus(item.status)" class="task-status-pill__dots">
                  <i></i><i></i><i></i>
                </span>
              </div>
              <span class="task-status-cell__meta">
                {{ buildTaskStatusHint(item) }}
              </span>
            </div>

            <span>{{ item.modelName || '--' }}</span>
            <span>{{ formatDateTime(item.createdAt) }}</span>

            <div class="workspace-action-row workspace-action-row--fit">
              <el-button link type="primary" @click="openTaskDetail(item.id)">查看详情</el-button>
              <el-button v-if="canOpenBizPage(item)" link @click="openBizPage(item)">前往业务</el-button>
              <el-button
                v-if="item.status === 'PENDING'"
                link
                type="warning"
                :loading="dispatchingTaskId === item.id"
                @click="dispatchTask(item.id)"
              >
                重新派发
              </el-button>
              <el-button
                v-if="item.status === 'FAILED'"
                link
                type="danger"
                :loading="retryingTaskId === item.id"
                @click="retryTask(item.id)"
              >
                重试
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
            @current-change="loadTasks"
            @size-change="loadTasks"
          />
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="任务详情" size="54%">
      <div v-if="detailLoading" class="state-block">正在加载任务详情...</div>
      <template v-else-if="taskDetail">
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span>任务类型</span>
            <strong>{{ formatTaskType(taskDetail.taskType) }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>任务状态</span>
            <strong>{{ formatStatus(taskDetail.status) }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>业务对象</span>
            <strong>{{ formatBizLabel(taskDetail) }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>执行模型</span>
            <strong>{{ taskDetail.modelName || '--' }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>开始时间</span>
            <strong>{{ formatDateTime(taskDetail.startedAt) }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>结束时间</span>
            <strong>{{ formatDateTime(taskDetail.finishedAt) }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>创建时间</span>
            <strong>{{ formatDateTime(taskDetail.createdAt) }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>更新时间</span>
            <strong>{{ formatDateTime(taskDetail.updatedAt) }}</strong>
          </div>
        </div>

        <div class="task-detail-actions">
          <el-button v-if="canOpenBizPage(taskDetail)" @click="openBizPage(taskDetail)">前往业务页</el-button>
          <el-button
            v-if="taskDetail.status === 'PENDING'"
            type="warning"
            plain
            :loading="dispatchingTaskId === taskDetail.id"
            @click="dispatchTask(taskDetail.id, true)"
          >
            重新派发
          </el-button>
          <el-button
            v-if="taskDetail.status === 'FAILED'"
            type="danger"
            plain
            :loading="retryingTaskId === taskDetail.id"
            @click="retryTask(taskDetail.id, true)"
          >
            重试任务
          </el-button>
        </div>

        <div class="task-json-grid">
          <div class="task-json-card">
            <div class="task-json-card__title">请求载荷</div>
            <pre class="task-json-block">{{ formatJsonContent(taskDetail.payloadJson) }}</pre>
          </div>
          <div class="task-json-card">
            <div class="task-json-card__title">任务结果</div>
            <pre class="task-json-block">{{ formatJsonContent(taskDetail.resultJson) }}</pre>
          </div>
          <div v-if="taskDetail.errorMessage" class="task-json-card task-json-card--error">
            <div class="task-json-card__title">错误信息</div>
            <pre class="task-json-block">{{ taskDetail.errorMessage }}</pre>
          </div>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  dispatchAiTaskApi,
  getAiTaskDetailApi,
  getAiTaskPageApi,
  retryAiTaskApi,
  type AiTaskDetail,
  type AiTaskPageItem,
  type AiTaskPagePayload
} from '@/api/modules/ai'

const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const retryingTaskId = ref<number | null>(null)
const dispatchingTaskId = ref<number | null>(null)
const tasks = ref<AiTaskPageItem[]>([])
const total = ref(0)
const taskDetail = ref<AiTaskDetail | null>(null)

const filters = reactive({
  taskType: '',
  status: ''
})

const page = reactive({
  current: 1,
  size: 10
})

const activeCount = computed(
  () => tasks.value.filter((item) => ['PENDING', 'RUNNING'].includes(String(item.status).toUpperCase())).length
)
const failedCount = computed(
  () => tasks.value.filter((item) => String(item.status).toUpperCase() === 'FAILED').length
)
const hasActiveTasks = computed(() =>
  tasks.value.some((item) => ['PENDING', 'RUNNING'].includes(String(item.status).toUpperCase()))
)
let autoRefreshTimer: number | null = null

const formatDateTime = (value?: string) => {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ').slice(0, 19)
}

const formatTaskType = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SUMMARY':
      return 'AI 总结'
    case 'QUESTION_GENERATE':
      return 'AI 出题'
    case 'PRACTICE_REVIEW':
      return '简答题评分'
    case 'EMBEDDING':
      return 'Embedding'
    default:
      return value || '未知任务'
  }
}

const formatStatus = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'PENDING':
      return '等待中...'
    case 'RUNNING':
      return '执行中...'
    case 'SUCCESS':
      return '已完成'
    case 'FAILED':
      return '失败'
    case 'CANCELLED':
      return '已取消'
    default:
      return value || '未知状态'
  }
}

const statusVisualClass = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'failed'
    case 'RUNNING':
      return 'running'
    case 'PENDING':
      return 'pending'
    default:
      return 'default'
  }
}

const isAnimatedStatus = (value?: string) => ['PENDING', 'RUNNING'].includes(String(value || '').toUpperCase())

const buildTaskStatusHint = (task: AiTaskPageItem) => {
  const status = String(task.status || '').toUpperCase()
  if (status === 'PENDING') {
    return '任务排队中；旧任务如果长时间不动，可以点“重新派发”'
  }
  if (status === 'RUNNING') {
    return '任务执行中，列表会自动刷新'
  }
  if (status === 'SUCCESS') {
    return task.finishedAt ? `已完成 · ${formatDateTime(task.finishedAt)}` : '已完成'
  }
  if (status === 'FAILED') {
    return task.errorMessage || '任务执行失败'
  }
  return '状态未知'
}

const formatBizLabel = (task: Pick<AiTaskPageItem, 'bizType' | 'bizId'>) => {
  const type = String(task.bizType || '').toUpperCase()
  if (type === 'MATERIAL') {
    return `资料 #${task.bizId || '--'}`
  }
  if (type === 'PRACTICE_SESSION') {
    return `练习 #${task.bizId || '--'}`
  }
  return task.bizId ? `${task.bizType || '业务'} #${task.bizId}` : task.bizType || '--'
}

const formatBizHint = (task: Pick<AiTaskPageItem, 'taskType' | 'bizType'>) => {
  const taskType = String(task.taskType || '').toUpperCase()
  const bizType = String(task.bizType || '').toUpperCase()
  if (taskType === 'SUMMARY' && bizType === 'MATERIAL') {
    return '总结生成结果会回到 AI 总结列表'
  }
  if (taskType === 'QUESTION_GENERATE' && bizType === 'MATERIAL') {
    return '题集生成结果会回到 AI 出题列表'
  }
  if (taskType === 'PRACTICE_REVIEW' && bizType === 'PRACTICE_SESSION') {
    return '简答题评分完成后可在练习记录查看'
  }
  if (taskType === 'EMBEDDING') {
    return '为后续 RAG 检索准备向量化数据'
  }
  return '统一由任务中心调度执行'
}

const canOpenBizPage = (task: Pick<AiTaskPageItem, 'taskType' | 'bizId'>) => Boolean(resolveBizRoute(task))

const resolveBizRoute = (task: Pick<AiTaskPageItem, 'taskType' | 'bizId'>) => {
  const bizId = task.bizId
  switch ((task.taskType || '').toUpperCase()) {
    case 'SUMMARY':
      return bizId ? { path: '/summary', query: { materialId: String(bizId) } } : { path: '/summary' }
    case 'QUESTION_GENERATE':
      return bizId ? { path: '/quiz', query: { materialId: String(bizId) } } : { path: '/quiz' }
    case 'PRACTICE_REVIEW':
      return bizId ? { path: '/practice', query: { sessionId: String(bizId) } } : { path: '/practice' }
    case 'EMBEDDING':
      return { path: '/materials' }
    default:
      return null
  }
}

const openBizPage = (task: Pick<AiTaskPageItem, 'taskType' | 'bizId'>) => {
  const route = resolveBizRoute(task)
  if (!route) {
    return
  }
  router.push(route)
}

const formatJsonContent = (value?: string) => {
  if (!value) {
    return '暂无内容'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

const loadTasks = async (silent = false) => {
  if (!silent) {
    loading.value = true
  }
  try {
    const res = await getAiTaskPageApi({
      current: page.current,
      size: page.size,
      taskType: filters.taskType || undefined,
      status: filters.status || undefined
    })
    const data = res.data.data as AiTaskPagePayload
    tasks.value = data.records || []
    total.value = data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载任务列表失败')
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

const searchTasks = () => {
  page.current = 1
  loadTasks()
}

const resetFilters = () => {
  filters.taskType = ''
  filters.status = ''
  page.current = 1
  loadTasks()
}

const openTaskDetail = async (taskId: number, silent = false) => {
  drawerVisible.value = true
  if (!silent) {
    detailLoading.value = true
  }
  try {
    const res = await getAiTaskDetailApi(taskId)
    taskDetail.value = res.data.data as AiTaskDetail
  } catch (error: any) {
    taskDetail.value = null
    ElMessage.error(error.message || '加载任务详情失败')
  } finally {
    if (!silent) {
      detailLoading.value = false
    }
  }
}

const retryTask = async (taskId: number, reloadDetail = false) => {
  retryingTaskId.value = taskId
  try {
    const res = await retryAiTaskApi(taskId)
    const retriedTask = res.data.data as AiTaskDetail
    ElMessage.success('任务已重新提交')
    await loadTasks()
    if (reloadDetail || taskDetail.value?.id === taskId) {
      taskDetail.value = retriedTask
      drawerVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '任务重试失败')
  } finally {
    retryingTaskId.value = null
  }
}

const dispatchTask = async (taskId: number, reloadDetail = false) => {
  dispatchingTaskId.value = taskId
  try {
    const res = await dispatchAiTaskApi(taskId)
    const dispatchedTask = res.data.data as AiTaskDetail
    ElMessage.success('任务已重新派发')
    await loadTasks()
    if (reloadDetail || taskDetail.value?.id === taskId) {
      taskDetail.value = dispatchedTask
      drawerVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '任务重新派发失败')
  } finally {
    dispatchingTaskId.value = null
  }
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer !== null) {
    window.clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  autoRefreshTimer = window.setInterval(() => {
    void loadTasks(true)
    if (drawerVisible.value && taskDetail.value && ['PENDING', 'RUNNING'].includes(String(taskDetail.value.status).toUpperCase())) {
      void openTaskDetail(taskDetail.value.id, true)
    }
  }, 3000)
}

watch(hasActiveTasks, (active) => {
  if (active) {
    startAutoRefresh()
    return
  }
  stopAutoRefresh()
})

onMounted(loadTasks)

onUnmounted(() => {
  stopAutoRefresh()
})
</script>
