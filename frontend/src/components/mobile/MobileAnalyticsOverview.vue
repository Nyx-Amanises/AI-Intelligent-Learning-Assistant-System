<template>
  <div class="mobile-analytics-overview" :aria-busy="loading">
    <div class="mobile-analytics-overview__toolbar">
      <span>{{ materialTitle }}</span>
      <div>
        <button
          type="button"
          :aria-expanded="showFilters"
          aria-controls="mobile-analytics-filters"
          @click="showFilters = !showFilters"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M4 6h16M7 12h10M10 18h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg
          >筛选
        </button>
        <button type="button" :disabled="loading" aria-label="刷新概览统计" @click="loadAnalytics">
          <AppIcon name="refresh" :size="18" />
        </button>
      </div>
    </div>
    <form
      v-if="showFilters"
      id="mobile-analytics-filters"
      class="mobile-analytics-overview__filters"
      @submit.prevent="applyFilters"
    >
      <label
        >学习资料<select v-model="draftMaterialId" :disabled="materialsLoading">
          <option :value="undefined">全部资料</option>
          <option v-for="material in materials" :key="material.id" :value="material.id">{{ material.title }}</option>
        </select></label
      >
      <div v-if="materialsError" class="mobile-analytics-overview__filter-error" role="alert">
        <span>资料列表暂时无法加载</span><button type="button" @click="emit('reload-materials')">重试</button>
      </div>
      <div class="mobile-analytics-overview__date-fields">
        <label>开始日期<input v-model="draftStartDate" type="date" min="1000-01-01" :max="today" /></label>
        <label>结束日期<input v-model="draftEndDate" type="date" min="1000-01-01" :max="today" /></label>
      </div>
      <p v-if="dateError" class="mobile-analytics-overview__filter-error" role="alert">{{ dateError }}</p>
      <div class="mobile-analytics-overview__filter-actions">
        <button type="button" @click="clearFilters">重置筛选</button
        ><button type="submit" :disabled="Boolean(dateError)">应用</button>
      </div>
    </form>
    <p v-if="customRange" class="mobile-analytics-overview__range-note">{{ startDate }} 至 {{ endDate }}</p>
    <div v-if="error" class="mobile-analytics-overview__error" role="alert">
      <p>概览统计暂时无法加载</p>
      <button type="button" @click="loadAnalytics">重新加载</button>
    </div>

    <template v-else>
      <section aria-labelledby="mobile-practice-count-title">
        <h2 id="mobile-practice-count-title" class="mobile-analytics-overview__section-title">
          <MobileIcon name="pencil" :size="17" />练习次数
        </h2>
        <div class="mobile-analytics-overview__card">
          <div class="mobile-analytics-overview__periods" role="group" aria-label="练习次数统计时间范围">
            <button
              v-for="period in periods"
              :key="period.days"
              type="button"
              :class="{ 'is-active': !customRange && days === period.days }"
              :aria-pressed="!customRange && days === period.days"
              @click="setPeriod(period.days)"
            >
              {{ period.label }}
            </button>
          </div>
          <div v-if="loading" class="mobile-analytics-overview__chart-status" role="status">正在整理练习记录…</div>
          <template v-else-if="barPoints.length">
            <div class="mobile-analytics-overview__legend"><i />练习</div>
            <div
              class="mobile-analytics-overview__bar-chart"
              :class="{ 'is-dense': barPoints.length > 12 }"
              :style="{ '--chart-columns': barPoints.length }"
              role="group"
              :aria-label="`练习次数趋势，${rangeLabel}`"
            >
              <button
                v-for="(point, index) in barPoints"
                :key="point.startDate"
                type="button"
                :class="{ 'is-selected': index === selectedBarIndex }"
                :aria-label="`${pointLabel(point)}，${point.count} 次练习`"
                :aria-pressed="index === selectedBarIndex"
                @click="selectedBarIndex = index"
              >
                <span class="mobile-analytics-overview__bar-track"
                  ><i v-if="point.count" :style="{ height: `${Math.max(3, (point.count / maxCount) * 100)}%` }" /><b
                    v-else-if="barPoints.length <= 12"
                    >0</b
                  ></span
                >
                <span class="mobile-analytics-overview__bar-date" :class="{ 'is-today': point.startDate === today }">{{
                  barLabel(point, index)
                }}</span>
              </button>
            </div>
            <p class="mobile-analytics-overview__chart-caption" aria-live="polite">
              {{ selectedBar ? `${pointLabel(selectedBar)} · ${selectedBar.count} 次练习` : rangeLabel }}
            </p>
          </template>
          <p v-else class="mobile-analytics-overview__chart-status">这个范围内还没有练习记录</p>
          <dl class="mobile-analytics-overview__metrics">
            <div>
              <dt>完成练习</dt>
              <dd>{{ loading ? '—' : formatNumber(analytics?.totalPracticeCount || 0) }}<small>次</small></dd>
            </div>
            <div>
              <dt>累计作答</dt>
              <dd>{{ loading ? '—' : formatNumber(analytics?.totalQuestionAttempts || 0) }}<small>次</small></dd>
            </div>
          </dl>
          <p v-if="analytics?.practiceCountTrend?.undatedCount" class="mobile-analytics-overview__note">
            另有 {{ analytics.practiceCountTrend.undatedCount }} 次练习未记录日期。
          </p>
        </div>
      </section>

      <section aria-labelledby="mobile-practice-duration-title">
        <h2
          id="mobile-practice-duration-title"
          class="mobile-analytics-overview__section-title mobile-analytics-overview__section-title--duration"
        >
          <MobileIcon name="clock" :size="17" />学习时长
        </h2>
        <div class="mobile-analytics-overview__card">
          <div class="mobile-analytics-overview__periods" role="group" aria-label="学习时长统计时间范围">
            <button
              v-for="period in periods"
              :key="period.days"
              type="button"
              :class="{ 'is-active': !customRange && days === period.days }"
              :aria-pressed="!customRange && days === period.days"
              @click="setPeriod(period.days)"
            >
              {{ period.label }}
            </button>
          </div>
          <div v-if="loading" class="mobile-analytics-overview__chart-status" role="status">正在整理时长记录…</div>
          <template v-else-if="linePoints.length">
            <div class="mobile-analytics-overview__line-chart">
              <svg
                viewBox="0 0 320 216"
                role="img"
                :aria-label="`逐次练习时长，纵轴单位${lineUnit}，横轴为练习提交顺序`"
              >
                <text x="0" y="8" fill="var(--muted)" font-size="8">{{ lineUnit }}</text>
                <g
                  v-for="tick in [0, lineMaximum / 2, lineMaximum]"
                  :key="tick"
                  class="mobile-analytics-overview__grid"
                >
                  <line x1="28" x2="310" :y1="lineY(tick)" :y2="lineY(tick)" />
                  <text x="0" :y="lineY(tick) + 3">{{ formatNumber(tick) }}</text>
                </g>
                <path
                  :d="linePath"
                  fill="none"
                  stroke="#d2d2d2"
                  stroke-width="2"
                  stroke-linejoin="round"
                  stroke-linecap="round"
                />
                <g
                  v-for="(point, index) in linePoints"
                  :key="point.sessionId"
                  role="button"
                  tabindex="0"
                  :aria-label="`${point.sessionName}，${shortDate(point.submitTime)}，练习时长 ${formatNumber(point.durationSeconds / lineDivisor)} ${lineUnit}`"
                  :aria-pressed="selectedLineIndex === index"
                  @click="selectedLineIndex = index"
                  @keydown.enter.prevent="selectedLineIndex = index"
                  @keydown.space.prevent="selectedLineIndex = index"
                >
                  <circle
                    :cx="lineX(index)"
                    :cy="lineY(point.durationSeconds / lineDivisor)"
                    :r="linePoints.length > 12 ? 3 : 4.5"
                    :fill="index === selectedLineIndex ? '#ff791f' : '#d2d2d2'"
                    stroke="var(--panel)"
                    stroke-width="1.5"
                  />
                  <circle
                    :cx="lineX(index)"
                    :cy="lineY(point.durationSeconds / lineDivisor)"
                    r="10"
                    fill="transparent"
                  />
                  <text
                    v-if="linePoints.length <= 7 || index === 0 || index === linePoints.length - 1"
                    :x="lineX(index)"
                    y="207"
                    text-anchor="middle"
                    :fill="index === selectedLineIndex ? '#db6414' : 'var(--muted)'"
                    font-size="9"
                  >
                    {{ shortDate(point.submitTime) }}
                  </text>
                </g>
              </svg>
            </div>
            <p class="mobile-analytics-overview__chart-caption" aria-live="polite">
              {{
                selectedLine
                  ? `${shortDate(selectedLine.submitTime)} · ${formatNumber(selectedLine.durationSeconds / lineDivisor)} ${lineUnit}`
                  : ''
              }}
            </p>
            <RouterLink
              v-if="selectedLine"
              class="mobile-analytics-overview__practice-link"
              :to="practiceRoute(selectedLine.sessionId)"
              ><span>{{ selectedLine.sessionName }}</span
              ><MobileIcon name="next" :size="14"
            /></RouterLink>
          </template>
          <div v-else class="mobile-analytics-overview__chart-status">
            <MobileIcon name="clock" :size="28" />
            <p>这个范围内暂无已记录时长的练习。</p>
          </div>
          <dl class="mobile-analytics-overview__metrics">
            <div>
              <dt>平均正确率</dt>
              <dd>
                {{ loading || !analytics?.totalQuestionAttempts ? '—' : formatNumber(analytics.averageAccuracyRate)
                }}<small v-if="analytics?.totalQuestionAttempts">%</small>
              </dd>
            </div>
            <div>
              <dt>练习总时长</dt>
              <dd>
                {{ loading ? '—' : duration.value }}<small>{{ duration.unit }}</small>
              </dd>
            </div>
          </dl>
          <p class="mobile-analytics-overview__note">
            {{ rangeLabel }} · 按提交顺序展示最近 {{ analytics?.practiceTrend.length || 0 }} 次已评分练习中的时长记录。
            <template v-if="missingDurationCount">{{ missingDurationCount }} 次未记录时长，未绘入图表。</template>
          </p>
        </div>
      </section>
      <RouterLink
        class="mobile-analytics-overview__mastery-link"
        :to="{ path: '/mastery', query: materialId ? { materialId: String(materialId) } : {} }"
        ><MobileIcon name="trend" :size="17" /><span>知识掌握明细</span><MobileIcon name="next" :size="17"
      /></RouterLink>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import type { MaterialPageItem } from '@/api/modules/material'
import {
  getLearningAnalyticsOverviewApi,
  type LearningAnalyticsOverviewPayload,
  type PracticeTrendPoint,
  type PracticeCountTrend
} from '@/api/modules/learningAnalytics'
import { practiceRoute } from '@/components/dashboard/routes'

const props = defineProps<{ materials: MaterialPageItem[]; materialsLoading: boolean; materialsError: string }>()
const emit = defineEmits<{ 'reload-materials': [] }>()
const periods = [
  { days: 7, label: '最近一周' },
  { days: 30, label: '最近30天' }
]
const days = ref(7)
const materialId = ref<number>()
const showFilters = ref(false)
const draftMaterialId = ref<number>()
const startDate = ref('')
const endDate = ref('')
const draftStartDate = ref('')
const draftEndDate = ref('')
const dateParts = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
}).formatToParts(new Date())
const part = (name: string) => dateParts.find((item) => item.type === name)?.value || ''
const today = `${part('year')}-${part('month')}-${part('day')}`
const analytics = ref<LearningAnalyticsOverviewPayload | null>(null)
const loading = ref(false)
const error = ref(false)
const selectedBarIndex = ref(0)
const selectedLineIndex = ref(0)
let requestId = 0
const customRange = computed(() => Boolean(startDate.value && endDate.value))
const rangeLabel = computed(() =>
  customRange.value ? `${startDate.value} 至 ${endDate.value}` : days.value === 7 ? '最近一周' : '最近30天'
)
const materialTitle = computed(() => props.materials.find((item) => item.id === materialId.value)?.title || '全部资料')
const dateError = computed(() => {
  if (!draftStartDate.value && !draftEndDate.value) return ''
  if (!draftStartDate.value || !draftEndDate.value) return '请选择完整的开始和结束日期。'
  if (draftStartDate.value < '1000-01-01' || draftEndDate.value < '1000-01-01') return '日期不能早于 1000-01-01。'
  if (draftStartDate.value > draftEndDate.value) return '开始日期不能晚于结束日期。'
  if (draftEndDate.value > today) return '结束日期不能晚于今天。'
  return ''
})
const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 }).format(value)
const barPoints = computed(() => analytics.value?.practiceCountTrend?.points || [])
const maxCount = computed(() => Math.max(1, ...barPoints.value.map((point) => point.count)))
const selectedBar = computed(() => barPoints.value[selectedBarIndex.value])
type CountPoint = PracticeCountTrend['points'][number]
const pointLabel = (point: CountPoint) =>
  point.startDate === point.endDate ? point.startDate : `${point.startDate} 至 ${point.endDate}`
const barLabel = (point: CountPoint, index: number) => {
  if (
    barPoints.value.length > 12 &&
    index !== 0 &&
    index !== barPoints.value.length - 1 &&
    index % Math.ceil(barPoints.value.length / 5)
  )
    return ''
  if (point.startDate === today && point.startDate === point.endDate) return '今'
  return point.startDate.slice(5).replace('-', '/')
}
const linePoints = computed(() =>
  (analytics.value?.practiceTrend || []).filter(
    (point): point is PracticeTrendPoint & { durationSeconds: number } =>
      typeof point.durationSeconds === 'number' && Number.isFinite(point.durationSeconds) && point.durationSeconds >= 0
  )
)
const missingDurationCount = computed(() => (analytics.value?.practiceTrend.length || 0) - linePoints.value.length)
const lineDivisor = computed(() =>
  Math.max(0, ...linePoints.value.map((point) => point.durationSeconds)) >= 60 ? 60 : 1
)
const lineUnit = computed(() => (lineDivisor.value === 60 ? '分钟' : '秒'))
const lineMaximum = computed(() =>
  Math.max(
    2,
    Math.ceil(Math.max(0, ...linePoints.value.map((point) => point.durationSeconds / lineDivisor.value)) / 2) * 2
  )
)
const selectedLine = computed(() => linePoints.value[selectedLineIndex.value])
const lineX = (index: number) =>
  linePoints.value.length < 2 ? 169 : 28 + (index / (linePoints.value.length - 1)) * 282
const lineY = (value: number) => 181 - (Math.max(0, value) / lineMaximum.value) * 160
const linePath = computed(() =>
  linePoints.value
    .map((point, index) => `${index ? 'L' : 'M'} ${lineX(index)} ${lineY(point.durationSeconds / lineDivisor.value)}`)
    .join(' ')
)
const shortDate = (value?: string) => (value ? value.slice(5, 10).replace('-', '/') : '无日期')
const duration = computed(() => {
  const seconds = Math.max(0, Number(analytics.value?.totalStudySeconds) || 0)
  return seconds > 0 && seconds < 60
    ? { value: formatNumber(seconds), unit: '秒' }
    : { value: formatNumber(Math.floor(seconds / 60)), unit: '分钟' }
})
const loadAnalytics = async () => {
  const currentRequest = ++requestId
  loading.value = true
  error.value = false
  try {
    const response = await getLearningAnalyticsOverviewApi({
      materialId: materialId.value,
      ...(customRange.value ? { startDate: startDate.value, endDate: endDate.value } : { days: days.value }),
      trendLimit: 30
    })
    if (currentRequest !== requestId) return
    analytics.value = response.data.data as LearningAnalyticsOverviewPayload
    selectedBarIndex.value = Math.max(0, barPoints.value.length - 1)
    selectedLineIndex.value = Math.max(0, linePoints.value.length - 1)
  } catch {
    if (currentRequest === requestId) error.value = true
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}
const setPeriod = (value: number) => {
  if (value === days.value && !customRange.value) return
  days.value = value
  startDate.value = ''
  endDate.value = ''
  draftStartDate.value = ''
  draftEndDate.value = ''
  void loadAnalytics()
}
const applyFilters = () => {
  if (dateError.value) return
  materialId.value = draftMaterialId.value
  startDate.value = draftStartDate.value
  endDate.value = draftEndDate.value
  showFilters.value = false
  void loadAnalytics()
}
const clearFilters = () => {
  draftMaterialId.value = undefined
  draftStartDate.value = ''
  draftEndDate.value = ''
  days.value = 7
  applyFilters()
}
onUnmounted(() => {
  requestId++
})
void loadAnalytics()
</script>

<style scoped>
.mobile-analytics-overview {
  color: var(--text);
  font-size: 14px;
}
.mobile-analytics-overview__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 36px;
  margin: -7px 0 6px;
}
.mobile-analytics-overview__toolbar > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--muted);
  font-size: 11px;
}
.mobile-analytics-overview__toolbar > div {
  display: flex;
  flex-shrink: 0;
}
.mobile-analytics-overview__toolbar button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 36px;
  min-height: 36px;
  padding: 0 6px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 11px;
}
.mobile-analytics-overview__toolbar button:disabled {
  opacity: 0.4;
}
.mobile-analytics-overview__filters {
  display: grid;
  gap: 14px;
  margin-bottom: 20px;
  padding: 16px;
  border-radius: 14px;
  background: var(--panel);
}
.mobile-analytics-overview__filters label {
  display: grid;
  min-width: 0;
  gap: 7px;
  color: var(--muted);
  font-size: 11px;
}
.mobile-analytics-overview__filters select,
.mobile-analytics-overview__filters input {
  width: 100%;
  min-width: 0;
  min-height: 40px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--panel);
  color: var(--text);
  font: inherit;
  font-size: 12px;
}
.mobile-analytics-overview__date-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.mobile-analytics-overview__filter-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.mobile-analytics-overview__filter-actions button,
.mobile-analytics-overview__filter-error button,
.mobile-analytics-overview__error button {
  min-height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: 18px;
  background: var(--bg-secondary);
  color: var(--muted);
  font: inherit;
  font-size: 12px;
}
.mobile-analytics-overview__filter-actions button[type='submit'],
.mobile-analytics-overview__error button {
  background: var(--brand-soft);
  color: #c25408;
}
.mobile-analytics-overview__filter-actions button:disabled {
  opacity: 0.4;
}
.mobile-analytics-overview__filter-error {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  color: #b5453d;
  font-size: 11px;
}
.mobile-analytics-overview__range-note {
  margin: 0 0 15px;
  color: var(--muted);
  font-size: 11px;
}
.mobile-analytics-overview__section-title {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 12px 0 13px;
  font-size: 15px;
  font-weight: 500;
}
.mobile-analytics-overview__section-title > .mobile-icon {
  color: #f6b516;
}
.mobile-analytics-overview__section-title--duration {
  margin-top: 32px;
}
.mobile-analytics-overview__section-title--duration > .mobile-icon {
  color: #ed6476;
}
.mobile-analytics-overview__card {
  overflow: hidden;
  padding: 16px 16px 24px;
  border-radius: 14px;
  background: var(--panel);
}
.mobile-analytics-overview__periods {
  display: flex;
  justify-content: center;
  gap: 30px;
  padding-bottom: 10px;
}
.mobile-analytics-overview__periods button {
  position: relative;
  min-height: 36px;
  padding: 0 2px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 14px;
}
.mobile-analytics-overview__periods button.is-active {
  color: var(--text);
}
.mobile-analytics-overview__periods button.is-active::after {
  position: absolute;
  bottom: 0;
  left: calc(50% - 4px);
  width: 8px;
  height: 2px;
  border-radius: 2px;
  background: #ff791f;
  content: '';
}
.mobile-analytics-overview__legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  padding: 0 4px 8px;
  color: var(--text-secondary);
  font-size: 10px;
}
.mobile-analytics-overview__legend i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff791f;
}
.mobile-analytics-overview__bar-chart {
  display: grid;
  grid-template-columns: repeat(var(--chart-columns), minmax(0, 1fr));
  height: 200px;
  padding: 0 2px;
}
.mobile-analytics-overview__bar-chart button {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
}
.mobile-analytics-overview__bar-track {
  display: flex;
  position: relative;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  height: 172px;
  border-left: 1px solid var(--line);
}
.mobile-analytics-overview__bar-chart button:last-child .mobile-analytics-overview__bar-track {
  border-right: 1px solid var(--line);
}
.mobile-analytics-overview__bar-track > i {
  display: block;
  width: 8px;
  max-width: 70%;
  border-radius: 5px;
  background: #ff9b56;
}
.mobile-analytics-overview__bar-chart button.is-selected .mobile-analytics-overview__bar-track > i {
  background: #ff791f;
}
.mobile-analytics-overview__bar-track > b {
  margin-bottom: 1px;
  color: var(--muted);
  font-size: 10px;
  font-weight: 400;
}
.mobile-analytics-overview__bar-date {
  display: block;
  width: max-content;
  padding-top: 7px;
  color: var(--muted);
  font-size: 9px;
}
.mobile-analytics-overview__bar-date.is-today {
  color: #db6310;
}
.mobile-analytics-overview__bar-chart.is-dense .mobile-analytics-overview__bar-track {
  border-left-color: var(--line);
}
.mobile-analytics-overview__bar-chart.is-dense .mobile-analytics-overview__bar-track > i {
  border-radius: 4px;
}
.mobile-analytics-overview__chart-caption {
  min-height: 18px;
  margin: 7px 0 0;
  color: var(--muted);
  font-size: 10px;
  text-align: center;
  line-height: 1.7;
}
.mobile-analytics-overview__metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 26px 16px 0;
}
.mobile-analytics-overview__metrics dt {
  color: var(--muted);
  font-size: 12px;
}
.mobile-analytics-overview__metrics dd {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 8px 0 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.mobile-analytics-overview__metrics small {
  color: var(--muted);
  font-size: 10px;
  font-weight: 400;
}
.mobile-analytics-overview__line-chart {
  margin-top: 5px;
}
.mobile-analytics-overview__line-chart svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}
.mobile-analytics-overview__grid line {
  stroke: var(--line);
  stroke-width: 1;
}
.mobile-analytics-overview__grid text {
  fill: var(--muted);
  font-size: 8px;
}
.mobile-analytics-overview__line-chart g[role='button'] {
  cursor: pointer;
}
.mobile-analytics-overview__practice-link {
  display: flex;
  justify-content: center;
  align-items: center;
  min-width: 0;
  min-height: 28px;
  gap: 2px;
  padding: 0 16px;
  color: var(--muted);
  font-size: 10px;
  text-decoration: none;
}
.mobile-analytics-overview__practice-link > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.mobile-analytics-overview__note {
  margin: 22px 0 0;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.7;
  text-align: center;
}
.mobile-analytics-overview__chart-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 235px;
  margin: 0;
  color: var(--muted);
  font-size: 11px;
  text-align: center;
}
.mobile-analytics-overview__chart-status p {
  margin: 12px 0 0;
  line-height: 1.7;
}
.mobile-analytics-overview__error {
  padding: 28px 16px;
  border-radius: 14px;
  background: var(--panel);
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}
.mobile-analytics-overview__error p {
  margin: 0 0 12px;
}
.mobile-analytics-overview__mastery-link {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  padding: 0 16px;
  margin-top: 16px;
  border-radius: 14px;
  background: var(--panel);
  color: var(--text-secondary);
  font-size: 13px;
  text-decoration: none;
}
.mobile-analytics-overview__mastery-link > .mobile-icon:first-child {
  color: #ab97b3;
}
.mobile-analytics-overview__mastery-link > span {
  flex: 1;
}
.mobile-analytics-overview__mastery-link > .mobile-icon:last-child {
  color: var(--muted);
}
.mobile-analytics-overview button:focus-visible,
.mobile-analytics-overview a:focus-visible,
.mobile-analytics-overview input:focus-visible,
.mobile-analytics-overview select:focus-visible {
  outline: 2px solid #df6411;
  outline-offset: 2px;
}
</style>
