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
.app-crossfade-enter-active {
  transition: opacity var(--duration-slow) var(--ease-out);
}
.app-crossfade-leave-active {
  transition: opacity var(--duration-fast) ease-in;
}
.app-crossfade-enter-from,
.app-crossfade-leave-to {
  opacity: 0;
}
</style>
