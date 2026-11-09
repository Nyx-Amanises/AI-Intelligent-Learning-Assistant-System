<template>
  <Teleport :to="portalTarget">
    <button
      v-show="!open && !confirmationOpen"
      ref="buttonRef"
      type="button"
      class="floating-assistant"
      :class="{
        'floating-assistant--dragging': dragging,
        'floating-assistant--in-dialog': inDialog,
        'floating-assistant--docked': docked
      }"
      :style="{
        transform: docked ? 'none' : `translate3d(${position.x}px, ${position.y}px, 0)`,
        '--assistant-width': `${docked ? 44 : WIDTH}px`,
        '--assistant-height': `${docked ? 44 : HEIGHT}px`
      }"
      :aria-label="`打开${ASSISTANT_NAME}`"
      aria-haspopup="dialog"
      aria-controls="global-assistant-panel"
      :aria-expanded="open"
      :title="docked ? `打开${ASSISTANT_NAME}` : `打开${ASSISTANT_NAME} · 拖动可调整位置，方向键可移动`"
      @pointerdown="startDrag"
      @pointermove="moveDrag"
      @pointerup="endDrag"
      @pointercancel="cancelDrag"
      @lostpointercapture="cancelDrag"
      @click="handleClick"
      @keydown="handleKeydown"
    >
      <AssistantMark :size="docked ? 26 : 28" />
      <span v-if="!docked" class="floating-assistant__label">{{ ASSISTANT_NAME }}</span>
    </button>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import AssistantMark from '@/components/assistant/AssistantMark.vue'
import { ASSISTANT_NAME } from '@/config/brand'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ (event: 'open'): void }>()
const buttonRef = ref<HTMLButtonElement | null>(null)
const portalTarget = shallowRef<string | HTMLElement>('body')
const inDialog = computed(() => portalTarget.value !== 'body')
const docked = computed(
  () =>
    portalTarget.value instanceof HTMLElement && portalTarget.value.classList.contains('resource-reader-assistant-slot')
)
const confirmationOpen = ref(false)
const dragging = ref(false)
const position = reactive({ x: 0, y: 0 })
const WIDTH = 56
const HEIGHT = 60
const GAP = 12
const STORAGE_KEY = 'learning-assistant-position'
let side: 'left' | 'right' = 'right'
let verticalRatio = 0.7
let pointer: { id: number; x: number; y: number; originX: number; originY: number } | null = null
let observer: MutationObserver | null = null
let resizeObserver: ResizeObserver | null = null
let frame = 0

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), Math.max(min, max))
const isVisible = (element: HTMLElement) => {
  const style = getComputedStyle(element)
  return element.getClientRects().length > 0 && style.visibility !== 'hidden' && style.display !== 'none'
}

function bounds() {
  const viewport = window.visualViewport
  const left = viewport?.offsetLeft || 0
  const top = viewport?.offsetTop || 0
  const width = viewport?.width || window.innerWidth
  const height = viewport?.height || window.innerHeight
  const host = portalTarget.value instanceof HTMLElement ? portalTarget.value : null
  const rect = host?.getBoundingClientRect()
  const safeStyle = buttonRef.value ? getComputedStyle(buttonRef.value) : null
  const safeTop = parseFloat(safeStyle?.getPropertyValue('--assistant-safe-top') || '0') || 0
  const safeBottom = parseFloat(safeStyle?.getPropertyValue('--assistant-safe-bottom') || '0') || 0
  const originX = rect ? rect.left + (host?.clientLeft || 0) : 0
  const originY = rect ? rect.top + (host?.clientTop || 0) - (host?.scrollTop || 0) : 0
  const minX = Math.max(left, originX) + GAP - originX
  const maxX = Math.min(left + width, rect?.right ?? width) - GAP - WIDTH - originX
  const minY = Math.max(top + safeTop + GAP, rect ? rect.top + 60 : top + 80) - originY
  let bottom = Math.min(top + height - safeBottom - GAP, rect ? rect.bottom - GAP : Infinity)
  // 页面底栏与练习操作留出触控空间；详情弹层则使用其自身的可视范围。
  if (!host) {
    bottom = Math.min(bottom, top + height - (width <= 760 ? 100 + safeBottom : 28))
    for (const bar of document.querySelectorAll<HTMLElement>('.bottom-nav, .practice-paper-submit__actions')) {
      if (!isVisible(bar)) continue
      const barRect = bar.getBoundingClientRect()
      if (barRect.top > top + height / 2 && barRect.top < top + height) bottom = Math.min(bottom, barRect.top - GAP)
    }
  }
  return { minX, maxX: Math.max(minX, maxX), minY, maxY: Math.max(minY, bottom - HEIGHT - originY) }
}

function snapToEdge() {
  if (docked.value) return
  const box = bounds()
  position.x = side === 'left' ? box.minX : box.maxX
  position.y = box.minY + (box.maxY - box.minY) * verticalRatio
}

function savePosition() {
  if (docked.value) return
  const box = bounds()
  side = position.x < (box.minX + box.maxX) / 2 ? 'left' : 'right'
  verticalRatio = box.maxY > box.minY ? clamp((position.y - box.minY) / (box.maxY - box.minY), 0, 1) : 0.7
  snapToEdge()
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ side, verticalRatio }))
  } catch {
    // 无存储权限时仍可在本次使用中调整位置。
  }
}

async function syncSurface() {
  frame = 0
  confirmationOpen.value = Array.from(document.querySelectorAll<HTMLElement>('.el-message-box')).some(isVisible)
  const dialogs = Array.from(document.querySelectorAll<HTMLElement>('.el-drawer, .el-dialog')).filter(isVisible)
  const zIndex = (element: HTMLElement) =>
    Number(getComputedStyle(element.closest('.el-overlay') || element).zIndex) || 0
  dialogs.sort((first, second) => zIndex(first) - zIndex(second))
  const topDialog = dialogs.at(-1)
  const readingSlot = topDialog?.classList.contains('resource-source-drawer--reading')
    ? topDialog.querySelector<HTMLElement>('.resource-reader-assistant-slot')
    : null
  const target = readingSlot && isVisible(readingSlot) ? readingSlot : topDialog || 'body'
  if (portalTarget.value !== target) {
    cancelDrag()
    const wasFocused = document.activeElement === buttonRef.value
    portalTarget.value = target
    resizeObserver?.disconnect()
    if (target instanceof HTMLElement) resizeObserver?.observe(target)
    await nextTick()
    if (wasFocused) buttonRef.value?.focus({ preventScroll: true })
  }
  if (!pointer) snapToEdge()
}

function scheduleSync() {
  if (!frame) frame = window.requestAnimationFrame(() => void syncSurface())
}

function startDrag(event: PointerEvent) {
  if (docked.value) return
  if (!event.isPrimary || event.button !== 0) return
  pointer = { id: event.pointerId, x: event.clientX, y: event.clientY, originX: position.x, originY: position.y }
  buttonRef.value?.setPointerCapture(event.pointerId)
}

function moveDrag(event: PointerEvent) {
  if (!pointer || pointer.id !== event.pointerId) return
  const deltaX = event.clientX - pointer.x
  const deltaY = event.clientY - pointer.y
  if (!dragging.value && Math.hypot(deltaX, deltaY) < 8) return
  dragging.value = true
  const box = bounds()
  position.x = clamp(pointer.originX + deltaX, box.minX, box.maxX)
  position.y = clamp(pointer.originY + deltaY, box.minY, box.maxY)
}

function endDrag(event: PointerEvent) {
  if (!pointer || pointer.id !== event.pointerId) return
  const moved = dragging.value || Math.hypot(event.clientX - pointer.x, event.clientY - pointer.y) >= 8
  pointer = null
  dragging.value = false
  if (buttonRef.value?.hasPointerCapture(event.pointerId)) buttonRef.value.releasePointerCapture(event.pointerId)
  if (moved) {
    savePosition()
  } else {
    // 触摸拖动后的首次轻点可能不合成 click，直接在有效的指针抬起时打开。
    buttonRef.value?.focus({ preventScroll: true })
    emit('open')
  }
}

function cancelDrag() {
  if (!pointer) return
  pointer = null
  dragging.value = false
  snapToEdge()
}

function handleClick(event: MouseEvent) {
  // 停靠按钮使用原生激活；浮窗的指针激活已由 pointerup 处理。
  if (docked.value || event.detail === 0) emit('open')
}

function handleKeydown(event: KeyboardEvent) {
  if (docked.value) return
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return
  event.preventDefault()
  event.stopPropagation()
  const box = bounds()
  if (event.key === 'ArrowLeft') position.x = box.minX
  if (event.key === 'ArrowRight') position.x = box.maxX
  if (event.key === 'ArrowUp') position.y = clamp(position.y - 32, box.minY, box.maxY)
  if (event.key === 'ArrowDown') position.y = clamp(position.y + 32, box.minY, box.maxY)
  savePosition()
}

onMounted(() => {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    if (saved?.side === 'left' || saved?.side === 'right') side = saved.side
    if (Number.isFinite(saved?.verticalRatio)) verticalRatio = clamp(saved.verticalRatio, 0, 1)
  } catch {
    // 旧的偏好不可读时使用默认停靠位置。
  }
  resizeObserver = new ResizeObserver(scheduleSync)
  observer = new MutationObserver((records) => {
    if (
      records.some(
        (record) =>
          !(record.target instanceof Element) || !record.target.closest('.floating-assistant, .assistant-overlay')
      )
    )
      scheduleSync()
  })
  observer.observe(document.body, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: ['style', 'class']
  })
  window.addEventListener('resize', scheduleSync)
  window.addEventListener('scroll', scheduleSync, { passive: true })
  document.addEventListener('transitionend', scheduleSync)
  window.visualViewport?.addEventListener('resize', scheduleSync)
  window.visualViewport?.addEventListener('scroll', scheduleSync)
  void syncSurface()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  resizeObserver?.disconnect()
  window.cancelAnimationFrame(frame)
  window.removeEventListener('resize', scheduleSync)
  window.removeEventListener('scroll', scheduleSync)
  document.removeEventListener('transitionend', scheduleSync)
  window.visualViewport?.removeEventListener('resize', scheduleSync)
  window.visualViewport?.removeEventListener('scroll', scheduleSync)
})

defineExpose({ buttonRef })
</script>

<style scoped>
.floating-assistant {
  --assistant-safe-top: env(safe-area-inset-top, 0px);
  --assistant-safe-bottom: env(safe-area-inset-bottom, 0px);
  --assistant-ink: #2b6150;
  --assistant-surface: #faf8f1;
  --assistant-tint: #e2efe7;
  --assistant-border: #bfd4c7;
  --assistant-highlight: rgb(255 255 255 / 75%);
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 1px;
  width: var(--assistant-width);
  height: var(--assistant-height);
  padding: 6px 7px 7px;
  border: 1px solid var(--assistant-border);
  border-radius: 16px;
  background: linear-gradient(155deg, var(--assistant-tint), var(--assistant-surface));
  color: var(--assistant-ink);
  box-shadow:
    var(--shadow),
    inset 0 1px 0 var(--assistant-highlight);
  font-family: var(--font-sans);
  cursor: pointer;
  touch-action: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition:
    transform 180ms var(--ease-out),
    box-shadow 180ms ease;
}
.floating-assistant--in-dialog {
  position: absolute;
}
.floating-assistant--docked {
  position: static;
  z-index: auto;
  flex-shrink: 0;
  padding: 8px;
  border-radius: 12px;
  box-shadow: none;
  touch-action: manipulation;
  transition: none;
}
.floating-assistant--dragging {
  cursor: grabbing;
  transition: none;
}
.floating-assistant:hover {
  box-shadow: var(--shadow-md);
}
.floating-assistant:active {
  background: var(--assistant-tint);
}
.floating-assistant:focus-visible {
  outline: 3px solid var(--assistant-ink);
  outline-offset: 3px;
}
.floating-assistant__label {
  font-size: 12px;
  line-height: 16px;
  font-weight: 650;
  letter-spacing: 0.12em;
  text-indent: 0.12em;
  white-space: nowrap;
  pointer-events: none;
}
:global([data-theme='dark'] .floating-assistant),
:global(.dark .floating-assistant) {
  --assistant-ink: #c0e4d3;
  --assistant-surface: #243b32;
  --assistant-tint: #29483c;
  --assistant-border: #4d7763;
  --assistant-highlight: rgb(192 228 211 / 12%);
}
@media (prefers-reduced-motion: reduce) {
  .floating-assistant {
    transition: none;
  }
}
</style>
