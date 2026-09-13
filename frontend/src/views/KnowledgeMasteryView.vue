<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">知识点掌握度</h1>
        <p class="page-desc">
          看看哪些知识已经掌握，哪些还值得再练一次。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="loading" @click="loadMastery">刷新统计</el-button>
      </div>
    </div>

    <div v-if="materialsError" class="mastery-load-error" role="alert">
      <div><strong>资料列表暂时无法加载</strong><p>可以继续查看知识点，重试后即可选择资料。</p></div>
      <el-button :loading="materialsLoading" @click="loadMaterials">重新加载资料</el-button>
    </div>

    <div v-if="hasLoaded && !loading && !loadError" class="mastery-summary-grid">
      <div class="mastery-summary-card mastery-summary-card--main">
        <span>平均掌握度</span>
        <strong>{{ overview.averageMasteryPercent }}%</strong>
        <p>来自 {{ overview.totalAttempts }} 次答题记录</p>
      </div>
      <div class="mastery-summary-card">
        <span>已掌握</span>
        <strong>{{ overview.masteredCount }}</strong>
        <p>掌握度 ≥ 85%</p>
      </div>
      <div class="mastery-summary-card">
        <span>待巩固</span>
        <strong>{{ overview.weakCount }}</strong>
        <p>掌握度 50% - 69%</p>
      </div>
      <div class="mastery-summary-card mastery-summary-card--risk">
        <span>薄弱点</span>
        <strong>{{ overview.riskCount }}</strong>
        <p>掌握度低于 50%</p>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="workspace-filter-bar workspace-filter-bar--mastery">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索知识点 / 资料 / 建议"
            class="workspace-filter-bar__search"
          />
          <el-select
            v-model="filters.materialId"
            clearable
            filterable
            placeholder="全部资料"
            aria-label="筛选学习资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
          <el-select v-model="filters.masteryLevel" clearable placeholder="掌握状态">
            <el-option label="薄弱" value="RISK" />
            <el-option label="待巩固" value="WEAK" />
            <el-option label="基本掌握" value="GOOD" />
            <el-option label="已掌握" value="MASTERED" />
          </el-select>
          <el-select v-model="filters.questionType" clearable placeholder="全部题型" aria-label="筛选题型">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="简答题" value="SHORT" />
          </el-select>
          <el-button @click="resetFilters">重置条件</el-button>
        </div>

        <div v-if="hasLoaded && !loading && !loadError" class="workspace-toolbar__meta">
          <span class="workspace-chip">知识点 {{ overview.totalKnowledgePoints }}</span>
          <span class="workspace-chip workspace-chip--brand">错题 {{ overview.wrongAttempts }}</span>
        </div>
      </div>

      <div v-if="hasLoaded && !loading && !loadError && reviewPoints.length" class="mastery-weak-list">
        <div class="mastery-weak-list__title">优先复习</div>
        <button
          v-for="item in reviewPoints"
          :key="`${item.materialId || 0}-${item.knowledgePoint}`"
          class="mastery-weak-pill"
          type="button"
          @click="focusKnowledgePoint(item)"
        >
          {{ item.knowledgePoint }} · {{ item.masteryPercent }}%
        </button>
      </div>

      <div class="workspace-body">
        <div v-if="loading" class="state-block">正在计算知识点掌握度...</div>
        <div v-else-if="loadError" class="mastery-load-error mastery-load-error--body" role="alert">
          <div><strong>{{ loadError }}</strong><p>数据暂时没有读取成功，请重试。</p></div>
          <el-button type="primary" :loading="loading" @click="loadMastery">重新加载统计</el-button>
        </div>
        <div v-else-if="hasLoaded && !records.length" class="state-block empty mastery-empty">
          <h3>{{ hasFilters ? '没有找到匹配的知识点' : '从一次练习开始，了解自己的掌握程度' }}</h3>
          <p>{{ hasFilters ? '试试其他关键词，或清除筛选条件查看全部知识点。' : '提交练习后，这里会整理已掌握的知识与需要巩固的重点。' }}</p>
          <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
          <el-button v-else type="primary" @click="router.push(materialsLoaded && !materials.length ? '/materials' : '/quiz')">{{ materialsLoaded && !materials.length ? '添加学习资料' : '去做一次练习' }}</el-button>
        </div>
        <div v-else-if="hasLoaded" class="workspace-table">
          <div class="workspace-table__head workspace-table__head--mastery">
            <span>知识点</span>
            <span>掌握度</span>
            <span>练习情况</span>
            <span>来源</span>
            <span>复习建议</span>
            <span>操作</span>
          </div>

          <div
            v-for="item in records"
            :key="`${item.materialId || 0}-${item.knowledgePoint}`"
            class="workspace-table__row workspace-table__row--mastery"
          >
            <div class="workspace-table__title workspace-table__title--truncate">
              <strong>{{ item.knowledgePoint }}</strong>
              <span>{{ formatQuestionTypes(item.questionTypes) }}</span>
            </div>
            <div class="mastery-progress-cell">
              <div class="mastery-progress-cell__top">
                <strong :class="`mastery-level mastery-level--${item.masteryLevel.toLowerCase()}`">
                  {{ item.masteryLabel }}
                </strong>
                <span>{{ item.masteryPercent }}%</span>
              </div>
              <el-progress
                :percentage="item.masteryPercent"
                :stroke-width="8"
                :show-text="false"
                :color="progressColor(item.masteryPercent)"
              />
            </div>
            <div class="task-meta-stack">
              <strong>{{ item.correctCount }} / {{ item.attemptCount }} 次正确</strong>
              <span>得分 {{ item.obtainedScore }} / {{ item.totalScore }} · 错题 {{ item.wrongCount }}</span>
            </div>
            <div class="task-meta-stack">
              <strong>{{ item.materialTitle || '未关联资料' }}</strong>
              <span>最近 {{ formatDateTime(item.lastPracticeTime) }}</span>
            </div>
            <p class="mastery-suggestion">{{ item.suggestion }}</p>
            <div class="workspace-action-row workspace-action-row--fit">
              <el-button link type="primary" @click="showDetail(item)">查看详情</el-button>
              <el-button link type="danger" @click="goWrongQuestions(item)">查看错题</el-button>
            </div>
          </div>
        </div>

        <div v-if="hasLoaded && !loading && !loadError && total" class="workspace-pagination">
          <div class="workspace-pagination__meta">
            第 {{ page.current }} / {{ Math.max(1, Math.ceil(total / page.size)) }} 页
          </div>
          <el-pagination
            v-model:current-page="page.current"
            v-model:page-size="page.size"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="total"
            @current-change="loadMastery"
            @size-change="loadMastery"
          />
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="掌握度详情" size="46%">
      <template v-if="detail">
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span>知识点</span>
            <strong>{{ detail.knowledgePoint }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>来源资料</span>
            <strong>{{ detail.materialTitle || '未关联资料' }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>掌握状态</span>
            <strong>{{ detail.masteryLabel }} · {{ detail.masteryPercent }}%</strong>
          </div>
          <div class="detail-meta-item">
            <span>题目覆盖</span>
            <strong>{{ detail.uniqueQuestionCount }} 道题 · {{ detail.attemptCount }} 次作答</strong>
          </div>
        </div>

        <div class="mastery-detail-panel">
          <div class="mastery-detail-row">
            <span>正确率</span>
            <strong>{{ detail.accuracyRate }}%</strong>
          </div>
          <div class="mastery-detail-row">
            <span>得分率</span>
            <strong>{{ detail.scoreRate }}%</strong>
          </div>
          <div class="mastery-detail-row">
            <span>错题次数</span>
            <strong>{{ detail.wrongCount }}</strong>
          </div>
          <div class="mastery-detail-row">
            <span>最近练习</span>
            <strong>{{ formatDateTime(detail.lastPracticeTime) }}</strong>
          </div>
        </div>

        <div class="analysis-box">
          <strong>系统建议</strong>
          <p>{{ detail.suggestion }}</p>
        </div>

        <div class="task-detail-actions">
          <el-button type="primary" @click="focusKnowledgePoint(detail)">按此知识点筛选</el-button>
          <el-button type="danger" plain @click="goWrongQuestions(detail)">查看相关错题</el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
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
    return 'var(--green)'
  }
  if (percent >= 70) {
    return 'var(--blue)'
  }
  if (percent >= 50) {
    return 'var(--accent)'
  }
  return 'var(--red)'
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
    materials.value = [...records, ...remainingPages.flatMap((response) => response.data.data.records || [])]
    materialsLoaded.value = true
  } catch {
    materialsError.value = '资料列表暂时无法加载'
  } finally {
    materialsLoading.value = false
  }
}

const loadMastery = async () => {
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
    applyOverview(res.data.data as KnowledgeMasteryOverviewPayload)
    hasLoaded.value = true
  } catch {
    loadError.value = '知识点掌握度暂时无法加载'
  } finally {
    loading.value = false
  }
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

void loadMaterials()
void loadMastery()
</script>

<style scoped>
.mastery-load-error { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; padding: 22px 24px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); }
.mastery-load-error--body { margin: 0; }
.mastery-load-error strong { color: var(--text); font-size: 15px; }
.mastery-load-error p { margin: 7px 0 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.mastery-empty { display: grid; justify-items: center; align-content: center; gap: 12px; min-height: 240px; padding: 32px 20px; text-align: center; }
.mastery-empty h3 { margin: 0; color: var(--text); font-size: 17px; font-weight: 600; line-height: 1.7; }
.mastery-empty p { max-width: 450px; margin: 0 0 6px; color: var(--muted); font-size: 13px; line-height: 1.9; }
</style>
