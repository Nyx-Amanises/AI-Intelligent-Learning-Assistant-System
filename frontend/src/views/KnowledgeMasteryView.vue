<template>
  <section class="learning-page knowledge-page" :aria-busy="loading" aria-labelledby="knowledge-title">
    <header class="page-header">
      <div>
        <h1 id="knowledge-title" class="page-title">知识掌握</h1>
        <p class="page-desc">看看哪些知识点已经掌握，哪些还需要练习。评估来自已完成评分的作答记录。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button :loading="loading" @click="loadMastery">
          <AppIcon v-if="!loading" name="refresh" :size="16" />刷新统计
        </el-button>
      </div>
    </header>

    <div v-if="materialsError" class="knowledge-notice" role="alert">
      <div>
        <strong>资料列表暂时无法加载</strong>
        <p>仍可查看知识点，重新加载后即可选择资料。</p>
      </div>
      <el-button :loading="materialsLoading" @click="loadMaterials">重新加载资料</el-button>
    </div>

    <section v-if="hasLoaded && !loadError" class="knowledge-overview surface" aria-label="知识点掌握概览">
      <StatTiles :items="overviewTiles" />
      <div class="knowledge-overview__notes">
        <span
          >知识点 <b>{{ overview.totalKnowledgePoints }}</b></span
        ><span
          >基本掌握 <b>{{ overview.goodCount }}</b></span
        ><span
          >待评估 <b>{{ overview.insufficientCount }}</b></span
        ><span
          >错题次数 <b>{{ overview.wrongAttempts }}</b></span
        ><span>{{ hasFilters ? '当前筛选范围的累计记录' : '全部练习的累计记录' }}</span>
      </div>
      <p class="knowledge-assessment-rule">
        完成至少
        {{ MASTERY_REQUIRED_QUESTION_COUNT }} 道不同的已评分题目后，才评估掌握等级；重复作答同一题不增加覆盖题数。
        未标注知识点的题目需先完善标注。待评估知识点的样本得分仅供参考，不计入平均掌握度或薄弱推荐。
      </p>
    </section>

    <section class="knowledge-library workspace-panel" aria-labelledby="knowledge-list-title">
      <div class="knowledge-section-heading">
        <h2 id="knowledge-list-title">知识点记录</h2>
        <span v-if="hasLoaded && !loadError">{{ total }} 个</span>
      </div>
      <div class="knowledge-filters" role="search" aria-label="筛选知识点">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索知识点、资料或建议"
          aria-label="搜索知识点、资料或建议"
          class="knowledge-search"
          ><template #prefix><AppIcon name="search" :size="17" /></template
        ></el-input>
        <el-select
          v-model="filters.materialId"
          clearable
          filterable
          placeholder="全部资料"
          aria-label="筛选学习资料"
          :loading="materialsLoading"
          ><el-option v-for="item in materials" :key="item.id" :label="item.title" :value="item.id"
        /></el-select>
        <el-select v-model="filters.masteryLevel" clearable placeholder="全部掌握状态" aria-label="筛选掌握状态"
          ><el-option v-for="item in masteryLevelOptions" :key="item.level" :label="item.label" :value="item.level"
        /></el-select>
        <el-select v-model="filters.questionType" clearable placeholder="全部题型" aria-label="筛选题型"
          ><el-option label="单选题" value="SINGLE" /><el-option label="多选题" value="MULTIPLE" /><el-option
            label="判断题"
            value="JUDGE" /><el-option label="简答题" value="SHORT"
        /></el-select>
        <el-button text :disabled="!hasFilters" @click="resetFilters">清除筛选</el-button>
      </div>

      <div v-if="hasLoaded && !loadError && reviewPoints.length" class="knowledge-priorities">
        <span>优先复习</span>
        <div>
          <article
            v-for="item in reviewPoints"
            :key="String(item.materialId || 0) + '-' + item.knowledgePoint"
            class="knowledge-priority"
          >
            <button
              type="button"
              class="knowledge-priority__point"
              :aria-label="'筛选 ' + item.knowledgePoint"
              @click="focusKnowledgePoint(item)"
            >
              {{ item.knowledgePoint }} <small>{{ item.masteryPercent }}%</small>
            </button>
            <span>{{ item.materialTitle || '未关联资料' }}</span>
            <div class="knowledge-priority__actions">
              <el-button
                type="primary"
                size="small"
                :disabled="Boolean(practiceUnavailableReason(item))"
                @click="goKnowledgePractice(item)"
                >去练习</el-button
              >
              <button type="button" @click="goWrongQuestions(item)">查看错题</button>
            </div>
            <p v-if="practiceUnavailableReason(item)" class="knowledge-practice-reason">
              {{ practiceUnavailableReason(item) }}
            </p>
          </article>
        </div>
      </div>
      <p
        v-else-if="hasLoaded && !loadError && overview.insufficientCount"
        class="knowledge-assessment-note"
        role="status"
      >
        {{ overview.assessedKnowledgePointCount ? '当前没有需要优先复习的已评估知识点。' : '当前知识点均待评估。' }}
        还有 {{ overview.insufficientCount }} 个知识点需补充不同题目的评分记录或完善标注，暂不判断是否薄弱。
      </p>

      <div v-if="loading && !hasLoaded" class="knowledge-state" role="status">正在加载知识掌握情况…</div>
      <div v-else-if="loadError" class="knowledge-notice" role="alert">
        <div>
          <strong>{{ loadError }}</strong>
          <p>数据暂时没有读取成功，请重试。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="loadMastery">重新加载统计</el-button>
      </div>
      <div v-else-if="hasLoaded && !records.length" class="knowledge-state knowledge-state--empty">
        <AppIcon name="mastery" :size="34" />
        <h3>{{ hasFilters ? '没有找到匹配的知识点' : '暂无知识掌握记录' }}</h3>
        <p>
          {{
            hasFilters
              ? '试试其他关键词，或清除筛选条件查看全部知识点。'
              : '提交包含知识点标注的练习并完成评分后，可查看各知识点的作答记录与评估结果。'
          }}
        </p>
        <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
        <el-button
          v-else
          type="primary"
          @click="router.push(materialsLoaded && !materials.length ? '/materials' : '/quiz')"
          >{{ materialsLoaded && !materials.length ? '添加学习资料' : '去练习' }}</el-button
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
            <span>{{ formatQuestionTypes(item.questionTypes) }}</span>
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
            <span>{{
              isMasteryAssessed(item) ? '掌握度' : hasScoredQuestions(item) ? '样本得分，仅供参考' : '暂无已评分题目'
            }}</span>
            <span class="knowledge-question-coverage"
              >不同已评分题 {{ item.uniqueQuestionCount }} / {{ requiredQuestionCount(item) }} 道</span
            >
            <span>{{ item.correctCount }} / {{ item.attemptCount }} 次正确</span>
          </div>
          <div class="knowledge-record__context">
            <p>{{ isMasteryAssessed(item) ? item.suggestion : assessmentReason(item) }}</p>
            <span>得分 {{ item.obtainedScore }} / {{ item.totalScore }} · 错题 {{ item.wrongCount }}</span
            ><span>最近练习 {{ formatDateTime(item.lastPracticeTime) }}</span>
            <p v-if="practiceUnavailableReason(item)" class="knowledge-practice-reason">
              {{ practiceUnavailableReason(item) }}
            </p>
          </div>
          <div class="knowledge-record__actions">
            <button
              type="button"
              :disabled="Boolean(practiceUnavailableReason(item))"
              @click="goKnowledgePractice(item)"
            >
              专项练习
            </button>
            <button type="button" @click="showDetail(item)">
              查看详情 <AppIcon name="arrow-up-right" :size="15" /></button
            ><button type="button" @click="goWrongQuestions(item)">查看错题</button>
          </div>
        </article>
      </div>

      <div v-if="hasLoaded && !loadError && total" class="knowledge-pagination">
        <p>共 {{ total }} 个知识点 · 第 {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页</p>
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

    <el-drawer v-model="drawerVisible" title="掌握度详情" size="min(580px, 100vw)">
      <template v-if="detail">
        <div class="knowledge-detail-heading">
          <h2>{{ detail.knowledgePoint }}</h2>
          <p>{{ detail.materialTitle || '未关联资料' }}</p>
        </div>
        <dl class="knowledge-detail-facts">
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
            <dd>{{ detail.accuracyRate }}%</dd>
          </div>
          <div>
            <dt>已评分作答得分率</dt>
            <dd>{{ hasScoredQuestions(detail) ? detail.scoreRate + '%' : '— · 暂无评分' }}</dd>
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
import StatTiles, { type StatTileItem } from '@/components/StatTiles.vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getKnowledgeMasteryOverviewApi,
  type KnowledgeMasteryItem,
  type KnowledgeMasteryOverviewPayload
} from '@/api/modules/knowledgeMastery'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import {
  MASTERY_LEVELS,
  MASTERY_REQUIRED_QUESTION_COUNT,
  isMasteryAssessed,
  needsKnowledgeReview,
  masteryColor
} from '@/utils/mastery'
import { formatQuestionTypes } from '@/utils/labels'
import { knowledgePracticeUnavailableReason } from '@/utils/knowledgePractice'

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
  insufficientCount: 0,
  assessedKnowledgePointCount: 0,
  averageMasteryPercent: null as number | null,
  weakestPoints: [] as KnowledgeMasteryItem[]
})

const reviewPoints = computed(() => overview.weakestPoints.filter(needsKnowledgeReview))
const requiredQuestionCount = (item: KnowledgeMasteryItem) =>
  item.requiredQuestionCount || MASTERY_REQUIRED_QUESTION_COUNT
const hasScoredQuestions = (item: KnowledgeMasteryItem) => item.uniqueQuestionCount > 0
const displayMasteryLevel = (item: KnowledgeMasteryItem) =>
  isMasteryAssessed(item) ? item.masteryLevel : 'INSUFFICIENT'
const assessmentReason = (item: KnowledgeMasteryItem) =>
  item.assessmentReason ||
  `已覆盖 ${item.uniqueQuestionCount} 道不同的已评分题目，至少需要 ${requiredQuestionCount(item)} 道后再评估。`

// 待评估优先列出，其余筛选项按掌握度从低到高排列。
const masteryLevelOptions = [...MASTERY_LEVELS].reverse()
const levelMin = (level: string) => MASTERY_LEVELS.find((item) => item.level === level)?.min ?? 0
const overviewTiles = computed<StatTileItem[]>(() => [
  {
    key: 'average',
    label: '平均掌握度',
    value: overview.averageMasteryPercent ?? '—',
    unit: overview.averageMasteryPercent === null ? '' : '%',
    note: overview.assessedKnowledgePointCount
      ? `仅统计 ${overview.assessedKnowledgePointCount} 个已评估知识点`
      : '暂无可评估知识点'
  },
  { key: 'mastered', label: '已掌握', value: overview.masteredCount, note: '掌握度 ≥ ' + levelMin('MASTERED') + '%' },
  {
    key: 'weak',
    label: '待巩固',
    value: overview.weakCount,
    note: '掌握度 ' + levelMin('WEAK') + '% – ' + (levelMin('GOOD') - 1) + '%'
  },
  {
    key: 'risk',
    label: '薄弱点',
    value: overview.riskCount,
    note: '掌握度低于 ' + levelMin('WEAK') + '%',
    tone: 'danger'
  }
])

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
.knowledge-page {
  padding-bottom: 24px;
  color: var(--text);
}
.knowledge-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.knowledge-overview {
  padding: 24px;
  margin-bottom: 20px;
}
.knowledge-overview__notes {
  display: flex;
  flex-wrap: wrap;
  gap: 11px 24px;
  padding-top: 21px;
  margin-top: 23px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}
.knowledge-overview__notes b {
  padding-left: 5px;
  color: var(--text-secondary);
  font-weight: 500;
}
.knowledge-overview__notes > span:last-child {
  margin-left: auto;
}
.knowledge-assessment-rule,
.knowledge-assessment-note {
  margin: 18px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.9;
}
.knowledge-assessment-note {
  margin: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
}
.knowledge-section-heading {
  padding: 16px 20px 0;
  margin-bottom: 14px;
}
.knowledge-section-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}
.knowledge-section-heading > span {
  font-size: 12px;
  color: var(--muted);
}
.knowledge-filters {
  display: grid;
  grid-template-columns: minmax(200px, 1.5fr) minmax(130px, 1fr) minmax(130px, 0.85fr) minmax(120px, 0.8fr) auto;
  gap: 12px;
  padding: 0 20px 16px;
  border-bottom: 1px solid var(--line);
}
.knowledge-filters > * {
  min-width: 0;
}
.knowledge-priorities {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 16px 20px 8px;
  border-bottom: 1px solid var(--line);
  background: var(--panel-hover);
}
.knowledge-priorities > span {
  flex-shrink: 0;
  color: var(--muted);
  font-size: 12px;
}
.knowledge-priorities > div {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 22px;
  min-width: 0;
  flex: 1;
}
.knowledge-priority {
  min-width: 0;
  padding-bottom: 10px;
}
.knowledge-priority > span {
  display: block;
  margin-bottom: 9px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-priority__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.knowledge-priority button:not(.el-button) {
  min-height: 40px;
  padding: 8px 0;
  background: transparent;
  border: 0;
  color: var(--red);
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-priority button:not(.el-button):hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}
.knowledge-priority__actions button:not(.el-button) {
  color: var(--muted);
}
.knowledge-practice-reason {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-priorities small {
  padding-left: 4px;
  color: var(--muted);
  font-size: 11px;
}
.knowledge-record {
  display: grid;
  grid-template-columns: minmax(170px, 1.1fr) 160px minmax(230px, 1.45fr) 96px;
  align-items: start;
  gap: 24px;
  padding: 20px;
  border-bottom: 1px solid var(--line);
  transition: background-color var(--duration-fast) ease;
}
.knowledge-record:hover {
  background: var(--panel-hover);
}
.knowledge-record:last-child {
  border-bottom: 0;
}
.knowledge-record > div {
  min-width: 0;
}
.knowledge-record__title h3 {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.knowledge-record__title p {
  margin: 9px 0 4px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.knowledge-record__title > span {
  color: var(--muted);
  font-size: 11px;
}
.knowledge-record__mastery {
  padding-top: 3px;
}
.knowledge-record__mastery > div:first-child {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 11px;
}
.knowledge-record__mastery strong {
  font-size: 12px;
  font-weight: 500;
}
.knowledge-record__mastery b {
  font-size: 21px;
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
  margin-top: 11px;
  color: var(--muted);
  font-size: 11px;
}
.knowledge-record__mastery :deep(.el-progress-bar__outer) {
  background: var(--line);
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
.knowledge-record__context p {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--muted);
  overflow-wrap: anywhere;
}
.knowledge-record__context span {
  display: block;
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}
.knowledge-record__actions {
  display: grid;
  justify-items: end;
}
.knowledge-record__actions button {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  min-height: 36px;
  padding: 6px 0;
  border: 0;
  background: transparent;
  font: inherit;
  font-size: 12px;
  color: var(--brand);
  white-space: nowrap;
  cursor: pointer;
}
.knowledge-record__actions button + button {
  color: var(--muted);
}
.knowledge-record__actions button:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}
.knowledge-record__actions button:disabled {
  color: var(--muted);
  opacity: 0.6;
  cursor: not-allowed;
  text-decoration: none;
}
.knowledge-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  padding: 14px 20px;
  border-top: 1px solid var(--line);
}
.knowledge-pagination p {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
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
  gap: 20px;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  margin-bottom: 20px;
  background: var(--panel);
}
.knowledge-notice strong {
  font-size: 15px;
  font-weight: 500;
}
.knowledge-notice p {
  color: var(--muted);
  font-size: 12px;
  margin: 8px 0 0;
  line-height: 1.8;
}
.knowledge-page[aria-busy='true'] .knowledge-overview,
.knowledge-page[aria-busy='true'] .knowledge-records,
.knowledge-page[aria-busy='true'] .knowledge-priorities {
  opacity: 0.55;
  transition: opacity 0.2s ease;
  pointer-events: none;
}
.knowledge-state {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 14px;
  padding: 32px 16px;
  min-height: 300px;
  text-align: center;
  color: var(--muted);
  font-size: 13px;
}
.knowledge-state .app-icon {
  color: var(--muted);
}
.knowledge-state h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 500;
  color: var(--text-secondary);
}
.knowledge-state p {
  max-width: 400px;
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.9;
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
.knowledge-page a:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 4px;
}
@media (max-width: 1100px) {
  .knowledge-record {
    grid-template-columns: minmax(160px, 1fr) 145px minmax(170px, 1.2fr) 85px;
    gap: 22px;
  }
  .knowledge-filters {
    grid-template-columns: minmax(200px, 1.4fr) minmax(130px, 1fr) minmax(120px, 1fr);
  }
  .knowledge-filters > :nth-child(4) {
    grid-column: 2;
  }
  .knowledge-filters > :last-child {
    justify-self: start;
  }
}
@media (max-width: 840px) {
  .knowledge-overview {
    padding: 24px;
  }
  .knowledge-record {
    grid-template-columns: minmax(0, 1fr) 155px;
    gap: 20px 28px;
  }
  .knowledge-record__context {
    grid-column: 1;
  }
  .knowledge-record__actions {
    grid-column: 2;
    align-self: end;
  }
  .knowledge-overview__notes > span:last-child {
    margin-left: 0;
    flex-basis: 100%;
  }
}
@media (max-width: 600px) {
  .knowledge-page {
    padding-top: 4px;
  }
  .knowledge-overview {
    padding: 18px 16px;
  }
  .knowledge-overview__notes {
    font-size: 11px;
    gap: 10px 16px;
  }
  .knowledge-section-heading h2 {
    font-size: 16px;
  }
  .knowledge-section-heading,
  .knowledge-assessment-note,
  .knowledge-priorities {
    padding-inline: 16px;
  }
  .knowledge-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 11px;
    padding-inline: 16px;
  }
  .knowledge-search {
    grid-column: 1 / -1;
  }
  .knowledge-filters > :nth-child(4) {
    grid-column: auto;
  }
  .knowledge-filters > :last-child {
    justify-self: stretch;
    text-align: center;
  }
  .knowledge-priorities {
    display: block;
    padding-block: 17px 9px;
  }
  .knowledge-priorities > div {
    margin-top: 7px;
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }
  .knowledge-record {
    grid-template-columns: minmax(0, 1fr) 116px;
    gap: 18px 21px;
    padding: 18px 16px;
  }
  .knowledge-record__title h3 {
    font-size: 15px;
  }
  .knowledge-record__title p {
    font-size: 12px;
  }
  .knowledge-record__mastery {
    padding-top: 1px;
  }
  .knowledge-record__mastery strong {
    font-size: 11px;
  }
  .knowledge-record__mastery b {
    font-size: 20px;
  }
  .knowledge-record__mastery > div:first-child {
    gap: 6px;
  }
  .knowledge-record__context {
    grid-column: 1 / -1;
  }
  .knowledge-record__context p {
    margin-bottom: 7px;
    font-size: 12px;
  }
  .knowledge-record__actions {
    grid-column: 1 / -1;
    display: flex;
    flex-wrap: wrap;
    gap: 26px;
    margin-top: -5px;
  }
  .knowledge-record__actions button {
    min-height: 40px;
    font-size: 12px;
  }
  .knowledge-pagination {
    display: grid;
    justify-content: center;
    gap: 16px;
    text-align: center;
  }
  .knowledge-pagination :deep(.el-pagination) {
    justify-content: center;
  }
  .knowledge-pagination :deep(.el-pagination__sizes) {
    display: none;
  }
  .knowledge-detail-facts {
    gap: 0 20px;
  }
}
@media (max-width: 760px) {
  .knowledge-page {
    padding-top: 0;
  }
  .knowledge-page > .page-header {
    position: relative;
  }
  .knowledge-page .page-title {
    padding-right: 108px;
  }
  .knowledge-page .page-header .toolbar--flush {
    position: absolute;
    top: 0;
    right: 0;
    width: auto;
  }
  .knowledge-page .page-header .el-button {
    height: 40px;
    padding-inline: 12px;
    border: 0;
    background: transparent;
    font-size: 13px;
  }
  .knowledge-overview {
    margin-bottom: 30px;
    padding: 4px 0 24px;
    border: 0;
    border-bottom: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
  }
  .knowledge-overview :deep(.stat-tile) {
    border-left: 0;
  }
  .knowledge-overview :deep(.stat-tile dd) {
    margin-top: 10px;
    font-size: 42px;
    font-weight: 500;
  }
  .knowledge-overview__notes {
    margin-top: 24px;
    gap: 8px 16px;
    font-size: 12px;
  }
  .knowledge-assessment-rule {
    margin-top: 14px;
    line-height: 1.8;
  }
  .knowledge-section-heading {
    padding: 0;
    margin-bottom: 18px;
  }
  .knowledge-section-heading h2 {
    font-size: 20px;
    letter-spacing: -0.02em;
  }
  .knowledge-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    padding: 0 0 20px;
  }
  .knowledge-search {
    grid-column: 1 / -1;
  }
  .knowledge-filters > :nth-child(4) {
    grid-column: auto;
  }
  .knowledge-filters > :last-child {
    justify-self: start;
  }
  .knowledge-priorities {
    display: block;
    margin-top: 20px;
    padding: 16px;
    border: 0;
    border-radius: 16px;
    background: var(--panel);
  }
  .knowledge-priorities > div {
    grid-template-columns: minmax(0, 1fr);
    margin-top: 8px;
    gap: 8px;
  }
  .knowledge-priority__point {
    font-size: 15px;
  }
  .knowledge-assessment-note {
    padding: 18px 0;
  }
  .knowledge-record {
    grid-template-columns: minmax(0, 1fr) 116px;
    gap: 18px 20px;
    padding: 24px 0;
  }
  .knowledge-record__title h3 {
    font-size: 18px;
    font-weight: 600;
  }
  .knowledge-record__title p {
    margin-top: 6px;
    font-size: 13px;
  }
  .knowledge-record__mastery b {
    font-size: 28px;
  }
  .knowledge-record__mastery > div:first-child {
    gap: 6px;
  }
  .knowledge-record__context {
    grid-column: 1 / -1;
  }
  .knowledge-record__context p {
    font-size: 14px;
    line-height: 1.8;
  }
  .knowledge-record__actions {
    grid-column: 1 / -1;
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    margin-top: -6px;
  }
  .knowledge-record__actions button {
    min-height: 44px;
    font-size: 14px;
  }
  .knowledge-pagination {
    padding: 20px 0;
  }
  .knowledge-pagination :deep(.el-pagination__sizes) {
    display: none;
  }
  .knowledge-notice {
    padding: 20px;
    border: 0;
    border-radius: 18px;
  }
}
</style>
