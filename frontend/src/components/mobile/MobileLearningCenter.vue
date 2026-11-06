<template>
  <div class="mobile-learning-center">
    <MobileAnalyticsOverview
      v-if="view === 'overview'"
      :materials="materials"
      :materials-loading="materialsLoading"
      :materials-error="materialsError"
      @reload-materials="emit('reload-materials')"
    />
    <MobileStudyCalendar v-else-if="view === 'calendar'" />
    <template v-else>
      <section aria-labelledby="mobile-learning-content-title">
        <div class="mobile-learning-center__section-heading">
          <h2 id="mobile-learning-content-title">学习内容</h2>
          <RouterLink class="mobile-learning-center__pill" to="/materials">换份资料</RouterLink>
        </div>
        <div class="mobile-learning-center__card mobile-learning-content" :aria-busy="materialsLoading || draftLoading">
          <p v-if="materialsLoading || draftLoading" class="mobile-learning-center__status" role="status">
            正在读取学习内容…
          </p>
          <template v-else-if="material || draft">
            <div class="mobile-learning-content__main">
              <RouterLink
                :to="material ? materialRoute(material.id) : practiceRoute(draft!.sessionId)"
                class="mobile-learning-content__cover"
                :aria-label="material ? `打开资料：${material.title}` : `继续练习：${draft!.sessionName}`"
              >
                <span>{{ material ? formatMaterialType(material.materialType, '资料') : '练习' }}</span>
                <strong>{{ material?.title || draft?.sessionName }}</strong>
                <MobileIcon name="book" :size="24" />
              </RouterLink>
              <div class="mobile-learning-content__description">
                <p class="mobile-learning-content__eyebrow">{{ draft ? '接着上次' : '当前资料' }}</p>
                <h3>{{ draft?.sessionName || material?.title }}</h3>
                <p v-if="draft">{{ draft.answeredCount }} / {{ draft.total }} 道题已作答</p>
                <p v-else-if="materialPerformance">已完成 {{ materialPerformance.practiceCount }} 次练习</p>
                <p v-else>
                  {{
                    material?.totalCharacters ? `${formatNumber(material.totalCharacters)} 字` : '从阅读这份资料开始'
                  }}
                </p>
                <RouterLink v-if="material" :to="summaryRoute(material.id)" class="mobile-learning-content__summary">
                  <MobileIcon name="book" :size="15" />知识总结<MobileIcon name="next" :size="13" />
                </RouterLink>
              </div>
            </div>
            <div v-if="draft?.total" class="mobile-learning-content__progress">
              <div
                role="progressbar"
                aria-label="当前练习作答进度"
                :aria-valuenow="draft.answeredCount"
                :aria-valuemax="draft.total"
                aria-valuemin="0"
              >
                <span :style="{ width: `${(draft.answeredCount / draft.total) * 100}%` }" />
              </div>
              <p>
                <span>已作答 {{ draft.answeredCount }} 题</span><span>共 {{ draft.total }} 题</span>
              </p>
            </div>
            <div v-else-if="materialPerformance?.attemptCount" class="mobile-learning-content__progress">
              <div
                role="progressbar"
                aria-label="这份资料的练习正确率"
                :aria-valuenow="materialPerformance.accuracyRate"
                aria-valuemax="100"
                aria-valuemin="0"
              >
                <span :style="{ width: `${Math.min(100, Math.max(0, materialPerformance.accuracyRate))}%` }" />
              </div>
              <p>
                <span>练习正确率 {{ formatNumber(materialPerformance.accuracyRate) }}%</span
                ><span>{{ materialPerformance.attemptCount }} 次作答</span>
              </p>
            </div>
            <div class="mobile-learning-content__footer">
              <span>{{ draft ? '练习进度已暂存' : '读一点，再练一练' }}</span>
              <RouterLink :to="draft ? practiceRoute(draft.sessionId) : materialRoute(material!.id)">
                {{ draft ? '继续练习' : '开始学习' }}<MobileIcon name="next" :size="15" />
              </RouterLink>
            </div>
          </template>
          <div v-else class="mobile-learning-center__empty">
            <MobileIcon name="book" :size="32" />
            <h3>{{ materialsError ? '学习资料暂时无法加载' : '从第一份资料开始' }}</h3>
            <p>{{ materialsError ? '重新加载后，即可继续学习。' : '添加讲义或笔记，开始阅读与练习。' }}</p>
            <button v-if="materialsError" type="button" @click="emit('reload-materials')">重新加载</button>
            <RouterLink v-else :to="uploadRoute">添加学习资料</RouterLink>
          </div>
        </div>
      </section>

      <section aria-labelledby="mobile-learning-statistics-title">
        <div class="mobile-learning-center__section-heading mobile-learning-center__section-heading--statistics">
          <h2 id="mobile-learning-statistics-title">统计</h2>
          <button
            class="mobile-learning-center__refresh"
            type="button"
            :disabled="loading"
            aria-label="刷新学习统计"
            @click="loadOverview"
          >
            <AppIcon name="refresh" :size="17" />
          </button>
        </div>
        <div class="mobile-learning-center__card mobile-learning-overview" :aria-busy="loading">
          <RouterLink
            class="mobile-learning-center__card-heading"
            :to="{ path: '/analytics', query: { view: 'overview' } }"
          >
            <h3>概览</h3>
            <MobileIcon name="next" :size="18" />
          </RouterLink>
          <div v-if="error" class="mobile-learning-center__error" role="alert">
            <p>{{ error }}</p>
            <button type="button" @click="loadOverview">重新加载</button>
          </div>
          <dl v-else class="mobile-learning-overview__metrics">
            <div v-for="metric in metrics" :key="metric.label">
              <dt>
                <MobileIcon :name="metric.icon" :size="17" :style="{ color: metric.color }" /><span>{{
                  metric.label
                }}</span>
              </dt>
              <dd>
                {{ loading ? '—' : metric.value }}<small v-if="!loading">{{ metric.unit }}</small>
              </dd>
            </div>
          </dl>
        </div>
        <MobileStudyCalendar compact />
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import MobileAnalyticsOverview from './MobileAnalyticsOverview.vue'
import MobileStudyCalendar from './MobileStudyCalendar.vue'
import { getLearningAnalyticsOverviewApi, type LearningAnalyticsOverviewPayload } from '@/api/modules/learningAnalytics'
import type { MaterialPageItem } from '@/api/modules/material'
import { getPracticeDetailApi } from '@/api/modules/practice'
import type { PracticeDetail } from '@/components/practice/practiceHelpers'
import { materialRoute, practiceRoute, summaryRoute, uploadRoute } from '@/components/dashboard/routes'
import { readActiveDraftSessionId, readDraftAnswers } from '@/utils/practiceDraft'
import { formatMaterialType } from '@/utils/labels'

const props = defineProps<{
  materials: MaterialPageItem[]
  materialsLoading: boolean
  materialsError: string
}>()
const emit = defineEmits<{ 'reload-materials': [] }>()
const route = useRoute()
const view = computed(() =>
  route.query.view === 'overview' || route.query.view === 'calendar' ? route.query.view : 'learning'
)
const draft = ref<{
  sessionId: number
  sessionName: string
  materialId?: number
  answeredCount: number
  total: number
} | null>(null)
const draftLoading = ref(false)
let draftLoaded = false
let active = true
const analytics = ref<LearningAnalyticsOverviewPayload | null>(null)
const loading = ref(false)
const error = ref('')
let overviewRequestId = 0
const loadOverview = async () => {
  const requestId = ++overviewRequestId
  loading.value = true
  error.value = ''
  try {
    const response = await getLearningAnalyticsOverviewApi({ trendLimit: 5 })
    if (active && requestId === overviewRequestId)
      analytics.value = response.data.data as LearningAnalyticsOverviewPayload
  } catch {
    if (active && requestId === overviewRequestId) error.value = '学习统计暂时无法加载'
  } finally {
    if (active && requestId === overviewRequestId) loading.value = false
  }
}

const loadDraft = async () => {
  if (draftLoaded) return
  draftLoaded = true
  const sessionId = readActiveDraftSessionId()
  if (!sessionId) return
  draftLoading.value = true
  try {
    const detail = (await getPracticeDetailApi(sessionId)).data.data as PracticeDetail
    if (!active || !detail?.sessionId || String(detail.sessionStatus).toUpperCase() === 'SUBMITTED') return
    const answers = readDraftAnswers(detail.sessionId)
    const questions = detail.answers || []
    draft.value = {
      sessionId: detail.sessionId,
      sessionName: detail.sessionName,
      materialId: detail.materialId,
      answeredCount: questions.filter((item) => Boolean(answers[item.questionId]?.trim())).length,
      total: questions.length
    }
  } catch {
    // 删除或失效的本机草稿不影响继续阅读资料。
  } finally {
    if (active) draftLoading.value = false
  }
}
watch(
  view,
  (value) => {
    if (value === 'learning') {
      void loadDraft()
      if (!analytics.value && !loading.value) void loadOverview()
    }
  },
  { immediate: true }
)
onUnmounted(() => {
  active = false
})

const material = computed(() =>
  draft.value ? props.materials.find((item) => item.id === draft.value?.materialId) || null : props.materials[0] || null
)
const materialPerformance = computed(() =>
  analytics.value?.materialPerformance.find((item) => item.materialId === material.value?.id)
)
const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 }).format(value)
const metrics = computed(() => {
  const duration = Math.max(0, Number(analytics.value?.totalStudySeconds) || 0)
  return [
    {
      label: '累计练习',
      icon: 'pencil',
      value: formatNumber(analytics.value?.totalPracticeCount || 0),
      unit: '次',
      color: '#f5af08'
    },
    {
      label: '累计作答',
      icon: 'trend',
      value: formatNumber(analytics.value?.totalQuestionAttempts || 0),
      unit: '次',
      color: '#ef6977'
    },
    {
      label: '练习时长',
      icon: 'clock',
      value: formatNumber(duration > 0 && duration < 60 ? duration : Math.floor(duration / 60)),
      unit: duration > 0 && duration < 60 ? '秒' : '分钟',
      color: '#f5af08'
    },
    {
      label: '平均正确率',
      icon: 'check',
      value: analytics.value?.totalQuestionAttempts ? formatNumber(analytics.value.averageAccuracyRate) : '—',
      unit: analytics.value?.totalQuestionAttempts ? '%' : '',
      color: '#ef6977'
    }
  ]
})
</script>

<style scoped>
.mobile-learning-center {
  color: var(--text);
  font-size: 14px;
  padding: 0 0 24px;
}
.mobile-learning-center__section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin: 0 16px 8px;
}
.mobile-learning-center__section-heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.mobile-learning-center__section-heading--statistics {
  margin-top: 25px;
}
.mobile-learning-center__pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 3px 12px;
  border-radius: 18px;
  color: #cb5700;
  background: var(--brand-soft);
  text-decoration: none;
  font-size: 12px;
}
.mobile-learning-center__card {
  overflow: hidden;
  border-radius: 14px;
  background: var(--panel);
}
.mobile-learning-content__main {
  display: flex;
  gap: 20px;
  padding: 16px;
}
.mobile-learning-content__cover {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  flex: 0 0 78px;
  height: 108px;
  padding: 9px;
  border-radius: 4px;
  color: #fff;
  background: #444b67;
  text-decoration: none;
  box-shadow: inset 3px 0 rgb(255 255 255 / 10%);
}
.mobile-learning-content__cover > span {
  font-size: 8px;
  opacity: 0.78;
}
.mobile-learning-content__cover strong {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.mobile-learning-content__cover > .mobile-icon {
  align-self: flex-end;
  opacity: 0.5;
}
.mobile-learning-content__description {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}
.mobile-learning-content__eyebrow {
  margin: 0 0 6px !important;
  font-size: 11px !important;
  color: var(--muted) !important;
}
.mobile-learning-content__description h3 {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.mobile-learning-content__description p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}
.mobile-learning-content__summary {
  display: flex;
  align-items: center;
  gap: 5px;
  width: fit-content;
  min-height: 32px;
  margin-top: 5px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 11px;
}
.mobile-learning-content__summary > .mobile-icon:last-child {
  color: var(--muted);
}
.mobile-learning-content__progress {
  padding: 5px 16px 12px;
}
.mobile-learning-content__progress > div {
  height: 7px;
  overflow: hidden;
  border-radius: 8px;
  background: var(--bg-secondary);
}
.mobile-learning-content__progress > div > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #ff791f;
}
.mobile-learning-content__progress p {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 7px 0 0;
  color: var(--muted);
  font-size: 11px;
}
.mobile-learning-content__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 56px;
  padding: 6px 16px;
  border-top: 1px solid var(--line);
  font-size: 13px;
}
.mobile-learning-content__footer > a {
  display: flex;
  align-items: center;
  gap: 2px;
  min-height: 40px;
  color: #ce5700;
  text-decoration: none;
}
.mobile-learning-center__refresh {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  margin-right: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
}
.mobile-learning-center__refresh:disabled {
  opacity: 0.5;
}
.mobile-learning-overview {
  padding: 8px 16px 24px;
  margin-bottom: 16px;
}
.mobile-learning-center__card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  color: var(--text);
  text-decoration: none;
}
.mobile-learning-center__card-heading h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
}
.mobile-learning-center__card-heading > .mobile-icon {
  color: var(--muted);
}
.mobile-learning-overview__metrics {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  row-gap: 24px;
  column-gap: 16px;
  margin: 7px 0 0;
}
.mobile-learning-overview__metrics dt {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}
.mobile-learning-overview__metrics dd {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 9px 0 0 25px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.mobile-learning-overview__metrics dd small {
  color: var(--muted);
  font-size: 10px;
  font-weight: 400;
}
.mobile-learning-center__status {
  padding: 24px 16px;
  color: var(--muted);
  text-align: center;
  font-size: 12px;
}
.mobile-learning-center__empty {
  padding: 28px 16px 20px;
  text-align: center;
  color: var(--muted);
}
.mobile-learning-center__empty h3 {
  margin: 12px 0 0;
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
}
.mobile-learning-center__empty p {
  margin: 8px 0 12px;
  font-size: 12px;
  line-height: 1.7;
}
.mobile-learning-center__empty a,
.mobile-learning-center__empty button,
.mobile-learning-center__error button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border: 0;
  border-radius: 20px;
  background: var(--brand-soft);
  color: #c2560e;
  font: inherit;
  font-size: 12px;
  text-decoration: none;
}
.mobile-learning-center__error {
  padding: 12px 0;
  font-size: 12px;
  color: var(--muted);
}
.mobile-learning-center a:focus-visible,
.mobile-learning-center button:focus-visible {
  outline: 2px solid #df6411;
  outline-offset: 3px;
}
</style>
