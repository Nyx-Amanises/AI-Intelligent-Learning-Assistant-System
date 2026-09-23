<template>
  <div v-if="segments.length" class="source-segment-list" :class="{ 'source-segment-list--compact': compact }">
    <div v-for="segment in segments" :key="String(segment.segmentId) + '-' + (segment.segmentNo || 0)" class="source-segment">
      <div class="source-segment__title">{{ formatSegmentTitle(segment) }}</div>
      <div class="source-segment__meta">{{ formatSegmentMeta(segment) }}</div>
      <div class="source-segment__body">{{ segment.contentText }}</div>
    </div>
  </div>
  <p v-else class="source-segment-list__empty">{{ emptyText }}</p>
</template>

<script setup lang="ts">
import { formatSegmentMeta, formatSegmentTitle, type SourceSegmentLike } from '@/utils/format'

/** AI 总结 / 出题 / 评分引用的资料片段列表。 */
withDefaults(defineProps<{
  segments?: SourceSegmentLike[]
  compact?: boolean
  emptyText?: string
}>(), {
  segments: () => [],
  compact: false,
  emptyText: '暂未记录引用资料。'
})
</script>

<style scoped>
.source-segment-list { display: grid; gap: 14px; }
.source-segment { padding: 16px 18px; border: 1px solid var(--line); border-radius: var(--radius-sm); background: var(--bg-secondary); }
.source-segment__title { color: var(--text); font-size: 14px; font-weight: 600; line-height: 1.6; overflow-wrap: anywhere; }
.source-segment__meta { margin-top: 4px; color: var(--muted); font-size: 12px; }
.source-segment__body { margin-top: 10px; color: var(--text-secondary); font-size: 14px; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; }
.source-segment-list--compact { gap: 10px; }
.source-segment-list--compact .source-segment { padding: 12px 14px; }
.source-segment-list--compact .source-segment__body { font-size: 13px; }
.source-segment-list__empty { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
</style>
