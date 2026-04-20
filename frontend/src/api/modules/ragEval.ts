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
}

export interface RagEvalRun {
  id: number
  datasetId: number
  materialId: number
  status: string
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

export const getRagEvalDatasetPageApi = (params: Record<string, unknown>) =>
  http.get('/rag/eval/datasets', { params })

export const createRagEvalDatasetApi = (data: {
  materialId: number
  name: string
  description?: string
}) => http.post('/rag/eval/datasets', data)

export const deleteRagEvalDatasetApi = (datasetId: number) =>
  http.delete(`/rag/eval/datasets/${datasetId}`)

export const getRagEvalSamplesApi = (datasetId: number) =>
  http.get(`/rag/eval/datasets/${datasetId}/samples`)

export const addRagEvalSamplesApi = (datasetId: number, samples: RagEvalSampleForm[]) =>
  http.post(`/rag/eval/datasets/${datasetId}/samples`, { samples })

export const updateRagEvalSampleApi = (
  datasetId: number,
  sampleId: number,
  data: RagEvalSampleForm
) => http.put(`/rag/eval/datasets/${datasetId}/samples/${sampleId}`, data)

export const deleteRagEvalSampleApi = (datasetId: number, sampleId: number) =>
  http.delete(`/rag/eval/datasets/${datasetId}/samples/${sampleId}`)

export const runRagEvalDatasetApi = (
  datasetId: number,
  data: { limit?: number; sampleIds?: number[] } = {}
) => http.post(`/rag/eval/datasets/${datasetId}/runs`, data)

export const getRagEvalRunApi = (runId: number) => http.get(`/rag/eval/runs/${runId}`)
