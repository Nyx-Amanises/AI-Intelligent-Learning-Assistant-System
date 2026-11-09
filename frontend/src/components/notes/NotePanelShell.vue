<template>
  <el-drawer
    v-if="compact"
    :model-value="modelValue"
    class="note-editor-drawer"
    title="学习笔记"
    size="100%"
    :style="{ top: viewportTop + 'px', height: viewportHeight + 'px' }"
    :close-on-click-modal="false"
    :show-close="false"
    append-to-body
    :before-close="beforeClose"
  >
    <template #header><slot name="header" /></template>
    <slot />
    <template #footer><slot name="footer" /></template>
  </el-drawer>
  <Teleport v-else to="body">
    <aside
      v-if="modelValue"
      class="note-editor-drawer note-desktop-panel"
      aria-label="学习笔记"
      :style="{ zIndex }"
      @keydown.esc.stop="beforeClose()"
    >
      <header class="el-drawer__header"><slot name="header" /></header>
      <div class="el-drawer__body"><slot /></div>
      <footer class="el-drawer__footer"><slot name="footer" /></footer>
    </aside>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useZIndex } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  compact: boolean
  beforeClose: (done?: () => void) => void | Promise<void>
}>()
const { nextZIndex } = useZIndex()
const zIndex = ref(nextZIndex())
const viewportHeight = ref(window.innerHeight)
const viewportTop = ref(0)
function syncViewport() {
  const viewport = window.visualViewport
  // Keep the mobile formatting bar above the on-screen keyboard; leave pinch zoom to the browser.
  viewportHeight.value = viewport && viewport.scale === 1 ? viewport.height : window.innerHeight
  viewportTop.value = viewport && viewport.scale === 1 ? viewport.offsetTop : 0
}
watch(
  () => [props.modelValue, props.compact],
  () => {
    syncViewport()
    if (props.modelValue && !props.compact) zIndex.value = nextZIndex()
  },
  { immediate: true }
)
onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('scroll', syncViewport)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
  window.visualViewport?.removeEventListener('resize', syncViewport)
  window.visualViewport?.removeEventListener('scroll', syncViewport)
})
</script>

<style>
.note-desktop-panel {
  position: fixed;
  inset: 0 0 0 auto;
  display: flex;
  flex-direction: column;
  width: 460px;
  max-width: 100%;
  box-sizing: border-box;
}
.note-desktop-panel > .el-drawer__body {
  flex: 1;
  overflow: auto;
}
.note-desktop-panel > .el-drawer__header,
.note-desktop-panel > .el-drawer__footer {
  flex: 0 0 auto;
}
</style>
