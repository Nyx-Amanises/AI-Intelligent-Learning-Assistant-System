<template>
  <section class="assistant-citations" :aria-label="`参考来源，共 ${evidence.citations.length} 条`">
    <div class="assistant-citations__head">
      <span class="assistant-citations__title">参考来源</span>
      <span v-if="evidence.materialTitle" class="assistant-citations__material">《{{ evidence.materialTitle }}》</span>
      <span v-if="evidence.degraded" class="assistant-citations__badge assistant-citations__badge--warn"
        >文本检索（已降级）</span
      >
      <span v-else-if="evidence.mockEmbedding" class="assistant-citations__badge">Mock 演示向量</span>
      <RouterLink v-if="evidence.materialId" class="assistant-citations__open" :to="materialLink"
        >打开资料<AppIcon name="arrow-up-right" :size="13"
      /></RouterLink>
    </div>

    <p v-if="evidence.evidenceLevel === 'NONE'" class="assistant-citations__notice" role="note">
      资料中没有检索到相关片段，上面的回答不是基于这份资料得出的，请谨慎参考。
    </p>
    <p v-else-if="evidence.evidenceLevel === 'WEAK'" class="assistant-citations__notice" role="note">
      检索到的片段与问题相关度较低，回答可能缺少资料依据，建议对照原文核实。
    </p>
    <p
      v-if="evidence.degraded && evidence.degradeReason"
      class="assistant-citations__notice assistant-citations__notice--muted"
    >
      {{ evidence.degradeReason }}。
    </p>

    <ol v-if="evidence.citations.length" class="assistant-citations__list">
      <li v-for="citation in evidence.citations" :key="citation.segmentId ?? citation.index">
        <details>
          <summary>
            <span class="assistant-citations__index">[{{ citation.index }}]</span>
            <span class="assistant-citations__location">{{ citation.locationText }}</span>
          </summary>
          <p class="assistant-citations__excerpt">{{ citation.contentText || '（该片段没有正文）' }}</p>
        </details>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { AssistantRagEvidence } from '@/api/modules/assistant'
import AppIcon from '@/components/AppIcon.vue'

/**
 * 助手回答下方的「参考来源」：资料名、页码、段落都来自检索结果，
 * 编号与回答中的 [n] 对应；依据不足或检索降级时给出提示。
 */
const props = defineProps<{
  evidence: AssistantRagEvidence
}>()

const materialLink = computed(() => ({
  path: '/materials',
  query: { materialId: String(props.evidence.materialId) }
}))
</script>

<style scoped>
.assistant-citations {
  width: 100%;
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-secondary);
}

.assistant-citations__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  font-size: 12px;
}

.assistant-citations__title {
  color: var(--text-secondary);
  font-weight: 700;
}

.assistant-citations__material {
  min-width: 0;
  color: var(--text);
  overflow-wrap: anywhere;
}

.assistant-citations__badge {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
}

.assistant-citations__badge--warn {
  background: var(--accent-soft);
  color: var(--accent);
}

.assistant-citations__open {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  color: var(--brand);
  text-decoration: none;
}

.assistant-citations__open:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.assistant-citations__notice {
  margin: 8px 0 0;
  color: var(--accent);
  font-size: 12px;
  line-height: 1.7;
}

.assistant-citations__notice--muted {
  color: var(--muted);
}

.assistant-citations__list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.assistant-citations__list li + li {
  margin-top: 2px;
}

.assistant-citations__list summary {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-height: 32px;
  padding: 6px 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
  cursor: pointer;
}

.assistant-citations__index {
  flex-shrink: 0;
  color: var(--brand);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.assistant-citations__location {
  min-width: 0;
  overflow-wrap: anywhere;
}

.assistant-citations__excerpt {
  margin: 0 0 8px;
  padding: 8px 10px;
  border-left: 2px solid var(--line);
  color: var(--text);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
