<template>
  <section class="practice-count-trend" aria-labelledby="practice-count-title">
    <div class="count-heading">
      <h2 id="practice-count-title">练习次数趋势</h2>
      <p v-if="trend">
        {{ granularityLabel }} · {{ trend.startDate }} 至 {{ trend.endDate }} · 统计已提交的练习，包含待评分记录。
      </p>
    </div>
    <template v-if="trend">
      <p v-if="trend.undatedCount > 0" class="count-note">
        {{ trend.undatedCount }} 次记录因提交日期缺失或异常未计入时间轴。
      </p>
      <div v-if="trend.points.length > PAGE_SIZE" class="count-pagination">
        <el-button :disabled="page === 0" @click="page--">较早日期</el-button>
        <span
          >显示第 {{ page * PAGE_SIZE + 1 }}–{{ Math.min((page + 1) * PAGE_SIZE, trend.points.length) }} 段，共
          {{ trend.points.length }} 段</span
        >
        <el-button :disabled="page >= lastPage" @click="page++">较近日期</el-button>
      </div>
      <TrendLineChart
        :points="chartPoints"
        :max="axisMax"
        integer-ticks
        unit="次"
        aria-label="练习次数趋势，纵轴为练习次数，每个点为一个日期区间"
      >
        <template #caption="{ point }"
          ><span>{{ point.fullRange }}</span
          ><strong>{{ point.value }} 次练习</strong></template
        >
        <template #empty><p>当前范围暂无可定位到日期的练习记录。</p></template>
      </TrendLineChart>
      <details
        v-if="chartPoints.length"
        class="count-details"
        @toggle="detailsOpen = ($event.target as HTMLDetailsElement).open"
      >
        <summary>查看日期明细（当前 {{ chartPoints.length }} 段）</summary>
        <table v-if="detailsOpen">
          <caption>
            当前时间轴的练习次数
          </caption>
          <thead>
            <tr>
              <th scope="col">日期范围</th>
              <th scope="col">练习次数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="point in chartPoints" :key="point.id">
              <td>{{ point.fullRange }}</td>
              <td>{{ point.value }} 次</td>
            </tr>
          </tbody>
        </table>
      </details>
    </template>
    <p v-else class="count-note">暂无练习次数趋势数据，请稍后刷新。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PracticeCountTrend } from '@/api/modules/learningAnalytics'
import TrendLineChart from './TrendLineChart.vue'
const props = defineProps<{ trend?: PracticeCountTrend | null }>()
const PAGE_SIZE = 120
const page = ref(0)
const detailsOpen = ref(false)
const lastPage = computed(() => Math.max(0, Math.ceil((props.trend?.points.length || 0) / PAGE_SIZE) - 1))
watch(
  () => props.trend,
  () => {
    page.value = lastPage.value
  },
  { immediate: true }
)
const granularityLabel = computed(
  () => ({ DAY: '按日汇总', WEEK: '按周汇总', MONTH: '按月汇总' })[props.trend?.granularity || 'DAY']
)
const chartPoints = computed(() =>
  (props.trend?.points || []).slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE).map((point) => {
    const fullRange = point.startDate === point.endDate ? point.startDate : `${point.startDate} 至 ${point.endDate}`
    return {
      id: point.startDate,
      label: props.trend?.granularity === 'MONTH' ? point.startDate.slice(0, 7) : point.startDate.slice(5),
      value: point.count,
      fullRange,
      ariaLabel: `${fullRange}，${point.count} 次练习`
    }
  })
)
const axisMax = computed(() =>
  (props.trend?.points || []).reduce((maximum, point) => Math.max(maximum, Math.ceil(point.count)), 1)
)
</script>

<style scoped>
.practice-count-trend {
  padding-top: 28px;
}
.count-heading h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.count-heading p,
.count-note {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.count-pagination {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: 14px 0;
  font-size: 12px;
  color: var(--muted);
}
.count-details {
  margin: 12px 0 24px;
  color: var(--muted);
  font-size: 12px;
}
.count-details summary {
  cursor: pointer;
  color: var(--brand);
  padding-block: 10px;
}
.count-details table {
  width: 100%;
  border-collapse: collapse;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.count-details caption {
  text-align: left;
  padding-block: 8px;
  color: var(--muted);
}
.count-details th,
.count-details td {
  padding: 10px 4px;
  text-align: left;
  border-bottom: 1px solid var(--line);
  overflow-wrap: anywhere;
}
.count-details th:last-child,
.count-details td:last-child {
  text-align: right;
  white-space: nowrap;
}
@media (max-width: 520px) {
  .count-heading h2 {
    font-size: 16px;
  }
  .count-pagination span {
    order: 1;
    flex-basis: 100%;
  }
}
</style>
