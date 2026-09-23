<template>
  <header class="assistant-topbar">
    <button
      ref="menuButtonRef"
      type="button"
      class="assistant-topbar__menu-button"
      aria-label="打开会话菜单"
      :aria-expanded="mobileSidebarOpen"
      @click="emit('open-menu')"
    >
      <span />
      <span />
      <span />
    </button>
    <div class="assistant-topbar__title-block">
      <span class="assistant-topbar__eyebrow">{{ ASSISTANT_NAME }}</span>
      <h1>{{ title }}</h1>
    </div>
    <div class="assistant-topbar__actions">
      <button
        type="button"
        class="assistant-top-button assistant-top-button--ghost"
        :disabled="loading"
        @click="emit('refresh')"
      >
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
      <button
        type="button"
        class="assistant-top-button assistant-top-button--close"
        :aria-label="`关闭${ASSISTANT_NAME}`"
        @click="emit('close')"
      >
        关闭
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ASSISTANT_NAME } from '@/config/brand'

defineProps<{
  title: string
  loading: boolean
  mobileSidebarOpen: boolean
}>()

const emit = defineEmits<{
  (event: 'open-menu'): void
  (event: 'refresh'): void
  (event: 'close'): void
}>()

/** 移动端菜单按钮，收起侧栏后焦点回到这里。 */
const menuButtonRef = ref<HTMLButtonElement | null>(null)

defineExpose({ menuButtonRef })
</script>

<style scoped>
.assistant-topbar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
}

.assistant-topbar__title-block {
  min-width: 0;
}

.assistant-topbar__eyebrow {
  color: var(--brand);
  font-size: 11px;
  font-weight: 700;
}

.assistant-topbar h1 {
  margin: 6px 0 0;
  color: var(--text);
  font-size: 19px;
  line-height: 1.5;
  letter-spacing: -0.03em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-topbar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.assistant-top-button {
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease;
}

.assistant-top-button--ghost {
  border-color: transparent;
}

.assistant-top-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.assistant-top-button:not(:disabled):hover {
  background: var(--brand-light);
}

.assistant-topbar__menu-button {
  display: none;
}

@media (max-width: 900px) {
  .assistant-topbar {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) 56px;
    align-items: center;
    gap: 8px;
    min-height: 76px;
    margin-bottom: 8px;
    padding: 10px 0;
  }

  .assistant-topbar__title-block {
    text-align: center;
  }

  .assistant-topbar__eyebrow {
    display: block;
    font-size: 10px;
    line-height: 1.5;
  }

  .assistant-topbar h1 {
    margin: 3px 0 0;
    font-size: 17px;
    line-height: 1.4;
    letter-spacing: 0;
  }

  .assistant-topbar__actions {
    justify-content: flex-end;
    gap: 0;
  }

  .assistant-top-button--ghost {
    display: none;
  }

  .assistant-top-button--close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 48px;
    padding: 6px 10px;
    font-size: 12px;
  }

  .assistant-topbar__menu-button {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 5px;
    width: 44px;
    height: 44px;
    padding: 11px 10px;
    border: 1px solid var(--line);
    border-radius: 10px;
    background: transparent;
    cursor: pointer;
  }

  .assistant-topbar__menu-button span {
    display: block;
    width: 100%;
    height: 2px;
    border-radius: 2px;
    background: var(--text-secondary);
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-top-button {
    transition: none;
  }
}
</style>
