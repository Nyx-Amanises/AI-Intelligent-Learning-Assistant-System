<template>
  <section class="mobile-preferences" aria-label="学习偏好">
    <header class="preferences-desktop-heading">
      <h1>学习偏好</h1>
      <nav aria-label="我的学习与偏好">
        <RouterLink to="/analytics">我的学习</RouterLink>
        <RouterLink to="/profile" aria-current="page">学习偏好</RouterLink>
      </nav>
    </header>
    <div class="preference-group">
      <h2 class="preference-group__title">账户</h2>
      <RouterLink :to="{ path: '/profile', query: { view: 'account' } }" class="preference-row">
        <span class="preference-row__label">账户资料</span>
        <span class="preference-row__value" :title="displayName">{{ displayName }}</span>
        <MobileIcon name="next" :size="16" />
      </RouterLink>
    </div>
    <div class="preference-group">
      <h2 class="preference-group__title">阅读与助手</h2>
      <button
        type="button"
        class="preference-row"
        :aria-expanded="themeExpanded"
        aria-controls="mobile-reading-themes"
        @click="themeExpanded = !themeExpanded"
      >
        <span class="preference-row__label">阅读主题</span>
        <span class="preference-row__value">{{ currentThemeOption.label }}</span>
        <MobileIcon :name="themeExpanded ? 'down' : 'next'" :size="16" />
      </button>
      <div
        v-if="themeExpanded"
        id="mobile-reading-themes"
        class="preference-theme-options"
        role="group"
        aria-label="选择阅读主题"
      >
        <button
          v-for="option in themeOptions"
          :key="option.key"
          type="button"
          :aria-pressed="currentTheme === option.key"
          @click="setTheme(option.key)"
        >
          <AppIcon :name="option.icon" :size="18" />{{ option.label }}
          <AppIcon v-if="currentTheme === option.key" name="check" :size="16" />
        </button>
      </div>
      <RouterLink to="/ai-config" class="preference-row">
        <span class="preference-row__label">AI 配置</span>
        <MobileIcon name="next" :size="16" />
      </RouterLink>
    </div>
    <div class="preference-group">
      <h2 class="preference-group__title">学习工具</h2>
      <RouterLink to="/ai-tasks" class="preference-row">
        <span class="preference-row__label">任务中心</span>
        <MobileIcon name="next" :size="16" />
      </RouterLink>
      <RouterLink v-if="userStore.isAdmin" to="/rag-eval" class="preference-row">
        <span class="preference-row__label">检索评测</span>
        <MobileIcon name="next" :size="16" />
      </RouterLink>
    </div>
    <div class="preference-group">
      <h2 class="preference-group__title">更多</h2>
      <RouterLink to="/materials" class="preference-row">
        <span class="preference-row__label">我的内容</span>
        <MobileIcon name="next" :size="16" />
      </RouterLink>
      <button type="button" class="preference-row" @click="logout">
        <span class="preference-row__label">退出登录</span>
        <MobileIcon name="next" :size="16" />
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '未设置昵称')
const themeExpanded = ref(false)
const { currentTheme, currentThemeOption, themeOptions, setTheme } = useTheme()
const logout = () => {
  userStore.logout()
  void router.push('/login')
}
</script>

<style scoped>
.preferences-desktop-heading,
.preference-group__title {
  display: none;
}
.mobile-preferences {
  display: grid;
  gap: 12px;
  padding: 8px 0 32px;
}
.preference-group {
  padding: 2px 16px;
  border-radius: 13px;
  background: var(--panel);
}
.preference-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 56px;
  padding: 10px 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.preference-row__label {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}
.preference-row__value {
  max-width: 52%;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preference-row > .mobile-icon {
  flex-shrink: 0;
  color: #999;
}
.preference-theme-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 0 14px;
}
.preference-theme-options button {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 8px;
  border: 0;
  border-radius: 10px;
  background: var(--bg);
  color: var(--text-secondary);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.preference-theme-options button[aria-pressed='true'] {
  color: var(--brand);
  background: var(--brand-light);
}
.mobile-preferences a:focus-visible,
.mobile-preferences button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}
@media (min-width: 761px) {
  .mobile-preferences {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
    gap: 24px;
    padding-top: 0;
  }
  .preferences-desktop-heading {
    display: flex;
    grid-column: 1 / -1;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    margin-bottom: 8px;
  }
  .preferences-desktop-heading h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 600;
  }
  .preferences-desktop-heading nav {
    display: flex;
    gap: 4px;
    padding: 4px;
    border-radius: 26px;
    background: var(--panel);
  }
  .preferences-desktop-heading a {
    min-height: 44px;
    padding: 12px 20px;
    border-radius: 22px;
    color: var(--text-secondary);
    text-decoration: none;
  }
  .preferences-desktop-heading a[aria-current='page'] {
    background: var(--brand-soft);
    color: var(--brand);
  }
  .preference-group {
    min-width: 0;
    padding: 20px 24px 12px;
    border-radius: 16px;
  }
  .preference-group__title {
    display: block;
    margin: 0 0 8px;
    color: var(--muted);
    font-size: 12px;
    font-weight: 500;
  }
  .preference-row {
    min-height: 60px;
  }
  .preference-row__label {
    font-size: 15px;
  }
  .preference-row__value {
    font-size: 13px;
  }
}
@media (max-width: 360px) {
  .preference-group {
    padding-inline: 14px;
  }
}
</style>
