<template>
  <div class="shell-layout">
    <aside class="shell-side">
      <div class="brand-block">
        <div class="brand-title">AI 智能学习助手</div>
        <div class="brand-subtitle">
          面向大学生的学习管理平台，围绕资料整理、AI 总结与练习复习构建学习闭环。
        </div>
      </div>

      <div class="nav-card">
        <RouterLink class="nav-link" to="/dashboard">概览面板</RouterLink>
        <RouterLink class="nav-link" to="/materials">资料管理</RouterLink>
        <RouterLink class="nav-link" to="/summary">AI 总结</RouterLink>
      </div>

      <el-button style="margin-top: 20px; width: 100%" @click="logout">退出登录</el-button>
    </aside>

    <main class="shell-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>
