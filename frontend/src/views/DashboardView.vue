<template>
  <section class="dashboard-container">
    <div class="dashboard-mobile-hero">
      <div class="dashboard-mobile-hero__copy">
        <p>{{ greetingName }}，你好</p>
        <h1>今天想学点什么？</h1>
      </div>
      <div class="dashboard-mobile-hero__questions">
        <button type="button" @click="router.push('/materials')">上传一份资料并整理重点</button>
        <button type="button" @click="router.push('/summary')">帮我生成今天的复习总结</button>
      </div>
      <div class="dashboard-mobile-hero__chips">
        <button type="button" @click="router.push('/materials')">资料管理</button>
        <button type="button" @click="router.push('/summary')">AI 总结</button>
        <button type="button" @click="router.push('/quiz')">AI 出题</button>
        <button type="button" @click="router.push('/practice')">练习记录</button>
      </div>
    </div>

    <div class="dashboard-header">
      <div class="dashboard-header__content">
        <h1 class="dashboard-title">学习概览</h1>
        <p class="dashboard-subtitle">智能学习助手，让学习更高效</p>
      </div>
      <div class="dashboard-header__actions">
        <el-button @click="loadDashboard" :loading="loading" :icon="loading ? '' : 'Refresh'">刷新</el-button>
        <el-button type="primary" @click="router.push('/materials')">新增资料</el-button>
      </div>
    </div>

    <div class="dashboard-stats">
      <div v-for="item in statCards" :key="item.label" class="stat-card-modern">
        <div class="stat-card-modern__icon" :class="`stat-card-modern__icon--${item.color}`">
          <AppIcon :name="item.icon" :size="24" />
        </div>
        <div class="stat-card-modern__content">
          <div class="stat-card-modern__label">{{ item.label }}</div>
          <div class="stat-card-modern__value">{{ item.value }}</div>
          <div class="stat-card-modern__footnote">{{ item.footnote }}</div>
        </div>
      </div>
    </div>

    <div class="dashboard-quick-actions">
      <button class="quick-action-card" @click="router.push('/materials')">
        <div class="quick-action-card__icon">
          <AppIcon name="materials" :size="28" />
        </div>
        <div class="quick-action-card__content">
          <h3>资料管理</h3>
          <p>上传、解析、整理学习材料</p>
        </div>
      </button>
      <button class="quick-action-card" @click="router.push('/summary')">
        <div class="quick-action-card__icon">
          <AppIcon name="summary" :size="28" />
        </div>
        <div class="quick-action-card__content">
          <h3>AI 总结</h3>
          <p>智能生成学习笔记</p>
        </div>
      </button>
      <button class="quick-action-card" @click="router.push('/quiz')">
        <div class="quick-action-card__icon">
          <AppIcon name="quiz" :size="28" />
        </div>
        <div class="quick-action-card__content">
          <h3>AI 出题</h3>
          <p>自动生成练习题集</p>
        </div>
      </button>
      <button class="quick-action-card" @click="router.push('/practice')">
        <div class="quick-action-card__icon">
          <AppIcon name="practice" :size="28" />
        </div>
        <div class="quick-action-card__content">
          <h3>练习记录</h3>
          <p>查看练习历史与成绩</p>
        </div>
      </button>
    </div>

    <div class="dashboard-content">
      <div class="dashboard-section">
        <div class="section-header">
          <h2>最近资料</h2>
          <el-button link type="primary" @click="router.push('/materials')">查看全部</el-button>
        </div>
        <div v-if="loading" class="section-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <div v-else-if="!materialRecords.length" class="section-empty">
          <AppIcon name="materials" :size="48" />
          <p>还没有资料</p>
          <el-button type="primary" @click="router.push('/materials')">立即添加</el-button>
        </div>
        <div v-else class="material-list">
          <div v-for="item in materialRecords" :key="item.id" class="material-item">
            <div class="material-item__icon">
              <AppIcon name="materials" :size="20" />
            </div>
            <div class="material-item__content">
              <div class="material-item__title">{{ item.title }}</div>
              <div class="material-item__meta">
                {{ formatTag(item.materialType) }} · {{ item.totalCharacters || 0 }} 字
              </div>
            </div>
            <div class="material-item__status" :class="`material-item__status--${getStatusColor(item.parseStatus)}`">
              {{ formatTag(item.parseStatus) }}
            </div>
          </div>
        </div>
      </div>

      <div class="dashboard-section">
        <div class="section-header">
          <h2>最近练习</h2>
          <el-button link type="primary" @click="router.push('/practice')">查看全部</el-button>
        </div>
        <div v-if="loading" class="section-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <div v-else-if="!practiceRecords.length" class="section-empty">
          <AppIcon name="practice" :size="48" />
          <p>还没有练习记录</p>
          <el-button type="primary" @click="router.push('/quiz')">开始练习</el-button>
        </div>
        <div v-else class="practice-list">
          <div v-for="item in practiceRecords" :key="item.id" class="practice-item">
            <div class="practice-item__icon">
              <AppIcon name="practice" :size="20" />
            </div>
            <div class="practice-item__content">
              <div class="practice-item__title">{{ item.sessionName }}</div>
              <div class="practice-item__meta">
                {{ item.correctCount || 0 }}/{{ item.totalQuestions || 0 }} 题正确
              </div>
            </div>
            <div class="practice-item__score">{{ item.obtainedScore || 0 }} 分</div>
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
import { Loading } from '@element-plus/icons-vue'
import AppIcon from '@/components/AppIcon.vue'
import { getMaterialPageApi } from '@/api/modules/material'
import { getPracticePageApi } from '@/api/modules/practice'
import { getQuestionSetPageApi } from '@/api/modules/question'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const materialRecords = ref<any[]>([])
const questionSetRecords = ref<any[]>([])
const practiceRecords = ref<any[]>([])

const greetingName = computed(() =>
  userStore.profile?.nickname || userStore.profile?.username || '同学'
)

const statCards = computed(() => {
  const parsedCount = materialRecords.value.filter((item) => item.parseStatus === 'SUCCESS').length
  const summarizedCount = materialRecords.value.filter((item) => item.summaryStatus === 'SUCCESS').length
  const latestPractice = practiceRecords.value[0]

  return [
    {
      label: '资料总数',
      value: materialRecords.value.length,
      footnote: `已解析 ${parsedCount} 份`,
      icon: 'materials',
      color: 'blue'
    },
    {
      label: 'AI 总结',
      value: summarizedCount,
      footnote: '沉淀为复习笔记',
      icon: 'summary',
      color: 'green'
    },
    {
      label: '题集数量',
      value: questionSetRecords.value.length,
      footnote: '支持继续练习',
      icon: 'quiz',
      color: 'purple'
    },
    {
      label: '最新得分',
      value: latestPractice ? `${latestPractice.obtainedScore || 0}` : '--',
      footnote: latestPractice
        ? `${latestPractice.correctCount || 0}/${latestPractice.totalQuestions || 0} 题`
        : '暂无练习记录',
      icon: 'practice',
      color: 'orange'
    }
  ]
})

const formatTag = (value?: string) => {
  if (!value) return '未知'
  return value.split('_').join(' ')
}

const getStatusColor = (status?: string) => {
  if (!status) return 'default'
  const upperStatus = status.toUpperCase()
  if (upperStatus === 'SUCCESS') return 'success'
  if (upperStatus === 'FAILED') return 'error'
  if (upperStatus === 'PENDING' || upperStatus === 'PROCESSING') return 'warning'
  return 'default'
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
