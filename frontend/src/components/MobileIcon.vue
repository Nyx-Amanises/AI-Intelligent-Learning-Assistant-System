<template>
  <svg
    class="mobile-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.85"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="path in paths" :key="path" :d="path" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ name: string; size?: number | string }>(), { size: 24 })

/** 手机端统一使用轻量的圆角线性图标，不影响桌面的图标体系。 */
const iconPaths: Record<string, string[]> = {
  study: ['M2.5 7.8 12 4l9.5 3.8L12 11.6 2.5 7.8Z', 'M5 9v8.6c3.8 2.7 8.1 3 12.2.5v-4.7L12 15.5 8.3 14'],
  archive: ['M5 3.5h14M3.5 7h17', 'M3 10.5h18l-1.6 10h-15L3 10.5Z', 'M10 16.5h4'],
  'chart-settings': [
    'M10.5 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v5.5',
    'M6.5 12.5 9 9l3 2.2 3.5-4.7',
    'M17 13.1h2l.4 1.4 1.2.7 1.4-.4 1 1.7-1 1 .1 1.4 1 .9-1 1.7-1.3-.3-1.3.7-.3 1.4h-2l-.4-1.4-1.2-.7-1.4.4-1-1.7 1-1-.1-1.4-1-.9 1-1.7 1.3.3 1.3-.7.3-1.4Z',
    'M19.5 18.4a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Z'
  ],
  mark: [
    'M11.5 7.5C9 5.9 6.1 5.7 3 6.5v13c3.1-.8 6-.6 8.5 1 2.5-1.6 5.4-1.8 8.5-1V12',
    'M11.5 7.5v13',
    'M18 2.5 19 5l2.5 1L19 7l-1 2.5L17 7l-2.5-1L17 5l1-2.5Z'
  ],
  assistant: [
    'M8.5 17v-1.3c0-2-3-3.1-3-6.2a6.5 6.5 0 0 1 13 0c0 3.1-3 4.2-3 6.2V17h-7Z',
    'M9 20h6M10.5 22h3',
    'M12 .5v1M3.5 3.5l1 1M19.5 4.5l1-1M1 10h1M22 10h1'
  ],
  back: ['M14.5 5 7.5 12l7 7'],
  down: ['M5 8.5 12 15.5l7-7'],
  next: ['M9 5.5 15.5 12 9 18.5'],
  search: ['M17.5 10.5a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z', 'm16 16 5 5'],
  book: ['M6 3h12a2 2 0 0 1 2 2v16H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z', 'M6 3v14M6 17h14M9 7h7M9 10h4'],
  calendar: ['M5 4.5h14a2 2 0 0 1 2 2V20H3V6.5a2 2 0 0 1 2-2Z', 'M7 2v5M17 2v5M3 10h18', 'm8 15 2.5 2.5L16 13'],
  check: ['M20 10v2a8 8 0 1 1-5-7.4', 'm7.5 10.5 4 4L21 5'],
  pencil: [
    'm5 15.5 10.7-12a1.8 1.8 0 0 1 2.6-.1l2.1 1.9a1.8 1.8 0 0 1 .1 2.5L9.8 19.9 3.5 22 5 15.5Z',
    'm14 5.5 4.7 4.2M5 15.5l4.8 4.4M14 21h7'
  ],
  clock: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z', 'M12 6v6l3.5 2.5'],
  trend: ['M4 4v16h16', 'm7 15 4-6 4 2 5-7'],
  plus: ['M12 5v14M5 12h14']
}

const paths = computed(() => iconPaths[props.name] || iconPaths.study)
</script>

<style scoped>
.mobile-icon {
  display: block;
  flex: 0 0 auto;
}
</style>
