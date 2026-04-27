<template>
  <div class="shell-app shell-app--workspace">
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
          :to="item.path"
        >
          <span class="nav-link__icon">
            <AppIcon :name="item.icon" :size="18" />
          </span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </div>

      <div class="side-footer side-footer--crm">
        <RouterLink class="side-settings-link" to="/ai-config">
          <span class="side-settings-link__icon">
            <AppIcon name="config" :size="18" />
          </span>
          <span>系统设置</span>
          <AppIcon name="chevron-right" :size="15" />
        </RouterLink>
      </div>
    </aside>

    <div class="shell-content">
    <header class="app-header">
      <button type="button" class="app-header__menu" aria-label="切换导航">
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
        <el-dropdown trigger="click" @command="handleUserCommand">
          <button type="button" class="app-header__user-badge">
            <span class="app-header__avatar">{{ avatarText }}</span>
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
    </header>

    <main class="shell-main shell-main--crm">
      <RouterView />
    </main>
    </div>

    <AssistantDrawer v-if="showAssistantDrawer" v-model="assistantVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import AssistantDrawer from '@/components/AssistantDrawer.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const assistantVisible = ref(false)

const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const showAssistantDrawer = computed(() => route.path !== '/dashboard')

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

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-header__user-badge {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font-size: 14px;
  cursor: pointer;
}

.app-header__user-label {
  color: var(--muted);
  font-size: 11px;
}

@media (max-width: 768px) {
  .app-header__actions {
    gap: 8px;
  }

  .app-header__user-badge {
    display: none;
  }
}
</style>
