<template>
  <div class="rag-run-evidence">
    <span
      >{{ formatRagStrategy(item.actualStrategy)
      }}<template v-if="item.mockEmbedding === true"> · 模拟向量</template></span
    >
    <span v-if="item.queryEmbeddingModel">查询模型 {{ item.queryEmbeddingModel }}</span>
    <p v-if="item.degradeReason" class="rag-run-evidence__degraded">降级原因：{{ item.degradeReason }}</p>
    <details v-if="item.retrievedSnapshot?.length">
      <summary>查看当时召回片段（{{ item.retrievedSnapshot.length }}）</summary>
      <article v-for="(hit, index) in item.retrievedSnapshot" :key="`${hit.segment.segmentId}-${index}`">
        <strong>{{ index + 1 }}. {{ hit.segment.sectionTitle || `片段 #${hit.segment.segmentId}` }}</strong>
        <span>段落 {{ hit.segment.segmentNo ?? '—' }} · 页码 {{ hit.segment.pageNo ?? '—' }}</span>
        <p>{{ hit.segment.contentText || '未记录片段内容' }}</p>
      </article>
    </details>
    <span v-else-if="item.retrievedSnapshot == null" class="rag-run-evidence__unknown">未保存召回内容快照</span>
  </div>
</template>

<script setup lang="ts">
import { formatRagStrategy, type RagEvalRunItem } from '@/api/modules/ragEval'
defineProps<{ item: RagEvalRunItem }>()
</script>

<style scoped>
.rag-run-evidence {
  display: grid;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
  line-height: 1.7;
}
.rag-run-evidence > span,
.rag-run-evidence article > span {
  color: var(--muted);
}
.rag-run-evidence__degraded {
  margin: 0;
  color: var(--accent);
}
.rag-run-evidence summary {
  cursor: pointer;
  color: var(--brand);
}
.rag-run-evidence article {
  display: grid;
  gap: 4px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}
.rag-run-evidence article p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 220px;
  overflow-y: auto;
}
.rag-run-evidence article strong {
  font-size: 12px;
}
</style>
