<template>
  <nav v-if="route.path === '/dashboard'" class="bottom-nav" aria-label="首页快捷导航">
    <RouterLink
      v-for="item in homeLinks"
      :key="item.path"
      :to="item.path"
      class="bottom-nav__item"
      :aria-label="item.label"
    >
      <MobileIcon :name="item.icon" :size="26" />
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import MobileIcon from '@/components/MobileIcon.vue'

/** 首页的三个手机入口，进入子页后使用顶栏返回。 */
const route = useRoute()
const homeLinks = [
  { path: '/quiz', label: '题集与练习', icon: 'study' },
  { path: '/materials', label: '我的内容', icon: 'archive' },
  { path: '/analytics', label: '我的学习与偏好', icon: 'chart-settings' }
]
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
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: calc(76px + env(safe-area-inset-bottom));
    padding: 6px 20px calc(10px + env(safe-area-inset-bottom));
    border: 0;
    background: transparent;
  }
  .bottom-nav__item {
    display: grid;
    place-items: center;
    width: 44px;
    height: 48px;
    color: #202827;
    -webkit-tap-highlight-color: transparent;
  }
  .bottom-nav__item:active {
    opacity: 0.5;
  }
}
</style>
