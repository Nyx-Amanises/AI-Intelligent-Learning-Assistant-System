<template>
  <div class="empty-state" :class="[`empty-state--${variant}`]" :role="variant === 'error' ? 'alert' : undefined">
    <div v-if="resolvedIcon" class="empty-state__icon">
      <AppIcon :name="resolvedIcon" :size="variant === 'compact' ? 24 : 30" />
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
  min-height: 360px;
  padding: 48px 20px;
  text-align: center;
}
.empty-state__icon {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  margin-bottom: 22px;
  border-radius: 50%;
  color: var(--brand);
  background: var(--brand-soft);
}
.empty-state__title {
  margin: 0 0 12px;
  color: var(--text);
  font-size: 21px;
  font-weight: 500;
  line-height: 1.5;
}
.empty-state__description {
  max-width: 440px;
  margin: 0 0 24px;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.9;
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
  padding: 28px 16px;
}
.empty-state--compact .empty-state__icon {
  width: 56px;
  height: 56px;
  margin-bottom: 16px;
}
.empty-state--compact .empty-state__title {
  font-size: 18px;
}
.empty-state--compact .empty-state__description {
  font-size: 14px;
  margin-bottom: 18px;
}
.empty-state--error .empty-state__icon {
  color: var(--red);
  background: var(--red-soft);
}
@media (max-width: 640px) {
  .empty-state {
    min-height: 320px;
    padding-inline: 8px;
  }
  .empty-state__title {
    font-size: 19px;
  }
  .empty-state__description {
    font-size: 14px;
  }
}
</style>
