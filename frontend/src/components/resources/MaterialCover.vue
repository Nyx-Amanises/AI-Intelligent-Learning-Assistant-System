<template>
  <ResourceCover :title="title" :subject="subject" :cover-url="imageUrl" />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import ResourceCover from './ResourceCover.vue'
import { getMaterialCoverApi } from '@/api/modules/material'
import { useUserStore } from '@/stores/user'

const props = withDefaults(
  defineProps<{
    materialId: number
    title: string
    subject?: string
    coverUrl?: string | null
  }>(),
  { subject: '' }
)
const user = useUserStore()
const imageUrl = ref('')

// 私有封面通过带鉴权的请求读取；令牌不放进图片 URL。
watch(
  () => [props.materialId, props.coverUrl, user.token] as const,
  async ([id, coverUrl, token], _, onCleanup) => {
    const controller = new AbortController()
    let objectUrl = ''
    imageUrl.value = ''
    onCleanup(() => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    })
    if (!coverUrl || !token) return
    try {
      const response = await getMaterialCoverApi(id, controller.signal)
      if (controller.signal.aborted || !response.data.type.startsWith('image/')) return
      objectUrl = URL.createObjectURL(response.data)
      imageUrl.value = objectUrl
    } catch {
      // 无封面或暂时加载失败时保留默认图，不影响阅读资料。
    }
  },
  { immediate: true }
)
</script>
