<template>
  <div class="shell-app">
    <header class="app-header">
      <div class="app-header__brand">
        <div class="app-header__logo">AI</div>
        <div class="app-header__brand-text">
          <strong>AI 智能学习助手</strong>
          <span>Learning Workspace</span>
        </div>
      </div>

      <div class="app-header__actions">
        <span class="app-header__icon">搜</span>
        <span class="app-header__icon">铃</span>
        <span class="app-header__icon">助</span>
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
            <span class="nav-link__icon">{{ item.icon }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')

const navItems = [
  { path: '/dashboard', label: '首页', icon: '首' },
  { path: '/materials', label: '资料管理', icon: '资' },
  { path: '/ai-tasks', label: '任务中心', icon: '任' },
  { path: '/summary', label: 'AI 总结', icon: '总' },
  { path: '/quiz', label: 'AI 出题', icon: '题' },
  { path: '/practice', label: '练习记录', icon: '练' },
  { path: '/ai-config', label: 'AI 配置', icon: '配' }
]

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>
