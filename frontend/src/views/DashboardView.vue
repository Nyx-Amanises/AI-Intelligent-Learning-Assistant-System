<template>
  <section class="dashboard-container dashboard-container--redesign">
    <div class="dashboard-header dashboard-header--workspace">
      <div class="dashboard-header__content">
        <h1 class="dashboard-title">学习概览</h1>
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

    <div class="dashboard-advice-strip">
      <div class="dashboard-advice-strip__lead">
        <span class="dashboard-advice-strip__icon">
          <AppIcon name="mastery" :size="38" />
        </span>
        <div>
          <strong>今日学习建议</strong>
          <p>{{ primaryAdvice }}</p>
        </div>
      </div>
      <div class="dashboard-advice-strip__actions">
        <div v-for="item in actionCards" :key="item.title" class="dashboard-advice-task">
          <span :class="`dashboard-advice-task__icon dashboard-advice-task__icon--${item.color}`">
            <AppIcon :name="item.icon" :size="19" />
          </span>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.desc }}</p>
          </div>
        </div>
        <button type="button" class="dashboard-outline-button" @click="router.push('/ai-tasks')">
          查看全部任务
        </button>
      </div>
    </div>

    <div class="dashboard-workbench">
      <div class="dashboard-main-column">
      <section class="dashboard-section dashboard-section--materials">
        <div class="section-header section-header--panel">
          <div>
            <h2>最近资料</h2>
          </div>
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
        <div v-else class="dashboard-table dashboard-table--materials">
          <div class="dashboard-table__head">
            <span>资料名称</span>
            <span>类型</span>
            <span>来源</span>
            <span>更新时间</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          <div v-for="item in materialRecords" :key="item.id" class="dashboard-table__row material-item">
            <div class="dashboard-table__title">
              <AppIcon :name="getMaterialIcon(item)" :size="22" />
              <strong>{{ item.title }}</strong>
            </div>
            <span class="file-type-badge" :class="`file-type-badge--${getMaterialBadge(item)}`">
              {{ formatMaterialBadge(item) }}
            </span>
            <span>本地上传</span>
            <span>{{ formatDateTime(item.updatedAt || item.createTime || item.createdAt) }}</span>
            <span class="material-item__status" :class="`material-item__status--${getStatusColor(item.parseStatus)}`">
              {{ formatStatusLabel(item.parseStatus) }}
            </span>
            <button type="button" class="table-action-button" aria-label="更多操作">...</button>
          </div>
        </div>
      </section>

      <section class="dashboard-section dashboard-section--practice">
        <div class="section-header section-header--panel">
          <div>
            <h2>最近练习</h2>
          </div>
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
        <div v-else class="dashboard-table dashboard-table--practice">
          <div class="dashboard-table__head">
            <span>练习名称</span>
            <span>题量</span>
            <span>正确率</span>
            <span>得分</span>
            <span>完成时间</span>
            <span>操作</span>
          </div>
          <div v-for="item in practiceRecords" :key="item.id" class="dashboard-table__row practice-item">
            <div class="dashboard-table__title">
              <AppIcon name="practice" :size="20" />
              <strong>{{ item.sessionName }}</strong>
            </div>
            <span>{{ item.totalQuestions || 0 }}</span>
            <span :class="getAccuracyClass(item)">{{ getPracticeRate(item) }}</span>
            <span>{{ item.obtainedScore || 0 }}</span>
            <span>{{ formatDateTime(item.submitTime || item.completedAt || item.updatedAt || item.createdAt) }}</span>
            <button type="button" class="table-link-button" @click="router.push('/practice')">查看解析</button>
          </div>
        </div>
      </section>
      </div>

      <aside class="dashboard-aside">
        <section class="dashboard-section dashboard-section--insight">
          <div class="section-header section-header--panel">
            <div>
              <h2>掌握度趋势</h2>
            </div>
            <button type="button" class="dashboard-period-button">近 7 天</button>
          </div>
          <div class="line-chart">
            <template v-if="chartPoints.length">
              <svg viewBox="0 0 320 190" role="img" aria-label="掌握度趋势折线图">
                <g class="line-chart__grid">
                  <path d="M34 24H306" />
                  <path d="M34 61H306" />
                  <path d="M34 98H306" />
                  <path d="M34 135H306" />
                  <path d="M34 172H306" />
                </g>
                <g class="line-chart__axis">
                  <text x="8" y="28">100</text>
                  <text x="15" y="65">75</text>
                  <text x="15" y="102">50</text>
                  <text x="15" y="139">25</text>
                  <text x="22" y="176">0</text>
                </g>
                <polygon class="line-chart__area" :points="chartAreaPoints" />
                <polyline class="line-chart__line" :points="chartLinePoints" />
                <g v-for="point in chartPoints" :key="point.label">
                  <circle class="line-chart__dot" :cx="point.x" :cy="point.y" r="4" />
                  <text class="line-chart__label" :x="point.x" y="186">{{ point.label }}</text>
                </g>
              </svg>
              <div class="line-chart__tooltip">
                <span>{{ latestTrendLabel }}</span>
                <strong>得分率 {{ averageScore }}%</strong>
              </div>
            </template>
            <div v-else class="line-chart__empty">暂无近 7 天练习趋势</div>
          </div>
          <div class="dashboard-trend-summary">
            <div>
              <span>当前平均得分率</span>
              <strong>{{ averageScore }}<small>%</small></strong>
            </div>
            <div>
              <span>累计学习时长</span>
              <strong class="dashboard-duration-text">{{ studyDuration }}</strong>
            </div>
          </div>
        </section>

        <section class="dashboard-section dashboard-section--weak">
          <div class="section-header section-header--panel">
            <div>
              <h2>薄弱知识点 TOP5</h2>
            </div>
            <el-button link type="primary" @click="router.push('/wrong-questions')">查看全部</el-button>
          </div>
          <div v-if="weakPoints.length" class="weak-list">
            <div v-for="item in weakPoints" :key="item.rank" class="weak-item">
              <span class="weak-item__rank">{{ item.rank }}</span>
              <div class="weak-item__body">
                <div>
                  <strong>{{ item.name }}</strong>
                  <span>{{ item.value }}%</span>
                </div>
                <i :style="{ width: `${item.value}%` }"></i>
              </div>
            </div>
          </div>
          <div v-else class="section-empty section-empty--compact">暂无薄弱知识点</div>
        </section>
      </aside>
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
import {
  getLearningAnalyticsOverviewApi,
  type LearningAnalyticsOverviewPayload
} from '@/api/modules/learningAnalytics'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const materialRecords = ref<any[]>([])
const questionSetRecords = ref<any[]>([])
const practiceRecords = ref<any[]>([])

const createEmptyAnalytics = (): LearningAnalyticsOverviewPayload => ({
  totalPracticeCount: 0,
  totalQuestionAttempts: 0,
  wrongAttemptCount: 0,
  totalStudySeconds: 0,
  totalKnowledgePoints: 0,
  weakKnowledgePointCount: 0,
  averageAccuracyRate: 0,
  averageScoreRate: 0,
  masteryDistribution: [],
  questionTypePerformance: [],
  materialPerformance: [],
  practiceTrend: [],
  weakKnowledgePoints: []
})

const analyticsOverview = ref<LearningAnalyticsOverviewPayload>(createEmptyAnalytics())

const actionCards = [
  { title: '复习高频错题', desc: '建议 30 分钟', path: '/wrong-questions', icon: 'materials', color: 'green' },
  { title: '完成 1 套练习', desc: '建议 20 分钟', path: '/quiz', icon: 'summary', color: 'blue' },
  { title: '薄弱知识点强化', desc: '建议 25 分钟', path: '/mastery', icon: 'analytics', color: 'orange' }
]

const greetingName = computed(() =>
  userStore.profile?.nickname || userStore.profile?.username || '同学'
)

const parsedMaterialCount = computed(() =>
  materialRecords.value.filter((item) => item.parseStatus === 'SUCCESS').length
)

const summarizedMaterialCount = computed(() =>
  materialRecords.value.filter((item) => item.summaryStatus === 'SUCCESS').length
)

const latestPractice = computed(() => practiceRecords.value[0])

const averageScore = computed(() => {
  return Math.round(toNumber(analyticsOverview.value.averageScoreRate))
})

const primaryAdvice = computed(() => {
  if (!materialRecords.value.length) return '先上传一份课程资料，系统会帮你整理摘要、题集和练习路径。'
  if (!summarizedMaterialCount.value) return '最近资料还没有生成总结，可以先沉淀一份复习笔记。'
  if (!practiceRecords.value.length) return '已有资料和总结，下一步可以生成题集做一次快速检测。'
  return '继续从最近练习和错题切入，优先巩固得分波动较大的知识点。'
})

const statCards = computed(() => {
  return [
    {
      label: '资料总数',
      value: materialRecords.value.length,
      footnote: `已解析 ${parsedMaterialCount.value} 份`,
      icon: 'materials',
      color: 'green'
    },
    {
      label: 'AI 总结',
      value: summarizedMaterialCount.value,
      footnote: `已总结 ${summarizedMaterialCount.value} 份`,
      icon: 'summary',
      color: 'blue'
    },
    {
      label: '题集数量',
      value: questionSetRecords.value.length,
      footnote: `已生成 ${questionSetRecords.value.length} 套`,
      icon: 'quiz',
      color: 'orange'
    },
    {
      label: '最新得分',
      value: latestPractice.value ? `${latestPractice.value.obtainedScore || 0}` : '--',
      footnote: latestPractice.value
        ? `${latestPractice.value.correctCount || 0}/${latestPractice.value.totalQuestions || 0} 题正确`
        : '暂无练习记录',
      icon: 'practice',
      color: 'green'
    }
  ]
})

const studyDuration = computed(() => {
  const totalSeconds = Math.max(0, Math.floor(toNumber(analyticsOverview.value.totalStudySeconds)))
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${days}天 ${hours}时 ${minutes}分 ${seconds}秒`
})

const chartData = computed(() => {
  return analyticsOverview.value.practiceTrend.map((item) => ({
    label: formatChartDate(item.submitTime),
    value: Math.max(0, Math.min(100, toNumber(item.scoreRate)))
  }))
})

const chartPoints = computed(() => {
  const data = chartData.value
  const step = data.length > 1 ? 272 / (data.length - 1) : 0
  return data.map((item, index) => ({
    ...item,
    x: 34 + step * index,
    y: 172 - item.value * 1.48
  }))
})

const chartLinePoints = computed(() =>
  chartPoints.value.map((point) => `${point.x},${point.y}`).join(' ')
)

const chartAreaPoints = computed(() => {
  const points = chartPoints.value
  if (!points.length) return ''
  return `34,172 ${points.map((point) => `${point.x},${point.y}`).join(' ')} 306,172`
})

const latestTrendLabel = computed(() => chartData.value[chartData.value.length - 1]?.label || '--')

const weakPoints = computed(() => {
  return analyticsOverview.value.weakKnowledgePoints.slice(0, 5).map((item, index) => ({
    name: item.knowledgePoint || '未标注知识点',
    value: Math.max(0, Math.min(100, toNumber(item.masteryPercent))),
    wrongCount: item.wrongCount,
    attemptCount: item.attemptCount,
    rank: index + 1
  }))
})

const toNumber = (value: unknown) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

const getMaterialIcon = (item: any) => {
  const text = `${item.title || ''} ${item.fileName || ''} ${item.materialType || ''}`.toLowerCase()
  if (text.includes('.pdf') || text.includes('pdf')) return 'file-pdf'
  if (text.includes('.doc') || text.includes('word') || text.includes('docx')) return 'file-word'
  if (text.includes('.xls') || text.includes('excel') || text.includes('xlsx')) return 'file-excel'
  if (text.includes('.ppt') || text.includes('powerpoint') || text.includes('pptx')) return 'file-ppt'
  if (/\.(png|jpg|jpeg|gif|webp|svg)\b/.test(text) || text.includes('image')) return 'file-image'
  if (/\.(mp4|mov|avi|mkv)\b/.test(text) || text.includes('video')) return 'file-video'
  if (/\.(zip|rar|7z)\b/.test(text)) return 'file-zip'
  if (/\.(txt|md)\b/.test(text) || text.includes('text')) return 'file-text'
  return 'file-unknown'
}

const getMaterialBadge = (item: any) => getMaterialIcon(item).replace('file-', '')

const formatMaterialBadge = (item: any) => {
  const badge = getMaterialBadge(item)
  const labelMap: Record<string, string> = {
    pdf: 'PDF',
    word: 'Word',
    excel: 'Excel',
    ppt: 'PPT',
    image: 'IMG',
    video: 'VID',
    zip: 'ZIP',
    text: 'TXT',
    unknown: 'FILE'
  }
  return labelMap[badge] || 'FILE'
}

const formatMaterialType = (item: any) => {
  const icon = getMaterialIcon(item)
  const labelMap: Record<string, string> = {
    'file-pdf': 'PDF 文档',
    'file-word': 'Word 文档',
    'file-excel': '表格文件',
    'file-ppt': '演示文稿',
    'file-image': '图片资料',
    'file-video': '视频资料',
    'file-zip': '压缩包',
    'file-text': '文本资料',
    'file-unknown': '学习资料'
  }
  return labelMap[icon]
}

const formatCharacters = (value?: number) => {
  const count = toNumber(value)
  if (!count) return '0 字'
  return `${count.toLocaleString()} 字`
}

const formatTag = (value?: string) => {
  if (!value) return '未知'
  return value.split('_').join(' ')
}

const formatDateTime = (value?: string) => {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  const pad = (numberValue: number) => `${numberValue}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const formatChartDate = (value?: string) => {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  const pad = (numberValue: number) => `${numberValue}`.padStart(2, '0')
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

const formatStatusLabel = (status?: string) => {
  if (!status) return '未知'
  const labelMap: Record<string, string> = {
    SUCCESS: '已解析',
    FAILED: '解析失败',
    PENDING: '待处理',
    PROCESSING: '处理中'
  }
  return labelMap[status.toUpperCase()] || formatTag(status)
}

const getStatusColor = (status?: string) => {
  if (!status) return 'default'
  const upperStatus = status.toUpperCase()
  if (upperStatus === 'SUCCESS') return 'success'
  if (upperStatus === 'FAILED') return 'error'
  if (upperStatus === 'PENDING' || upperStatus === 'PROCESSING') return 'warning'
  return 'default'
}

const getPracticeRate = (item: any) => {
  const correct = toNumber(item.correctCount)
  const total = toNumber(item.totalQuestions)
  if (!total) return '0%'
  return `${Math.round((correct / total) * 100)}%`
}

const getAccuracyClass = (item: any) => {
  const rate = Number.parseInt(getPracticeRate(item), 10)
  if (rate >= 85) return 'accuracy-text accuracy-text--good'
  if (rate >= 70) return 'accuracy-text accuracy-text--warn'
  return 'accuracy-text accuracy-text--low'
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const [materialRes, questionRes, practiceRes, analyticsRes] = await Promise.all([
      getMaterialPageApi({ current: 1, size: 5 }),
      getQuestionSetPageApi({ current: 1, size: 5 }),
      getPracticePageApi({ current: 1, size: 5 }),
      getLearningAnalyticsOverviewApi({ trendLimit: 7, days: 7 })
    ])

    materialRecords.value = materialRes.data.data.records || []
    questionSetRecords.value = questionRes.data.data.records || []
    practiceRecords.value = practiceRes.data.data.records || []
    analyticsOverview.value = analyticsRes.data.data || createEmptyAnalytics()
  } catch (error: any) {
    ElMessage.error(error.message || '加载概览数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>
