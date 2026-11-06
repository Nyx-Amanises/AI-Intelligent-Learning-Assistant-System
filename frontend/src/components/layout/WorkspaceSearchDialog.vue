<template>
  <el-dialog
    :model-value="modelValue"
    class="search-palette"
    width="min(560px, calc(100vw - 24px))"
    top="12vh"
    :show-close="false"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
    @open="prepare"
    @opened="inputRef?.focus()"
  >
    <template #header="{ titleId }">
      <h2 :id="titleId" class="visually-hidden">搜索资料或页面</h2>
    </template>
    <form class="search-palette__field" role="search" @submit.prevent="runActive">
      <AppIcon name="search" :size="20" />
      <input
        ref="inputRef"
        v-model="keyword"
        type="text"
        enterkeyhint="search"
        autocomplete="off"
        spellcheck="false"
        placeholder="搜索资料，或输入页面名称"
        aria-label="搜索资料或页面"
        aria-controls="search-palette-results"
        :aria-activedescendant="results[activeIndex] ? 'search-result-' + activeIndex : undefined"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
      />
      <kbd>Esc</kbd>
    </form>
    <p class="search-palette__group">{{ keyword.trim() ? '搜索结果' : '常用页面' }}</p>
    <ul id="search-palette-results" class="search-palette__results" role="listbox" aria-label="搜索结果">
      <li
        v-for="(item, index) in results"
        :id="'search-result-' + index"
        :key="item.id"
        role="option"
        :aria-selected="index === activeIndex"
        class="search-palette__item"
        :class="{ 'is-active': index === activeIndex }"
        @mousemove="activeIndex = index"
        @click="run(item)"
      >
        <span class="search-palette__icon"><AppIcon :name="item.icon" :size="18" /></span>
        <span class="search-palette__text">
          <strong>{{ item.label }}</strong>
          <small>{{ item.hint }}</small>
        </span>
        <AppIcon class="search-palette__go" name="arrow-right" :size="16" />
      </li>
    </ul>
    <p class="search-palette__help">
      <span><kbd>↑</kbd><kbd>↓</kbd>选择</span><span><kbd>Enter</kbd>打开</span>
    </p>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { allNavigationItems, findSection, type NavigationItem } from '@/config/navigation'
import { useUserStore } from '@/stores/user'

interface SearchResult {
  id: string
  label: string
  hint: string
  icon: string
  to: { path: string; query?: Record<string, string> }
}

/** Ctrl K 打开的搜索面板：回车搜索资料库，也可以按名称直接跳到某个页面。 */
defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const userStore = useUserStore()
const inputRef = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const activeIndex = ref(0)

const QUICK_PATHS = ['/materials', '/summary', '/quiz', '/wrong-questions', '/analytics']

const pageResult = (item: NavigationItem): SearchResult => ({
  id: item.path,
  label: item.label,
  hint: findSection(item.path)?.label ?? '页面',
  icon: item.icon,
  to: { path: item.path }
})

const pages = computed(() => allNavigationItems.filter((item) => !item.adminOnly || userStore.isAdmin))

const results = computed<SearchResult[]>(() => {
  const text = keyword.value.trim()
  if (!text) {
    return QUICK_PATHS.map((path) => pages.value.find((item) => item.path === path))
      .filter((item): item is NavigationItem => Boolean(item))
      .map(pageResult)
  }
  const lower = text.toLowerCase()
  const matchedPages = pages.value
    .filter((item) => `${item.label} ${item.keywords || ''}`.toLowerCase().includes(lower))
    .slice(0, 5)
    .map(pageResult)
  return [
    {
      id: 'materials-search',
      label: `在资料库中搜索“${text}”`,
      hint: '按资料名称、标签和说明查找',
      icon: 'search',
      to: { path: '/materials', query: { keyword: text } }
    },
    ...matchedPages
  ]
})

watch(keyword, () => {
  activeIndex.value = 0
})

const prepare = () => {
  keyword.value = ''
  activeIndex.value = 0
}

const move = (step: number) => {
  const count = results.value.length
  if (count) activeIndex.value = (activeIndex.value + step + count) % count
}

const run = (item: SearchResult) => {
  emit('update:modelValue', false)
  void router.push(item.to)
}

const runActive = () => {
  const item = results.value[activeIndex.value]
  if (item) run(item)
}
</script>
<style scoped>
:global(:root .el-dialog.search-palette) {
  --el-dialog-padding-primary: 0;
  overflow: hidden;
}
:global(:root .el-dialog.search-palette .el-dialog__header) {
  padding: 0;
}
.search-palette__field {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
}
.search-palette__field input {
  flex: 1;
  min-width: 0;
  height: 58px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font-size: 16px;
}
.search-palette__field input::placeholder {
  color: var(--muted);
}
.search-palette kbd {
  padding: 1px 6px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-xs);
  background: var(--panel-hover);
  color: var(--muted);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
}
.search-palette__group {
  margin: 12px 18px 4px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
}
.search-palette__results {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 4px 8px 8px;
  list-style: none;
}
.search-palette__item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.search-palette__item.is-active {
  background: var(--bg-secondary);
}
.search-palette__icon {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  background: var(--brand-light);
  color: var(--brand);
}
.search-palette__text {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 1px;
}
.search-palette__text strong {
  overflow: hidden;
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-palette__text small {
  color: var(--muted);
  font-size: 12px;
}
.search-palette__go {
  color: var(--muted);
  opacity: 0;
  transform: translateX(-4px);
  transition:
    opacity var(--duration-fast) ease,
    transform var(--duration) var(--ease-out);
}
.search-palette__item.is-active .search-palette__go {
  opacity: 1;
  transform: none;
}
.search-palette__help {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 0;
  padding: 10px 18px;
  border-top: 1px solid var(--line);
  background: var(--panel-hover);
  color: var(--muted);
  font-size: 12px;
}
.search-palette__help span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
@media (pointer: coarse) {
  .search-palette__field kbd,
  .search-palette__help {
    display: none;
  }
}
</style>
