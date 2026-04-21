<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">错题本</h1>
        <p class="page-desc">
          自动收集练习中的错题和低分简答题，支持按资料、题型和关键词筛选，并可联动知识点掌握度分析。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="loading" @click="loadWrongQuestions">刷新列表</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--wrong">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索题干 / 知识点 / 资料 / 练习名称"
            class="workspace-filter-bar__search"
          />
          <el-select
            v-model="filters.materialId"
            clearable
            filterable
            placeholder="资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · #${item.id}`"
              :value="item.id"
            />
          </el-select>
          <el-select v-model="filters.questionType" clearable placeholder="题型">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="简答题" value="SHORT" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
          <el-button @click="resetFilters">重置条件</el-button>
        </div>

        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">共 {{ total }} 道错题</span>
          <span class="workspace-chip workspace-chip--brand">当前页 {{ wrongQuestions.length }} 道</span>
        </div>
      </div>

      <div class="workspace-body">
        <div v-if="loading" class="state-block">正在加载错题本...</div>
        <div v-else-if="!wrongQuestions.length" class="state-block empty">
          当前条件下没有错题。练习提交后，系统会自动把错误题目和低分简答题收进这里。
        </div>
        <div v-else class="workspace-table">
          <div class="workspace-table__head workspace-table__head--wrong">
            <span>题目</span>
            <span>来源</span>
            <span>题型 / 难度</span>
            <span>得分</span>
            <span>答题时间</span>
            <span>操作</span>
          </div>

          <div
            v-for="item in wrongQuestions"
            :key="item.answerId"
            class="workspace-table__row workspace-table__row--wrong"
          >
            <div class="workspace-table__title workspace-table__title--truncate">
              <strong>{{ item.stemText }}</strong>
              <span>{{ item.knowledgePoint || '暂无知识点' }}</span>
            </div>
            <div class="task-meta-stack">
              <strong>{{ item.materialTitle || '未关联资料' }}</strong>
              <span>{{ item.sessionName || item.questionSetTitle || '未知练习' }}</span>
            </div>
            <div class="task-meta-stack">
              <strong>{{ formatQuestionType(item.questionType) }}</strong>
              <span>难度 {{ item.difficultyLevel || 3 }}</span>
            </div>
            <strong class="wrong-score">
              {{ item.obtainedScore ?? 0 }} / {{ item.fullScore || 0 }}
            </strong>
            <span>{{ formatDateTime(item.answerTime || item.createdAt) }}</span>
            <div class="workspace-action-row workspace-action-row--fit">
              <el-button link type="primary" @click="openDetail(item)">查看解析</el-button>
              <el-button link type="success" @click="goPractice(item.sessionId)">查看练习</el-button>
              <el-button
                link
                type="danger"
                :loading="removingAnswerId === item.answerId"
                @click="removeWrongQuestion(item)"
              >
                移出错题本
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
            @current-change="loadWrongQuestions"
            @size-change="loadWrongQuestions"
          />
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="错题解析" size="54%">
      <div v-if="detailLoading" class="state-block">正在加载错题详情...</div>
      <template v-else-if="detail">
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span>资料</span>
            <strong>{{ detail.materialTitle || '未关联资料' }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>练习</span>
            <strong>{{ detail.sessionName || '--' }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>题型</span>
            <strong>{{ formatQuestionType(detail.questionType) }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>得分</span>
            <strong>{{ detail.obtainedScore ?? 0 }} / {{ detail.fullScore || 0 }}</strong>
          </div>
        </div>

        <div class="wrong-detail-card">
          <div class="wrong-detail-card__label">题干</div>
          <div class="wrong-detail-card__stem">{{ detail.stemText }}</div>
          <div v-if="hasOptions(detail)" class="option-list">
            <span v-if="detail.optionA">A. {{ detail.optionA }}</span>
            <span v-if="detail.optionB">B. {{ detail.optionB }}</span>
            <span v-if="detail.optionC">C. {{ detail.optionC }}</span>
            <span v-if="detail.optionD">D. {{ detail.optionD }}</span>
          </div>
        </div>

        <div class="practice-answer-review" style="margin-top: 16px">
          <div class="practice-answer-review__item">
            <span>我的答案</span>
            <strong>{{ detail.userAnswer || '未作答' }}</strong>
          </div>
          <div class="practice-answer-review__item practice-answer-review__item--accent">
            <span>正确答案 / 人工参考答案</span>
            <strong>{{ detail.correctAnswer || '--' }}</strong>
          </div>
          <div class="practice-answer-review__item">
            <span>评分方式</span>
            <strong>{{ formatReviewMode(detail.reviewMode) }}</strong>
          </div>
        </div>

        <div v-if="detail.reviewComment" class="analysis-box">
          <strong>评语</strong>
          <p>{{ detail.reviewComment }}</p>
        </div>

        <div v-if="detail.answerAnalysis" class="analysis-box">
          <strong>答案解析</strong>
          <p>{{ detail.answerAnalysis }}</p>
        </div>

        <div v-if="detail.knowledgePoint" class="analysis-box">
          <strong>关联知识点</strong>
          <p>{{ detail.knowledgePoint }}</p>
        </div>

        <div class="task-detail-actions">
          <el-button type="success" @click="goPractice(detail.sessionId)">查看原练习</el-button>
          <el-button
            type="danger"
            plain
            :loading="removingAnswerId === detail.answerId"
            @click="removeWrongQuestion(detail, true)"
          >
            移出错题本
          </el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import {
  getWrongQuestionDetailApi,
  getWrongQuestionPageApi,
  removeWrongQuestionApi,
  type WrongQuestionItem,
  type WrongQuestionPagePayload
} from '@/api/modules/wrongQuestion'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const materialsLoading = ref(false)
const removingAnswerId = ref<number | null>(null)
const wrongQuestions = ref<WrongQuestionItem[]>([])
const materials = ref<MaterialPageItem[]>([])
const detail = ref<WrongQuestionItem | null>(null)
const total = ref(0)

const filters = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  materialId: typeof route.query.materialId === 'string' && Number(route.query.materialId)
    ? Number(route.query.materialId)
    : undefined as number | undefined,
  questionType: typeof route.query.questionType === 'string' ? route.query.questionType : ''
})

const page = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.materialId, () => filters.questionType],
  () => {
    page.current = 1
    return loadWrongQuestions()
  }
)

const formatDateTime = (value?: string) => {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ').slice(0, 19)
}

const formatQuestionType = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SINGLE':
      return '单选题'
    case 'JUDGE':
      return '判断题'
    case 'SHORT':
    case 'SHORT_ANSWER':
      return '简答题'
    default:
      return value || '未知题型'
  }
}

const formatReviewMode = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'AI':
      return 'AI 判分'
    case 'AI_PENDING':
      return 'AI 评分中'
    case 'RULE':
      return '规则判定'
    default:
      return value || '--'
  }
}

const hasOptions = (item: WrongQuestionItem) =>
  Boolean(item.optionA || item.optionB || item.optionC || item.optionD)

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

const loadWrongQuestions = async () => {
  loading.value = true
  try {
    const res = await getWrongQuestionPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword || undefined,
      materialId: filters.materialId || undefined,
      questionType: filters.questionType || undefined
    })
    const data = res.data.data as WrongQuestionPagePayload
    wrongQuestions.value = data.records || []
    total.value = data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载错题本失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.materialId = undefined
    filters.questionType = ''
    page.current = 1
  })
}

const openDetail = async (item: WrongQuestionItem) => {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getWrongQuestionDetailApi(item.answerId)
    detail.value = res.data.data as WrongQuestionItem
  } catch (error: any) {
    detail.value = null
    ElMessage.error(error.message || '加载错题详情失败')
  } finally {
    detailLoading.value = false
  }
}

const removeWrongQuestion = async (item: WrongQuestionItem, closeDrawer = false) => {
  try {
    await ElMessageBox.confirm('移出后这道题不会再显示在错题本中，原练习记录仍会保留，确定继续吗？', '移出确认', {
      type: 'warning',
      confirmButtonText: '确认移出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  removingAnswerId.value = item.answerId
  try {
    await removeWrongQuestionApi(item.answerId)
    ElMessage.success('已移出错题本')
    if (closeDrawer || detail.value?.answerId === item.answerId) {
      drawerVisible.value = false
      detail.value = null
    }
    if (wrongQuestions.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    await loadWrongQuestions()
  } catch (error: any) {
    ElMessage.error(error.message || '移出错题本失败')
  } finally {
    removingAnswerId.value = null
  }
}

const goPractice = (sessionId?: number) => {
  if (!sessionId) {
    return
  }
  router.push({ path: '/practice', query: { sessionId: String(sessionId) } })
}

void loadMaterials()
void loadWrongQuestions()
</script>
