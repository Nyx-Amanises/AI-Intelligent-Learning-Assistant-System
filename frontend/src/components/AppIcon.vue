<template>
  <svg
    class="app-icon"
    :width="sizeValue"
    :height="sizeValue"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <template v-if="name === 'google'">
      <path fill="#4285F4" d="M22.56 12.25c0-.77-.07-1.5-.2-2.22H12v4.2h5.92a5.06 5.06 0 0 1-2.19 3.32v2.72h3.55c2.08-1.92 3.28-4.74 3.28-8.02Z" />
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.55-2.72c-.98.66-2.23 1.04-3.73 1.04-2.86 0-5.29-1.93-6.16-4.53H2.18v2.81A11 11 0 0 0 12 23Z" />
      <path fill="#FBBC05" d="M5.84 14.13a6.61 6.61 0 0 1 0-4.26V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.81Z" />
      <path fill="#EA4335" d="M12 5.34c1.62 0 3.07.56 4.22 1.65l3.14-3.14A10.56 10.56 0 0 0 12 1 11 11 0 0 0 2.18 7.06l3.66 2.81C6.71 7.27 9.14 5.34 12 5.34Z" />
    </template>

    <template v-else-if="name === 'github'">
      <path
        fill="currentColor"
        d="M12 2.25c-5.5 0-9.95 4.45-9.95 9.95 0 4.4 2.85 8.12 6.81 9.44.5.09.68-.21.68-.48v-1.68c-2.77.6-3.36-1.19-3.36-1.19-.45-1.15-1.1-1.46-1.1-1.46-.9-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.89 1.52 2.33 1.08 2.9.83.09-.64.35-1.08.63-1.33-2.21-.25-4.54-1.1-4.54-4.91 0-1.08.39-1.97 1.02-2.66-.1-.25-.44-1.26.1-2.63 0 0 .83-.27 2.73 1.02A9.4 9.4 0 0 1 12 7.24c.84 0 1.68.11 2.48.33 1.89-1.29 2.72-1.02 2.72-1.02.55 1.37.21 2.38.1 2.63.64.69 1.02 1.58 1.02 2.66 0 3.82-2.33 4.66-4.55 4.91.36.31.68.92.68 1.85v2.75c0 .27.18.58.69.48a9.96 9.96 0 0 0 6.81-9.44c0-5.5-4.45-9.95-9.95-9.95Z"
      />
    </template>

    <template v-else-if="name === 'discord'">
      <path fill="#5865F2" d="M19.56 5.18A18.7 18.7 0 0 0 14.88 3.7l-.23.46c1.63.45 2.39 1.1 2.39 1.1a15.23 15.23 0 0 0-10.08 0s.76-.65 2.39-1.1l-.23-.46c-1.63.28-3.2.78-4.68 1.48C1.48 10.3.67 15.28 1.08 20.18A18.8 18.8 0 0 0 6.83 23l.7-.95a11.78 11.78 0 0 1-1.1-.53c.09-.07.18-.14.26-.22a13.4 13.4 0 0 0 10.62 0l.26.22c-.35.2-.72.38-1.1.53l.7.95a18.8 18.8 0 0 0 5.75-2.82c.49-5.68-.83-10.6-3.36-15Z" />
      <path fill="#fff" d="M8.62 15.68c-.82 0-1.5-.76-1.5-1.7s.66-1.7 1.5-1.7c.83 0 1.51.76 1.5 1.7 0 .94-.67 1.7-1.5 1.7Zm6.76 0c-.82 0-1.5-.76-1.5-1.7s.66-1.7 1.5-1.7c.83 0 1.51.76 1.5 1.7 0 .94-.67 1.7-1.5 1.7Z" />
    </template>

    <template v-else-if="name === 'qq'">
      <path fill="#111827" d="M12 2.4c-2.4 0-4.28 2.1-4.28 5.05 0 1.45.28 2.62.74 3.5-.78.75-1.4 1.79-1.64 2.9-.15.7.44 1.19 1.05.82l.66-.4c-.03.34-.05.68-.05 1.02 0 3.18 1.58 5.31 3.52 5.31s3.52-2.13 3.52-5.31c0-.34-.02-.68-.05-1.02l.66.4c.61.37 1.2-.12 1.05-.82-.24-1.11-.86-2.15-1.64-2.9.46-.88.74-2.05.74-3.5C16.28 4.5 14.4 2.4 12 2.4Z" />
      <path fill="#fff" d="M10.45 7.92c0 .55-.3 1-.68 1s-.68-.45-.68-1 .3-1 .68-1 .68.45.68 1Zm4.46 0c0 .55-.3 1-.68 1s-.68-.45-.68-1 .3-1 .68-1 .68.45.68 1Z" />
      <path fill="#F97316" d="M9.8 10.27c.56.42 1.17.63 2.2.63s1.64-.21 2.2-.63c.09.7-.72 1.74-2.2 1.74s-2.29-1.04-2.2-1.74Z" />
      <path fill="#EF4444" d="M8.75 13.65h6.5c.12.58.18 1.13.18 1.64H8.57c0-.51.06-1.06.18-1.64Z" />
    </template>

    <template v-else-if="fileIcon">
      <path :fill="fileIcon.paper" d="M5.2 2.8h8.1l5.5 5.5v12.9H5.2V2.8Z" />
      <path :fill="fileIcon.fold" d="M13.3 2.8v5.5h5.5L13.3 2.8Z" />
      <path :fill="fileIcon.accent" d="M5.2 2.8h8.1l5.5 5.5v12.9H5.2V2.8Z" fill-opacity="0.09" />
      <path :stroke="fileIcon.accent" d="M5.2 2.8h8.1l5.5 5.5v12.9H5.2V2.8Z" stroke-width="1.25" stroke-linejoin="round" />
      <path :stroke="fileIcon.accent" d="M13.3 2.8v5.5h5.5" stroke-width="1.25" stroke-linejoin="round" />
      <rect x="7.05" y="12.3" width="9.9" height="5.35" rx="1.35" :fill="fileIcon.accent" />
      <text
        x="12"
        y="15.95"
        fill="#fff"
        font-family="Arial, Helvetica, sans-serif"
        font-size="3.2"
        font-weight="800"
        text-anchor="middle"
        letter-spacing="0"
      >{{ fileIcon.label }}</text>
    </template>

    <template v-else>
      <path
        v-for="path in iconPaths"
        :key="path"
        :d="path"
        stroke="currentColor"
        stroke-width="1.85"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  size?: number | string
}>(), {
  size: 20
})

const sizeValue = computed(() =>
  typeof props.size === 'number' ? `${props.size}px` : props.size
)

const fileIconMap: Record<string, { label: string; accent: string; paper: string; fold: string }> = {
  'file-pdf': { label: 'PDF', accent: '#dc2626', paper: '#fff7f7', fold: '#fecaca' },
  'file-word': { label: 'DOC', accent: '#2563eb', paper: '#f7fbff', fold: '#bfdbfe' },
  'file-excel': { label: 'XLS', accent: '#16a34a', paper: '#f6fff9', fold: '#bbf7d0' },
  'file-ppt': { label: 'PPT', accent: '#ea580c', paper: '#fff8f2', fold: '#fed7aa' },
  'file-text': { label: 'TXT', accent: '#64748b', paper: '#f8fafc', fold: '#e2e8f0' },
  'file-image': { label: 'IMG', accent: '#0891b2', paper: '#f3fcff', fold: '#a5f3fc' },
  'file-video': { label: 'VID', accent: '#7c3aed', paper: '#fbf7ff', fold: '#ddd6fe' },
  'file-zip': { label: 'ZIP', accent: '#b45309', paper: '#fffaf0', fold: '#fde68a' },
  'file-unknown': { label: 'FILE', accent: '#475569', paper: '#f8fafc', fold: '#e2e8f0' }
}

const fileIcon = computed(() => fileIconMap[props.name])

const pathMap: Record<string, string[]> = {
  brand: [
    'M12 3.2 20.4 7.8 12 12.4 3.6 7.8 12 3.2Z',
    'M6.5 10.1v4.6c1.4 1.6 3.2 2.4 5.5 2.4s4.1-.8 5.5-2.4v-4.6',
    'M20.4 7.8v5.4'
  ],
  copilot: [
    'M12 3.2c3.8 0 6.8 2.8 6.8 6.3v3.8c0 3.5-3 6.3-6.8 6.3s-6.8-2.8-6.8-6.3V9.5c0-3.5 3-6.3 6.8-6.3Z',
    'M8.5 11.4h.02M15.5 11.4h.02',
    'M9.2 15c.7.65 1.65 1 2.8 1s2.1-.35 2.8-1',
    'M4.4 11.2H3.2M20.8 11.2h-1.2'
  ],
  assistant: [
    'M12 4.2c3.6 0 6.5 2.7 6.5 6.1v3.4c0 3.4-2.9 6.1-6.5 6.1s-6.5-2.7-6.5-6.1v-3.4c0-3.4 2.9-6.1 6.5-6.1Z',
    'M8.6 11h.02M15.4 11h.02',
    'M9.1 14.8c.75.58 1.72.87 2.9.87s2.15-.29 2.9-.87',
    'M12 2.6v1.6'
  ],
  menu: ['M4.5 6.5h15', 'M4.5 12h15', 'M4.5 17.5h15'],
  search: ['M10.8 18.1a7.3 7.3 0 1 0 0-14.6 7.3 7.3 0 0 0 0 14.6Z', 'M16.2 16.2l4.3 4.3'],
  bell: ['M6.7 10.2a5.3 5.3 0 0 1 10.6 0c0 4 1.7 5.1 2.2 6.1H4.5c.5-1 2.2-2.1 2.2-6.1Z', 'M10 19.1c.4.9 1.1 1.4 2 1.4s1.6-.5 2-1.4'],
  camera: ['M8.3 7.2 9.5 5.4h5l1.2 1.8h2.6c.9 0 1.7.8 1.7 1.7v8c0 .9-.8 1.7-1.7 1.7H5.7c-.9 0-1.7-.8-1.7-1.7v-8c0-.9.8-1.7 1.7-1.7h2.6Z', 'M12 15.8a3.4 3.4 0 1 0 0-6.8 3.4 3.4 0 0 0 0 6.8Z'],
  'chevron-down': ['M6.8 9.2 12 14.4l5.2-5.2'],
  'chevron-right': ['M9.2 6.8 14.4 12l-5.2 5.2'],
  user: [
    'M12 12.3a4.2 4.2 0 1 0 0-8.4 4.2 4.2 0 0 0 0 8.4Z',
    'M4.7 20.1c1.25-3.4 3.7-5.1 7.3-5.1s6.05 1.7 7.3 5.1'
  ],
  home: ['M3.6 11.4 12 4.2l8.4 7.2', 'M5.8 10v9.2h4.2v-5.4h4v5.4h4.2V10'],
  materials: ['M6.2 4.2h9.1l2.5 2.5v13.1H6.2V4.2Z', 'M15.2 4.2v3h3', 'M8.8 11h6.4', 'M8.8 14.2h6.4', 'M8.8 17.4h4.2'],
  tasks: ['M5 5.6h14', 'M5 12h14', 'M5 18.4h14', 'M3 5.6h.01', 'M3 12h.01', 'M3 18.4h.01'],
  eval: ['M4.8 18.8V9.6', 'M10 18.8V5.2', 'M15.2 18.8v-6.5', 'M20.4 18.8V7.7', 'M3.8 20h17.2'],
  summary: ['M5.2 4.4h13.6v15.2H5.2V4.4Z', 'M8 8.2h8', 'M8 11.8h8', 'M8 15.4h4.8'],
  quiz: ['M8.4 8.1a3.7 3.7 0 0 1 7.2 1.1c0 2.8-3.6 3-3.6 5', 'M12 18.6h.02', 'M4.5 12a7.5 7.5 0 1 0 15 0 7.5 7.5 0 0 0-15 0Z'],
  practice: ['M6 4.5h9.2l2.8 2.8v12.2H6V4.5Z', 'M15.2 4.5v3h3', 'M8.5 13.2l2 2 4.8-5'],
  wrong: ['M12 4.5a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15Z', 'M9.4 9.4l5.2 5.2', 'M14.6 9.4l-5.2 5.2'],
  mastery: ['M4.6 14.4 9.2 9.8l3.4 3.4 6.8-7.4', 'M5 19.4h14'],
  analytics: ['M4.4 19.2V5', 'M4.4 19.2h15.2', 'M7.5 15.8l3.3-4 3.2 2.5 4.3-6.3'],
  spark: ['M12 3.8l1.5 4.2 4.2 1.5-4.2 1.5-1.5 4.2-1.5-4.2-4.2-1.5 4.2-1.5L12 3.8Z', 'M18.3 14.2l.7 1.9 1.9.7-1.9.7-.7 1.9-.7-1.9-1.9-.7 1.9-.7.7-1.9Z', 'M6.4 14.7l.5 1.4 1.4.5-1.4.5-.5 1.4-.5-1.4-1.4-.5 1.4-.5.5-1.4Z'],
  config: ['M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8Z', 'M19.1 13.2a7.9 7.9 0 0 0 0-2.4l2-1.5-2-3.4-2.4 1a7.4 7.4 0 0 0-2.1-1.2L14.3 3h-4.6l-.3 2.7a7.4 7.4 0 0 0-2.1 1.2l-2.4-1-2 3.4 2 1.5a7.9 7.9 0 0 0 0 2.4l-2 1.5 2 3.4 2.4-1a7.4 7.4 0 0 0 2.1 1.2l.3 2.7h4.6l.3-2.7a7.4 7.4 0 0 0 2.1-1.2l2.4 1 2-3.4-2-1.5Z']
}

const iconPaths = computed(() => pathMap[props.name] || pathMap.brand)
</script>

<style scoped>
.app-icon {
  display: block;
  flex: 0 0 auto;
}
</style>
