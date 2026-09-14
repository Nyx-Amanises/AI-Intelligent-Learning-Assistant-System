<template>
  <div class="daily-home">
    <section class="daily-scene" :class="{ 'daily-scene--long-quote': isLongQuote }" aria-labelledby="daily-quote-title">
      <picture class="daily-scene__picture" aria-hidden="true">
        <source media="(max-width: 760px)" :srcset="lakeMobile">
        <img :src="lakeImage" alt="" width="2200" height="1250" fetchpriority="high" decoding="async">
      </picture>
      <div class="daily-scene__veil" aria-hidden="true" />
      <div class="daily-scene__inner">
        <div class="daily-scene__opening">
          <span class="daily-scene__eyebrow"><i aria-hidden="true" />每日一句</span>
          <span class="daily-scene__welcome">{{ displayName }}，欢迎回来</span>
        </div>
        <div class="daily-scene__quote">
          <h1 id="daily-quote-title" :class="{ 'daily-scene__quote--long': isLongQuote }">
            <span v-for="(line, index) in quoteLines" :key="index">{{ line }}</span>
          </h1>
          <p class="daily-scene__source">{{ dailyQuote.author }}<span aria-hidden="true"> / </span><cite>《{{ dailyQuote.source }}》</cite></p>
        </div>
        <div class="daily-scene__bottom">
          <div class="daily-actions" aria-label="开始今天的学习">
            <RouterLink :to="learningDestination" class="daily-action daily-action--learn">
              <span><strong>{{ materials.length ? '继续学习' : '开始学习' }}</strong><small>{{ learningHint }}</small></span>
              <span class="daily-action__arrow"><AppIcon name="arrow-up-right" :size="22" /></span>
            </RouterLink>
            <RouterLink to="/wrong-questions" class="daily-action daily-action--review">
              <span><strong>温习一下</strong><small>让理解，再深一点</small></span>
              <span class="daily-action__arrow"><AppIcon name="arrow-up-right" :size="22" /></span>
            </RouterLink>
          </div>
          <a class="daily-scene__scroll" href="#my-reading"><span>我的学习内容</span><AppIcon name="chevron-down" :size="18" /></a>
        </div>
      </div>
    </section>

    <div id="my-reading" class="daily-content">
      <div class="daily-reading-layout">
        <section class="daily-reading" aria-labelledby="daily-reading-title" :aria-busy="loading">
          <header class="daily-section-heading">
            <div><span class="daily-section-index">01 / 阅读</span><h2 id="daily-reading-title">从这里，继续。</h2></div>
            <RouterLink to="/materials" class="daily-text-link">全部资料<AppIcon name="arrow-up-right" :size="17" /></RouterLink>
          </header>
          <div v-if="loading && !materials.length" class="daily-loading" role="status">
            <div v-for="n in 3" :key="n" class="daily-loading__row" aria-hidden="true"><span /><div><i /><i /></div></div>
            <span class="visually-hidden">正在加载学习资料</span>
          </div>
          <div v-else-if="loadError && !materials.length" class="daily-reading-state" role="alert">
            <AppIcon name="folder" :size="30" /><h3>暂时没能找到你的资料</h3><p>检查网络后，再试一次。</p>
            <button class="daily-text-link" type="button" @click="loadMaterials">重新加载<AppIcon name="refresh" :size="17" /></button>
          </div>
          <div v-else-if="!materials.length" class="daily-first-reading">
            <div class="daily-first-reading__book" aria-hidden="true"><span>知序</span><i /><small>我的第一份资料</small></div>
            <div class="daily-first-reading__copy"><h3>一本讲义，一篇文章，<br>都可以是起点。</h3><p>放入你正在读的内容，开始这一程学习。</p><RouterLink :to="uploadDestination" class="daily-text-link">添加第一份资料<AppIcon name="plus" :size="18" /></RouterLink></div>
          </div>
          <ol v-else class="daily-reading-list">
            <li v-for="(material, index) in materials" :key="material.id">
              <RouterLink :to="materialDestination(material.id)" class="daily-reading-row">
                <span class="daily-book" :class="'daily-book--' + index % 4" aria-hidden="true"><i /><small>{{ formatMaterialType(material.materialType) }}</small><b>{{ String(index + 1).padStart(2, '0') }}</b></span>
                <span class="daily-reading-row__copy"><strong>{{ material.title }}</strong><small>{{ formatMaterialType(material.materialType) }}<i aria-hidden="true">·</i>{{ material.totalCharacters ? material.totalCharacters.toLocaleString() + ' 字' : '学习资料' }}<i aria-hidden="true">·</i>{{ formatMaterialDate(material.createdAt) }}</small></span>
                <span class="daily-reading-row__state" :class="{ 'is-error': material.parseStatus === 'FAILED' }">{{ formatParseStatus(material.parseStatus) }}</span>
                <AppIcon class="daily-reading-row__arrow" name="arrow-up-right" :size="20" />
              </RouterLink>
            </li>
          </ol>
          <div v-if="loadError && materials.length" class="daily-inline-error" role="alert">刷新暂未成功，正在显示上次加载的资料。<button type="button" @click="loadMaterials">重试</button></div>
        </section>

        <section class="daily-notes" aria-labelledby="daily-notes-title">
          <RouterLink to="/summary" class="daily-notes__link">
            <div class="daily-notes__photo"><img :src="forestImage" alt="阳光落在山间的树林里" width="800" height="980" loading="lazy" decoding="async"><span class="daily-notes__badge"><AppIcon name="arrow-up-right" :size="23" /></span></div>
            <div class="daily-notes__copy"><span class="daily-section-index">02 / 思考</span><h2 id="daily-notes-title"><span>读过之后，</span><span>留点思考。</span></h2><p>把零散的知识，整理成自己的学习笔记。</p><span class="daily-text-link">我的笔记<AppIcon name="arrow-right" :size="17" /></span></div>
          </RouterLink>
        </section>
      </div>

      <nav class="daily-explore" aria-label="更多学习方式">
        <RouterLink to="/quiz" class="daily-explore__item"><AppIcon name="practice" :size="26" /><span><strong>练一练</strong><small>用一次练习，检验理解</small></span><AppIcon name="arrow-up-right" :size="19" /></RouterLink>
        <button type="button" class="daily-explore__item" @click="emit('openAssistant')"><AppIcon name="spark" :size="26" /><span><strong>问一问</strong><small>和学习助手一起理清思路</small></span><AppIcon name="arrow-up-right" :size="19" /></button>
        <RouterLink to="/analytics" class="daily-explore__item"><AppIcon name="analytics" :size="26" /><span><strong>看看进步</strong><small>学习统计与每一次积累</small></span><AppIcon name="arrow-up-right" :size="19" /></RouterLink>
      </nav>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { useDailyQuote } from '@/composables/useDailyQuote'
import { useUserStore } from '@/stores/user'
import lakeImage from '@/assets/scenery/quiet-lake.webp'
import lakeMobile from '@/assets/scenery/quiet-lake-mobile.webp'
import forestImage from '@/assets/scenery/forest-light.webp'

const emit = defineEmits<{ openAssistant: [] }>()
const userStore = useUserStore()
const dailyQuote = useDailyQuote()
const materials = ref<MaterialPageItem[]>([])
const loading = ref(false)
const loadError = ref(false)
let isActive = true
const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '同学')
const quoteLines = computed(() => dailyQuote.value.text.match(/[^，；。！？]+[，；。！？]?/g) || [dailyQuote.value.text])
const isLongQuote = computed(() => dailyQuote.value.text.length > 25 || quoteLines.value.length > 2)
const uploadDestination = { path: '/materials', query: { action: 'upload' } }
const materialDestination = (id: number) => ({ path: '/materials', query: { materialId: String(id) } })
const learningDestination = computed(() => {
  const readable = materials.value.find(material => material.parseStatus === 'SUCCESS')
  return readable ? materialDestination(readable.id) : materials.value.length || loading.value || loadError.value ? { path: '/materials' } : uploadDestination
})
const learningHint = computed(() => materials.value.length ? '打开资料，读懂新知' : '从一份感兴趣的资料开始')
function formatMaterialType(type: string) {
  return ({ PDF: 'PDF', DOCX: 'Word', WORD: 'Word', TEXT: '文本', TXT: 'TXT', MARKDOWN: 'Markdown' } as Record<string, string>)[type?.toUpperCase()] || '文档'
}
function formatMaterialDate(value?: string) {
  if (!value) return '最近添加'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '最近添加' : new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date)
}
function formatParseStatus(status: string) {
  return ({ SUCCESS: '继续阅读', FAILED: '需重新解析', PENDING: '等待解析', PROCESSING: '正在解析' } as Record<string, string>)[status?.toUpperCase()] || '查看资料'
}
async function loadMaterials() {
  if (loading.value) return
  loading.value = true
  loadError.value = false
  const requestToken = userStore.token
  try {
    const response = await getMaterialPageApi({ current: 1, size: 3 })
    if (!isActive || userStore.token !== requestToken) return
    if (!Array.isArray(response.data?.data?.records)) throw new Error('Missing material records')
    materials.value = response.data.data.records
  } catch {
    if (isActive && userStore.token === requestToken) loadError.value = true
  } finally {
    if (isActive) loading.value = false
  }
}
onMounted(loadMaterials)
onUnmounted(() => { isActive = false })
</script>

<style scoped>
.daily-home { --home-ink: #253b34; --home-muted: #5b6c62; }
.daily-scene { position: relative; min-height: 650px; overflow: hidden; isolation: isolate; background: #dce7e4; }
.daily-scene__picture, .daily-scene__picture img, .daily-scene__veil { position: absolute; inset: 0; width: 100%; height: 100%; }
.daily-scene__picture { z-index: -3; }
.daily-scene__picture img { object-fit: cover; object-position: center 54%; }
.daily-scene__veil { z-index: -2; background: linear-gradient(90deg, rgb(239 244 236 / 97%) 0%, rgb(239 244 236 / 87%) 27%, rgb(237 242 234 / 37%) 51%, transparent 74%), linear-gradient(0deg, rgb(18 51 40 / 20%), transparent 36%); }
.daily-scene__inner { display: flex; flex-direction: column; width: min(1296px, 100%); min-height: 650px; margin: 0 auto; padding: 43px 48px 40px; color: var(--home-ink); }
.daily-scene__opening { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.daily-scene__eyebrow { display: inline-flex; align-items: center; gap: 11px; font-size: 12px; font-weight: 500; letter-spacing: 3px; }
.daily-scene__eyebrow i { width: 6px; height: 6px; border-radius: 50%; background: #a64b38; }
.daily-scene__welcome { max-width: 230px; padding: 7px 13px; border-radius: 24px; background: rgb(251 252 249 / 87%); color: #43544a; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.daily-scene__quote { margin-top: 57px; margin-bottom: 45px; }
.daily-scene__quote h1 { max-width: 660px; margin: 0; font-family: "Noto Serif SC", "Songti SC", "STSong", "SimSun", serif; font-size: clamp(36px, 4.1vw, 55px); font-weight: 400; letter-spacing: -.04em; line-height: 1.55; }
.daily-scene__quote h1 span { display: block; }
.daily-scene__quote h1.daily-scene__quote--long { font-size: clamp(30px, 3.2vw, 38px); line-height: 1.5; }
.daily-scene--long-quote .daily-scene__quote { margin-top: 28px; margin-bottom: 22px; }
.daily-scene__source { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 21px 0 0; color: #52665a; font-size: 12px; letter-spacing: .5px; }
.daily-scene__source > span { color: #718374; padding-inline: 3px; }
.daily-scene__source cite { font-style: normal; }
.daily-scene__bottom { display: flex; align-items: flex-end; justify-content: space-between; gap: 40px; margin-top: auto; }
.daily-actions { display: grid; grid-template-columns: 1.13fr 1fr; width: 630px; max-width: 100%; border-radius: 14px; background: rgb(255 255 250 / 88%); backdrop-filter: blur(12px); }
.daily-action { display: flex; align-items: center; justify-content: space-between; min-width: 0; min-height: 116px; padding: 25px 27px; gap: 12px; color: var(--home-ink); transition: background-color .2s; }
.daily-action--learn { border-radius: 14px 0 0 14px; }
.daily-action--review { position: relative; border-radius: 0 14px 14px 0; }
.daily-action--review::before { position: absolute; top: 28px; bottom: 28px; left: 0; width: 1px; background: #d4ddd3; content: ""; }
.daily-action:hover { background: rgb(255 255 255 / 78%); }
.daily-action > span:first-child { min-width: 0; }
.daily-action strong { display: block; margin-bottom: 7px; font-size: 24px; line-height: 1.3; font-weight: 600; letter-spacing: -.8px; white-space: nowrap; }
.daily-action small { display: block; color: var(--home-muted); font-size: 12px; line-height: 1.5; }
.daily-action__arrow { display: grid; place-items: center; width: 40px; height: 40px; flex: 0 0 40px; border-radius: 50%; border: 1px solid #b5c2b6; }
.daily-action--learn .daily-action__arrow { color: white; border-color: #385a4a; background: #385a4a; }
.daily-scene__scroll { display: inline-flex; align-items: center; gap: 13px; min-height: 44px; padding: 0 17px; border-radius: 24px; background: rgb(255 255 250 / 89%); color: #355348; font-size: 12px; white-space: nowrap; }
.daily-content { max-width: 1296px; margin: 0 auto; padding: 74px 48px 12px; scroll-margin-top: 105px; }
.daily-reading-layout { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr); align-items: start; gap: 74px; }
.daily-section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 23px; }
.daily-section-index { display: block; margin-bottom: 13px; color: #788175; font-size: 11px; font-weight: 500; letter-spacing: 2px; }
.daily-section-heading h2, .daily-notes h2 { margin: 0; color: var(--text); font-size: 29px; font-weight: 550; line-height: 1.5; letter-spacing: -.8px; }
.daily-text-link { display: inline-flex; align-items: center; gap: 10px; min-height: 44px; padding: 0; border: 0; color: var(--brand); background: transparent; font: inherit; font-size: 13px; white-space: nowrap; cursor: pointer; }
.daily-text-link:hover { color: var(--brand-hover); text-decoration: underline; text-underline-offset: 5px; }
.daily-reading-list { margin: 0; padding: 0; list-style: none; }
.daily-reading-list li { border-bottom: 1px solid var(--line); }
.daily-reading-list li:first-child { border-top: 1px solid var(--line); }
.daily-reading-row { display: flex; align-items: center; gap: 21px; min-width: 0; padding: 20px 0; color: var(--text); }
.daily-reading-row:hover .daily-reading-row__copy strong { color: var(--brand); }
.daily-book { position: relative; display: flex; flex-direction: column; justify-content: space-between; width: 58px; height: 76px; flex: 0 0 58px; padding: 11px 9px 9px 12px; overflow: hidden; background: #dde2d1; color: #506346; border-radius: 1px 3px 3px 1px; box-shadow: inset 4px 0 0 rgb(36 57 44 / 10%); }
.daily-book i { position: absolute; right: 6px; top: 30px; height: 40px; width: 40px; border: 1px solid currentColor; opacity: .18; border-radius: 50%; }
.daily-book small { font-size: 9px; font-weight: 600; letter-spacing: .4px; }
.daily-book b { font-family: Georgia, serif; font-size: 25px; line-height: 1; font-weight: 400; }
.daily-book--1 { background: #e7d8c9; color: #8b6447; }
.daily-book--2 { background: #dbe4e6; color: #466875; }
.daily-book--3 { background: #e5dfe7; color: #706079; }
.daily-reading-row__copy { flex: 1; min-width: 0; }
.daily-reading-row__copy strong { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; color: var(--text); font-size: 16px; font-weight: 500; line-height: 1.7; overflow-wrap: anywhere; }
.daily-reading-row__copy small { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; color: var(--muted); font-size: 11px; }
.daily-reading-row__copy i { font-style: normal; opacity: .6; }
.daily-reading-row__state { flex-shrink: 0; font-size: 11px; color: var(--muted); }
.daily-reading-row__state.is-error { color: var(--red); }
.daily-reading-row__arrow { color: var(--brand); }
.daily-first-reading { display: flex; align-items: center; gap: 32px; min-height: 297px; padding: 28px 0; border-block: 1px solid var(--line); }
.daily-first-reading__book { position: relative; display: flex; flex-direction: column; justify-content: space-between; flex: 0 0 118px; height: 165px; padding: 19px 18px 16px 22px; overflow: hidden; color: #f5f4e9; background: #537060; border-radius: 1px 4px 4px 1px; box-shadow: inset 6px 0 0 #395a49; }
.daily-first-reading__book > span { font-family: "Noto Serif SC", "Songti SC", serif; font-size: 24px; letter-spacing: 4px; }
.daily-first-reading__book > i { position: absolute; top: 32px; right: -29px; width: 120px; height: 120px; border: 1px solid #f5f4e938; border-radius: 50%; }
.daily-first-reading__book > small { position: relative; font-size: 9px; letter-spacing: 1px; }
.daily-first-reading__copy h3 { margin: 0 0 13px; font-size: 21px; font-weight: 450; line-height: 1.7; letter-spacing: -.4px; }
.daily-first-reading__copy p { margin: 0 0 17px; color: var(--muted); font-size: 13px; line-height: 1.8; }
.daily-reading-state { padding: 54px 20px; border-block: 1px solid var(--line); color: var(--muted); text-align: center; }
.daily-reading-state > .app-icon { margin: 0 auto 16px; }
.daily-reading-state h3 { margin: 0; color: var(--text); font-size: 18px; font-weight: 500; }
.daily-reading-state p { margin: 10px 0; font-size: 13px; }
.daily-inline-error { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; color: var(--red); font-size: 12px; }
.daily-inline-error button { min-height: 44px; border: 0; background: transparent; color: var(--brand); cursor: pointer; }
.daily-loading__row { display: flex; align-items: center; gap: 22px; height: 117px; border-bottom: 1px solid var(--line); }
.daily-loading__row > span { width: 58px; height: 76px; background: #e6e9df; }
.daily-loading__row > div { flex: 1; }
.daily-loading__row i { display: block; width: 70%; height: 13px; margin-block: 15px; background: #e6e9df; border-radius: 3px; }
.daily-loading__row i + i { width: 42%; height: 9px; }
.daily-notes__link { display: block; color: var(--text); }
.daily-notes__photo { position: relative; height: 250px; overflow: hidden; background: #e1e6dc; border-radius: 3px; }
.daily-notes__photo img { width: 100%; height: 100%; object-fit: cover; object-position: center 49%; transition: transform .5s; }
.daily-notes__link:hover img { transform: scale(1.035); }
.daily-notes__badge { position: absolute; display: grid; place-items: center; right: 17px; bottom: 17px; width: 42px; height: 42px; color: #364c3f; background: #fafcf4; border-radius: 50%; }
.daily-notes__copy { padding-top: 24px; }
.daily-notes .daily-section-index { margin-bottom: 8px; }
.daily-notes h2 { font-size: 22px; }
.daily-notes p { margin: 10px 0 8px; color: var(--muted); font-size: 13px; line-height: 1.8; }
.daily-explore { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: 67px; padding-top: 28px; border-top: 1px solid var(--line); }
.daily-explore__item { display: flex; align-items: center; gap: 19px; min-width: 0; padding: 11px 25px; border: 0; color: var(--brand); background: transparent; text-align: left; font: inherit; cursor: pointer; }
.daily-explore__item:first-child { padding-left: 0; }
.daily-explore__item:last-child { padding-right: 0; }
.daily-explore__item + .daily-explore__item { border-left: 1px solid var(--line); }
.daily-explore__item > span { flex: 1; min-width: 0; }
.daily-explore__item strong { display: block; margin-bottom: 5px; color: var(--text); font-size: 17px; font-weight: 500; }
.daily-explore__item small { display: block; color: var(--muted); font-size: 12px; }
.daily-explore__item:hover strong { color: var(--brand); }
@media (max-width: 1100px) {
  .daily-scene, .daily-scene__inner { min-height: 600px; }
  .daily-scene__inner { padding-inline: 40px; }
  .daily-scene__quote { margin-top: 52px; }
  .daily-scene__scroll { display: none; }
  .daily-reading-layout { gap: 40px; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); }
  .daily-content { padding-inline: 40px; }
  .daily-reading-row__state { display: none; }
  .daily-first-reading { gap: 23px; }
  .daily-first-reading__book { flex-basis: 98px; height: 140px; }
  .daily-first-reading__copy h3 { font-size: 19px; }
  .daily-explore__item { padding-inline: 17px; gap: 13px; }
  .daily-explore__item > .app-icon:first-child { display: none; }
}
@media (max-width: 760px) {
  .daily-scene, .daily-scene__inner { min-height: max(600px, calc(100svh - 146px)); }
  .daily-scene__picture img { object-position: center 48%; }
  .daily-scene__veil { background: linear-gradient(180deg, rgb(235 242 238 / 95%) 0%, rgb(235 242 238 / 75%) 27%, rgb(235 242 238 / 12%) 54%, rgb(22 55 44 / 8%) 76%, rgb(21 44 36 / 13%) 100%); }
  .daily-scene--long-quote .daily-scene__veil { background: linear-gradient(180deg, rgb(235 242 238 / 98%) 0%, rgb(235 242 238 / 92%) 32%, rgb(235 242 238 / 75%) 47%, rgb(235 242 238 / 12%) 65%, rgb(21 44 36 / 13%) 100%); }
  .daily-scene__inner { padding: 29px 23px 28px; }
  .daily-scene__eyebrow { font-size: 11px; gap: 8px; letter-spacing: 2px; }
  .daily-scene__welcome { padding: 0; background: transparent; max-width: 170px; color: #536359; font-size: 11px; }
  .daily-scene__quote { margin-top: 46px; margin-bottom: 32px; }
  .daily-scene--long-quote .daily-scene__quote { margin-top: 46px; margin-bottom: 32px; }
  .daily-scene__quote h1 { max-width: 540px; font-size: clamp(30px, 7.7vw, 42px); font-weight: 500; line-height: 1.7; letter-spacing: -.055em; }
  .daily-scene__quote h1.daily-scene__quote--long { font-size: clamp(25px, 6.7vw, 36px); line-height: 1.65; }
  .daily-scene__source { margin-top: 19px; gap: 4px; max-width: 300px; font-size: 11px; }
  .daily-actions { width: 100%; grid-template-columns: 1.08fr 1fr; border-radius: 12px; }
  .daily-action { min-height: 100px; padding: 20px 17px; gap: 9px; }
  .daily-action strong { margin-bottom: 8px; font-size: 22px; letter-spacing: -.6px; }
  .daily-action small { font-size: 10px; line-height: 1.6; }
  .daily-action--learn { border-radius: 12px 0 0 12px; }
  .daily-action--review { border-radius: 0 12px 12px 0; }
  .daily-action__arrow { flex-basis: 25px; width: 25px; height: 25px; border: 0; background: none; }
  .daily-action--learn .daily-action__arrow { background: none; color: var(--home-ink); }
  .daily-action__arrow .app-icon { width: 20px; height: 20px; }
  .daily-action--review::before { top: 23px; bottom: 23px; }
  .daily-content { padding: 48px 23px 0; scroll-margin-top: 95px; }
  .daily-reading-layout { grid-template-columns: minmax(0, 1fr); gap: 43px; }
  .daily-section-heading { gap: 14px; margin-bottom: 22px; }
  .daily-section-index { font-size: 10px; margin-bottom: 9px; }
  .daily-section-heading h2 { font-size: 27px; }
  .daily-section-heading .daily-text-link { font-size: 12px; gap: 5px; }
  .daily-reading-row { padding-block: 18px; gap: 17px; }
  .daily-reading-row__copy strong { font-size: 15px; }
  .daily-reading-row__copy small { font-size: 10px; gap: 6px; }
  .daily-first-reading { min-height: 235px; gap: 24px; }
  .daily-first-reading__copy h3 { font-size: 18px; }
  .daily-first-reading__copy p { font-size: 12px; margin-bottom: 10px; }
  .daily-first-reading__book { flex-basis: 91px; height: 134px; padding: 17px 12px 14px 17px; }
  .daily-first-reading__book > span { font-size: 21px; }
  .daily-first-reading__book > small { font-size: 8px; letter-spacing: 0; }
  .daily-notes__link { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.25fr); align-items: center; gap: 25px; }
  .daily-notes__photo { height: 220px; }
  .daily-notes__copy { padding: 0; }
  .daily-notes h2 { font-size: 21px; line-height: 1.65; }
  .daily-notes h2 > span { display: block; }
  .daily-notes p { margin-block: 11px 4px; font-size: 12px; }
  .daily-notes__badge { right: 12px; bottom: 12px; width: 34px; height: 34px; }
  .daily-notes__badge > .app-icon { width: 20px; height: 20px; }
  .daily-explore { grid-template-columns: minmax(0, 1fr); margin-top: 40px; padding-top: 7px; }
  .daily-explore__item, .daily-explore__item:first-child, .daily-explore__item:last-child { padding: 23px 0; gap: 21px; }
  .daily-explore__item + .daily-explore__item { border-left: 0; border-top: 1px solid var(--line); }
  .daily-explore__item > .app-icon:first-child { display: block; }
  .daily-explore__item strong { font-size: 17px; }
  .daily-explore__item small { font-size: 12px; }
}
@media (max-width: 360px) {
  .daily-scene__inner, .daily-content { padding-inline: 19px; }
  .daily-action { padding-inline: 13px; gap: 7px; }
  .daily-action strong { font-size: 20px; }
  .daily-action__arrow { flex-basis: 20px; width: 20px; height: 20px; }
  .daily-action small { font-size: 9px; }
  .daily-scene__welcome { max-width: 142px; }
  .daily-first-reading { gap: 16px; }
  .daily-first-reading__copy h3 { font-size: 16px; }
  .daily-notes__link { gap: 18px; }
}
@media (prefers-reduced-motion: reduce) {
  .daily-action, .daily-notes__photo img { transition: none; }
  .daily-notes__link:hover img { transform: none; }
}
</style>
