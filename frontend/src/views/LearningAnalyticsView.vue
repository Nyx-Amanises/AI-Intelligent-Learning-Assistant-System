<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">学习分析</h1>
        <p class="page-desc">
          看见每一次练习的进步，找到下一步值得复习的内容。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-select
          v-model="filters.materialId"
          clearable
          filterable
          placeholder="全部资料"
          aria-label="按学习资料筛选分析"
          :loading="materialsLoading"
          style="width: 260px"
          @change="loadAnalytics"
        >
          <el-option
            v-for="item in materials"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
        <el-button :loading="loading" @click="loadAnalytics">刷新图表</el-button>
      </div>
    </div>

    <div v-if="materialsError" class="insight-load-error" role="alert">
      <div><strong>资料列表暂时无法加载</strong><p>可以继续查看学习分析，重试后即可选择资料。</p></div>
      <el-button :loading="materialsLoading" @click="loadMaterials">重新加载资料</el-button>
    </div>
    <div v-if="loadError" class="insight-load-error" role="alert">
      <div><strong>{{ loadError }}</strong><p>数据暂时没有读取成功，请重试。</p></div>
      <el-button type="primary" :loading="loading" @click="loadAnalytics">重新加载分析</el-button>
    </div>

    <div v-if="hasLoaded && !loading && !loadError && !analytics.totalQuestionAttempts" class="analytics-empty-callout">
      <div>
        <h2>{{ filters.materialId ? '这份资料还没有练习记录' : '完成一次练习，开始了解你的学习状态' }}</h2>
        <p>提交练习后，正确率趋势与复习重点会自动呈现在这里。</p>
      </div>
      <div class="analytics-empty-actions">
        <el-button v-if="filters.materialId" @click="clearMaterialFilter">查看全部资料</el-button>
        <el-button type="primary" @click="router.push(materialsLoaded && !materials.length ? '/materials' : '/quiz')">{{ materialsLoaded && !materials.length ? '添加学习资料' : '去做一次练习' }}</el-button>
      </div>
    </div>

    <div v-if="hasLoaded && !loading && !loadError" class="analytics-hero">
      <div class="analytics-hero__main">
        <span>学习概览</span>
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
    <template v-else-if="hasLoaded && !loadError">
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
            <div class="trend-chart-frame">
              <div class="trend-y-axis" aria-hidden="true">
                <span v-for="tick in trendAxisTicks" :key="tick.value" :style="{ top: tick.y / 220 * 100 + '%' }">{{ tick.value }}%</span>
              </div>
              <svg viewBox="0 0 640 220" preserveAspectRatio="none" role="group" aria-label="练习正确率趋势，纵轴范围为 0 至 100%">
                <line v-for="tick in trendAxisTicks" :key="tick.value" x1="12" :y1="tick.y" x2="628" :y2="tick.y" class="trend-grid-line" aria-hidden="true" />
                <line x1="12" y1="28" x2="12" y2="188" class="chart-axis" aria-hidden="true" />
                <line x1="12" y1="188" x2="628" y2="188" class="chart-axis" aria-hidden="true" />
                <polyline :points="trendLinePoints" class="trend-line" aria-hidden="true" />
                <circle
                  v-for="point in trendPlotPoints"
                  :key="point.sessionId"
                  :cx="point.x"
                  :cy="point.y"
                  r="5"
                  class="trend-dot"
                  role="img"
                  tabindex="0"
                  :aria-label="formatTrendPoint(point)"
                ><title>{{ formatTrendPoint(point) }}</title></circle>
              </svg>
            </div>
            <div class="trend-labels" aria-hidden="true">
              <span
                v-for="(point, index) in trendAxisLabels"
                :key="'label-' + point.sessionId"
                :class="{
                  'trend-label--first': index === 0 && trendAxisLabels.length > 1,
                  'trend-label--last': index === trendAxisLabels.length - 1 && trendAxisLabels.length > 1
                }"
                :style="{ left: point.x / 640 * 100 + '%' }"
                :title="formatDateTime(point.submitTime)"
              >{{ point.label }}</span>
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
              <p>找到还需要复习的学习资料。</p>
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
              <p>优先复习掌握度低于 70% 的知识点。</p>
            </div>
            <el-button link type="primary" @click="router.push('/wrong-questions')">看错题</el-button>
          </div>
          <div v-if="reviewPoints.length" class="weak-point-grid">
            <button
              v-for="item in reviewPoints"
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
          <div v-else class="state-block empty">{{ analytics.totalKnowledgePoints ? '目前没有掌握度低于 70% 的知识点，继续保持。' : '完成练习后，这里会整理需要优先复习的知识点。' }}</div>
        </div>

        <div class="analytics-card">
          <div class="analytics-card__head">
            <div>
              <h3>最近练习</h3>
              <p>回顾最近的练习与正确率。</p>
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
const hasLoaded = ref(false)
const loadError = ref('')
const materialsError = ref('')
const materialsLoaded = ref(false)
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

const reviewPoints = computed(() =>
  analytics.weakKnowledgePoints.filter((item) => Number(item.masteryPercent) < 70)
)

const masteryColors: Record<string, string> = {
  MASTERED: 'var(--green)',
  GOOD: 'var(--blue)',
  WEAK: 'var(--accent)',
  RISK: 'var(--red)'
}

const masteryLegend = computed(() =>
  analytics.masteryDistribution.map((item) => ({
    ...item,
    color: masteryColors[item.level] || 'var(--muted)'
  }))
)

const donutStyle = computed(() => {
  if (!analytics.masteryDistribution.length || !analytics.totalKnowledgePoints) {
    return { background: 'var(--line)' }
  }
  let current = 0
  const segments = analytics.masteryDistribution.map((item) => {
    const start = current
    current += Number(item.percent || 0)
    const color = masteryColors[item.level] || 'var(--muted)'
    return `${color} ${start}% ${current}%`
  })
  return { background: `conic-gradient(${segments.join(', ')})` }
})

const trendAxisTicks = [{ value: 100, y: 28 }, { value: 50, y: 108 }, { value: 0, y: 188 }]

const trendPlotPoints = computed(() => {
  const list = analytics.practiceTrend
  const width = 640
  const height = 220
  const left = 12
  const right = 628
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

const trendAxisLabels = computed(() => {
  const points = trendPlotPoints.value
  if (points.length <= 5) return points
  return Array.from({ length: 5 }, (_, index) => points[Math.round(index * (points.length - 1) / 4)])
})

const formatTrendPoint = (point: PracticeTrendPoint) =>
  `${point.sessionName}，${formatDateTime(point.submitTime)}，正确率 ${point.accuracyRate}%（${point.correctCount} / ${point.totalQuestions} 题正确）`

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
  materialsError.value = ''
  try {
    const res = await getMaterialPageApi({
      current: 1,
      size: 50,
      parseStatus: 'SUCCESS'
    })
    const firstPage = res.data.data
    const records = firstPage.records || []
    const pageCount = Math.ceil(Number(firstPage.total || records.length) / 50)
    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) =>
        getMaterialPageApi({ current: index + 2, size: 50, parseStatus: 'SUCCESS' })
      )
    )
    materials.value = [...records, ...remainingPages.flatMap((response) => response.data.data.records || [])]
    materialsLoaded.value = true
  } catch {
    materialsError.value = '资料列表暂时无法加载'
  } finally {
    materialsLoading.value = false
  }
}

const loadAnalytics = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getLearningAnalyticsOverviewApi({
      materialId: filters.materialId || undefined,
      trendLimit: 12
    })
    applyAnalytics(res.data.data as LearningAnalyticsOverviewPayload)
    hasLoaded.value = true
  } catch {
    loadError.value = '学习分析暂时无法加载'
  } finally {
    loading.value = false
  }
}

const clearMaterialFilter = () => {
  filters.materialId = undefined
  void loadAnalytics()
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

<style scoped>
.insight-load-error { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; padding: 22px 24px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); }
.insight-load-error strong { color: var(--text); font-size: 15px; }
.insight-load-error p { margin: 7px 0 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.analytics-empty-callout { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 20px; margin-bottom: 20px; padding: 22px 24px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); }
.analytics-empty-callout h2 { margin: 0; color: var(--text); font-size: 16px; font-weight: 600; }
.analytics-empty-callout p { margin: 8px 0 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.analytics-empty-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.analytics-empty-actions :deep(.el-button + .el-button) { margin-left: 0; }
.trend-chart-frame { position: relative; padding-left: 42px; }
.trend-y-axis { position: absolute; inset: 0 auto 0 0; width: 34px; color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.trend-y-axis span { position: absolute; right: 0; transform: translateY(-50%); }
.line-chart-wrap svg { height: 220px; border-radius: 8px; background: var(--bg); overflow: visible; }
.trend-grid-line { stroke: var(--line); stroke-width: 1; stroke-dasharray: 4 5; vector-effect: non-scaling-stroke; }
.trend-line, .trend-dot, .chart-axis { vector-effect: non-scaling-stroke; }
.trend-dot:focus { fill: var(--brand); stroke-width: 6; }
.trend-labels { position: relative; display: block; height: 22px; margin: 8px 0 0 42px; color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.trend-labels span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
.trend-labels .trend-label--first { transform: none; }
.trend-labels .trend-label--last { transform: translateX(-100%); }
@media (max-width: 600px) {
  .analytics-empty-callout { padding: 18px; }
  .trend-chart-frame { padding-left: 36px; }
  .trend-y-axis { width: 30px; font-size: 11px; }
  .trend-labels { margin-left: 36px; font-size: 11px; }
}
</style>
