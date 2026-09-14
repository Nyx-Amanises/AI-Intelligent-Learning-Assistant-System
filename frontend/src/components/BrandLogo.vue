<template>
  <img
    class="brand-logo"
    :src="variant === 'mark' ? brandMark : brandLogo"
    :alt="APP_NAME"
    :width="variant === 'mark' ? 64 : 184"
    height="64"
    decoding="async"
    draggable="false"
  >
</template>

<script setup lang="ts">
import brandLogo from '@/assets/brand/zhixu-logo.svg'
import brandMark from '@/assets/brand/zhixu-mark.svg'
import { APP_NAME } from '@/config/brand'

withDefaults(defineProps<{ variant?: 'horizontal' | 'mark' }>(), {
  variant: 'horizontal',
})
</script>

<style scoped>
.brand-logo {
  display: block;
  flex-shrink: 0;
  max-width: 100%;
  height: auto;
  object-fit: contain;
  user-select: none;
}
</style>
