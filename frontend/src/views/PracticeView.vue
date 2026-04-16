<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">练习记录</h1>
        <p class="page-desc">
          查看历史练习记录，继续完成未提交的题目，并在提交后查看正确率、得分和答案解析。
        </p>
      </div>
      <el-button :loading="historyLoading" @click="loadHistory">刷新记录</el-button>
    </div>

    <div class="content-grid content-grid--2">
      <div class="page-card page-card--fit">
        <div class="panel-title-row">
          <h3>历史练习</h3>
          <span class="soft-text">共 {{ historyRecords.length }} 条</span>
        </div>
        <div v-if="historyLoading" class="state-block">正在加载练习记录...</div>
        <div v-else-if="!historyRecords.length" class="state-block empty">暂无练习记录，先去生成题集开始练习。</div>
        <div v-else class="list-stack">
          <div v-for="item in historyRecords" :key="item.id" class="list-row list-row--action">
            <div>
              <div class="list-row__title">{{ item.sessionName }}</div>
              <div class="list-row__meta">
                {{ item.sessionStatus }} · {{ item.correctCount || 0 }}/{{ item.totalQuestions || 0 }} 题 ·
                {{ Number(item.accuracyRate || 0).toFixed(0) }}%
              </div>
            </div>
            <div class="toolbar" style="margin-bottom: 0">
              <el-button link @click="loadPracticeDetail(item.id)">查看</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="page-card">
        <div class="panel-title-row">
          <h3>当前练习</h3>
        </div>
        <div v-if="detailLoading" class="state-block">正在加载练习详情...</div>
        <div v-else-if="practiceDetail" class="preview-stack">
          <div class="detail-meta-grid">
            <div class="detail-meta-item">
              <span>练习名称</span>
              <strong>{{ practiceDetail.sessionName }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>题目数量</span>
              <strong>{{ practiceDetail.totalQuestions }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>当前状态</span>
              <strong>{{ practiceDetail.sessionStatus }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>已得分</span>
              <strong>{{ practiceDetail.obtainedScore || 0 }}</strong>
            </div>
          </div>

          <div class="list-stack">
            <div v-for="(answer, index) in practiceDetail.answers" :key="answer.questionId" class="section-card">
              <div class="section-card__title">{{ index + 1 }}. {{ answer.stemText }}</div>

              <template v-if="isChoiceQuestion(answer)">
                <el-radio-group
                  v-model="answerForm[answer.questionId]"
                  class="choice-group"
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
                  :rows="3"
                  placeholder="请输入你的答案"
                />
              </template>

              <div v-if="practiceDetail.sessionStatus === 'SUBMITTED'" class="analysis-box" style="margin-top: 12px">
                <div><strong>你的答案：</strong>{{ formatAnswer(answer, answer.userAnswer) }}</div>
                <div><strong>参考答案：</strong>{{ formatAnswer(answer, answer.correctAnswer) }}</div>
                <div><strong>判定结果：</strong>{{ answer.isCorrect ? '正确' : '错误' }}</div>
                <div><strong>解析：</strong>{{ answer.answerAnalysis || '暂无解析' }}</div>
              </div>
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
            <div v-else class="result-banner">
              正确率 {{ Number(practiceDetail.accuracyRate || 0).toFixed(0) }}%，共得 {{ practiceDetail.obtainedScore || 0 }} 分
            </div>
          </div>
        </div>
        <div v-else class="state-block empty">选择一条练习记录后，这里会显示答题详情。</div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getPracticeDetailApi, getPracticePageApi, submitPracticeApi } from '@/api/modules/practice'

const route = useRoute()
const router = useRouter()
const historyRecords = ref<any[]>([])
const practiceDetail = ref<any>(null)
const answerForm = ref<Record<number, string>>({})
const historyLoading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)

const syncAnswerForm = () => {
  const form: Record<number, string> = {}
  ;(practiceDetail.value?.answers || []).forEach((item: any) => {
    form[item.questionId] = item.userAnswer || ''
  })
  answerForm.value = form
}

const isChoiceQuestion = (answer: any) => ['SINGLE', 'JUDGE'].includes(answer.questionType)

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
    const res = await getPracticePageApi({ current: 1, size: 20 })
    historyRecords.value = res.data.data.records || []
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
  } else if (historyRecords.value.length) {
    await loadPracticeDetail(historyRecords.value[0].id)
  }
})
</script>
