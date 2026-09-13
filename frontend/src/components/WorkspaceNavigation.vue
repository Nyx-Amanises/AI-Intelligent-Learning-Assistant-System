<template>
  <div class="workspace-navigation" :class="{ 'is-collapsed': collapsed }">
    <RouterLink class="workspace-brand" to="/dashboard" aria-label="AI 学习助手，返回学习概览" @click="emit('navigate')">
      <span class="workspace-brand__mark"><AppIcon name="book-open" :size="23" /></span>
      <span class="workspace-brand__name"><strong>AI 学习助手</strong><small>让知识，成为自己的</small></span>
    </RouterLink>
    <nav class="workspace-navigation__groups" aria-label="主要导航">
      <div v-for="group in navigationGroups" :key="group.label" class="workspace-nav-group">
        <p class="workspace-nav-group__label">{{ group.label }}</p>
        <RouterLink v-for="item in group.items" :key="item.path" :to="item.path" class="workspace-nav-item" :title="collapsed ? item.label : undefined" :aria-label="item.label" @click="emit('navigate')">
          <AppIcon :name="item.icon" :size="19" /><span>{{ item.label }}</span><i class="workspace-nav-item__active" aria-hidden="true" />
        </RouterLink>
      </div>
    </nav>
    <div class="workspace-navigation__bottom">
      <button class="workspace-ai-entry" type="button" aria-label="打开 AI 学习助手" @click="emit('openAssistant')">
        <span class="workspace-ai-entry__icon"><AppIcon name="spark" :size="21" /></span>
        <span class="workspace-ai-entry__copy"><strong>学习路上，有我陪你</strong><small>向 AI 提问，理清思路</small></span>
        <AppIcon class="workspace-ai-entry__arrow" name="arrow-up-right" :size="17" />
      </button>
      <RouterLink class="workspace-nav-item workspace-settings" to="/ai-config" aria-label="模型与设置" :title="collapsed ? '模型与设置' : undefined" @click="emit('navigate')">
        <AppIcon name="config" :size="19" /><span>模型与设置</span>
      </RouterLink>
      <p class="workspace-navigation__note">每一点积累，都算数。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { navigationGroups } from '@/config/navigation'
withDefaults(defineProps<{ collapsed?: boolean }>(), { collapsed: false })
const emit = defineEmits<{ navigate: []; openAssistant: [] }>()
</script>

<style scoped>
.workspace-navigation { display: flex; flex-direction: column; height: 100%; min-height: 0; padding: 0 16px; background: var(--sidebar-bg); }
.workspace-brand { display: flex; align-items: center; gap: 11px; flex: 0 0 89px; padding: 0 8px; }
.workspace-brand__mark { display: grid; place-items: center; width: 37px; height: 40px; border-radius: 11px; color: #fff; background: var(--brand); flex-shrink: 0; }
.workspace-brand__name { display: grid; gap: 5px; min-width: 0; }
.workspace-brand__name strong { color: var(--text); font-size: 17px; font-weight: 700; letter-spacing: -.5px; white-space: nowrap; }
.workspace-brand__name small { font-size: 12px; color: var(--muted); white-space: nowrap; letter-spacing: .6px; }
.workspace-navigation__groups { flex: 1; overflow-y: auto; scrollbar-width: thin; padding-bottom: 12px; }
.workspace-nav-group + .workspace-nav-group { margin-top: 24px; }
.workspace-nav-group__label { margin: 7px 13px 9px; color: var(--muted); font-size: 12px; font-weight: 500; letter-spacing: .8px; }
.workspace-nav-item { position: relative; display: flex; align-items: center; gap: 12px; min-height: 43px; margin-bottom: 3px; padding: 10px 13px; border-radius: 8px; color: var(--text-secondary); font-size: 13px; font-weight: 500; transition: color .18s, background .18s; }
.workspace-nav-item > .app-icon { color: var(--muted); }
.workspace-nav-item:hover { background: var(--bg-secondary); color: var(--text); }
.workspace-nav-item.router-link-active { color: var(--brand); background: var(--brand-soft); font-weight: 650; }
.workspace-nav-item.router-link-active > .app-icon { color: var(--brand); }
.workspace-nav-item__active { display: none; height: 5px; width: 5px; border-radius: 50%; background: var(--brand); margin-left: auto; }
.workspace-nav-item.router-link-active .workspace-nav-item__active { display: block; }
.workspace-navigation__bottom { flex-shrink: 0; padding: 14px 0 18px; }
.workspace-ai-entry { width: 100%; display: flex; gap: 10px; align-items: center; text-align: left; padding: 14px 11px; margin-bottom: 14px; border: 1px solid #dce5d9; border-radius: 10px; background: #edf2e9; color: var(--brand); cursor: pointer; transition: border-color .18s, background .18s; }
.workspace-ai-entry:hover { background: #e4eddf; border-color: #b3c9b3; }
.workspace-ai-entry__icon { display: flex; flex: 0 0 23px; }
.workspace-ai-entry__copy { display: grid; gap: 6px; min-width: 0; }
.workspace-ai-entry__copy strong { font-size: 12px; font-weight: 600; white-space: nowrap; }
.workspace-ai-entry__copy small { font-size: 12px; color: #61725f; white-space: nowrap; }
.workspace-ai-entry__arrow { margin-left: auto; }
.workspace-settings { border-top: 1px solid var(--line); border-radius: 0; padding-top: 15px; }
.workspace-navigation__note { margin: 12px 13px 0; color: var(--muted); font-size: 12px; }
.is-collapsed { padding-inline: 10px; }
.is-collapsed .workspace-brand { padding-inline: 0; justify-content: center; }
.is-collapsed .workspace-brand__name, .is-collapsed .workspace-nav-item > span, .is-collapsed .workspace-nav-item__active, .is-collapsed .workspace-ai-entry__copy, .is-collapsed .workspace-ai-entry__arrow, .is-collapsed .workspace-navigation__note { display: none; }
.is-collapsed .workspace-nav-group__label { height: 1px; font-size: 0; border-top: 1px solid var(--line); margin: 14px 8px; }
.is-collapsed .workspace-nav-item, .is-collapsed .workspace-ai-entry { justify-content: center; padding-inline: 0; }
.is-collapsed .workspace-nav-group + .workspace-nav-group { margin-top: 12px; }
@media (max-height: 800px) {
  .workspace-brand { flex-basis: 75px; }
  .workspace-nav-group + .workspace-nav-group { margin-top: 14px; }
  .workspace-nav-item { min-height: 39px; padding-block: 8px; }
  .workspace-navigation__note { display: none; }
  .workspace-navigation__bottom { padding-block: 9px 12px; }
}
@media (max-width: 760px) {
  .workspace-nav-item { min-height: 44px; font-size: 14px; }
  .workspace-ai-entry__copy strong { font-size: 12px; }
  .workspace-ai-entry__copy small { font-size: 12px; }
}
</style>
