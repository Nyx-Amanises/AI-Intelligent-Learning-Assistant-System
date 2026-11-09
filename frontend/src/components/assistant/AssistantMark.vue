<template>
  <svg
    class="assistant-mark"
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <path
      d="M5 12c4-1 7.7-.1 11 2.5 3.3-2 7-2.6 11-1.7v13c-4-.9-7.7-.2-11 1.7-3.3-2.4-7-3.2-11-2.2Z"
      fill="currentColor"
      fill-opacity=".1"
    />
    <path
      d="M13 12.6A14.8 14.8 0 0 0 5 12v13.3c4-1 7.7-.2 11 2.2 3.3-1.9 7-2.6 11-1.7v-13l-2.5-.3M16 16v11.5"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path d="m22 2.8 1.4 3.8L27.2 8l-3.8 1.4-1.4 3.8-1.4-3.8L16.8 8l3.8-1.4Z" fill="currentColor" />
  </svg>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ size?: number | string }>(), { size: 28 })
</script>

<style scoped>
.assistant-mark {
  display: block;
  flex-shrink: 0;
  overflow: visible;
  pointer-events: none;
}
</style>
