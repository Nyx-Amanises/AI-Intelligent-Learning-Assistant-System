<template>
  <div class="assistant-thread-wrap">
    <div
      ref="threadRef"
      class="assistant-thread"
      role="log"
      aria-label="对话消息"
      :aria-busy="busy"
      @scroll.passive="handleScroll"
    >
      <div v-if="!messages.length" class="assistant-thread__empty">
        <strong>这段对话已经准备好了</strong>
        <p>直接开始提问，或者点一条建议让我先帮你起个头。</p>
        <div class="assistant-suggestion-row">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            type="button"
            class="assistant-suggestion-chip"
            @click="emit('pick-prompt', prompt)"
          >
            {{ prompt }}
          </button>
        </div>
      </div>

      <AssistantMessageTurn
        v-for="message in messages"
        :key="message.id"
        :message="message"
        :streaming="streamingMessageId === message.id"
        :tool-calls="relatedToolCalls.get(message.id) || EMPTY_TOOL_CALLS"
        :memories="memoryMap[message.id] || EMPTY_MEMORIES"
        @open-practice="emit('openPractice')"
      />
    </div>

    <button
      v-if="!stickToBottom"
      type="button"
      class="assistant-thread__jump"
      :class="{ 'assistant-thread__jump--unread': hasUnreadBelow }"
      @click="jumpToLatest"
    >
      <AppIcon name="chevron-down" :size="16" />
      <span>{{ hasUnreadBelow ? '有新内容' : '回到最新' }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { AssistantMessage, AssistantRelevantMemory, AssistantToolCall } from '@/api/modules/assistant'
import AppIcon from '@/components/AppIcon.vue'
import AssistantMessageTurn from '@/components/assistant/AssistantMessageTurn.vue'

const props = defineProps<{
  messages: AssistantMessage[]
  /** 当前会话 id；切换会话时重置滚动状态并回到底部。 */
  sessionId: number
  /** 正在流式生成的助手消息 id。 */
  streamingMessageId: number | null
  /** 助手消息 id -> 本轮工具调用，由上层一次性算好。 */
  relatedToolCalls: Map<number, AssistantToolCall[]>
  memoryMap: Record<number, AssistantRelevantMemory[]>
  quickPrompts: string[]
  busy: boolean
}>()

const emit = defineEmits<{
  (event: 'pick-prompt', prompt: string): void
  (event: 'openPractice'): void
}>()

const EMPTY_TOOL_CALLS: AssistantToolCall[] = []
const EMPTY_MEMORIES: AssistantRelevantMemory[] = []
const THREAD_BOTTOM_THRESHOLD = 48

const threadRef = ref<HTMLElement | null>(null)
/** 用户是否停留在消息列表底部；只有在底部时新内容才会自动滚动。 */
const stickToBottom = ref(true)
/** 用户上翻期间是否有新内容到达。 */
const hasUnreadBelow = ref(false)
let scrollFrame: number | undefined

const isNearBottom = (element: HTMLElement) =>
  element.scrollHeight - element.scrollTop - element.clientHeight <= THREAD_BOTTOM_THRESHOLD

const handleScroll = () => {
  const element = threadRef.value
  if (!element) {
    return
  }
  const nearBottom = isNearBottom(element)
  stickToBottom.value = nearBottom
  if (nearBottom) {
    hasUnreadBelow.value = false
  }
}

/**
 * 滚动到底部（rAF 节流）。默认只在用户本来就停留在底部时才滚动，避免用户上翻阅读时被强行拉回；
 * `force` 用于用户主动发送消息、切换会话等场景。
 */
const scrollToBottom = (force = false) => {
  if (!threadRef.value) {
    return
  }
  if (force) {
    stickToBottom.value = true
  } else if (!stickToBottom.value) {
    hasUnreadBelow.value = true
    return
  }
  if (scrollFrame !== undefined) {
    return
  }
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = undefined
    const target = threadRef.value
    if (!target) {
      return
    }
    target.scrollTop = target.scrollHeight
    stickToBottom.value = true
    hasUnreadBelow.value = false
  })
}

const jumpToLatest = () => {
  scrollToBottom(true)
}

watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

watch(
  () => props.sessionId,
  async () => {
    stickToBottom.value = true
    hasUnreadBelow.value = false
    await nextTick()
    scrollToBottom(true)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (scrollFrame !== undefined) {
    window.cancelAnimationFrame(scrollFrame)
  }
})

defineExpose({ scrollToBottom, jumpToLatest })
</script>

<style scoped>
.assistant-thread-wrap {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.assistant-thread {
  flex: 1;
  min-height: 0;
  margin-top: 14px;
  padding: 0 0 16px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.assistant-thread::-webkit-scrollbar {
  width: 8px;
}

.assistant-thread::-webkit-scrollbar-track {
  background: transparent;
}

.assistant-thread::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--line);
}

.assistant-thread::-webkit-scrollbar-thumb:hover {
  background: var(--muted);
}

.assistant-thread__empty {
  width: min(920px, 100%);
  margin: 0 auto;
  padding: 12px 0 18px;
}

.assistant-thread__empty strong {
  display: block;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.assistant-thread__empty p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.assistant-suggestion-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  max-width: 820px;
}

.assistant-suggestion-chip {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    color 0.18s ease;
}

.assistant-suggestion-chip:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.assistant-thread__jump {
  position: absolute;
  bottom: 22px;
  left: 50%;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 6px 14px 6px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel);
  color: var(--text-secondary);
  font: inherit;
  font-size: 12px;
  box-shadow: var(--shadow-md);
  transform: translateX(-50%);
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background-color 0.15s ease;
}

.assistant-thread__jump:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.assistant-thread__jump--unread {
  border-color: var(--brand);
  background: var(--brand-light);
  color: var(--brand);
}

@media (max-width: 640px) {
  .assistant-thread {
    margin-top: 10px;
    padding: 0 0 10px;
    scrollbar-gutter: auto;
  }

  .assistant-thread__empty {
    padding: 8px 0 16px;
  }

  .assistant-thread__empty strong {
    font-size: 24px;
  }

  .assistant-suggestion-row {
    flex-direction: column;
    align-items: flex-start;
    width: 100%;
    gap: 8px;
  }

  .assistant-suggestion-chip {
    width: 100%;
    padding: 10px 12px;
    border-radius: 9px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-suggestion-chip,
  .assistant-thread__jump {
    transition: none;
  }
}
</style>
