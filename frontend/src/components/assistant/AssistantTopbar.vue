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
      <AppIcon name="menu" :size="19" />
    </button>
    <div class="assistant-topbar__title-block">
      <h1 :title="title">{{ title }}</h1>
    </div>
    <div class="assistant-topbar__actions">
      <button
        type="button"
        class="assistant-top-button assistant-top-button--ghost"
        :disabled="loading"
        aria-label="刷新对话列表"
        @click="emit('refresh')"
      >
        <AppIcon name="refresh" :size="18" :class="{ 'is-loading': loading }" />
      </button>
      <button
        type="button"
        class="assistant-top-button assistant-top-button--close"
        :aria-label="`关闭${ASSISTANT_NAME}`"
        @click="emit('close')"
      >
        <AppIcon name="close" :size="19" />
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ASSISTANT_NAME } from '@/config/brand'
import AppIcon from '@/components/AppIcon.vue'

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
  min-height: 44px;
  margin-bottom: 16px;
}

.assistant-topbar__title-block {
  flex: 1;
  min-width: 0;
}

.assistant-topbar h1 {
  margin: 0;
  color: var(--text);
  font-size: 19px;
  font-weight: 600;
  line-height: 1.5;
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
  display: grid;
  place-items: center;
  width: 40px;
  min-height: 44px;
  padding: 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--duration-fast) ease;
}

.assistant-top-button--ghost {
  border-color: transparent;
}

.assistant-top-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.assistant-top-button:not(:disabled):hover {
  background: var(--panel-hover);
  color: var(--brand);
}
.assistant-top-button .is-loading {
  animation: zx-spin 0.8s linear infinite;
}

.assistant-topbar__menu-button {
  display: none;
}

@media (max-width: 900px) {
  .assistant-topbar {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) 44px;
    align-items: center;
    gap: 8px;
    min-height: 64px;
    margin-bottom: 8px;
    padding: 10px 0;
  }

  .assistant-topbar__title-block {
    text-align: center;
  }

  .assistant-topbar h1 {
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
    min-width: 44px;
    padding: 8px;
    font-size: 12px;
  }

  .assistant-topbar__menu-button {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    padding: 10px;
    border: 0;
    border-radius: var(--radius-sm);
    background: transparent;
    cursor: pointer;
  }

  .assistant-topbar__menu-button:hover {
    background: var(--panel-hover);
    color: var(--brand);
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-top-button {
    transition: none;
  }
}
@media (max-width: 760px) {
  .assistant-topbar {
    min-height: 68px;
    padding-block: 12px;
    margin-bottom: 12px;
  }
  .assistant-topbar h1 {
    font-size: 18px;
    font-weight: 600;
  }
}
</style>
