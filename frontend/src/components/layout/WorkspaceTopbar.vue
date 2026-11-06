<template>
  <header class="topbar" :class="{ 'is-scrolled': scrolled, 'topbar--home': isHome }">
    <div class="topbar__inner">
      <RouterLink to="/dashboard" class="topbar__brand" :aria-label="APP_NAME + '，返回首页'">
        <BrandLogo aria-hidden="true" />
        <span class="topbar__brand-text" aria-hidden="true">知序</span>
      </RouterLink>

      <nav ref="navRef" class="topbar__nav" aria-label="主要导航">
        <RouterLink
          v-for="item in primaryLinks"
          :key="item.key"
          :to="item.path"
          class="topbar__nav-item"
          :class="{ 'is-active': activeKey === item.key }"
          :aria-current="activeKey === item.key ? 'page' : undefined"
          >{{ item.label }}</RouterLink
        >
        <span class="topbar__nav-indicator" :style="indicatorStyle" aria-hidden="true" />
      </nav>

      <div class="topbar__actions">
        <button type="button" class="topbar__search" aria-label="搜索资料或页面（Ctrl K）" @click="emit('search')">
          <AppIcon name="search" :size="18" />
          <span>搜索</span>
          <kbd>Ctrl K</kbd>
        </button>
        <button
          type="button"
          class="topbar__assistant"
          :aria-label="'打开' + ASSISTANT_NAME"
          @click="emit('assistant')"
        >
          <AppIcon name="spark" :size="18" />
          <span>{{ ASSISTANT_NAME }}</span>
        </button>
        <el-dropdown class="topbar__theme" trigger="click" placement="bottom-end" @command="handleThemeCommand">
          <button
            type="button"
            class="topbar__icon-button"
            :aria-label="'界面主题：' + currentThemeOption.label"
            :title="'界面主题：' + currentThemeOption.label"
          >
            <AppIcon :name="currentThemeOption.icon" :size="18" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="opt in themeOptions" :key="opt.key" :command="opt.key">
                <span class="theme-option">
                  <span class="theme-option__swatch" :style="{ backgroundColor: opt.color }" />
                  <span class="theme-option__text"
                    ><strong>{{ opt.label }}</strong
                    ><small>{{ opt.hint }}</small></span
                  >
                  <AppIcon v-if="currentTheme === opt.key" name="check" :size="16" class="theme-option__check" />
                </span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <WorkspaceAccountMenu />
      </div>
    </div>
    <div class="mobile-topbar" :class="{ 'mobile-topbar--home': isHome }">
      <button
        v-if="isHome"
        type="button"
        class="mobile-topbar__button mobile-topbar__mark"
        :aria-label="'打开' + ASSISTANT_NAME"
        @click="emit('assistant')"
      >
        <MobileIcon name="mark" :size="25" />
      </button>
      <template v-else>
        <RouterLink :to="mobileBack.path" class="mobile-topbar__button" :aria-label="mobileBack.label">
          <MobileIcon :name="mobileBack.path === '/dashboard' ? 'down' : 'back'" :size="24" />
        </RouterLink>
        <nav v-if="showLearningTabs" class="mobile-topbar__tabs" aria-label="学习与偏好">
          <RouterLink
            to="/analytics"
            :class="{ 'is-active': route.path === '/analytics' }"
            :aria-current="route.path === '/analytics' ? 'page' : undefined"
            >我的学习</RouterLink
          >
          <RouterLink
            to="/profile"
            :class="{ 'is-active': route.path === '/profile' }"
            :aria-current="route.path === '/profile' ? 'page' : undefined"
            >学习偏好</RouterLink
          >
        </nav>
        <span v-else class="mobile-topbar__title">{{ mobileTitle }}</span>
        <button
          v-if="isMaterialLibrary"
          type="button"
          class="mobile-topbar__button"
          aria-label="搜索资料或页面"
          @click="emit('search')"
        >
          <MobileIcon name="search" :size="23" />
        </button>
        <button
          v-else-if="showMobileAssistant"
          type="button"
          class="mobile-topbar__button"
          :aria-label="'打开' + ASSISTANT_NAME"
          @click="emit('assistant')"
        >
          <MobileIcon name="assistant" :size="23" />
        </button>
        <span v-else aria-hidden="true" />
      </template>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import WorkspaceAccountMenu from '@/components/layout/WorkspaceAccountMenu.vue'
import { APP_NAME, ASSISTANT_NAME } from '@/config/brand'
import { activeNavigationKey, findNavigationItem, primaryLinks } from '@/config/navigation'
import { useSlidingIndicator } from '@/composables/useSlidingIndicator'
import { useTheme, type ThemeMode } from '@/composables/useTheme'

/** 顶栏：品牌、主导航（当前项背后有跟随滑动的底色）、搜索、学习助手、主题和账户菜单。 */
defineProps<{ scrolled: boolean }>()
const emit = defineEmits<{ search: []; assistant: [] }>()

const route = useRoute()
const isHome = computed(() => route.path === '/dashboard')
const isMaterialLibrary = computed(
  () =>
    route.path === '/materials' &&
    (route.query.view === 'library' ||
      route.query.keyword !== undefined ||
      route.query.materialId !== undefined ||
      route.query.action === 'upload')
)
const showLearningTabs = computed(
  () =>
    (route.path === '/analytics' && !['overview', 'calendar'].includes(String(route.query.view))) ||
    (route.path === '/profile' && route.query.view !== 'account')
)
const showMobileAssistant = computed(() =>
  ['/materials', '/summary', '/quiz', '/practice', '/wrong-questions', '/analytics', '/mastery'].includes(route.path)
)
const mobileTitle = computed(() => {
  if (route.path === '/materials') return isMaterialLibrary.value ? '资料库' : '我的内容'
  if (route.path === '/analytics') {
    if (route.query.view === 'overview') return '概览'
    if (route.query.view === 'calendar') return '学习日历'
  }
  return findNavigationItem(route.path)?.label || APP_NAME
})
const mobileBack = computed(() => {
  if (isMaterialLibrary.value || route.path === '/summary') return { path: '/materials', label: '返回我的内容' }
  if (route.path === '/analytics' && !showLearningTabs.value) return { path: '/analytics', label: '返回我的学习' }
  if (route.path === '/mastery') return { path: '/analytics', label: '返回我的学习' }
  if (
    (route.path === '/profile' && route.query.view === 'account') ||
    ['/ai-config', '/ai-tasks', '/rag-eval'].includes(route.path)
  ) {
    return { path: '/profile', label: '返回学习偏好' }
  }
  if (route.path === '/practice' || route.path === '/wrong-questions') return { path: '/quiz', label: '返回题集' }
  return { path: '/dashboard', label: '返回首页' }
})
const navRef = ref<HTMLElement | null>(null)
const activeKey = computed(() => activeNavigationKey(route.path))
const { indicatorStyle } = useSlidingIndicator(navRef, '.topbar__nav-item.is-active', activeKey)
const { currentTheme, themeOptions, currentThemeOption, setTheme } = useTheme()

const handleThemeCommand = (command: string) => {
  setTheme(command as ThemeMode)
  ElMessage.success(`已切换到${currentThemeOption.value.label}主题`)
}
</script>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  height: var(--shell-header-height);
  border-bottom: 1px solid transparent;
  background: var(--topbar-bg);
  backdrop-filter: saturate(1.4) blur(14px);
  -webkit-backdrop-filter: saturate(1.4) blur(14px);
  transition:
    border-color var(--duration) ease,
    box-shadow var(--duration) ease;
}
.topbar.is-scrolled {
  border-bottom-color: var(--line);
  box-shadow: 0 6px 20px -14px rgb(29 42 36 / 30%);
}
.topbar__inner {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 40px;
}
.topbar__brand {
  display: flex;
  flex: 0 0 auto;
  border-radius: var(--radius-xs);
}
.topbar__brand :deep(.brand-logo) {
  width: 104px;
}
.topbar__brand-text {
  display: none;
}
.topbar__nav {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-right: auto;
}
.topbar__nav-item {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 500;
  white-space: nowrap;
  transition: color var(--duration-fast) ease;
}
.topbar__nav-item:hover {
  color: var(--text);
}
.topbar__nav-item.is-active {
  color: var(--brand);
}
.topbar__nav-indicator {
  position: absolute;
  top: 0;
  left: 0;
  border-radius: var(--radius-sm);
  background: var(--brand-soft);
  pointer-events: none;
  transition:
    transform var(--duration-slow) var(--ease-out),
    width var(--duration-slow) var(--ease-out),
    opacity var(--duration-fast) ease;
}
.topbar__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.topbar__search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 196px;
  height: 38px;
  padding: 0 8px 0 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--muted);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) ease,
    background-color var(--duration-fast) ease;
}
.topbar__search span {
  flex: 1;
  text-align: left;
}
.topbar__search:hover {
  border-color: var(--el-border-color-hover);
  color: var(--text-secondary);
}
.topbar__search kbd {
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: var(--radius-xs);
  background: var(--panel-hover);
  font: inherit;
  font-size: 11px;
  line-height: 20px;
}
.topbar__assistant {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 14px 0 12px;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--brand-light);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand) 16%, transparent);
  color: var(--brand);
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.topbar__assistant:hover {
  background: var(--brand-soft);
}
.topbar__icon-button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.topbar__icon-button:hover {
  color: var(--text);
  background: var(--bg-secondary);
}
.theme-option {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
  padding: 2px 0;
}
.theme-option__swatch {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px var(--line-strong);
}
.theme-option__text {
  display: grid;
  flex: 1;
  line-height: 1.45;
}
.theme-option__text strong {
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
}
.theme-option__text small {
  color: var(--muted);
  font-size: 12px;
}
.theme-option__check {
  color: var(--brand);
}
.mobile-topbar {
  display: none;
}
@media (max-width: 1180px) {
  .topbar__inner {
    gap: 24px;
    padding: 0 32px;
  }
  .topbar__search {
    width: 38px;
    padding: 0;
    justify-content: center;
    border-color: transparent;
    background: transparent;
  }
  .topbar__search:hover {
    border-color: transparent;
    background: var(--bg-secondary);
  }
  .topbar__search span,
  .topbar__search kbd {
    display: none;
  }
}
@media (max-width: 900px) {
  .topbar__inner {
    gap: 16px;
  }
  .topbar__nav-item {
    padding: 0 10px;
  }
  .topbar__assistant {
    width: 38px;
    padding: 0;
    justify-content: center;
  }
  .topbar__assistant span {
    display: none;
  }
}
@media (max-width: 760px) {
  .topbar {
    padding-top: env(safe-area-inset-top);
    background: var(--topbar-bg);
    color: var(--text);
    border: 0;
    box-shadow: none;
  }
  .topbar__inner {
    display: none;
  }
  .mobile-topbar {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) 44px;
    align-items: center;
    height: 56px;
    padding: 0 12px;
  }
  .mobile-topbar__button {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    padding: 0;
    border: 0;
    border-radius: 50%;
    background: transparent;
    color: inherit;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }
  .mobile-topbar__button:active {
    opacity: 0.55;
  }
  .mobile-topbar__title {
    overflow: hidden;
    font-size: 18px;
    font-weight: 600;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .mobile-topbar__tabs {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    justify-self: center;
    width: min(220px, 100%);
    padding: 3px;
    border-radius: 22px;
    background: var(--bg-secondary);
  }
  .mobile-topbar__tabs a {
    display: grid;
    place-items: center;
    min-height: 33px;
    border-radius: 18px;
    color: var(--muted);
    font-size: 15px;
    font-weight: 600;
    white-space: nowrap;
  }
  .mobile-topbar__tabs a.is-active {
    background: var(--panel);
    color: var(--text);
    box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
  }
  .topbar.topbar--home {
    background: transparent;
    color: #222;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
  .mobile-topbar--home {
    padding: 0 16px;
  }
  .mobile-topbar__mark {
    width: 38px;
    height: 38px;
    border-radius: 15px;
    background: #ffdab5;
    box-shadow: 0 2px 10px rgb(53 48 40 / 8%);
  }
}
</style>
