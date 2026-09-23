<template>
  <button
    type="button"
    class="icon-menu-button"
    :disabled="disabled || loading"
    :aria-label="label"
    :aria-busy="loading"
    :title="label"
  >
    <span v-if="loading" class="icon-menu-button__spinner" aria-hidden="true" />
    <AppIcon v-else name="more-horizontal" :size="18" />
  </button>
</template>

<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'

/** 卡片上的"更多操作"触发按钮，放在 el-dropdown 的默认插槽里使用。 */
withDefaults(
  defineProps<{
    label?: string
    loading?: boolean
    disabled?: boolean
  }>(),
  {
    label: '更多操作',
    loading: false,
    disabled: false
  }
)
</script>

<style scoped>
.icon-menu-button {
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition:
    color 0.15s ease,
    background-color 0.15s ease;
}
.icon-menu-button:hover:not(:disabled) {
  color: var(--text);
  background: var(--bg-secondary);
}
.icon-menu-button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
.icon-menu-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.icon-menu-button__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--line);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: icon-menu-spin 0.7s linear infinite;
}
@keyframes icon-menu-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
