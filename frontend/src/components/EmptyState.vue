<template>
  <div class="empty-state" :class="[`empty-state--${variant}`]" :role="variant === 'error' ? 'alert' : undefined">
    <div v-if="resolvedIcon" class="empty-state__icon">
      <AppIcon :name="resolvedIcon" :size="variant === 'compact' ? 22 : 26" />
    </div>
    <h3 v-if="title" class="empty-state__title">{{ title }}</h3>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__actions"><slot /></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

/**
 * 统一的空状态 / 错误状态。
 * - default：列表为空时的引导
 * - compact：对话框内的小尺寸空态
 * - error：加载失败，红色警示图标，容器带 role="alert"
 */
const props = withDefaults(
  defineProps<{
    icon?: string
    title?: string
    description?: string
    variant?: 'default' | 'compact' | 'error'
  }>(),
  {
    icon: '',
    title: '',
    description: '',
    variant: 'default'
  }
)

/** 错误态固定用警示图标，避免各页面传入不相干的图标。 */
const resolvedIcon = computed(() => (props.variant === 'error' ? 'alert' : props.icon))
</script>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 300px;
  padding: 40px 20px;
  text-align: center;
  animation: zx-rise var(--duration-slow) var(--ease-out) both;
}
.empty-state__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 18px;
  border-radius: 14px;
  color: var(--brand);
  background: var(--brand-light);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand) 12%, transparent);
}
.empty-state__title {
  margin: 0 0 8px;
  color: var(--text);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.5;
}
.empty-state__description {
  max-width: 420px;
  margin: 0 0 22px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.empty-state__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}
.empty-state__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.empty-state--compact {
  min-height: 200px;
  padding: 24px 16px;
}
.empty-state--compact .empty-state__icon {
  width: 44px;
  height: 44px;
  margin-bottom: 14px;
  border-radius: 12px;
}
.empty-state--compact .empty-state__title {
  font-size: 16px;
}
.empty-state--compact .empty-state__description {
  margin-bottom: 16px;
  font-size: 13px;
}
.empty-state--error .empty-state__icon {
  color: var(--red);
  background: var(--red-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--red) 14%, transparent);
}
@media (max-width: 640px) {
  .empty-state {
    min-height: 260px;
    padding-inline: 8px;
  }
}
</style>
