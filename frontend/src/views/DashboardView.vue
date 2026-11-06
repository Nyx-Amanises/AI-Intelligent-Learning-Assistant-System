<template>
  <div class="home">
    <MobileStudyHome
      :loading="continueLoading"
      :error="materialsState.error && !materials.length && !draft"
      :practice="draft"
      :material="continueMaterial"
      :wrong-count="wrongState.loaded && !wrongState.error ? wrongCount : null"
      :material-count="materialsState.loaded && !materialsState.error ? materialTotal : null"
      @reload="reloadAll"
    />
    <header class="home-head reveal">
      <div class="home-greeting">
        <p class="home-greeting__date">{{ todayLabel }}</p>
        <h1>
          {{ greeting }}，<span :title="displayName">{{ displayName }}</span>
        </h1>
        <p class="home-greeting__status">{{ statusLine }}</p>
      </div>
      <figure class="home-quote">
        <blockquote>{{ dailyQuote.text }}</blockquote>
        <figcaption>{{ dailyQuote.author }}《{{ dailyQuote.source }}》</figcaption>
      </figure>
    </header>

    <div class="home-grid">
      <DashboardContinueCard
        class="home-grid__tall home-continue reveal"
        style="--i: 1"
        :loading="continueLoading"
        :error="materialsState.error && !materials.length && !draft"
        :practice="draft"
        :material="continueMaterial"
        @reload="reloadAll"
      />
      <DashboardWeekCard
        class="home-grid__tall home-week reveal"
        style="--i: 2"
        :loading="weekState.loading"
        :error="weekState.error"
        :points="weekPoints"
        :accuracy-rate="week?.averageAccuracyRate || 0"
        :study-seconds="week?.totalStudySeconds || 0"
        :attempts="week?.totalQuestionAttempts || 0"
        @reload="loadWeek"
      />
      <DashboardRecentCard
        class="home-recent reveal"
        style="--i: 3"
        :loading="materialsState.loading"
        :error="materialsState.error"
        :materials="materials"
        :total="materialTotal"
        @reload="loadMaterials"
      />
      <DashboardShortcuts
        class="home-shortcuts reveal"
        style="--i: 4"
        :wrong-count="wrongCount"
        @open-assistant="emit('openAssistant')"
      />
    </div>

    <DashboardWeakPoints v-if="weakPoints.length" class="home-weak reveal" style="--i: 5" :points="weakPoints" />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { getLearningAnalyticsOverviewApi, type LearningAnalyticsOverviewPayload } from '@/api/modules/learningAnalytics'
import { getWrongQuestionPageApi } from '@/api/modules/wrongQuestion'
import { getKnowledgeMasteryOverviewApi, type KnowledgeMasteryItem } from '@/api/modules/knowledgeMastery'
import { getPracticeDetailApi } from '@/api/modules/practice'
import DashboardContinueCard, { type ContinuePractice } from '@/components/dashboard/DashboardContinueCard.vue'
import DashboardRecentCard from '@/components/dashboard/DashboardRecentCard.vue'
import DashboardShortcuts from '@/components/dashboard/DashboardShortcuts.vue'
import DashboardWeakPoints from '@/components/dashboard/DashboardWeakPoints.vue'
import DashboardWeekCard from '@/components/dashboard/DashboardWeekCard.vue'
import MobileStudyHome from '@/components/dashboard/MobileStudyHome.vue'
import { groupAnswers, type PracticeDetail } from '@/components/practice/practiceHelpers'
import { useDailyQuote } from '@/composables/useDailyQuote'
import { useUserStore } from '@/stores/user'
import { readActiveDraftSessionId, readDraftAnswers } from '@/utils/practiceDraft'

const emit = defineEmits<{ openAssistant: [] }>()
const userStore = useUserStore()
const dailyQuote = useDailyQuote()
let isActive = true

/** 每块数据单独加载、单独失败，一个接口出错不影响其他卡片。 */
const useSection = () => reactive({ loading: true, loaded: false, error: false })
const run = async <T,>(state: ReturnType<typeof useSection>, request: () => Promise<T>, apply: (value: T) => void) => {
  const token = userStore.token
  state.loading = true
  state.error = false
  try {
    const value = await request()
    if (!isActive || userStore.token !== token) return
    apply(value)
    state.loaded = true
  } catch {
    if (isActive && userStore.token === token) state.error = true
  } finally {
    if (isActive) state.loading = false
  }
}

const materials = ref<MaterialPageItem[]>([])
const materialTotal = ref(0)
const materialsState = useSection()
const loadMaterials = () =>
  run(
    materialsState,
    () => getMaterialPageApi({ current: 1, size: 4 }),
    (res) => {
      const page = res.data?.data
      if (!Array.isArray(page?.records)) throw new Error('Missing material records')
      materials.value = page.records
      materialTotal.value = Number(page.total) || page.records.length
    }
  )
const week = ref<LearningAnalyticsOverviewPayload | null>(null)
const weekState = useSection()
const loadWeek = () =>
  run(
    weekState,
    () => getLearningAnalyticsOverviewApi({ days: 7, trendLimit: 5 }),
    (res) => {
      week.value = res.data.data as LearningAnalyticsOverviewPayload
    }
  )
const weekPoints = computed(() =>
  (week.value?.practiceCountTrend?.points || []).map((point) => ({ startDate: point.startDate, count: point.count }))
)

const wrongCount = ref(0)
const wrongState = useSection()
const loadWrongCount = () =>
  run(
    wrongState,
    () => getWrongQuestionPageApi({ current: 1, size: 1 }),
    (res) => {
      wrongCount.value = Number(res.data?.data?.total) || 0
    }
  )

const weakPoints = ref<KnowledgeMasteryItem[]>([])
const weakState = useSection()
const loadWeakPoints = () =>
  run(
    weakState,
    () => getKnowledgeMasteryOverviewApi({ current: 1, size: 1 }),
    (res) => {
      weakPoints.value = (res.data?.data?.weakestPoints || []).slice(0, 3)
    }
  )

// 未交卷的练习只保存在本机草稿里（练习记录接口只返回已提交的练习）。
const draft = ref<ContinuePractice | null>(null)
const draftState = useSection()
const loadDraft = () =>
  run(
    draftState,
    async () => {
      const sessionId = readActiveDraftSessionId()
      if (!sessionId) return null
      try {
        return (await getPracticeDetailApi(sessionId)).data.data as PracticeDetail
      } catch {
        // 练习已被删除或无法读取时，首页不显示“接着上次”，也不算作加载失败。
        return null
      }
    },
    (detail) => {
      if (!detail?.sessionId || String(detail.sessionStatus || '').toUpperCase() === 'SUBMITTED') {
        draft.value = null
        return
      }
      const answers = readDraftAnswers(detail.sessionId)
      draft.value = {
        sessionId: detail.sessionId,
        sessionName: detail.sessionName,
        // 与练习页答题卡同样按题型分组编号，题号能对上。
        answered: groupAnswers(detail.answers)
          .flatMap((section) => section.items)
          .map((item) => Boolean(answers[item.questionId]?.trim()))
      }
    }
  )
const continueMaterial = computed(
  () => materials.value.find((item) => item.parseStatus === 'SUCCESS') || materials.value[0] || null
)
const continueLoading = computed(() => draftState.loading || (materialsState.loading && !materials.value.length))

/** 问候语下面的一句话：等相关数据都返回后再显示，避免内容来回跳动。 */
const statusLine = computed(() => {
  if (materialsState.loading || weekState.loading || wrongState.loading) return ''
  if (materialsState.loaded && !materialTotal.value) return '这里会汇总你的资料、练习和错题。'
  const parts: string[] = []
  if (weekState.loaded) {
    const count = weekPoints.value.reduce((sum, point) => sum + point.count, 0)
    parts.push(count ? `近 7 天练习了 ${count} 次` : '这几天还没有练习')
  }
  if (wrongState.loaded && wrongCount.value) parts.push(`错题本里有 ${wrongCount.value} 道题待复习`)
  return parts.length ? `${parts.join('，')}。` : '今天想从哪里开始？'
})

const now = ref(new Date())
const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '同学')
const greeting = computed(() => {
  const hour = Number(
    new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Shanghai', hour: 'numeric', hourCycle: 'h23' }).format(now.value)
  )
  if (hour < 5) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 13) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const todayLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  }).format(now.value)
)

const reloadAll = () => void Promise.all([loadMaterials(), loadDraft(), loadWeek(), loadWrongCount(), loadWeakPoints()])

let clockTimer: number | undefined
const tick = () => {
  now.value = new Date()
}
onMounted(() => {
  reloadAll()
  clockTimer = window.setInterval(tick, 60_000)
  window.addEventListener('focus', tick)
  document.addEventListener('visibilitychange', tick)
})
onUnmounted(() => {
  isActive = false
  window.clearInterval(clockTimer)
  window.removeEventListener('focus', tick)
  document.removeEventListener('visibilitychange', tick)
})
</script>
<style scoped>
.home {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}
.home-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px 40px;
  margin-bottom: 24px;
}
.home-greeting {
  flex: 1 1 360px;
  min-width: 0;
}
.home-greeting__date {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.home-greeting h1 {
  display: flex;
  max-width: 100%;
  margin: 4px 0 0;
  color: var(--text);
  font-size: 28px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}
.home-greeting h1 span {
  overflow: hidden;
  text-overflow: ellipsis;
}
.home-greeting__status {
  min-height: 24px;
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 15px;
  animation: zx-fade var(--duration) ease;
}
.home-quote {
  flex: 0 1 340px;
  margin: 0;
  padding-left: 16px;
  border-left: 2px solid color-mix(in srgb, var(--cinnabar) 45%, var(--line));
}
.home-quote blockquote {
  margin: 0;
  color: var(--text-secondary);
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1.8;
  letter-spacing: 0.04em;
  text-wrap: balance;
}
.home-quote figcaption {
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
}
.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}
.home-grid__tall {
  align-self: stretch;
}
.home-weak {
  margin-top: 32px;
}
@media (max-width: 1080px) {
  .home-grid {
    grid-template-columns: minmax(0, 1fr) 320px;
  }
}
@media (max-width: 900px) {
  .home-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .home-grid__tall {
    align-self: auto;
  }
}
@media (max-width: 760px) {
  .home-head,
  .home-grid,
  .home-weak {
    display: none;
  }
}
</style>
