<template>
  <figure v-if="points.length" class="trend-chart">
    <div class="trend-chart__frame">
      <div class="trend-chart__y-axis" aria-hidden="true">
        <span v-for="tick in axisTicks" :key="tick.value" :style="{ top: tick.y + 'px' }">{{ tick.value }}{{ unit }}</span>
      </div>
      <div ref="plotRef" class="trend-chart__plot">
        <svg :viewBox="'0 0 ' + size.width + ' ' + size.height" :width="size.width" :height="size.height" role="group" :aria-label="ariaLabel">
          <line v-for="tick in axisTicks" :key="tick.value" :x1="plot.left" :y1="tick.y" :x2="plot.right" :y2="tick.y" class="trend-chart__grid" aria-hidden="true" />
          <polyline :points="linePoints" class="trend-chart__line" aria-hidden="true" />
          <circle
            v-for="point in plotPoints"
            :key="point.id"
            :cx="point.x"
            :cy="point.y"
            :r="point.id === activePoint?.id ? 5 : 4"
            class="trend-chart__dot"
            :class="{ 'is-active': point.id === activePoint?.id }"
            role="img"
            tabindex="0"
            :aria-label="point.ariaLabel || point.label + ' ' + point.value + unit"
            @mouseenter="select(point)"
            @focus="select(point)"
          ><title>{{ point.ariaLabel || point.label + ' ' + point.value + unit }}</title></circle>
        </svg>
      </div>
    </div>
    <div class="trend-chart__x-axis" aria-hidden="true">
      <span v-for="(point, index) in axisLabels" :key="point.id" :class="{ 'is-first': index === 0 && axisLabels.length > 1, 'is-last': index === axisLabels.length - 1 && axisLabels.length > 1 }" :style="{ left: point.x + 'px' }">{{ point.label }}</span>
    </div>
    <figcaption class="trend-chart__caption" aria-live="polite" aria-atomic="true">
      <slot v-if="activePoint" name="caption" :point="activePoint"><span>{{ activePoint.label }}</span><strong>{{ activePoint.value }}{{ unit }}</strong></slot>
    </figcaption>
  </figure>
  <div v-else class="trend-chart__empty"><slot name="empty"><p>暂无数据。</p></slot></div>
</template>

<script lang="ts">
export interface TrendChartPoint {
  id: string | number
  /** 横轴文字，如日期 */
  label: string
  value: number
  /** 读屏与 title 使用的完整描述 */
  ariaLabel?: string
}
</script>

<script setup lang="ts" generic="T extends TrendChartPoint">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'

// 折线趋势图：容器尺寸由 ResizeObserver 实时测量，viewBox 与像素尺寸一致，因此圆点不会被拉伸成椭圆
const props = withDefaults(defineProps<{
  points: T[]
  max?: number
  unit?: string
  ariaLabel?: string
}>(), { max: 100, unit: '%', ariaLabel: '趋势图' })

const emit = defineEmits<{ select: [point: T] }>()
const selectedId = defineModel<string | number | null>('selectedId', { default: null })

defineSlots<{
  caption?(props: { point: T }): unknown
  empty?(): unknown
}>()

const PAD_X = 12
const PAD_TOP = 30
const PAD_BOTTOM = 35

const plotRef = ref<HTMLElement | null>(null)
const size = reactive({ width: 640, height: 240 })
let observer: ResizeObserver | null = null
let usingWindowResize = false

const measure = () => {
  const el = plotRef.value
  if (!el) return
  size.width = Math.max(1, Math.round(el.clientWidth))
  size.height = Math.max(1, Math.round(el.clientHeight))
}

const stopObserving = () => {
  observer?.disconnect()
  observer = null
  if (usingWindowResize) {
    window.removeEventListener('resize', measure)
    usingWindowResize = false
  }
}

watch(plotRef, (el) => {
  stopObserving()
  if (!el) return
  measure()
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => measure())
    observer.observe(el)
  } else {
    window.addEventListener('resize', measure)
    usingWindowResize = true
  }
}, { flush: 'post' })

onBeforeUnmount(stopObserving)

const plot = computed(() => ({
  left: PAD_X,
  right: Math.max(PAD_X, size.width - PAD_X),
  top: PAD_TOP,
  bottom: Math.max(PAD_TOP, size.height - PAD_BOTTOM)
}))

const safeMax = computed(() => Math.max(1, Number(props.max) || 100))
const clamp = (value: number) => Math.min(safeMax.value, Math.max(0, Number(value) || 0))
const toY = (value: number) => plot.value.bottom - (plot.value.bottom - plot.value.top) * clamp(value) / safeMax.value

const axisTicks = computed(() => [safeMax.value, safeMax.value / 2, 0].map((value) => ({ value: Math.round(value * 10) / 10, y: toY(value) })))

const plotPoints = computed(() => {
  const list = props.points
  const { left, right } = plot.value
  return list.map((point, index) => ({
    ...point,
    x: list.length === 1 ? (left + right) / 2 : left + (right - left) * index / (list.length - 1),
    y: toY(point.value)
  }))
})

const linePoints = computed(() => plotPoints.value.map((point) => point.x + ',' + point.y).join(' '))

// 横轴只标注首、中、末三个点，避免文字重叠
const axisLabels = computed(() => {
  const list = plotPoints.value
  if (list.length <= 3) return list
  return Array.from({ length: 3 }, (_, index) => list[Math.round(index * (list.length - 1) / 2)])
})

const activePoint = computed<T | undefined>(() => props.points.find((point) => point.id === selectedId.value) || props.points[props.points.length - 1])

const select = (point: T) => {
  selectedId.value = point.id
  emit('select', point)
}
</script>

<style scoped>
.trend-chart { margin: 0; }
.trend-chart__frame { position: relative; padding-left: 43px; }
.trend-chart__plot { height: 240px; }
.trend-chart__plot svg { display: block; width: 100%; height: 100%; overflow: visible; }
.trend-chart__y-axis { position: absolute; inset: 0 auto 0 0; width: 34px; color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.trend-chart__y-axis span { position: absolute; right: 0; transform: translateY(-50%); }
.trend-chart__grid { stroke: var(--line); stroke-width: 1; }
.trend-chart__line { fill: none; stroke: color-mix(in srgb, var(--brand) 45%, transparent); stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.trend-chart__dot { fill: color-mix(in srgb, var(--brand) 70%, var(--panel)); stroke: var(--panel); stroke-width: 2; cursor: crosshair; }
.trend-chart__dot.is-active { fill: var(--brand); }
.trend-chart__dot:focus { outline: none; stroke: color-mix(in srgb, var(--accent) 45%, transparent); stroke-width: 5; }
.trend-chart__x-axis { position: relative; height: 23px; margin: 2px 0 0 43px; color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.trend-chart__x-axis span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
.trend-chart__x-axis .is-first { transform: none; }
.trend-chart__x-axis .is-last { transform: translateX(-100%); }
.trend-chart__caption { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 9px 22px; min-height: 44px; padding-top: 15px; color: var(--muted); font-size: 12px; line-height: 1.6; }
.trend-chart__caption > span, .trend-chart__caption :slotted(span) { min-width: 0; overflow-wrap: anywhere; }
.trend-chart__caption > strong, .trend-chart__caption :slotted(strong) { margin-left: auto; color: var(--text-secondary); font-weight: 500; white-space: nowrap; }
.trend-chart__empty { display: grid; min-height: 220px; align-content: center; justify-items: center; gap: 12px; color: var(--muted); }
.trend-chart__empty :deep(p) { margin: 0; color: var(--muted); font-size: 13px; }
@media (max-width: 520px) {
  .trend-chart__frame { padding-left: 31px; }
  .trend-chart__plot { height: 210px; }
  .trend-chart__y-axis { width: 25px; font-size: 11px; }
  .trend-chart__x-axis { margin-left: 31px; font-size: 11px; }
  .trend-chart__caption > span, .trend-chart__caption :slotted(span) { flex-basis: 100%; }
  .trend-chart__caption > strong, .trend-chart__caption :slotted(strong) { margin-left: 0; }
}
</style>
