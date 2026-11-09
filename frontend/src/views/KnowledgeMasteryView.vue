<template>
  <section class="learning-page knowledge-page" :aria-busy="loading" aria-labelledby="knowledge-title">
    <header class="page-header">
      <div class="knowledge-page-heading">
        <RouterLink to="/analytics" class="knowledge-back" aria-label="返回我的学习">
          <MobileIcon name="back" :size="22" />
        </RouterLink>
        <h1 id="knowledge-title" class="page-title">知识掌握</h1>
      </div>
    </header>

    <div class="knowledge-toolbar">
      <span :title="selectedMaterialTitle">{{ selectedMaterialTitle }}</span>
      <div>
        <button
          ref="filterToggle"
          type="button"
          :class="{ 'is-active': hasFilters }"
          :aria-expanded="filtersOpen"
          aria-controls="knowledge-filter-panel"
          @click="toggleFilters"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M4 6h16M7 12h10M10 18h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg
          >筛选
          <span v-if="activeFilterCount" class="knowledge-filter-count">{{ activeFilterCount }}</span>
        </button>
        <button type="button" :disabled="loading" aria-label="刷新知识掌握" @click="loadMastery">
          <AppIcon name="refresh" :size="17" />
        </button>
      </div>
    </div>

    <form
      v-if="filtersOpen"
      id="knowledge-filter-panel"
      class="knowledge-filter-panel"
      aria-label="筛选知识点"
      @submit.prevent="applyFilters"
      @keydown.esc.prevent="closeFilters"
    >
      <label class="knowledge-filter-search">
        搜索
        <input v-model="draftFilters.keyword" type="search" placeholder="知识点、资料或建议" />
      </label>
      <div class="knowledge-filter-fields">
        <label>
          学习资料
          <select v-model="draftFilters.materialId" :disabled="materialsLoading">
            <option :value="undefined">全部资料</option>
            <option v-for="item in materials" :key="item.id" :value="item.id">{{ item.title }}</option>
          </select>
        </label>
        <label>
          题型
          <select v-model="draftFilters.questionType">
            <option value="">全部题型</option>
            <option value="SINGLE">单选题</option>
            <option value="MULTIPLE">多选题</option>
            <option value="JUDGE">判断题</option>
            <option value="SHORT">简答题</option>
          </select>
        </label>
      </div>
      <fieldset class="knowledge-level-filter">
        <legend>掌握状态</legend>
        <div class="knowledge-level-tabs">
          <button
            v-for="item in [{ level: '', label: '全部' }, ...masteryLevelOptions]"
            :key="item.level"
            type="button"
            :class="{ 'is-active': draftFilters.masteryLevel === item.level }"
            :aria-pressed="draftFilters.masteryLevel === item.level"
            @click="draftFilters.masteryLevel = item.level"
          >
            {{ item.label }}
          </button>
        </div>
      </fieldset>
      <div class="knowledge-filter-actions">
        <button type="button" @click="resetFilters">重置</button>
        <button type="submit">应用</button>
      </div>
    </form>

    <div v-if="materialsError" class="knowledge-notice" role="alert">
      <div>
        <strong>资料列表暂时无法加载</strong>
      </div>
      <el-button :loading="materialsLoading" @click="loadMaterials">重试</el-button>
    </div>

    <div class="knowledge-content">
      <section v-if="hasLoaded && !loadError" class="knowledge-summary" aria-label="知识掌握分布">
        <h2 class="knowledge-chart-title"><MobileIcon name="trend" :size="17" />掌握分布</h2>
        <section class="knowledge-overview surface" aria-label="知识点掌握概览">
          <MasteryDistributionChart :overview="overview" />
          <details class="knowledge-assessment-rule">
            <summary>评估说明</summary>
            <p>
              完成至少
              {{ MASTERY_REQUIRED_QUESTION_COUNT }} 道不同的已评分题目后，才评估掌握等级；重复作答同一题不增加覆盖题数。
              未标注知识点的题目需先完善标注。待评估知识点的样本得分仅供参考，不计入平均掌握度或薄弱推荐。
            </p>
          </details>
        </section>
      </section>

      <section class="knowledge-library workspace-panel" aria-labelledby="knowledge-list-title">
        <div class="knowledge-section-heading">
          <h2 id="knowledge-list-title">知识点</h2>
          <span v-if="hasLoaded && !loadError">{{ total }} 个</span>
        </div>
        <div v-if="loading && !hasLoaded" class="knowledge-state" role="status">正在加载知识掌握情况…</div>
        <div v-else-if="loadError" class="knowledge-notice" role="alert">
          <div>
            <strong>{{ loadError }}</strong>
          </div>
          <el-button type="primary" :loading="loading" @click="loadMastery">重试</el-button>
        </div>
        <div v-else-if="hasLoaded && !records.length" class="knowledge-state knowledge-state--empty">
          <AppIcon name="mastery" :size="34" />
          <h3>{{ hasFilters ? '没有找到匹配的知识点' : '暂无知识掌握记录' }}</h3>
          <p>
            {{ hasFilters ? '换个条件试试。' : '完成练习后，即可查看掌握情况。' }}
          </p>
          <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
          <el-button
            v-else
            type="primary"
            @click="router.push(materialsLoaded && !materials.length ? '/resources' : '/quiz')"
            >{{ materialsLoaded && !materials.length ? '找学习资料' : '去练习' }}</el-button
          >
        </div>
        <div v-else-if="hasLoaded" class="knowledge-records">
          <article
            v-for="item in records"
            :key="String(item.materialId || 0) + '-' + item.knowledgePoint"
            class="knowledge-record"
          >
            <div class="knowledge-record__title">
              <h3>{{ item.knowledgePoint }}</h3>
              <p>{{ item.materialTitle || '未关联资料' }}</p>
            </div>
            <div class="knowledge-record__mastery">
              <div>
                <strong :class="'knowledge-level--' + displayMasteryLevel(item).toLowerCase()">{{
                  isMasteryAssessed(item) ? item.masteryLabel : '待评估'
                }}</strong
                ><b :class="{ 'knowledge-sample-score': !isMasteryAssessed(item) }"
                  >{{ hasScoredQuestions(item) ? item.masteryPercent : '—'
                  }}<small v-if="hasScoredQuestions(item)">%</small></b
                >
              </div>
              <el-progress
                v-if="hasScoredQuestions(item)"
                :percentage="item.masteryPercent"
                :stroke-width="5"
                :show-text="false"
                :color="masteryColor(displayMasteryLevel(item))"
                :aria-label="item.knowledgePoint + (isMasteryAssessed(item) ? '掌握度' : '样本得分')"
              />
              <span v-if="!isMasteryAssessed(item)">{{
                hasScoredQuestions(item) ? '样本得分，仅供参考' : '暂无评分'
              }}</span>
            </div>
            <div class="knowledge-record__actions">
              <button type="button" @click="goSupplementalLearning(item)">补充学习</button>
              <button
                type="button"
                :disabled="Boolean(practiceUnavailableReason(item))"
                :title="practiceUnavailableReason(item) || undefined"
                @click="goKnowledgePractice(item)"
              >
                去练习
              </button>
              <button type="button" @click="showDetail(item)">详情 <AppIcon name="arrow-up-right" :size="15" /></button>
            </div>
          </article>
        </div>

        <div v-if="hasLoaded && !loadError && total" class="knowledge-pagination">
          <p>{{ total }} 个知识点 · {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页</p>
          <el-pagination
            v-model:current-page="page.current"
            v-model:page-size="page.size"
            :page-sizes="[10, 20, 50]"
            :pager-count="5"
            layout="sizes, prev, pager, next"
            :total="total"
            @current-change="loadMastery"
            @size-change="changePageSize"
          />
        </div>
      </section>
    </div>
    <el-drawer v-model="drawerVisible" title="掌握度详情" size="min(580px, 100vw)">
      <template v-if="detail">
        <div class="knowledge-detail-heading">
          <h2>{{ detail.knowledgePoint }}</h2>
          <p>{{ detail.materialTitle || '未关联资料' }}</p>
        </div>
        <dl class="knowledge-detail-facts">
          <div>
            <dt>题型</dt>
            <dd>{{ formatQuestionTypes(detail.questionTypes) }}</dd>
          </div>
          <div>
            <dt>掌握状态</dt>
            <dd :style="{ color: masteryColor(displayMasteryLevel(detail)) }">
              {{ isMasteryAssessed(detail) ? detail.masteryLabel : '待评估' }}
            </dd>
          </div>
          <div>
            <dt>{{ isMasteryAssessed(detail) ? '掌握度' : '样本得分（仅供参考）' }}</dt>
            <dd>{{ hasScoredQuestions(detail) ? detail.masteryPercent + '%' : '— · 暂无评分' }}</dd>
          </div>
          <div>
            <dt>不同已评分题目覆盖</dt>
            <dd>
              {{ detail.uniqueQuestionCount }} / {{ requiredQuestionCount(detail) }} 道 · 累计
              {{ detail.attemptCount }} 次作答
            </dd>
          </div>
          <div>
            <dt>累计作答正确率</dt>
            <dd>{{ detail.accuracyRate }}% · {{ detail.correctCount }} / {{ detail.attemptCount }} 次正确</dd>
          </div>
          <div>
            <dt>已评分作答得分率</dt>
            <dd>
              {{ hasScoredQuestions(detail) ? detail.scoreRate + '%' : '— · 暂无评分' }}
              <template v-if="hasScoredQuestions(detail)">
                · {{ detail.obtainedScore }} / {{ detail.totalScore }} 分</template
              >
            </dd>
          </div>
          <div>
            <dt>错题次数</dt>
            <dd>{{ detail.wrongCount }}</dd>
          </div>
          <div>
            <dt>最近练习</dt>
            <dd>{{ formatDateTime(detail.lastPracticeTime) }}</dd>
          </div>
        </dl>
        <div class="knowledge-detail-advice">
          <h3>{{ isMasteryAssessed(detail) ? '复习建议' : '评估说明' }}</h3>
          <p>{{ isMasteryAssessed(detail) ? detail.suggestion : assessmentReason(detail) }}</p>
          <p>
            至少需要
            {{ requiredQuestionCount(detail) }}
            道不同的已评分题目。重复作答同一题不增加覆盖题数；掌握度按已评分作答的得分率计算。
          </p>
        </div>
        <div class="knowledge-detail-actions">
          <el-button @click="goSupplementalLearning(detail)">补充学习</el-button>
          <el-button
            type="primary"
            :disabled="Boolean(practiceUnavailableReason(detail))"
            @click="goKnowledgePractice(detail)"
            >专项练习</el-button
          >
          <el-button @click="focusKnowledgePoint(detail)">按此知识点筛选</el-button
          ><el-button @click="goWrongQuestions(detail)">查看相关错题</el-button>
        </div>
        <p v-if="practiceUnavailableReason(detail)" class="knowledge-practice-reason">
          {{ practiceUnavailableReason(detail) }}
        </p>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import MasteryDistributionChart from '@/components/charts/MasteryDistributionChart.vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  getKnowledgeMasteryOverviewApi,
  type KnowledgeMasteryItem,
  type KnowledgeMasteryOverviewPayload
} from '@/api/modules/knowledgeMastery'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import { MASTERY_LEVELS, MASTERY_REQUIRED_QUESTION_COUNT, isMasteryAssessed, masteryColor } from '@/utils/mastery'
import { formatQuestionTypes } from '@/utils/labels'
import { knowledgePracticeUnavailableReason } from '@/utils/knowledgePractice'
import { knowledgeResourceRoute } from '@/utils/resourceNavigation'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
const materialsError = ref('')
const materialsLoaded = ref(false)
const materialsLoading = ref(false)
const drawerVisible = ref(false)
const filtersOpen = ref(false)
const filterToggle = ref<HTMLButtonElement | null>(null)
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
  insufficientCount: 0,
  assessedKnowledgePointCount: 0,
  averageMasteryPercent: null as number | null,
  weakestPoints: [] as KnowledgeMasteryItem[]
})

const requiredQuestionCount = (item: KnowledgeMasteryItem) =>
  item.requiredQuestionCount || MASTERY_REQUIRED_QUESTION_COUNT
const hasScoredQuestions = (item: KnowledgeMasteryItem) => item.uniqueQuestionCount > 0
const displayMasteryLevel = (item: KnowledgeMasteryItem) =>
  isMasteryAssessed(item) ? item.masteryLevel : 'INSUFFICIENT'
const assessmentReason = (item: KnowledgeMasteryItem) =>
  item.assessmentReason ||
  `已覆盖 ${item.uniqueQuestionCount} 道不同的已评分题目，至少需要 ${requiredQuestionCount(item)} 道后再评估。`

const goSupplementalLearning = (item: KnowledgeMasteryItem) => {
  void router.push(knowledgeResourceRoute(item.knowledgePoint, item.materialId))
}

// 待评估优先列出，其余筛选项按掌握度从低到高排列。
const masteryLevelOptions = [...MASTERY_LEVELS].reverse()

const filters = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  materialId:
    typeof route.query.materialId === 'string' && Number(route.query.materialId)
      ? Number(route.query.materialId)
      : (undefined as number | undefined),
  masteryLevel:
    typeof route.query.masteryLevel === 'string' &&
    masteryLevelOptions.some((item) => item.level === route.query.masteryLevel)
      ? route.query.masteryLevel
      : '',
  questionType: ''
})

const hasFilters = computed(() =>
  Boolean(filters.keyword || filters.materialId || filters.masteryLevel || filters.questionType)
)
const draftFilters = reactive({ ...filters })
const selectedMaterialTitle = computed(
  () =>
    materials.value.find((item) => item.id === filters.materialId)?.title ||
    (filters.materialId ? '所选资料' : '全部资料')
)
const activeFilterCount = computed(() => Object.values(filters).filter(Boolean).length)
const closeFilters = () => {
  filtersOpen.value = false
  filterToggle.value?.focus()
}
const toggleFilters = () => {
  if (filtersOpen.value) return closeFilters()
  Object.assign(draftFilters, filters)
  filtersOpen.value = true
}
const applyFilters = () => {
  autoQuery.runAfterMutation(() => Object.assign(filters, draftFilters, { keyword: draftFilters.keyword.trim() }))
  closeFilters()
}

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

const applyOverview = (data: KnowledgeMasteryOverviewPayload) => {
  overview.totalKnowledgePoints = data.totalKnowledgePoints || 0
  overview.totalAttempts = data.totalAttempts || 0
  overview.wrongAttempts = data.wrongAttempts || 0
  overview.masteredCount = data.masteredCount || 0
  overview.goodCount = data.goodCount || 0
  overview.weakCount = data.weakCount || 0
  overview.riskCount = data.riskCount || 0
  overview.insufficientCount = data.insufficientCount || 0
  overview.assessedKnowledgePointCount = data.assessedKnowledgePointCount || 0
  overview.averageMasteryPercent = data.averageMasteryPercent ?? null
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
    Object.assign(draftFilters, filters)
  })
  if (filtersOpen.value) closeFilters()
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
      knowledgePoint: item.knowledgePoint,
      materialId: item.materialId ? String(item.materialId) : undefined
    }
  })
}

const practiceUnavailableReason = (item: KnowledgeMasteryItem) =>
  knowledgePracticeUnavailableReason(item, {
    materials: materials.value,
    loaded: materialsLoaded.value,
    loading: materialsLoading.value,
    error: materialsError.value
  })
const goKnowledgePractice = (item: KnowledgeMasteryItem) => {
  if (practiceUnavailableReason(item)) return
  router.push({
    path: '/quiz',
    query: { materialId: String(item.materialId), knowledgePoint: item.knowledgePoint.trim(), generate: '1' }
  })
}

onUnmounted(() => {
  masteryRequestId++
  materialRequestId++
})
void loadMaterials()
void loadMastery()
</script>

<style scoped>
.knowledge-back {
  display: none;
}
.knowledge-page {
  padding-bottom: 24px;
  color: var(--text);
}
.knowledge-toolbar,
.knowledge-toolbar > div,
.knowledge-toolbar button {
  display: flex;
  align-items: center;
}
.knowledge-toolbar {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  color: var(--muted);
  font-size: 12px;
}
.knowledge-toolbar > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.knowledge-toolbar > div {
  flex-shrink: 0;
  gap: 8px;
}
.knowledge-toolbar button {
  justify-content: center;
  gap: 5px;
  min-width: 44px;
  min-height: 44px;
  padding: 0 6px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.knowledge-toolbar button.is-active {
  color: var(--brand);
}
.knowledge-toolbar button:disabled {
  opacity: 0.4;
  cursor: wait;
}
.knowledge-filter-count {
  display: grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand-soft);
  font-size: 11px;
}
.knowledge-filter-panel {
  display: grid;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--panel);
}
.knowledge-filter-panel label {
  display: grid;
  min-width: 0;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 12px;
}
.knowledge-filter-panel input,
.knowledge-filter-panel select {
  width: 100%;
  min-width: 0;
  min-height: 44px;
  padding: 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: 7px;
  background: var(--panel);
  color: var(--text);
  font: inherit;
  font-size: 14px;
}
.knowledge-filter-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.knowledge-level-filter {
  min-width: 0;
  padding: 0;
  margin: 0;
  border: 0;
}
.knowledge-level-filter legend {
  padding: 0;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}
.knowledge-level-tabs {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}
.knowledge-level-tabs button {
  position: relative;
  min-height: 44px;
  padding: 0 2px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.knowledge-level-tabs button.is-active {
  color: var(--text);
  font-weight: 500;
}
.knowledge-level-tabs button.is-active::after {
  position: absolute;
  bottom: 2px;
  left: calc(50% - 4px);
  width: 8px;
  height: 2px;
  border-radius: 2px;
  background: var(--mobile-accent, var(--brand));
  content: '';
}
.knowledge-filter-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.knowledge-filter-actions button {
  min-width: 72px;
  min-height: 44px;
  padding: 0 18px;
  border: 0;
  border-radius: 22px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.knowledge-filter-actions button[type='submit'] {
  background: var(--brand-soft);
  color: var(--brand);
}
.knowledge-chart-title,
.knowledge-section-heading h2 {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 12px 0 13px;
  font-size: 15px;
  font-weight: 500;
}
.knowledge-chart-title .mobile-icon {
  color: var(--accent);
}
.knowledge-overview {
  padding: 20px 24px 8px;
  margin-bottom: 24px;
  border-radius: 14px;
}
.knowledge-assessment-rule {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
.knowledge-assessment-rule summary {
  display: list-item;
  width: fit-content;
  min-height: 44px;
  padding-block: 11px;
  margin: 8px auto 0;
  cursor: pointer;
}
.knowledge-assessment-rule p {
  margin: 0 0 16px;
}
.knowledge-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
}
.knowledge-section-heading > span {
  color: var(--muted);
  font-size: 12px;
}
.knowledge-record {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(145px, 0.5fr) 90px;
  align-items: center;
  gap: 24px;
  padding: 20px;
  border-bottom: 1px solid var(--line);
}
.knowledge-record:last-child {
  border-bottom: 0;
}
.knowledge-record > div {
  min-width: 0;
}
.knowledge-record__title h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.6;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.knowledge-record__title p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-record__mastery > div:first-child {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.knowledge-record__mastery strong {
  font-size: 12px;
  font-weight: 500;
}
.knowledge-record__mastery b {
  font-size: 24px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.knowledge-record__mastery small {
  padding-left: 2px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 400;
}
.knowledge-record__mastery > span {
  display: block;
  margin-top: 7px;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.6;
}
.knowledge-record__mastery :deep(.el-progress-bar__outer) {
  background: var(--bg-secondary);
}
.knowledge-level--mastered {
  color: var(--brand);
}
.knowledge-level--good {
  color: var(--green);
}
.knowledge-level--weak {
  color: var(--accent);
}
.knowledge-level--risk {
  color: var(--red);
}
.knowledge-level--insufficient,
.knowledge-sample-score {
  color: var(--muted);
}
.knowledge-record__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.knowledge-record__actions button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 44px;
  min-width: 64px;
  padding: 0 8px;
  border: 0;
  border-radius: 22px;
  background: var(--brand-light);
  color: var(--brand);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.knowledge-record__actions button + button {
  background: transparent;
  color: var(--text-secondary);
}
.knowledge-record__actions button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.knowledge-practice-reason {
  margin-top: 12px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.knowledge-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px 20px;
  border-top: 1px solid var(--line);
}
.knowledge-pagination p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}
.knowledge-pagination :deep(.el-pagination) {
  flex-wrap: wrap;
  gap: 5px;
}
.knowledge-pagination :deep(.el-pagination__sizes) {
  margin-right: 12px;
}
.knowledge-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding: 20px;
  margin-bottom: 20px;
  border-radius: 14px;
  background: var(--panel);
}
.knowledge-notice strong {
  font-size: 14px;
  font-weight: 500;
}
.knowledge-page[aria-busy='true'] .knowledge-overview,
.knowledge-page[aria-busy='true'] .knowledge-records {
  opacity: 0.55;
  pointer-events: none;
}
.knowledge-state {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 14px;
  min-height: 235px;
  padding: 28px 16px;
  border-radius: 14px;
  background: var(--panel);
  color: var(--text-secondary);
  text-align: center;
  font-size: 13px;
}
.knowledge-state h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
}
.knowledge-state p {
  margin: 0 0 6px;
  line-height: 1.8;
}
.knowledge-detail-heading h2 {
  margin: 6px 0 10px;
  font-size: 22px;
  font-weight: 500;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-detail-heading p {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-detail-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
  margin: 24px 0;
}
.knowledge-detail-facts > div {
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
  min-width: 0;
}
.knowledge-detail-facts dt {
  color: var(--muted);
  font-size: 12px;
  margin-bottom: 9px;
}
.knowledge-detail-facts dd {
  margin: 0;
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-detail-advice {
  margin: 26px 0;
  padding: 0 0 0 16px;
  border-left: 2px solid color-mix(in srgb, var(--brand) 40%, transparent);
}
.knowledge-detail-advice h3 {
  margin: 0 0 10px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
}
.knowledge-detail-advice p {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}
.knowledge-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}
.knowledge-detail-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.knowledge-page button:focus-visible,
.knowledge-page input:focus-visible,
.knowledge-page select:focus-visible,
.knowledge-page summary:focus-visible,
.knowledge-page a:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}
@media (min-width: 761px) {
  .knowledge-page-heading {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .knowledge-back {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: var(--panel);
    color: var(--text-secondary);
  }
  .knowledge-overview {
    border: 0;
    background: var(--panel);
    box-shadow: none;
  }
  .knowledge-library {
    min-width: 0;
    overflow: hidden;
    border: 0;
    border-radius: 16px;
    background: var(--panel);
    box-shadow: none;
  }
  .knowledge-record {
    grid-template-columns: minmax(0, 1fr) 132px;
    gap: 12px 20px;
  }
  .knowledge-record__actions {
    grid-column: 1 / -1;
  }
  .knowledge-filter-panel {
    border: 0;
    border-radius: 16px;
  }
}
@media (min-width: 1000px) {
  .knowledge-content {
    display: grid;
    grid-template-columns: minmax(280px, 0.85fr) minmax(0, 1.5fr);
    align-items: start;
    gap: 24px;
  }
  .knowledge-summary {
    position: sticky;
    top: 96px;
  }
  .knowledge-summary .knowledge-chart-title {
    margin-top: 0;
  }
  .knowledge-overview {
    padding: 20px 20px 8px;
    margin: 0;
  }
  .knowledge-library:only-child {
    grid-column: 1 / -1;
  }
}
@media (max-width: 760px) {
  .knowledge-page {
    padding-top: 0;
  }
  .knowledge-page > .page-header {
    display: none;
  }
  .knowledge-toolbar {
    margin-top: -4px;
    margin-bottom: 4px;
  }
  .knowledge-filter-panel {
    border: 0;
  }
  .knowledge-filter-panel input,
  .knowledge-filter-panel select {
    font-size: 16px;
  }
  .knowledge-level-tabs {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .knowledge-overview {
    padding: 16px 16px 4px;
    border: 0;
    box-shadow: none;
    background: var(--panel);
  }
  .knowledge-library {
    border: 0;
    border-radius: 0;
    background: transparent;
    box-shadow: none;
  }
  .knowledge-section-heading {
    padding: 0;
  }
  .knowledge-records {
    overflow: hidden;
    border-radius: 14px;
    background: var(--panel);
  }
  .knowledge-record {
    grid-template-columns: minmax(0, 1fr) 116px;
    gap: 12px 16px;
    padding: 16px;
  }
  .knowledge-record__actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
  .knowledge-pagination {
    justify-content: center;
    padding: 16px 0;
    border: 0;
  }
  .knowledge-pagination :deep(.el-pagination__sizes) {
    display: none;
  }
  .knowledge-detail-facts {
    gap: 0 16px;
  }
}
@media (max-width: 359px) {
  .knowledge-record {
    grid-template-columns: minmax(0, 1fr) 104px;
    gap: 12px;
  }
  .knowledge-filter-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
