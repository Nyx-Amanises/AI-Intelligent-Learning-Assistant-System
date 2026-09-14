<template>
  <section class="knowledge-page" :aria-busy="loading">
    <header class="knowledge-heading">
      <div><h1>知识掌握</h1><p>已掌握的知识，以及值得再练一次的内容。</p></div>
      <div class="knowledge-heading__actions">
        <RouterLink to="/analytics" class="knowledge-text-link">学习统计 <AppIcon name="arrow-up-right" :size="17" /></RouterLink>
        <button class="knowledge-refresh" type="button" :disabled="loading" aria-label="刷新知识掌握统计" @click="loadMastery"><AppIcon name="refresh" :size="21" :class="{ 'is-spinning': loading }" /></button>
      </div>
    </header>

    <div v-if="materialsError" class="knowledge-notice" role="alert"><div><strong>资料列表暂时无法加载</strong><p>仍可查看知识点，重新加载后即可选择资料。</p></div><el-button :loading="materialsLoading" @click="loadMaterials">重新加载资料</el-button></div>

    <section v-if="hasLoaded && !loading && !loadError" class="knowledge-overview" aria-label="知识点掌握概览">
      <dl class="knowledge-totals">
        <div><dt>平均掌握度</dt><dd>{{ overview.averageMasteryPercent }}<small>%</small></dd><p>{{ overview.totalAttempts }} 次作答记录</p></div>
        <div><dt>已掌握</dt><dd>{{ overview.masteredCount }}</dd><p>掌握度 ≥ 85%</p></div>
        <div><dt>待巩固</dt><dd>{{ overview.weakCount }}</dd><p>掌握度 50% – 69%</p></div>
        <div><dt>薄弱点</dt><dd class="knowledge-totals__risk">{{ overview.riskCount }}</dd><p>掌握度低于 50%</p></div>
      </dl>
      <div class="knowledge-overview__notes"><span>知识点 <b>{{ overview.totalKnowledgePoints }}</b></span><span>基本掌握 <b>{{ overview.goodCount }}</b></span><span>错题次数 <b>{{ overview.wrongAttempts }}</b></span><span>{{ hasFilters ? '当前筛选范围的累计记录' : '全部练习的累计记录' }}</span></div>
    </section>

    <section class="knowledge-library" aria-labelledby="knowledge-list-title">
      <div class="knowledge-section-heading"><h2 id="knowledge-list-title">我的知识点</h2><span v-if="hasLoaded && !loading && !loadError">{{ total }} 个</span></div>
      <div class="knowledge-filters" aria-label="筛选知识点">
        <el-input v-model="filters.keyword" clearable placeholder="搜索知识点、资料或建议" aria-label="搜索知识点、资料或建议" class="knowledge-search"><template #prefix><AppIcon name="search" :size="17" /></template></el-input>
        <el-select v-model="filters.materialId" clearable filterable placeholder="全部资料" aria-label="筛选学习资料" :loading="materialsLoading"><el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id" /></el-select>
        <el-select v-model="filters.masteryLevel" clearable placeholder="全部掌握状态" aria-label="筛选掌握状态"><el-option label="薄弱" value="RISK" /><el-option label="待巩固" value="WEAK" /><el-option label="基本掌握" value="GOOD" /><el-option label="已掌握" value="MASTERED" /></el-select>
        <el-select v-model="filters.questionType" clearable placeholder="全部题型" aria-label="筛选题型"><el-option label="单选题" value="SINGLE" /><el-option label="判断题" value="JUDGE" /><el-option label="简答题" value="SHORT" /></el-select>
        <button type="button" class="knowledge-reset" @click="resetFilters">重置条件</button>
      </div>

      <div v-if="hasLoaded && !loading && !loadError && reviewPoints.length" class="knowledge-priorities">
        <span>优先复习</span>
        <div><button v-for="item in reviewPoints" :key="String(item.materialId || 0) + '-' + item.knowledgePoint" type="button" @click="focusKnowledgePoint(item)">{{ item.knowledgePoint }} <small>{{ item.masteryPercent }}%</small></button></div>
      </div>

      <div v-if="loading" class="knowledge-state" role="status">正在整理知识掌握情况…</div>
      <div v-else-if="loadError" class="knowledge-notice" role="alert"><div><strong>{{ loadError }}</strong><p>数据暂时没有读取成功，请重试。</p></div><el-button type="primary" :loading="loading" @click="loadMastery">重新加载统计</el-button></div>
      <div v-else-if="hasLoaded && !records.length" class="knowledge-state knowledge-state--empty">
        <AppIcon name="mastery" :size="34" />
        <h3>{{ hasFilters ? '没有找到匹配的知识点' : '从一次练习开始' }}</h3>
        <p>{{ hasFilters ? '试试其他关键词，或清除筛选条件查看全部知识点。' : '提交练习后，这里会整理已掌握的知识与需要巩固的重点。' }}</p>
        <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
        <el-button v-else type="primary" @click="router.push(materialsLoaded && !materials.length ? '/materials' : '/quiz')">{{ materialsLoaded && !materials.length ? '添加学习资料' : '去练习' }}</el-button>
      </div>
      <div v-else-if="hasLoaded" class="knowledge-records">
        <article v-for="item in records" :key="String(item.materialId || 0) + '-' + item.knowledgePoint" class="knowledge-record">
          <div class="knowledge-record__title"><h3>{{ item.knowledgePoint }}</h3><p>{{ item.materialTitle || '未关联资料' }}</p><span>{{ formatQuestionTypes(item.questionTypes) }}</span></div>
          <div class="knowledge-record__mastery">
            <div><strong :class="'knowledge-level--' + item.masteryLevel.toLowerCase()">{{ item.masteryLabel }}</strong><b>{{ item.masteryPercent }}<small>%</small></b></div>
            <el-progress :percentage="item.masteryPercent" :stroke-width="5" :show-text="false" :color="progressColor(item.masteryPercent)" :aria-label="item.knowledgePoint + '掌握度'" />
            <span>{{ item.correctCount }} / {{ item.attemptCount }} 次正确</span>
          </div>
          <div class="knowledge-record__context"><p>{{ item.suggestion }}</p><span>得分 {{ item.obtainedScore }} / {{ item.totalScore }} · 错题 {{ item.wrongCount }}</span><span>最近练习 {{ formatDateTime(item.lastPracticeTime) }}</span></div>
          <div class="knowledge-record__actions"><button type="button" @click="showDetail(item)">查看详情 <AppIcon name="arrow-up-right" :size="15" /></button><button type="button" @click="goWrongQuestions(item)">查看错题</button></div>
        </article>
      </div>

      <div v-if="hasLoaded && !loading && !loadError && total" class="knowledge-pagination">
        <p>共 {{ total }} 个知识点 · 第 {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页</p>
        <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" :pager-count="5" layout="sizes, prev, pager, next" :total="total" @current-change="loadMastery" @size-change="changePageSize" />
      </div>
    </section>

    <el-drawer v-model="drawerVisible" title="掌握度详情" size="min(580px, 100vw)">
      <template v-if="detail">
        <div class="knowledge-detail-heading"><h2>{{ detail.knowledgePoint }}</h2><p>{{ detail.materialTitle || '未关联资料' }}</p></div>
        <dl class="knowledge-detail-facts">
          <div><dt>掌握状态</dt><dd>{{ detail.masteryLabel }} · {{ detail.masteryPercent }}%</dd></div>
          <div><dt>题目覆盖</dt><dd>{{ detail.uniqueQuestionCount }} 道题 · {{ detail.attemptCount }} 次作答</dd></div>
          <div><dt>正确率</dt><dd>{{ detail.accuracyRate }}%</dd></div>
          <div><dt>得分率</dt><dd>{{ detail.scoreRate }}%</dd></div>
          <div><dt>错题次数</dt><dd>{{ detail.wrongCount }}</dd></div>
          <div><dt>最近练习</dt><dd>{{ formatDateTime(detail.lastPracticeTime) }}</dd></div>
        </dl>
        <div class="knowledge-detail-advice"><h3>复习建议</h3><p>{{ detail.suggestion }}</p></div>
        <div class="knowledge-detail-actions"><el-button type="primary" @click="focusKnowledgePoint(detail)">按此知识点筛选</el-button><el-button @click="goWrongQuestions(detail)">查看相关错题</el-button></div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeMasteryOverviewApi, type KnowledgeMasteryItem, type KnowledgeMasteryOverviewPayload } from '@/api/modules/knowledgeMastery'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { useAutoListQuery } from '@/composables/useAutoListQuery'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
const materialsError = ref('')
const materialsLoaded = ref(false)
const materialsLoading = ref(false)
const drawerVisible = ref(false)
const records = ref<KnowledgeMasteryItem[]>([])
const materials = ref<MaterialPageItem[]>([])
const detail = ref<KnowledgeMasteryItem | null>(null)
const total = ref(0)
let masteryRequestId = 0
let materialRequestId = 0

const overview = reactive({
  totalKnowledgePoints: 0,
  totalAttempts: 0,
  wrongAttempts: 0,
  masteredCount: 0,
  goodCount: 0,
  weakCount: 0,
  riskCount: 0,
  averageMasteryPercent: 0,
  weakestPoints: [] as KnowledgeMasteryItem[]
})

const reviewPoints = computed(() =>
  overview.weakestPoints.filter((item) => Number(item.masteryPercent) < 70)
)

const filters = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  materialId: typeof route.query.materialId === 'string' && Number(route.query.materialId)
    ? Number(route.query.materialId)
    : undefined as number | undefined,
  masteryLevel: '',
  questionType: ''
})

const hasFilters = computed(() => Boolean(filters.keyword || filters.materialId || filters.masteryLevel || filters.questionType))

const page = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.materialId, () => filters.masteryLevel, () => filters.questionType],
  () => {
    page.current = 1
    return loadMastery()
  }
)

const formatDateTime = (value?: string) => {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ').slice(0, 16)
}

const formatQuestionTypes = (value?: string) => {
  if (!value) {
    return '题型未记录'
  }
  const labels = value
    .split(',')
    .filter(Boolean)
    .map((item) => {
      switch (item.toUpperCase()) {
        case 'SINGLE':
          return '单选题'
        case 'JUDGE':
          return '判断题'
        case 'SHORT':
        case 'SHORT_ANSWER':
          return '简答题'
        default:
          return item
      }
    })
  return [...new Set(labels)].join(' / ')
}

const progressColor = (percent: number) => {
  if (percent >= 85) {
    return '#385a4a'
  }
  if (percent >= 70) {
    return '#8eab94'
  }
  if (percent >= 50) {
    return '#c5a267'
  }
  return '#b16a4f'
}

const applyOverview = (data: KnowledgeMasteryOverviewPayload) => {
  overview.totalKnowledgePoints = data.totalKnowledgePoints || 0
  overview.totalAttempts = data.totalAttempts || 0
  overview.wrongAttempts = data.wrongAttempts || 0
  overview.masteredCount = data.masteredCount || 0
  overview.goodCount = data.goodCount || 0
  overview.weakCount = data.weakCount || 0
  overview.riskCount = data.riskCount || 0
  overview.averageMasteryPercent = data.averageMasteryPercent || 0
  overview.weakestPoints = data.weakestPoints || []
  records.value = data.page?.records || []
  total.value = data.page?.total || 0
}

const loadMaterials = async () => {
  const requestId = ++materialRequestId
  materialsLoading.value = true
  materialsError.value = ''
  try {
    const res = await getMaterialPageApi({
      current: 1,
      size: 50,
      parseStatus: 'SUCCESS'
    })
    const firstPage = res.data.data
    const records = firstPage.records || []
    const pageCount = Math.ceil(Number(firstPage.total || records.length) / 50)
    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) =>
        getMaterialPageApi({ current: index + 2, size: 50, parseStatus: 'SUCCESS' })
      )
    )
    if (requestId !== materialRequestId) return
    materials.value = [...records, ...remainingPages.flatMap((response) => response.data.data.records || [])]
    materialsLoaded.value = true
  } catch {
    if (requestId === materialRequestId) materialsError.value = '资料列表暂时无法加载'
  } finally {
    if (requestId === materialRequestId) materialsLoading.value = false
  }
}

const loadMastery = async () => {
  const requestId = ++masteryRequestId
  loading.value = true
  loadError.value = ''
  try {
    const res = await getKnowledgeMasteryOverviewApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword || undefined,
      materialId: filters.materialId || undefined,
      masteryLevel: filters.masteryLevel || undefined,
      questionType: filters.questionType || undefined
    })
    if (requestId !== masteryRequestId) return
    applyOverview(res.data.data as KnowledgeMasteryOverviewPayload)
    hasLoaded.value = true
  } catch {
    if (requestId === masteryRequestId) loadError.value = '知识点掌握度暂时无法加载'
  } finally {
    if (requestId === masteryRequestId) loading.value = false
  }
}

const changePageSize = () => {
  page.current = 1
  void loadMastery()
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.materialId = undefined
    filters.masteryLevel = ''
    filters.questionType = ''
    page.current = 1
  })
}

const focusKnowledgePoint = (item: KnowledgeMasteryItem) => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = item.knowledgePoint
    filters.materialId = item.materialId
    filters.masteryLevel = ''
    page.current = 1
  })
  drawerVisible.value = false
}

const showDetail = (item: KnowledgeMasteryItem) => {
  detail.value = item
  drawerVisible.value = true
}

const goWrongQuestions = (item: KnowledgeMasteryItem) => {
  router.push({
    path: '/wrong-questions',
    query: {
      keyword: item.knowledgePoint,
      materialId: item.materialId ? String(item.materialId) : undefined
    }
  })
}

onUnmounted(() => { masteryRequestId++; materialRequestId++ })
void loadMaterials()
void loadMastery()
</script>

<style scoped>
.knowledge-page { max-width: 1180px; margin: 0 auto; padding: 12px 0 36px; color: var(--text, #252a25); }
.knowledge-heading, .knowledge-heading__actions, .knowledge-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.knowledge-heading { margin-bottom: 32px; }
.knowledge-heading h1 { margin: 0; font-size: clamp(27px, 3vw, 34px); font-weight: 600; letter-spacing: -.04em; }
.knowledge-heading p { margin: 11px 0 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
.knowledge-heading__actions { flex-shrink: 0; gap: 24px; }
.knowledge-text-link { display: inline-flex; align-items: center; min-height: 44px; gap: 8px; color: #385a4a; text-decoration: none; font-size: 13px; white-space: nowrap; }
.knowledge-refresh { display: grid; width: 44px; height: 44px; place-items: center; padding: 0; border: 0; border-radius: 50%; background: #ebece6; color: #465443; cursor: pointer; }
.knowledge-refresh:hover { background: #e1e6dc; }
.knowledge-refresh:disabled { opacity: .6; cursor: wait; }
.knowledge-overview { padding: 32px 36px 23px; margin-bottom: 42px; border-radius: 22px; background: #fff; }
.knowledge-totals { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; }
.knowledge-totals > div { min-width: 0; padding: 0 30px; border-left: 1px solid #e8eae3; }
.knowledge-totals > div:first-child { padding-left: 0; border-left: 0; }
.knowledge-totals > div:last-child { padding-right: 0; }
.knowledge-totals dt { color: #697262; font-size: 13px; }
.knowledge-totals dd { display: flex; align-items: baseline; flex-wrap: wrap; gap: 5px; margin: 13px 0 0; font-size: clamp(30px, 3.8vw, 46px); font-weight: 550; font-variant-numeric: tabular-nums; line-height: 1.15; letter-spacing: -.045em; overflow-wrap: anywhere; }
.knowledge-totals small { font-size: 18px; color: #738269; }
.knowledge-totals p { margin: 11px 0 0; color: #697262; font-size: 11px; line-height: 1.7; }
.knowledge-totals__risk { color: #a15e45; }
.knowledge-overview__notes { display: flex; flex-wrap: wrap; gap: 11px 24px; padding-top: 21px; margin-top: 23px; border-top: 1px solid #eceee8; color: #737c6c; font-size: 12px; }
.knowledge-overview__notes b { padding-left: 5px; color: #53664a; font-weight: 500; }
.knowledge-overview__notes > span:last-child { margin-left: auto; }
.knowledge-section-heading { margin-bottom: 22px; }
.knowledge-section-heading h2 { margin: 0; font-size: 19px; font-weight: 600; }
.knowledge-section-heading > span { font-size: 12px; color: #6f7965; }
.knowledge-filters { display: grid; grid-template-columns: minmax(200px, 1.5fr) minmax(130px, 1fr) minmax(130px, .85fr) minmax(120px, .8fr) 80px; gap: 12px; padding-bottom: 22px; border-bottom: 1px solid var(--line); }
.knowledge-filters > * { min-width: 0; }
.knowledge-filters :deep(.el-select__wrapper), .knowledge-filters :deep(.el-input__wrapper) { min-height: 44px; border-radius: 10px; background: transparent; box-shadow: 0 0 0 1px var(--line) inset; }
.knowledge-filters :deep(.el-input__wrapper.is-focus), .knowledge-filters :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px #385a4a inset; }
.knowledge-reset { padding: 0 5px; min-height: 44px; background: transparent; border: 0; color: #68765d; font: inherit; font-size: 13px; cursor: pointer; }
.knowledge-priorities { display: flex; align-items: baseline; gap: 16px; padding: 13px 0 8px; border-bottom: 1px solid var(--line); }
.knowledge-priorities > span { flex-shrink: 0; color: #737c6c; font-size: 12px; }
.knowledge-priorities > div { display: flex; flex-wrap: wrap; gap: 0 22px; min-width: 0; }
.knowledge-priorities button { min-height: 40px; padding: 8px 0; background: transparent; border: 0; color: #955e46; font: inherit; font-size: 12px; text-align: left; cursor: pointer; line-height: 1.7; overflow-wrap: anywhere; }
.knowledge-priorities button:hover { text-decoration: underline; text-underline-offset: 4px; }
.knowledge-priorities small { padding-left: 4px; color: #7d7768; font-size: 11px; }
.knowledge-record { display: grid; grid-template-columns: minmax(170px, 1.1fr) 160px minmax(230px, 1.45fr) 96px; align-items: start; gap: 32px; padding: 27px 0; border-bottom: 1px solid var(--line); }
.knowledge-record > div { min-width: 0; }
.knowledge-record__title h3 { margin: 0; font-size: 15px; line-height: 1.7; font-weight: 500; overflow-wrap: anywhere; }
.knowledge-record__title p { margin: 9px 0 4px; color: #697461; font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.knowledge-record__title > span { color: #747c6c; font-size: 11px; }
.knowledge-record__mastery { padding-top: 3px; }
.knowledge-record__mastery > div:first-child { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 11px; }
.knowledge-record__mastery strong { font-size: 12px; font-weight: 500; }
.knowledge-record__mastery b { font-size: 21px; font-weight: 500; font-variant-numeric: tabular-nums; }
.knowledge-record__mastery small { padding-left: 2px; color: #74806a; font-size: 11px; font-weight: 400; }
.knowledge-record__mastery > span { display: block; margin-top: 11px; color: #6e7863; font-size: 11px; }
.knowledge-record__mastery :deep(.el-progress-bar__outer) { background: #e5e9de; }
.knowledge-level--mastered { color: #385a4a; }
.knowledge-level--good { color: #567552; }
.knowledge-level--weak { color: #8b6a35; }
.knowledge-level--risk { color: #a15e45; }
.knowledge-record__context p { margin: 0 0 10px; font-size: 12px; line-height: 1.8; color: #626d58; overflow-wrap: anywhere; }
.knowledge-record__context span { display: block; margin-top: 5px; color: #737d68; font-size: 11px; line-height: 1.7; }
.knowledge-record__actions { display: grid; justify-items: end; }
.knowledge-record__actions button { display: flex; align-items: center; justify-content: flex-end; gap: 5px; min-height: 36px; padding: 6px 0; border: 0; background: transparent; font: inherit; font-size: 12px; color: #385a4a; white-space: nowrap; cursor: pointer; }
.knowledge-record__actions button + button { color: #67755c; }
.knowledge-record__actions button:hover { text-decoration: underline; text-underline-offset: 4px; }
.knowledge-pagination { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 18px; padding-top: 26px; }
.knowledge-pagination p { margin: 0; font-size: 12px; color: #6f7963; }
.knowledge-pagination :deep(.el-pagination) { flex-wrap: wrap; gap: 5px; }
.knowledge-pagination :deep(.el-pagination__sizes) { margin-right: 12px; }
.knowledge-pagination :deep(.el-pager li), .knowledge-pagination :deep(.btn-prev), .knowledge-pagination :deep(.btn-next) { background: transparent; }
.knowledge-notice { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 20px; padding: 24px 0; border-top: 1px solid var(--line); margin-bottom: 24px; }
.knowledge-notice strong { font-size: 15px; font-weight: 500; }
.knowledge-notice p { color: var(--muted); font-size: 12px; margin: 8px 0 0; line-height: 1.8; }
.knowledge-state { display: grid; justify-items: center; align-content: center; gap: 14px; padding: 32px 16px; min-height: 300px; text-align: center; color: #6b785f; font-size: 13px; }
.knowledge-state .app-icon { color: #92a486; }
.knowledge-state h3 { margin: 0; font-size: 17px; font-weight: 500; color: #49553f; }
.knowledge-state p { max-width: 400px; margin: 0 0 6px; font-size: 13px; line-height: 1.9; }
.knowledge-detail-heading h2 { margin: 6px 0 10px; font-size: 22px; font-weight: 500; line-height: 1.7; overflow-wrap: anywhere; }
.knowledge-detail-heading p { color: #727c68; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.knowledge-detail-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; margin: 24px 0; }
.knowledge-detail-facts > div { padding: 18px 0; border-bottom: 1px solid var(--line); min-width: 0; }
.knowledge-detail-facts dt { color: #727c68; font-size: 12px; margin-bottom: 9px; }
.knowledge-detail-facts dd { margin: 0; color: #3b4931; font-size: 14px; font-weight: 500; line-height: 1.7; overflow-wrap: anywhere; }
.knowledge-detail-advice { margin: 26px 0; padding: 0 0 0 16px; border-left: 2px solid #b9c7af; }
.knowledge-detail-advice h3 { margin: 0 0 10px; color: #4a5d3c; font-size: 14px; font-weight: 500; }
.knowledge-detail-advice p { color: #68765d; font-size: 13px; line-height: 1.9; }
.knowledge-detail-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 28px; }
.knowledge-detail-actions :deep(.el-button + .el-button) { margin-left: 0; }
.knowledge-page button:focus-visible, .knowledge-page a:focus-visible { outline: 2px solid #385a4a; outline-offset: 4px; }
.is-spinning { animation: knowledge-spin 1s linear infinite; }
@keyframes knowledge-spin { to { transform: rotate(360deg); } }
@media (max-width: 1100px) {
  .knowledge-record { grid-template-columns: minmax(160px, 1fr) 145px minmax(170px, 1.2fr) 85px; gap: 22px; }
  .knowledge-filters { grid-template-columns: minmax(200px, 1.4fr) minmax(130px, 1fr) minmax(120px, 1fr); }
  .knowledge-filters > :nth-child(4) { grid-column: 2; }
  .knowledge-filters > :last-child { justify-self: start; }
}
@media (max-width: 840px) {
  .knowledge-overview { padding: 27px; }
  .knowledge-totals > div { padding-inline: 18px; }
  .knowledge-record { grid-template-columns: minmax(0, 1fr) 155px; gap: 20px 28px; }
  .knowledge-record__context { grid-column: 1; }
  .knowledge-record__actions { grid-column: 2; align-self: end; }
  .knowledge-overview__notes > span:last-child { margin-left: 0; flex-basis: 100%; }
}
@media (max-width: 600px) {
  .knowledge-page { padding-top: 4px; }
  .knowledge-heading { align-items: start; margin-bottom: 26px; }
  .knowledge-heading h1 { font-size: 26px; }
  .knowledge-heading p { max-width: 220px; font-size: 12px; }
  .knowledge-heading__actions { gap: 8px; }
  .knowledge-text-link { font-size: 12px; gap: 4px; }
  .knowledge-heading__actions .app-icon { width: 18px; }
  .knowledge-text-link .app-icon { display: none; }
  .knowledge-refresh { height: 40px; width: 40px; }
  .knowledge-overview { padding: 26px 22px 21px; margin-bottom: 32px; border-radius: 19px; }
  .knowledge-totals { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 26px 0; }
  .knowledge-totals > div:nth-child(odd) { border-left: 0; padding-left: 0; }
  .knowledge-totals > div:nth-child(even) { padding-right: 0; padding-left: 22px; }
  .knowledge-totals dt { font-size: 12px; }
  .knowledge-totals dd { font-size: 35px; }
  .knowledge-totals small { font-size: 15px; }
  .knowledge-totals p { font-size: 11px; }
  .knowledge-overview__notes { font-size: 11px; gap: 10px 16px; }
  .knowledge-section-heading h2 { font-size: 18px; }
  .knowledge-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 11px; }
  .knowledge-search { grid-column: 1 / -1; }
  .knowledge-filters > :nth-child(4) { grid-column: auto; }
  .knowledge-filters > :last-child { justify-self: stretch; text-align: center; }
  .knowledge-priorities { display: block; padding-block: 17px 9px; }
  .knowledge-priorities > div { margin-top: 7px; gap: 0 20px; }
  .knowledge-record { grid-template-columns: minmax(0, 1fr) 116px; gap: 18px 21px; padding: 24px 0; }
  .knowledge-record__title h3 { font-size: 15px; }
  .knowledge-record__title p { font-size: 12px; }
  .knowledge-record__mastery { padding-top: 1px; }
  .knowledge-record__mastery strong { font-size: 11px; }
  .knowledge-record__mastery b { font-size: 20px; }
  .knowledge-record__mastery > div:first-child { gap: 6px; }
  .knowledge-record__context { grid-column: 1 / -1; }
  .knowledge-record__context p { margin-bottom: 7px; font-size: 12px; }
  .knowledge-record__actions { grid-column: 1 / -1; display: flex; gap: 26px; margin-top: -5px; }
  .knowledge-record__actions button { min-height: 40px; font-size: 12px; }
  .knowledge-pagination { display: grid; justify-content: center; gap: 16px; text-align: center; }
  .knowledge-pagination :deep(.el-pagination) { justify-content: center; }
  .knowledge-pagination :deep(.el-pagination__sizes) { flex-basis: 100%; display: flex; justify-content: center; margin: 0 0 8px; }
  .knowledge-detail-facts { gap: 0 20px; }
}
@media (prefers-reduced-motion: reduce) { .is-spinning { animation: none; } }
</style>
