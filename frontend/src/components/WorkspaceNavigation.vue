<template>
  <div class="workspace-navigation">
    <div class="workspace-navigation__header">
      <RouterLink class="workspace-brand" to="/dashboard" :aria-label="APP_NAME + '，返回首页'" @click="emit('navigate')">
        <BrandLogo class="workspace-brand__logo" aria-hidden="true" />
      </RouterLink>
      <button type="button" class="workspace-navigation__close" aria-label="关闭更多功能" @click="emit('close')">
        <AppIcon name="close" :size="21" />
      </button>
    </div>

    <div class="workspace-navigation__scroll">
      <button class="workspace-assistant-entry" type="button" :aria-label="'打开' + ASSISTANT_NAME" @click="emit('openAssistant')">
        <AppIcon name="spark" :size="24" />
        <span><strong>{{ ASSISTANT_NAME }}</strong><small>解答疑惑，整理思路</small></span>
        <AppIcon name="arrow-up-right" :size="20" />
      </button>

      <nav class="workspace-navigation__groups" aria-label="全部功能">
        <section v-for="group in navigationGroups" :key="group.label" class="workspace-nav-group" :aria-label="group.label">
          <h2 class="workspace-nav-group__label">{{ group.label }}</h2>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="workspace-nav-item"
            :aria-label="item.label"
            @click="emit('navigate')"
          >
            <AppIcon :name="item.icon" :size="20" />
            <span>{{ item.label }}</span>
            <AppIcon class="workspace-nav-item__arrow" name="chevron-right" :size="16" />
          </RouterLink>
        </section>
      </nav>

      <div class="workspace-navigation__account" aria-label="账户操作">
        <button type="button" class="workspace-account-action" :disabled="avatarUploading" @click="emit('userCommand', 'avatar')">
          <AppIcon name="camera" :size="19" />
          <span>{{ avatarUploading ? '正在上传…' : '更换头像' }}</span>
        </button>
        <button type="button" class="workspace-account-action" @click="emit('userCommand', 'logout')">
          <span>退出登录</span>
          <AppIcon name="arrow-right" :size="19" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import { navigationGroups } from '@/config/navigation'
import { APP_NAME, ASSISTANT_NAME } from '@/config/brand'

withDefaults(defineProps<{ avatarUploading?: boolean }>(), { avatarUploading: false })
const emit = defineEmits<{
  navigate: []
  close: []
  openAssistant: []
  userCommand: [command: string]
}>()
</script>

<style scoped>
.workspace-navigation {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--bg);
}
.workspace-navigation__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 24px;
  padding: max(24px, env(safe-area-inset-top)) 26px 22px;
}
.workspace-brand { display: flex; align-items: center; min-height: 48px; border-radius: 4px; }
.workspace-brand__logo { width: 142px; }
.workspace-navigation__close {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text);
  cursor: pointer;
}
.workspace-navigation__close:hover { background: var(--bg-secondary); }
.workspace-navigation__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  padding: 0 26px max(24px, env(safe-area-inset-bottom));
}
.workspace-assistant-entry {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 16px;
  padding: 20px 0 24px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--brand);
  text-align: left;
  cursor: pointer;
}
.workspace-assistant-entry > span { display: grid; gap: 4px; }
.workspace-assistant-entry strong { font-size: 16px; font-weight: 600; }
.workspace-assistant-entry small { color: var(--muted); font-size: 12px; font-weight: 400; }
.workspace-assistant-entry > .app-icon:last-child { margin-left: auto; }
.workspace-assistant-entry:hover { color: var(--brand-hover); }
.workspace-nav-group { padding-top: 22px; }
.workspace-nav-group + .workspace-nav-group { margin-top: 10px; border-top: 1px solid var(--line); }
.workspace-nav-group__label { margin: 0 0 10px; color: var(--muted); font-size: 12px; font-weight: 500; letter-spacing: .1em; }
.workspace-nav-item {
  display: flex;
  align-items: center;
  gap: 15px;
  min-height: 49px;
  margin-inline: -10px;
  padding: 10px;
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 400;
  text-decoration: none;
  transition: color .18s ease, background-color .18s ease;
}
.workspace-nav-item__arrow { margin-left: auto; color: var(--muted); opacity: .6; }
.workspace-nav-item:hover { color: var(--text); background: var(--bg-secondary); }
.workspace-nav-item.router-link-active { color: var(--brand); font-weight: 600; background: var(--brand-light); }
.workspace-nav-item.router-link-active .workspace-nav-item__arrow { color: var(--brand); opacity: 1; }
.workspace-navigation__account { display: flex; gap: 20px; justify-content: space-between; margin-top: 26px; padding-top: 16px; border-top: 1px solid var(--line); }
.workspace-account-action {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
}
.workspace-account-action:hover { color: var(--text); }
.workspace-account-action:disabled { opacity: .55; cursor: wait; }
.workspace-navigation button:focus-visible,
.workspace-navigation a:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
@media (prefers-reduced-motion: reduce) {
  .workspace-nav-item { transition: none; }
}
</style>
