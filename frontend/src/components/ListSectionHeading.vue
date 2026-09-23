<template>
  <div class="list-section-heading">
    <div class="list-section-heading__text">
      <h2 class="list-section-heading__title">
        {{ title }}<span v-if="count !== undefined" class="list-section-heading__count">{{ count }}</span>
      </h2>
      <p v-if="description" class="list-section-heading__description">{{ description }}</p>
    </div>
    <div class="list-section-heading__actions">
      <slot />
      <el-button v-if="refreshable" text :loading="loading" @click="emit('refresh')">
        <AppIcon v-if="!loading" name="refresh" :size="16" />刷新
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'

/** 列表区块标题：标题 + 数量 + 可选说明 + 刷新按钮。 */
withDefaults(
  defineProps<{
    title: string
    count?: number
    description?: string
    loading?: boolean
    refreshable?: boolean
  }>(),
  {
    count: undefined,
    description: '',
    loading: false,
    refreshable: true
  }
)

const emit = defineEmits<{ refresh: [] }>()
</script>

<style scoped>
.list-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 0 0 12px;
}
.list-section-heading__title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
}
.list-section-heading__count {
  color: var(--muted);
  font-size: 14px;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}
.list-section-heading__description {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.list-section-heading__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}
.list-section-heading__actions :deep(.el-button span) {
  gap: 6px;
}
</style>
