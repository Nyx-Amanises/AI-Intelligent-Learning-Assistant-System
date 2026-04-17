<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">学习概览</h1>
        <p class="page-desc">
          建议流程：先录入资料并完成解析，再去 AI 总结和 AI 出题，最后回到练习记录查看结果。
        </p>
        <div class="inline-actions">
          <el-button link type="primary" @click="router.push('/materials')">去录入资料</el-button>
          <el-button link type="primary" @click="router.push('/summary')">去生成总结</el-button>
          <el-button link type="primary" @click="router.push('/quiz')">去智能出题</el-button>
        </div>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="loading" @click="loadDashboard">刷新数据</el-button>
        <el-button type="primary" @click="router.push('/materials')">新增资料</el-button>
      </div>
    </div>

    <div class="stats-grid">
      <div v-for="item in statCards" :key="item.label" class="stat-card">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-footnote">{{ item.footnote }}</div>
      </div>
    </div>

    <div class="overview-grid">
      <div class="page-card">
        <div class="panel-title-row">
          <h3>最近资料</h3>
          <el-button link type="primary" @click="router.push('/materials')">前往管理</el-button>
        </div>
        <div v-if="loading" class="state-block">正在加载资料概况...</div>
        <div v-else-if="!materialRecords.length" class="state-block empty">还没有资料，先录入一份课堂笔记试试。</div>
        <div v-else class="list-stack">
          <div v-for="item in materialRecords" :key="item.id" class="list-row">
            <div>
              <div class="list-row__title">{{ item.title }}</div>
              <div class="list-row__meta">
                {{ formatTag(item.materialType) }} · 解析 {{ formatTag(item.parseStatus) }} · 总结
                {{ formatTag(item.summaryStatus) }}
              </div>
            </div>
            <div class="list-row__aside">{{ item.totalCharacters || 0 }} 字</div>
          </div>
        </div>
      </div>

      <div class="page-card">
        <div class="panel-title-row">
          <h3>题集与练习</h3>
          <el-button link type="primary" @click="router.push('/quiz')">立即出题</el-button>
        </div>
        <div v-if="loading" class="state-block">正在整理练习数据...</div>
        <div v-else class="summary-metrics">
          <div class="summary-metric">
            <span>题集数量</span>
            <strong>{{ questionSetRecords.length }}</strong>
          </div>
          <div class="summary-metric">
            <span>练习次数</span>
            <strong>{{ practiceRecords.length }}</strong>
          </div>
          <div class="summary-metric">
            <span>最近正确率</span>
            <strong>{{ latestAccuracy }}</strong>
          </div>
        </div>
        <div v-if="!loading && practiceRecords.length" class="list-stack" style="margin-top: 18px">
          <div v-for="item in practiceRecords" :key="item.id" class="list-row">
            <div>
              <div class="list-row__title">{{ item.sessionName }}</div>
              <div class="list-row__meta">
                {{ formatTag(item.sessionStatus) }} · {{ item.correctCount || 0 }}/{{ item.totalQuestions || 0 }} 题答对
              </div>
            </div>
            <div class="list-row__aside">{{ item.obtainedScore || 0 }} 分</div>
          </div>
        </div>
        <div v-else-if="!loading" class="state-block empty" style="margin-top: 18px">
          还没有练习记录，生成题集后就可以开始刷题。
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getMaterialPageApi } from '@/api/modules/material'
import { getPracticePageApi } from '@/api/modules/practice'
import { getQuestionSetPageApi } from '@/api/modules/question'

const router = useRouter()
const loading = ref(false)
const materialRecords = ref<any[]>([])
const questionSetRecords = ref<any[]>([])
const practiceRecords = ref<any[]>([])

const statCards = computed(() => {
  const parsedCount = materialRecords.value.filter((item) => item.parseStatus === 'SUCCESS').length
  const summarizedCount = materialRecords.value.filter((item) => item.summaryStatus === 'SUCCESS').length
  const latestPractice = practiceRecords.value[0]

  return [
    {
      label: '资料总数',
      value: String(materialRecords.value.length).padStart(2, '0'),
      footnote: `已解析 ${parsedCount} 份`
    },
    {
      label: 'AI 总结',
      value: String(summarizedCount).padStart(2, '0'),
      footnote: '沉淀为复习笔记'
    },
    {
      label: '题集数量',
      value: String(questionSetRecords.value.length).padStart(2, '0'),
      footnote: '支持继续练习'
    },
    {
      label: '最新得分',
      value: latestPractice ? `${latestPractice.obtainedScore || 0}` : '--',
      footnote: latestPractice
        ? `${latestPractice.correctCount || 0}/${latestPractice.totalQuestions || 0} 题`
        : '暂无练习记录'
    }
  ]
})

const latestAccuracy = computed(() => {
  const latestPractice = practiceRecords.value[0]
  if (!latestPractice) {
    return '--'
  }
  return `${Number(latestPractice.accuracyRate || 0).toFixed(0)}%`
})

const formatTag = (value?: string) => {
  if (!value) {
    return '未知'
  }
  return value.split('_').join(' ')
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const [materialRes, questionRes, practiceRes] = await Promise.all([
      getMaterialPageApi({ current: 1, size: 5 }),
      getQuestionSetPageApi({ current: 1, size: 5 }),
      getPracticePageApi({ current: 1, size: 5 })
    ])

    materialRecords.value = materialRes.data.data.records || []
    questionSetRecords.value = questionRes.data.data.records || []
    practiceRecords.value = practiceRes.data.data.records || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载概览数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>
