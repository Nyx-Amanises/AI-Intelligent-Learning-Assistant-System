<template>
  <section class="week-card surface" aria-labelledby="week-card-title">
    <header class="week-card__head">
      <h2 id="week-card-title">近 7 天</h2>
      <span v-if="rangeLabel">{{ rangeLabel }}</span>
    </header>

    <div v-if="loading" class="week-card__loading" aria-hidden="true">
      <span class="skeleton" />
      <span class="skeleton" />
    </div>
    <div v-else-if="error" class="week-card__error" role="alert">
      <p>统计没有加载出来</p>
      <button type="button" class="text-link" @click="emit('reload')">重新加载</button>
    </div>
    <template v-else>
      <p class="week-card__total">
        <strong>{{ totalCount }}</strong
        ><span>次练习</span>
      </p>
      <div class="week-card__chart" role="img" :aria-label="chartLabel">
        <div v-for="(day, index) in days" :key="day.date" class="week-card__day" :class="{ 'is-today': day.isToday }">
          <span class="week-card__track">
            <span
              class="week-card__bar"
              :class="{ 'is-empty': !day.count }"
              :style="{ height: barHeight(day.count), '--bar': index }"
            />
          </span>
          <small>{{ day.label }}</small>
        </div>
      </div>
      <dl class="week-card__stats">
        <div>
          <dt>正确率</dt>
          <dd>{{ accuracyText }}</dd>
        </div>
        <div>
          <dt>练习时长</dt>
          <dd>{{ durationText }}</dd>
        </div>
        <div>
          <dt>作答</dt>
          <dd>{{ attempts }} 题</dd>
        </div>
      </dl>
      <p v-if="!totalCount" class="week-card__hint">完成练习后，这里会显示学习趋势。</p>
    </template>
    <RouterLink to="/analytics" class="text-link week-card__more"
      >学习统计<AppIcon name="arrow-right" :size="15"
    /></RouterLink>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'

interface DayPoint {
  startDate: string
  count: number
}

/** 首页“近 7 天”：每天的交卷次数柱状图，加上正确率、时长和作答题数。 */
const props = defineProps<{
  loading: boolean
  error: boolean
  points: DayPoint[]
  accuracyRate: number
  studySeconds: number
  attempts: number
}>()
const emit = defineEmits<{ reload: [] }>()

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
// 接口返回的日期是北京时间的自然日，按 UTC 解析只取星期，避免浏览器时区导致错位。
const weekdayOf = (date: string) => WEEKDAYS[new Date(`${date}T00:00:00Z`).getUTCDay()] ?? ''
const shortDate = (date: string) => `${Number(date.slice(5, 7))}月${Number(date.slice(8, 10))}日`

const days = computed(() =>
  props.points.map((point, index) => {
    const isToday = index === props.points.length - 1
    return { date: point.startDate, count: point.count, isToday, label: isToday ? '今' : weekdayOf(point.startDate) }
  })
)
const totalCount = computed(() => props.points.reduce((sum, point) => sum + point.count, 0))
const peak = computed(() => Math.max(1, ...props.points.map((point) => point.count)))
const barHeight = (count: number) => (count ? `${Math.max(12, Math.round((count / peak.value) * 100))}%` : '4px')
const rangeLabel = computed(() =>
  props.points.length ? `${shortDate(props.points[0].startDate)} – ${shortDate(props.points.at(-1)!.startDate)}` : ''
)
const accuracyText = computed(() => (props.attempts ? `${Math.round(props.accuracyRate)}%` : '—'))
const durationText = computed(() => {
  const minutes = props.studySeconds > 0 ? Math.max(1, Math.round(props.studySeconds / 60)) : 0
  return minutes >= 60 ? `${(minutes / 60).toFixed(1)} 小时` : `${minutes} 分钟`
})
const chartLabel = computed(
  () =>
    `近 7 天每天的练习次数：` +
    days.value.map((day) => `${day.isToday ? '今天' : '星期' + day.label} ${day.count} 次`).join('，')
)
</script>
<style scoped>
.week-card {
  display: flex;
  flex-direction: column;
  padding: 22px 24px 18px;
  border-radius: 18px;
}
.week-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.week-card__head h2 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
}
.week-card__head span {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.week-card__total {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 10px 0 0;
}
.week-card__total strong {
  color: var(--text);
  font-size: 34px;
  font-weight: 600;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.week-card__total span {
  color: var(--muted);
  font-size: 13px;
}
.week-card__chart {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}
.week-card__day {
  display: grid;
  justify-items: center;
  gap: 6px;
}
.week-card__track {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  height: 72px;
}
.week-card__bar {
  display: block;
  width: min(22px, 100%);
  border-radius: 6px 6px 3px 3px;
  background: color-mix(in srgb, var(--brand) 32%, var(--panel));
  transform-origin: bottom;
  animation: week-bar-grow 0.6s var(--ease-out) both;
  animation-delay: calc(0.12s + var(--bar, 0) * 45ms);
}
.week-card__day.is-today .week-card__bar {
  background: var(--brand);
}
.week-card__bar.is-empty {
  background: var(--line);
}
.week-card__day small {
  color: var(--muted);
  font-size: 12px;
}
.week-card__day.is-today small {
  color: var(--brand);
  font-weight: 600;
}
.week-card__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 16px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}
.week-card__stats div + div {
  padding-left: 14px;
  border-left: 1px solid var(--line);
}
.week-card__stats dt {
  color: var(--muted);
  font-size: 12px;
}
.week-card__stats dd {
  margin: 2px 0 0;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.week-card__hint {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.week-card__more {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  min-height: 44px;
  margin-top: auto;
  padding-top: 10px;
  font-size: 13px;
}
.week-card__loading {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}
.week-card__loading span:first-child {
  width: 90px;
  height: 34px;
}
.week-card__loading span:last-child {
  height: 96px;
}
.week-card__error {
  display: grid;
  justify-items: start;
  gap: 4px;
  margin: 18px 0;
  color: var(--text-secondary);
  font-size: 14px;
}
.week-card__error p {
  margin: 0;
}
@keyframes week-bar-grow {
  from {
    transform: scaleY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .week-card__bar {
    animation: none;
  }
}
</style>
