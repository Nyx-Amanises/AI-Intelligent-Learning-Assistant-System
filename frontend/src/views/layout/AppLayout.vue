<template>
  <div class="learning-shell">
    <div
      v-if="pageProgress.visible.value"
      class="workspace-progress-bar"
      :style="{ width: `${pageProgress.progress.value}%`, opacity: pageProgress.progress.value === 100 ? 0 : 1 }"
      aria-hidden="true"
    />
    <a class="skip-to-content" href="#workspace-content">跳到主要内容</a>

    <header class="workspace-topbar">
      <div class="workspace-topbar__inner">
        <RouterLink to="/dashboard" class="workspace-topbar__brand" :aria-label="APP_NAME + '，返回首页'">
          <BrandLogo aria-hidden="true" />
        </RouterLink>

        <nav class="workspace-primary-nav" aria-label="主要导航">
          <RouterLink
            v-for="item in primaryNavigationItems"
            :key="item.path"
            :to="item.path"
            class="workspace-primary-nav__item"
            :class="{ 'is-active': item.activePaths.includes(route.path) }"
            :aria-current="item.activePaths.includes(route.path) ? 'page' : undefined"
            >{{ item.label }}</RouterLink
          >
        </nav>

        <div class="workspace-topbar__actions">
          <button
            class="workspace-search-trigger"
            type="button"
            aria-label="搜索学习资料（Ctrl K）"
            @click="openSearch"
          >
            <AppIcon name="search" :size="21" />
            <span>搜索</span>
            <kbd>Ctrl K</kbd>
          </button>

          <button
            type="button"
            class="workspace-assistant-trigger"
            :aria-label="'打开' + ASSISTANT_NAME"
            @click="openAssistant"
          >
            <AppIcon name="spark" :size="21" />
            <span>{{ ASSISTANT_NAME }}</span>
          </button>

          <el-dropdown class="workspace-theme__dropdown" trigger="click" @command="handleThemeCommand">
            <button
              type="button"
              class="workspace-theme-trigger"
              :aria-label="'当前主题：' + currentThemeOption.label + '，点击切换风格'"
              :title="'主题风格：' + currentThemeOption.label"
            >
              <AppIcon :name="currentThemeOption.icon" :size="18" />
            </button>
            <template #dropdown>
              <el-dropdown-menu class="workspace-theme-menu">
                <el-dropdown-item
                  v-for="opt in themeOptions"
                  :key="opt.key"
                  :command="opt.key"
                  :class="{ 'is-active': currentTheme === opt.key }"
                >
                  <span class="theme-menu-item">
                    <span class="theme-menu-item__dot" :style="{ backgroundColor: opt.color }"></span>
                    <span class="theme-menu-item__label">{{ opt.label }}</span>
                    <AppIcon v-if="currentTheme === opt.key" name="check" :size="16" class="theme-menu-item__check" />
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <div class="workspace-account">
            <RouterLink to="/profile" class="workspace-user" :aria-label="displayName + '，个人信息'" title="个人信息">
              <img
                v-if="avatarSrc && !avatarLoadFailed"
                :src="avatarSrc"
                alt=""
                class="workspace-user__avatar"
                @error="avatarLoadFailed = true"
              />
              <span v-else class="workspace-user__avatar">{{ avatarText }}</span>
            </RouterLink>
            <el-dropdown class="workspace-account__dropdown" trigger="click" @command="handleUserCommand">
              <button type="button" class="workspace-account__menu" aria-label="账户菜单" title="账户菜单">
                <AppIcon name="chevron-down" :size="16" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                  <el-dropdown-item command="avatar" :disabled="avatarUploading">{{
                    avatarUploading ? '正在上传…' : '更换头像'
                  }}</el-dropdown-item>
                  <el-dropdown-item command="settings">模型与设置</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <el-dropdown class="workspace-more__dropdown" trigger="click" @command="handleMoreCommand">
            <button
              type="button"
              class="workspace-more-trigger"
              :class="{ 'is-active': moreNavigationActive }"
              aria-label="更多功能"
              title="更多功能"
            >
              <AppIcon name="menu" :size="20" />
            </button>
            <template #dropdown>
              <el-dropdown-menu class="workspace-more-menu">
                <el-dropdown-item
                  v-for="item in visibleMoreNavigationItems"
                  :key="item.path"
                  :command="item.path"
                  :divided="item.divided"
                  :class="{ 'is-current': route.path === item.path }"
                >
                  <span class="workspace-more-item">
                    <AppIcon :name="item.icon" :size="16" />
                    <span>{{ item.label }}</span>
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <input
            ref="avatarInputRef"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            class="visually-hidden"
            tabindex="-1"
            aria-hidden="true"
            @change="handleAvatarChange"
          />
        </div>
      </div>
    </header>

    <main
      id="workspace-content"
      class="workspace-content"
      :class="{ 'workspace-content--home': isHome }"
      tabindex="-1"
      @touchstart.passive="handleTouchStart"
      @touchend.passive="handleTouchEnd"
    >
      <div class="workspace-view-container">
        <RouterView v-slot="{ Component, route: currentRoute }">
          <Transition :name="transitionName">
            <div :key="currentRoute.path" class="workspace-view-page">
              <component :is="Component" @open-assistant="openAssistant" />
            </div>
          </Transition>
        </RouterView>
      </div>
      <footer class="workspace-footer">
        <span>{{ APP_NAME }}</span
        ><span>让每一次学习，都有所收获。</span>
      </footer>
    </main>

    <nav class="workspace-bottom-nav" aria-label="移动端主要导航">
      <RouterLink
        v-for="item in mobileNavigationItems"
        :key="item.path"
        :to="item.path"
        class="workspace-bottom-nav__item"
        :class="{ 'is-active': item.activePaths.includes(route.path) }"
        :aria-current="item.activePaths.includes(route.path) ? 'page' : undefined"
      >
        <AppIcon :name="item.icon" :size="23" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <el-dialog
      v-model="searchVisible"
      title="搜索学习资料"
      width="520px"
      class="workspace-search-dialog"
      @opened="searchInputRef?.focus()"
    >
      <form @submit.prevent="searchMaterials">
        <label for="workspace-search-input" class="workspace-search-label">输入资料名称或关键词</label>
        <el-input
          id="workspace-search-input"
          ref="searchInputRef"
          v-model="searchKeyword"
          placeholder="例如：计算机网络、数据结构…"
          clearable
          size="large"
        />
        <p class="workspace-search-help">在你的资料中查找，继续上一次的学习。</p>
        <div class="workspace-search-actions">
          <el-button @click="searchVisible = false">取消</el-button
          ><el-button type="primary" native-type="submit">搜索资料</el-button>
        </div>
      </form>
    </el-dialog>

    <AssistantDrawer v-model="assistantVisible" :show-launcher="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getProfileApi, uploadAvatarApi } from '@/api/modules/auth'
import AssistantDrawer from '@/components/AssistantDrawer.vue'
import AppIcon from '@/components/AppIcon.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import { APP_NAME, ASSISTANT_NAME } from '@/config/brand'
import {
  mobileNavigationItems,
  moreNavigationItems,
  navigationItems,
  primaryNavigationItems
} from '@/config/navigation'
import { useUserStore } from '@/stores/user'
import { resolveAvatarUrl } from '@/utils/avatar'
import { usePageProgress } from '@/composables/usePageProgress'
import { useTheme, type ThemeMode } from '@/composables/useTheme'
import { getErrorMessage } from '@/utils/errorMessage'

const userStore = useUserStore()
const pageProgress = usePageProgress()
const { currentTheme, themeOptions, currentThemeOption, setTheme } = useTheme()
const router = useRouter()
const route = useRoute()
const assistantVisible = ref(false)
const searchVisible = ref(false)
const searchKeyword = ref('')
const searchInputRef = ref<{ focus: () => void }>()
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const avatarLoadFailed = ref(false)
const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')
const avatarText = computed(() => Array.from(displayName.value)[0]?.toUpperCase() || '学')
const currentPage = computed(() => navigationItems.find((item) => item.path === route.path) || navigationItems[0])
const avatarSrc = computed(() => resolveAvatarUrl(userStore.profile?.avatarUrl))
const isHome = computed(
  () => route.name === 'dashboard' || route.path === '/dashboard' || route.path === '/' || route.path === ''
)
const visibleMoreNavigationItems = computed(() =>
  moreNavigationItems.filter((item) => !item.adminOnly || userStore.isAdmin)
)
const moreNavigationActive = computed(() => visibleMoreNavigationItems.value.some((item) => item.path === route.path))

const ROUTE_ORDER: Record<string, number> = {
  '/dashboard': 10,
  '/materials': 20,
  '/summary': 25,
  '/rag-eval': 28,
  '/quiz': 30,
  '/practice': 35,
  '/wrong-questions': 37,
  '/mastery': 39,
  '/analytics': 40,
  '/ai-tasks': 50,
  '/profile': 60,
  '/ai-config': 70
}

const transitionName = ref('slide-left')

watch(
  () => route.path,
  (toPath, fromPath) => {
    if (!fromPath) {
      transitionName.value = 'slide-left'
      return
    }
    const toOrder = ROUTE_ORDER[toPath] ?? 99
    const fromOrder = ROUTE_ORDER[fromPath] ?? 99
    transitionName.value = toOrder < fromOrder ? 'slide-right' : 'slide-left'
  }
)

let touchStartX = 0
let touchStartY = 0
let touchStartTime = 0

function handleTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1) return
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touchStartTime = Date.now()
}

function handleTouchEnd(e: TouchEvent) {
  if (e.changedTouches.length !== 1) return
  const deltaX = e.changedTouches[0].clientX - touchStartX
  const deltaY = e.changedTouches[0].clientY - touchStartY
  const duration = Date.now() - touchStartTime

  if (duration < 400 && Math.abs(deltaX) > 50 && Math.abs(deltaX) > Math.abs(deltaY) * 1.5) {
    const mainTabs = primaryNavigationItems.map((item) => item.path)
    const currentIndex = mainTabs.indexOf(route.path)
    if (currentIndex !== -1) {
      if (deltaX < 0 && currentIndex < mainTabs.length - 1) {
        void router.push(mainTabs[currentIndex + 1])
      } else if (deltaX > 0 && currentIndex > 0) {
        void router.push(mainTabs[currentIndex - 1])
      }
    }
  }
}

function openSearch() {
  searchVisible.value = true
}
function openAssistant() {
  searchVisible.value = false
  assistantVisible.value = true
}
function searchMaterials() {
  const keyword = searchKeyword.value.trim()
  searchVisible.value = false
  void router.push({ path: '/materials', query: keyword ? { keyword } : {} })
}
function handleThemeCommand(command: string) {
  setTheme(command as ThemeMode)
  ElMessage.success(`已切换为${currentThemeOption.value.label}`)
}
function handleUserCommand(command: string) {
  if (command === 'profile') void router.push('/profile')
  if (command === 'avatar') avatarInputRef.value?.click()
  if (command === 'settings') void router.push('/ai-config')
  if (command === 'logout') {
    userStore.logout()
    void router.push('/login')
  }
}
function handleMoreCommand(path: string) {
  void router.push(path)
}
async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || avatarUploading.value) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.warning('请选择 JPG、PNG、WebP 或 GIF 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像图片不能超过 5MB')
    return
  }
  avatarUploading.value = true
  const requestToken = userStore.token
  try {
    const response = await uploadAvatarApi(file)
    if (!requestToken || userStore.token !== requestToken) return
    userStore.setProfile(response.data.data)
    ElMessage.success('头像已更新')
  } catch (error) {
    if (requestToken && userStore.token === requestToken) {
      ElMessage.error(getErrorMessage(error, '头像上传失败'))
    }
  } finally {
    avatarUploading.value = false
  }
}
function handleShortcut(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !assistantVisible.value) {
    event.preventDefault()
    searchVisible.value = !searchVisible.value
  }
}
watch(
  () => route.path,
  () => {
    document.title = currentPage.value.label + ' · ' + APP_NAME
  }
)
watch(avatarSrc, () => {
  avatarLoadFailed.value = false
})
onMounted(() => {
  window.addEventListener('keydown', handleShortcut)
  document.title = currentPage.value.label + ' · ' + APP_NAME
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
        /* Authentication errors are handled by the shared interceptor. */
      })
  }
})
onUnmounted(() => {
  window.removeEventListener('keydown', handleShortcut)
})
</script>

<style scoped>
.learning-shell {
  --shell-header-height: 88px;
  min-height: 100dvh;
  background: var(--bg);
}
.workspace-topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  height: var(--shell-header-height);
  background: var(--topbar-bg, rgba(255, 255, 255, 0.88));
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--line);
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}
.workspace-topbar__inner {
  display: flex;
  align-items: center;
  gap: 42px;
  width: 100%;
  max-width: 1440px;
  height: 100%;
  margin-inline: auto;
  padding-inline: 48px;
}
.workspace-topbar__brand {
  display: flex;
  align-items: center;
  flex: 0 0 138px;
  min-height: 48px;
  border-radius: 4px;
}
.workspace-topbar__brand :deep(.brand-logo) {
  width: 138px;
}
.workspace-primary-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: auto;
}
.workspace-primary-nav__item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  border-radius: 999px;
  color: var(--muted);
  text-decoration: none;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.02em;
  white-space: nowrap;
  transition: all 0.2s ease;
}
.workspace-primary-nav__item:hover {
  color: var(--text);
  background: var(--bg-secondary);
}
.workspace-primary-nav__item.is-active {
  color: var(--brand);
  font-weight: 600;
  background: var(--brand-light);
}
.workspace-topbar__actions,
.workspace-account {
  display: flex;
  align-items: center;
  min-width: 0;
}
.workspace-topbar__actions {
  gap: 11px;
}
.workspace-account {
  gap: 0;
}
.workspace-search-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 38px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel);
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s ease;
}
.workspace-search-trigger:hover {
  color: var(--text);
  background: var(--panel-hover);
  border-color: var(--brand);
  box-shadow: var(--shadow-sm);
}
.workspace-search-trigger kbd {
  padding: 2px 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--bg-secondary);
  color: var(--muted);
  font: inherit;
  font-size: 11px;
}
.workspace-theme-trigger {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--panel);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}
.workspace-theme-trigger:hover {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-light);
  transform: translateY(-1px);
}
.workspace-theme-menu {
  min-width: 170px;
}
.theme-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.theme-menu-item__dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid var(--line);
  flex-shrink: 0;
}
.theme-menu-item__label {
  flex: 1;
  font-weight: 500;
}
.theme-menu-item__check {
  margin-left: auto;
  color: var(--brand);
}
.workspace-assistant-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 38px;
  padding: 0 15px;
  border: 1px solid color-mix(in srgb, var(--brand) 25%, transparent);
  border-radius: 999px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--brand) 9%, transparent) 0%,
    color-mix(in srgb, var(--accent) 10%, transparent) 100%
  );
  color: var(--brand);
  font-size: 13px;
  font-weight: 550;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s ease;
}
.workspace-assistant-trigger:hover {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--brand) 15%, transparent) 0%,
    color-mix(in srgb, var(--accent) 17%, transparent) 100%
  );
  border-color: color-mix(in srgb, var(--brand) 40%, transparent);
  color: var(--brand-hover);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.workspace-user {
  display: grid;
  place-items: center;
  min-width: 44px;
  min-height: 44px;
  border-radius: 50%;
  color: var(--text);
  text-decoration: none;
}
.workspace-user__avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1.5px solid color-mix(in srgb, var(--brand) 28%, transparent);
  border-radius: 50%;
  object-fit: cover;
  background: var(--bg-secondary);
  color: var(--brand);
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}
.workspace-user:hover .workspace-user__avatar {
  border-color: var(--brand);
  transform: scale(1.06);
  box-shadow: var(--shadow-sm);
}
.workspace-account__menu,
.workspace-more-trigger {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    color 0.18s ease,
    background-color 0.18s ease;
}
.workspace-account__menu {
  width: 44px;
  min-height: 44px;
}
.workspace-account__menu:hover,
.workspace-more-trigger:hover {
  color: var(--text);
  background: var(--bg-secondary);
}
.workspace-more-trigger.is-active {
  color: var(--brand);
  background: var(--brand-soft);
}
.workspace-content {
  width: 100%;
  max-width: 1296px;
  min-height: calc(100dvh - var(--shell-header-height));
  margin-inline: auto;
  padding: 42px 48px 30px;
  outline: none;
  scroll-margin-top: calc(var(--shell-header-height) + 20px);
  overflow-x: clip;
}
.workspace-content--home,
.workspace-content:has(.daily-home) {
  max-width: 100% !important;
  padding: 0 0 30px !important;
}
.workspace-footer {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-top: 64px;
  padding-top: 24px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.06em;
}
.workspace-content--home .workspace-footer,
.workspace-content:has(.daily-home) .workspace-footer {
  max-width: 1296px;
  margin: 64px auto 0;
  padding-inline: 48px;
}
.workspace-bottom-nav {
  display: none;
}
.skip-to-content {
  position: fixed;
  z-index: 4000;
  top: -80px;
  left: 20px;
  padding: 12px 18px;
  border-radius: 8px;
  color: #fff;
  background: var(--brand);
}
.skip-to-content:focus {
  top: 10px;
}
.workspace-search-label {
  display: block;
  margin-bottom: 10px;
  font-size: 14px;
  color: var(--text-secondary);
}
.workspace-search-help {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}
.workspace-search-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
}
.workspace-topbar a:focus-visible,
.workspace-topbar button:focus-visible,
.workspace-bottom-nav a:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}
.workspace-more-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 110px;
}
.workspace-more-item :deep(.app-icon) {
  color: var(--muted);
  flex-shrink: 0;
  transition: color 0.18s ease;
}
:deep(.el-dropdown-menu__item:hover) .workspace-more-item :deep(.app-icon),
:deep(.el-dropdown-menu__item.is-current) .workspace-more-item :deep(.app-icon) {
  color: var(--brand);
}
:deep(.el-dropdown-menu__item.is-current) {
  color: var(--brand);
  font-weight: 600;
}

@media (max-width: 1180px) {
  .workspace-topbar__inner {
    gap: 30px;
    padding-inline: 32px;
  }
  .workspace-primary-nav {
    gap: 23px;
  }
  .workspace-topbar__actions {
    gap: 9px;
  }
  .workspace-search-trigger span,
  .workspace-search-trigger kbd {
    display: none;
  }
  .workspace-content {
    padding-inline: 32px;
  }
  .workspace-content--home {
    padding-inline: 0;
  }
  .workspace-content--home .workspace-footer {
    padding-inline: 32px;
    margin-inline: auto;
  }
}
@media (max-width: 960px) {
  .workspace-topbar__inner {
    gap: 24px;
  }
  .workspace-topbar__brand {
    flex-basis: 124px;
  }
  .workspace-topbar__brand :deep(.brand-logo) {
    width: 124px;
  }
  .workspace-primary-nav {
    gap: 15px;
  }
  .workspace-assistant-trigger {
    margin-inline: 0;
  }
  .workspace-assistant-trigger span {
    display: none;
  }
}
@media (max-width: 760px) {
  .learning-shell {
    --shell-header-height: calc(72px + env(safe-area-inset-top));
    padding-bottom: calc(74px + env(safe-area-inset-bottom));
  }
  .workspace-topbar {
    padding-top: env(safe-area-inset-top);
  }
  .workspace-topbar__inner {
    justify-content: space-between;
    gap: 12px;
    padding-inline: 22px;
  }
  .workspace-topbar__brand {
    flex-basis: 110px;
  }
  .workspace-topbar__brand :deep(.brand-logo) {
    width: 110px;
  }
  .workspace-primary-nav,
  .workspace-assistant-trigger,
  .workspace-account__dropdown {
    display: none;
  }
  .workspace-topbar__actions {
    gap: 5px;
  }
  .workspace-user__avatar {
    width: 34px;
    height: 34px;
  }
  .workspace-content {
    min-height: calc(100dvh - var(--shell-header-height) - 74px - env(safe-area-inset-bottom));
    padding: 28px 22px 28px;
  }
  .workspace-content--home {
    padding: 0 0 24px;
  }
  .workspace-footer {
    gap: 12px;
    margin-top: 46px;
    padding-top: 22px;
    font-size: 12px;
  }
  .workspace-content--home .workspace-footer {
    padding-inline: 22px;
    margin-inline: auto;
  }
  .workspace-bottom-nav {
    position: fixed;
    z-index: 40;
    right: 0;
    bottom: 0;
    left: 0;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
    min-height: calc(74px + env(safe-area-inset-bottom));
    padding: 8px 18px calc(8px + env(safe-area-inset-bottom));
    border-top: 1px solid var(--line);
    background: var(--bg);
  }
  .workspace-bottom-nav__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    min-width: 44px;
    min-height: 57px;
    border-radius: 6px;
    color: var(--muted);
    font-size: 12px;
    line-height: 1.35;
    font-weight: 400;
    text-decoration: none;
    transition: color 0.18s ease;
  }
  .workspace-bottom-nav__item.is-active {
    color: var(--brand);
    font-weight: 650;
  }
  .workspace-bottom-nav__item.is-active :deep(path) {
    stroke-width: 2.2;
  }
  .workspace-bottom-nav__item:active {
    background: var(--bg-secondary);
  }
}
@media (max-width: 360px) {
  .workspace-topbar__inner {
    padding-inline: 16px;
    gap: 8px;
  }
  .workspace-topbar__actions {
    gap: 1px;
  }
  .workspace-footer {
    flex-wrap: wrap;
  }
}
/* 顶栏微光流光进度条 */
.workspace-progress-bar {
  position: fixed;
  top: 0;
  left: 0;
  height: 2.5px;
  background: linear-gradient(90deg, var(--brand) 0%, var(--brand) 60%, var(--accent) 100%);
  box-shadow: 0 0 10px color-mix(in srgb, var(--accent) 45%, transparent);
  z-index: 10000;
  pointer-events: none;
  transition:
    width 0.22s cubic-bezier(0.16, 1, 0.3, 1),
    opacity 0.28s ease;
}

/* 页面视口容器：水平裁剪超出的滑动元素，防止横向滚动条闪烁 */
.workspace-view-container {
  position: relative;
  width: 100%;
  overflow-x: clip;
  min-height: calc(100dvh - var(--shell-header-height) - 140px);
}

.workspace-view-page {
  width: 100%;
}

/* 动效核心：手机原生左右滑动手感（iOS UINavigationController 视差推进阻尼曲线） */
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  transition:
    transform 0.36s cubic-bezier(0.25, 1, 0.5, 1),
    opacity 0.36s cubic-bezier(0.25, 1, 0.5, 1);
  will-change: transform, opacity;
}

/* 离场组件脱离文档流置底，使得进场组件天然决定高度并无缝滑过 */
.slide-left-leave-active,
.slide-right-leave-active {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  width: 100%;
  pointer-events: none;
  z-index: 1;
}

.slide-left-enter-active,
.slide-right-enter-active {
  position: relative;
  z-index: 2;
}

/* 1. 向左滑动 (Next / 前进)：新页面从右侧 100% 滑入，旧页面向左偏移 28% 视差淡出 */
.slide-left-enter-from {
  transform: translateX(100%);
  opacity: 0.85;
}
.slide-left-leave-to {
  transform: translateX(-28%);
  opacity: 0;
}

/* 2. 向右滑动 (Back / 返回)：旧页面向右侧 100% 滑出，新页面从左侧 -28% 视差滑入 */
.slide-right-enter-from {
  transform: translateX(-28%);
  opacity: 0;
}
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0.85;
}

@media (prefers-reduced-motion: reduce) {
  .workspace-primary-nav__item,
  .workspace-primary-nav__item::after,
  .workspace-search-trigger,
  .workspace-assistant-trigger,
  .workspace-account__menu,
  .workspace-more-trigger,
  .workspace-bottom-nav__item {
    transition: none;
  }
  .slide-left-enter-active,
  .slide-left-leave-active,
  .slide-right-enter-active,
  .slide-right-leave-active {
    transition: opacity 0.1s ease;
  }
  .slide-left-enter-from,
  .slide-left-leave-to,
  .slide-right-enter-from,
  .slide-right-leave-to {
    opacity: 0;
    transform: none;
  }
  .workspace-progress-bar {
    transition: none;
  }
}
</style>
