<template>
  <div class="bar-row">
    <div class="bar-row__label"><strong>{{ label }}</strong><span v-if="meta">{{ meta }}</span><b>{{ value }}</b></div>
    <div class="bar-row__track" aria-hidden="true"><span :style="{ width: clampedPercent + '%', background: color }"></span></div>
    <small v-if="note">{{ note }}</small>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

// 带标签的横向条形：标签、说明、数值文本 + 百分比进度条；数值以文本呈现，进度条仅作装饰
const props = withDefaults(defineProps<{
  label: string
  /** 右侧显示的数值文本，如 "82%" */
  value: string | number
  /** 条形长度，0 – 100 */
  percent: number
  /** 标签旁的补充说明 */
  meta?: string
  /** 条形下方的备注 */
  note?: string
  color?: string
}>(), { meta: '', note: '', color: 'var(--brand)' })

const clampedPercent = computed(() => Math.min(100, Math.max(0, Number(props.percent) || 0)))
</script>

<style scoped>
.bar-row__label { display: flex; align-items: baseline; gap: 12px; margin-bottom: 9px; }
.bar-row__label strong { font-size: 13px; font-weight: 500; }
.bar-row__label span { color: var(--muted); font-size: 12px; }
.bar-row__label b { margin-left: auto; font-size: 13px; font-weight: 500; font-variant-numeric: tabular-nums; }
.bar-row__track { height: 5px; overflow: hidden; border-radius: 4px; background: var(--line); }
.bar-row__track span { display: block; height: 100%; border-radius: inherit; }
.bar-row > small { display: block; margin-top: 6px; color: var(--muted); font-size: 11px; text-align: right; }
@media (max-width: 1000px) {
  .bar-row__label { gap: 8px; flex-wrap: wrap; }
  .bar-row__label span { order: 3; flex-basis: 100%; }
}
@media (max-width: 760px) {
  .bar-row__label span { order: 0; flex-basis: auto; }
}
</style>
