<template>
  <section class="mobile-content-hub" aria-label="我的内容">
    <div class="content-feature-rail" aria-label="学习内容快捷入口">
      <section class="content-feature content-feature--reading">
        <h2><MobileIcon name="book" :size="21" />阅读资料</h2>
        <div class="content-feature__tiles">
          <RouterLink :to="libraryRoute">
            <strong>资料库</strong>
            <span>{{ materialCountText }}</span>
            <MobileIcon name="next" :size="17" />
          </RouterLink>
          <RouterLink to="/summary">
            <strong>学习总结</strong>
            <span>{{ countText(counts.summaries, '条') }}</span>
            <MobileIcon name="next" :size="17" />
          </RouterLink>
        </div>
      </section>
      <section class="content-feature content-feature--practice">
        <h2><MobileIcon name="pencil" :size="21" />练习复习</h2>
        <div class="content-feature__tiles">
          <RouterLink to="/practice">
            <strong>练习记录</strong>
            <span>{{ countText(counts.practices, '次') }}</span>
            <MobileIcon name="next" :size="17" />
          </RouterLink>
          <RouterLink to="/wrong-questions">
            <strong>错题本</strong>
            <span>{{ countText(counts.wrong, '道') }}</span>
            <MobileIcon name="next" :size="17" />
          </RouterLink>
        </div>
      </section>
    </div>

    <div class="content-group">
      <RouterLink to="/resources" class="content-row">
        <span class="content-row__icon content-row__icon--blue"><MobileIcon name="search" :size="23" /></span>
        <span class="content-row__label">找资料</span>
        <MobileIcon class="content-row__arrow" name="next" :size="16" />
      </RouterLink>
      <RouterLink :to="libraryRoute" class="content-row">
        <span class="content-row__icon content-row__icon--mint"><MobileIcon name="book" :size="23" /></span>
        <span class="content-row__label">所有资料</span>
        <span class="content-row__value">{{ materialCountText }}</span>
        <MobileIcon class="content-row__arrow" name="next" :size="16" />
      </RouterLink>
    </div>
    <div class="content-group">
      <RouterLink to="/practice" class="content-row">
        <span class="content-row__icon content-row__icon--gold"><MobileIcon name="calendar" :size="23" /></span>
        <span class="content-row__label">练习记录</span>
        <span class="content-row__value">{{ countText(counts.practices, '次') }}</span>
        <MobileIcon class="content-row__arrow" name="next" :size="16" />
      </RouterLink>
      <RouterLink to="/wrong-questions" class="content-row">
        <span class="content-row__icon content-row__icon--gold"><MobileIcon name="check" :size="23" /></span>
        <span class="content-row__label">错题本</span>
        <span class="content-row__value">{{ countText(counts.wrong, '道') }}</span>
        <MobileIcon class="content-row__arrow" name="next" :size="16" />
      </RouterLink>
    </div>
    <div class="content-group">
      <RouterLink to="/notes" class="content-row">
        <span class="content-row__icon content-row__icon--blue"><MobileIcon name="pencil" :size="23" /></span>
        <span class="content-row__label">我的笔记</span>
        <MobileIcon class="content-row__arrow" name="next" :size="16" />
      </RouterLink>
      <RouterLink to="/summary" class="content-row">
        <span class="content-row__icon content-row__icon--blue"><MobileIcon name="pencil" :size="23" /></span>
        <span class="content-row__label">学习总结</span>
        <span class="content-row__value">{{ countText(counts.summaries, '条') }}</span>
        <MobileIcon class="content-row__arrow" name="next" :size="16" />
      </RouterLink>
      <RouterLink to="/mastery" class="content-row">
        <span class="content-row__icon content-row__icon--blue"><MobileIcon name="trend" :size="23" /></span>
        <span class="content-row__label">知识掌握</span>
        <span class="content-row__value">{{ countText(counts.mastery, '项') }}</span>
        <MobileIcon class="content-row__arrow" name="next" :size="16" />
      </RouterLink>
    </div>
    <p v-if="countError" class="content-count-error" role="status">
      部分数量暂未加载。<button type="button" :disabled="countsLoading" @click="loadCounts">重试</button>
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MobileIcon from '@/components/MobileIcon.vue'
import { getAllSummaryHistoryApi } from '@/api/modules/ai'
import { getPracticePageApi } from '@/api/modules/practice'
import { getWrongQuestionPageApi } from '@/api/modules/wrongQuestion'
import { getKnowledgeMasteryOverviewApi } from '@/api/modules/knowledgeMastery'
import { getMaterialPageApi } from '@/api/modules/material'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const libraryRoute = { path: '/materials', query: { view: 'library' } }
const counts = reactive<{
  materials: number | null
  summaries: number | null
  practices: number | null
  wrong: number | null
  mastery: number | null
}>({
  materials: null,
  summaries: null,
  practices: null,
  wrong: null,
  mastery: null
})
const countsLoading = ref(false)
const countError = ref(false)
let active = true
const countText = (value: number | null, unit: string) => (value === null ? '—' : `${value} ${unit}`)
const materialCountText = computed(() => countText(counts.materials, '份'))
const readCount = (value: unknown): number => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) throw new Error('Missing count')
  return value
}
async function loadCounts() {
  if (countsLoading.value) return
  countsLoading.value = true
  countError.value = false
  const token = userStore.token
  const results = await Promise.allSettled([
    getMaterialPageApi({ current: 1, size: 1 }).then((res) => readCount(res.data.data?.total)),
    getAllSummaryHistoryApi().then((res) => {
      if (!Array.isArray(res.data.data)) throw new Error('Missing summary records')
      return res.data.data.length
    }),
    getPracticePageApi({ current: 1, size: 1 }).then((res) => readCount(res.data.data?.total)),
    getWrongQuestionPageApi({ current: 1, size: 1 }).then((res) => readCount(res.data.data?.total)),
    getKnowledgeMasteryOverviewApi({ current: 1, size: 1 }).then((res) =>
      readCount(res.data.data?.totalKnowledgePoints)
    )
  ])
  countsLoading.value = false
  if (!active || userStore.token !== token) return
  const keys = ['materials', 'summaries', 'practices', 'wrong', 'mastery'] as const
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') counts[keys[index]] = result.value
    else {
      counts[keys[index]] = null
      countError.value = true
    }
  })
}
onMounted(loadCounts)
onBeforeUnmount(() => {
  active = false
})
</script>

<style scoped>
.mobile-content-hub {
  display: grid;
  gap: 12px;
  padding: 8px 0 24px;
}
.content-feature-rail {
  display: flex;
  gap: 16px;
  min-width: 0;
  margin: 0 0 8px;
  padding: 0 0 14px;
  overflow-x: auto;
  scrollbar-width: none;
  scroll-snap-type: x proximity;
}
.content-feature-rail::-webkit-scrollbar {
  display: none;
}
.content-feature {
  flex: 0 0 226px;
  min-width: 0;
  padding: 16px;
  border: 1px solid rgb(255 255 255 / 65%);
  border-radius: 13px;
  box-shadow: 0 8px 13px rgb(40 43 50 / 12%);
  scroll-snap-align: start;
}
.content-feature--reading {
  --feature-color: #ad691d;
  background: linear-gradient(160deg, #fff0d9, #fae2d5);
}
.content-feature--practice {
  --feature-color: #238697;
  background: linear-gradient(160deg, #ddffff, #c5edf1);
}
.content-feature h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  color: var(--feature-color);
  font-size: 14px;
  font-weight: 650;
}
.content-feature__tiles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.content-feature__tiles a {
  position: relative;
  min-height: 80px;
  padding: 12px;
  border: 1px solid rgb(255 255 255 / 80%);
  border-radius: 11px;
  background: rgb(255 255 255 / 60%);
}
.content-feature__tiles strong,
.content-feature__tiles span {
  display: block;
}
.content-feature__tiles strong {
  font-size: 14px;
  font-weight: 500;
}
.content-feature__tiles span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}
.content-feature__tiles .mobile-icon {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 3px;
  width: 23px;
  height: 23px;
  border-radius: 50%;
  background: rgb(255 255 255 / 75%);
  color: var(--feature-color);
}
.content-group {
  padding: 2px 16px;
  border-radius: 13px;
  background: var(--panel);
}
.content-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 58px;
}
.content-row__icon {
  display: grid;
  flex: 0 0 32px;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}
.content-row__icon--mint {
  background: #edfaf6;
  color: #288f7c;
}
.content-row__icon--gold {
  background: #fcf9e9;
  color: #b88511;
}
.content-row__icon--blue {
  background: #edf3ff;
  color: #4c81d5;
}
.content-row__label {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
}
.content-row__value {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.content-row__arrow {
  flex-shrink: 0;
  color: #999;
}
.content-count-error {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}
.content-count-error button {
  min-height: 44px;
  border: 0;
  background: none;
  color: var(--brand);
  font: inherit;
}
@media (max-width: 360px) {
  .content-feature {
    flex-basis: 226px;
    padding: 14px;
  }
  .content-group {
    padding-inline: 14px;
  }
  .content-row {
    gap: 8px;
  }
}
@media (min-width: 761px) {
  .mobile-content-hub {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 20px;
    padding: 0 0 24px;
  }
  .content-feature-rail {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
    margin: 0;
    padding: 0;
    overflow: visible;
    scroll-snap-type: none;
  }
  .content-feature {
    padding: 20px;
    border: 1px solid var(--line);
    border-radius: 18px;
    background: var(--panel);
    box-shadow: none;
  }
  .content-feature h2 {
    margin-bottom: 16px;
    font-size: 16px;
  }
  .content-feature__tiles {
    gap: 12px;
  }
  .content-feature__tiles a {
    min-height: 94px;
    padding: 16px;
    border: 0;
    border-radius: 14px;
    background: color-mix(in srgb, var(--feature-color) 7%, var(--panel));
  }
  .content-feature__tiles a:hover {
    background: color-mix(in srgb, var(--feature-color) 12%, var(--panel));
  }
  .content-feature__tiles strong {
    font-size: 15px;
  }
  .content-feature__tiles span {
    margin-top: 8px;
    font-size: 13px;
  }
  .content-group {
    padding: 4px 18px;
    border: 1px solid var(--line);
    border-radius: 18px;
  }
  .content-row {
    min-height: 68px;
  }
  .content-row + .content-row {
    border-top: 1px solid var(--line);
  }
  .content-row:hover .content-row__label {
    color: var(--brand);
  }
  .mobile-content-hub a:focus-visible {
    outline: 2px solid var(--brand);
    outline-offset: 4px;
    border-radius: 12px;
  }
  .content-count-error {
    grid-column: 1 / -1;
  }
}
@media (min-width: 761px) and (max-width: 980px) {
  .mobile-content-hub {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
