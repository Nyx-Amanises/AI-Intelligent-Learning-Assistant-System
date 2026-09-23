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

    <div v-if="loading && !hasLoaded" class="statistics-loading" role="status"><span class="statistics-loading__line"></span>正在整理学习记录…</div>
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
          <StatTiles :items="overviewTiles" />
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

          <TrendLineChart v-model:selected-id="selectedTrendId" :points="trendPoints" :max="100" unit="%" :aria-label="'练习' + trendMetricLabel + '趋势，纵轴范围为 0 至 100%，每个点是一份练习记录'">
            <template #caption="{ point }"><span>{{ formatDateTime(point.raw.submitTime) }} · {{ point.raw.sessionName }}</span><strong>{{ trendMetricLabel }} {{ formatNumber(point.raw[trendMetric]) }}%</strong></template>
            <template #empty><AppIcon name="analytics" :size="32" /><p>练习后，变化会从这里开始。</p></template>
          </TrendLineChart>
        </section>
      </div>

      <template v-if="analytics.totalQuestionAttempts">
        <div class="statistics-breakdowns">
          <section class="statistics-distribution" aria-labelledby="statistics-mastery-title">
            <div class="statistics-section-heading">
              <div><h2 id="statistics-mastery-title">知识掌握</h2><p>{{ analytics.totalKnowledgePoints }} 个知识点 · {{ analytics.weakKnowledgePointCount }} 个待复习</p></div>
              <RouterLink class="statistics-text-link" :to="{ path: '/mastery', query: { materialId: filters.materialId ? String(filters.materialId) : undefined } }">全部明细 <AppIcon name="arrow-right" :size="16" /></RouterLink>
            </div>
            <DonutChart :segments="masterySegments" :total="analytics.totalKnowledgePoints" center-label="知识点" legend-label="知识点掌握度分布" />
          </section>

          <section class="statistics-question-types" aria-labelledby="statistics-types-title">
            <div class="statistics-section-heading"><div><h2 id="statistics-types-title">题型表现</h2><p>不同题型的正确率与作答情况。</p></div></div>
            <ul v-if="analytics.questionTypePerformance.length" class="statistics-type-list">
              <li v-for="item in analytics.questionTypePerformance" :key="item.questionType">
                <BarRow :label="item.questionTypeLabel" :meta="item.attemptCount + ' 次作答 · 错 ' + item.wrongCount + ' 次'" :value="item.accuracyRate + '%'" :percent="item.accuracyRate" :note="'得分率 ' + item.scoreRate + '%'" />
              </li>
            </ul>
            <p v-else class="statistics-empty-note">暂无题型记录。</p>
          </section>
        </div>

        <section class="statistics-review" aria-labelledby="statistics-review-title">
          <div class="statistics-section-heading"><div><h2 id="statistics-review-title">值得再练一次</h2><p>优先复习掌握度低于 {{ MASTERY_REVIEW_THRESHOLD }}% 的知识点。</p></div><RouterLink class="statistics-text-link" to="/wrong-questions">查看错题 <AppIcon name="arrow-right" :size="16" /></RouterLink></div>
          <div v-if="reviewPoints.length" class="statistics-review-list">
            <button v-for="(item, index) in reviewPoints" :key="String(item.materialId || 0) + '-' + item.knowledgePoint" type="button" class="statistics-review-row" @click="goMastery(item)">
              <span class="statistics-row-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="statistics-review-row__main"><strong>{{ item.knowledgePoint }}</strong><small>{{ item.materialTitle || '未关联资料' }}</small></span>
              <span class="statistics-review-row__score"><strong>{{ item.masteryPercent }}<small>%</small></strong><small>掌握度 · 错 {{ item.wrongCount }} 次</small></span>
              <AppIcon name="arrow-up-right" :size="18" />
            </button>
          </div>
          <p v-else class="statistics-empty-note">目前没有掌握度低于 {{ MASTERY_REVIEW_THRESHOLD }}% 的知识点，继续保持。</p>
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
import StatTiles, { type StatTileItem } from '@/components/StatTiles.vue'
import TrendLineChart, { type TrendChartPoint } from '@/components/charts/TrendLineChart.vue'
import DonutChart, { type DonutSegment } from '@/components/charts/DonutChart.vue'
import BarRow from '@/components/charts/BarRow.vue'
import { MASTERY_REVIEW_THRESHOLD, masteryColor } from '@/utils/mastery'
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
const selectedTrendId = ref<string | number | null>(null)
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
const reviewPoints = computed(() => analytics.weakKnowledgePoints.filter((item) => Number(item.masteryPercent) < MASTERY_REVIEW_THRESHOLD))
const overviewTiles = computed<StatTileItem[]>(() => [
  { key: 'practice', label: '完成练习', value: formatNumber(analytics.totalPracticeCount), unit: '次' },
  { key: 'attempts', label: '累计作答', value: formatNumber(analytics.totalQuestionAttempts), unit: '次' },
  { key: 'duration', label: '练习时长', value: studyDuration.value.value, unit: studyDuration.value.unit },
  { key: 'accuracy', label: '平均正确率', value: formatNumber(analytics.averageAccuracyRate), unit: '%' }
])
const masterySegments = computed<DonutSegment[]>(() => analytics.masteryDistribution.map((item) => ({
  key: item.level, label: item.label, percent: Number(item.percent || 0), count: item.count, color: masteryColor(item.level)
})))

interface TrendPoint extends TrendChartPoint { raw: PracticeTrendPoint }
const trendPoints = computed<TrendPoint[]>(() => analytics.practiceTrend.map((item) => ({
  id: item.sessionId,
  label: item.submitTime ? item.submitTime.slice(5, 10) : '未记录日期',
  value: clampPercent(item[trendMetric.value]),
  ariaLabel: formatTrendPoint(item),
  raw: item
})))
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
.statistics-page { max-width: 1180px; margin: 0 auto; padding: 12px 0 36px; color: var(--text); }
.statistics-heading, .statistics-heading__actions, .statistics-filters, .statistics-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.statistics-heading { margin-bottom: 36px; }
.statistics-heading h1 { margin: 0; font-size: clamp(27px, 3vw, 34px); font-weight: 600; letter-spacing: -.04em; }
.statistics-heading p { margin: 11px 0 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
.statistics-heading__actions { gap: 24px; flex-shrink: 0; }
.statistics-text-link { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; color: var(--brand); text-decoration: none; font-size: 13px; white-space: nowrap; }
.statistics-text-link:hover { color: var(--brand-hover); }
.statistics-refresh { display: grid; width: 44px; height: 44px; padding: 0; place-items: center; border: 0; border-radius: 50%; background: var(--bg-secondary); color: var(--text-secondary); cursor: pointer; }
.statistics-refresh:hover { background: var(--line); }
.statistics-refresh:disabled { cursor: wait; opacity: .6; }
.statistics-filters { margin-bottom: 24px; }
.statistics-periods { display: inline-flex; padding: 4px; border-radius: 999px; background: var(--bg-secondary); }
.statistics-periods button { min-height: 42px; min-width: 88px; padding: 9px 21px; border: 0; border-radius: 999px; background: transparent; color: var(--muted); font: inherit; font-size: 14px; cursor: pointer; }
.statistics-periods button.is-active { color: var(--text); background: var(--panel); box-shadow: var(--shadow-sm); font-weight: 600; }
.statistics-material-filter { width: 250px; }
.statistics-material-filter :deep(.el-select__wrapper) { min-height: 44px; border-radius: 11px; box-shadow: 0 0 0 1px var(--line) inset; background: transparent; }
.statistics-sheet { padding: 32px 36px; background: var(--panel); border-radius: 22px; }
.statistics-section-heading { margin-bottom: 24px; }
.statistics-section-heading h2 { margin: 0; font-size: 18px; font-weight: 600; letter-spacing: -.015em; }
.statistics-section-heading p { margin: 8px 0 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
.statistics-section-heading--overview { margin-bottom: 28px; }
.statistics-scope { max-width: 55%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--muted); font-size: 12px; }
.statistics-overview__notes { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; padding: 24px 0 28px; margin-top: 6px; border-bottom: 1px solid var(--line); font-size: 12px; color: var(--muted); }
.statistics-overview__notes b { padding-left: 6px; color: var(--text-secondary); font-weight: 500; }
.statistics-overview__notes > span:last-child { margin-left: auto; font-size: 12px; }
.statistics-trend { padding-top: 28px; }
.statistics-metric-switch { display: flex; align-self: start; gap: 22px; }
.statistics-metric-switch button { position: relative; min-height: 42px; padding: 0 0 10px; border: 0; background: transparent; color: var(--muted); font: inherit; font-size: 13px; cursor: pointer; }
.statistics-metric-switch button.is-active { color: var(--brand); font-weight: 600; }
.statistics-metric-switch button.is-active::after { position: absolute; right: calc(50% - 7px); bottom: 0; width: 14px; height: 3px; border-radius: 2px; background: var(--accent); content: ''; }
.statistics-breakdowns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 56px; padding: 44px 0; border-bottom: 1px solid var(--line); }
.statistics-question-types { padding-left: 44px; border-left: 1px solid var(--line); }
.statistics-type-list, .statistics-record-list { padding: 0; margin: 0; list-style: none; }
.statistics-type-list { display: grid; gap: 22px; }
.statistics-review { padding: 38px 0 30px; border-bottom: 1px solid var(--line); }
.statistics-review-list { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); column-gap: 48px; }
.statistics-review-row { display: flex; align-items: center; width: 100%; min-width: 0; gap: 17px; padding: 20px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; color: inherit; text-align: left; font: inherit; cursor: pointer; }
.statistics-review-row:hover .statistics-review-row__main strong { color: var(--brand); }
.statistics-review-row > .app-icon { flex-shrink: 0; color: var(--muted); }
.statistics-row-index { color: var(--muted); font-size: 13px; font-variant-numeric: tabular-nums; }
.statistics-review-row__main { display: grid; flex: 1; min-width: 0; gap: 7px; }
.statistics-review-row__main strong { font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
.statistics-review-row__main small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--muted); font-size: 12px; }
.statistics-review-row__score { display: grid; gap: 5px; justify-items: end; flex-shrink: 0; }
.statistics-review-row__score strong { color: var(--red); font-size: 22px; font-weight: 500; font-variant-numeric: tabular-nums; }
.statistics-review-row__score strong small { margin-left: 2px; font-size: 12px; }
.statistics-review-row__score > small { color: var(--muted); font-size: 11px; }
.statistics-record-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 56px; padding-top: 38px; }
.statistics-small-label { color: var(--muted); font-size: 12px; }
.statistics-record-list li { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 17px 0; border-bottom: 1px solid var(--line); }
.statistics-record-list li > div { display: grid; gap: 7px; min-width: 0; }
.statistics-record-list strong { color: var(--text-secondary); font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }
.statistics-record-list span { color: var(--muted); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.statistics-record-list b { flex-shrink: 0; font-size: 21px; font-weight: 500; font-variant-numeric: tabular-nums; }
.statistics-record-list b small { margin-left: 2px; color: var(--muted); font-size: 12px; font-weight: 400; }
.statistics-empty-note { padding: 24px 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.statistics-notice, .statistics-empty-intro { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 18px; padding: 23px 0; border-top: 1px solid var(--line); margin-bottom: 20px; }
.statistics-notice strong, .statistics-empty-intro h2 { margin: 0; font-size: 15px; font-weight: 500; line-height: 1.7; }
.statistics-notice p, .statistics-empty-intro p { margin: 7px 0 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.statistics-empty-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.statistics-empty-actions :deep(.el-button + .el-button) { margin-left: 0; }
.statistics-page[aria-busy="true"] .statistics-sheet, .statistics-page[aria-busy="true"] .statistics-empty-intro { opacity: .55; transition: opacity .2s ease; pointer-events: none; }
.statistics-loading { display: grid; justify-items: center; align-content: center; min-height: 380px; gap: 18px; font-size: 13px; color: var(--muted); }
.statistics-loading__line { width: 42px; height: 3px; border-radius: 3px; background: var(--brand); animation: statistics-pulse 1.2s ease-in-out infinite alternate; }
.statistics-page button:focus-visible, .statistics-page a:focus-visible { outline: 2px solid var(--brand); outline-offset: 4px; }
.is-spinning { animation: statistics-spin 1s linear infinite; }
@keyframes statistics-pulse { to { opacity: .25; } }
@keyframes statistics-spin { to { transform: rotate(360deg); } }
@media (max-width: 1000px) {
  .statistics-sheet { padding: 28px; }
  .statistics-breakdowns, .statistics-record-columns { gap: 32px; }
  .statistics-question-types { padding-left: 28px; }
}
@media (max-width: 760px) {
  .statistics-page { padding-top: 4px; }
  .statistics-heading { align-items: start; margin-bottom: 28px; }
  .statistics-heading__actions { gap: 14px; }
  .statistics-overview__notes > span:last-child { margin-left: 0; flex-basis: 100%; }
  .statistics-breakdowns, .statistics-record-columns { grid-template-columns: minmax(0, 1fr); gap: 34px; }
  .statistics-question-types { padding: 28px 0 0; border-left: 0; border-top: 1px solid var(--line); }
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
  .statistics-overview__notes { gap: 10px 18px; padding-bottom: 23px; font-size: 12px; }
  .statistics-trend > .statistics-section-heading { flex-wrap: wrap; }
  .statistics-metric-switch { margin-top: 2px; }
  .statistics-metric-switch button { font-size: 12px; }
  .statistics-breakdowns { padding: 32px 0; }
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
