<template>
  <div class="learning-shell" :class="{ 'learning-shell--collapsed': isSidebarCollapsed }">
    <a class="skip-to-content" href="#workspace-content">跳到主要内容</a>
    <aside class="learning-sidebar" aria-label="学习工作台导航">
      <WorkspaceNavigation :collapsed="isSidebarCollapsed" @open-assistant="openAssistant" />
    </aside>
    <div class="learning-main">
      <header class="workspace-topbar">
        <div class="workspace-topbar__location">
          <button type="button" class="workspace-icon-button" :aria-label="isMobile ? '打开导航' : isSidebarCollapsed ? '展开导航栏' : '收起导航栏'" :aria-expanded="isMobile ? mobileNavOpen : !isSidebarCollapsed" @click="toggleSidebar">
            <AppIcon name="menu" :size="19" />
          </button>
          <div class="workspace-breadcrumb" aria-label="当前位置">
            <span>{{ currentPage.group }}</span><AppIcon name="chevron-right" :size="13" /><strong>{{ currentPage.label }}</strong>
          </div>
        </div>
        <div class="workspace-topbar__actions">
          <button class="workspace-search-trigger" type="button" aria-label="搜索学习资料" @click="searchVisible = true">
            <AppIcon name="search" :size="17" /><span>搜索学习资料</span><kbd>Ctrl K</kbd>
          </button>
          <button type="button" class="workspace-topbar__ai" aria-label="打开 AI 学习助手" @click="openAssistant">
            <AppIcon name="spark" :size="18" /><span>AI 助手</span>
          </button>
          <span class="workspace-topbar__divider" aria-hidden="true" />
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button type="button" class="workspace-user" aria-label="账户菜单">
              <img v-if="avatarSrc" :src="avatarSrc" alt="" class="workspace-user__avatar">
              <span v-else class="workspace-user__avatar">{{ avatarText }}</span>
              <span class="workspace-user__name">{{ displayName }}</span><AppIcon name="chevron-down" :size="13" />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="avatar" :disabled="avatarUploading">{{ avatarUploading ? '正在上传…' : '更换头像' }}</el-dropdown-item>
                <el-dropdown-item command="settings">模型与设置</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <input ref="avatarInputRef" type="file" accept="image/jpeg,image/png,image/webp,image/gif" class="visually-hidden" tabindex="-1" aria-hidden="true" @change="handleAvatarChange">
        </div>
      </header>
      <main id="workspace-content" class="workspace-content" tabindex="-1">
        <RouterView v-slot="{ Component }"><component :is="Component" @open-assistant="openAssistant" /></RouterView>
        <footer class="workspace-footer"><span>AI 智能学习助手</span><span>让每一次学习，都有所收获。</span></footer>
      </main>
    </div>
    <el-drawer v-model="mobileNavOpen" direction="ltr" size="260px" :with-header="false" class="workspace-mobile-nav" aria-label="学习导航">
      <button type="button" class="workspace-mobile-close" aria-label="关闭导航" @click="mobileNavOpen = false"><AppIcon name="close" :size="18" /></button>
      <WorkspaceNavigation @navigate="mobileNavOpen = false" @open-assistant="openAssistant" />
    </el-drawer>
    <el-dialog v-model="searchVisible" title="搜索学习资料" width="520px" class="workspace-search-dialog" @opened="searchInputRef?.focus()">
      <form @submit.prevent="searchMaterials">
        <label for="workspace-search-input" class="workspace-search-label">输入资料名称或关键词</label>
        <el-input id="workspace-search-input" ref="searchInputRef" v-model="searchKeyword" placeholder="例如：计算机网络、数据结构…" clearable size="large" />
        <p class="workspace-search-help">在你的资料库中查找，继续上一次的学习。</p>
        <div class="workspace-search-actions"><el-button @click="searchVisible = false">取消</el-button><el-button type="primary" native-type="submit">搜索资料</el-button></div>
      </form>
    </el-dialog>
    <AssistantDrawer v-model="assistantVisible" :show-launcher="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { API_BASE_URL } from '@/api/http'
import { getProfileApi, uploadAvatarApi } from '@/api/modules/auth'
import AssistantDrawer from '@/components/AssistantDrawer.vue'
import AppIcon from '@/components/AppIcon.vue'
import WorkspaceNavigation from '@/components/WorkspaceNavigation.vue'
import { navigationItems } from '@/config/navigation'
import { useUserStore } from '@/stores/user'

const SIDEBAR_COLLAPSED_KEY = 'ai-learning-assistant:sidebar-collapsed'
const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const assistantVisible = ref(false)
const mobileNavOpen = ref(false)
const searchVisible = ref(false)
const searchKeyword = ref('')
const searchInputRef = ref<{ focus: () => void }>()
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const isSidebarCollapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1')
const mobileQuery = window.matchMedia('(max-width: 760px)')
const isMobile = ref(mobileQuery.matches)
const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const currentPage = computed(() => navigationItems.find((item) => item.path === route.path) || navigationItems[0])
const avatarSrc = computed(() => {
  const url = userStore.profile?.avatarUrl
  if (!url) return ''
  if (/^(https?:|data:|blob:)/i.test(url)) return url
  const origin = API_BASE_URL.replace(/\/api\/?$/, '')
  if (url.startsWith('/api/')) return origin + url
  return url.startsWith('/') ? url : origin + '/api/' + url.replace(/^\/+/, '')
})

function toggleSidebar() {
  if (isMobile.value) { mobileNavOpen.value = !mobileNavOpen.value; return }
  isSidebarCollapsed.value = !isSidebarCollapsed.value
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, isSidebarCollapsed.value ? '1' : '0')
}
function openAssistant() { mobileNavOpen.value = false; assistantVisible.value = true }
function searchMaterials() {
  const keyword = searchKeyword.value.trim()
  searchVisible.value = false
  void router.push({ path: '/materials', query: keyword ? { keyword } : {} })
}
function handleUserCommand(command: string) {
  if (command === 'avatar') avatarInputRef.value?.click()
  if (command === 'settings') void router.push('/ai-config')
  if (command === 'logout') { userStore.logout(); void router.push('/login') }
}
async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.warning('请选择 JPG、PNG、WebP 或 GIF 图片'); return
  }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('头像图片不能超过 5MB'); return }
  avatarUploading.value = true
  try {
    const response = await uploadAvatarApi(file)
    userStore.setProfile(response.data.data)
    ElMessage.success('头像已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '头像上传失败') }
  finally { avatarUploading.value = false }
}
function handleViewportChange(event: MediaQueryListEvent) {
  isMobile.value = event.matches
  if (!event.matches) mobileNavOpen.value = false
}
function handleShortcut(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !assistantVisible.value) {
    event.preventDefault(); searchVisible.value = !searchVisible.value
  }
}
watch(() => route.path, () => {
  mobileNavOpen.value = false
  document.title = currentPage.value.label + ' · AI 智能学习助手'
})
onMounted(() => {
  mobileQuery.addEventListener('change', handleViewportChange)
  window.addEventListener('keydown', handleShortcut)
  document.title = currentPage.value.label + ' · AI 智能学习助手'
  if (userStore.token) {
    void getProfileApi().then((response) => {
      if (response.data.data) userStore.setProfile(response.data.data)
    }).catch(() => { /* Authentication errors are handled by the shared interceptor. */ })
  }
})
onUnmounted(() => {
  mobileQuery.removeEventListener('change', handleViewportChange)
  window.removeEventListener('keydown', handleShortcut)
})
</script>

<style scoped>
.learning-shell { display: grid; grid-template-columns: 232px minmax(0, 1fr); min-height: 100dvh; background: var(--bg); }
.learning-shell--collapsed { grid-template-columns: 76px minmax(0, 1fr); }
.learning-sidebar { position: sticky; top: 0; height: 100dvh; border-right: 1px solid var(--line); min-width: 0; }
.learning-main { min-width: 0; }
.workspace-topbar { position: sticky; top: 0; z-index: 30; display: flex; align-items: center; justify-content: space-between; height: 72px; padding: 0 38px 0 26px; gap: 20px; background: rgba(255,255,253,.97); border-bottom: 1px solid var(--line); }
.workspace-topbar__location, .workspace-topbar__actions, .workspace-breadcrumb { display: flex; align-items: center; min-width: 0; }
.workspace-topbar__location { gap: 14px; }
.workspace-icon-button { width: 38px; height: 40px; display: grid; place-items: center; padding: 0; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.workspace-icon-button:hover { background: var(--bg-secondary); color: var(--text); }
.workspace-breadcrumb { gap: 12px; font-size: 12px; white-space: nowrap; }
.workspace-breadcrumb > span { color: var(--muted); }
.workspace-breadcrumb .app-icon { color: #a0a79e; }
.workspace-breadcrumb strong { font-weight: 500; }
.workspace-topbar__actions { gap: 20px; }
.workspace-search-trigger { display: flex; align-items: center; gap: 10px; min-height: 37px; width: 236px; padding: 0 12px; border: 1px solid var(--line); border-radius: 7px; color: var(--muted); background: #f9faf7; text-align: left; font-size: 12px; cursor: pointer; }
.workspace-search-trigger:hover { border-color: #b4c5b7; }
.workspace-search-trigger kbd { margin-left: auto; font: inherit; font-size: 12px; color: var(--muted); padding: 2px 5px; background: #fff; border: 1px solid var(--line); border-radius: 4px; }
.workspace-topbar__ai { display: flex; align-items: center; gap: 7px; min-height: 40px; padding: 0; border: 0; background: transparent; color: var(--brand); cursor: pointer; font-size: 12px; font-weight: 500; white-space: nowrap; }
.workspace-topbar__divider { height: 22px; width: 1px; background: var(--line); }
.workspace-user { display: flex; align-items: center; gap: 9px; min-height: 44px; padding: 0; border: 0; background: transparent; color: var(--text); cursor: pointer; }
.workspace-user__avatar { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; object-fit: cover; background: #eeeadd; color: #6d644f; font-size: 13px; font-weight: 650; }
.workspace-user__name { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.workspace-content { max-width: 1540px; margin: 0 auto; padding: 34px 38px 20px; outline: none; min-height: calc(100dvh - 72px); }
.workspace-footer { display: flex; justify-content: space-between; gap: 14px; margin-top: 38px; padding-top: 17px; border-top: 1px solid var(--line); color: var(--muted); font-size: 12px; letter-spacing: .3px; }
.skip-to-content { position: fixed; z-index: 4000; top: -80px; left: 20px; padding: 12px 18px; border-radius: 8px; color: #fff; background: var(--brand); }
.skip-to-content:focus { top: 10px; }
.workspace-search-label { display: block; margin-bottom: 10px; font-size: 13px; color: var(--text-secondary); }
.workspace-search-help { color: var(--muted); font-size: 12px; line-height: 1.8; }
.workspace-search-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 24px; }
.workspace-mobile-close { position: absolute; right: 8px; top: 8px; z-index: 2; display: grid; place-items: center; width: 44px; height: 44px; border: 1px solid var(--line); border-radius: 50%; background: #fff; color: var(--muted); cursor: pointer; }
@media (max-width: 1180px) {
  .workspace-topbar { padding-inline: 18px 24px; gap: 12px; }
  .workspace-topbar__actions { gap: 12px; }
  .workspace-search-trigger { width: 190px; }
  .workspace-content { padding: 28px 24px 20px; }
  .workspace-user__name { display: none; }
}
@media (max-width: 960px) {
  .workspace-search-trigger { width: 40px; justify-content: center; padding: 0; }
  .workspace-search-trigger span, .workspace-search-trigger kbd { display: none; }
  .workspace-breadcrumb > span, .workspace-breadcrumb > .app-icon { display: none; }
}
@media (max-width: 760px) {
  .learning-shell, .learning-shell--collapsed { grid-template-columns: minmax(0,1fr); }
  .learning-sidebar { display: none; }
  .workspace-topbar { height: 64px; padding-inline: 12px 18px; gap: 8px; }
  .workspace-topbar__location { gap: 6px; }
  .workspace-topbar__actions { gap: 9px; }
  .workspace-topbar__ai { width: 44px; justify-content: center; min-height: 44px; }
  .workspace-topbar__ai span, .workspace-topbar__divider, .workspace-user > .app-icon { display: none; }
  .workspace-icon-button, .workspace-search-trigger { width: 44px; min-height: 44px; }
  .workspace-user { min-width: 44px; justify-content: center; }
  .workspace-content { padding: 24px 18px 20px; min-height: calc(100dvh - 64px); }
  .workspace-footer { font-size: 12px; flex-wrap: wrap; }
}
</style>
