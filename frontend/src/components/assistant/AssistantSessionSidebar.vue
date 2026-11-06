<template>
  <aside
    ref="rootRef"
    class="assistant-sidebar"
    :class="{ 'assistant-sidebar--mobile-open': mobileOpen }"
    :inert="sidebarInert"
    aria-label="学习助手会话"
  >
    <div class="assistant-sidebar__brand">
      <div class="assistant-sidebar__brand-row">
        <span class="assistant-sidebar__logo">
          <AppIcon name="copilot" :size="25" />
        </span>
        <div>
          <h2>{{ ASSISTANT_NAME }}</h2>
        </div>
        <button type="button" class="assistant-sidebar__close" aria-label="收起会话列表" @click="emit('close-mobile')">
          收起
        </button>
      </div>
    </div>

    <details class="assistant-context-card">
      <summary>
        <span>对话范围</span><strong>{{ bindingLabel }}</strong
        ><AppIcon name="chevron-down" :size="14" />
      </summary>
      <p>{{ bindingHelperText }}</p>
      <p>提问时可以说明资料或题集的名称。</p>
    </details>

    <div class="assistant-sidebar__actions">
      <button
        type="button"
        class="assistant-control assistant-control--primary"
        :disabled="creating"
        @click="emit('create')"
      >
        <AppIcon name="plus" :size="17" />{{ creating ? '正在创建…' : '新对话' }}
      </button>
      <button
        type="button"
        class="assistant-control assistant-control--ghost"
        :disabled="loading"
        :aria-label="loading ? '正在刷新对话' : '刷新对话列表'"
        @click="emit('refresh')"
      >
        <AppIcon name="refresh" :size="17" :class="{ 'is-loading': loading }" />
      </button>
    </div>

    <div class="assistant-session-header">
      <div>
        <span>最近对话</span>
        <strong>{{ total }}</strong>
      </div>
    </div>

    <div v-if="loading && !sessions.length" class="assistant-sidebar__empty" role="status">正在加载对话…</div>
    <div v-else-if="!sessions.length" class="assistant-sidebar__empty">还没有对话。输入问题，就能开始。</div>
    <div v-else class="assistant-session-list">
      <article
        v-for="item in sessions"
        :key="item.id"
        class="assistant-session-card"
        :class="{
          'assistant-session-card--active': activeSessionId === item.id,
          'assistant-session-card--pinned': isSessionPinned(item)
        }"
      >
        <div class="assistant-session-card__top">
          <span v-if="isSessionPinned(item)" class="assistant-session-card__pin" title="已置顶">
            <AppIcon name="pin" :size="13" />
          </span>
          <button
            type="button"
            class="assistant-session-card__select"
            :aria-current="activeSessionId === item.id ? 'true' : undefined"
            :title="item.title || '新对话'"
            @click="emit('select', item.id)"
          >
            {{ item.title || '新对话' }}
          </button>
          <el-dropdown
            trigger="click"
            popper-class="assistant-session-menu"
            :disabled="busySessionIds.includes(item.id)"
            @command="handleCommand(item, $event)"
          >
            <button
              type="button"
              class="assistant-session-card__menu-button"
              :disabled="busySessionIds.includes(item.id)"
              :aria-label="`${item.title || '新对话'}的更多操作`"
              @click.stop
            >
              <AppIcon name="more-horizontal" :size="16" />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item :command="isSessionPinned(item) ? 'unpin' : 'pin'">
                  {{ isSessionPinned(item) ? '取消置顶' : '置顶对话' }}
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided class="assistant-session-menu__danger">
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </article>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { AssistantSessionPageItem } from '@/api/modules/assistant'
import type { AssistantSessionCommand } from '@/composables/useAssistantSessions'
import AppIcon from '@/components/AppIcon.vue'
import { ASSISTANT_NAME } from '@/config/brand'
import { isSessionPinned } from '@/utils/assistantFormat'

defineProps<{
  sessions: AssistantSessionPageItem[]
  total: number
  activeSessionId: number | null
  loading: boolean
  creating: boolean
  /** 正在重命名 / 置顶 / 删除中的会话 id，对应菜单会被禁用。 */
  busySessionIds: number[]
  bindingLabel: string
  bindingHelperText: string
  /** 移动端离屏侧栏是否展开。 */
  mobileOpen: boolean
  /** 紧凑布局下侧栏收起时整块置为 inert，避免被 Tab 到。 */
  sidebarInert: boolean
}>()

const emit = defineEmits<{
  (event: 'create'): void
  (event: 'refresh'): void
  (event: 'select', sessionId: number): void
  (event: 'command', item: AssistantSessionPageItem, command: AssistantSessionCommand): void
  (event: 'close-mobile'): void
}>()

const rootRef = ref<HTMLElement | null>(null)

const handleCommand = (item: AssistantSessionPageItem, command: unknown) => {
  emit('command', item, String(command) as AssistantSessionCommand)
}

defineExpose({ rootRef })
</script>

<style scoped>
.assistant-sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
  padding: 24px 18px;
  border-right: 1px solid var(--line);
  background: var(--bg);
  overflow: hidden;
}

.assistant-sidebar__brand-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assistant-sidebar__logo {
  display: grid;
  flex: 0 0 40px;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: var(--brand);
  color: var(--on-brand);
}

.assistant-sidebar__brand h2 {
  margin: 0;
  color: var(--text);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: -0.3px;
}

.assistant-context-card p {
  display: block;
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-style: normal;
  line-height: 1.7;
}

.assistant-sidebar__close {
  display: none;
}

.assistant-context-card {
  padding: 4px 0 14px;
  border-bottom: 1px solid var(--line);
}

.assistant-context-card summary {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  color: var(--muted);
  font-size: 12px;
  list-style: none;
  cursor: pointer;
}
.assistant-context-card summary::-webkit-details-marker {
  display: none;
}
.assistant-context-card summary .app-icon {
  flex-shrink: 0;
  margin-left: auto;
}
.assistant-context-card[open] summary .app-icon {
  transform: rotate(180deg);
}

.assistant-context-card strong {
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.assistant-sidebar__actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  gap: 6px;
}

.assistant-control {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 0;
  border-radius: var(--el-border-radius-base);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition: background var(--duration-fast) ease;
}

.assistant-control--primary {
  background: var(--brand);
  color: var(--on-brand);
}
.assistant-control--primary:not(:disabled):hover {
  background: var(--brand-hover);
}
.assistant-control--ghost {
  justify-content: center;
}
.assistant-control .is-loading {
  animation: zx-spin 0.8s linear infinite;
}

.assistant-control:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.assistant-control--ghost:not(:disabled):hover {
  background: var(--brand-light);
}

.assistant-session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assistant-session-header span {
  color: var(--muted);
  font-size: 12px;
}

.assistant-session-header strong {
  margin-left: 8px;
  color: var(--text);
  font-size: 12px;
  font-weight: 500;
}

.assistant-sidebar__empty {
  padding: 8px 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.75;
}

.assistant-session-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
  padding-right: 6px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.assistant-session-list::-webkit-scrollbar {
  width: 8px;
}

.assistant-session-list::-webkit-scrollbar-track {
  background: transparent;
}

.assistant-session-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--line);
}

.assistant-session-list::-webkit-scrollbar-thumb:hover {
  background: var(--muted);
}

.assistant-session-card {
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) ease;
  cursor: pointer;
}

.assistant-session-card:hover {
  background: var(--panel);
}

.assistant-session-card--pinned:not(.assistant-session-card--active) {
  background: color-mix(in srgb, var(--panel) 32%, transparent);
}

.assistant-session-card--active,
.assistant-session-card--active:hover {
  background: var(--brand-soft);
}

.assistant-session-card__top {
  display: flex;
  align-items: center;
  gap: 6px;
}

.assistant-session-card__pin {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  color: var(--text-secondary);
}

.assistant-session-card__select {
  flex: 1;
  min-width: 0;
  min-height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.assistant-session-card--active .assistant-session-card__select {
  color: var(--brand);
  font-weight: 600;
}

.assistant-session-card__menu-button {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background var(--duration-fast) ease;
}

.assistant-session-card__menu-button:not(:disabled):hover,
.assistant-session-card__menu-button:not(:disabled):focus-visible {
  background: var(--line);
  color: var(--text);
}

.assistant-session-card__menu-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

/* 下拉菜单挂在 body 下，需要全局选择器。 */
:global(.assistant-session-menu__danger) {
  color: var(--red);
}

@media (max-width: 900px) {
  .assistant-sidebar {
    position: absolute;
    inset: 0 auto 0 0;
    z-index: 3;
    width: min(86vw, 320px);
    padding: 22px 16px;
    overflow-y: auto;
    box-shadow: 14px 0 36px rgb(27 42 32 / 12%);
    transform: translateX(-105%);
    visibility: hidden;
    transition:
      transform var(--duration) var(--ease-out),
      visibility var(--duration) ease;
  }

  .assistant-sidebar--mobile-open {
    transform: translateX(0);
    visibility: visible;
  }

  .assistant-sidebar__close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 44px;
    min-height: 44px;
    margin-left: auto;
    padding: 0 4px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 12px;
    cursor: pointer;
  }
}

@media (max-width: 760px) {
  .assistant-sidebar {
    width: min(88vw, 340px);
    padding: calc(24px + env(safe-area-inset-top)) 20px calc(20px + env(safe-area-inset-bottom));
    gap: 20px;
  }
  .assistant-sidebar__logo {
    background: var(--mobile-accent, var(--brand));
    color: var(--text);
  }
  .assistant-sidebar__brand h2 {
    font-size: 20px;
  }
  .assistant-context-card {
    padding-bottom: 20px;
  }
  .assistant-context-card p {
    font-size: 13px;
  }
  .assistant-control {
    min-height: 48px;
  }
  .assistant-control--primary,
  .assistant-control--primary:not(:disabled):hover {
    background: var(--mobile-accent, var(--brand));
    color: var(--text);
    font-weight: 600;
  }
  .assistant-session-list {
    gap: 8px;
    padding-right: 0;
  }
  .assistant-session-card {
    padding-block: 5px;
    border-radius: 12px;
  }
  .assistant-session-card__select {
    font-size: 14px;
  }
  .assistant-session-card--active .assistant-session-card__select {
    color: var(--text);
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-sidebar,
  .assistant-control,
  .assistant-session-card__menu-button {
    transition: none;
  }
}
</style>
