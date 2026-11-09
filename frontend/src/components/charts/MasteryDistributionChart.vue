<template>
  <section class="mastery-distribution" aria-label="知识点掌握分布">
    <ul class="mastery-distribution__rows">
      <li v-for="row in rows" :key="row.level" class="mastery-distribution__row">
        <span class="mastery-distribution__label">{{ row.label }}</span>
        <span class="mastery-distribution__track" aria-hidden="true">
          <span
            class="mastery-distribution__bar"
            :class="{ 'mastery-distribution__bar--pending': row.level === 'INSUFFICIENT' }"
            :style="{ width: `${row.percent}%` }"
          />
        </span>
        <span class="mastery-distribution__count">{{ formatNumber(row.count) }}</span>
      </li>
    </ul>
    <dl class="mastery-distribution__metrics">
      <div>
        <dt>平均掌握度</dt>
        <dd>
          {{ average === null ? '—' : formatNumber(average) }}<small v-if="average !== null">%</small>
          <span class="mastery-distribution__metric-note">仅已评估</span>
        </dd>
      </div>
      <div>
        <dt>知识点</dt>
        <dd>{{ formatNumber(total) }}<small>个</small></dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { KnowledgeMasteryOverviewPayload } from '@/api/modules/knowledgeMastery'
import { MASTERY_LEVELS, type MasteryLevel } from '@/utils/mastery'

type MasteryOverview = Pick<
  KnowledgeMasteryOverviewPayload,
  | 'totalKnowledgePoints'
  | 'masteredCount'
  | 'goodCount'
  | 'weakCount'
  | 'riskCount'
  | 'insufficientCount'
  | 'averageMasteryPercent'
  | 'assessedKnowledgePointCount'
>

const props = defineProps<{ overview: MasteryOverview }>()
const countKeys: Record<
  MasteryLevel,
  keyof Pick<MasteryOverview, 'masteredCount' | 'goodCount' | 'weakCount' | 'riskCount' | 'insufficientCount'>
> = {
  MASTERED: 'masteredCount',
  GOOD: 'goodCount',
  WEAK: 'weakCount',
  RISK: 'riskCount',
  INSUFFICIENT: 'insufficientCount'
}
const countValue = (value: number) => (Number.isFinite(value) ? Math.max(0, value) : 0)
const total = computed(() => countValue(props.overview.totalKnowledgePoints))
const average = computed(() => {
  const value = props.overview.averageMasteryPercent
  return props.overview.assessedKnowledgePointCount > 0 && value !== null && Number.isFinite(value) ? value : null
})
const rows = computed(() =>
  MASTERY_LEVELS.map(({ level, label }) => {
    const count = countValue(props.overview[countKeys[level]])
    return { level, label, count, percent: total.value ? Math.min(100, (count / total.value) * 100) : 0 }
  })
)
const numberFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 })
const formatNumber = (value: number) => numberFormatter.format(value)
</script>

<style scoped>
.mastery-distribution {
  color: var(--text);
}
.mastery-distribution__rows {
  display: grid;
  gap: 20px;
  margin: 0;
  padding: 8px 0;
  list-style: none;
}
.mastery-distribution__row {
  display: grid;
  grid-template-columns: 4.5em minmax(0, 1fr) minmax(2em, auto);
  align-items: center;
  gap: 12px;
  min-width: 0;
  font-size: 12px;
}
.mastery-distribution__label {
  color: var(--text-secondary);
}
.mastery-distribution__track {
  display: block;
  height: 8px;
  overflow: hidden;
  border-radius: 5px;
  background: var(--bg-secondary);
}
.mastery-distribution__bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--mobile-accent, var(--accent));
}
.mastery-distribution__bar--pending {
  background: var(--muted);
}
.mastery-distribution__count {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.mastery-distribution__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 28px 0 0;
}
.mastery-distribution__metrics dt {
  color: var(--muted);
  font-size: 12px;
}
.mastery-distribution__metrics dd {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
  margin: 8px 0 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.mastery-distribution__metrics small,
.mastery-distribution__metric-note {
  color: var(--muted);
  font-size: 11px;
  font-weight: 400;
}
.mastery-distribution__metric-note {
  flex-basis: 100%;
  margin: 7px 0 0;
}
</style>
