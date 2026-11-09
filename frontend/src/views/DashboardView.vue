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
    <div class="home-grid">
      <DashboardWeekCard
        class="home-week reveal"
        style="--i: 1"
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
        style="--i: 2"
        :loading="materialsState.loading"
        :error="materialsState.error"
        :materials="materials"
        :total="materialTotal"
        @reload="loadMaterials"
      />
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { getLearningAnalyticsOverviewApi, type LearningAnalyticsOverviewPayload } from '@/api/modules/learningAnalytics'
import { getWrongQuestionPageApi } from '@/api/modules/wrongQuestion'
import { getPracticeDetailApi } from '@/api/modules/practice'
import type { ContinuePractice } from '@/components/dashboard/DashboardContinueCard.vue'
import DashboardRecentCard from '@/components/dashboard/DashboardRecentCard.vue'
import DashboardWeekCard from '@/components/dashboard/DashboardWeekCard.vue'
import MobileStudyHome from '@/components/dashboard/MobileStudyHome.vue'
import { groupAnswers, type PracticeDetail } from '@/components/practice/practiceHelpers'
import { useUserStore } from '@/stores/user'
import { readActiveDraftSessionId, readDraftAnswers } from '@/utils/practiceDraft'

defineEmits<{ openAssistant: [] }>()
const userStore = useUserStore()
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

const reloadAll = () => void Promise.all([loadMaterials(), loadDraft(), loadWeek(), loadWrongCount()])

onMounted(reloadAll)
onUnmounted(() => {
  isActive = false
})
</script>
<style scoped>
.home {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}
.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  align-items: stretch;
  gap: 20px;
  margin-top: 24px;
}
.home-grid > * {
  min-width: 0;
}
@media (min-width: 761px) and (max-width: 1040px) {
  .home-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 760px) {
  .home-grid {
    display: none;
  }
}
</style>
