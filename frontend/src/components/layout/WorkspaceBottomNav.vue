<template>
  <nav class="bottom-nav" aria-label="底部导航">
    <RouterLink
      v-for="item in mobileLinks"
      :key="item.key"
      :to="item.path"
      class="bottom-nav__item"
      :class="{ 'is-active': activeKey === item.key }"
      :aria-current="activeKey === item.key ? 'page' : undefined"
    >
      <span class="bottom-nav__icon"><AppIcon :name="item.icon" :size="22" /></span>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { activeNavigationKey, mobileLinks } from '@/config/navigation'

/** 手机底部的五个主入口，只在窄屏显示。 */
const route = useRoute()
const activeKey = computed(() => activeNavigationKey(route.path))
</script>

<style scoped>
.bottom-nav {
  display: none;
}
@media (max-width: 760px) {
  .bottom-nav {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    padding: 6px 8px calc(6px + env(safe-area-inset-bottom));
    border-top: 1px solid var(--line);
    background: var(--topbar-bg);
    backdrop-filter: saturate(1.4) blur(14px);
    -webkit-backdrop-filter: saturate(1.4) blur(14px);
  }
  .bottom-nav__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    min-height: 52px;
    color: var(--muted);
    font-size: 12px;
    font-weight: 500;
    line-height: 1.3;
    transition: color var(--duration-fast) ease;
  }
  .bottom-nav__icon {
    display: grid;
    place-items: center;
    width: 52px;
    height: 28px;
    border-radius: 999px;
    transition: background-color var(--duration) var(--ease-out);
  }
  .bottom-nav__item.is-active {
    color: var(--brand);
  }
  .bottom-nav__item.is-active .bottom-nav__icon {
    background: var(--brand-soft);
  }
  .bottom-nav__item:active .bottom-nav__icon {
    background: var(--bg-secondary);
  }
}
</style>
