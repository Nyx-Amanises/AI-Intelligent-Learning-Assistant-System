<template>
  <section class="shortcuts surface" aria-labelledby="shortcuts-title">
    <h2 id="shortcuts-title">常用</h2>
    <nav class="shortcuts__list" aria-label="常用功能">
      <RouterLink to="/summary" class="shortcuts__item">
        <span class="shortcuts__icon"><AppIcon name="summary" :size="18" /></span>
        <span class="shortcuts__text"><strong>学习总结</strong><small>整理重点，对照原文回看</small></span>
        <AppIcon class="shortcuts__go" name="chevron-right" :size="16" />
      </RouterLink>
      <RouterLink to="/quiz" class="shortcuts__item">
        <span class="shortcuts__icon"><AppIcon name="quiz" :size="18" /></span>
        <span class="shortcuts__text"><strong>题集</strong><small>选一份资料，生成练习题</small></span>
        <AppIcon class="shortcuts__go" name="chevron-right" :size="16" />
      </RouterLink>
      <RouterLink to="/wrong-questions" class="shortcuts__item">
        <span class="shortcuts__icon"><AppIcon name="wrong" :size="18" /></span>
        <span class="shortcuts__text"><strong>错题本</strong><small>看解析，或单独重做一题</small></span>
        <span v-if="wrongCount" class="shortcuts__count">{{ wrongCount > 99 ? '99+' : wrongCount }}</span>
        <AppIcon class="shortcuts__go" name="chevron-right" :size="16" />
      </RouterLink>
      <button type="button" class="shortcuts__item" @click="emit('openAssistant')">
        <span class="shortcuts__icon"><AppIcon name="spark" :size="18" /></span>
        <span class="shortcuts__text"><strong>学习助手</strong><small>问概念、找出处、分析错题</small></span>
        <AppIcon class="shortcuts__go" name="chevron-right" :size="16" />
      </button>
    </nav>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'

/** 首页右侧的常用入口。错题本显示待复习数量。 */
defineProps<{ wrongCount: number }>()
const emit = defineEmits<{ openAssistant: [] }>()
</script>

<style scoped>
.shortcuts {
  padding: 20px 8px 10px;
}
.shortcuts h2 {
  display: flex;
  align-items: center;
  min-height: 32px;
  margin: 0;
  padding: 0 16px 6px;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
}
.shortcuts__list {
  display: grid;
}
.shortcuts__item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 60px;
  padding: 10px 12px 10px 16px;
  border: 0;
  border-radius: var(--radius);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.shortcuts__item:hover {
  background: var(--panel-hover);
}
.shortcuts__icon {
  display: grid;
  flex: 0 0 36px;
  place-items: center;
  height: 36px;
  border-radius: 10px;
  background: var(--brand-light);
  color: var(--brand);
}
.shortcuts__text {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 2px;
}
.shortcuts__text strong {
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
}
.shortcuts__text small {
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shortcuts__count {
  min-width: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--red-soft);
  color: var(--red);
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.shortcuts__go {
  color: var(--muted);
  transition: transform var(--duration) var(--ease-out);
}
.shortcuts__item:hover .shortcuts__go {
  transform: translateX(2px);
}
</style>
