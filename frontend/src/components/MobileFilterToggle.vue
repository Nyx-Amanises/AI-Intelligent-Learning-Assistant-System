<template>
  <button
    type="button"
    class="mobile-filter-toggle"
    :class="{ 'has-filters': count > 0 }"
    :aria-expanded="modelValue"
    :aria-controls="controls"
    :aria-label="`${modelValue ? '收起' : '展开'}筛选${count ? `，已选 ${count} 项条件` : ''}`"
    @click="emit('update:modelValue', !modelValue)"
  >
    <span
      >筛选<span v-if="count"> · {{ count }}</span></span
    >
    <AppIcon name="chevron-down" :size="14" />
  </button>
</template>

<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'

defineProps<{ modelValue: boolean; count: number; controls: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<style src="@/styles/mobile-filters.css"></style>
