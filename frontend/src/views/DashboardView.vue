<template>
  <section>
    <div class="page-card dashboard-hero">
      <div class="dashboard-hero__content">
        <div>
          <div class="dashboard-hero__eyebrow">Learning Workspace</div>
          <h1 class="page-title">学习概览</h1>
          <p class="page-desc">
            先录入并解析资料，再进入 AI 总结和 AI 出题，最后回到练习记录查看 AI 判分和练习结果。
          </p>
        </div>
        <div class="toolbar" style="margin-bottom: 0">
          <el-button @click="loadDashboard" :loading="loading">刷新数据</el-button>
          <el-button type="primary" @click="router.push('/materials')">新增资料</el-button>
        </div>
      </div>

      <div class="dashboard-shortcuts">
        <button type="button" class="dashboard-shortcut" @click="router.push('/materials')">
          <span>资料管理</span>
          <strong>去录入资料</strong>
          <em>上传、解析、整理学习材料</em>
        </button>
        <button type="button" class="dashboard-shortcut" @click="router.push('/summary')">
          <span>AI 总结</span>
          <strong>查看总结列表</strong>
          <em>直接管理历史总结与生成记录</em>
        </button>
        <button type="button" class="dashboard-shortcut" @click="router.push('/quiz')">
          <span>AI 出题</span>
          <strong>生成题集</strong>
          <em>出题后可继续进入练习记录</em>
        </button>
      </div>
    </div>

    <div class="summary-overview-grid" style="margin-top: 20px">
      <div v-for="item in statCards" :key="item.label" class="summary-overview-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <em>{{ item.footnote }}</em>
      </div>
    </div>

    <div class="workspace-panel" style="margin-top: 20px">
      <div class="workspace-toolbar">
        <div class="workspace-tabs">
          <button type="button" class="workspace-tab workspace-tab--active">概览面板</button>
        </div>
        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">资料 {{ materialRecords.length }} 份</span>
          <span class="workspace-chip">题集 {{ questionSetRecords.length }} 套</span>
          <span class="workspace-chip workspace-chip--brand">练习 {{ practiceRecords.length }} 次</span>
        </div>
      </div>

      <div class="workspace-body">
        <div class="overview-grid">
          <div class="page-card workspace-card">
            <div class="workspace-card__header workspace-card__header--spaced">
              <div>
                <h3>最近资料</h3>
                <p>最近录入和解析的资料会出现在这里，方便继续去做总结或出题。</p>
              </div>
              <el-button link type="primary" @click="router.push('/materials')">前往管理</el-button>
            </div>
            <div v-if="loading" class="state-block">正在加载资料概况...</div>
            <div v-else-if="!materialRecords.length" class="state-block empty">还没有资料，先录入一份课堂笔记试试。</div>
            <div v-else class="list-stack">
              <div v-for="item in materialRecords" :key="item.id" class="list-row list-row--action">
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

          <div class="page-card workspace-card">
            <div class="workspace-card__header workspace-card__header--spaced">
              <div>
                <h3>题集与练习</h3>
                <p>查看最近练习成绩，也可以快速跳到题集页继续出题。</p>
              </div>
              <el-button link type="primary" @click="router.push('/quiz')">立即出题</el-button>
            </div>
            <div v-if="loading" class="state-block">正在整理练习数据...</div>
            <template v-else>
              <div class="summary-metrics">
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
              <div v-if="practiceRecords.length" class="list-stack" style="margin-top: 18px">
                <div v-for="item in practiceRecords" :key="item.id" class="list-row list-row--action">
                  <div>
                    <div class="list-row__title">{{ item.sessionName }}</div>
                    <div class="list-row__meta">
                      {{ formatTag(item.sessionStatus) }} · {{ item.correctCount || 0 }}/{{ item.totalQuestions || 0 }} 题答对
                    </div>
                  </div>
                  <div class="list-row__aside">{{ item.obtainedScore || 0 }} 分</div>
                </div>
              </div>
              <div v-else class="state-block empty" style="margin-top: 18px">还没有练习记录，生成题集后就可以开始刷题。</div>
            </template>
          </div>
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
