<template>
  <section class="statistics-page" :aria-busy="loading">
    <header class="statistics-heading">
      <div><h1>学习统计</h1><p>练习记录、学习时长与知识掌握情况。</p></div>
      <div class="statistics-heading__actions">
        <RouterLink class="statistics-text-link" to="/mastery">知识掌握 <AppIcon name="arrow-up-right" :size="17" /></RouterLink>
        <button class="statistics-refresh" type="button" aria-label="刷新学习统计" :disabled="loading" @click="loadAnalytics"><AppIcon name="refresh" :size="21" :class="{ 'is-spinning': loading }" /></button>
      </div>
    </header>

    <div class="statistics-filters">
      <div class="statistics-periods" role="group" aria-label="统计时间范围">
        <button v-for="period in periods" :key="period.days" type="button" :aria-pressed="filters.days === period.days" :class="{ 'is-active': filters.days === period.days }" @click="setPeriod(period.days)">{{ period.label }}</button>
      </div>
      <el-select v-model="filters.materialId" clearable filterable placeholder="全部资料" aria-label="按学习资料筛选统计" :loading="materialsLoading" class="statistics-material-filter" @change="loadAnalytics">
        <el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" />
      </el-select>
    </div>

    <div v-if="materialsError" class="statistics-notice" role="alert">
      <div><strong>资料列表暂时无法加载</strong><p>仍可查看统计，重新加载后即可选择资料。</p></div>
      <el-button :loading="materialsLoading" @click="loadMaterials">重新加载资料</el-button>
    </div>
    <div v-if="loadError" class="statistics-notice" role="alert">
      <div><strong>{{ loadError }}</strong><p>请重试，学习记录会保留。</p></div>
      <el-button type="primary" :loading="loading" @click="loadAnalytics">重新加载统计</el-button>
    </div>

    <div v-if="loading" class="statistics-loading" role="status"><span class="statistics-loading__line"></span>正在整理学习记录…</div>
    <template v-else-if="hasLoaded && !loadError">
      <div v-if="!analytics.totalQuestionAttempts" class="statistics-empty-intro">
        <div><h2>{{ filters.days || filters.materialId ? '这个范围内还没有练习记录' : '完成一次练习，留下第一份记录' }}</h2><p>提交练习后，就可以在这里回顾表现与复习重点。</p></div>
        <div class="statistics-empty-actions">
          <el-button v-if="filters.days || filters.materialId" @click="clearFilters">查看全部记录</el-button>
          <el-button type="primary" @click="router.push(materialsLoaded && !materials.length ? '/materials' : '/quiz')">{{ materialsLoaded && !materials.length ? '添加学习资料' : '去练习' }}</el-button>
        </div>
      </div>

      <div class="statistics-sheet">
        <section class="statistics-overview" aria-labelledby="statistics-overview-title">
          <div class="statistics-section-heading statistics-section-heading--overview"><h2 id="statistics-overview-title">{{ periodLabel }}概览</h2><span class="statistics-scope">{{ selectedMaterialTitle }}</span></div>
          <dl class="statistics-totals">
            <div><dt>完成练习</dt><dd>{{ formatNumber(analytics.totalPracticeCount) }}<small>次</small></dd></div>
            <div><dt>累计作答</dt><dd>{{ formatNumber(analytics.totalQuestionAttempts) }}<small>次</small></dd></div>
            <div><dt>练习时长</dt><dd>{{ studyDuration.value }}<small>{{ studyDuration.unit }}</small></dd></div>
            <div><dt>平均正确率</dt><dd>{{ formatNumber(analytics.averageAccuracyRate) }}<small>%</small></dd></div>
          </dl>
          <div class="statistics-overview__notes">
            <span>平均得分率 <b>{{ formatNumber(analytics.averageScoreRate) }}%</b></span>
            <span>错题次数 <b>{{ formatNumber(analytics.wrongAttemptCount) }}</b></span>
            <span>来自已提交的练习记录</span>
          </div>
        </section>

        <section class="statistics-trend" aria-labelledby="statistics-trend-title">
          <div class="statistics-section-heading">
            <div><h2 id="statistics-trend-title">练习趋势</h2><p>按提交顺序，展示{{ filters.days ? '所选时间内' : '' }}最近 {{ analytics.practiceTrend.length }} 次练习。</p></div>
            <div class="statistics-metric-switch" role="group" aria-label="趋势统计指标">
              <button v-for="metric in trendMetrics" :key="metric.key" type="button" :aria-pressed="trendMetric === metric.key" :class="{ 'is-active': trendMetric === metric.key }" @click="trendMetric = metric.key">{{ metric.label }}</button>
            </div>
          </div>

          <figure v-if="analytics.practiceTrend.length" class="statistics-trend-figure">
            <div class="statistics-chart-frame">
              <div class="statistics-y-axis" aria-hidden="true"><span v-for="tick in trendAxisTicks" :key="tick.value" :style="{ top: tick.y / 220 * 100 + '%' }">{{ tick.value }}%</span></div>
              <svg viewBox="0 0 640 220" preserveAspectRatio="none" role="group" :aria-label="'练习' + trendMetricLabel + '趋势，纵轴范围为 0 至 100%，每个点是一份练习记录'">
                <line v-for="tick in trendAxisTicks" :key="tick.value" x1="12" :y1="tick.y" x2="628" :y2="tick.y" class="statistics-grid-line" aria-hidden="true" />
                <polyline :points="trendLinePoints" class="statistics-trend-line" aria-hidden="true" />
                <circle v-for="point in trendPlotPoints" :key="point.sessionId" :cx="point.x" :cy="point.y" :r="activeTrendPoint?.sessionId === point.sessionId ? 5 : 4" class="statistics-trend-dot" :class="{ 'is-active': activeTrendPoint?.sessionId === point.sessionId }" role="img" tabindex="0" :aria-label="formatTrendPoint(point)" @mouseenter="selectedTrendId = point.sessionId" @focus="selectedTrendId = point.sessionId"><title>{{ formatTrendPoint(point) }}</title></circle>
              </svg>
            </div>
            <div class="statistics-x-axis" aria-hidden="true">
              <span v-for="(point, index) in trendAxisLabels" :key="point.sessionId" :class="{ 'is-first': index === 0 && trendAxisLabels.length > 1, 'is-last': index === trendAxisLabels.length - 1 && trendAxisLabels.length > 1 }" :style="{ left: point.x / 640 * 100 + '%' }">{{ point.label }}</span>
            </div>
            <figcaption class="statistics-trend-caption" aria-live="polite" aria-atomic="true">
              <template v-if="activeTrendPoint"><span>{{ formatDateTime(activeTrendPoint.submitTime) }} · {{ activeTrendPoint.sessionName }}</span><strong>{{ trendMetricLabel }} {{ formatNumber(activeTrendPoint[trendMetric]) }}%</strong></template>
            </figcaption>
          </figure>
          <div v-else class="statistics-empty-chart"><AppIcon name="analytics" :size="32" /><p>练习后，变化会从这里开始。</p></div>
        </section>
      </div>

      <template v-if="analytics.totalQuestionAttempts">
        <div class="statistics-breakdowns">
          <section class="statistics-distribution" aria-labelledby="statistics-mastery-title">
            <div class="statistics-section-heading">
              <div><h2 id="statistics-mastery-title">知识掌握</h2><p>{{ analytics.totalKnowledgePoints }} 个知识点 · {{ analytics.weakKnowledgePointCount }} 个待复习</p></div>
              <RouterLink class="statistics-text-link" :to="{ path: '/mastery', query: { materialId: filters.materialId ? String(filters.materialId) : undefined } }">全部明细 <AppIcon name="arrow-right" :size="16" /></RouterLink>
            </div>
            <div class="statistics-mastery-distribution">
              <div class="statistics-donut" :style="donutStyle" aria-hidden="true"><div><strong>{{ analytics.totalKnowledgePoints }}</strong><span>知识点</span></div></div>
              <ul class="statistics-mastery-legend" aria-label="知识点掌握度分布"><li v-for="item in masteryLegend" :key="item.level"><i :style="{ background: item.color }" aria-hidden="true"></i><span>{{ item.label }}</span><strong>{{ item.count }}</strong><small>{{ item.percent }}%</small></li></ul>
            </div>
          </section>

          <section class="statistics-question-types" aria-labelledby="statistics-types-title">
            <div class="statistics-section-heading"><div><h2 id="statistics-types-title">题型表现</h2><p>不同题型的正确率与作答情况。</p></div></div>
            <ul v-if="analytics.questionTypePerformance.length" class="statistics-type-list">
              <li v-for="item in analytics.questionTypePerformance" :key="item.questionType">
                <div class="statistics-type-list__label"><strong>{{ item.questionTypeLabel }}</strong><span>{{ item.attemptCount }} 次作答 · 错 {{ item.wrongCount }} 次</span><b>{{ item.accuracyRate }}%</b></div>
                <div class="statistics-bar" aria-hidden="true"><span :style="{ width: clampPercent(item.accuracyRate) + '%' }"></span></div>
                <small>得分率 {{ item.scoreRate }}%</small>
              </li>
            </ul>
            <p v-else class="statistics-empty-note">暂无题型记录。</p>
          </section>
        </div>

        <section class="statistics-review" aria-labelledby="statistics-review-title">
          <div class="statistics-section-heading"><div><h2 id="statistics-review-title">值得再练一次</h2><p>优先复习掌握度低于 70% 的知识点。</p></div><RouterLink class="statistics-text-link" to="/wrong-questions">查看错题 <AppIcon name="arrow-right" :size="16" /></RouterLink></div>
          <div v-if="reviewPoints.length" class="statistics-review-list">
            <button v-for="(item, index) in reviewPoints" :key="String(item.materialId || 0) + '-' + item.knowledgePoint" type="button" class="statistics-review-row" @click="goMastery(item)">
              <span class="statistics-row-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="statistics-review-row__main"><strong>{{ item.knowledgePoint }}</strong><small>{{ item.materialTitle || '未关联资料' }}</small></span>
              <span class="statistics-review-row__score"><strong>{{ item.masteryPercent }}<small>%</small></strong><small>掌握度 · 错 {{ item.wrongCount }} 次</small></span>
              <AppIcon name="arrow-up-right" :size="18" />
            </button>
          </div>
          <p v-else class="statistics-empty-note">目前没有掌握度低于 70% 的知识点，继续保持。</p>
        </section>

        <div class="statistics-record-columns">
          <section aria-labelledby="statistics-materials-title">
            <div class="statistics-section-heading"><h2 id="statistics-materials-title">资料表现</h2><span class="statistics-small-label">得分率</span></div>
            <ul v-if="analytics.materialPerformance.length" class="statistics-record-list"><li v-for="item in analytics.materialPerformance" :key="item.materialId || item.materialTitle"><div><strong>{{ item.materialTitle }}</strong><span>{{ item.practiceCount }} 次练习 · {{ item.attemptCount }} 次作答</span></div><b>{{ item.scoreRate }}<small>%</small></b></li></ul>
            <p v-else class="statistics-empty-note">暂无资料表现记录。</p>
          </section>
          <section aria-labelledby="statistics-recent-title">
            <div class="statistics-section-heading"><h2 id="statistics-recent-title">最近练习</h2><span class="statistics-small-label">正确率</span></div>
            <ul v-if="recentTrend.length" class="statistics-record-list"><li v-for="item in recentTrend" :key="item.sessionId"><div><strong>{{ item.sessionName }}</strong><span>{{ item.materialTitle || '未关联资料' }} · {{ formatDateTime(item.submitTime) }}</span></div><b>{{ item.accuracyRate }}<small>%</small></b></li></ul>
            <p v-else class="statistics-empty-note">暂无最近练习。</p>
          </section>
        </div>
      </template>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { getLearningAnalyticsOverviewApi, type LearningAnalyticsOverviewPayload, type PracticeTrendPoint, type WeakKnowledgePointItem } from '@/api/modules/learningAnalytics'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'

const router = useRouter()
const loading = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
const materialsError = ref('')
const materialsLoaded = ref(false)
const materialsLoading = ref(false)
const materials = ref<MaterialPageItem[]>([])
const filters = reactive({ materialId: undefined as number | undefined, days: 0 })
const periods = [{ days: 7, label: '近 7 天' }, { days: 30, label: '近 30 天' }, { days: 0, label: '全部' }]
const periodLabel = computed(() => filters.days ? '近 ' + filters.days + ' 天' : '累计')
const selectedMaterialTitle = computed(() => filters.materialId ? materials.value.find((item) => item.id === filters.materialId)?.title || '所选资料' : '全部资料')
const trendMetric = ref<'accuracyRate' | 'scoreRate'>('accuracyRate')
const trendMetrics = [{ key: 'accuracyRate', label: '正确率' }, { key: 'scoreRate', label: '得分率' }] as const
const trendMetricLabel = computed(() => trendMetric.value === 'accuracyRate' ? '正确率' : '得分率')
const selectedTrendId = ref<number | null>(null)
let analyticsRequestId = 0
let materialRequestId = 0

const analytics = reactive<LearningAnalyticsOverviewPayload>({
  totalPracticeCount: 0, totalQuestionAttempts: 0, wrongAttemptCount: 0, totalStudySeconds: 0,
  totalKnowledgePoints: 0, weakKnowledgePointCount: 0, averageAccuracyRate: 0, averageScoreRate: 0,
  masteryDistribution: [], questionTypePerformance: [], materialPerformance: [], practiceTrend: [], weakKnowledgePoints: []
})

const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value)
const clampPercent = (value: number) => Math.min(100, Math.max(0, Number(value) || 0))
const studyDuration = computed(() => {
  const seconds = Math.max(0, Number(analytics.totalStudySeconds) || 0)
  return seconds > 0 && seconds < 60
    ? { value: formatNumber(seconds), unit: '秒' }
    : { value: formatNumber(Math.floor(seconds / 60)), unit: '分钟' }
})
const reviewPoints = computed(() => analytics.weakKnowledgePoints.filter((item) => Number(item.masteryPercent) < 70))
const masteryColors: Record<string, string> = { MASTERED: '#385a4a', GOOD: '#8eab94', WEAK: '#d3b279', RISK: '#b96b51' }
const masteryLegend = computed(() => analytics.masteryDistribution.map((item) => ({ ...item, color: masteryColors[item.level] || 'var(--muted)' })))
const donutStyle = computed(() => {
  if (!analytics.masteryDistribution.length || !analytics.totalKnowledgePoints) return { background: 'var(--line)' }
  let current = 0
  const segments = analytics.masteryDistribution.map((item) => {
    const start = current
    current += Number(item.percent || 0)
    return (masteryColors[item.level] || 'var(--muted)') + ' ' + start + '% ' + current + '%'
  })
  return { background: 'conic-gradient(' + segments.join(', ') + ')' }
})

const trendAxisTicks = [{ value: 100, y: 28 }, { value: 50, y: 108 }, { value: 0, y: 188 }]
const trendPlotPoints = computed(() => analytics.practiceTrend.map((item, index, list) => ({
  ...item,
  x: list.length === 1 ? 320 : 12 + 616 * index / (list.length - 1),
  y: 188 - 160 * clampPercent(item[trendMetric.value]) / 100,
  label: item.submitTime ? item.submitTime.slice(5, 10) : '未记录日期'
})))
const trendLinePoints = computed(() => trendPlotPoints.value.map((point) => point.x + ',' + point.y).join(' '))
const trendAxisLabels = computed(() => {
  const points = trendPlotPoints.value
  if (points.length <= 3) return points
  return Array.from({ length: 3 }, (_, index) => points[Math.round(index * (points.length - 1) / 2)])
})
const activeTrendPoint = computed(() => analytics.practiceTrend.find((point) => point.sessionId === selectedTrendId.value) || analytics.practiceTrend[analytics.practiceTrend.length - 1])
const recentTrend = computed(() => [...analytics.practiceTrend].reverse().slice(0, 5))
const formatDateTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : '时间未记录'
const formatTrendPoint = (point: PracticeTrendPoint) => point.sessionName + '，' + formatDateTime(point.submitTime) + '，' + trendMetricLabel.value + ' ' + point[trendMetric.value] + '%，' + point.correctCount + ' / ' + point.totalQuestions + ' 题正确'

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
  const requestId = ++materialRequestId
  materialsLoading.value = true
  materialsError.value = ''
  try {
    const res = await getMaterialPageApi({ current: 1, size: 50, parseStatus: 'SUCCESS' })
    const firstPage = res.data.data
    const records = firstPage.records || []
    const pageCount = Math.ceil(Number(firstPage.total || records.length) / 50)
    const remainingPages = await Promise.all(Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) => getMaterialPageApi({ current: index + 2, size: 50, parseStatus: 'SUCCESS' })))
    if (requestId !== materialRequestId) return
    materials.value = [...records, ...remainingPages.flatMap((response) => response.data.data.records || [])]
    materialsLoaded.value = true
  } catch {
    if (requestId === materialRequestId) materialsError.value = '资料列表暂时无法加载'
  } finally {
    if (requestId === materialRequestId) materialsLoading.value = false
  }
}

const loadAnalytics = async () => {
  const requestId = ++analyticsRequestId
  loading.value = true
  loadError.value = ''
  try {
    const res = await getLearningAnalyticsOverviewApi({ materialId: filters.materialId || undefined, days: filters.days || undefined, trendLimit: 30 })
    if (requestId !== analyticsRequestId) return
    applyAnalytics(res.data.data as LearningAnalyticsOverviewPayload)
    selectedTrendId.value = null
    hasLoaded.value = true
  } catch {
    if (requestId === analyticsRequestId) loadError.value = '学习统计暂时无法加载'
  } finally {
    if (requestId === analyticsRequestId) loading.value = false
  }
}

const setPeriod = (days: number) => {
  if (filters.days === days) return
  filters.days = days
  void loadAnalytics()
}
const clearFilters = () => {
  filters.materialId = undefined
  filters.days = 0
  void loadAnalytics()
}
const goMastery = (item: WeakKnowledgePointItem) => router.push({ path: '/mastery', query: { keyword: item.knowledgePoint, materialId: item.materialId ? String(item.materialId) : undefined } })

onUnmounted(() => { analyticsRequestId++; materialRequestId++ })
void loadMaterials()
void loadAnalytics()
</script>

<style scoped>
.statistics-page { max-width: 1180px; margin: 0 auto; padding: 12px 0 36px; color: var(--text, #252a25); }
.statistics-heading, .statistics-heading__actions, .statistics-filters, .statistics-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.statistics-heading { margin-bottom: 36px; }
.statistics-heading h1 { margin: 0; font-size: clamp(27px, 3vw, 34px); font-weight: 600; letter-spacing: -.04em; }
.statistics-heading p { margin: 11px 0 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
.statistics-heading__actions { gap: 24px; flex-shrink: 0; }
.statistics-text-link { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; color: #385a4a; text-decoration: none; font-size: 13px; white-space: nowrap; }
.statistics-text-link:hover { color: #1f3e30; }
.statistics-refresh { display: grid; width: 44px; height: 44px; padding: 0; place-items: center; border: 0; border-radius: 50%; background: #ebece6; color: #465443; cursor: pointer; }
.statistics-refresh:hover { background: #e1e6dc; }
.statistics-refresh:disabled { cursor: wait; opacity: .6; }
.statistics-filters { margin-bottom: 24px; }
.statistics-periods { display: inline-flex; padding: 4px; border-radius: 999px; background: #eaece5; }
.statistics-periods button { min-height: 42px; min-width: 88px; padding: 9px 21px; border: 0; border-radius: 999px; background: transparent; color: #697262; font: inherit; font-size: 14px; cursor: pointer; }
.statistics-periods button.is-active { color: #252a25; background: #fff; box-shadow: 0 1px 4px #25382208; font-weight: 600; }
.statistics-material-filter { width: 250px; }
.statistics-material-filter :deep(.el-select__wrapper) { min-height: 44px; border-radius: 11px; box-shadow: 0 0 0 1px var(--line) inset; background: transparent; }
.statistics-sheet { padding: 32px 36px; background: #fff; border-radius: 22px; }
.statistics-section-heading { margin-bottom: 24px; }
.statistics-section-heading h2 { margin: 0; font-size: 18px; font-weight: 600; letter-spacing: -.015em; }
.statistics-section-heading p { margin: 8px 0 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
.statistics-section-heading--overview { margin-bottom: 28px; }
.statistics-scope { max-width: 55%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--muted); font-size: 12px; }
.statistics-totals { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; }
.statistics-totals > div { min-width: 0; padding: 0 30px; border-left: 1px solid #e8eae3; }
.statistics-totals > div:first-child { padding-left: 0; border-left: 0; }
.statistics-totals > div:last-child { padding-right: 0; }
.statistics-totals dt { color: #697262; font-size: 13px; }
.statistics-totals dd { display: flex; align-items: baseline; flex-wrap: wrap; gap: 7px; margin: 13px 0 0; font-size: clamp(30px, 3.8vw, 46px); font-weight: 550; font-variant-numeric: tabular-nums; letter-spacing: -.045em; line-height: 1.15; overflow-wrap: anywhere; }
.statistics-totals small { color: #697262; font-size: 12px; font-weight: 400; letter-spacing: 0; }
.statistics-overview__notes { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; padding: 24px 0 28px; margin-top: 6px; border-bottom: 1px solid #eceee8; font-size: 12px; color: #697262; }
.statistics-overview__notes b { padding-left: 6px; color: #596451; font-weight: 500; }
.statistics-overview__notes > span:last-child { margin-left: auto; font-size: 12px; }
.statistics-trend { padding-top: 28px; }
.statistics-metric-switch { display: flex; align-self: start; gap: 22px; }
.statistics-metric-switch button { position: relative; min-height: 42px; padding: 0 0 10px; border: 0; background: transparent; color: #697262; font: inherit; font-size: 13px; cursor: pointer; }
.statistics-metric-switch button.is-active { color: #385a4a; font-weight: 600; }
.statistics-metric-switch button.is-active::after { position: absolute; right: calc(50% - 7px); bottom: 0; width: 14px; height: 3px; border-radius: 2px; background: #b36b50; content: ''; }
.statistics-trend-figure { margin: 0; }
.statistics-chart-frame { position: relative; padding-left: 43px; }
.statistics-chart-frame svg { display: block; width: 100%; height: 240px; overflow: visible; }
.statistics-y-axis { position: absolute; inset: 0 auto 0 0; width: 34px; color: #697262; font-size: 12px; font-variant-numeric: tabular-nums; }
.statistics-y-axis span { position: absolute; right: 0; transform: translateY(-50%); }
.statistics-grid-line { stroke: #eeefe9; stroke-width: 1; vector-effect: non-scaling-stroke; }
.statistics-trend-line { fill: none; stroke: #b8cbbd; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; vector-effect: non-scaling-stroke; }
.statistics-trend-dot { fill: #7d9a84; stroke: #fff; stroke-width: 2; vector-effect: non-scaling-stroke; cursor: crosshair; }
.statistics-trend-dot.is-active { fill: #385a4a; }
.statistics-trend-dot:focus { outline: none; stroke: #d2be9c; stroke-width: 5; }
.statistics-x-axis { position: relative; height: 23px; margin: 2px 0 0 43px; color: #697262; font-size: 12px; font-variant-numeric: tabular-nums; }
.statistics-x-axis span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
.statistics-x-axis .is-first { transform: none; }
.statistics-x-axis .is-last { transform: translateX(-100%); }
.statistics-trend-caption { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 9px 22px; min-height: 44px; padding-top: 15px; color: #697262; font-size: 12px; line-height: 1.6; }
.statistics-trend-caption > span { min-width: 0; overflow-wrap: anywhere; }
.statistics-trend-caption strong { margin-left: auto; color: #53684e; font-weight: 500; white-space: nowrap; }
.statistics-empty-chart { display: grid; min-height: 220px; align-content: center; justify-items: center; gap: 12px; color: #9caa95; }
.statistics-empty-chart p { margin: 0; color: #697262; font-size: 13px; }
.statistics-breakdowns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 56px; padding: 44px 0; border-bottom: 1px solid var(--line, #e5e7df); }
.statistics-question-types { padding-left: 44px; border-left: 1px solid var(--line, #e5e7df); }
.statistics-mastery-distribution { display: grid; grid-template-columns: 140px minmax(0, 1fr); align-items: center; gap: 30px; min-height: 165px; }
.statistics-donut { position: relative; display: grid; width: 140px; height: 140px; place-items: center; border-radius: 50%; }
.statistics-donut::before { position: absolute; inset: 11px; border-radius: 50%; background: var(--bg, #f7f7f2); content: ''; }
.statistics-donut > div { position: relative; display: grid; gap: 3px; justify-items: center; }
.statistics-donut strong { font-size: 30px; font-weight: 550; font-variant-numeric: tabular-nums; }
.statistics-donut span { color: #697262; font-size: 12px; }
.statistics-mastery-legend, .statistics-type-list, .statistics-record-list { padding: 0; margin: 0; list-style: none; }
.statistics-mastery-legend { display: grid; gap: 18px; }
.statistics-mastery-legend li { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto 38px; align-items: center; gap: 10px; font-size: 12px; }
.statistics-mastery-legend i { width: 7px; height: 7px; border-radius: 50%; }
.statistics-mastery-legend strong { font-weight: 600; }
.statistics-mastery-legend small { text-align: right; color: #697262; font-size: 12px; }
.statistics-type-list { display: grid; gap: 22px; }
.statistics-type-list__label { display: flex; align-items: baseline; gap: 12px; margin-bottom: 9px; }
.statistics-type-list__label strong { font-size: 13px; font-weight: 500; }
.statistics-type-list__label span { color: #697262; font-size: 12px; }
.statistics-type-list__label b { margin-left: auto; font-size: 13px; font-weight: 500; }
.statistics-type-list > li > small { display: block; margin-top: 6px; color: #697262; font-size: 11px; text-align: right; }
.statistics-bar { height: 5px; overflow: hidden; border-radius: 4px; background: #e7eae1; }
.statistics-bar span { display: block; height: 100%; border-radius: inherit; background: #8ea68b; }
.statistics-review { padding: 38px 0 30px; border-bottom: 1px solid var(--line, #e5e7df); }
.statistics-review-list { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); column-gap: 48px; }
.statistics-review-row { display: flex; align-items: center; width: 100%; min-width: 0; gap: 17px; padding: 20px 0; border: 0; border-bottom: 1px solid #e7e9e1; background: transparent; color: inherit; text-align: left; font: inherit; cursor: pointer; }
.statistics-review-row:hover .statistics-review-row__main strong { color: #385a4a; }
.statistics-review-row > .app-icon { flex-shrink: 0; color: #697262; }
.statistics-row-index { color: #a8ae9e; font-size: 13px; font-variant-numeric: tabular-nums; }
.statistics-review-row__main { display: grid; flex: 1; min-width: 0; gap: 7px; }
.statistics-review-row__main strong { font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
.statistics-review-row__main small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #697262; font-size: 12px; }
.statistics-review-row__score { display: grid; gap: 5px; justify-items: end; flex-shrink: 0; }
.statistics-review-row__score strong { color: #a15e45; font-size: 22px; font-weight: 500; font-variant-numeric: tabular-nums; }
.statistics-review-row__score strong small { margin-left: 2px; font-size: 12px; }
.statistics-review-row__score > small { color: #697262; font-size: 11px; }
.statistics-record-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 56px; padding-top: 38px; }
.statistics-small-label { color: #697262; font-size: 12px; }
.statistics-record-list li { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 17px 0; border-bottom: 1px solid #e7e9e1; }
.statistics-record-list li > div { display: grid; gap: 7px; min-width: 0; }
.statistics-record-list strong { color: #424c3c; font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }
.statistics-record-list span { color: #697262; font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.statistics-record-list b { flex-shrink: 0; font-size: 21px; font-weight: 500; font-variant-numeric: tabular-nums; }
.statistics-record-list b small { margin-left: 2px; color: #697262; font-size: 12px; font-weight: 400; }
.statistics-empty-note { padding: 24px 0; color: #697262; font-size: 13px; line-height: 1.8; }
.statistics-notice, .statistics-empty-intro { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 18px; padding: 23px 0; border-top: 1px solid var(--line); margin-bottom: 20px; }
.statistics-notice strong, .statistics-empty-intro h2 { margin: 0; font-size: 15px; font-weight: 500; line-height: 1.7; }
.statistics-notice p, .statistics-empty-intro p { margin: 7px 0 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.statistics-empty-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.statistics-empty-actions :deep(.el-button + .el-button) { margin-left: 0; }
.statistics-loading { display: grid; justify-items: center; align-content: center; min-height: 380px; gap: 18px; font-size: 13px; color: #697262; }
.statistics-loading__line { width: 42px; height: 3px; border-radius: 3px; background: #8fa18a; animation: statistics-pulse 1.2s ease-in-out infinite alternate; }
.statistics-page button:focus-visible, .statistics-page a:focus-visible { outline: 2px solid #385a4a; outline-offset: 4px; }
.is-spinning { animation: statistics-spin 1s linear infinite; }
@keyframes statistics-pulse { to { opacity: .25; } }
@keyframes statistics-spin { to { transform: rotate(360deg); } }
@media (max-width: 1000px) {
  .statistics-sheet { padding: 28px; }
  .statistics-totals > div { padding-inline: 22px; }
  .statistics-breakdowns, .statistics-record-columns { gap: 32px; }
  .statistics-question-types { padding-left: 28px; }
  .statistics-mastery-distribution { grid-template-columns: 114px minmax(0, 1fr); gap: 20px; }
  .statistics-donut { width: 114px; height: 114px; }
  .statistics-type-list__label { gap: 8px; flex-wrap: wrap; }
  .statistics-type-list__label span { order: 3; flex-basis: 100%; }
}
@media (max-width: 760px) {
  .statistics-page { padding-top: 4px; }
  .statistics-heading { align-items: start; margin-bottom: 28px; }
  .statistics-heading__actions { gap: 14px; }
  .statistics-totals { grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: 27px; }
  .statistics-totals > div { padding-inline: 22px 0; }
  .statistics-totals > div:nth-child(odd) { border-left: 0; padding-left: 0; }
  .statistics-totals dd { font-size: 38px; }
  .statistics-overview__notes > span:last-child { margin-left: 0; flex-basis: 100%; }
  .statistics-breakdowns, .statistics-record-columns { grid-template-columns: minmax(0, 1fr); gap: 34px; }
  .statistics-question-types { padding: 28px 0 0; border-left: 0; border-top: 1px solid var(--line); }
  .statistics-mastery-distribution { grid-template-columns: 140px minmax(0, 1fr); gap: 32px; }
  .statistics-donut { width: 140px; height: 140px; }
  .statistics-type-list__label span { order: 0; flex-basis: auto; }
  .statistics-review-list { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 520px) {
  .statistics-heading h1 { font-size: 26px; }
  .statistics-heading p { max-width: 210px; font-size: 12px; }
  .statistics-heading__actions { gap: 6px; }
  .statistics-heading__actions .statistics-text-link { font-size: 12px; gap: 4px; }
  .statistics-heading__actions .statistics-text-link .app-icon { display: none; }
  .statistics-refresh { width: 40px; height: 40px; }
  .statistics-filters { flex-wrap: wrap; gap: 14px; }
  .statistics-periods { width: 100%; }
  .statistics-periods button { flex: 1; min-width: 0; padding-inline: 12px; }
  .statistics-material-filter { width: 100%; }
  .statistics-sheet { padding: 24px 20px; border-radius: 19px; }
  .statistics-section-heading { align-items: start; gap: 10px; margin-bottom: 20px; }
  .statistics-section-heading h2 { font-size: 16px; }
  .statistics-section-heading p { font-size: 12px; }
  .statistics-totals dt { font-size: 12px; }
  .statistics-totals dd { font-size: 34px; gap: 5px; margin-top: 11px; }
  .statistics-totals small { font-size: 12px; }
  .statistics-overview__notes { gap: 10px 18px; padding-bottom: 23px; font-size: 12px; }
  .statistics-trend > .statistics-section-heading { flex-wrap: wrap; }
  .statistics-metric-switch { margin-top: 2px; }
  .statistics-metric-switch button { font-size: 12px; }
  .statistics-chart-frame { padding-left: 31px; }
  .statistics-chart-frame svg { height: 210px; }
  .statistics-y-axis { width: 25px; font-size: 11px; }
  .statistics-x-axis { margin-left: 31px; font-size: 11px; }
  .statistics-trend-caption { font-size: 12px; }
  .statistics-trend-caption > span { flex-basis: 100%; }
  .statistics-trend-caption strong { margin-left: 0; }
  .statistics-breakdowns { padding: 32px 0; }
  .statistics-mastery-distribution { grid-template-columns: 122px minmax(0, 1fr); gap: 24px; }
  .statistics-donut { width: 122px; height: 122px; }
  .statistics-mastery-legend li { gap: 7px; grid-template-columns: 7px minmax(0, 1fr) auto 34px; font-size: 12px; }
  .statistics-mastery-legend small { font-size: 11px; }
  .statistics-text-link { font-size: 12px; gap: 5px; min-height: 44px; }
  .statistics-review-row { gap: 12px; }
  .statistics-review-row__main strong { font-size: 13px; }
  .statistics-review-row__score strong { font-size: 20px; }
  .statistics-row-index { font-size: 12px; }
  .statistics-review-row > .app-icon { width: 15px; }
  .statistics-review, .statistics-record-columns { padding-top: 30px; }
}
@media (prefers-reduced-motion: reduce) { .is-spinning, .statistics-loading__line { animation: none; } }
</style>
