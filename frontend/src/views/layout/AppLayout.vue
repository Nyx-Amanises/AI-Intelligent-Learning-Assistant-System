<template>
  <div class="learning-shell">
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
          >{{ item.label }}</RouterLink>
        </nav>

        <div class="workspace-topbar__actions">
          <button class="workspace-search-trigger" type="button" aria-label="搜索学习资料（Ctrl K）" @click="openSearch">
            <AppIcon name="search" :size="21" />
            <span>搜索</span>
            <kbd>Ctrl K</kbd>
          </button>

          <button type="button" class="workspace-assistant-trigger" :aria-label="'打开' + ASSISTANT_NAME" @click="openAssistant">
            <AppIcon name="spark" :size="21" />
            <span>{{ ASSISTANT_NAME }}</span>
          </button>

          <div class="workspace-account">
            <RouterLink to="/profile" class="workspace-user" :aria-label="displayName + '，个人信息'" title="个人信息">
              <img v-if="avatarSrc && !avatarLoadFailed" :src="avatarSrc" alt="" class="workspace-user__avatar" @error="avatarLoadFailed = true">
              <span v-else class="workspace-user__avatar">{{ avatarText }}</span>
            </RouterLink>
            <el-dropdown class="workspace-account__dropdown" trigger="click" @command="handleUserCommand">
              <button type="button" class="workspace-account__menu" aria-label="账户菜单" title="账户菜单">
                <AppIcon name="chevron-down" :size="16" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                  <el-dropdown-item command="avatar" :disabled="avatarUploading">{{ avatarUploading ? '正在上传…' : '更换头像' }}</el-dropdown-item>
                  <el-dropdown-item command="settings">模型与设置</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <button
            type="button"
            class="workspace-more-trigger"
            :class="{ 'is-active': moreNavigationActive }"
            aria-label="更多功能"
            aria-haspopup="dialog"
            :aria-expanded="navigationVisible"
            @click="navigationVisible = true"
          ><AppIcon name="menu" :size="22" /></button>

          <input ref="avatarInputRef" type="file" accept="image/jpeg,image/png,image/webp,image/gif" class="visually-hidden" tabindex="-1" aria-hidden="true" @change="handleAvatarChange">
        </div>
      </div>
    </header>

    <main id="workspace-content" class="workspace-content" :class="{ 'workspace-content--home': isHome }" tabindex="-1">
      <RouterView v-slot="{ Component }"><component :is="Component" @open-assistant="openAssistant" /></RouterView>
      <footer class="workspace-footer"><span>{{ APP_NAME }}</span><span>让每一次学习，都有所收获。</span></footer>
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

    <el-drawer
      v-model="navigationVisible"
      direction="rtl"
      size="380px"
      :with-header="false"
      class="workspace-more-nav"
      aria-label="更多功能"
    >
      <WorkspaceNavigation
        :avatar-uploading="avatarUploading"
        @navigate="navigationVisible = false"
        @close="navigationVisible = false"
        @open-assistant="openAssistant"
        @user-command="handleUserCommand"
      />
    </el-drawer>

    <el-dialog v-model="searchVisible" title="搜索学习资料" width="520px" class="workspace-search-dialog" @opened="searchInputRef?.focus()">
      <form @submit.prevent="searchMaterials">
        <label for="workspace-search-input" class="workspace-search-label">输入资料名称或关键词</label>
        <el-input id="workspace-search-input" ref="searchInputRef" v-model="searchKeyword" placeholder="例如：计算机网络、数据结构…" clearable size="large" />
        <p class="workspace-search-help">在你的资料中查找，继续上一次的学习。</p>
        <div class="workspace-search-actions"><el-button @click="searchVisible = false">取消</el-button><el-button type="primary" native-type="submit">搜索资料</el-button></div>
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
import WorkspaceNavigation from '@/components/WorkspaceNavigation.vue'
import { APP_NAME, ASSISTANT_NAME } from '@/config/brand'
import { mobileNavigationItems, navigationItems, primaryNavigationItems } from '@/config/navigation'
import { useUserStore } from '@/stores/user'
import { resolveAvatarUrl } from '@/utils/avatar'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const assistantVisible = ref(false)
const navigationVisible = ref(false)
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
const isHome = computed(() => route.path === '/dashboard')
const moreNavigationActive = computed(() => ['/ai-tasks', '/rag-eval', '/ai-config'].includes(route.path))

function openSearch() {
  navigationVisible.value = false
  searchVisible.value = true
}
function openAssistant() {
  navigationVisible.value = false
  searchVisible.value = false
  assistantVisible.value = true
}
function searchMaterials() {
  const keyword = searchKeyword.value.trim()
  searchVisible.value = false
  void router.push({ path: '/materials', query: keyword ? { keyword } : {} })
}
function handleUserCommand(command: string) {
  navigationVisible.value = false
  if (command === 'profile') void router.push('/profile')
  if (command === 'avatar') avatarInputRef.value?.click()
  if (command === 'settings') void router.push('/ai-config')
  if (command === 'logout') { userStore.logout(); void router.push('/login') }
}
async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || avatarUploading.value) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.warning('请选择 JPG、PNG、WebP 或 GIF 图片'); return
  }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('头像图片不能超过 5MB'); return }
  avatarUploading.value = true
  const requestToken = userStore.token
  try {
    const response = await uploadAvatarApi(file)
    if (!requestToken || userStore.token !== requestToken) return
    userStore.setProfile(response.data.data)
    ElMessage.success('头像已更新')
  } catch (error) {
    if (requestToken && userStore.token === requestToken) {
      ElMessage.error(error instanceof Error ? error.message : '头像上传失败')
    }
  } finally { avatarUploading.value = false }
}
function handleShortcut(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !assistantVisible.value) {
    event.preventDefault()
    navigationVisible.value = false
    searchVisible.value = !searchVisible.value
  }
}
watch(() => route.path, () => {
  navigationVisible.value = false
  document.title = currentPage.value.label + ' · ' + APP_NAME
})
watch(avatarSrc, () => { avatarLoadFailed.value = false })
onMounted(() => {
  window.addEventListener('keydown', handleShortcut)
  document.title = currentPage.value.label + ' · ' + APP_NAME
  if (userStore.token && route.path !== '/profile') {
    const requestToken = userStore.token
    const previousProfile = userStore.profile
    void getProfileApi().then((response) => {
      if (response.data.data && userStore.token === requestToken && userStore.profile === previousProfile) {
        userStore.setProfile(response.data.data)
      }
    }).catch(() => { /* Authentication errors are handled by the shared interceptor. */ })
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
  background: var(--bg);
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
.workspace-topbar__brand { display: flex; align-items: center; flex: 0 0 138px; min-height: 48px; border-radius: 4px; }
.workspace-topbar__brand :deep(.brand-logo) { width: 138px; }
.workspace-primary-nav { display: flex; align-items: center; gap: 32px; margin-right: auto; }
.workspace-primary-nav__item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 48px;
  color: var(--muted);
  text-decoration: none;
  font-size: 15px;
  font-weight: 400;
  letter-spacing: .06em;
  white-space: nowrap;
  transition: color .18s ease;
}
.workspace-primary-nav__item::after {
  position: absolute;
  right: 10px;
  bottom: 2px;
  left: 10px;
  height: 2px;
  border-radius: 2px;
  background: var(--brand);
  content: '';
  opacity: 0;
  transition: opacity .18s ease;
}
.workspace-primary-nav__item:hover { color: var(--text); }
.workspace-primary-nav__item.is-active { color: var(--text); font-weight: 600; }
.workspace-primary-nav__item.is-active::after { opacity: 1; }
.workspace-topbar__actions, .workspace-account { display: flex; align-items: center; min-width: 0; }
.workspace-topbar__actions { gap: 13px; }
.workspace-account { gap: 0; }
.workspace-search-trigger, .workspace-assistant-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-width: 44px;
  min-height: 44px;
  padding: 0 4px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  transition: color .18s ease;
}
.workspace-search-trigger kbd { padding: 2px 4px; border: 1px solid var(--line); border-radius: 4px; color: var(--muted); font: inherit; font-size: 12px; }
.workspace-assistant-trigger { color: var(--brand); margin-inline: 7px; }
.workspace-search-trigger:hover, .workspace-assistant-trigger:hover { color: var(--text); }
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
  width: 35px;
  height: 35px;
  border: 1px solid rgba(56,90,74,.12);
  border-radius: 50%;
  object-fit: cover;
  background: #ece9df;
  color: #645c4b;
  font-size: 14px;
  font-weight: 600;
}
.workspace-account__menu, .workspace-more-trigger {
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
  transition: color .18s ease, background-color .18s ease;
}
.workspace-account__menu { width: 44px; min-height: 44px; }
.workspace-account__menu:hover, .workspace-more-trigger:hover { color: var(--text); background: var(--bg-secondary); }
.workspace-more-trigger.is-active { color: var(--brand); background: var(--brand-soft); }
.workspace-content {
  width: 100%;
  max-width: 1296px;
  min-height: calc(100dvh - var(--shell-header-height));
  margin-inline: auto;
  padding: 42px 48px 30px;
  outline: none;
  scroll-margin-top: calc(var(--shell-header-height) + 20px);
}
.workspace-content--home { max-width: 1440px; padding: 0 0 30px; }
.workspace-footer {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-top: 64px;
  padding-top: 24px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: .06em;
}
.workspace-content--home .workspace-footer { margin-inline: 48px; }
.workspace-bottom-nav { display: none; }
.skip-to-content { position: fixed; z-index: 4000; top: -80px; left: 20px; padding: 12px 18px; border-radius: 8px; color: #fff; background: var(--brand); }
.skip-to-content:focus { top: 10px; }
.workspace-search-label { display: block; margin-bottom: 10px; font-size: 14px; color: var(--text-secondary); }
.workspace-search-help { color: var(--muted); font-size: 13px; line-height: 1.8; }
.workspace-search-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 24px; }
.workspace-topbar a:focus-visible, .workspace-topbar button:focus-visible,
.workspace-bottom-nav a:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
:global(.workspace-more-nav.el-drawer) { width: min(380px, 100vw) !important; }
:global(.workspace-more-nav .el-drawer__body) { padding: 0; overflow: hidden; }

@media (max-width: 1180px) {
  .workspace-topbar__inner { gap: 30px; padding-inline: 32px; }
  .workspace-primary-nav { gap: 23px; }
  .workspace-topbar__actions { gap: 9px; }
  .workspace-search-trigger span, .workspace-search-trigger kbd { display: none; }
  .workspace-content { padding-inline: 32px; }
  .workspace-content--home { padding-inline: 0; }
  .workspace-content--home .workspace-footer { margin-inline: 32px; }
}
@media (max-width: 960px) {
  .workspace-topbar__inner { gap: 24px; }
  .workspace-topbar__brand { flex-basis: 124px; }
  .workspace-topbar__brand :deep(.brand-logo) { width: 124px; }
  .workspace-primary-nav { gap: 15px; }
  .workspace-assistant-trigger { margin-inline: 0; }
  .workspace-assistant-trigger span { display: none; }
}
@media (max-width: 760px) {
  .learning-shell { --shell-header-height: calc(72px + env(safe-area-inset-top)); padding-bottom: calc(74px + env(safe-area-inset-bottom)); }
  .workspace-topbar { padding-top: env(safe-area-inset-top); }
  .workspace-topbar__inner { justify-content: space-between; gap: 12px; padding-inline: 22px; }
  .workspace-topbar__brand { flex-basis: 110px; }
  .workspace-topbar__brand :deep(.brand-logo) { width: 110px; }
  .workspace-primary-nav, .workspace-assistant-trigger, .workspace-account__dropdown { display: none; }
  .workspace-topbar__actions { gap: 5px; }
  .workspace-user__avatar { width: 34px; height: 34px; }
  .workspace-content { min-height: calc(100dvh - var(--shell-header-height) - 74px - env(safe-area-inset-bottom)); padding: 28px 22px 28px; }
  .workspace-content--home { padding: 0 0 24px; }
  .workspace-footer { gap: 12px; margin-top: 46px; padding-top: 22px; font-size: 12px; }
  .workspace-content--home .workspace-footer { margin-inline: 22px; }
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
    transition: color .18s ease;
  }
  .workspace-bottom-nav__item.is-active { color: var(--brand); font-weight: 650; }
  .workspace-bottom-nav__item.is-active :deep(path) { stroke-width: 2.2; }
  .workspace-bottom-nav__item:active { background: var(--bg-secondary); }
}
@media (max-width: 360px) {
  .workspace-topbar__inner { padding-inline: 16px; gap: 8px; }
  .workspace-topbar__actions { gap: 1px; }
  .workspace-footer { flex-wrap: wrap; }
}
@media (prefers-reduced-motion: reduce) {
  .workspace-primary-nav__item, .workspace-primary-nav__item::after,
  .workspace-search-trigger, .workspace-assistant-trigger, .workspace-account__menu,
  .workspace-more-trigger, .workspace-bottom-nav__item { transition: none; }
}
</style>
