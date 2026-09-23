<template>
  <div class="donut-chart">
    <div class="donut-chart__ring" :style="ringStyle" aria-hidden="true">
      <div>
        <strong>{{ total }}</strong
        ><span>{{ centerLabel }}</span>
      </div>
    </div>
    <slot name="legend" :segments="segments">
      <ul class="donut-chart__legend" :aria-label="legendLabel">
        <li v-for="item in segments" :key="item.key">
          <i :style="{ background: item.color }" aria-hidden="true"></i>
          <span>{{ item.label }}</span>
          <strong>{{ item.count ?? '' }}</strong>
          <small>{{ formatPercent(item.percent) }}%</small>
        </li>
      </ul>
    </slot>
  </div>
</template>

<script lang="ts">
export interface DonutSegment {
  key: string
  label: string
  /** 占比 0 – 100，各段之和按 100 处理：不足 100 时最后一段补齐，超出时截断 */
  percent: number
  count?: number
  color: string
}
</script>

<script setup lang="ts">
import { computed } from 'vue'

// 环形占比图：conic-gradient 圆环 + 中心总数 + 图例；图例中的数字即文本形式的数据
const props = withDefaults(
  defineProps<{
    segments: DonutSegment[]
    total: string | number
    centerLabel?: string
    /** 图例的无障碍名称 */
    legendLabel?: string
  }>(),
  { centerLabel: '', legendLabel: '占比分布' }
)

const formatPercent = (value: number) => Math.round((Number(value) || 0) * 10) / 10

const ringStyle = computed(() => {
  const percents = props.segments.map((item) => Math.max(0, Number(item.percent) || 0))
  const lastFilledIndex = percents.reduce((found, value, index) => (value > 0 ? index : found), -1)
  if (lastFilledIndex < 0) return { background: 'var(--line)' }
  let cursor = 0
  const stops = props.segments.map((item, index) => {
    const start = Math.min(100, cursor)
    cursor += percents[index]
    // 四舍五入后总和不足 100 时，最后一段有值的扇区补到 100；超过 100 时截断在 100
    const end = index === lastFilledIndex ? 100 : Math.min(100, cursor)
    return item.color + ' ' + start + '% ' + end + '%'
  })
  return { background: 'conic-gradient(' + stops.join(', ') + ')' }
})
</script>

<style scoped>
.donut-chart {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  align-items: center;
  gap: 30px;
  min-height: 165px;
}
.donut-chart__ring {
  position: relative;
  display: grid;
  width: 140px;
  height: 140px;
  place-items: center;
  border-radius: 50%;
}
.donut-chart__ring::before {
  position: absolute;
  inset: 11px;
  border-radius: 50%;
  background: var(--bg);
  content: '';
}
.donut-chart__ring > div {
  position: relative;
  display: grid;
  gap: 3px;
  justify-items: center;
}
.donut-chart__ring strong {
  font-size: 30px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}
.donut-chart__ring span {
  color: var(--muted);
  font-size: 12px;
}
.donut-chart__legend {
  display: grid;
  gap: 18px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.donut-chart__legend li {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto 38px;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.donut-chart__legend i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.donut-chart__legend strong {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.donut-chart__legend small {
  text-align: right;
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1000px) {
  .donut-chart {
    grid-template-columns: 114px minmax(0, 1fr);
    gap: 20px;
  }
  .donut-chart__ring {
    width: 114px;
    height: 114px;
  }
}
@media (max-width: 760px) {
  .donut-chart {
    grid-template-columns: 140px minmax(0, 1fr);
    gap: 32px;
  }
  .donut-chart__ring {
    width: 140px;
    height: 140px;
  }
}
@media (max-width: 520px) {
  .donut-chart {
    grid-template-columns: 122px minmax(0, 1fr);
    gap: 24px;
  }
  .donut-chart__ring {
    width: 122px;
    height: 122px;
  }
  .donut-chart__legend li {
    gap: 7px;
    grid-template-columns: 7px minmax(0, 1fr) auto 34px;
  }
  .donut-chart__legend small {
    font-size: 11px;
  }
}
</style>
