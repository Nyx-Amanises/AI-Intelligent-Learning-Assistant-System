<template>
  <div
    class="shell-app shell-app--workspace"
    :class="{ 'shell-app--sidebar-collapsed': isSidebarCollapsed }"
  >
    <aside class="shell-side shell-side--crm">
      <div class="app-header__brand app-header__brand--sidebar">
        <div class="app-header__logo">
          <AppIcon name="brand" :size="23" />
        </div>
        <div class="app-header__brand-text">
          <strong>AI 智能学习助手</strong>
        </div>
      </div>

      <div class="nav-card nav-card--crm">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          class="nav-link nav-link--crm"
          :title="isSidebarCollapsed ? item.label : undefined"
          :to="item.path"
        >
          <span class="nav-link__icon">
            <AppIcon :name="item.icon" :size="18" />
          </span>
          <span class="nav-link__label">{{ item.label }}</span>
        </RouterLink>
      </div>

      <div class="side-footer side-footer--crm">
        <RouterLink
          class="side-settings-link"
          title="系统设置"
          to="/ai-config"
        >
          <span class="side-settings-link__icon">
            <AppIcon name="config" :size="18" />
          </span>
          <span class="side-settings-link__label">系统设置</span>
          <AppIcon class="side-settings-link__chevron" name="chevron-right" :size="15" />
        </RouterLink>
      </div>
    </aside>

    <div class="shell-content">
      <header class="app-header">
        <button
          type="button"
          class="app-header__menu"
          :aria-expanded="!isSidebarCollapsed"
          :aria-label="isSidebarCollapsed ? '展开导航栏' : '收起导航栏'"
          :title="isSidebarCollapsed ? '展开导航栏' : '收起导航栏'"
          @click="toggleSidebar"
        >
          <AppIcon name="menu" :size="20" />
        </button>

        <div class="app-header__actions">
          <button type="button" class="app-header__tool" aria-label="搜索">
            <AppIcon name="search" :size="18" />
          </button>
          <button type="button" class="app-header__tool app-header__tool--notice" aria-label="通知">
            <AppIcon name="bell" :size="18" />
            <span>3</span>
          </button>

          <div class="app-header__user-group">
            <input
              ref="avatarInputRef"
              class="app-header__avatar-input"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              @change="handleAvatarChange"
            >
            <button
              type="button"
              class="app-header__avatar-button"
              :class="{ 'is-uploading': avatarUploading }"
              :disabled="avatarUploading"
              aria-label="上传头像"
              title="上传头像"
              @click="openAvatarPicker"
            >
              <img
                v-if="avatarSrc"
                class="app-header__avatar-image"
                :src="avatarSrc"
                alt=""
              >
              <span v-else class="app-header__avatar">{{ avatarText }}</span>
              <span class="app-header__avatar-mask">
                <AppIcon name="camera" :size="14" />
              </span>
            </button>

            <el-dropdown trigger="click" @command="handleUserCommand">
              <button type="button" class="app-header__user-badge">
                <strong>{{ displayName }}</strong>
                <AppIcon name="chevron-down" :size="15" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </header>

      <main class="shell-main shell-main--crm">
        <RouterView />
      </main>
    </div>

    <AssistantDrawer v-if="showAssistantDrawer" v-model="assistantVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { API_BASE_URL } from '@/api/http'
import { getProfileApi, uploadAvatarApi } from '@/api/modules/auth'
import AssistantDrawer from '@/components/AssistantDrawer.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'

const SIDEBAR_COLLAPSED_KEY = 'ai-learning-assistant:sidebar-collapsed'
const MAX_AVATAR_SIZE = 5 * 1024 * 1024
const ALLOWED_AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const assistantVisible = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const isSidebarCollapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1')

const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const showAssistantDrawer = computed(() => route.path !== '/dashboard')
const avatarSrc = computed(() => resolveAvatarUrl(userStore.profile?.avatarUrl))

const apiOrigin = computed(() => API_BASE_URL.replace(/\/api\/?$/, ''))

const handleUserCommand = (command: string) => {
  if (command === 'logout') {
    logout()
  }
}

const navItems = [
  { path: '/dashboard', label: '首页', icon: 'home' },
  { path: '/materials', label: '资料管理', icon: 'materials' },
  { path: '/ai-tasks', label: '任务中心', icon: 'tasks' },
  { path: '/rag-eval', label: 'RAG 评测', icon: 'eval' },
  { path: '/summary', label: 'AI 总结', icon: 'summary' },
  { path: '/quiz', label: 'AI 出题', icon: 'quiz' },
  { path: '/practice', label: '练习记录', icon: 'practice' },
  { path: '/wrong-questions', label: '错题本', icon: 'wrong' },
  { path: '/mastery', label: '掌握度', icon: 'mastery' },
  { path: '/analytics', label: '学习分析', icon: 'analytics' },
  { path: '/ai-config', label: 'AI 配置', icon: 'config' }
]

onMounted(() => {
  void refreshProfile()
})

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, isSidebarCollapsed.value ? '1' : '0')
}

const openAvatarPicker = () => {
  avatarInputRef.value?.click()
}

const handleAvatarChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }

  if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
    ElMessage.warning('请选择 JPG、PNG、WebP 或 GIF 图片')
    return
  }
  if (file.size > MAX_AVATAR_SIZE) {
    ElMessage.warning('头像图片不能超过 5MB')
    return
  }

  avatarUploading.value = true
  try {
    const response = await uploadAvatarApi(file)
    const profile = response.data?.data
    userStore.setProfile(profile)
    ElMessage.success('头像已更新')
  } catch (error) {
    const message = error instanceof Error ? error.message : '头像上传失败'
    ElMessage.error(message)
  } finally {
    avatarUploading.value = false
  }
}

const refreshProfile = async () => {
  if (!userStore.token) {
    return
  }
  try {
    const response = await getProfileApi()
    const profile = response.data?.data
    if (profile) {
      userStore.setProfile(profile)
    }
  } catch {
    // Token 失效会由 http 拦截器处理。
  }
}

const resolveAvatarUrl = (url?: string) => {
  if (!url) {
    return ''
  }
  if (/^(https?:|data:|blob:)/i.test(url)) {
    return url
  }
  if (url.startsWith('/api/')) {
    return `${apiOrigin.value}${url}`
  }
  if (url.startsWith('/')) {
    return url
  }
  return `${apiOrigin.value}/api/${url.replace(/^\/+/, '')}`
}

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.shell-app--workspace {
  transition: grid-template-columns 0.2s ease;
}

.shell-app--workspace.shell-app--sidebar-collapsed {
  grid-template-columns: 72px minmax(0, 1fr);
}

.shell-app--sidebar-collapsed .shell-side--crm {
  padding-inline: 10px;
  overflow-x: hidden;
}

.shell-app--sidebar-collapsed .app-header__brand--sidebar {
  justify-content: center;
  padding-inline: 0;
}

.shell-app--sidebar-collapsed .app-header__brand-text,
.shell-app--sidebar-collapsed .nav-link__label,
.shell-app--sidebar-collapsed .side-settings-link__label,
.shell-app--sidebar-collapsed .side-settings-link__chevron {
  width: 0;
  opacity: 0;
  overflow: hidden;
  pointer-events: none;
}

.shell-app--sidebar-collapsed .nav-link--crm,
.shell-app--sidebar-collapsed .side-settings-link {
  justify-content: center;
  padding-inline: 0;
}

.shell-app--sidebar-collapsed .nav-link--crm {
  gap: 0;
}

.shell-app--sidebar-collapsed .side-settings-link__icon {
  background: transparent;
}

.app-header__menu {
  border-radius: 8px;
  transition: background 0.2s ease;
}

.app-header__menu:hover {
  background: #f1f5f9;
}

.app-header__user-group {
  display: flex;
  align-items: center;
  gap: 9px;
}

.app-header__avatar-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.app-header__avatar-button {
  position: relative;
  width: 38px;
  height: 38px;
  display: inline-grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  overflow: hidden;
}

.app-header__avatar-button:disabled {
  cursor: wait;
}

.app-header__avatar-button:hover .app-header__avatar-mask,
.app-header__avatar-button:focus-visible .app-header__avatar-mask,
.app-header__avatar-button.is-uploading .app-header__avatar-mask {
  opacity: 1;
}

.app-header__avatar-button:focus-visible {
  outline: 2px solid rgba(31, 122, 90, 0.35);
  outline-offset: 3px;
}

.app-header__avatar-image,
.app-header__avatar {
  width: 38px;
  height: 38px;
}

.app-header__avatar-image {
  display: block;
  object-fit: cover;
  border-radius: 50%;
}

.app-header__avatar {
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #dbeafe, #dcfce7);
  color: #0f5138;
  font-size: 14px;
  font-weight: 800;
}

.app-header__avatar-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.52);
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.app-header__user-badge {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font-size: 14px;
  cursor: pointer;
}

.app-header__user-badge strong {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-header__user-label {
  color: var(--muted);
  font-size: 11px;
}

@media (max-width: 720px) {
  .shell-app--workspace.shell-app--sidebar-collapsed {
    grid-template-columns: 1fr;
  }

  .shell-app--sidebar-collapsed .app-header__brand-text,
  .shell-app--sidebar-collapsed .nav-link__label,
  .shell-app--sidebar-collapsed .side-settings-link__label,
  .shell-app--sidebar-collapsed .side-settings-link__chevron {
    width: auto;
    opacity: 1;
    pointer-events: auto;
  }
}

@media (max-width: 768px) {
  .app-header__actions {
    gap: 8px;
  }

  .app-header__user-group {
    display: none;
  }
}
</style>
