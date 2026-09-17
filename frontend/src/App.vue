<template>
  <router-view v-slot="{ Component }">
    <transition name="app-crossfade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<style>
.app-crossfade-enter-active,
.app-crossfade-leave-active {
  transition: opacity 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}
.app-crossfade-enter-from,
.app-crossfade-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .app-crossfade-enter-active,
  .app-crossfade-leave-active {
    transition: none;
  }
}
</style>
