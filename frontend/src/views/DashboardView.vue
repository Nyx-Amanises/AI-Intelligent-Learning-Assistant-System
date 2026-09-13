<template>
  <section class="study-home" :aria-busy="loading">
    <header class="study-home__heading">
      <div>
        <p class="study-home__eyebrow">学习，是一点一滴的积累</p>
        <h1>{{ greeting }}，{{ greetingName }}<span class="study-home__greeting-dot">。</span></h1>
        <p class="study-home__subtitle">欢迎回到你的学习空间，今天也向前一步。</p>
      </div>
      <div class="study-home__date"><AppIcon name="calendar" :size="16" /><span>{{ todayLabel }}</span><button type="button" :disabled="loading" aria-label="刷新学习概览" title="刷新学习概览" @click="loadDashboard"><AppIcon name="refresh" :size="16" :class="{ 'is-spinning': loading }" /></button></div>
    </header>

    <div v-if="failedSections.length" class="study-notice" role="alert">
      <AppIcon name="clock" :size="18" /><span>{{ failedSections.join('、') }}暂时未能加载，稍后再试一下。</span><button type="button" @click="loadDashboard">重新加载</button>
    </div>

    <section class="study-hero" aria-labelledby="study-hero-title">
      <div class="study-hero__content">
        <span class="study-hero__eyebrow"><AppIcon name="spark" :size="15" />为你推荐的下一步</span>
        <h2 id="study-hero-title">{{ nextStep.title }}</h2>
        <p>{{ nextStep.description }}</p>
        <div class="study-hero__actions">
          <button type="button" class="study-hero__primary" @click="goNextStep">{{ nextStep.action }}<AppIcon name="arrow-right" :size="17" /></button>
          <button type="button" class="study-hero__secondary" @click="emit('openAssistant')"><AppIcon name="spark" :size="16" />和 AI 聊聊</button>
        </div>
      </div>
      <div class="study-illustration" aria-hidden="true">
        <svg viewBox="0 0 360 260" fill="none">
          <circle cx="203" cy="128" r="104" stroke="#759272" stroke-opacity=".28" />
          <circle cx="203" cy="128" r="79" stroke="#759272" stroke-opacity=".24" stroke-dasharray="3 8" />
          <ellipse cx="198" cy="215" rx="114" ry="13" fill="#163a2c" fill-opacity=".6" />
          <path d="M94 80c34-9 65-3 96 19v112c-30-21-61-29-96-19V80Z" fill="#9bae8a" />
          <path d="M190 99c31-22 62-28 96-19v112c-35-10-66-2-96 19V99Z" fill="#6f8d69" />
          <path d="M91 70c35-7 68 1 99 25v105c-30-23-64-31-99-22V70Z" fill="#f1f1d9" />
          <path d="M190 95c31-24 64-32 99-25v108c-35-9-69-1-99 22V95Z" fill="#dfe7cc" />
          <path d="M190 95v105" stroke="#a9b796" stroke-width="2" />
          <path d="m109 97 61 19m-61-1 61 19m-61-1 45 14m56-30 57-18m-57 37 57-18m-57 37 43-14" stroke="#adba97" stroke-width="3" stroke-linecap="round" />
          <path d="M249 68v43l10-10 10 4V66" fill="#bf9250" />
          <path d="m319 135 3 8 8 3-8 3-3 8-3-8-8-3 8-3 3-8Z" fill="#c4d0a0" />
          <circle cx="77" cy="145" r="5" fill="#b5c294" />
          <path d="m130 37 2 6 6 2-6 2-2 6-2-6-6-2 6-2 2-6Z" fill="#d4d9ab" />
        </svg>
        <span class="study-illustration__label study-illustration__label--top"><AppIcon name="check" :size="14" />读懂一个新知识</span>
        <span class="study-illustration__label study-illustration__label--bottom"><AppIcon name="book-open" :size="14" />收获一点新进步</span>
      </div>
    </section>

    <div class="study-stats">
      <button v-for="stat in stats" :key="stat.label" type="button" class="study-stat" @click="router.push(stat.path)">
        <div class="study-stat__top"><span>{{ stat.label }}</span><AppIcon :name="stat.icon" :size="18" /></div>
        <div class="study-stat__value"><span v-if="stat.loading && stat.value === '—'" class="study-skeleton study-skeleton--number" /><template v-else>{{ stat.value }}<small v-if="stat.value !== '—'">{{ stat.unit }}</small></template></div>
        <span class="study-stat__caption">{{ stat.caption }}<AppIcon name="arrow-up-right" :size="13" /></span>
      </button>
    </div>

    <div class="study-columns">
      <div class="study-main-column">
        <section class="study-materials" aria-labelledby="recent-materials-title">
          <div class="study-section-heading"><div><h2 id="recent-materials-title">最近资料</h2><p>从熟悉的地方，继续探索。</p></div><RouterLink to="/materials" class="study-text-link">全部资料<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
          <div v-if="states.materials === 'loading' && !materials.length" class="study-material-grid">
            <div v-for="n in 3" :key="n" class="study-material-card study-material-card--skeleton"><span class="study-skeleton study-skeleton--icon" /><span class="study-skeleton" /><span class="study-skeleton study-skeleton--short" /></div>
          </div>
          <div v-else-if="states.materials === 'error' && !materials.length" class="study-empty"><AppIcon name="folder" :size="28" /><h3>资料暂时未能加载</h3><button type="button" class="study-text-link" @click="loadSection('materials')">重新加载</button></div>
          <div v-else-if="!materials.length" class="study-empty"><AppIcon name="book-open" :size="32" /><h3>你的知识库，从这里开始</h3><p>上传一份课程讲义，或写下正在学习的内容。</p><RouterLink :to="{ path: '/materials', query: { action: 'upload' } }" class="study-empty__action"><AppIcon name="plus" :size="16" />添加第一份资料</RouterLink></div>
          <div v-else class="study-material-grid">
            <button v-for="(material, index) in materials" :key="material.id" type="button" class="study-material-card" @click="openMaterial(material.id)">
              <div class="study-material-card__top"><span class="study-material-card__icon" :class="'study-material-card__icon--' + index"><AppIcon name="materials" :size="24" /></span><span class="study-material-card__type">{{ formatMaterialType(material.materialType) }}</span></div>
              <h3>{{ material.title }}</h3>
              <p>{{ formatCharacters(material.totalCharacters) }}<span>·</span>{{ formatDate(material.createdAt) }}</p>
              <div class="study-material-card__bottom"><span :class="{ 'is-ready': material.parseStatus === 'SUCCESS', 'is-error': material.parseStatus === 'FAILED' }"><i />{{ formatParseStatus(material.parseStatus) }}</span><AppIcon name="arrow-up-right" :size="16" /></div>
            </button>
          </div>
        </section>

        <section class="study-practices" aria-labelledby="recent-practices-title">
          <div class="study-section-heading"><div><h2 id="recent-practices-title">最近练习</h2><p>回看每次练习，让进步有迹可循。</p></div><RouterLink to="/practice" class="study-text-link">全部记录<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
          <div v-if="states.practices === 'loading' && !practices.length" class="study-practice-list"><div v-for="n in 3" :key="n" class="study-practice-row"><span class="study-skeleton" /></div></div>
          <div v-else-if="states.practices === 'error' && !practices.length" class="study-empty"><AppIcon name="practice" :size="28" /><h3>练习记录暂时未能加载</h3><button type="button" class="study-text-link" @click="loadSection('practices')">重新加载</button></div>
          <div v-else-if="!practices.length" class="study-empty"><AppIcon name="practice" :size="30" /><h3>用一次练习，检验新的收获</h3><p>从资料生成题集，完成后就能在这里查看表现。</p><RouterLink to="/quiz" class="study-text-link">去做一轮练习<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
          <div v-else class="study-practice-list">
            <RouterLink v-for="practice in practices" :key="practice.id" class="study-practice-row" :to="{ path: '/practice', query: { sessionId: String(practice.id) } }">
              <span class="study-practice-row__icon"><AppIcon name="practice" :size="20" /></span>
              <div class="study-practice-row__title"><h3>{{ practice.sessionName }}</h3><p>{{ practice.totalQuestions }} 道题<span>·</span>{{ formatDate(practice.submitTime) }}</p></div>
              <div class="study-practice-row__result"><strong>{{ formatRate(practice.accuracyRate) }}<small>%</small></strong><span>正确率</span></div>
              <AppIcon name="chevron-right" :size="16" />
            </RouterLink>
          </div>
          <RouterLink to="/quiz" class="study-new-practice"><AppIcon name="plus" :size="17" /><span>开启下一轮练习</span></RouterLink>
        </section>
      </div>

      <aside class="study-side-column" aria-label="学习表现和复习建议">
        <section class="study-chart-panel">
          <div class="study-panel-heading"><h2>练习表现</h2><RouterLink to="/analytics" class="study-icon-link" aria-label="查看完整学习分析"><AppIcon name="arrow-up-right" :size="18" /></RouterLink></div>
          <div class="study-chart-switch" aria-label="图表指标"><button type="button" :aria-pressed="chartMetric === 'accuracyRate'" @click="chartMetric = 'accuracyRate'">正确率</button><button type="button" :aria-pressed="chartMetric === 'scoreRate'" @click="chartMetric = 'scoreRate'">得分率</button></div>
          <div v-if="analytics && analytics.totalQuestionAttempts" class="study-chart-total"><strong>{{ formatRate(chartMetric === 'accuracyRate' ? analytics.averageAccuracyRate : analytics.averageScoreRate) }}<small>%</small></strong><span>{{ chartMetric === 'accuracyRate' ? '整体正确率' : '整体得分率' }}</span></div>
          <div v-if="chartPoints.length" class="study-chart">
            <svg viewBox="0 0 300 180" role="img" :aria-label="'最近 ' + chartPoints.length + ' 次练习的' + (chartMetric === 'accuracyRate' ? '正确率' : '得分率') + '趋势'">
              <defs><linearGradient id="study-chart-gradient" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#94b18a" stop-opacity=".35" /><stop offset="100%" stop-color="#94b18a" stop-opacity=".02" /></linearGradient></defs>
              <g v-for="tick in [0,50,100]" :key="tick"><line x1="32" :y1="146 - tick * 1.15" x2="286" :y2="146 - tick * 1.15" stroke="#e8ece4" stroke-dasharray="3 4" /><text x="24" :y="150 - tick * 1.15" text-anchor="end" class="study-chart__tick">{{ tick }}</text></g>
              <polygon v-if="chartPoints.length > 1" :points="chartArea" fill="url(#study-chart-gradient)" />
              <polyline :points="chartLine" fill="none" stroke="#478058" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
              <g v-for="(point,index) in chartPoints" :key="point.sessionId">
                <circle :cx="point.x" :cy="point.y" r="4" fill="#478058" stroke="white" stroke-width="2" tabindex="0" role="img" :aria-label="point.sessionName + '，' + formatRate(point.value) + '%'"><title>{{ point.sessionName }} · {{ formatRate(point.value) }}%</title></circle>
                <text v-if="index === 0 || index === chartPoints.length - 1 || index === Math.floor((chartPoints.length - 1) / 2)" :x="point.x" y="170" text-anchor="middle" class="study-chart__tick">{{ formatDate(point.submitTime) }}</text>
              </g>
            </svg>
            <p>最近 {{ chartPoints.length }} 次已提交的练习</p>
          </div>
          <div v-else class="study-chart-empty"><AppIcon name="analytics" :size="30" /><p>{{ states.analytics === 'loading' ? '正在加载练习表现…' : states.analytics === 'error' ? '分析数据暂时未能加载' : '完成练习后，在这里看见进步' }}</p><button v-if="states.analytics === 'error'" type="button" class="study-text-link" @click="loadSection('analytics')">重试</button></div>
        </section>

        <section class="study-review-panel">
          <div class="study-panel-heading"><h2>值得再温习</h2><span v-if="weakPoints.length" class="study-count">{{ weakPoints.length }} 个知识点</span></div>
          <p class="study-panel-description">把不太确定的，变成胸有成竹。</p>
          <div v-if="weakPoints.length" class="study-review-list">
            <RouterLink v-for="point in weakPoints" :key="String(point.materialId) + point.knowledgePoint" :to="{ path: '/mastery', query: { keyword: point.knowledgePoint, ...(point.materialId ? { materialId: String(point.materialId) } : {}) } }" class="study-review-item">
              <div><strong>{{ point.knowledgePoint }}</strong><span>{{ formatRate(point.masteryPercent) }}%</span></div>
              <div class="study-review-track"><i :style="{ width: clampRate(point.masteryPercent) + '%' }" /></div>
              <small>{{ point.materialTitle || '查看知识点详情' }}</small>
            </RouterLink>
          </div>
          <div v-else class="study-review-empty"><AppIcon name="check" :size="22" /><p>{{ states.analytics === 'error' ? '复习建议暂时不可用' : analytics?.totalQuestionAttempts ? '当前没有需要优先巩固的知识点，保持你的学习节奏。' : '完成一次练习后，为你找出值得巩固的知识点。' }}</p></div>
          <RouterLink to="/wrong-questions" class="study-review-link">打开错题本<span v-if="wrongTotal !== null">{{ wrongTotal }} 条</span><AppIcon name="arrow-right" :size="15" /></RouterLink>
        </section>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { getPracticePageApi } from '@/api/modules/practice'
import { getQuestionSetPageApi } from '@/api/modules/question'
import { getWrongQuestionPageApi } from '@/api/modules/wrongQuestion'
import { getLearningAnalyticsOverviewApi, type LearningAnalyticsOverviewPayload } from '@/api/modules/learningAnalytics'
import { useUserStore } from '@/stores/user'

interface PracticePreview { id: number; sessionName: string; totalQuestions: number; accuracyRate: number; submitTime?: string }
type LoadKey = 'materials' | 'practices' | 'questions' | 'analytics' | 'wrong'
const emit = defineEmits<{ openAssistant: [] }>()
const router = useRouter()
const userStore = useUserStore()
const pending = reactive<Record<LoadKey, boolean>>({ materials: false, practices: false, questions: false, analytics: false, wrong: false })
const loading = computed(() => Object.values(pending).some(Boolean))
const materials = ref<MaterialPageItem[]>([])
const practices = ref<PracticePreview[]>([])
const materialTotal = ref<number | null>(null)
const practiceTotal = ref<number | null>(null)
const questionTotal = ref<number | null>(null)
const wrongTotal = ref<number | null>(null)
const analytics = ref<LearningAnalyticsOverviewPayload | null>(null)
const states = reactive<Record<LoadKey, 'loading' | 'success' | 'error'>>({ materials: 'loading', practices: 'loading', questions: 'loading', analytics: 'loading', wrong: 'loading' })
const chartMetric = ref<'accuracyRate' | 'scoreRate'>('accuracyRate')
const greetingName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '同学')
const now = new Date()
const greeting = now.getHours() < 6 ? '夜深了' : now.getHours() < 12 ? '上午好' : now.getHours() < 18 ? '下午好' : '晚上好'
const todayLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(now)
const stateLabels: Record<LoadKey,string> = { materials: '资料', practices: '练习记录', questions: '题集', analytics: '学习分析', wrong: '错题本' }
const failedSections = computed(() => (Object.keys(states) as LoadKey[]).filter(key => states[key] === 'error').map(key => stateLabels[key]))
const nextStep = computed(() => {
  if (materialTotal.value === 0) return { title: '从一份资料，开启新的收获。', description: '放入你的课件、讲义或笔记，让 AI 帮你梳理重点，再通过练习加深理解。', action: '添加学习资料', path: '/materials', upload: true }
  if (wrongTotal.value && wrongTotal.value > 0) return { title: '温故，而后知新。', description: '错题本里有 ' + wrongTotal.value + ' 条记录。回看不确定的知识，让理解再深一点。', action: '开始温习错题', path: '/wrong-questions' }
  if (questionTotal.value && questionTotal.value > 0) return { title: '学有所思，也学有所练。', description: '用一轮练习检验理解，把读过的知识转化为真正掌握的能力。', action: '开始一轮练习', path: '/quiz' }
  return { title: '把好奇，变成自己的知识。', description: '整理一份学习笔记，厘清一个新概念。进步，就藏在这些小小的积累里。', action: '探索我的资料', path: '/materials' }
})
const duration = computed(() => {
  if (!analytics.value) return { value: '—', unit: '' }
  const minutes = Math.floor(Math.max(0, Number(analytics.value.totalStudySeconds) || 0) / 60)
  if (minutes >= 60) return { value: (minutes / 60).toFixed(1).replace(/\.0$/, ''), unit: '小时' }
  return { value: String(minutes), unit: '分钟' }
})
const stats = computed(() => [
  { label: '我的资料', loading: pending.materials, value: materialTotal.value === null ? '—' : materialTotal.value.toLocaleString(), unit: '份', caption: states.materials === 'error' ? '资料暂不可用' : '积累你的知识库', icon: 'folder', path: '/materials' },
  { label: '完成练习', loading: pending.practices, value: practiceTotal.value === null ? '—' : practiceTotal.value.toLocaleString(), unit: '次', caption: states.practices === 'error' ? '记录暂不可用' : '每一次练习，都是进步', icon: 'practice', path: '/practice' },
  { label: '整体正确率', loading: pending.analytics, value: analytics.value?.totalQuestionAttempts ? formatRate(analytics.value.averageAccuracyRate) : '—', unit: '%', caption: states.analytics === 'error' ? '分析暂不可用' : '基于已评分的作答', icon: 'mastery', path: '/analytics' },
  { label: '累计练习时长', loading: pending.analytics, ...duration.value, caption: states.analytics === 'error' ? '分析暂不可用' : '来自已提交的练习', icon: 'clock', path: '/analytics' }
])
const weakPoints = computed(() => (analytics.value?.weakKnowledgePoints || []).filter(point => point.masteryPercent < 70).slice(0, 3))
const chartPoints = computed(() => {
  const points = analytics.value?.practiceTrend || []
  return points.map((point, index) => {
    const value = clampRate(point[chartMetric.value])
    return { ...point, value, x: points.length === 1 ? 159 : 38 + index * 242 / (points.length - 1), y: 146 - value * 1.15 }
  })
})
const chartLine = computed(() => chartPoints.value.map(p => p.x + ',' + p.y).join(' '))
const chartArea = computed(() => {
  if (!chartPoints.value.length) return ''
  return chartPoints.value[0].x + ',146 ' + chartLine.value + ' ' + chartPoints.value[chartPoints.value.length - 1].x + ',146'
})
function clampRate(value: unknown) { return Math.min(100, Math.max(0, Number(value) || 0)) }
function formatRate(value: unknown) { return clampRate(value).toFixed(1).replace(/\.0$/, '') }
function formatCharacters(value?: number) { return (value || 0).toLocaleString() + ' 字' }
function formatDate(value?: string) {
  if (!value) return '日期未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '日期未知' : String(date.getMonth() + 1).padStart(2,'0') + '/' + String(date.getDate()).padStart(2,'0')
}
function formatMaterialType(type: string) {
  return ({ PDF: 'PDF', DOCX: 'Word', WORD: 'Word', TEXT: '文本', TXT: 'TXT', MARKDOWN: 'Markdown' } as Record<string,string>)[type?.toUpperCase()] || '文档'
}
function formatParseStatus(status: string) {
  return ({ SUCCESS: '可开始学习', FAILED: '解析失败', PENDING: '待解析', PROCESSING: '解析中' } as Record<string,string>)[status?.toUpperCase()] || '待处理'
}
function openMaterial(id: number) { void router.push({ path: '/materials', query: { materialId: String(id) } }) }
function goNextStep() { void router.push({ path: nextStep.value.path, query: nextStep.value.upload ? { action: 'upload' } : {} }) }
const sectionRequests: Record<LoadKey, { fetch: () => Promise<any>; apply: (data: any) => void }> = {
  materials: { fetch: () => getMaterialPageApi({ current: 1, size: 3 }), apply: data => { materials.value = data.records || []; materialTotal.value = Number(data.total) || 0 } },
  practices: { fetch: () => getPracticePageApi({ current: 1, size: 3 }), apply: data => { practices.value = data.records || []; practiceTotal.value = Number(data.total) || 0 } },
  questions: { fetch: () => getQuestionSetPageApi({ current: 1, size: 1 }), apply: data => { questionTotal.value = Number(data.total) || 0 } },
  wrong: { fetch: () => getWrongQuestionPageApi({ current: 1, size: 1 }), apply: data => { wrongTotal.value = Number(data.total) || 0 } },
  analytics: { fetch: () => getLearningAnalyticsOverviewApi({ trendLimit: 8 }), apply: data => { analytics.value = data } }
}
async function loadSection(key: LoadKey) {
  if (pending[key]) return
  pending[key] = true
  states[key] = 'loading'
  try {
    const response = await sectionRequests[key].fetch()
    if (!response.data?.data) throw new Error('Missing overview data')
    sectionRequests[key].apply(response.data.data)
    states[key] = 'success'
  } catch { states[key] = 'error' }
  finally { pending[key] = false }
}
async function loadDashboard() {
  await Promise.allSettled((Object.keys(sectionRequests) as LoadKey[]).map(loadSection))
}
onMounted(loadDashboard)
</script>

<style scoped>
.study-home { min-width: 0; }
.study-home__heading { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 27px; }
.study-home__eyebrow { margin: 0 0 6px; color: var(--muted); font-size: 12px; letter-spacing: .8px; }
.study-home h1 { margin: 0; font-size: 31px; font-weight: 650; letter-spacing: -.9px; line-height: 1.5; }
.study-home__greeting-dot { color: var(--brand); }
.study-home__subtitle { margin: 5px 0 0; font-size: 12px; color: var(--muted); }
.study-home__date { display: flex; align-items: center; gap: 9px; color: var(--muted); font-size: 12px; white-space: nowrap; }
.study-home__date > button { display: grid; place-items: center; width: 34px; height: 38px; background: #fff; border: 1px solid var(--line); border-radius: 7px; color: var(--muted); cursor: pointer; margin-left: 3px; }
.study-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) 340px; min-height: 242px; overflow: hidden; background: #264e3c; border: 1px solid #254d3b; border-radius: 15px; color: #fff; }
.study-hero__content { position: relative; z-index: 1; padding: 30px 0 30px 34px; }
.study-hero__eyebrow { display: flex; align-items: center; gap: 7px; color: #d7e2c9; font-size: 12px; letter-spacing: .5px; }
.study-hero h2 { font-size: 28px; font-weight: 550; letter-spacing: .5px; line-height: 1.5; margin: 12px 0 8px; color: #fbf9e9; }
.study-hero__content > p { max-width: 470px; margin: 0; color: #cfddcd; font-size: 12px; line-height: 1.9; }
.study-hero__actions { display: flex; gap: 17px; align-items: center; margin-top: 23px; }
.study-hero__actions button { display: flex; align-items: center; justify-content: center; gap: 9px; min-height: 40px; border-radius: 7px; padding: 0 16px; cursor: pointer; font-size: 12px; transition: background .18s; }
.study-hero__primary { border: 1px solid #f2f2df; background: #f3f3df; color: #264e3c; font-weight: 650; }
.study-hero__primary:hover { background: #fffdec; }
.study-hero__secondary { border: 1px solid #5d7b64; color: #e6ecdd; background: transparent; }
.study-hero__secondary:hover { background: #365e47; }
.study-illustration { position: relative; align-self: stretch; }
.study-illustration > svg { position: absolute; width: 330px; height: 250px; right: 19px; bottom: -3px; }
.study-illustration__label { position: absolute; display: flex; align-items: center; gap: 8px; padding: 9px 11px; border: 1px solid #607c5d; border-radius: 8px; background: #335c44; color: #e1e8d0; font-size: 12px; box-shadow: 0 5px 16px #19392533; transform: rotate(-5deg); }
.study-illustration__label--top { right: 31px; top: 24px; }
.study-illustration__label--bottom { right: 196px; bottom: 30px; transform: rotate(4deg); }
.study-stats { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 17px; margin: 22px 0 30px; }
.study-stat { display: flex; flex-direction: column; align-items: stretch; text-align: left; padding: 18px 20px 15px; border: 1px solid var(--line); border-radius: 11px; background: var(--panel); cursor: pointer; transition: border-color .18s, box-shadow .18s; min-width: 0; }
.study-stat:hover { border-color: #bacdb4; box-shadow: var(--shadow); }
.study-stat__top { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--text-secondary); font-size: 12px; }
.study-stat__top > .app-icon { color: #84957e; }
.study-stat__value { font-size: 30px; font-weight: 600; color: var(--text); line-height: 1.3; margin: 12px 0 11px; letter-spacing: -.8px; font-variant-numeric: tabular-nums; min-height: 39px; }
.study-stat__value small { font-size: 12px; font-weight: 400; margin-left: 7px; letter-spacing: 0; color: var(--muted); }
.study-stat__caption { display: flex; align-items: center; justify-content: space-between; gap: 5px; color: var(--muted); font-size: 12px; }
.study-columns { display: grid; grid-template-columns: minmax(0,1fr) 300px; gap: 26px; align-items: start; }
.study-main-column, .study-side-column { display: grid; gap: 28px; min-width: 0; }
.study-side-column { gap: 20px; }
.study-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.study-section-heading h2, .study-panel-heading h2 { margin: 0; font-size: 17px; color: var(--text); font-weight: 600; letter-spacing: -.3px; }
.study-section-heading p { margin: 5px 0 0; font-size: 12px; color: var(--muted); }
.study-text-link { display: inline-flex; align-items: center; gap: 6px; padding: 3px 0; color: var(--brand); font-size: 12px; white-space: nowrap; background: transparent; border: 0; cursor: pointer; }
.study-text-link:hover { color: #122e20; text-decoration: underline; text-underline-offset: 4px; }
.study-material-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 14px; }
.study-material-card { padding: 18px 16px 0; text-align: left; background: #fff; border: 1px solid var(--line); border-radius: 11px; cursor: pointer; min-width: 0; transition: border-color .18s, box-shadow .18s; }
.study-material-card:hover { border-color: #a9bea2; box-shadow: var(--shadow); }
.study-material-card__top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.study-material-card__icon { display: grid; place-items: center; width: 42px; height: 46px; border-radius: 9px; background: #edf2e7; color: #6b825d; }
.study-material-card__icon--1 { background: #f8efdf; color: #b4935b; }
.study-material-card__icon--2 { background: #ebeff7; color: #7a88a9; }
.study-material-card__type { font-size: 12px; line-height: 1.7; border: 1px solid var(--line); border-radius: 4px; padding: 1px 5px; color: var(--muted); }
.study-material-card h3 { margin: 17px 0 7px; font-size: 14px; font-weight: 600; color: var(--text); line-height: 1.7; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; min-height: 44px; overflow-wrap: anywhere; }
.study-material-card > p { font-size: 12px; color: var(--muted); margin: 0 0 16px; display: flex; gap: 6px; flex-wrap: wrap; }
.study-material-card__bottom { display: flex; justify-content: space-between; align-items: center; min-height: 43px; border-top: 1px solid #f0f2ea; color: var(--muted); font-size: 12px; gap: 6px; }
.study-material-card__bottom > span { display: flex; align-items: center; gap: 5px; }
.study-material-card__bottom i { display: inline-block; width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.study-material-card__bottom .is-ready { color: var(--brand); }
.study-material-card__bottom .is-error { color: var(--red); }
.study-practice-list { border: 1px solid var(--line); border-radius: 11px; background: #fff; overflow: hidden; }
.study-practice-row { display: flex; align-items: center; gap: 13px; padding: 17px 19px; min-width: 0; min-height: 84px; transition: background .18s; }
.study-practice-row + .study-practice-row { border-top: 1px solid #edf0e8; }
.study-practice-row:hover { background: #fbfcf8; }
.study-practice-row__icon { display: grid; place-items: center; width: 38px; height: 40px; flex: 0 0 38px; background: var(--brand-light); color: #7a9272; border-radius: 9px; }
.study-practice-row__title { min-width: 0; flex: 1; }
.study-practice-row__title h3 { margin: 0 0 5px; color: var(--text); font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.study-practice-row__title p { display: flex; gap: 8px; margin: 0; font-size: 12px; color: var(--muted); }
.study-practice-row__result { text-align: right; min-width: 56px; }
.study-practice-row__result strong { color: var(--brand); font-size: 18px; font-weight: 600; font-variant-numeric: tabular-nums; }
.study-practice-row__result strong small { font-size: 12px; margin-left: 2px; }
.study-practice-row__result > span { display: block; color: var(--muted); font-size: 12px; }
.study-practice-row > .app-icon { color: #95a28f; }
.study-new-practice { display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 44px; margin-top: 11px; border: 1px dashed #cdd8c6; border-radius: 9px; color: var(--brand); font-size: 12px; }
.study-new-practice:hover { background: var(--brand-light); }
.study-chart-panel, .study-review-panel { padding: 20px; background: #fff; border: 1px solid var(--line); border-radius: 11px; }
.study-panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.study-panel-heading h2 { font-size: 15px; }
.study-icon-link { display: grid; place-items: center; width: 26px; height: 28px; color: var(--muted); }
.study-chart-switch { display: inline-flex; padding: 3px; background: var(--bg-secondary); border-radius: 6px; margin-top: 16px; }
.study-chart-switch button { padding: 4px 11px; border: 0; border-radius: 4px; font-size: 12px; color: var(--muted); background: transparent; cursor: pointer; }
.study-chart-switch button[aria-pressed="true"] { background: #fff; color: var(--brand); box-shadow: 0 1px 3px #203d2510; }
.study-chart-total { display: flex; gap: 10px; align-items: baseline; margin: 14px 0 0; }
.study-chart-total > strong { color: var(--brand); font-size: 30px; font-weight: 600; line-height: 1.25; font-variant-numeric: tabular-nums; }
.study-chart-total > strong small { font-size: 13px; margin-left: 3px; }
.study-chart-total > span { font-size: 12px; color: var(--muted); }
.study-chart { margin: 0 -7px; }
.study-chart > svg { display: block; width: 100%; height: auto; }
.study-chart__tick { fill: var(--muted); font-size: 12px; }
.study-chart > p { margin: -1px 0 0; font-size: 12px; color: var(--muted); text-align: center; }
.study-chart-empty { min-height: 162px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; text-align: center; color: var(--muted); font-size: 12px; }
.study-chart-empty p { margin: 0; }
.study-count { font-size: 12px; color: #8e753e; background: #f8f0df; padding: 3px 7px; border-radius: 5px; }
.study-panel-description { margin: 9px 0 17px; font-size: 12px; color: var(--muted); }
.study-review-list { display: grid; gap: 18px; }
.study-review-item { display: block; }
.study-review-item > div:first-child { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 7px; }
.study-review-item strong { color: var(--text); font-size: 12px; font-weight: 500; }
.study-review-item span { color: #9e7c43; font-size: 12px; }
.study-review-track { height: 4px; background: #f3f1e8; border-radius: 3px; overflow: hidden; }
.study-review-track i { display: block; height: 100%; background: #c5ae77; border-radius: inherit; }
.study-review-item small { display: block; font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 5px; }
.study-review-item:hover strong { color: var(--brand); }
.study-review-link { display: flex; align-items: center; gap: 7px; border-top: 1px solid var(--line); margin-top: 19px; padding-top: 15px; font-size: 12px; color: var(--brand); }
.study-review-link > span { margin-left: auto; color: var(--muted); font-size: 12px; }
.study-review-link > .app-icon:last-child { margin-left: auto; }
.study-review-link > span + .app-icon:last-child { margin-left: 0; }
.study-review-empty { display: flex; gap: 11px; align-items: flex-start; color: #7a8f70; font-size: 12px; }
.study-review-empty > .app-icon { flex-shrink: 0; margin-top: 2px; }
.study-review-empty p { margin: 0; line-height: 1.8; color: var(--muted); }
.study-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 31px 20px; min-height: 215px; background: #fff; border: 1px dashed #d8e1cf; border-radius: 11px; text-align: center; }
.study-empty > .app-icon { color: #8b9f7c; }
.study-empty h3 { margin: 0; font-size: 14px; color: var(--text); font-weight: 500; }
.study-empty p { margin: 0; color: var(--muted); font-size: 12px; }
.study-empty__action { display: flex; align-items: center; gap: 7px; margin-top: 6px; padding: 9px 14px; background: var(--brand); color: #fff; border-radius: 7px; font-size: 12px; }
.study-notice { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 12px 16px; margin-bottom: 20px; color: #86652f; background: #faf2e1; border: 1px solid #ebdcc0; border-radius: 9px; font-size: 12px; }
.study-notice button { margin-left: auto; padding: 4px 7px; border: 0; color: #775521; background: transparent; cursor: pointer; text-decoration: underline; }
.study-skeleton { display: block; height: 14px; width: 90%; border-radius: 5px; background: #eef1e8; animation: study-pulse 1.3s ease-in-out infinite alternate; }
.study-skeleton--number { width: 65px; height: 31px; }
.study-skeleton--icon { width: 44px; height: 44px; margin-bottom: 20px; }
.study-skeleton--short { width: 55%; margin-top: 14px; }
.study-material-card--skeleton { min-height: 211px; }
.is-spinning { animation: study-spin 1s linear infinite; }
@keyframes study-pulse { to { opacity: .45; } }
@keyframes study-spin { to { transform: rotate(360deg); } }
@media (min-width: 1600px) { .study-hero__content { padding: 36px 0 36px 40px; } .study-hero h2 { font-size: 32px; } .study-columns { grid-template-columns: minmax(0,1fr) 330px; gap: 30px; } .study-material-card h3 { font-size: 14px; } }
@media (max-width: 1200px) { .study-columns { grid-template-columns: minmax(0,1fr) 265px; gap: 20px; } .study-stats { gap: 12px; } .study-stat { padding: 16px; } .study-hero { grid-template-columns: minmax(0,1fr) 270px; } .study-illustration > svg { width: 275px; right: 0; } .study-illustration__label--top { right: 18px; } .study-illustration__label--bottom { right: 143px; } .study-hero h2 { font-size: 25px; } .study-material-grid { gap: 10px; } .study-material-card { padding-inline: 12px; } }
@media (max-width: 1040px) { .study-stats { grid-template-columns: repeat(2,minmax(0,1fr)); } .study-columns { grid-template-columns: minmax(0,1fr); } .study-side-column { grid-template-columns: repeat(2,minmax(0,1fr)); align-items: start; } .study-hero { grid-template-columns: minmax(0,1fr) 220px; } .study-illustration > svg { width: 240px; right: -15px; } .study-illustration__label--bottom { right: 68px; bottom: 24px; } .study-illustration__label--top { top: 26px; right: 8px; } .study-hero__content { padding-left: 25px; } .study-home__date > span { display: none; } .study-stat__caption { font-size: 12px; } }
@media (max-width: 760px) { .study-home__heading { gap: 12px; margin-bottom: 22px; } .study-home h1 { font-size: 27px; } .study-home__eyebrow { font-size: 12px; } .study-home__date > .app-icon { display: none; } .study-home__date > button { height: 44px; width: 40px; } .study-hero { grid-template-columns: minmax(0,1fr) 210px; } .study-hero h2 { font-size: 24px; } .study-hero__content { padding: 26px 0 26px 24px; } .study-hero__actions { gap: 10px; flex-wrap: wrap; } .study-hero__actions button { min-height: 44px; } .study-stats { grid-template-columns: repeat(2,minmax(0,1fr)); margin-bottom: 25px; } .study-stat { padding: 17px; } .study-stat__caption { font-size: 12px; } .study-stat__top { font-size: 13px; } .study-material-card h3 { font-size: 14px; } .study-material-card__type, .study-material-card__bottom, .study-material-card > p { font-size: 12px; } .study-text-link { font-size: 12px; min-height: 40px; } .study-practice-row__title h3 { font-size: 13px; } .study-practice-row__title p, .study-practice-row__result > span { font-size: 12px; } .study-chart-switch button { min-height: 38px; font-size: 12px; } }
@media (max-width: 550px) { .study-home h1 { font-size: 25px; } .study-home__subtitle { font-size: 12px; } .study-hero { display: block; } .study-hero__content { padding: 25px 22px; } .study-hero h2 { font-size: 23px; } .study-hero__content > p { font-size: 12px; max-width: 100%; } .study-illustration { display: none; } .study-stats { gap: 10px; margin-top: 16px; } .study-stat { padding: 14px; } .study-stat__value { font-size: 29px; } .study-stat__caption { font-size: 12px; } .study-stat__caption > .app-icon { display: none; } .study-material-grid { grid-template-columns: minmax(0,1fr); gap: 12px; } .study-material-card { display: grid; grid-template-columns: 48px minmax(0,1fr); column-gap: 14px; padding: 16px 16px 0; } .study-material-card__top { grid-row: span 2; display: block; } .study-material-card__type { display: inline-block; margin-top: 6px; } .study-material-card h3 { min-height: 0; margin: 0 0 5px; } .study-material-card > p { margin: 0 0 15px; } .study-material-card__bottom { grid-column: 1 / -1; } .study-side-column { grid-template-columns: minmax(0,1fr); } .study-practice-row { padding: 17px 13px; gap: 10px; } .study-practice-row__title h3 { white-space: normal; line-height: 1.6; } .study-practice-row__icon { display: none; } .study-chart-panel, .study-review-panel { padding: 20px; } .study-review-item strong { font-size: 14px; } .study-review-item small { font-size: 12px; } .study-review-link { min-height: 44px; font-size: 13px; } .study-panel-description { font-size: 12px; } .study-material-card--skeleton { display: block; min-height: 150px; } }
</style>
