<template>
  <div
    class="assistant-turn"
    :class="{
      'assistant-turn--user': isUser,
      'assistant-turn--assistant': !isUser
    }"
  >
    <div class="assistant-turn__meta">
      <span
        class="assistant-turn__avatar"
        :class="{
          'assistant-turn__avatar--user': isUser,
          'assistant-turn__avatar--assistant': !isUser
        }"
      >
        <AppIcon :name="isUser ? 'user' : 'assistant'" :size="16" />
      </span>
      <span class="assistant-turn__name">{{ isUser ? '你' : '学习助手' }}</span>
      <span class="assistant-turn__time">{{ formatDateTime(message.createdAt) }}</span>
    </div>

    <div v-if="streaming && !message.contentText" class="assistant-thinking">
      <span class="assistant-thinking__dot" />
      <span class="assistant-thinking__dot" />
      <span class="assistant-thinking__dot" />
      <span>正在整理上下文并开始生成...</span>
    </div>

    <div
      v-else
      class="assistant-turn__content"
      :class="{
        'assistant-turn__content--assistant': !isUser,
        'assistant-turn__content--streaming': streaming
      }"
    >
      <LearningNoteContent v-if="!isUser" :text="message.contentText" />
      <template v-else>{{ message.contentText || '' }}</template>
    </div>
    <p v-if="message.localStatus === 'stopped'" class="assistant-turn__note">已停止生成</p>
    <p v-else-if="message.localStatus === 'timeout'" class="assistant-turn__note assistant-turn__note--warn">
      助手长时间没有响应，已停止等待。可以重新发送试试。
    </p>

    <AssistantCitations v-if="!isUser && evidence" :evidence="evidence" />

    <AssistantToolActivity v-if="!isUser && toolCalls.length" :tools="toolCalls" @open-practice="emit('openPractice')" />
    <details v-if="!isUser && memories.length" class="assistant-turn__trace">
      <summary>参考的学习记忆</summary>
      <div class="assistant-trace-block">
        <div class="assistant-trace-block__label">参考记忆</div>
        <div class="assistant-trace-chip-row">
          <span v-for="memory in memories" :key="memory.id" class="assistant-trace-chip assistant-trace-chip--memory">
            {{ memory.topicName || memory.memoryType || '会话记忆' }}
          </span>
        </div>
      </div>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AssistantMessage, AssistantRelevantMemory, AssistantToolCall } from '@/api/modules/assistant'
import AppIcon from '@/components/AppIcon.vue'
import LearningNoteContent from '@/components/LearningNoteContent.vue'
import AssistantCitations from '@/components/assistant/AssistantCitations.vue'
import AssistantToolActivity from '@/components/assistant/AssistantToolActivity.vue'
import { formatDateTime, isUserMessage, parseRagEvidence } from '@/utils/assistantFormat'

const props = defineProps<{
  message: AssistantMessage
  /** 这条助手消息是否正在流式生成。 */
  streaming: boolean
  /** 本轮回复对应的工具调用（挂在对应的用户消息上）。 */
  toolCalls: AssistantToolCall[]
  memories: AssistantRelevantMemory[]
}>()
const emit = defineEmits<{ openPractice: [] }>()

const isUser = computed(() => isUserMessage(props.message.role))

/** 本轮资料问答的检索依据；没有调用 rag.retrieve 时为 null。 */
const evidence = computed(() => (isUser.value ? null : parseRagEvidence(props.toolCalls)))
</script>

<style scoped>
.assistant-turn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: min(920px, 100%);
  margin: 0 auto 28px;
}

.assistant-turn--user {
  align-items: flex-end;
  margin-bottom: 20px;
}

.assistant-turn__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
}

.assistant-turn--user .assistant-turn__meta {
  justify-content: flex-end;
}

.assistant-turn__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
}

.assistant-turn__avatar--assistant {
  background: var(--brand-soft);
  color: var(--brand);
}

.assistant-turn__avatar--user {
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

.assistant-turn__content {
  width: 100%;
  margin-top: 8px;
  color: var(--text);
  font-size: 15px;
  line-height: 1.95;
  white-space: pre-wrap;
  word-break: break-word;
}

.assistant-turn__content--assistant {
  white-space: normal;
}

.assistant-turn__content--assistant :deep(.learning-note) {
  font-size: inherit;
  line-height: inherit;
}

.assistant-turn__content--assistant :deep(.learning-note__paragraph) {
  margin-bottom: 12px;
}

.assistant-turn__content--assistant :deep(.learning-note__heading) {
  margin-top: 18px;
}

/* 流式生成时把闪烁光标贴在最后一个块的末尾，而不是另起一行。 */
.assistant-turn__content--streaming :deep(.learning-note > :last-child)::after {
  content: '▍';
  display: inline-block;
  margin-left: 2px;
  color: var(--brand);
  animation: assistant-cursor 0.9s steps(1) infinite;
}

.assistant-turn--user .assistant-turn__content {
  width: auto;
  max-width: min(640px, 72%);
  padding: 14px 18px;
  border: 1px solid var(--line);
  border-radius: 16px 16px 5px 16px;
  background: var(--brand-light);
  text-align: left;
}

.assistant-turn__note {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.assistant-turn__note--warn {
  color: var(--accent);
}

.assistant-thinking {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  margin-top: 8px;
  color: var(--text-secondary);
}

.assistant-thinking__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
  animation: assistant-bounce 1.1s ease-in-out infinite;
}

.assistant-thinking__dot:nth-child(2) {
  animation-delay: 0.12s;
}

.assistant-thinking__dot:nth-child(3) {
  animation-delay: 0.24s;
}

.assistant-turn__trace {
  width: 100%;
  margin-top: 10px;
  padding: 12px 0 0;
  border-top: 1px solid var(--line);
}

.assistant-turn__trace summary {
  min-height: 36px;
  padding-top: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.assistant-trace-block + .assistant-trace-block {
  margin-top: 14px;
}

.assistant-trace-block__label {
  margin: 12px 0 8px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.assistant-trace-chip-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.assistant-trace-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 12px;
}

.assistant-trace-chip.assistant-inline-chip--success {
  background: var(--green-soft);
}

.assistant-trace-chip.assistant-inline-chip--warning {
  background: var(--accent-soft);
}

.assistant-trace-chip.assistant-inline-chip--danger {
  background: var(--red-soft);
}

.assistant-trace-chip--memory {
  color: var(--brand);
}

.assistant-trace-block pre {
  margin: 0;
  padding: 12px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

@keyframes assistant-bounce {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@keyframes assistant-cursor {
  0%,
  49% {
    opacity: 1;
  }
  50%,
  100% {
    opacity: 0;
  }
}

@media (max-width: 640px) {
  .assistant-turn {
    margin-bottom: 22px;
  }

  .assistant-turn__content {
    line-height: 1.85;
  }

  .assistant-turn--user .assistant-turn__content {
    max-width: 90%;
    padding: 12px 15px;
    border-radius: 14px 14px 4px 14px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-thinking__dot,
  .assistant-turn__content--streaming :deep(.learning-note > :last-child)::after {
    animation: none;
  }
}
</style>
