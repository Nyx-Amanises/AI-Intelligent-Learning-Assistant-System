<template>
  <el-config-provider :locale="zhCn" :z-index="3000">
    <router-view v-slot="{ Component }">
      <transition name="app-crossfade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

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
