<template>
  <nav ref="navRef" class="section-nav" :aria-label="section.label + '分区导航'">
    <RouterLink
      v-for="item in visibleItems"
      :key="item.path"
      :to="{ path: item.path, query: item.query }"
      class="section-nav__item"
      :class="{ 'is-active': route.path === item.path }"
      :aria-current="route.path === item.path ? 'page' : undefined"
      >{{ item.label }}</RouterLink
    >
    <span class="section-nav__indicator" :style="indicatorStyle" aria-hidden="true" />
  </nav>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { NavigationSection } from '@/config/navigation'
import { useSlidingIndicator } from '@/composables/useSlidingIndicator'
import { useUserStore } from '@/stores/user'

/** 子页面的紧凑分区导航，例如“资料库 / 学习总结”。 */
const props = defineProps<{ section: NavigationSection }>()

const route = useRoute()
const userStore = useUserStore()
const navRef = ref<HTMLElement | null>(null)
const visibleItems = computed(() => props.section.items.filter((item) => !item.adminOnly || userStore.isAdmin))
const { indicatorStyle } = useSlidingIndicator(navRef, '.section-nav__item.is-active', () => route.path)

/** 手机上标签可以横向滚动，切换后把当前标签移进可见区域，不改变页面的纵向滚动位置。 */
const revealActive = () => {
  const nav = navRef.value
  const active = nav?.querySelector<HTMLElement>('.section-nav__item.is-active')
  if (!nav || !active) return
  const left = active.offsetLeft - 16
  const right = active.offsetLeft + active.offsetWidth + 16
  if (left < nav.scrollLeft) nav.scrollLeft = left
  else if (right > nav.scrollLeft + nav.clientWidth) nav.scrollLeft = right - nav.clientWidth
}

watch(
  () => route.path,
  () => void nextTick(revealActive)
)
onMounted(revealActive)
</script>

<style scoped>
.section-nav {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 4px;
  width: fit-content;
  max-width: 100%;
  margin: 0 0 24px;
  padding: 4px;
  border-radius: 16px;
  background: var(--bg-secondary);
  overflow-x: auto;
  scrollbar-width: none;
}
.section-nav::-webkit-scrollbar {
  display: none;
}
.section-nav__item {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  min-height: 36px;
  padding: 0 16px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  transition: color var(--duration-fast) ease;
}
.section-nav__item:hover,
.section-nav__item.is-active {
  color: var(--text);
}
.section-nav__indicator {
  position: absolute;
  top: 0;
  left: 0;
  border-radius: 12px;
  background: var(--panel);
  box-shadow: 0 1px 3px rgb(37 38 43 / 4%);
  pointer-events: none;
  transition:
    transform var(--duration) var(--ease-out),
    width var(--duration) var(--ease-out),
    opacity var(--duration-fast) ease;
}
.section-nav-enter-active {
  animation: zx-fade var(--duration) var(--ease-out);
}
@media (max-width: 760px) {
  .section-nav {
    margin: -4px -16px 20px;
    padding-inline: 4px;
  }
  .section-nav__item {
    font-size: 14px;
  }
}
</style>
