<template>
  <span class="type-badge" :class="'type-badge--' + tone" :style="{ '--badge-size': size + 'px' }" aria-hidden="true">{{
    label
  }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** 资料类型小方块：PDF、DOC、MD、TXT，按类型着色。纯装饰，类型文字另在正文中给出。 */
const props = withDefaults(defineProps<{ type?: string | null; size?: number }>(), { type: '', size: 36 })

const kind = computed(() => String(props.type || '').toUpperCase())
const label = computed(
  () =>
    ({ PDF: 'PDF', DOCX: 'DOC', DOC: 'DOC', WORD: 'DOC', MARKDOWN: 'MD', TXT: 'TXT', TEXT: 'TXT' })[kind.value] ||
    'FILE'
)
const tone = computed(() => {
  if (kind.value === 'PDF') return 'pdf'
  if (['DOCX', 'DOC', 'WORD'].includes(kind.value)) return 'word'
  if (['MARKDOWN', 'TXT', 'TEXT'].includes(kind.value)) return 'text'
  return 'other'
})
</script>

<style scoped>
.type-badge {
  display: grid;
  flex: 0 0 var(--badge-size);
  place-items: center;
  width: var(--badge-size);
  height: var(--badge-size);
  border-radius: calc(var(--badge-size) * 0.28);
  font-size: max(11px, calc(var(--badge-size) * 0.28));
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1;
}
.type-badge--pdf {
  color: var(--cinnabar);
  background: color-mix(in srgb, var(--cinnabar) 10%, var(--panel));
}
.type-badge--word {
  color: var(--blue);
  background: var(--blue-soft);
}
.type-badge--text {
  color: var(--brand);
  background: var(--brand-light);
}
.type-badge--other {
  color: var(--muted);
  background: var(--bg-secondary);
}
</style>
