import http from '@/api/http'

export interface RagEvalDataset {
  id: number
  materialId: number
  materialTitle?: string
  name: string
  description?: string
  status: string
  sampleCount: number
  lastRunId?: number
  lastRunAt?: string
  createdAt?: string
}

export interface RagEvalDatasetPagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: RagEvalDataset[]
}

export interface RagEvalSample {
  id: number
  datasetId: number
  materialId: number
  materialTitle?: string
  queryText: string
  expectedSegmentIds: number[]
  expectedPageNos: number[]
  expectedKeywords?: string
  tag?: string
  difficulty?: number
  sourceType?: string
  note?: string
  createdAt?: string
}

export interface RagEvalSampleForm {
  materialId?: number
  queryText: string
  expectedSegmentIds?: number[]
  expectedPageNos?: number[]
  expectedKeywords?: string
  tag?: string
  difficulty?: number
  sourceType?: string
  note?: string
}

export interface RagEvalRunItem {
  id: number
  sampleId: number
  materialId: number
  queryText: string
  expectedSegmentIds: number[]
  expectedPageNos: number[]
  retrievedSegmentIds: number[]
  retrievedPageNos: number[]
  hitRank?: number
  reciprocalRank?: number
  recallAt1?: number
  recallAt3?: number
  recallAt5?: number
  latencyMs?: number
  errorMessage?: string
  actualStrategy?: string | null
  mockEmbedding?: boolean | null
  queryEmbeddingModel?: string | null
  degradeReason?: string | null
  retrievedSnapshot?: RagRetrievedHit[] | null
}

export interface RagRetrievedHit {
  segment: {
    segmentId: number
    segmentNo?: number
    pageNo?: number
    sectionTitle?: string
    contentText?: string
    keywords?: string
    score?: number
  }
  semanticScore?: number | null
  lexicalScore?: number | null
  relevanceScore?: number | null
}

export type RagRetrievalStrategy = 'TEXT' | 'VECTOR_ONLY' | 'VECTOR_RERANK'

export interface RagMaterialSnapshot {
  materialId: number
  title?: string
  chunkStrategy?: string | null
  chunkSize?: number | null
  chunkOverlap?: number | null
  segmentCount?: number
  contentHash?: string | null
  segmentHash?: string | null
  indexModels?: string[] | null
  indexedSegments?: number | null
}

export interface RagParameterSnapshot {
  version: string
  strategy: RagRetrievalStrategy
  limit: number
  oversampleFactor?: number
  minCandidates?: number
  semanticWeight?: number
  lexicalWeight?: number
  titleBoost?: number
  exactBoost?: number
  materials: RagMaterialSnapshot[]
  sampleFingerprint?: string
  corpusFingerprint?: string
  queryModels?: string[] | null
}

export interface RagEvalRun {
  id: number
  datasetId: number
  materialId: number
  status: string
  name?: string | null
  strategy?: RagRetrievalStrategy | null
  parameterSnapshot?: RagParameterSnapshot | null
  actualStrategies?: string[] | null
  mockEmbedding?: boolean | null
  degradedSamples?: number | null
  retrievalLimit: number
  totalSamples: number
  evaluatedSamples: number
  failedSamples: number
  hitAt1?: number
  hitAt3?: number
  hitAt5?: number
  recallAt1?: number
  recallAt3?: number
  recallAt5?: number
  mrr?: number
  avgLatencyMs?: number
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  items: RagEvalRunItem[]
}

export interface RagEvalRunPagePayload {
  current: number
  size: number
  total: number
  pages: number
  records: RagEvalRun[]
}

export interface RagEvalComparison {
  runs: RagEvalRun[]
  comparable: boolean
  reasons: string[]
}

export const RAG_STRATEGY_LABELS: Record<RagRetrievalStrategy, string> = {
  TEXT: '文本检索',
  VECTOR_ONLY: '纯向量检索',
  VECTOR_RERANK: '向量检索 + 词面重排'
}

export const formatRagStrategy = (strategy?: string | null) =>
  RAG_STRATEGY_LABELS[strategy as RagRetrievalStrategy] || strategy || '未记录'

export const formatRagChunkStrategy = (strategy?: string | null) =>
  strategy === 'CHAR_WINDOW_V2' ? '按字符分块' : strategy === 'ORIGINAL_CONTEXT' ? '保留原始段落' : '未记录'

export interface CmrcImportResult {
  materialId: number
  materialTitle: string
  datasetId: number
  datasetName: string
  segmentCount: number
  sampleCount: number
  embeddingTaskId?: number
}

export const getRagEvalDatasetPageApi = (params: Record<string, unknown>) => http.get('/rag/eval/datasets', { params })

export const createRagEvalDatasetApi = (data: { materialId: number; name: string; description?: string }) =>
  http.post('/rag/eval/datasets', data)

export const deleteRagEvalDatasetApi = (datasetId: number) => http.delete(`/rag/eval/datasets/${datasetId}`)

export const getRagEvalSamplesApi = (datasetId: number) => http.get(`/rag/eval/datasets/${datasetId}/samples`)

export const addRagEvalSamplesApi = (datasetId: number, samples: RagEvalSampleForm[]) =>
  http.post(`/rag/eval/datasets/${datasetId}/samples`, { samples })

export const updateRagEvalSampleApi = (datasetId: number, sampleId: number, data: RagEvalSampleForm) =>
  http.put(`/rag/eval/datasets/${datasetId}/samples/${sampleId}`, data)

export const deleteRagEvalSampleApi = (datasetId: number, sampleId: number) =>
  http.delete(`/rag/eval/datasets/${datasetId}/samples/${sampleId}`)

export const runRagEvalDatasetApi = (
  datasetId: number,
  data: { limit?: number; sampleIds?: number[]; strategy?: RagRetrievalStrategy; name?: string } = {}
) => http.post(`/rag/eval/datasets/${datasetId}/runs`, data)

export const getRagEvalRunsApi = (datasetId: number, params: { current: number; size: number }) =>
  http.get(`/rag/eval/datasets/${datasetId}/runs`, { params })

export const compareRagEvalRunsApi = (datasetId: number, runIds: number[]) =>
  http.get(`/rag/eval/datasets/${datasetId}/compare`, { params: { runIds: runIds.join(',') } })

export const getRagEvalRunApi = (runId: number) => http.get(`/rag/eval/runs/${runId}`)

export const importCmrc2018Api = (data: {
  file: File
  materialTitle?: string
  datasetName?: string
  splitName?: string
  maxSamples?: number
  submitEmbeddingTask?: boolean
}) => {
  const formData = new FormData()
  formData.append('file', data.file)
  if (data.materialTitle) {
    formData.append('materialTitle', data.materialTitle)
  }
  if (data.datasetName) {
    formData.append('datasetName', data.datasetName)
  }
  if (data.splitName) {
    formData.append('splitName', data.splitName)
  }
  if (typeof data.maxSamples === 'number') {
    formData.append('maxSamples', String(data.maxSamples))
  }
  formData.append('submitEmbeddingTask', String(Boolean(data.submitEmbeddingTask)))
  return http.post('/rag/eval/cmrc2018/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
