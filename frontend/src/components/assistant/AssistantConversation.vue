<template>
  <section class="assistant-conversation">
    <div class="assistant-conversation__hero">
      <div class="assistant-conversation__meta">
        <span class="assistant-status-chip assistant-status-chip--brand">{{ contextLabel }}</span>
        <span class="assistant-status-chip">消息 {{ session.messages.length }}</span>
      </div>
      <p v-if="lastActiveAt">最近活跃 {{ formatDateTime(lastActiveAt) }}</p>
    </div>

    <div class="visually-hidden" aria-live="polite">{{ liveAnnouncement }}</div>

    <AssistantThread
      ref="threadRef"
      :messages="session.messages"
      :session-id="session.id"
      :streaming-message-id="streamingMessageId"
      :related-tool-calls="relatedToolCalls"
      :memory-map="memoryMap"
      :quick-prompts="quickPrompts"
      :busy="sending"
      @pick-prompt="emit('pick-prompt', $event)"
    />

    <AssistantComposer
      :model-value="draft"
      variant="dock"
      :sending="sending"
      @update:model-value="emit('update:draft', $event)"
      @send="emit('send')"
      @stop="emit('stop')"
    >
      <template #topline>
        <span v-if="session.currentContextType">{{ contextLabel }}</span>
        <button type="button" class="assistant-inline-danger" @click="emit('delete')">删除会话</button>
      </template>
    </AssistantComposer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AssistantRelevantMemory, AssistantSessionDetail, AssistantToolCall } from '@/api/modules/assistant'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantThread from '@/components/assistant/AssistantThread.vue'
import { formatContextLabel, formatDateTime } from '@/utils/assistantFormat'

const props = defineProps<{
  session: AssistantSessionDetail
  draft: string
  sending: boolean
  streamingMessageId: number | null
  relatedToolCalls: Map<number, AssistantToolCall[]>
  memoryMap: Record<number, AssistantRelevantMemory[]>
  quickPrompts: string[]
  /** 供读屏软件播报的整轮结果。 */
  liveAnnouncement: string
}>()

const emit = defineEmits<{
  (event: 'update:draft', value: string): void
  (event: 'send'): void
  (event: 'stop'): void
  (event: 'delete'): void
  (event: 'pick-prompt', prompt: string): void
}>()

const threadRef = ref<InstanceType<typeof AssistantThread> | null>(null)

const contextLabel = computed(() =>
  formatContextLabel(props.session.currentContextType, props.session.currentContextId, props.session)
)

const lastActiveAt = computed(
  () => props.session.lastMessageAt || props.session.updatedAt || props.session.createdAt
)

defineExpose({
  scrollToBottom: (force?: boolean) => threadRef.value?.scrollToBottom(force)
})
</script>

<style scoped>
.assistant-conversation {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.assistant-conversation__hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: min(920px, 100%);
  margin: 0 auto;
  padding: 0 0 14px;
  border-bottom: 1px solid var(--line);
}

.assistant-conversation__hero p {
  color: var(--text-secondary);
}

.assistant-conversation__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.assistant-status-chip {
  display: inline-flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.assistant-status-chip--brand {
  color: var(--brand);
}

.assistant-inline-danger {
  min-height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--red);
  font-size: 12px;
  cursor: pointer;
}

@media (max-width: 640px) {
  .assistant-conversation {
    padding-bottom: calc(env(safe-area-inset-bottom) + 4px);
  }

  .assistant-conversation__hero {
    display: block;
    width: 100%;
    margin: 0;
    padding: 2px 0 12px;
  }

  .assistant-conversation__meta {
    gap: 8px;
  }

  .assistant-conversation__hero p {
    margin: 10px 0 0;
    font-size: 12px;
  }
}
</style>
