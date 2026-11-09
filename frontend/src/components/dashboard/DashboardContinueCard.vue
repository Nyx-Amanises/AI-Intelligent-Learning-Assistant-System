<template>
  <section class="continue-card" aria-labelledby="continue-card-title" :aria-busy="loading">
    <template v-if="loading">
      <div class="continue-card__main" aria-hidden="true">
        <span class="skeleton continue-card__sk continue-card__sk--label" />
        <span class="skeleton continue-card__sk continue-card__sk--title" />
        <span class="skeleton continue-card__sk continue-card__sk--meta" />
        <span class="skeleton continue-card__sk continue-card__sk--button" />
      </div>
      <div class="continue-card__aside" aria-hidden="true">
        <span class="skeleton continue-card__sk continue-card__sk--aside" />
      </div>
      <h2 id="continue-card-title" class="visually-hidden">正在加载学习进度</h2>
    </template>

    <template v-else-if="practice">
      <div class="continue-card__main">
        <p class="continue-card__label"><AppIcon name="practice" :size="16" />接着上次的练习</p>
        <h2 id="continue-card-title" class="continue-card__title" :title="practice.sessionName">
          {{ practice.sessionName }}
        </h2>
        <p class="continue-card__meta">
          已答 {{ answeredCount }} / {{ practice.answered.length }} 题，答案暂存在这台设备上
        </p>
        <div class="continue-card__actions">
          <el-button type="primary" @click="router.push(practiceRoute(practice.sessionId))">
            继续作答<AppIcon name="arrow-right" :size="16" />
          </el-button>
          <el-button @click="router.push('/practice')">练习记录</el-button>
        </div>
      </div>
      <div v-if="practice.answered.length" class="continue-card__aside">
        <p class="continue-card__aside-title">题号</p>
        <div
          class="continue-card__chips"
          role="img"
          :aria-label="`共 ${practice.answered.length} 题，已答 ${answeredCount} 题`"
        >
          <span v-for="(done, index) in practice.answered" :key="index" :class="{ 'is-done': done }">{{
            index + 1
          }}</span>
        </div>
        <p class="continue-card__legend" aria-hidden="true">
          <span><i class="is-done" />已答</span><span><i />未答</span>
        </p>
      </div>
    </template>
    <template v-else-if="material">
      <div class="continue-card__main">
        <p class="continue-card__label"><AppIcon name="book-open" :size="16" />最近的资料</p>
        <h2 id="continue-card-title" class="continue-card__title" :title="material.title">{{ material.title }}</h2>
        <p class="continue-card__meta">{{ materialMeta }}</p>
        <div class="continue-card__actions">
          <el-button type="primary" @click="router.push(materialRoute(material.id))">
            打开资料<AppIcon name="arrow-right" :size="16" />
          </el-button>
          <el-button :disabled="!materialReady" @click="router.push(summaryRoute(material.id))">看总结</el-button>
          <el-button :disabled="!materialReady" @click="router.push(quizRoute(material.id))">出一套题</el-button>
        </div>
      </div>
      <div class="continue-card__aside">
        <p class="continue-card__aside-title">处理进度</p>
        <ul class="continue-card__checks">
          <li v-for="check in materialChecks" :key="check.label" :class="'is-' + check.state">
            <i aria-hidden="true" /><span>{{ check.label }}</span
            ><b>{{ check.text }}</b>
          </li>
        </ul>
      </div>
    </template>

    <template v-else-if="error">
      <div class="continue-card__main">
        <p class="continue-card__label"><AppIcon name="alert" :size="16" />没有加载出来</p>
        <h2 id="continue-card-title" class="continue-card__title">学习进度暂时无法显示</h2>
        <p class="continue-card__meta">可能是网络或服务暂时不可用，已有的资料和练习不会丢失。</p>
        <div class="continue-card__actions">
          <el-button type="primary" @click="emit('reload')">重新加载</el-button>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="continue-card__main">
        <p class="continue-card__label"><AppIcon name="spark" :size="16" />从第一份资料开始</p>
        <h2 id="continue-card-title" class="continue-card__title">找一份资料，开始学习</h2>
        <p class="continue-card__meta">搜索想学的课程或知识点，也可以上传自己的讲义和笔记。</p>
        <div class="continue-card__actions">
          <el-button type="primary" @click="router.push({ path: '/resources', query: { from: 'home' } })">
            <AppIcon name="search" :size="16" />找资料
          </el-button>
          <el-button @click="router.push(uploadRoute)"> <AppIcon name="upload" :size="16" />上传资料 </el-button>
        </div>
      </div>
      <div class="continue-card__aside">
        <p class="continue-card__aside-title">接下来</p>
        <ol class="continue-card__steps">
          <li><b>1</b>生成总结，把重点理一遍</li>
          <li><b>2</b>出一套题，检验有没有读懂</li>
          <li><b>3</b>答错的题自动进错题本</li>
        </ol>
      </div>
    </template>
  </section>
</template>
<script lang="ts">
export interface ContinuePractice {
  sessionId: number
  sessionName: string
  /** 按答题卡顺序，每题是否已经作答。 */
  answered: boolean[]
}
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import type { MaterialPageItem } from '@/api/modules/material'
import { formatCharacters, formatRelativeDay } from '@/utils/format'
import { formatMaterialType, formatParseStatus } from '@/utils/labels'
import { materialRoute, practiceRoute, quizRoute, summaryRoute, uploadRoute } from './routes'

type CheckState = 'done' | 'running' | 'failed' | 'pending'

/** 首页继续入口：优先练习草稿与最近资料，新用户可查找资料或自行上传。 */
const props = defineProps<{
  loading: boolean
  error: boolean
  practice: ContinuePractice | null
  material: MaterialPageItem | null
}>()
const emit = defineEmits<{ reload: [] }>()
const router = useRouter()

const answeredCount = computed(() => props.practice?.answered.filter(Boolean).length ?? 0)
const materialReady = computed(() => props.material?.parseStatus === 'SUCCESS')
const materialMeta = computed(() => {
  const item = props.material
  if (!item) return ''
  return [formatMaterialType(item.materialType, '文档'), `${formatRelativeDay(item.createdAt)}添加`].join(' · ')
})

const upper = (value?: string) => String(value || '').toUpperCase()
const isParsed = (item: MaterialPageItem) => upper(item.parseStatus) === 'SUCCESS'

interface Check {
  label: string
  state: CheckState
  text: string
}

const parseCheck = (item: MaterialPageItem): Check => {
  const label = '解析内容'
  const status = upper(item.parseStatus)
  if (status === 'SUCCESS') return { label, state: 'done', text: formatCharacters(item.totalCharacters) }
  if (status === 'FAILED') return { label, state: 'failed', text: '解析失败' }
  return { label, state: status === 'PROCESSING' ? 'running' : 'pending', text: formatParseStatus(status) }
}

const summaryCheck = (item: MaterialPageItem): Check => {
  const label = '学习总结'
  const status = upper(item.summaryStatus)
  if (!isParsed(item)) return { label, state: 'pending', text: '解析后可生成' }
  if (status === 'SUCCESS') return { label, state: 'done', text: '已生成' }
  if (status === 'RUNNING' || status === 'PROCESSING') return { label, state: 'running', text: '生成中' }
  if (status === 'FAILED') return { label, state: 'failed', text: '生成失败' }
  return { label, state: 'pending', text: '还没有' }
}

const embeddingCheck = (item: MaterialPageItem): Check => {
  const label = '向量索引'
  const status = upper(item.embeddingStatus)
  const embedded = Number(item.embeddedSegmentCount || 0)
  const total = Number(item.totalSegmentCount || 0)
  if (!isParsed(item)) return { label, state: 'pending', text: '解析后可建立' }
  if (status === 'SUCCESS') return { label, state: 'done', text: total ? `${total} 段` : '已完成' }
  if (status === 'RUNNING') return { label, state: 'running', text: '建立中' }
  if (status === 'FAILED') return { label, state: 'failed', text: '建立失败' }
  if (status === 'PARTIAL_FAILED') return { label, state: 'failed', text: `部分失败 ${embedded} / ${total}` }
  if (embedded > 0) return { label, state: 'pending', text: `已建立 ${embedded} / ${total} 段` }
  return { label, state: 'pending', text: '未建立' }
}

const materialChecks = computed(() =>
  props.material ? [parseCheck, summaryCheck, embeddingCheck].map((check) => check(props.material!)) : []
)
</script>
<style scoped>
.continue-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(200px, 0.72fr);
  align-items: center;
  gap: 20px 32px;
  min-height: 232px;
  padding: 28px 28px 28px 32px;
  border: 1px solid color-mix(in srgb, var(--brand) 16%, var(--line));
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--brand-light) 0%, var(--panel) 72%);
}
/* 没有右侧栏时（加载失败），主内容占满整行。 */
.continue-card__main:last-child {
  grid-column: 1 / -1;
}
.continue-card__main {
  min-width: 0;
}
.continue-card__label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
}
.continue-card__title {
  margin: 10px 0 0;
  overflow: hidden;
  color: var(--text);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.continue-card__meta {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
}
.continue-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}
.continue-card__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.continue-card__aside {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px;
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--panel) 75%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand) 10%, var(--line));
}
.continue-card__aside-title {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
}
.continue-card__chips {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(30px, 1fr));
  gap: 6px;
}
.continue-card__chips span {
  display: grid;
  place-items: center;
  height: 30px;
  border-radius: var(--radius-xs);
  background: var(--panel);
  box-shadow: inset 0 0 0 1px var(--line-strong);
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.continue-card__chips span.is-done {
  background: var(--brand-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand) 30%, transparent);
  color: var(--brand);
  font-weight: 600;
}
.continue-card__legend {
  display: flex;
  gap: 14px;
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.continue-card__legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.continue-card__legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  box-shadow: inset 0 0 0 1px var(--line-strong);
}
.continue-card__legend i.is-done {
  background: var(--brand-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand) 30%, transparent);
}
.continue-card__checks {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.continue-card__checks li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.continue-card__checks i {
  flex: 0 0 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--line-strong);
}
.continue-card__checks span {
  flex: 1;
  color: var(--text-secondary);
}
.continue-card__checks b {
  color: var(--muted);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.continue-card__checks .is-done i {
  background: var(--brand);
}
.continue-card__checks .is-done b {
  color: var(--text);
}
.continue-card__checks .is-running i {
  background: var(--accent);
  animation: zx-pulse 1.6s ease-in-out infinite;
}
.continue-card__checks .is-running b {
  color: var(--accent);
}
.continue-card__checks .is-failed i {
  background: var(--red);
}
.continue-card__checks .is-failed b {
  color: var(--red);
}
.continue-card__steps {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--text-secondary);
  font-size: 13px;
}
.continue-card__steps li {
  display: flex;
  align-items: center;
  gap: 10px;
}
.continue-card__steps b {
  display: grid;
  flex: 0 0 22px;
  place-items: center;
  height: 22px;
  border-radius: 50%;
  background: var(--brand-light);
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
}
.continue-card__sk {
  display: block;
}
.continue-card__sk--label {
  width: 112px;
  height: 14px;
}
.continue-card__sk--title {
  width: min(320px, 85%);
  height: 26px;
  margin-top: 14px;
}
.continue-card__sk--meta {
  width: min(220px, 60%);
  height: 14px;
  margin-top: 12px;
}
.continue-card__sk--button {
  width: 120px;
  height: 40px;
  margin-top: 24px;
  border-radius: var(--radius-sm);
}
.continue-card__sk--aside {
  height: 100%;
  min-height: 120px;
  border-radius: var(--radius);
}
.continue-card__aside:has(.continue-card__sk) {
  padding: 0;
  box-shadow: none;
}
@media (max-width: 1080px) {
  .continue-card {
    grid-template-columns: minmax(0, 1fr);
    padding: 24px;
  }
}
@media (max-width: 760px) {
  .continue-card {
    min-height: 0;
    padding: 20px;
  }
  .continue-card__title {
    font-size: 19px;
    white-space: normal;
  }
}
</style>
