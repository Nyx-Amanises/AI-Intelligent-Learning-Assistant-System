<template>
  <dl class="stat-tiles">
    <div v-for="item in items" :key="item.key ?? item.label" class="stat-tile">
      <dt>{{ item.label }}</dt>
      <dd :class="{ 'is-danger': item.tone === 'danger' }">
        {{ item.value }}<small v-if="item.unit">{{ item.unit }}</small>
      </dd>
      <p v-if="item.note">{{ item.note }}</p>
    </div>
  </dl>
</template>

<script lang="ts">
export interface StatTileItem {
  key?: string
  label: string
  value: string | number
  unit?: string
  note?: string
  tone?: 'default' | 'danger'
}
</script>

<script setup lang="ts">
// 概览数字块：标签 + 大号数值（含单位）+ 可选说明，供统计类页面复用
defineProps<{ items: StatTileItem[] }>()
</script>

<style scoped>
.stat-tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
}
.stat-tile {
  min-width: 0;
  padding: 0 30px;
  border-left: 1px solid var(--line);
}
.stat-tile:first-child {
  padding-left: 0;
  border-left: 0;
}
.stat-tile:last-child {
  padding-right: 0;
}
.stat-tile dt {
  color: var(--muted);
  font-size: 13px;
}
.stat-tile dd {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
  margin: 13px 0 0;
  font-size: clamp(30px, 3.8vw, 46px);
  font-weight: 550;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.045em;
  line-height: 1.15;
  overflow-wrap: anywhere;
}
.stat-tile dd.is-danger {
  color: var(--red);
}
.stat-tile small {
  color: var(--muted);
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 0;
}
.stat-tile p {
  margin: 11px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}
@media (max-width: 1000px) {
  .stat-tile {
    padding-inline: 22px;
  }
}
@media (max-width: 760px) {
  .stat-tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 27px;
  }
  .stat-tile {
    padding-inline: 22px 0;
  }
  .stat-tile:nth-child(odd) {
    border-left: 0;
    padding-left: 0;
  }
  .stat-tile dd {
    font-size: 38px;
  }
}
@media (max-width: 520px) {
  .stat-tile dt {
    font-size: 12px;
  }
  .stat-tile dd {
    font-size: 34px;
    gap: 5px;
    margin-top: 11px;
  }
  .stat-tile small {
    font-size: 12px;
  }
}
</style>
