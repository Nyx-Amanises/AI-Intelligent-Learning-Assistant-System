<template>
  <div class="study-home">
    <section class="study-hero" aria-labelledby="study-home-title">
      <div class="study-hero__main">
        <div class="study-hero__copy">
          <div class="study-hero__eyebrow"><span>我的学习</span><i aria-hidden="true" />{{ todayLabel }}</div>
          <h1 id="study-home-title">
            欢迎回来，<span :title="displayName">{{ displayName }}</span>
          </h1>
          <p class="study-hero__description">阅读资料、整理重点，或开始一组练习。</p>
          <div class="study-hero__actions">
            <RouterLink :to="learningDestination" class="study-button study-button--primary">
              <AppIcon :name="hasNoMaterials ? 'plus' : 'book-open'" :size="18" />
              {{ hasNoMaterials ? '添加学习资料' : '阅读资料' }}
              <AppIcon name="arrow-right" :size="17" />
            </RouterLink>
            <RouterLink to="/quiz" class="study-button study-button--secondary">开始练习</RouterLink>
          </div>
        </div>
        <div class="study-hero__art">
          <ReadingIllustration :paused="animationPaused" />
          <button
            type="button"
            class="study-hero__motion"
            :aria-label="animationPaused ? '播放插画动画' : '暂停插画动画'"
            :aria-pressed="animationPaused"
            :title="animationPaused ? '播放动画' : '暂停动画'"
            @click="animationPaused = !animationPaused"
          >
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
              <path v-if="animationPaused" d="m6 4 6 4-6 4Z" fill="currentColor" />
              <path v-else d="M6 4v8m4-8v8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>
      <div class="study-quote">
        <span class="study-quote__label">每日一句</span>
        <blockquote>{{ dailyQuote.text }}</blockquote>
        <cite
          >{{ dailyQuote.author }}<span>《{{ dailyQuote.source }}》</span></cite
        >
      </div>
    </section>
    <div class="study-home__body">
      <section class="study-library" aria-labelledby="recent-materials-title" :aria-busy="loading">
        <header class="study-section-heading">
          <div class="study-section-heading__title">
            <h2 id="recent-materials-title">最近资料</h2>
            <span v-if="materials.length">最近添加的 {{ materials.length }} 份</span>
          </div>
          <RouterLink to="/materials" class="study-text-link"
            >全部资料<AppIcon name="arrow-right" :size="16"
          /></RouterLink>
        </header>
        <div class="study-library__panel">
          <div v-if="loading && !materials.length" class="study-loading" role="status">
            <div v-for="n in 3" :key="n" class="study-loading__row" aria-hidden="true">
              <span />
              <div><i /><i /></div>
            </div>
            <span class="visually-hidden">正在加载学习资料</span>
          </div>
          <div v-else-if="loadError && !materials.length" class="study-empty" role="alert">
            <span class="study-empty__icon"><AppIcon name="folder" :size="25" /></span>
            <h3>资料加载失败</h3>
            <p>请检查网络连接后重试。</p>
            <button class="study-button study-button--secondary" type="button" @click="loadMaterials">
              重新加载<AppIcon name="refresh" :size="16" />
            </button>
          </div>
          <div v-else-if="!materials.length" class="study-empty">
            <span class="study-empty__icon"><AppIcon name="book-open" :size="26" /></span>
            <h3>还没有学习资料</h3>
            <p>上传讲义、文档，或直接添加文本。</p>
            <RouterLink :to="uploadDestination" class="study-button study-button--secondary"
              ><AppIcon name="plus" :size="16" />添加资料</RouterLink
            >
          </div>
          <ul v-else class="study-materials">
            <li v-for="(material, index) in materials" :key="material.id">
              <RouterLink :to="materialDestination(material.id)" class="study-material">
                <span class="study-document" :class="'study-document--' + (index % 3)" aria-hidden="true">
                  <svg viewBox="0 0 28 34" width="28" height="34" fill="none">
                    <path
                      d="M4 1.5h13l7 7V32H4Z"
                      fill="var(--panel)"
                      stroke="currentColor"
                      stroke-width="1.2"
                      stroke-linejoin="round"
                    />
                    <path
                      d="M17 1.5v7h7M9 16h10M9 21h10M9 26h6"
                      stroke="currentColor"
                      stroke-width="1.2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </span>
                <span class="study-material__copy">
                  <strong :title="material.title">{{ material.title }}</strong>
                  <span class="study-material__meta"
                    >{{ formatMaterialType(material.materialType, '文档') }}<i aria-hidden="true">·</i
                    >{{ material.totalCharacters ? formatCharacters(material.totalCharacters) : '学习资料'
                    }}<i aria-hidden="true">·</i>{{ formatShortDate(material.createdAt, '最近添加') }}</span
                  >
                </span>
                <span class="study-material__state" :class="{ 'is-error': material.parseStatus === 'FAILED' }">{{
                  formatParseStatus(material.parseStatus)
                }}</span>
                <AppIcon class="study-material__arrow" name="arrow-up-right" :size="18" />
              </RouterLink>
            </li>
          </ul>
          <div v-if="loadError && materials.length" class="study-inline-error" role="alert">
            刷新失败，当前显示上次加载的资料。<button type="button" @click="loadMaterials">重试</button>
          </div>
          <RouterLink v-if="materials.length" :to="uploadDestination" class="study-add-material"
            ><AppIcon name="plus" :size="16" />添加资料</RouterLink
          >
        </div>
      </section>
      <section class="study-tools" aria-labelledby="study-tools-title">
        <header class="study-section-heading"><h2 id="study-tools-title">常用工具</h2></header>
        <nav class="study-tools__panel" aria-label="学习工具">
          <RouterLink v-for="tool in studyTools" :key="tool.path" :to="tool.path" class="study-tool">
            <span class="study-tool__icon"><AppIcon :name="tool.icon" :size="20" /></span>
            <span class="study-tool__copy"
              ><strong>{{ tool.title }}</strong
              ><small>{{ tool.description }}</small></span
            >
            <AppIcon name="chevron-right" :size="16" />
          </RouterLink>
        </nav>
      </section>
    </div>
    <section class="study-assistant" aria-labelledby="study-assistant-title">
      <span class="study-assistant__icon" aria-hidden="true"><AppIcon name="assistant" :size="23" /></span>
      <div class="study-assistant__copy">
        <h2 id="study-assistant-title">学习助手</h2>
        <p>解释概念、查找资料，或帮你分析一道题。</p>
      </div>
      <button type="button" class="study-text-link" @click="emit('openAssistant')">
        打开助手<AppIcon name="arrow-up-right" :size="18" />
      </button>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import ReadingIllustration from '@/components/ReadingIllustration.vue'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { useDailyQuote } from '@/composables/useDailyQuote'
import { useUserStore } from '@/stores/user'
import { formatCharacters, formatShortDate } from '@/utils/format'
import { formatMaterialType } from '@/utils/labels'

const emit = defineEmits<{ openAssistant: [] }>()
const userStore = useUserStore()
const dailyQuote = useDailyQuote()
const materials = ref<MaterialPageItem[]>([])
const loading = ref(true)
const loadError = ref(false)
const hasLoaded = ref(false)
const animationPaused = ref(false)
const today = ref(new Date())
let dateTimer: number | undefined
let isActive = true
let requestPending = false
const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '同学')
const todayLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  }).format(today.value)
)
const uploadDestination = { path: '/materials', query: { action: 'upload' } }
const materialDestination = (id: number) => ({ path: '/materials', query: { materialId: String(id) } })
const hasNoMaterials = computed(() => hasLoaded.value && !loadError.value && !materials.value.length)
const learningDestination = computed(() => {
  const readable = materials.value.find((material) => material.parseStatus === 'SUCCESS')
  return readable ? materialDestination(readable.id) : hasNoMaterials.value ? uploadDestination : { path: '/materials' }
})
const studyTools = [
  { path: '/summary', icon: 'summary', title: '学习笔记', description: '查看资料摘要与重点' },
  { path: '/wrong-questions', icon: 'wrong', title: '错题复习', description: '重做错题，查看解析' },
  { path: '/analytics', icon: 'analytics', title: '学习统计', description: '查看练习与掌握情况' }
]
function formatParseStatus(status: string) {
  return (
    ({ SUCCESS: '可阅读', FAILED: '解析失败', PENDING: '待解析', PROCESSING: '解析中' } as Record<string, string>)[
      status?.toUpperCase()
    ] || '查看资料'
  )
}
async function loadMaterials() {
  if (requestPending) return
  requestPending = true
  loading.value = true
  loadError.value = false
  const requestToken = userStore.token
  try {
    const response = await getMaterialPageApi({ current: 1, size: 3 })
    if (!isActive || userStore.token !== requestToken) return
    if (!Array.isArray(response.data?.data?.records)) throw new Error('Missing material records')
    materials.value = response.data.data.records
    hasLoaded.value = true
  } catch {
    if (isActive && userStore.token === requestToken) loadError.value = true
  } finally {
    requestPending = false
    if (isActive) loading.value = false
  }
}
function refreshDate() {
  window.clearTimeout(dateTimer)
  today.value = new Date()
  // Update at midnight in Beijing even when the page stays open.
  const dayMs = 24 * 60 * 60 * 1000
  const elapsedToday = (today.value.getTime() + 8 * 60 * 60 * 1000) % dayMs
  dateTimer = window.setTimeout(refreshDate, dayMs - elapsedToday + 100)
}
onMounted(() => {
  void loadMaterials()
  refreshDate()
  window.addEventListener('focus', refreshDate)
  document.addEventListener('visibilitychange', refreshDate)
})
onUnmounted(() => {
  isActive = false
  window.clearTimeout(dateTimer)
  window.removeEventListener('focus', refreshDate)
  document.removeEventListener('visibilitychange', refreshDate)
})
</script>
<style scoped>
.study-home {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
}
.study-hero {
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--brand) 13%, var(--bg));
  border-radius: 18px;
  background: color-mix(in srgb, var(--brand-soft) 44%, var(--panel));
}
.study-hero__main {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  align-items: center;
  min-height: 302px;
  padding: 18px 48px;
  gap: 20px;
}
.study-hero__copy {
  min-width: 0;
  padding: 24px 0;
}
.study-hero__eyebrow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--muted);
  font-size: 12px;
}
.study-hero__eyebrow > span {
  color: var(--brand);
  font-weight: 500;
}
.study-hero__eyebrow > i {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--muted);
  opacity: 0.65;
}
.study-hero h1 {
  display: flex;
  align-items: baseline;
  max-width: 100%;
  margin: 19px 0 13px;
  color: var(--text);
  font-size: clamp(28px, 3vw, 39px);
  font-weight: 550;
  line-height: 1.45;
  letter-spacing: -0.04em;
  white-space: nowrap;
}
.study-hero h1 > span {
  overflow: hidden;
  text-overflow: ellipsis;
}
.study-hero__description {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.8;
}
.study-hero__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}
.study-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 43px;
  padding: 10px 18px;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  cursor: pointer;
  transition:
    background 0.18s,
    border-color 0.18s;
}
.study-button--primary {
  color: #fff;
  background: var(--brand);
  border-color: var(--brand);
}
.study-button--primary:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}
.study-button--primary > :last-child {
  margin-left: 12px;
}
.study-button--secondary {
  color: var(--text-secondary);
  background: var(--panel);
  border-color: color-mix(in srgb, var(--brand) 17%, var(--panel));
}
.study-button--secondary:hover {
  color: var(--brand);
  border-color: var(--brand);
}
.study-hero__art {
  position: relative;
  width: 100%;
  max-width: 410px;
  justify-self: end;
}
.study-hero__motion {
  position: absolute;
  bottom: 5px;
  right: -22px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--muted);
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--brand) 15%, transparent);
  border-radius: 50%;
  cursor: pointer;
}
.study-hero__motion:hover {
  color: var(--brand);
  background: var(--panel);
}
.study-quote {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  column-gap: 20px;
  row-gap: 6px;
  min-height: 63px;
  margin: 0 32px;
  padding: 16px;
  border-top: 1px solid color-mix(in srgb, var(--brand) 11%, transparent);
  font-size: 12px;
}
.study-quote__label {
  flex: 0 0 auto;
  color: var(--brand);
}
.study-quote blockquote {
  margin: 0;
  color: var(--text-secondary);
  font-family: 'Noto Serif SC', 'Songti SC', 'SimSun', serif;
  font-size: 14px;
  letter-spacing: 0.04em;
}
.study-quote cite {
  margin-left: auto;
  color: var(--muted);
  font-size: 11px;
  font-style: normal;
}
.study-quote cite span {
  margin-left: 6px;
}
.study-home__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 28px;
  margin-top: 34px;
}
.study-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 32px;
  margin-bottom: 17px;
}
.study-section-heading__title {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  column-gap: 14px;
}
.study-section-heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.02em;
}
.study-section-heading__title > span {
  color: var(--muted);
  font-size: 11px;
}
.study-text-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 32px;
  padding: 0;
  color: var(--brand);
  background: transparent;
  border: 0;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
}
.study-text-link:hover {
  color: var(--brand-hover);
  text-decoration: underline;
  text-underline-offset: 4px;
}
.study-library,
.study-tools {
  min-width: 0;
}
.study-library__panel,
.study-tools__panel {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel);
}
.study-materials {
  list-style: none;
  margin: 0;
  padding: 0 22px;
}
.study-materials > li + li {
  border-top: 1px solid var(--line);
}
.study-material {
  display: flex;
  align-items: center;
  gap: 17px;
  min-height: 88px;
  padding: 17px 0;
}
.study-document {
  display: grid;
  place-items: center;
  flex: 0 0 47px;
  height: 52px;
  border-radius: 7px;
  color: #708d78;
  background: #edf2ed;
}
.study-document--1 {
  color: #a49171;
  background: #f5f1e9;
}
.study-document--2 {
  color: #7d8c9e;
  background: #eef1f5;
}
.study-material__copy {
  flex: 1;
  min-width: 0;
}
.study-material__copy > strong {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color 0.18s;
}
.study-material:hover .study-material__copy > strong {
  color: var(--brand);
}
.study-material__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  column-gap: 7px;
  margin-top: 7px;
  color: var(--muted);
  font-size: 11px;
}
.study-material__meta i {
  color: var(--line);
  font-style: normal;
}
.study-material__state {
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}
.study-material__arrow {
  flex-shrink: 0;
  color: var(--muted);
}
.study-material__state.is-error {
  color: var(--red);
}
.study-add-material {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 42px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  background: color-mix(in srgb, var(--bg) 48%, var(--panel));
  font-size: 12px;
}
.study-add-material:hover {
  color: var(--brand);
  background: var(--brand-light);
}
.study-tool {
  display: flex;
  align-items: center;
  gap: 13px;
  min-height: 103px;
  margin: 0 22px;
  padding: 20px 0;
}
.study-tool + .study-tool {
  border-top: 1px solid var(--line);
}
.study-tool__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--brand);
  background: var(--bg);
  transition: background 0.18s;
}
.study-tool__copy {
  flex: 1;
  min-width: 0;
}
.study-tool__copy strong {
  display: block;
  font-size: 13px;
  font-weight: 500;
}
.study-tool__copy small {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
}
.study-tool > .app-icon {
  color: var(--muted);
}
.study-tool:hover .study-tool__icon {
  background: var(--brand-soft);
}
.study-tool:hover strong {
  color: var(--brand);
}
.study-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 306px;
  padding: 28px 20px;
  text-align: center;
}
.study-empty__icon {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  margin-bottom: 14px;
  color: var(--brand);
  background: var(--brand-light);
  border-radius: 14px;
}
.study-empty h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
}
.study-empty p {
  margin: 7px 0 20px;
  color: var(--muted);
  font-size: 12px;
}
.study-loading {
  min-height: 306px;
  padding: 0 22px;
}
.study-loading__row {
  display: flex;
  align-items: center;
  gap: 18px;
  height: 88px;
  border-bottom: 1px solid var(--line);
}
.study-loading__row > span {
  width: 47px;
  height: 52px;
  border-radius: 7px;
  background: var(--bg-secondary);
}
.study-loading__row > div {
  flex: 1;
}
.study-loading__row i {
  display: block;
  width: 60%;
  height: 12px;
  border-radius: 3px;
  background: var(--bg-secondary);
}
.study-loading__row i + i {
  width: 40%;
  height: 8px;
  margin-top: 12px;
}
.study-inline-error {
  padding: 12px 22px;
  color: var(--red);
  font-size: 12px;
}
.study-inline-error button {
  padding: 4px 8px;
  color: inherit;
  border: 0;
  background: transparent;
  text-decoration: underline;
  cursor: pointer;
}
.study-assistant {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-top: 24px;
  padding: 20px 25px;
  background: color-mix(in srgb, var(--brand-soft) 35%, var(--bg));
  border: 1px solid color-mix(in srgb, var(--brand) 11%, var(--bg));
  border-radius: 12px;
}
.study-assistant__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: var(--brand);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 12px;
}
.study-assistant__copy {
  flex: 1;
  min-width: 0;
}
.study-assistant h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
}
.study-assistant p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
@media (max-width: 1100px) {
  .study-hero__main {
    padding-inline: 32px;
  }
  .study-hero h1 {
    font-size: 32px;
  }
  .study-hero__art {
    max-width: 340px;
  }
  .study-quote {
    margin-inline: 16px;
  }
  .study-home__body {
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 22px;
  }
  .study-section-heading__title > span {
    display: none;
  }
  .study-material {
    display: grid;
    grid-template-columns: 47px minmax(0, 1fr) 18px;
    gap: 5px 14px;
  }
  .study-document {
    grid-row: 1 / 3;
  }
  .study-material__copy {
    grid-column: 2;
  }
  .study-material__state {
    grid-column: 2;
    grid-row: 2;
  }
  .study-material__arrow {
    grid-column: 3;
    grid-row: 1 / 3;
  }
}
@media (max-width: 760px) {
  .study-hero {
    border-radius: 14px;
  }
  .study-hero__main {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
    padding: 26px 24px 0;
  }
  .study-hero__copy {
    padding: 0;
  }
  .study-hero__eyebrow {
    font-size: 11px;
    gap: 9px;
  }
  .study-hero h1 {
    margin-top: 16px;
    font-size: clamp(24px, 6vw, 32px);
  }
  .study-hero__description {
    font-size: 12px;
  }
  .study-hero__actions {
    margin-top: 22px;
    gap: 9px;
  }
  .study-button {
    min-height: 44px;
    padding-inline: 14px;
    font-size: 12px;
  }
  .study-button--primary > :last-child {
    margin-left: 2px;
  }
  .study-hero__art {
    width: min(100%, 280px);
    margin: 0 auto;
    justify-self: center;
  }
  .study-hero__motion {
    right: -6px;
    bottom: 18px;
    width: 32px;
    height: 32px;
  }
  .study-quote {
    gap: 7px 12px;
    margin-inline: 20px;
    padding: 15px 0 18px;
  }
  .study-quote blockquote {
    flex: 1 1 calc(100% - 70px);
    font-size: 12px;
  }
  .study-quote cite {
    width: 100%;
    text-align: right;
    font-size: 10px;
  }
  .study-home__body {
    grid-template-columns: minmax(0, 1fr);
    gap: 26px;
    margin-top: 26px;
  }
  .study-section-heading {
    margin-bottom: 12px;
  }
  .study-section-heading h2 {
    font-size: 16px;
  }
  .study-materials {
    padding-inline: 16px;
  }
  .study-material {
    grid-template-columns: 41px minmax(0, 1fr) 18px;
    gap: 5px 12px;
    min-height: 86px;
  }
  .study-material__copy > strong {
    font-size: 13px;
  }
  .study-material__meta {
    gap: 5px;
    font-size: 11px;
  }
  .study-document {
    width: 41px;
    height: 48px;
    flex-basis: 41px;
  }
  .study-add-material {
    min-height: 44px;
  }
  .study-tool {
    min-height: 84px;
    margin-inline: 18px;
    padding-block: 16px;
  }
  .study-empty,
  .study-loading {
    min-height: 280px;
  }
  .study-assistant {
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 22px;
    padding: 18px;
  }
  .study-assistant__icon {
    display: none;
  }
  .study-assistant__copy {
    flex-basis: 100%;
  }
  .study-assistant > button {
    min-height: 36px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .study-hero__motion {
    display: none;
  }
}
</style>
