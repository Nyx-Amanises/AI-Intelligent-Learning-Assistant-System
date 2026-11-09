<template>
  <NodeViewWrapper as="figure" class="note-image" :class="{ 'is-selected': selected }" contenteditable="false">
    <img
      v-if="imageUrl"
      :src="imageUrl"
      :alt="node.attrs.alt || '笔记插图'"
      :width="node.attrs.width"
      :height="node.attrs.height"
      draggable="true"
      data-drag-handle
      tabindex="0"
      @click="selectImage"
      @keydown.enter.prevent="preview = true"
    />
    <div v-else class="note-image-status" role="status">
      <span>{{ error || '图片加载中…' }}</span>
      <button v-if="error" type="button" @click="retry++">重试</button>
    </div>
    <div v-if="selected" class="note-image-actions">
      <input
        :value="node.attrs.alt"
        aria-label="图片说明"
        maxlength="200"
        placeholder="图片说明（选填）"
        @input="updateAttributes({ alt: ($event.target as HTMLInputElement).value })"
      />
      <button type="button" aria-label="查看大图" :disabled="!imageUrl" @click="preview = true">
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          aria-hidden="true"
        >
          <path d="M9 4H4v5m11-5h5v5M4 15v5h5m11-5v5h-5" />
        </svg>
      </button>
      <button type="button" aria-label="删除图片" @click="deleteNode()">
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 10v7m4-7v7" />
        </svg>
      </button>
    </div>
    <figcaption v-else-if="node.attrs.alt">{{ node.attrs.alt }}</figcaption>
    <el-image-viewer v-if="preview && imageUrl" :url-list="[imageUrl]" teleported @close="preview = false" />
  </NodeViewWrapper>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3'
import { getNoteImageApi } from '@/api/modules/note'
import { useUserStore } from '@/stores/user'

const props = defineProps(nodeViewProps)
const user = useUserStore()
const imageUrl = ref('')
const error = ref('')
const retry = ref(0)
const preview = ref(false)
function selectImage() {
  const position = props.getPos()
  if (position !== undefined) props.editor.commands.setNodeSelection(position)
}
watch(
  () => [props.node.attrs.src, user.token, retry.value] as const,
  async ([src, token], _, cleanup) => {
    const controller = new AbortController()
    let url = ''
    imageUrl.value = error.value = ''
    preview.value = false
    cleanup(() => {
      controller.abort()
      if (url) URL.revokeObjectURL(url)
    })
    if (!token) return
    try {
      const blob = await getNoteImageApi(src, controller.signal)
      if (controller.signal.aborted) return
      url = URL.createObjectURL(blob)
      imageUrl.value = url
    } catch {
      if (!controller.signal.aborted) error.value = '图片暂时无法加载'
    }
  },
  { immediate: true }
)
</script>

<style>
.note-rich-editor .note-image {
  margin: 20px 0;
  border-radius: 8px;
  outline: 2px solid transparent;
}
.note-rich-editor .note-image.is-selected {
  outline-color: var(--brand);
}
.note-image img {
  display: block;
  width: 100%;
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  cursor: pointer;
}
.note-image-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 120px;
  padding: 16px;
  background: var(--panel-hover);
  font-size: 13px;
  color: var(--muted);
}
.note-image-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: var(--panel-hover);
}
.note-image-actions input {
  flex: 1;
  min-width: 0;
  padding: 8px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
}
.note-image-actions button {
  display: grid;
  place-items: center;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}
.note-image-actions button:hover {
  background: var(--brand-light);
  color: var(--brand);
}
.note-image figcaption {
  padding: 6px 8px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}
.note-image-uploading {
  display: inline-block;
  margin-block: 12px;
  padding: 10px 16px;
  border-radius: 8px;
  background: var(--brand-light);
  color: var(--brand);
  font-size: 13px;
}
</style>
