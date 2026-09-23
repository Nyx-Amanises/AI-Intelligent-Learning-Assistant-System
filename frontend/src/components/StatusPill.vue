<template>
  <span class="status-pill" :class="[`status-pill--${tone}`, { 'status-pill--plain': plain }]" role="status">
    <i
      v-if="!hideDot"
      class="status-pill__dot"
      :class="{ 'status-pill__dot--pulse': tone === 'processing' }"
      aria-hidden="true"
    />
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup lang="ts">
import type { StatusTone } from '@/utils/labels'

/**
 * 统一的状态标签：颜色只由 tone 决定，processing 会带脉冲圆点。
 * `plain` 去掉背景，只保留圆点与文字（用于紧凑的卡片顶部行）。
 */
withDefaults(
  defineProps<{
    tone?: StatusTone
    label?: string
    plain?: boolean
    hideDot?: boolean
  }>(),
  {
    tone: 'brand',
    label: '',
    plain: false,
    hideDot: false
  }
)
</script>

<style scoped>
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.6;
  white-space: nowrap;
  color: var(--brand);
  background: var(--brand-light);
}
.status-pill__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 auto;
}
.status-pill__dot--pulse {
  animation: status-pill-pulse 1.8s infinite ease-in-out;
}
.status-pill--success {
  color: var(--green);
  background: var(--green-soft);
}
.status-pill--processing {
  color: var(--accent);
  background: var(--accent-soft);
}
.status-pill--failed {
  color: var(--red);
  background: var(--red-soft);
}
.status-pill--muted {
  color: var(--muted);
  background: var(--bg-secondary);
}
.status-pill--plain {
  padding: 0;
  background: transparent;
}
@keyframes status-pill-pulse {
  0%,
  100% {
    transform: scale(0.9);
    opacity: 0.7;
  }
  50% {
    transform: scale(1.25);
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .status-pill__dot--pulse {
    animation: none;
  }
}
</style>
