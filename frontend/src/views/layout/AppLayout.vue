<template>
  <div class="shell-app">
    <header class="app-header">
      <div class="app-header__brand">
        <div class="app-header__logo">
          <AppIcon name="brand" :size="23" />
        </div>
        <div class="app-header__brand-text">
          <strong>AI 智能学习助手</strong>
          <span>Learning Workspace</span>
        </div>
      </div>

      <div class="app-header__actions">
        <div class="app-header__user-badge">
          <span class="app-header__user-label">当前用户</span>
          <strong>{{ displayName }}</strong>
        </div>
      </div>
    </header>

    <div class="shell-layout shell-layout--crm">
      <aside class="shell-side shell-side--crm">
        <div class="nav-card nav-card--crm">
          <div class="nav-section-title">功能导航</div>
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

        <div class="side-profile side-profile--crm">
          <div class="side-profile__label">当前账号</div>
          <div class="side-profile__name">{{ displayName }}</div>
          <div class="side-profile__meta">{{ userStore.profile?.username || '未命名用户' }}</div>
          <el-button style="margin-top: 16px; width: 100%" @click="logout">退出登录</el-button>
        </div>
      </aside>

      <main class="shell-main shell-main--crm">
        <RouterView />
      </main>
    </div>

    <AssistantDrawer v-model="assistantVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import AssistantDrawer from '@/components/AssistantDrawer.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const assistantVisible = ref(false)

const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')

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
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
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
