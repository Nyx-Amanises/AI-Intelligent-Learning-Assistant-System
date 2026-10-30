<template>
  <div class="rag-comparison">
    <el-alert
      :type="comparison.comparable ? 'success' : 'warning'"
      :title="comparison.comparable ? '本组选定运行具备可比条件' : '本组结果仅供并列查看，不能直接判断方案优劣'"
      :closable="false"
      show-icon
    />
    <ul v-if="comparison.reasons.length" class="rag-comparison__reasons">
      <li v-for="reason in comparison.reasons" :key="reason">{{ reason }}</li>
    </ul>
    <p class="rag-comparison__hint">各列保留各自运行时的数据与参数；指标越高越好，耗时需结合服务负载参考。</p>
    <div class="rag-comparison__scroll" tabindex="0" aria-label="运行指标对比表，可横向滚动">
      <table>
        <caption>
          运行指标与参数
        </caption>
        <thead>
          <tr>
            <th scope="col">指标</th>
            <th v-for="run in comparison.runs" :key="run.id" scope="col">
              {{ run.name || `运行 #${run.id}` }}<small>#{{ run.id }} · {{ formatDateTime(run.startedAt) }}</small>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th scope="row">请求策略</th>
            <td v-for="run in comparison.runs" :key="run.id">{{ formatRagStrategy(run.strategy) }}</td>
          </tr>
          <tr>
            <th scope="row">Top K</th>
            <td v-for="run in comparison.runs" :key="run.id">{{ run.retrievalLimit }}</td>
          </tr>
          <tr>
            <th scope="row">成功 / 总样本</th>
            <td v-for="run in comparison.runs" :key="run.id">
              {{ run.evaluatedSamples }} / {{ run.totalSamples }}（失败 {{ run.failedSamples }}）
            </td>
          </tr>
          <tr v-for="metric in metrics" :key="metric.key">
            <th scope="row">{{ metric.label }}</th>
            <td v-for="run in comparison.runs" :key="run.id">
              {{ metric.k > run.retrievalLimit ? '—（未召回到此 K）' : percentage(run[metric.key]) }}
            </td>
          </tr>
          <tr>
            <th scope="row">平均耗时</th>
            <td v-for="run in comparison.runs" :key="run.id">{{ latency(run.avgLatencyMs) }}</td>
          </tr>
          <tr>
            <th scope="row">分块快照</th>
            <td v-for="run in comparison.runs" :key="run.id">
              <template v-if="run.parameterSnapshot"
                ><p v-for="material in run.parameterSnapshot.materials" :key="material.materialId">
                  {{ material.title || `资料 #${material.materialId}`
                  }}<small
                    >{{ formatRagChunkStrategy(material.chunkStrategy) }} · 大小 {{ material.chunkSize ?? '未记录' }} /
                    重叠 {{ material.chunkOverlap ?? '未记录' }} · {{ material.segmentCount ?? '—' }} 段</small
                  >
                </p></template
              ><template v-else>参数未记录</template>
            </td>
          </tr>
          <tr>
            <th scope="row">查询向量模型</th>
            <td v-for="run in comparison.runs" :key="run.id">
              {{ run.parameterSnapshot?.queryModels?.join('、') || (run.strategy === 'TEXT' ? '未使用' : '未记录') }}
            </td>
          </tr>
          <tr>
            <th scope="row">实际检索 / 演示向量</th>
            <td v-for="run in comparison.runs" :key="run.id">
              {{ run.actualStrategies?.map(formatRagStrategy).join('、') || '未记录'
              }}<small
                >{{
                  run.mockEmbedding === true
                    ? '模拟向量'
                    : run.mockEmbedding === false
                      ? '未使用模拟向量'
                      : '来源未记录'
                }}
                · 降级 {{ run.degradedSamples ?? '未记录' }}</small
              >
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="rag-comparison__samples-heading">
      <h4>逐样本对比</h4>
      <span>按样本 ID 对齐，共 {{ sampleRows.length }} 条</span>
    </div>
    <div class="rag-comparison__scroll" tabindex="0" aria-label="逐样本对比表，可横向滚动">
      <table>
        <caption>
          逐样本召回结果
        </caption>
        <thead>
          <tr>
            <th scope="col">问题</th>
            <th v-for="run in comparison.runs" :key="run.id" scope="col">{{ run.name || `运行 #${run.id}` }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visibleRows" :key="row.key">
            <th scope="row">
              {{ row.queryText }}<small>样本 #{{ row.sampleId }} · 资料 #{{ row.materialId }}</small>
            </th>
            <td v-for="(item, index) in row.items" :key="comparison.runs[index].id">
              <template v-if="item">
                <p v-if="item.queryText !== row.queryText">该次问题：{{ item.queryText }}</p>
                <strong>{{
                  item.errorMessage ? '运行失败' : item.hitRank ? `第 ${item.hitRank} 名命中` : '未命中'
                }}</strong>
                <small
                  >Recall@1 / 3 / 5：{{ percentage(item.recallAt1) }} /
                  {{ recallForK(item.recallAt3, comparison.runs[index].retrievalLimit, 3) }} /
                  {{ recallForK(item.recallAt5, comparison.runs[index].retrievalLimit, 5) }}</small
                >
                <small>耗时 {{ latency(item.latencyMs) }}</small>
                <small
                  >标准段落 {{ numberList(item.expectedSegmentIds) }} · 页码
                  {{ numberList(item.expectedPageNos) }}</small
                >
                <small
                  >召回段落 {{ numberList(item.retrievedSegmentIds) }} · 页码
                  {{ numberList(item.retrievedPageNos) }}</small
                >
                <small v-if="item.errorMessage" class="rag-comparison__error">{{ item.errorMessage }}</small>
                <RagRunEvidence :item="item" />
              </template>
              <span v-else>本次未评测该样本</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="sampleRows.length > pageSize" class="workspace-pagination">
      <span>共 {{ sampleRows.length }} 条样本</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        layout="prev, pager, next"
        :total="sampleRows.length"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  formatRagStrategy,
  formatRagChunkStrategy,
  type RagEvalComparison,
  type RagEvalRunItem
} from '@/api/modules/ragEval'
import { formatDateTime } from '@/utils/format'
import RagRunEvidence from './RagRunEvidence.vue'

const props = defineProps<{ comparison: RagEvalComparison }>()
const page = ref(1)
const pageSize = 20
const metrics = [
  { key: 'hitAt1', label: 'Hit@1', k: 1 },
  { key: 'hitAt3', label: 'Hit@3', k: 3 },
  { key: 'hitAt5', label: 'Hit@5', k: 5 },
  { key: 'recallAt1', label: 'Recall@1', k: 1 },
  { key: 'recallAt3', label: 'Recall@3', k: 3 },
  { key: 'recallAt5', label: 'Recall@5', k: 5 },
  { key: 'mrr', label: 'MRR', k: 0 }
] as const
const percentage = (value?: number) => (typeof value === 'number' ? `${(value * 100).toFixed(1)}%` : '未记录')
const recallForK = (value: number | undefined, limit: number, k: number) => (limit < k ? '—' : percentage(value))
const latency = (value?: number) => (typeof value === 'number' ? `${Math.round(value)} ms` : '未记录')
const numberList = (values?: number[]) => (values?.length ? values.join(', ') : '—')
const sampleRows = computed(() => {
  const rows = new Map<
    string,
    { key: string; sampleId: number; materialId: number; queryText: string; items: (RagEvalRunItem | undefined)[] }
  >()
  props.comparison.runs.forEach((run, index) => {
    ;(run.items || []).forEach((item) => {
      const key = `${item.materialId}:${item.sampleId}`
      let row = rows.get(key)
      if (!row) {
        row = {
          key,
          sampleId: item.sampleId,
          materialId: item.materialId,
          queryText: item.queryText,
          items: Array.from({ length: props.comparison.runs.length })
        }
        rows.set(key, row)
      }
      row.items[index] = item
    })
  })
  return Array.from(rows.values())
})
const visibleRows = computed(() => sampleRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(
  () => props.comparison,
  () => {
    page.value = 1
  }
)
</script>

<style scoped>
.rag-comparison__reasons {
  padding-left: 20px;
  line-height: 1.8;
  color: var(--text);
}
.rag-comparison__hint {
  font-size: 13px;
  line-height: 1.7;
  color: var(--muted);
}
.rag-comparison__scroll {
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: 10px;
}
.rag-comparison table {
  width: 100%;
  min-width: 680px;
  border-collapse: collapse;
  font-size: 13px;
}
.rag-comparison caption {
  text-align: left;
  padding: 12px 16px;
  font-weight: 600;
  background: var(--bg-secondary);
}
.rag-comparison th,
.rag-comparison td {
  min-width: 170px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  vertical-align: top;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.rag-comparison th {
  font-weight: 550;
}
.rag-comparison tbody th {
  width: 20%;
}
.rag-comparison small {
  display: block;
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
.rag-comparison p {
  margin: 0 0 8px;
}
.rag-comparison__samples-heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}
.rag-comparison__samples-heading h4 {
  margin: 0 0 12px;
}
.rag-comparison__samples-heading span {
  color: var(--muted);
  font-size: 12px;
}
.rag-comparison .rag-comparison__error {
  color: var(--red);
}
</style>
