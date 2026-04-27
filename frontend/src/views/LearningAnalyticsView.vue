<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">学习分析</h1>
        <p class="page-desc">
          把练习、错题和知识点掌握度汇总成可视化看板，用来观察学习趋势、薄弱点和资料表现。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-select
          v-model="filters.materialId"
          clearable
          filterable
          placeholder="全部资料"
          :loading="materialsLoading"
          style="width: 260px"
          @change="loadAnalytics"
        >
          <el-option
            v-for="item in materials"
            :key="item.id"
            :label="`${item.title} · #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-button :loading="loading" @click="loadAnalytics">刷新图表</el-button>
      </div>
    </div>

    <div class="analytics-hero">
      <div class="analytics-hero__main">
        <span>Learning Signals</span>
        <strong>{{ analytics.averageScoreRate }}%</strong>
        <p>平均得分率。当前统计来自 {{ analytics.totalPracticeCount }} 次练习、{{ analytics.totalQuestionAttempts }} 次作答。</p>
      </div>
      <div class="analytics-kpi-grid">
        <div class="analytics-kpi">
          <span>平均正确率</span>
          <strong>{{ analytics.averageAccuracyRate }}%</strong>
        </div>
        <div class="analytics-kpi">
          <span>错题次数</span>
          <strong>{{ analytics.wrongAttemptCount }}</strong>
        </div>
        <div class="analytics-kpi">
          <span>知识点</span>
          <strong>{{ analytics.totalKnowledgePoints }}</strong>
        </div>
        <div class="analytics-kpi analytics-kpi--risk">
          <span>薄弱知识点</span>
          <strong>{{ analytics.weakKnowledgePointCount }}</strong>
        </div>
      </div>
    </div>

    <div v-if="loading" class="state-block">正在生成学习分析图表...</div>
    <template v-else>
      <div class="analytics-grid analytics-grid--top">
        <div class="analytics-card">
          <div class="analytics-card__head">
            <div>
              <h3>掌握度分布</h3>
              <p>按知识点得分率划分学习状态。</p>
            </div>
            <el-button link type="primary" @click="router.push('/mastery')">看明细</el-button>
          </div>
          <div class="donut-wrap">
            <div class="donut-chart" :style="donutStyle">
              <div>
                <strong>{{ analytics.totalKnowledgePoints }}</strong>
                <span>知识点</span>
              </div>
            </div>
            <div class="donut-legend">
              <div v-for="item in masteryLegend" :key="item.level" class="donut-legend__item">
                <i :style="{ background: item.color }"></i>
                <span>{{ item.label }}</span>
                <strong>{{ item.count }}</strong>
                <em>{{ item.percent }}%</em>
              </div>
            </div>
          </div>
        </div>

        <div class="analytics-card">
          <div class="analytics-card__head">
            <div>
              <h3>练习趋势</h3>
              <p>展示最近练习的正确率变化。</p>
            </div>
            <span class="analytics-card__badge">最近 {{ analytics.practiceTrend.length }} 次</span>
          </div>
          <div v-if="analytics.practiceTrend.length" class="line-chart-wrap">
            <svg viewBox="0 0 640 220" role="img" aria-label="练习正确率趋势">
              <line x1="32" y1="28" x2="32" y2="188" class="chart-axis" />
              <line x1="32" y1="188" x2="610" y2="188" class="chart-axis" />
              <polyline :points="trendLinePoints" class="trend-line" />
              <circle
                v-for="point in trendPlotPoints"
                :key="point.sessionId"
                :cx="point.x"
                :cy="point.y"
                r="5"
                class="trend-dot"
              />
            </svg>
            <div class="trend-labels">
              <span v-for="point in trendPlotPoints" :key="`label-${point.sessionId}`">
                {{ point.label }}
              </span>
            </div>
          </div>
          <div v-else class="state-block empty">还没有可展示的练习趋势。</div>
        </div>
      </div>

      <div class="analytics-grid">
        <div class="analytics-card">
          <div class="analytics-card__head">
            <div>
              <h3>题型表现</h3>
              <p>不同题型的作答量、正确率和得分率。</p>
            </div>
          </div>
          <div v-if="analytics.questionTypePerformance.length" class="bar-list">
            <div
              v-for="item in analytics.questionTypePerformance"
              :key="item.questionType"
              class="bar-row"
            >
              <div class="bar-row__meta">
                <strong>{{ item.questionTypeLabel }}</strong>
                <span>{{ item.attemptCount }} 次 · 错 {{ item.wrongCount }} 次</span>
              </div>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: `${item.accuracyRate}%` }"></div>
              </div>
              <strong class="bar-row__value">{{ item.accuracyRate }}%</strong>
            </div>
          </div>
          <div v-else class="state-block empty">暂无题型统计。</div>
        </div>

        <div class="analytics-card">
          <div class="analytics-card__head">
            <div>
              <h3>资料表现</h3>
              <p>按资料聚合练习表现，方便定位哪份资料还需要复习。</p>
            </div>
          </div>
          <div v-if="analytics.materialPerformance.length" class="material-rank-list">
            <div
              v-for="item in analytics.materialPerformance"
              :key="item.materialId || item.materialTitle"
              class="material-rank-item"
            >
              <div>
                <strong>{{ item.materialTitle }}</strong>
                <span>{{ item.practiceCount }} 次练习 · {{ item.attemptCount }} 次作答</span>
              </div>
              <div class="material-rank-item__score">
                <em>{{ item.scoreRate }}%</em>
                <div class="bar-track">
                  <div class="bar-fill bar-fill--warm" :style="{ width: `${item.scoreRate}%` }"></div>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="state-block empty">暂无资料表现统计。</div>
        </div>
      </div>

      <div class="analytics-grid analytics-grid--bottom">
        <div class="analytics-card">
          <div class="analytics-card__head">
            <div>
              <h3>薄弱知识点</h3>
              <p>优先显示掌握度较低、错题较多的知识点。</p>
            </div>
            <el-button link type="primary" @click="router.push('/wrong-questions')">看错题</el-button>
          </div>
          <div v-if="analytics.weakKnowledgePoints.length" class="weak-point-grid">
            <button
              v-for="item in analytics.weakKnowledgePoints"
              :key="`${item.materialId || 0}-${item.knowledgePoint}`"
              type="button"
              class="weak-point-card"
              @click="goMastery(item)"
            >
              <span>{{ item.materialTitle || '未关联资料' }}</span>
              <strong>{{ item.knowledgePoint }}</strong>
              <em>掌握度 {{ item.masteryPercent }}% · 错 {{ item.wrongCount }} 次</em>
            </button>
          </div>
          <div v-else class="state-block empty">暂无薄弱知识点。</div>
        </div>

        <div class="analytics-card">
          <div class="analytics-card__head">
            <div>
              <h3>最近练习</h3>
              <p>把最新练习作为趋势图的文字补充。</p>
            </div>
          </div>
          <div v-if="recentTrend.length" class="recent-practice-list">
            <div v-for="item in recentTrend" :key="item.sessionId" class="recent-practice-item">
              <div>
                <strong>{{ item.sessionName }}</strong>
                <span>{{ item.materialTitle || '未关联资料' }} · {{ formatDateTime(item.submitTime) }}</span>
              </div>
              <em>{{ item.accuracyRate }}%</em>
            </div>
          </div>
          <div v-else class="state-block empty">暂无最近练习。</div>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getLearningAnalyticsOverviewApi,
  type LearningAnalyticsOverviewPayload,
  type PracticeTrendPoint,
  type WeakKnowledgePointItem
} from '@/api/modules/learningAnalytics'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'

const router = useRouter()
const loading = ref(false)
const materialsLoading = ref(false)
const materials = ref<MaterialPageItem[]>([])

const filters = reactive({
  materialId: undefined as number | undefined
})

const analytics = reactive<LearningAnalyticsOverviewPayload>({
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

const masteryColors: Record<string, string> = {
  MASTERED: '#16a34a',
  GOOD: '#2563eb',
  WEAK: '#f59e0b',
  RISK: '#e11d48'
}

const masteryLegend = computed(() =>
  analytics.masteryDistribution.map((item) => ({
    ...item,
    color: masteryColors[item.level] || '#94a3b8'
  }))
)

const donutStyle = computed(() => {
  if (!analytics.masteryDistribution.length || !analytics.totalKnowledgePoints) {
    return { background: '#eef2f7' }
  }
  let current = 0
  const segments = analytics.masteryDistribution.map((item) => {
    const start = current
    current += Number(item.percent || 0)
    const color = masteryColors[item.level] || '#94a3b8'
    return `${color} ${start}% ${current}%`
  })
  return { background: `conic-gradient(${segments.join(', ')})` }
})

const trendPlotPoints = computed(() => {
  const list = analytics.practiceTrend
  const width = 640
  const height = 220
  const left = 32
  const right = 610
  const top = 28
  const bottom = 188
  if (!list.length) {
    return []
  }
  return list.map((item, index) => {
    const x = list.length === 1 ? (left + right) / 2 : left + ((right - left) * index) / (list.length - 1)
    const y = bottom - ((bottom - top) * Number(item.accuracyRate || 0)) / 100
    return {
      ...item,
      x,
      y,
      label: formatTrendLabel(item.submitTime),
      width,
      height
    }
  })
})

const trendLinePoints = computed(() => trendPlotPoints.value.map((item) => `${item.x},${item.y}`).join(' '))

const recentTrend = computed(() => [...analytics.practiceTrend].reverse().slice(0, 5))

const formatDateTime = (value?: string) => {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ').slice(0, 16)
}

const formatTrendLabel = (value?: string) => {
  if (!value) {
    return '--'
  }
  const normalized = value.replace('T', ' ')
  return normalized.slice(5, 10)
}

const applyAnalytics = (data: LearningAnalyticsOverviewPayload) => {
  analytics.totalPracticeCount = data.totalPracticeCount || 0
  analytics.totalQuestionAttempts = data.totalQuestionAttempts || 0
  analytics.wrongAttemptCount = data.wrongAttemptCount || 0
  analytics.totalStudySeconds = data.totalStudySeconds || 0
  analytics.totalKnowledgePoints = data.totalKnowledgePoints || 0
  analytics.weakKnowledgePointCount = data.weakKnowledgePointCount || 0
  analytics.averageAccuracyRate = Number(data.averageAccuracyRate || 0)
  analytics.averageScoreRate = Number(data.averageScoreRate || 0)
  analytics.masteryDistribution = data.masteryDistribution || []
  analytics.questionTypePerformance = data.questionTypePerformance || []
  analytics.materialPerformance = data.materialPerformance || []
  analytics.practiceTrend = data.practiceTrend || []
  analytics.weakKnowledgePoints = data.weakKnowledgePoints || []
}

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

const loadAnalytics = async () => {
  loading.value = true
  try {
    const res = await getLearningAnalyticsOverviewApi({
      materialId: filters.materialId || undefined,
      trendLimit: 12
    })
    applyAnalytics(res.data.data as LearningAnalyticsOverviewPayload)
  } catch (error: any) {
    ElMessage.error(error.message || '加载学习分析失败')
  } finally {
    loading.value = false
  }
}

const goMastery = (item: WeakKnowledgePointItem) => {
  router.push({
    path: '/mastery',
    query: {
      keyword: item.knowledgePoint,
      materialId: item.materialId ? String(item.materialId) : undefined
    }
  })
}

void loadMaterials()
void loadAnalytics()
</script>
