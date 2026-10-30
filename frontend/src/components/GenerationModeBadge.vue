<template>
  <span
    class="generation-mode-badge"
    :class="`generation-mode-badge--${mode === 'MOCK' ? 'mock' : 'neutral'}`"
    :title="description"
    :aria-label="`${label}：${description}`"
    >{{ label }}</span
  >
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { GenerationMode } from '@/utils/generationMode'

const props = defineProps<{ mode?: GenerationMode | null }>()
const label = computed(() => (props.mode === 'MOCK' ? '模拟数据' : props.mode === 'LIVE' ? '模型生成' : '来源未记录'))
const description = computed(() =>
  props.mode === 'MOCK'
    ? '生成时使用演示模式，仅用于体验流程；切换当前配置不会改变此标记。'
    : props.mode === 'LIVE'
      ? '生成时使用真实模型，内容请结合原始资料核对。'
      : '该记录未保存生成模式，无法确认是否来自演示模式。'
)
</script>

<style scoped>
.generation-mode-badge {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  padding: 2px 9px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
  white-space: nowrap;
  vertical-align: middle;
}
.generation-mode-badge--mock {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: currentColor;
}
.generation-mode-badge--neutral {
  color: var(--muted);
  background: var(--bg-secondary);
}
</style>
