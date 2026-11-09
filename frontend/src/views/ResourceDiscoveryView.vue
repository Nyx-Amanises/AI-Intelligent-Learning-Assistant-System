<template>
  <section class="learning-page resource-view" aria-labelledby="resource-view-title">
    <header class="page-header">
      <h1 id="resource-view-title" class="page-title">找资料</h1>
    </header>

    <form class="resource-search" role="search" @submit.prevent="submitSearch">
      <el-input
        v-model="keywordInput"
        clearable
        maxlength="100"
        placeholder="搜索课程、书名、作者或 ISBN"
        aria-label="搜索课程、教材或复习资料"
        @clear="submitSearch"
      >
        <template #prefix><AppIcon name="search" :size="18" /></template>
        <template #suffix>
          <span v-if="!loading && !loadError" class="resource-search__count" role="status">{{ total }}条</span>
        </template>
      </el-input>
    </form>

    <div class="resource-toolbar">
      <nav class="resource-tabs" aria-label="资源分类">
        <button type="button" :aria-pressed="activeTab === 'featured' && !resourceKind" @click="changeKind()">
          全部
        </button>
        <button
          v-for="kind in browseKinds"
          :key="kind.value"
          type="button"
          :aria-pressed="activeTab === 'featured' && resourceKind === kind.value"
          @click="changeKind(kind.value)"
        >
          {{ kind.label }}
        </button>
        <button type="button" :aria-pressed="activeTab === 'bookmarks'" @click="changeTab('bookmarks')">收藏</button>
        <button
          v-if="userStore.isAdmin"
          type="button"
          :aria-pressed="activeTab === 'admin'"
          @click="changeTab('admin')"
        >
          管理
        </button>
      </nav>
      <div class="resource-toolbar__actions">
        <button
          class="resource-icon-button"
          type="button"
          aria-label="刷新资源"
          :disabled="loading"
          @click="loadResources"
        >
          <AppIcon name="refresh" :size="18" />
        </button>
      </div>
    </div>

    <nav v-if="activeTab !== 'admin'" class="resource-subjects" aria-label="课程">
      <button
        v-for="course in subjects"
        :key="course.value"
        type="button"
        :aria-pressed="subject === course.value"
        @click="changeSubject(course.value)"
      >
        {{ course.label }}
      </button>
    </nav>

    <p v-if="knowledgePoint && activeTab !== 'admin'" class="resource-context">
      <span>补强：{{ knowledgePoint }}</span>
      <RouterLink v-if="practiceTarget" :to="practiceTarget" class="resource-practice-link">继续练习</RouterLink>
      <button type="button" class="resource-icon-button" aria-label="清除知识点" @click="clearKnowledgePoint">
        <AppIcon name="close" :size="16" />
      </button>
    </p>
    <nav v-if="activeTab === 'admin' && userStore.isAdmin" class="resource-status-tabs" aria-label="资源上架状态">
      <button
        v-for="item in statusOptions"
        :key="item.value"
        type="button"
        :aria-pressed="adminStatus === item.value"
        @click="changeStatusFilter(item.value)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div v-if="loading" class="resource-grid" role="status" aria-label="正在加载资源">
      <div v-for="item in 4" :key="item" class="resource-card resource-card--loading" aria-hidden="true">
        <span class="skeleton resource-skeleton-cover" />
        <div class="resource-card__body">
          <span class="skeleton resource-skeleton-title" /><span class="skeleton resource-skeleton-line" /><span
            class="skeleton resource-skeleton-action"
          />
        </div>
      </div>
    </div>
    <EmptyState v-else-if="loadError" class="resource-empty" variant="error" title="暂时无法加载">
      <el-button class="resource-soft-button" @click="loadResources">重新加载</el-button>
    </EmptyState>
    <EmptyState v-else-if="!resources.length" class="resource-empty" icon="book-open" :title="emptyTitle">
      <el-button v-if="hasSearchContext" class="resource-soft-button" @click="clearSearch">清除搜索</el-button>
      <el-button v-else-if="activeTab === 'bookmarks'" class="resource-soft-button" @click="changeTab('featured')"
        >返回全部</el-button
      >
    </EmptyState>
    <ul v-else class="resource-grid" aria-label="资源列表">
      <li v-for="item in resources" :key="item.id" class="resource-card">
        <ResourceCover
          class="resource-card__cover"
          :subject="item.subject"
          :title="item.title"
          :resource-kind="item.resourceKind"
          :cover-url="item.coverUrl"
        />
        <div class="resource-card__body">
          <div class="resource-card__heading">
            <h2>
              <button
                type="button"
                class="resource-card__open"
                :aria-label="`打开：${item.title}`"
                @click="openSource(item)"
              >
                {{ item.title }}
              </button>
            </h2>
            <button
              v-if="activeTab !== 'admin'"
              type="button"
              class="resource-icon-button resource-bookmark"
              :class="{ 'is-active': item.bookmarked }"
              :aria-label="`${item.bookmarked ? '取消收藏' : '收藏'}：${item.title}`"
              :aria-pressed="item.bookmarked"
              :aria-busy="bookmarkPending.has(item.id)"
              :disabled="bookmarkPending.has(item.id)"
              @click="toggleBookmark(item)"
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                :fill="item.bookmarked ? 'currentColor' : 'none'"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M6 3.5h12v17l-6-4-6 4v-17Z" />
              </svg>
            </button>
          </div>
          <p v-if="item.author" class="resource-card__author" :title="item.author">{{ item.author }}</p>
          <p
            v-if="item.publisher || item.sourceName"
            class="resource-card__publisher"
            :title="item.publisher || item.sourceName"
          >
            {{ item.publisher || item.sourceName }}
          </p>
          <el-button
            v-if="activeTab === 'admin' && userStore.isAdmin"
            class="resource-soft-button resource-card__status"
            :loading="statusPending.has(item.id)"
            :disabled="statusPending.has(item.id)"
            @click="changeResourceStatus(item, item.status === 'PUBLISHED' ? 'WITHDRAWN' : 'PUBLISHED')"
          >
            {{ item.status === 'PUBLISHED' ? '下架' : '上架' }}
          </el-button>
        </div>
      </li>
    </ul>

    <div v-if="!loading && !loadError && total > PAGE_SIZE && !showRecommendations" class="resource-pagination">
      <el-pagination
        :current-page="currentPage"
        :page-size="PAGE_SIZE"
        :pager-count="5"
        layout="prev, pager, next"
        :total="total"
        @current-change="changePage"
      />
    </div>

    <el-drawer
      v-model="sourceVisible"
      :size="documentUrl ? 'min(1100px, 100%)' : 'min(600px, 100%)'"
      class="resource-source-drawer"
      :class="{ 'resource-source-drawer--reading': documentUrl && !bookInfoVisible }"
      header-class="resource-source-drawer__header"
      :show-close="false"
      :aria-modal="!noteVisible"
      @focusout.capture="allowNoteFocus"
      @keydown.tab.capture="allowNoteFocus"
      @closed="closeSource"
    >
      <template #header="{ titleId }">
        <div class="resource-source-navigation">
          <button type="button" class="resource-icon-button" aria-label="返回资源列表" @click="sourceVisible = false">
            <MobileIcon name="back" :size="24" />
          </button>
          <h2 :id="titleId">{{ documentUrl && !bookInfoVisible ? '阅读教材' : '资料详情' }}</h2>
          <button
            v-if="documentUrl"
            type="button"
            class="resource-icon-button"
            :aria-label="bookInfoVisible ? '返回教材阅读' : '查看作者与来源'"
            :aria-pressed="bookInfoVisible"
            @click="bookInfoVisible = !bookInfoVisible"
          >
            <svg
              width="21"
              height="21"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="9" />
              <path d="M12 10v7M12 6.5v1" />
            </svg>
          </button>
          <span v-else aria-hidden="true" />
        </div>
      </template>
      <div v-if="detailLoading" class="resource-detail-loading" role="status" aria-label="正在加载来源">
        <span class="skeleton resource-skeleton-title" /><span class="skeleton resource-skeleton-line" /><span
          class="skeleton resource-skeleton-line"
        />
      </div>
      <EmptyState v-else-if="detailError || !detail" class="resource-empty" variant="error" title="暂时无法加载来源">
        <el-button v-if="detailTargetId" class="resource-soft-button" @click="loadDetail(detailTargetId)"
          >重新加载</el-button
        >
      </EmptyState>
      <PdfBookReader
        v-else-if="documentUrl && !bookInfoVisible"
        :key="detail?.id"
        :url="documentUrl"
        :resource-id="detail?.id"
        :initial-page="readerStartPage"
        :prefer-original="foundationBookReading[documentUrl]?.preferOriginal"
        :source-credit="
          isOpenStaxBook ? { label: 'Access for free at openstax.org.', url: 'https://openstax.org/' } : undefined
        "
        :title="detail?.title || ''"
        @progress="readingProgress = $event"
        @page-change="readerStartPage = $event"
      />
      <div v-else-if="detail" class="resource-detail">
        <h3>{{ detail.title }}</h3>
        <p v-if="detail.summary" class="resource-detail__summary">{{ detail.summary }}</p>
        <dl class="resource-attribution">
          <div v-if="detail.isbn">
            <dt>ISBN</dt>
            <dd>{{ detail.isbn }}</dd>
          </div>
          <div v-if="detail.publisher">
            <dt>出版</dt>
            <dd>
              {{ detail.publisher }}<template v-if="detail.publicationYear"> · {{ detail.publicationYear }}</template>
            </dd>
          </div>
          <div v-if="detail.educationLevel">
            <dt>层次</dt>
            <dd>{{ detail.educationLevel }}</dd>
          </div>
          <div v-if="detail.coverageNote">
            <dt>范围</dt>
            <dd>{{ detail.coverageNote }}</dd>
          </div>
          <div>
            <dt>来源</dt>
            <dd>
              <a
                v-if="allowURL(detail.sourceUrl)"
                :href="allowURL(detail.sourceUrl)"
                target="_blank"
                rel="noopener noreferrer"
                >{{ detail.sourceName || '查看来源' }} <AppIcon name="arrow-up-right" :size="14" /></a
              ><span v-else>{{ detail.sourceName || '站内资源' }}</span>
            </dd>
          </div>
          <div>
            <dt>作者</dt>
            <dd>{{ detail.author || '未注明' }}</dd>
          </div>
          <div>
            <dt>{{ detail.licenseCode === 'LINK_ONLY' ? '获取' : '许可' }}</dt>
            <dd>
              {{
                detail.accessMode === 'CATALOG_ONLY'
                  ? '书目信息，全文获取以出版社说明为准'
                  : licenseLabel(detail.licenseCode)
              }}
            </dd>
          </div>
        </dl>
        <div class="resource-license-note">
          <p v-if="detail.licenseNote">{{ detail.licenseNote }}</p>
          <p v-else>{{ detail.resourceType === 'INTERNAL' ? 'AI 辅助编写，可结合来源核对。' : '仅在原站阅读。' }}</p>
          <a
            v-if="allowURL(detail.licenseUrl)"
            :href="allowURL(detail.licenseUrl)"
            target="_blank"
            rel="noopener noreferrer"
            >查看许可 <AppIcon name="arrow-up-right" :size="14"
          /></a>
        </div>
        <details
          v-if="activeTab === 'admin' && userStore.isAdmin && detail.resourceType === 'INTERNAL' && detail.contentText"
          class="resource-admin-content"
        >
          <summary>查看内容 <AppIcon name="chevron-down" :size="16" /></summary>
          <p>{{ detail.contentText }}</p>
        </details>
      </div>
      <template v-if="detail && !detailLoading" #footer>
        <div class="resource-detail-actions">
          <el-button
            v-if="activeTab === 'admin' && userStore.isAdmin"
            class="resource-soft-button"
            :loading="statusPending.has(detail.id)"
            :disabled="statusPending.has(detail.id)"
            @click="changeResourceStatus(detail, detail.status === 'PUBLISHED' ? 'WITHDRAWN' : 'PUBLISHED')"
            >{{ detail.status === 'PUBLISHED' ? '下架' : '上架' }}</el-button
          >
          <el-button
            v-else-if="detail.resourceType === 'INTERNAL'"
            class="resource-soft-button"
            :loading="importingId === detail.id"
            :disabled="importingId !== null || detail.status !== 'PUBLISHED'"
            @click="startLearning(detail)"
            >{{ detail.materialId ? '打开我的资料' : '加入我的资料' }}</el-button
          >
          <a
            v-else-if="allowURL(detail.sourceUrl) && detail.status === 'PUBLISHED'"
            class="resource-link-button"
            :href="allowURL(detail.sourceUrl)"
            target="_blank"
            rel="noopener noreferrer"
            >{{ readingLabel(detail) }} <AppIcon name="arrow-up-right" :size="15"
          /></a>
          <el-button v-else class="resource-soft-button" disabled>暂不可用</el-button>
          <a
            v-if="
              activeTab !== 'admin' &&
              detail.accessMode !== 'CATALOG_ONLY' &&
              detail.status === 'PUBLISHED' &&
              (documentUrl || allowURL(detail.downloadUrl))
            "
            class="resource-download-link resource-icon-button"
            :aria-label="documentUrl ? '下载完整 PDF' : '获取完整文件'"
            :title="documentUrl ? '下载完整 PDF' : '获取完整文件'"
            :href="documentUrl || allowURL(detail.downloadUrl)"
            :download="documentUrl ? `${detail.title}.pdf` : undefined"
            target="_blank"
            rel="noopener noreferrer"
          >
            <svg
              width="21"
              height="21"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M12 3v12m-4-4 4 4 4-4M5 15v5h14v-5" />
            </svg>
          </a>
          <button
            v-if="activeTab !== 'admin'"
            type="button"
            class="resource-icon-button resource-bookmark"
            :class="{ 'is-active': detail.bookmarked }"
            :aria-label="detail.bookmarked ? '取消收藏' : '收藏资源'"
            :aria-pressed="detail.bookmarked"
            :aria-busy="bookmarkPending.has(detail.id)"
            :disabled="bookmarkPending.has(detail.id)"
            @click="toggleBookmark(detail)"
          >
            <svg
              width="21"
              height="21"
              viewBox="0 0 24 24"
              :fill="detail.bookmarked ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M6 3.5h12v17l-6-4-6 4v-17Z" />
            </svg>
          </button>
          <span v-if="documentUrl && !bookInfoVisible" class="resource-reader-assistant-slot" />
        </div>
        <div
          v-if="documentUrl && !bookInfoVisible"
          class="resource-reading-progress"
          role="status"
          aria-label="阅读进度，按当前页码计算"
        >
          已读 {{ readingProgress }}%
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import ResourceCover from '@/components/resources/ResourceCover.vue'
import PdfBookReader from '@/components/resources/PdfBookReader.vue'
import { foundationBookReading } from '@/config/foundationBookReading'
import { useUserStore } from '@/stores/user'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useNoteWorkspace } from '@/composables/useNoteWorkspace'
import { confirmDanger } from '@/utils/confirm'
import { knowledgePointPracticeReason } from '@/utils/knowledgePractice'
import {
  getResourcePageApi,
  getResourceDetailApi,
  getResourceRecommendationsApi,
  getResourceAdminPageApi,
  getResourceAdminDetailApi,
  importResourceApi,
  setResourceBookmarkApi,
  setResourceStatusApi,
  type ResourceVO,
  type ResourceStatus,
  type ResourceKind
} from '@/api/modules/resource'

type ResourceTab = 'featured' | 'bookmarks' | 'admin'
interface MutationOverride<T> {
  value: T
  version: number
  pending: boolean
}
const PAGE_SIZE = 12
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const stringQuery = (value: unknown) => (typeof value === 'string' ? value : '')
const keyword = computed(() => stringQuery(route.query.keyword).trim().slice(0, 100))
const keywordInput = ref(keyword.value)
const knowledgePoint = computed(() => stringQuery(route.query.knowledgePoint).trim())
const subject = computed(() => stringQuery(route.query.subject).trim())
const browseKinds: { value: ResourceKind; label: string }[] = [
  { value: 'TEXTBOOK', label: '教材' },
  { value: 'COURSE_PACK', label: '课程' },
  { value: 'REVIEW_PACK', label: '复习' }
]
const resourceKind = computed(() => browseKinds.find((kind) => kind.value === route.query.resourceKind)?.value)
const accessMode = computed(() => {
  if (route.query.tab === 'admin') return undefined
  return route.query.accessMode === 'OPEN_RESOURCE' ? route.query.accessMode : undefined
})
const subjects = [
  { value: '', label: '全部' },
  { value: '高等数学', label: '高数' },
  { value: '线性代数', label: '线代' },
  { value: '概率论与数理统计', label: '概率' },
  { value: '大学物理', label: '物理' },
  { value: '大学英语', label: '英语' },
  { value: '计算机', label: '计算机' },
  { value: '经济学', label: '经济' },
  { value: '心理学', label: '心理' }
]
const readingLabel = (item: ResourceVO) =>
  item.accessMode === 'CATALOG_ONLY'
    ? '查看书目'
    : item.resourceKind === 'TEXTBOOK'
      ? '阅读教材'
      : item.resourceKind === 'REVIEW_PACK'
        ? '查看复习资料'
        : '进入课程'
const practiceTarget = computed(() => {
  const materialId = Number(route.query.practiceMaterialId)
  return !knowledgePointPracticeReason(knowledgePoint.value) && Number.isSafeInteger(materialId) && materialId > 0
    ? { path: '/quiz', query: { materialId: String(materialId), knowledgePoint: knowledgePoint.value, generate: '1' } }
    : undefined
})
const activeTab = computed<ResourceTab>(() => {
  if (route.query.tab === 'admin' && userStore.isAdmin) return 'admin'
  return route.query.tab === 'bookmarks' ? 'bookmarks' : 'featured'
})
const currentPage = computed(() => {
  if (linkedResourceId.value) return 1
  const page = Number(route.query.page)
  return Number.isSafeInteger(page) && page > 0 ? page : 1
})
const linkedResourceId = computed(() => {
  const value = stringQuery(route.query.resourceId)
  const id = Number(value)
  return /^[1-9]\d*$/.test(value) && Number.isSafeInteger(id) ? id : undefined
})
const linkedBookPage = computed(() => {
  if (!linkedResourceId.value) return undefined
  const value = Number(route.query.page)
  return Number.isSafeInteger(value) && value > 0 ? value : undefined
})
const statusOptions: { value: ResourceStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已上架' },
  { value: 'WITHDRAWN', label: '已下架' }
]
const adminStatus = computed<ResourceStatus>(
  () => statusOptions.find((item) => item.value === route.query.status)?.value || 'DRAFT'
)
const showRecommendations = computed(
  () =>
    activeTab.value === 'featured' &&
    Boolean(knowledgePoint.value) &&
    !keyword.value &&
    !subject.value &&
    !resourceKind.value &&
    !accessMode.value
)
const hasSearchContext = computed(() =>
  Boolean(
    keyword.value ||
    resourceKind.value ||
    accessMode.value ||
    (activeTab.value !== 'admin' && (knowledgePoint.value || subject.value))
  )
)
const emptyTitle = computed(() => {
  if (hasSearchContext.value) return '没有找到相关资源'
  if (activeTab.value === 'bookmarks') return '还没有收藏的资源'
  if (activeTab.value === 'admin') return '暂无这类资源'
  return '资源正在准备中'
})
const resources = ref<ResourceVO[]>([])
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)
const sourceVisible = ref(false)
const detail = ref<ResourceVO | null>(null)
const bookInfoVisible = ref(false)
const readingProgress = ref(0)
const readerStartPage = ref<number>()
const { visible: noteVisible } = useNoteWorkspace()
const allowNoteFocus = (event: Event) => {
  // ElDrawer keeps an internal focus trap even without trap-focus; allow both split panes to receive focus.
  if (noteVisible.value && window.matchMedia('(min-width: 1100px)').matches) event.stopImmediatePropagation()
}
const documentUrl = computed(() => {
  const url = detail.value?.documentUrl
  return url && /^\/open-books\/[a-z0-9-]+\/book\.pdf$/.test(url) ? url : undefined
})
const isOpenStaxBook = computed(() => /^https:\/\/github\.com\/openstax\//i.test(detail.value?.sourceUrl || ''))
const rootScrollLockClass = 'resource-reader-scroll-lock'
watch([sourceVisible, documentUrl], ([visible, url]) => {
  document.documentElement.classList.toggle(rootScrollLockClass, Boolean(visible && url))
})
const detailTargetId = ref<number | null>(null)
const detailLoading = ref(false)
const detailError = ref(false)
const importingId = ref<number | null>(null)
const bookmarkPending = reactive(new Set<number>())
const statusPending = reactive(new Set<number>())
const bookmarkOverrides = new Map<number, MutationOverride<boolean>>()
const statusOverrides = new Map<number, MutationOverride<ResourceStatus>>()
let mutationVersion = 0
const listRequest = useLatestRequest()
const detailRequest = useLatestRequest()
let searchTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false

/** 资源链接只允许无凭据的 HTTPS 地址，站外内容交由原站呈现。 */
const allowURL = (value?: string) => {
  if (!value) return undefined
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && url.hostname && !url.username && !url.password ? url.href : undefined
  } catch {
    return undefined
  }
}

const licenseLabels: Record<string, string> = {
  INTERNAL: '本站编写',
  LINK_ONLY: '原站获取',
  'CC-BY-4.0': 'CC BY 4.0 · 署名',
  'CC-BY-SA-4.0': 'CC BY-SA 4.0 · 署名、相同方式共享',
  'CC-BY-NC-SA-4.0': 'CC BY-NC-SA 4.0 · 署名、非商业、相同方式共享',
  'CC-BY-NC-3.0': 'CC BY-NC 3.0 · 署名、非商业',
  'CC-BY-SA-3.0-US': 'CC BY-SA 3.0 US · 署名、相同方式共享',
  'GFDL-1.2-or-later': 'GNU FDL 1.2 或后续版本 · 自由文档许可',
  'GFDL-1.3-or-later': 'GNU FDL 1.3 或后续版本 · 自由文档许可',
  'CC0-1.0': 'CC0 · 公共领域',
  CC0: 'CC0 · 公共领域'
}
const licenseLabel = (code: string) => licenseLabels[code] || code || '见来源说明'

const cancelSearch = () => {
  if (searchTimer !== undefined) clearTimeout(searchTimer)
  searchTimer = undefined
}

const hydrateResource = (item: ResourceVO, requestVersion: number): ResourceVO => {
  const bookmark = bookmarkOverrides.get(item.id)
  const status = statusOverrides.get(item.id)
  // 只保护在途写入或 GET 发起后发生的变更；写完后新发起的 GET 采用服务器数据。
  return {
    ...item,
    bookmarked: bookmark && (bookmark.pending || bookmark.version > requestVersion) ? bookmark.value : item.bookmarked,
    status: status && (status.pending || status.version > requestVersion) ? status.value : item.status
  }
}

const updateQuery = async (patch: LocationQueryRaw) => {
  cancelSearch()
  const query = { ...route.query, ...patch }
  if (router.resolve({ path: route.path, query }).fullPath === route.fullPath) {
    await loadResources()
  } else {
    await router.replace({ path: route.path, query })
  }
}

const submitSearch = () => updateQuery({ keyword: keywordInput.value.trim() || undefined, page: undefined })
const changeTab = (tab: ResourceTab) => {
  if (tab === 'admin' && !userStore.isAdmin) return
  sourceVisible.value = false
  return updateQuery({
    tab: tab === 'featured' ? undefined : tab,
    resourceKind: undefined,
    accessMode: tab === 'admin' ? undefined : accessMode.value,
    keyword: keywordInput.value.trim() || undefined,
    page: undefined
  })
}
const changeStatusFilter = (status: ResourceStatus) => updateQuery({ status, page: undefined })
const changeKind = (kind?: ResourceKind) => {
  sourceVisible.value = false
  return updateQuery({
    tab: undefined,
    resourceKind: kind,
    keyword: keywordInput.value.trim() || undefined,
    page: undefined
  })
}
const changeSubject = (value: string) =>
  updateQuery({
    subject: value || undefined,
    keyword: keywordInput.value.trim() || undefined,
    knowledgePoint: undefined,
    practiceMaterialId: undefined,
    page: undefined
  })
const changePage = (page: number) => updateQuery({ page: page > 1 ? String(page) : undefined })
const clearKnowledgePoint = () =>
  updateQuery({ knowledgePoint: undefined, practiceMaterialId: undefined, page: undefined })
const clearSearch = () =>
  updateQuery({
    keyword: undefined,
    knowledgePoint: undefined,
    practiceMaterialId: undefined,
    subject: undefined,
    resourceKind: undefined,
    accessMode: undefined,
    page: undefined
  })

const loadResources = async () => {
  if (disposed) return
  if (keywordInput.value.trim() !== keyword.value) {
    await submitSearch()
    return
  }
  const requestId = listRequest.begin()
  const requestVersion = mutationVersion
  loading.value = true
  loadError.value = false
  try {
    if (showRecommendations.value) {
      const response = await getResourceRecommendationsApi({ knowledgePoint: knowledgePoint.value, limit: 3 })
      if (disposed || !listRequest.isLatest(requestId)) return
      resources.value = (response.data.data || []).map((item) => hydrateResource(item, requestVersion))
      total.value = resources.value.length
    } else {
      const response =
        activeTab.value === 'admin' && userStore.isAdmin
          ? await getResourceAdminPageApi({
              keyword: keyword.value || undefined,
              status: adminStatus.value,
              resourceKind: resourceKind.value,
              accessMode: accessMode.value,
              current: currentPage.value,
              size: PAGE_SIZE
            })
          : await getResourcePageApi({
              keyword: keyword.value || knowledgePoint.value || undefined,
              subject: subject.value || undefined,
              resourceKind: resourceKind.value,
              accessMode: accessMode.value,
              bookmarkedOnly: activeTab.value === 'bookmarks',
              current: currentPage.value,
              size: PAGE_SIZE
            })
      if (disposed || !listRequest.isLatest(requestId)) return
      const data = response.data.data
      total.value = data.total || 0
      const lastPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
      if (currentPage.value > lastPage) {
        await updateQuery({ page: lastPage > 1 ? String(lastPage) : undefined })
        return
      }
      resources.value = (data.records || []).map((item) => hydrateResource(item, requestVersion))
    }
  } catch {
    if (!disposed && listRequest.isLatest(requestId)) loadError.value = true
  } finally {
    if (!disposed && listRequest.isLatest(requestId)) loading.value = false
  }
}

const updateBookmark = (id: number, bookmarked: boolean, pending: boolean) => {
  bookmarkOverrides.set(id, { value: bookmarked, version: ++mutationVersion, pending })
  resources.value = resources.value.map((item) => (item.id === id ? { ...item, bookmarked } : item))
  if (detail.value?.id === id) detail.value = { ...detail.value, bookmarked }
}

const reloadAfterRemoval = async (id: number) => {
  const remaining = resources.value.filter((item) => item.id !== id)
  if (remaining.length < resources.value.length) {
    resources.value = remaining
    total.value = Math.max(0, total.value - 1)
  }
  if (!remaining.length && currentPage.value > 1) await changePage(currentPage.value - 1)
  else await loadResources()
}

const toggleBookmark = async (item: ResourceVO) => {
  if (bookmarkPending.has(item.id) || disposed) return
  const previous = item.bookmarked
  bookmarkPending.add(item.id)
  updateBookmark(item.id, !previous, true)
  try {
    await setResourceBookmarkApi(item.id, !previous)
    if (disposed) return
    updateBookmark(item.id, !previous, false)
    if (activeTab.value === 'bookmarks') {
      if (previous) await reloadAfterRemoval(item.id)
      else await loadResources()
    }
  } catch {
    if (disposed) return
    updateBookmark(item.id, previous, false)
    ElMessage.error('收藏未保存，请重试')
  } finally {
    bookmarkPending.delete(item.id)
  }
}

const startLearning = async (item: ResourceVO) => {
  if (disposed || importingId.value !== null || item.resourceType !== 'INTERNAL' || item.status !== 'PUBLISHED') return
  importingId.value = item.id
  const practiceMaterialId = Number(route.query.practiceMaterialId)
  const returnContext = {
    from: 'resources',
    resourceId: String(item.id),
    knowledgePoint: knowledgePoint.value || undefined,
    resourceKeyword: keyword.value || undefined,
    resourceSubject: subject.value || undefined,
    resourceKind: resourceKind.value,
    resourceAccessMode: accessMode.value,
    resourceTab: activeTab.value === 'featured' ? undefined : activeTab.value,
    resourcePage: currentPage.value > 1 ? String(currentPage.value) : undefined,
    resourceStatus: activeTab.value === 'admin' ? adminStatus.value : undefined,
    practiceMaterialId:
      Number.isSafeInteger(practiceMaterialId) && practiceMaterialId > 0 ? String(practiceMaterialId) : undefined,
    resourceFrom: ['home', 'mastery'].includes(stringQuery(route.query.from))
      ? stringQuery(route.query.from)
      : undefined
  }
  try {
    const response = await importResourceApi(item.id)
    if (disposed) return
    const materialId = Number(response.data.data?.materialId)
    if (!Number.isSafeInteger(materialId) || materialId <= 0) throw new Error('invalid material')
    await router.push({ path: '/materials', query: { ...returnContext, materialId: String(materialId) } })
  } catch {
    if (!disposed) ElMessage.error('暂时无法开始学习，请重试')
  } finally {
    if (!disposed) importingId.value = null
  }
}

const loadDetail = async (id: number) => {
  if (disposed) return
  const requestId = detailRequest.begin()
  const requestVersion = mutationVersion
  detailLoading.value = true
  detailError.value = false
  detail.value = null
  try {
    const response =
      activeTab.value === 'admin' && userStore.isAdmin
        ? await getResourceAdminDetailApi(id)
        : await getResourceDetailApi(id)
    if (disposed || !sourceVisible.value || !detailRequest.isLatest(requestId)) return
    detail.value = hydrateResource(response.data.data, requestVersion)
  } catch {
    if (!disposed && detailRequest.isLatest(requestId)) detailError.value = true
  } finally {
    if (!disposed && detailRequest.isLatest(requestId)) detailLoading.value = false
  }
}

const openSource = (item: ResourceVO) => {
  readerStartPage.value = undefined
  bookInfoVisible.value = false
  detailTargetId.value = item.id
  sourceVisible.value = true
  void loadDetail(item.id)
}
const closeSource = () => {
  if (sourceVisible.value) return
  detailRequest.invalidate()
  detail.value = null
  detailTargetId.value = null
  detailLoading.value = false
  readerStartPage.value = undefined
  if (route.path === '/resources' && linkedResourceId.value) {
    const query = { ...route.query }
    delete query.resourceId
    delete query.page
    void router.replace({ path: '/resources', query })
  }
}

const changeResourceStatus = async (item: ResourceVO, status: ResourceStatus) => {
  if (!userStore.isAdmin || disposed || statusPending.has(item.id)) return
  statusPending.add(item.id)
  try {
    if (
      status === 'WITHDRAWN' &&
      !(await confirmDanger('下架后将不再推荐这份资源。', { title: '下架资源', confirmText: '确认下架' }))
    )
      return
    if (!userStore.isAdmin || disposed) return
    await setResourceStatusApi(item.id, { status })
    if (disposed) return
    statusOverrides.set(item.id, { value: status, version: ++mutationVersion, pending: false })
    if (detail.value?.id === item.id) detail.value = { ...detail.value, status }
    ElMessage.success(status === 'PUBLISHED' ? '已上架' : '已下架')
    if (activeTab.value === 'admin' && adminStatus.value !== status) await reloadAfterRemoval(item.id)
    else await loadResources()
  } catch {
    if (!disposed) ElMessage.error('操作未完成，请重试')
  } finally {
    statusPending.delete(item.id)
  }
}

const listKey = computed(() =>
  JSON.stringify({
    tab: activeTab.value,
    keyword: keyword.value,
    knowledgePoint: knowledgePoint.value,
    subject: subject.value,
    resourceKind: resourceKind.value,
    accessMode: accessMode.value,
    status: adminStatus.value,
    page: currentPage.value
  })
)

watch(
  keywordInput,
  (value) => {
    const hadPendingSearch = searchTimer !== undefined
    cancelSearch()
    if (value.trim() === keyword.value) {
      if (hadPendingSearch) void loadResources()
      return
    }
    listRequest.invalidate()
    loading.value = true
    searchTimer = setTimeout(() => {
      void submitSearch()
    }, 360)
  },
  { flush: 'sync' }
)
watch(
  listKey,
  () => {
    cancelSearch()
    keywordInput.value = keyword.value
    void loadResources()
  },
  { immediate: true, flush: 'sync' }
)
watch(
  () => userStore.isAdmin,
  (isAdmin) => {
    if (!isAdmin && route.query.tab === 'admin') {
      sourceVisible.value = false
      closeSource()
      void updateQuery({ tab: undefined, status: undefined, page: undefined })
    }
  },
  { immediate: true }
)
watch(sourceVisible, (visible) => {
  if (!visible) detailRequest.invalidate()
})
watch(activeTab, () => {
  sourceVisible.value = false
  closeSource()
})
watch(
  [linkedResourceId, linkedBookPage],
  ([id], [previousId]) => {
    if (!id) {
      if (previousId) sourceVisible.value = false
      return
    }
    readerStartPage.value = linkedBookPage.value
    bookInfoVisible.value = false
    if (sourceVisible.value && detailTargetId.value === id) return
    detailTargetId.value = id
    sourceVisible.value = true
    void loadDetail(id)
  },
  { immediate: true }
)

onUnmounted(() => {
  document.documentElement.classList.remove(rootScrollLockClass)
  disposed = true
  cancelSearch()
  listRequest.invalidate()
  detailRequest.invalidate()
})
</script>

<style scoped>
.resource-view {
  max-width: 1120px;
  margin-inline: auto;
}
.resource-search {
  margin-bottom: 12px;
}
.resource-search :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 12px;
}
.resource-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.resource-tabs,
.resource-status-tabs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.resource-toolbar__actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 2px;
}
.resource-search__count {
  flex-shrink: 0;
  padding-left: 4px;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.resource-tabs button,
.resource-status-tabs button {
  min-height: 44px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.resource-tabs button[aria-pressed='true'] {
  background: var(--panel);
  color: var(--text);
  font-weight: 600;
}
.resource-tabs button {
  min-width: 44px;
  padding-inline: 10px;
}
.resource-status-tabs {
  margin: 0 0 14px;
}
.resource-status-tabs button {
  padding-inline: 14px;
  font-size: 13px;
}
.resource-status-tabs button[aria-pressed='true'] {
  color: var(--blue);
  background: var(--blue-soft);
}
.resource-subjects {
  display: flex;
  flex-wrap: nowrap;
  overflow-x: auto;
  scrollbar-width: none;
  gap: 3px;
  margin: -4px 0 14px;
}
.resource-subjects button {
  flex-shrink: 0;
  min-width: 44px;
  min-height: 44px;
  padding: 0 9px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.resource-subjects button[aria-pressed='true'] {
  color: var(--blue);
  background: var(--blue-soft);
}
.resource-card__author,
.resource-card__publisher {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.resource-card__author {
  color: var(--text-secondary);
}
.resource-card__publisher {
  margin-top: 2px;
  color: var(--muted);
}
.resource-practice-link {
  flex-shrink: 0;
  margin-left: auto;
  padding: 12px 2px;
  color: inherit;
  font-weight: 500;
}
.resource-download-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  padding: 0 6px;
  color: var(--blue);
  font-size: 13px;
  text-decoration: none;
}
.resource-icon-button {
  display: inline-flex;
  flex: 0 0 44px;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.resource-icon-button:hover:not(:disabled) {
  background: var(--bg-secondary);
}
.resource-icon-button:disabled {
  opacity: 0.45;
  cursor: default;
}
.resource-bookmark.is-active {
  color: var(--brand);
}
.resource-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 0 14px;
  padding: 0 4px 0 14px;
  border-radius: 12px;
  background: var(--blue-soft);
  color: var(--blue);
  font-size: 13px;
  line-height: 1.6;
}
.resource-context > span {
  min-width: 0;
  overflow-wrap: anywhere;
}
.resource-context .resource-icon-button {
  color: inherit;
}
.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.resource-card {
  position: relative;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: start;
  column-gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--panel);
}
.resource-card__cover {
  width: 72px;
  margin-top: 3px;
}
.resource-card__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.resource-card__heading {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.resource-card__heading h2 {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 14px;
}
.resource-card__open {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  width: 100%;
  overflow: hidden;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-weight: 600;
  line-height: 1.5;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}
.resource-card__open::after {
  position: absolute;
  inset: 0;
  border-radius: 14px;
  content: '';
}
.resource-card__open:focus-visible {
  outline: none;
}
.resource-card__open:focus-visible::after {
  outline: 2px solid var(--blue);
  outline-offset: 2px;
}
.resource-card__heading .resource-bookmark {
  position: relative;
  z-index: 1;
  margin-top: -9px;
  margin-bottom: -8px;
  margin-right: -6px;
}
.resource-card__status {
  position: relative;
  z-index: 1;
  align-self: flex-start;
  margin-top: 6px;
}
:root .el-button.resource-soft-button {
  --el-button-text-color: var(--blue);
  --el-button-bg-color: var(--blue-soft);
  --el-button-border-color: transparent;
  --el-button-hover-text-color: var(--blue);
  --el-button-hover-bg-color: color-mix(in srgb, var(--blue) 15%, var(--panel));
  --el-button-hover-border-color: transparent;
  --el-button-active-text-color: var(--blue);
  --el-button-active-bg-color: color-mix(in srgb, var(--blue) 20%, var(--panel));
  --el-button-active-border-color: transparent;
  height: 44px;
  min-height: 44px;
  margin: 0;
  padding-inline: 16px;
  border-radius: 10px;
  font-size: 13px;
  box-shadow: none;
}
.resource-link-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 44px;
  padding: 0 16px;
  border-radius: 10px;
  background: var(--blue-soft);
  color: var(--blue);
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
}
.resource-link-button:hover {
  background: color-mix(in srgb, var(--blue) 15%, var(--panel));
}
.resource-view button:not(.resource-card__open):focus-visible,
.resource-source-drawer button:focus-visible,
.resource-link-button:focus-visible,
.resource-attribution a:focus-visible,
.resource-license-note a:focus-visible {
  outline: 2px solid var(--blue);
  outline-offset: 2px;
}
.resource-card--loading {
  min-height: 140px;
}
.resource-card--loading .resource-card__body {
  gap: 13px;
  padding-top: 4px;
}
.resource-skeleton-cover {
  width: 72px;
  height: 96px;
  border-radius: 7px;
}
.resource-skeleton-title {
  display: block;
  width: 70%;
  height: 20px;
}
.resource-skeleton-line {
  display: block;
  width: 94%;
  height: 13px;
}
.resource-skeleton-action {
  display: block;
  width: 92px;
  height: 38px;
  margin-top: 6px;
  border-radius: 10px;
}
.resource-empty :deep(.empty-state__icon) {
  background: var(--blue-soft);
  color: var(--blue);
  box-shadow: none;
}
.resource-empty :deep(.empty-state__title) {
  margin-bottom: 16px;
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 500;
}
.resource-pagination {
  display: flex;
  justify-content: center;
  padding-block: 24px;
}
:global(:root .el-drawer__header.resource-source-drawer__header) {
  padding: 6px 12px;
  margin-bottom: 0;
}
:global(:root .resource-source-drawer) {
  max-width: 100%;
  max-height: 100dvh;
}
@media (min-width: 1100px) {
  :global(body.note-split-open .resource-source-drawer) {
    right: 460px;
    max-width: calc(100% - 460px);
  }
}
:global(html.resource-reader-scroll-lock) {
  overflow: hidden;
  scrollbar-gutter: auto;
}
:global(.resource-source-drawer--reading .el-drawer__body) {
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
:global(.resource-source-drawer--reading .el-drawer__footer) {
  padding: 6px 16px max(6px, env(safe-area-inset-bottom));
}
.resource-source-navigation {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 44px;
  align-items: center;
  width: 100%;
}
.resource-source-navigation h2 {
  min-width: 0;
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}
.resource-source-navigation .resource-icon-button {
  color: var(--text);
}
.resource-detail-loading {
  display: grid;
  gap: 20px;
  padding-top: 10px;
}
.resource-detail h3 {
  margin: 0 0 12px;
  color: var(--text);
  font-size: 19px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.resource-detail__summary {
  margin: 0 0 24px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.resource-attribution {
  display: grid;
  gap: 18px;
  margin: 0;
}
.resource-attribution > div {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 14px;
  font-size: 14px;
  line-height: 1.75;
}
.resource-attribution dt {
  color: var(--muted);
}
.resource-attribution dd {
  margin: 0;
  color: var(--text);
  overflow-wrap: anywhere;
}
.resource-attribution a,
.resource-license-note a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  margin-block: -9px;
  color: var(--blue);
  text-decoration: none;
}
.resource-license-note {
  margin-top: 24px;
  padding: 16px;
  border-radius: 12px;
  background: var(--bg);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.resource-license-note p {
  margin: 0;
  white-space: pre-wrap;
}
.resource-license-note p + p {
  margin-top: 8px;
}
.resource-license-note a {
  margin-top: 4px;
}
.resource-detail-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 640px;
  margin-inline: auto;
}
.resource-detail-actions > .resource-soft-button,
.resource-detail-actions > .resource-link-button {
  flex: 1;
}
.resource-reader-assistant-slot {
  display: flex;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
}
.resource-reading-progress {
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.4;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.resource-admin-content {
  margin-top: 20px;
  border-top: 1px solid var(--line);
}
.resource-admin-content summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  color: var(--text-secondary);
  font-size: 14px;
  list-style: none;
  cursor: pointer;
}
.resource-admin-content summary::-webkit-details-marker {
  display: none;
}
.resource-admin-content summary:focus-visible {
  outline: 2px solid var(--blue);
  outline-offset: 2px;
}
.resource-admin-content[open] summary :deep(.app-icon) {
  transform: rotate(180deg);
}
.resource-admin-content p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 760px) {
  .resource-view > .page-header {
    display: none;
  }
  .resource-grid {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .resource-card {
    padding: 12px;
  }
  .resource-search {
    margin-bottom: 10px;
  }
  .resource-tabs button {
    padding-inline: 10px;
  }
  .resource-detail h3 {
    font-size: 17px;
  }
}
@media (min-width: 761px) {
  .resource-view {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(300px, 520px);
    column-gap: 24px;
  }
  .resource-view > * {
    grid-column: 1 / -1;
  }
  .resource-view > .page-header {
    grid-column: 1;
    align-self: center;
    margin-bottom: 20px;
  }
  .resource-view > .resource-search {
    grid-column: 2;
    margin-bottom: 20px;
  }
  .resource-toolbar {
    padding: 5px 10px;
    border: 1px solid var(--line);
    border-radius: 16px;
    background: var(--panel);
  }
  .resource-tabs button[aria-pressed='true'] {
    background: var(--bg-secondary);
  }
  .resource-subjects {
    flex-wrap: wrap;
    gap: 4px;
    margin: 0 0 18px;
    overflow: visible;
  }
  .resource-grid {
    gap: 16px;
  }
  .resource-card {
    min-height: 142px;
    padding: 16px;
    border-radius: 18px;
  }
  .resource-card:hover {
    border-color: var(--line-strong);
  }
  .resource-card__heading h2 {
    font-size: 15px;
  }
  .resource-card__author,
  .resource-card__publisher {
    font-size: 13px;
  }
  .resource-card__open::after {
    border-radius: 18px;
  }
}
@media (min-width: 1180px) {
  .resource-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
