<template>
  <span
    v-for="(run, index) in runs"
    :key="index"
    class="book-text-run"
    :style="{
      ...getPdfReadingColorStyle(run.color),
      fontWeight: run.bold ? 700 : undefined,
      fontStyle: run.italic ? 'italic' : undefined,
      fontFamily: run.monospace ? 'ui-monospace, SFMono-Regular, Consolas, monospace' : undefined
    }"
    >{{ run.text }}</span
  >
</template>

<script setup lang="ts">
import { getPdfReadingColorStyle } from '@/utils/pdfReadingContrast'
import type { ReadingRun } from '@/utils/pdfReadingLayout'

defineProps<{ runs: ReadingRun[] }>()
</script>

<style scoped>
.book-text-run {
  -webkit-box-decoration-break: clone;
  box-decoration-break: clone;
}
</style>
