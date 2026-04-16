<template>
  <div class="shell-layout">
    <aside class="shell-side">
      <div class="brand-block">
        <div class="brand-badge">Resume Project</div>
        <div class="brand-title">AI 智能学习助手</div>
        <div class="brand-subtitle">
          面向大学生的学习管理平台，覆盖资料整理、AI 总结、智能出题与练习复盘。
        </div>
      </div>

      <div class="nav-card">
        <div class="nav-section-title">核心功能</div>
        <RouterLink class="nav-link" to="/dashboard">概览面板</RouterLink>
        <RouterLink class="nav-link" to="/materials">资料管理</RouterLink>
        <RouterLink class="nav-link" to="/summary">AI 总结</RouterLink>
        <RouterLink class="nav-link" to="/quiz">AI 出题</RouterLink>
        <RouterLink class="nav-link" to="/practice">练习记录</RouterLink>
        <RouterLink class="nav-link" to="/ai-config">AI 配置</RouterLink>
      </div>

      <div class="side-profile">
        <div class="side-profile__label">当前账号</div>
        <div class="side-profile__name">{{ displayName }}</div>
        <div class="side-profile__meta">{{ userStore.profile?.username || '未命名用户' }}</div>
      </div>

      <el-button style="margin-top: 20px; width: 100%" @click="logout">退出登录</el-button>
    </aside>

    <main class="shell-main">
      <header class="shell-topbar">
        <div>
          <div class="shell-topbar__eyebrow">Learning Command Center</div>
          <div class="shell-topbar__title">把“整理 -> 练习 -> 复习”真正串起来</div>
        </div>
        <div class="shell-topbar__user">
          <span class="status-dot"></span>
          <span>{{ displayName }}</span>
        </div>
      </header>

      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>
