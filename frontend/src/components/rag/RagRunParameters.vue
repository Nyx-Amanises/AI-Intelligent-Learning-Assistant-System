<template>
  <div class="rag-parameters">
    <p v-if="!run.parameterSnapshot" class="rag-parameters__unknown">
      本次运行未保存参数快照，无法还原检索和分块设置。
    </p>
    <template v-else>
      <dl class="rag-parameters__grid">
        <div>
          <dt>检索策略</dt>
          <dd>{{ formatRagStrategy(run.parameterSnapshot.strategy) }}</dd>
        </div>
        <div>
          <dt>返回条数 Top K</dt>
          <dd>{{ run.parameterSnapshot.limit }}</dd>
        </div>
        <div>
          <dt>实际检索</dt>
          <dd>{{ run.actualStrategies?.map(formatRagStrategy).join('、') || '未记录' }}</dd>
        </div>
        <div>
          <dt>查询向量模型</dt>
          <dd>
            {{
              run.parameterSnapshot.queryModels?.join('、') ||
              (run.parameterSnapshot.strategy === 'TEXT' ? '未使用' : '未记录')
            }}
          </dd>
        </div>
        <div>
          <dt>降级样本</dt>
          <dd>{{ run.degradedSamples ?? '未记录' }}</dd>
        </div>
        <div>
          <dt>演示向量</dt>
          <dd>
            {{ run.mockEmbedding === true ? '使用了模拟向量' : run.mockEmbedding === false ? '未使用' : '未记录' }}
          </dd>
        </div>
      </dl>
      <details>
        <summary>查看分块及检索参数</summary>
        <p class="rag-parameters__note">以下为运行时保存的参数。要调整分块，请前往资料库重新解析，再运行同一评测集。</p>
        <div
          v-for="material in run.parameterSnapshot.materials || []"
          :key="material.materialId"
          class="rag-parameters__material"
        >
          <strong>{{ material.title || `资料 #${material.materialId}` }}</strong>
          <span
            >分块方式 {{ formatRagChunkStrategy(material.chunkStrategy) }} · 大小 {{ material.chunkSize ?? '未记录' }} ·
            重叠 {{ material.chunkOverlap ?? '未记录' }} · {{ material.segmentCount ?? '—' }} 段</span
          >
          <small :title="material.contentHash || ''"
            >内容版本 {{ abbreviatedHash(material.contentHash) }} · 分段版本
            {{ abbreviatedHash(material.segmentHash) }}</small
          >
          <small
            >索引模型 {{ material.indexModels?.join('、') || '未记录' }} · 已索引
            {{ material.indexedSegments ?? '未记录' }} 段</small
          >
        </div>
        <dl class="rag-parameters__grid rag-parameters__technical">
          <div v-if="run.parameterSnapshot.strategy === 'VECTOR_RERANK'">
            <dt>候选放大倍数 / 最少候选</dt>
            <dd>
              {{ run.parameterSnapshot.oversampleFactor ?? '未记录' }} /
              {{ run.parameterSnapshot.minCandidates ?? '未记录' }}
            </dd>
          </div>
          <div v-if="run.parameterSnapshot.strategy === 'VECTOR_RERANK'">
            <dt>语义 / 词面权重</dt>
            <dd>
              {{ run.parameterSnapshot.semanticWeight ?? '未记录' }} /
              {{ run.parameterSnapshot.lexicalWeight ?? '未记录' }}
            </dd>
          </div>
          <div v-if="run.parameterSnapshot.strategy === 'VECTOR_RERANK'">
            <dt>标题 / 精确命中加分</dt>
            <dd>
              {{ run.parameterSnapshot.titleBoost ?? '未记录' }} / {{ run.parameterSnapshot.exactBoost ?? '未记录' }}
            </dd>
          </div>
          <div>
            <dt>资料版本</dt>
            <dd :title="run.parameterSnapshot.corpusFingerprint">
              {{ abbreviatedHash(run.parameterSnapshot.corpusFingerprint) }}
            </dd>
          </div>
          <div>
            <dt>样本版本</dt>
            <dd :title="run.parameterSnapshot.sampleFingerprint">
              {{ abbreviatedHash(run.parameterSnapshot.sampleFingerprint) }}
            </dd>
          </div>
        </dl>
      </details>
    </template>
  </div>
</template>

<script setup lang="ts">
import { formatRagStrategy, formatRagChunkStrategy, type RagEvalRun } from '@/api/modules/ragEval'

defineProps<{ run: RagEvalRun }>()
const abbreviatedHash = (value?: string | null) => (value ? value.slice(0, 12) : '未记录')
</script>

<style scoped>
.rag-parameters {
  margin: 16px 0;
  color: var(--text);
  font-size: 13px;
}
.rag-parameters__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(155px, 1fr));
  gap: 12px 20px;
  margin: 0 0 16px;
}
.rag-parameters__grid div {
  min-width: 0;
}
.rag-parameters dt {
  margin-bottom: 4px;
  color: var(--muted);
  font-size: 12px;
}
.rag-parameters dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.rag-parameters summary {
  cursor: pointer;
  color: var(--brand);
}
.rag-parameters__note,
.rag-parameters__unknown {
  color: var(--muted);
  line-height: 1.7;
}
.rag-parameters__material {
  display: grid;
  gap: 5px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
  line-height: 1.6;
}
.rag-parameters__material small {
  color: var(--muted);
}
.rag-parameters__technical {
  margin-top: 16px;
}
</style>
