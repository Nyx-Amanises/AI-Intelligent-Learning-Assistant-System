<template>
  <div class="learning-shell" :class="{ 'learning-shell--home': route.path === '/dashboard' }">
    <span ref="scrollSentinel" class="learning-shell__sentinel" aria-hidden="true" />
    <div
      v-if="pageProgress.visible.value"
      class="workspace-progress-bar"
      :style="{ width: `${pageProgress.progress.value}%`, opacity: pageProgress.progress.value === 100 ? 0 : 1 }"
      aria-hidden="true"
    />
    <a class="skip-to-content" href="#workspace-content">跳到主要内容</a>

    <WorkspaceTopbar :scrolled="scrolled" @search="searchVisible = true" @assistant="openAssistant" />

    <main
      id="workspace-content"
      class="workspace-content"
      tabindex="-1"
      @touchstart.passive="handleTouchStart"
      @touchend.passive="handleTouchEnd"
    >
      <WorkspaceSectionNav v-if="currentSection" :section="currentSection" />
      <div class="workspace-view-container">
        <RouterView v-slot="{ Component, route: currentRoute }">
          <Transition name="page">
            <div :key="currentRoute.path" class="workspace-view-page">
              <component :is="Component" @open-assistant="openAssistant" />
            </div>
          </Transition>
        </RouterView>
      </div>
      <footer class="workspace-footer">
        <span>{{ APP_NAME }}</span
        ><span>资料 · 总结 · 练习 · 复盘</span>
      </footer>
    </main>

    <WorkspaceBottomNav />
    <WorkspaceSearchDialog v-model="searchVisible" />
    <AssistantDrawer ref="assistantDrawerRef" v-model="assistantVisible" :show-launcher="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { getProfileApi } from '@/api/modules/auth'
import AssistantDrawer from '@/components/AssistantDrawer.vue'
import WorkspaceBottomNav from '@/components/layout/WorkspaceBottomNav.vue'
import WorkspaceSearchDialog from '@/components/layout/WorkspaceSearchDialog.vue'
import WorkspaceSectionNav from '@/components/layout/WorkspaceSectionNav.vue'
import WorkspaceTopbar from '@/components/layout/WorkspaceTopbar.vue'
import { APP_NAME } from '@/config/brand'
import { findNavigationItem, findSection, primaryLinks } from '@/config/navigation'
import { usePageProgress } from '@/composables/usePageProgress'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const pageProgress = usePageProgress()
const router = useRouter()
const route = useRoute()
const assistantVisible = ref(false)
const assistantDrawerRef = ref<InstanceType<typeof AssistantDrawer> | null>(null)
const searchVisible = ref(false)
const scrollSentinel = ref<HTMLElement | null>(null)
const scrolled = ref(false)
const currentSection = computed(() => findSection(route.path))
let sentinelObserver: IntersectionObserver | null = null

function openAssistant(request?: { practiceSessionId?: number } | Event) {
  searchVisible.value = false
  if (request && 'practiceSessionId' in request && request.practiceSessionId) {
    void assistantDrawerRef.value?.openPracticeAnalysis(request.practiceSessionId)
    return
  }
  assistantVisible.value = true
}

function handleShortcut(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !assistantVisible.value) {
    event.preventDefault()
    searchVisible.value = !searchVisible.value
  }
}

const syncTitle = () => {
  const item = findNavigationItem(route.path)
  document.title = item ? `${item.label} · ${APP_NAME}` : APP_NAME
}
watch(() => route.path, syncTitle)

// 手机上左右滑动切换主导航；从可横向滚动的区域或输入控件开始的滑动不处理。
const SWIPE_PATHS = primaryLinks.map((item) => item.path)
const SWIPE_IGNORE = 'input, textarea, select, [contenteditable], .el-slider, .el-input-number'
let touchStartX = 0
let touchStartY = 0
let touchStartTime = 0
let touchIgnored = false

const startsInScrollableRow = (target: EventTarget | null) => {
  for (let node = target as HTMLElement | null; node && node !== document.body; node = node.parentElement) {
    if (node.scrollWidth > node.clientWidth + 1 && /auto|scroll/.test(getComputedStyle(node).overflowX)) return true
  }
  return false
}
function handleTouchStart(event: TouchEvent) {
  if (event.touches.length !== 1) return
  const target = event.target as HTMLElement | null
  touchIgnored = Boolean(target?.closest(SWIPE_IGNORE)) || startsInScrollableRow(target)
  touchStartX = event.touches[0].clientX
  touchStartY = event.touches[0].clientY
  touchStartTime = Date.now()
}

function handleTouchEnd(event: TouchEvent) {
  if (touchIgnored || event.changedTouches.length !== 1) return
  const deltaX = event.changedTouches[0].clientX - touchStartX
  const deltaY = event.changedTouches[0].clientY - touchStartY
  if (Date.now() - touchStartTime > 400 || Math.abs(deltaX) < 60 || Math.abs(deltaX) < Math.abs(deltaY) * 1.5) return
  const index = SWIPE_PATHS.indexOf(route.path)
  if (index === -1) return
  const next = SWIPE_PATHS[index + (deltaX < 0 ? 1 : -1)]
  if (next) void router.push(next)
}

onMounted(() => {
  window.addEventListener('keydown', handleShortcut)
  syncTitle()
  if (typeof IntersectionObserver !== 'undefined' && scrollSentinel.value) {
    sentinelObserver = new IntersectionObserver(([entry]) => {
      scrolled.value = !entry.isIntersecting
    })
    sentinelObserver.observe(scrollSentinel.value)
  }
  if (userStore.token && route.path !== '/profile') {
    const requestToken = userStore.token
    const previousProfile = userStore.profile
    void getProfileApi()
      .then((response) => {
        if (response.data.data && userStore.token === requestToken && userStore.profile === previousProfile) {
          userStore.setProfile(response.data.data)
        }
      })
      .catch(() => {
        /* 登录失效由共享拦截器统一处理。 */
      })
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleShortcut)
  sentinelObserver?.disconnect()
})
</script>
<style scoped>
.learning-shell {
  --shell-header-height: 64px;
  min-height: 100dvh;
  background: var(--bg);
}
.learning-shell__sentinel {
  position: absolute;
  top: 0;
  left: 0;
  width: 1px;
  height: 1px;
  pointer-events: none;
}
.workspace-content {
  width: 100%;
  max-width: 1280px;
  min-height: calc(100dvh - var(--shell-header-height));
  margin: 0 auto;
  padding: 28px 40px 32px;
  outline: none;
  overflow-x: clip;
}
/* 页内锚点和题号跳转时，目标停在吸顶栏下方而不是被它挡住。 */
.workspace-content :deep([id]) {
  scroll-margin-top: calc(var(--shell-header-height) + 16px);
}
.workspace-view-container {
  position: relative;
  min-height: calc(100dvh - var(--shell-header-height) - 160px);
}
.workspace-view-page {
  width: 100%;
}
/* 换页：旧页面快速淡出并脱离文档流，新页面轻微上移淡入。 */
.page-enter-active {
  animation: zx-rise var(--duration-slow) var(--ease-out) both;
}
.page-leave-active {
  position: absolute;
  inset: 0 0 auto;
  pointer-events: none;
  animation: page-out 0.14s ease-in both;
}
@keyframes page-out {
  to {
    opacity: 0;
  }
}
.workspace-footer {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 56px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.04em;
}
.workspace-progress-bar {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 10000;
  height: 2px;
  background: linear-gradient(90deg, var(--brand), color-mix(in srgb, var(--brand) 55%, var(--accent)));
  pointer-events: none;
  transition:
    width 0.22s var(--ease-out),
    opacity 0.28s ease;
}
.skip-to-content {
  position: fixed;
  top: -80px;
  left: 20px;
  z-index: 4000;
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  background: var(--brand);
  color: var(--on-brand);
}
.skip-to-content:focus {
  top: 10px;
}
@media (max-width: 1180px) {
  .workspace-content {
    padding-inline: 32px;
  }
}
@media (max-width: 760px) {
  .learning-shell {
    --shell-header-height: calc(56px + env(safe-area-inset-top));
    padding-bottom: env(safe-area-inset-bottom);
  }
  .workspace-content {
    padding: 16px 16px 28px;
  }
  .learning-shell--home .workspace-content {
    padding: 0;
  }
  .workspace-content > :deep(.section-nav) {
    display: none;
  }
  .workspace-footer {
    display: none;
  }
}
</style>
