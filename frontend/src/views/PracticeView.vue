<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">练习记录</h1>
        <p class="page-desc">补上重复练习和删除，让练习页也像正常后台记录页一样能管理历史。</p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="historyLoading" @click="loadHistory">刷新记录</el-button>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-tabs">
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'current' }"
            @click="activeTab = 'current'"
          >
            当前练习
          </button>
          <button
            type="button"
            class="workspace-tab"
            :class="{ 'workspace-tab--active': activeTab === 'history' }"
            @click="activeTab = 'history'"
          >
            历史练习
          </button>
        </div>

        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">共 {{ total }} 条记录</span>
          <span v-if="practiceDetail" class="workspace-chip workspace-chip--brand">
            {{ practiceDetail.sessionStatus }}
          </span>
        </div>
      </div>

      <div v-if="activeTab === 'history'" class="workspace-body">
        <div class="page-card workspace-card">
          <div class="workspace-table" v-if="historyRecords.length">
            <div class="workspace-table__head workspace-table__head--practice">
              <span>练习名称</span>
              <span>状态</span>
              <span>正确率</span>
              <span>得分</span>
              <span>操作</span>
            </div>

            <div
              v-for="item in historyRecords"
              :key="item.id"
              class="workspace-table__row workspace-table__row--practice"
            >
              <div class="workspace-table__title workspace-table__title--truncate">
                <strong :title="item.sessionName">{{ item.sessionName }}</strong>
                <span>#{{ item.id }} · {{ item.correctCount || 0 }}/{{ item.totalQuestions || 0 }} 题正确</span>
              </div>
              <span>{{ item.sessionStatus }}</span>
              <span>{{ Number(item.accuracyRate || 0).toFixed(0) }}%</span>
              <span>{{ item.obtainedScore || 0 }}</span>
              <div class="workspace-action-row workspace-action-row--fit">
                <el-button link type="primary" @click="openPracticeFromHistory(item.id)">查看</el-button>
                <el-button
                  link
                  type="success"
                  @click="restartPractice(item.questionSetId || practiceDetail?.questionSetId)"
                >
                  再练一次
                </el-button>
                <el-button
                  link
                  type="danger"
                  :loading="actionLoadingId === item.id"
                  @click="removePractice(item.id)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>
          <div class="workspace-pagination" v-if="historyRecords.length">
            <div class="workspace-pagination__meta">第 {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页</div>
            <el-pagination
              v-model:current-page="page.current"
              v-model:page-size="page.size"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="total"
              @current-change="loadHistory"
              @size-change="loadHistory"
            />
          </div>
          <div v-else-if="historyLoading" class="state-block">正在加载练习记录...</div>
          <div v-else class="state-block empty">暂无练习记录，先去 AI 出题页面生成一套题目吧。</div>
        </div>
      </div>

      <div v-else class="workspace-body">
        <div v-if="detailLoading" class="page-card workspace-card">
          <div class="state-block">正在加载练习详情...</div>
        </div>

        <template v-else-if="practiceDetail">
          <div class="page-card workspace-card">
            <div class="workspace-card__header workspace-card__header--spaced">
              <div>
                <h3>{{ practiceDetail.sessionName }}</h3>
                <p>
                  {{ practiceDetail.sessionStatus === 'SUBMITTED'
                    ? '这次练习已经提交，可以回看 AI 判分、参考答案和解析；如果想重新做一遍，直接点“再练一次”。'
                    : '当前是进行中的练习，填写完成后直接提交。'
                  }}
                </p>
              </div>
              <div class="workspace-toolbar__meta">
                <span class="workspace-chip">题目 {{ practiceDetail.totalQuestions }}</span>
                <span class="workspace-chip">得分 {{ practiceDetail.obtainedScore || 0 }}</span>
                <span class="workspace-chip workspace-chip--brand">
                  正确率 {{ Number(practiceDetail.accuracyRate || 0).toFixed(0) }}%
                </span>
              </div>
            </div>
          </div>

          <div class="practice-question-list">
            <div
              v-for="(answer, index) in practiceDetail.answers"
              :key="answer.questionId"
              class="page-card practice-question-card practice-question-card--wide"
            >
              <div class="practice-question-card__head">
                <div>
                  <div class="practice-question-card__index">第 {{ index + 1 }} 题</div>
                  <div class="section-card__title">{{ answer.stemText }}</div>
                </div>
                <div class="practice-question-card__type">{{ getQuestionTypeLabel(answer.questionType) }}</div>
              </div>

              <template v-if="isChoiceQuestion(answer)">
                <el-radio-group
                  v-model="answerForm[answer.questionId]"
                  class="choice-group choice-group--cards"
                  :disabled="practiceDetail.sessionStatus === 'SUBMITTED'"
                >
                  <el-radio
                    v-for="option in buildOptions(answer)"
                    :key="option.value"
                    :label="option.value"
                    border
                    class="choice-option"
                  >
                    {{ option.label }}
                  </el-radio>
                </el-radio-group>
              </template>

              <template v-else>
                <el-input
                  v-model="answerForm[answer.questionId]"
                  :disabled="practiceDetail.sessionStatus === 'SUBMITTED'"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入你的答案"
                />
              </template>

              <div v-if="practiceDetail.sessionStatus === 'SUBMITTED'" class="analysis-box practice-answer-review">
                <div class="practice-answer-review__item">
                  <span>你的答案</span>
                  <strong>{{ formatAnswer(answer, answer.userAnswer) }}</strong>
                </div>

                <template v-if="isShortQuestion(answer)">
                  <div class="practice-answer-review__item practice-answer-review__item--accent">
                    <span>AI 判分</span>
                    <strong>{{ answer.reviewLabel || `AI 判分：${answer.aiScore || 0}` }}</strong>
                  </div>
                  <div class="practice-answer-review__item">
                    <span>人工参考答案</span>
                    <strong>{{ answer.referenceAnswer || '暂无参考答案' }}</strong>
                  </div>
                  <div class="practice-answer-review__item practice-answer-review__item--full">
                    <span>AI 评语</span>
                    <strong>{{ answer.reviewComment || 'AI 暂未返回评语' }}</strong>
                  </div>
                </template>

                <template v-else>
                  <div class="practice-answer-review__item">
                    <span>参考答案</span>
                    <strong>{{ formatAnswer(answer, answer.referenceAnswer || answer.correctAnswer) }}</strong>
                  </div>
                  <div class="practice-answer-review__item">
                    <span>判定结果</span>
                    <strong>{{ answer.isCorrect ? '正确' : '错误' }}</strong>
                  </div>
                </template>

                <div class="practice-answer-review__item practice-answer-review__item--full">
                  <span>解析</span>
                  <strong>{{ answer.answerAnalysis || '暂无解析' }}</strong>
                </div>
              </div>
            </div>
          </div>

          <div class="page-card workspace-card">
            <div class="workspace-card__header">
              <div>
                <h3>本次操作</h3>
                <p>提交、再练和删除都统一放在答题区最下方，避免和题目内容抢视觉重心。</p>
              </div>
            </div>
            <div class="toolbar" style="margin-bottom: 0">
              <el-button
                v-if="practiceDetail.sessionStatus !== 'SUBMITTED'"
                type="primary"
                :loading="submitting"
                @click="submitPractice"
              >
                提交练习
              </el-button>
              <el-button type="success" @click="restartPractice(practiceDetail.questionSetId)">再练一次</el-button>
              <el-button
                type="danger"
                plain
                :loading="actionLoadingId === practiceDetail.sessionId"
                @click="removePractice(practiceDetail.sessionId, true)"
              >
                删除本次练习
              </el-button>
            </div>
          </div>
        </template>

        <div v-else class="page-card workspace-card">
          <div class="state-block empty">先到“历史练习”里点一条记录，这里再展示完整答题内容。</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deletePracticeApi, getPracticeDetailApi, getPracticePageApi, startPracticeApi, submitPracticeApi } from '@/api/modules/practice'

const route = useRoute()
const router = useRouter()
const historyRecords = ref<any[]>([])
const total = ref(0)
const practiceDetail = ref<any>(null)
const answerForm = ref<Record<number, string>>({})
const historyLoading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const activeTab = ref<'current' | 'history'>('current')
const actionLoadingId = ref<number | null>(null)
const page = reactive({
  current: 1,
  size: 10
})

const syncAnswerForm = () => {
  const form: Record<number, string> = {}
  ;(practiceDetail.value?.answers || []).forEach((item: any) => {
    form[item.questionId] = item.userAnswer || ''
  })
  answerForm.value = form
}

const getQuestionTypeLabel = (type?: string) => {
  switch ((type || '').toUpperCase()) {
    case 'SINGLE':
      return '单选题'
    case 'JUDGE':
      return '判断题'
    case 'SHORT':
      return '简答题'
    default:
      return type || '题目'
  }
}

const isChoiceQuestion = (answer: any) => ['SINGLE', 'JUDGE'].includes(answer.questionType)

const isShortQuestion = (answer: any) => String(answer.questionType || '').toUpperCase() === 'SHORT'

const isOptionItem = (item: any): item is { value: string; label: string } => Boolean(item)

const buildOptions = (answer: any) => {
  if (answer.questionType === 'JUDGE') {
    return [
      { value: '正确', label: '正确' },
      { value: '错误', label: '错误' }
    ]
  }

  return [
    answer.optionA ? { value: 'A', label: `A. ${answer.optionA}` } : null,
    answer.optionB ? { value: 'B', label: `B. ${answer.optionB}` } : null,
    answer.optionC ? { value: 'C', label: `C. ${answer.optionC}` } : null,
    answer.optionD ? { value: 'D', label: `D. ${answer.optionD}` } : null
  ].filter(isOptionItem)
}

const formatAnswer = (answer: any, value?: string) => {
  if (!value) {
    return '未作答'
  }
  if (answer.questionType === 'SINGLE') {
    const option = buildOptions(answer).find((item: any) => item.value === value)
    return option ? option.label : value
  }
  return value
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getPracticePageApi({ current: page.current, size: page.size })
    historyRecords.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载练习记录失败')
  } finally {
    historyLoading.value = false
  }
}

const loadPracticeDetail = async (sessionId: number) => {
  detailLoading.value = true
  try {
    const res = await getPracticeDetailApi(sessionId)
    practiceDetail.value = res.data.data
    syncAnswerForm()
    router.replace({ path: '/practice', query: { sessionId: String(sessionId) } })
  } catch (error: any) {
    practiceDetail.value = null
    ElMessage.error(error.message || '加载练习详情失败')
  } finally {
    detailLoading.value = false
  }
}

const openPracticeFromHistory = async (sessionId: number) => {
  await loadPracticeDetail(sessionId)
  activeTab.value = 'current'
}

const restartPractice = async (questionSetId?: number) => {
  if (!questionSetId) {
    ElMessage.warning('当前缺少题集信息，无法重新练习')
    return
  }
  try {
    const res = await startPracticeApi(questionSetId)
    ElMessage.success('新的练习已开始')
    page.current = 1
    await loadHistory()
    await loadPracticeDetail(res.data.data.sessionId)
    activeTab.value = 'current'
  } catch (error: any) {
    ElMessage.error(error.message || '重新开始练习失败')
  }
}

const removePractice = async (sessionId: number, clearCurrent = false) => {
  try {
    await ElMessageBox.confirm('删除后这次练习记录和答案将不可恢复，确定继续吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  actionLoadingId.value = sessionId
  try {
    await deletePracticeApi(sessionId)
    ElMessage.success('练习记录已删除')
    if (clearCurrent && practiceDetail.value?.sessionId === sessionId) {
      practiceDetail.value = null
      router.replace({ path: '/practice' })
      activeTab.value = 'history'
    }
    if (!clearCurrent && historyRecords.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    await loadHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '删除练习失败')
  } finally {
    actionLoadingId.value = null
  }
}

const submitPractice = async () => {
  if (!practiceDetail.value?.sessionId) {
    return
  }

  submitting.value = true
  try {
    const answers = (practiceDetail.value.answers || []).map((item: any) => ({
      questionId: item.questionId,
      userAnswer: answerForm.value[item.questionId] || ''
    }))

    const res = await submitPracticeApi(practiceDetail.value.sessionId, answers)
    practiceDetail.value = res.data.data
    syncAnswerForm()
    ElMessage.success('练习提交成功')
    await loadHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '提交练习失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadHistory()
  const sessionId = Number(route.query.sessionId)
  if (sessionId) {
    await loadPracticeDetail(sessionId)
    activeTab.value = 'current'
  } else if (historyRecords.value.length) {
    activeTab.value = 'history'
  }
})
</script>
