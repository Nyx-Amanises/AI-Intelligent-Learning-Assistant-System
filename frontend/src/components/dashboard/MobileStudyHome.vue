<template>
  <section class="mobile-study-home" aria-labelledby="mobile-study-title">
    <StudyLandscape class="mobile-study-home__scene" />
    <div class="mobile-study-home__headline">
      <h1 id="mobile-study-title">学而有序</h1>
      <p v-if="practice">接着上次，继续向前。</p>
    </div>
    <p v-if="error" class="mobile-study-home__notice" role="alert">资料暂时没有加载出来，点学习重新加载</p>
    <div class="mobile-study-home__actions">
      <button type="button" :disabled="loading" class="mobile-study-home__action" @click="startLearning">
        <strong>{{ practice ? '继续学习' : '学习' }}</strong>
        <span v-if="loading" class="mobile-study-home__count">准备中…</span>
        <span v-else-if="practice" class="mobile-study-home__count"
          >{{ answeredCount }} / {{ practice.answered.length }} <small>已答</small></span
        >
        <span v-else-if="error" class="mobile-study-home__count">重新加载</span>
        <span v-else-if="!material" class="mobile-study-home__count mobile-study-home__start">发现学习资料</span>
        <span v-else class="mobile-study-home__count">{{ materialCount ?? '—' }} <small>份资料</small></span>
      </button>
      <RouterLink :to="{ path: '/wrong-questions', query: { from: 'home' } }" class="mobile-study-home__action">
        <strong>复习</strong>
        <span class="mobile-study-home__count">{{ wrongCount ?? '—' }} <small>道错题</small></span>
      </RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import type { MaterialPageItem } from '@/api/modules/material'
import type { ContinuePractice } from './DashboardContinueCard.vue'
import StudyLandscape from './StudyLandscape.vue'
import { materialRoute, practiceRoute } from './routes'

const props = defineProps<{
  loading: boolean
  error: boolean
  practice: ContinuePractice | null
  material: MaterialPageItem | null
  materialCount: number | null
  wrongCount: number | null
}>()
const emit = defineEmits<{ reload: [] }>()
const router = useRouter()
const answeredCount = computed(() => props.practice?.answered.filter(Boolean).length || 0)
const startLearning = () => {
  if (props.loading) return
  if (!props.practice && !props.material && props.error) return emit('reload')
  const target = props.practice
    ? practiceRoute(props.practice.sessionId)
    : props.material
      ? materialRoute(props.material.id)
      : { path: '/resources', query: {} }
  void router.push({ ...target, query: { ...target.query, from: 'home' } })
}
</script>

<style scoped>
.mobile-study-home {
  display: none;
}
@media (min-width: 761px) {
  .mobile-study-home {
    position: relative;
    display: block;
    height: clamp(410px, 39vw, 480px);
    overflow: hidden;
    border: 1px solid color-mix(in srgb, var(--line) 55%, transparent);
    border-radius: 24px;
    color: #232a2b;
    isolation: isolate;
  }
  .mobile-study-home__scene {
    position: absolute;
    inset: 0;
    z-index: -1;
  }
  .mobile-study-home__headline {
    position: absolute;
    inset: 15% 24px auto;
    text-align: center;
  }
  .mobile-study-home__headline h1 {
    margin: 0;
    font-size: clamp(38px, 3.6vw, 48px);
    font-weight: 650;
    line-height: 1.4;
    letter-spacing: 0.06em;
  }
  .mobile-study-home__headline p {
    margin: 10px 0 0;
    color: #53686b;
    font-size: 13px;
    letter-spacing: 0.04em;
  }
  .mobile-study-home__actions {
    position: absolute;
    bottom: 32px;
    left: 50%;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    width: min(480px, calc(100% - 64px));
    transform: translateX(-50%);
  }
  .mobile-study-home__action {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 3px;
    min-width: 0;
    min-height: 86px;
    padding: 14px 24px;
    border: 1px solid rgb(255 255 255 / 45%);
    border-radius: 14px;
    background: rgb(249 253 251 / 62%);
    color: inherit;
    font: inherit;
    text-align: left;
    text-decoration: none;
    cursor: pointer;
    backdrop-filter: blur(12px);
    transition:
      background-color 0.2s ease,
      transform 0.2s ease;
  }
  .mobile-study-home__action strong {
    font-size: 22px;
    font-weight: 650;
    line-height: 1.4;
  }
  .mobile-study-home__count {
    color: var(--mobile-accent, var(--brand));
    font-size: 21px;
    line-height: 1.2;
    font-variant-numeric: tabular-nums;
  }
  .mobile-study-home__count small {
    font-size: 12px;
  }
  .mobile-study-home__count.mobile-study-home__start {
    font-size: 14px;
    line-height: 1.6;
  }
  .mobile-study-home__action:enabled:hover,
  a.mobile-study-home__action:hover {
    background: rgb(255 255 255 / 82%);
    transform: translateY(-2px);
  }
  .mobile-study-home__action:focus-visible {
    outline: 2px solid var(--mobile-accent, var(--brand));
    outline-offset: 4px;
  }
  .mobile-study-home__action:active {
    transform: scale(0.98);
  }
  .mobile-study-home__action:disabled {
    opacity: 0.7;
    cursor: wait;
  }
  .mobile-study-home__notice {
    position: absolute;
    right: 24px;
    bottom: 132px;
    left: 24px;
    margin: 0;
    color: #526769;
    font-size: 12px;
    text-align: center;
  }
}
@media (max-width: 760px) {
  .mobile-study-home {
    position: relative;
    display: block;
    min-height: 100svh;
    margin-top: calc(-1 * var(--shell-header-height, 56px));
    overflow: hidden;
    color: #232a2b;
    isolation: isolate;
  }
  .mobile-study-home__scene {
    position: absolute;
    inset: 0;
    z-index: -1;
  }
  .mobile-study-home__headline {
    position: absolute;
    inset: 27.5% 20px auto;
    text-align: center;
  }
  .mobile-study-home__headline h1 {
    margin: 0;
    font-size: clamp(30px, 8.7vw, 38px);
    font-weight: 650;
    line-height: 1.4;
    letter-spacing: 0.04em;
  }
  .mobile-study-home__headline p {
    margin: 12px 0 0;
    color: #53686b;
    font-size: 12px;
    letter-spacing: 0.05em;
  }
  .mobile-study-home__count.mobile-study-home__start {
    font-size: 13px;
    line-height: 1.6;
  }
  .mobile-study-home__actions {
    position: absolute;
    right: 16px;
    bottom: calc(104px + env(safe-area-inset-bottom));
    left: 16px;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }
  .mobile-study-home__action {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 1px;
    min-width: 0;
    min-height: 76px;
    padding: 11px 16px;
    border: 0;
    border-radius: 9px;
    background: rgb(249 253 251 / 48%);
    color: inherit;
    font: inherit;
    text-align: left;
    cursor: pointer;
    backdrop-filter: blur(12px);
    transition:
      background-color 0.2s ease,
      transform 0.2s ease;
  }
  .mobile-study-home__action strong {
    font-size: 21px;
    font-weight: 650;
    line-height: 1.4;
  }
  .mobile-study-home__count {
    color: var(--mobile-accent);
    font-size: 21px;
    line-height: 1.2;
    font-variant-numeric: tabular-nums;
  }
  .mobile-study-home__count small {
    font-size: 11px;
  }
  .mobile-study-home__action:active {
    transform: scale(0.98);
    background: rgb(255 255 255 / 65%);
  }
  .mobile-study-home__action:disabled {
    opacity: 0.7;
    cursor: wait;
  }
  .mobile-study-home__notice {
    position: absolute;
    bottom: calc(190px + env(safe-area-inset-bottom));
    right: 20px;
    left: 20px;
    margin: 0;
    color: #526769;
    font-size: 12px;
    text-align: center;
  }
}
@media (min-width: 761px) and (prefers-reduced-motion: reduce) {
  .mobile-study-home__action {
    transition: none;
  }
}
</style>
