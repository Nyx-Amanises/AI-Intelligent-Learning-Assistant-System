<template>
  <section
    class="mobile-study-calendar"
    :class="{ 'mobile-study-calendar--compact': compact }"
    :aria-busy="loading"
    :aria-label="compact ? '本周学习日历' : '学习日历'"
  >
    <RouterLink
      v-if="compact"
      class="mobile-study-calendar__heading"
      :to="{ path: '/analytics', query: { view: 'calendar' } }"
    >
      <h3>学习日历</h3>
      <span
        >{{ loading ? '正在加载' : error ? '' : `本周练习 ${activeDays} 天` }}<MobileIcon name="next" :size="17"
      /></span>
    </RouterLink>
    <div v-else class="mobile-study-calendar__month">
      <button type="button" aria-label="上个月" :disabled="month <= '1000-01'" @click="changeMonth(-1)">
        <MobileIcon name="next" class="mobile-study-calendar__previous" :size="22" />
      </button>
      <h2>{{ monthLabel }}</h2>
      <button type="button" aria-label="下个月" :disabled="month >= today.slice(0, 7)" @click="changeMonth(1)">
        <MobileIcon name="next" :size="22" />
      </button>
    </div>
    <div class="mobile-study-calendar__weekdays" aria-hidden="true">
      <span v-for="day in weekdays" :key="day">{{ day }}</span>
    </div>
    <div class="mobile-study-calendar__dates">
      <template v-for="(date, index) in displayedDates" :key="date || `empty-${index}`">
        <span v-if="!date" />
        <button
          v-else
          type="button"
          :class="{
            'is-today': date === today,
            'is-future': date > today,
            'has-practice': countForDate(date) > 0,
            'is-selected': !compact && selectedDate === date
          }"
          :aria-label="`${date}${date === today ? '，今天' : ''}${loading || error ? '' : `，${countForDate(date)} 次练习`}`"
          :aria-current="date === today ? 'date' : undefined"
          :aria-pressed="compact ? undefined : selectedDate === date"
          :disabled="date > today || loading || Boolean(error)"
          @click="selectDate(date)"
        >
          <span>{{ date === today ? '今' : Number(date.slice(-2)) }}</span
          ><i v-if="countForDate(date) > 0" aria-hidden="true" />
        </button>
      </template>
    </div>
    <div v-if="error" class="mobile-study-calendar__error" role="alert">
      <span>学习日历暂时无法加载</span><button type="button" @click="loadCalendar">重试</button>
    </div>
    <template v-else-if="!compact">
      <p class="mobile-study-calendar__selection" aria-live="polite">
        {{
          loading
            ? '正在读取练习记录…'
            : selectedDate
              ? `${Number(selectedDate.slice(5, 7))} 月 ${Number(selectedDate.slice(-2))} 日 · ${countForDate(selectedDate)} 次练习`
              : '选择日期查看练习次数'
        }}
      </p>
      <dl class="mobile-study-calendar__metrics">
        <div>
          <dt>本月学习</dt>
          <dd>{{ loading ? '—' : activeDays }}<small>天</small></dd>
        </div>
        <div>
          <dt>完成练习</dt>
          <dd>{{ loading ? '—' : totalCount }}<small>次</small></dd>
        </div>
      </dl>
      <p class="mobile-study-calendar__note"><i aria-hidden="true" />标记有已提交练习的日期</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import MobileIcon from '@/components/MobileIcon.vue'
import { getLearningAnalyticsOverviewApi, type PracticeCountTrend } from '@/api/modules/learningAnalytics'

const props = withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
const router = useRouter()
const weekdays = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
const dateParts = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
}).formatToParts(new Date())
const part = (name: string) => dateParts.find((item) => item.type === name)?.value || ''
const today = `${part('year')}-${part('month')}-${part('day')}`
const month = ref(today.slice(0, 7))
const selectedDate = ref(today)
const loading = ref(false)
const error = ref(false)
const trend = ref<PracticeCountTrend | null>(null)
let requestId = 0
const dateFromIso = (value: string) => new Date(`${value}T00:00:00Z`)
const isoFromDate = (value: Date) => value.toISOString().slice(0, 10)
const addDays = (value: string, amount: number) => {
  const date = dateFromIso(value)
  date.setUTCDate(date.getUTCDate() + amount)
  return isoFromDate(date)
}
const weekStart = computed(() => addDays(today, -((dateFromIso(today).getUTCDay() + 6) % 7)))
const monthLabel = computed(() => `${Number(month.value.slice(0, 4))}年${Number(month.value.slice(5, 7))}月`)
const displayedDates = computed(() => {
  if (props.compact) return Array.from({ length: 7 }, (_, index) => addDays(weekStart.value, index))
  const firstDate = `${month.value}-01`
  const daysInMonth = new Date(
    Date.UTC(Number(month.value.slice(0, 4)), Number(month.value.slice(5, 7)), 0)
  ).getUTCDate()
  return [
    ...Array<string>((dateFromIso(firstDate).getUTCDay() + 6) % 7).fill(''),
    ...Array.from({ length: daysInMonth }, (_, index) => `${month.value}-${String(index + 1).padStart(2, '0')}`)
  ]
})
const counts = computed(
  () =>
    new Map(
      (trend.value?.granularity === 'DAY' ? trend.value.points : [])
        .filter((point) => point.startDate <= today && displayedDates.value.includes(point.startDate))
        .map((point) => [point.startDate, point.count])
    )
)
const countForDate = (date: string) => counts.value.get(date) || 0
const activeDays = computed(() => [...counts.value.values()].filter((count) => count > 0).length)
const totalCount = computed(() => [...counts.value.values()].reduce((sum, count) => sum + count, 0))
const loadCalendar = async () => {
  const currentRequest = ++requestId
  loading.value = true
  error.value = false
  trend.value = null
  const startDate = props.compact ? weekStart.value : `${month.value}-01`
  const dates = displayedDates.value.filter(Boolean)
  const lastDate = dates[dates.length - 1]
  try {
    const response = await getLearningAnalyticsOverviewApi({
      startDate,
      endDate: lastDate > today ? today : lastDate,
      trendLimit: 5
    })
    if (currentRequest !== requestId) return
    const result = response.data.data.practiceCountTrend as PracticeCountTrend | null
    if (!result || result.granularity !== 'DAY') throw new Error('Daily practice data unavailable')
    trend.value = result
  } catch {
    if (currentRequest === requestId) error.value = true
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}
const changeMonth = (delta: number) => {
  const date = dateFromIso(`${month.value}-01`)
  date.setUTCMonth(date.getUTCMonth() + delta)
  const nextMonth = isoFromDate(date).slice(0, 7)
  if (nextMonth > today.slice(0, 7) || nextMonth < '1000-01') return
  month.value = nextMonth
  selectedDate.value = month.value === today.slice(0, 7) ? today : `${month.value}-01`
}
const selectDate = (date: string) => {
  if (props.compact) {
    void router.push({ path: '/analytics', query: { view: 'calendar' } })
    return
  }
  selectedDate.value = date
}
watch(
  month,
  () => {
    void loadCalendar()
  },
  { immediate: true }
)
onUnmounted(() => {
  requestId++
})
</script>

<style scoped>
.mobile-study-calendar {
  padding: 16px 16px 20px;
  overflow: hidden;
  background: var(--panel);
  border-radius: 14px;
  color: var(--text);
}
.mobile-study-calendar__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  gap: 8px;
  color: inherit;
  text-decoration: none;
}
.mobile-study-calendar__heading h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
}
.mobile-study-calendar__heading > span {
  display: flex;
  align-items: center;
  gap: 2px;
  color: var(--muted);
  font-size: 10px;
}
.mobile-study-calendar__month {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 18px;
  padding: 10px 0 18px;
  border-bottom: 1px solid var(--line);
}
.mobile-study-calendar__month h2 {
  min-width: 116px;
  margin: 0;
  text-align: center;
  font-size: 16px;
  font-weight: 500;
}
.mobile-study-calendar__month button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
}
.mobile-study-calendar__month button:disabled {
  color: var(--line-strong);
}
.mobile-study-calendar__previous {
  transform: rotate(180deg);
}
.mobile-study-calendar__weekdays,
.mobile-study-calendar__dates {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}
.mobile-study-calendar__weekdays {
  margin-top: 20px;
  color: var(--muted);
  font-size: 10px;
  text-align: center;
}
.mobile-study-calendar__dates {
  grid-auto-rows: 60px;
  margin-top: 8px;
}
.mobile-study-calendar__dates button {
  display: flex;
  position: relative;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 14px;
  font-weight: 500;
}
.mobile-study-calendar__dates button > span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 50%;
}
.mobile-study-calendar__dates button.is-selected:not(.is-today) > span {
  border-color: #f4b082;
  background: var(--brand-light);
}
.mobile-study-calendar__dates button.is-today > span {
  color: #fff;
  background: #ff791f;
}
.mobile-study-calendar__dates button.is-future {
  color: var(--line-strong);
}
.mobile-study-calendar__dates button i {
  position: absolute;
  bottom: 6px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #ff791f;
}
.mobile-study-calendar__selection {
  min-height: 36px;
  margin: 14px 0 0;
  color: var(--muted);
  font-size: 11px;
  text-align: center;
  line-height: 36px;
}
.mobile-study-calendar__metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 22px 16px 0;
}
.mobile-study-calendar__metrics dt {
  color: var(--muted);
  font-size: 12px;
}
.mobile-study-calendar__metrics dd {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 10px 0 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1;
}
.mobile-study-calendar__metrics small {
  color: var(--muted);
  font-size: 11px;
  font-weight: 400;
}
.mobile-study-calendar__note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin: 27px 0 0;
  color: var(--muted);
  font-size: 10px;
}
.mobile-study-calendar__note i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #ff791f;
}
.mobile-study-calendar__error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 11px;
}
.mobile-study-calendar__error button {
  min-height: 36px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  color: #c5540b;
  font: inherit;
}
.mobile-study-calendar--compact {
  padding-top: 12px;
  padding-bottom: 12px;
}
.mobile-study-calendar--compact .mobile-study-calendar__weekdays {
  margin-top: 15px;
}
.mobile-study-calendar--compact .mobile-study-calendar__dates {
  grid-auto-rows: 44px;
  margin-top: 4px;
}
.mobile-study-calendar--compact .mobile-study-calendar__dates button {
  font-size: 12px;
}
.mobile-study-calendar--compact .mobile-study-calendar__dates button > span {
  width: 26px;
  height: 26px;
}
.mobile-study-calendar--compact .mobile-study-calendar__dates button i {
  bottom: 1px;
}
.mobile-study-calendar button:focus-visible,
.mobile-study-calendar a:focus-visible {
  outline: 2px solid #df6411;
  outline-offset: 2px;
}
</style>
