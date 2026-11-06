<template>
  <section class="recent-card surface" aria-labelledby="recent-card-title" :aria-busy="loading">
    <header class="recent-card__head">
      <h2 id="recent-card-title">最近添加的资料</h2>
      <RouterLink to="/materials" class="text-link"
        >全部 {{ total || '' }}<AppIcon name="arrow-right" :size="15"
      /></RouterLink>
    </header>

    <div v-if="loading && !materials.length" aria-hidden="true">
      <div v-for="n in 3" :key="n" class="recent-card__row">
        <span class="skeleton recent-card__skeleton-badge" /><span class="skeleton recent-card__skeleton-text" />
      </div>
    </div>
    <div v-else-if="error && !materials.length" class="recent-card__empty" role="alert">
      <p>资料列表没有加载出来。</p>
      <button type="button" class="text-link" @click="emit('reload')">重新加载</button>
    </div>
    <div v-else-if="!materials.length" class="recent-card__empty">
      <p>还没有资料，上传后会出现在这里。</p>
      <RouterLink :to="uploadRoute" class="text-link"><AppIcon name="plus" :size="15" />上传资料</RouterLink>
    </div>
    <ul v-else class="recent-card__list">
      <li v-for="item in materials" :key="item.id">
        <RouterLink :to="materialRoute(item.id)" class="recent-card__row">
          <MaterialTypeBadge :type="item.materialType" />
          <span class="recent-card__text">
            <strong :title="item.title">{{ item.title }}</strong>
            <small>{{ rowMeta(item) }}</small>
          </span>
          <StatusPill
            v-if="item.parseStatus !== 'SUCCESS'"
            :tone="statusTone(item.parseStatus)"
            :label="formatParseStatus(item.parseStatus)"
          />
          <AppIcon class="recent-card__go" name="chevron-right" :size="16" />
        </RouterLink>
      </li>
      <li v-if="materials.length < 4">
        <RouterLink :to="uploadRoute" class="recent-card__row recent-card__add">
          <span class="recent-card__add-icon"><AppIcon name="plus" :size="18" /></span>
          <span class="recent-card__text"><strong>添加资料</strong><small>PDF、Word、TXT 或一段文字</small></span>
        </RouterLink>
      </li>
    </ul>
    <p v-if="error && materials.length" class="recent-card__stale" role="status">
      刷新失败，显示的是上次加载的内容。<button type="button" @click="emit('reload')">重试</button>
    </p>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MaterialTypeBadge from '@/components/MaterialTypeBadge.vue'
import StatusPill from '@/components/StatusPill.vue'
import type { MaterialPageItem } from '@/api/modules/material'
import { formatCharacters, formatRelativeDay } from '@/utils/format'
import { formatMaterialType, formatParseStatus, statusTone } from '@/utils/labels'
import { materialRoute, uploadRoute } from './routes'

/** 首页“最近添加的资料”：最多四条，点开直接进入资料详情。 */
defineProps<{ loading: boolean; error: boolean; materials: MaterialPageItem[]; total: number }>()
const emit = defineEmits<{ reload: [] }>()

const rowMeta = (item: MaterialPageItem) =>
  [
    formatMaterialType(item.materialType, '文档'),
    item.parseStatus === 'SUCCESS' ? formatCharacters(item.totalCharacters) : '',
    `${formatRelativeDay(item.createdAt)}添加`
  ]
    .filter(Boolean)
    .join(' · ')
</script>
<style scoped>
.recent-card {
  padding: 20px 8px 10px;
}
.recent-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  padding: 0 16px 6px;
}
.recent-card__head h2 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
}
.recent-card__head .text-link {
  font-size: 13px;
}
.recent-card__add-icon {
  display: grid;
  flex: 0 0 36px;
  place-items: center;
  height: 36px;
  border: 1px dashed var(--line-strong);
  border-radius: 10px;
  color: var(--muted);
}
a.recent-card__add strong {
  color: var(--text-secondary);
}
a.recent-card__add:hover .recent-card__add-icon {
  border-color: var(--brand);
  color: var(--brand);
}
.recent-card__list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.recent-card__row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 60px;
  padding: 10px 12px 10px 16px;
  border-radius: var(--radius);
  transition: background-color var(--duration-fast) ease;
}
a.recent-card__row:hover {
  background: var(--panel-hover);
}
.recent-card__text {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 2px;
}
.recent-card__text strong {
  overflow: hidden;
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
a.recent-card__row:hover strong {
  color: var(--brand);
}
.recent-card__text small {
  color: var(--muted);
  font-size: 12px;
}
.recent-card__go {
  color: var(--muted);
  transition: transform var(--duration) var(--ease-out);
}
a.recent-card__row:hover .recent-card__go {
  transform: translateX(2px);
}
.recent-card__skeleton-badge {
  flex: 0 0 36px;
  height: 36px;
  border-radius: 10px;
}
.recent-card__skeleton-text {
  flex: 0 1 60%;
  height: 14px;
}
.recent-card__empty {
  display: grid;
  justify-items: start;
  gap: 4px;
  padding: 18px 16px 16px;
  color: var(--text-secondary);
  font-size: 14px;
}
.recent-card__empty p {
  margin: 0;
}
.recent-card__stale {
  margin: 4px 16px 6px;
  color: var(--muted);
  font-size: 12px;
}
.recent-card__stale button {
  padding: 0 4px;
  border: 0;
  background: none;
  color: var(--brand);
  font: inherit;
  cursor: pointer;
}
</style>
