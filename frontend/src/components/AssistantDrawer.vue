<template>
  <button
    v-if="!visible && showLauncher"
    ref="launcherRef"
    type="button"
    class="assistant-fab"
    :aria-label="`打开${ASSISTANT_NAME}`"
    aria-haspopup="dialog"
    @click="openPanel"
  >
    <span class="assistant-fab__icon">
      <AppIcon name="assistant" :size="24" />
    </span>
    <span class="assistant-fab__label">{{ ASSISTANT_NAME }}</span>
  </button>

  <Teleport to="body">
    <transition name="assistant-overlay">
      <div v-if="visible" class="assistant-overlay" @click.self="closePanel">
        <div v-if="mobileSidebarOpen" class="assistant-mobile-scrim" @click="closeMobileSidebar" />
        <div
          ref="panelRef"
          class="assistant-panel"
          role="dialog"
          aria-modal="true"
          :aria-label="ASSISTANT_NAME"
          tabindex="-1"
          @keydown="handlePanelKeydown"
        >
          <AssistantSessionSidebar
            ref="sidebarComponentRef"
            :sessions="sessionPage.records"
            :total="sessionPage.total"
            :active-session-id="activeSessionId"
            :loading="sessionPageLoading"
            :creating="creatingBlank"
            :busy-session-ids="busySessionIds"
            :binding-label="currentBinding.label"
            :binding-helper-text="currentBinding.helperText"
            :mobile-open="mobileSidebarOpen"
            :sidebar-inert="isCompactLayout && !mobileSidebarOpen"
            @create="createSession(false)"
            @refresh="loadSessionPage(true)"
            @select="selectSession"
            @command="handleSessionCommand"
            @close-mobile="closeMobileSidebar"
          />

          <main class="assistant-main" :inert="isCompactLayout && mobileSidebarOpen">
            <AssistantTopbar
              ref="topbarRef"
              :title="sessionDetail ? sessionDetail.title || '新对话' : '今天继续哪一块？'"
              :loading="sessionPageLoading"
              :mobile-sidebar-open="mobileSidebarOpen"
              @open-menu="openMobileSidebar"
              @refresh="loadSessionPage(true)"
              @close="closePanel"
            />

            <div v-if="aiMockMode" class="assistant-mock-notice">
              <strong>当前使用模拟回复</strong>
              <span>当前回复由模拟模式提供。需要真实 AI 回答时，可在 AI 设置中关闭模拟模式。</span>
            </div>

            <AssistantHome
              v-if="!sessionDetail"
              v-model:draft="draftMessage"
              :greeting-name="greetingName"
              :binding-label="currentBinding.label"
              :quick-prompts="quickPrompts"
              :sending="sendingMessage"
              @send="sendMessage"
              @pick-prompt="fillPrompt"
            />

            <AssistantConversation
              v-else
              ref="conversationRef"
              v-model:draft="draftMessage"
              :session="sessionDetail"
              :sending="sendingMessage"
              :streaming-message-id="streamingMessageId"
              :related-tool-calls="relatedToolCallMap"
              :memory-map="memoryMap"
              :quick-prompts="quickPrompts"
              :live-announcement="liveAnnouncement"
              @send="sendMessage"
              @stop="stopStreaming"
              @delete="removeSession(sessionDetail.id)"
              @pick-prompt="fillPrompt"
            />
          </main>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getAiConfigApi } from '@/api/modules/ai'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/AppIcon.vue'
import AssistantSessionSidebar from '@/components/assistant/AssistantSessionSidebar.vue'
import AssistantTopbar from '@/components/assistant/AssistantTopbar.vue'
import AssistantHome from '@/components/assistant/AssistantHome.vue'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import { ASSISTANT_NAME } from '@/config/brand'
import { useAssistantPageBinding } from '@/composables/useAssistantPageBinding'
import { useAssistantSessions } from '@/composables/useAssistantSessions'
import { useAssistantStream } from '@/composables/useAssistantStream'
import { useModalShell } from '@/composables/useModalShell'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    showLauncher?: boolean
  }>(),
  {
    showLauncher: true
  }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const userStore = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const panelRef = ref<HTMLElement | null>(null)
const launcherRef = ref<HTMLButtonElement | null>(null)
const sidebarComponentRef = ref<InstanceType<typeof AssistantSessionSidebar> | null>(null)
const topbarRef = ref<InstanceType<typeof AssistantTopbar> | null>(null)
const conversationRef = ref<InstanceType<typeof AssistantConversation> | null>(null)
const sidebarRef = computed(() => sidebarComponentRef.value?.rootRef ?? null)
const mobileMenuRef = computed(() => topbarRef.value?.menuButtonRef ?? null)

const aiMockMode = ref(false)
const aiConfigChecking = ref(false)

const greetingName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '同学')

const { currentBinding } = useAssistantPageBinding()

const {
  isCompactLayout,
  mobileSidebarOpen,
  openPanel,
  closePanel,
  openMobileSidebar,
  closeMobileSidebar,
  handlePanelKeydown
} = useModalShell({
  visible,
  panelRef,
  sidebarRef,
  launcherRef,
  mobileMenuRef,
  onOpen: () => {
    // 正在生成回复时重新打开面板，保留当前会话与流式状态。
    if (sendingMessage.value) return
    mobileSidebarOpen.value = false
    resetSessionState()
    resetStreamState()
    void loadAiConfigNotice()
    void loadSessionPage(false)
  }
})

const {
  sessionPage,
  sessionPageLoading,
  creatingBlank,
  activeSessionId,
  sessionDetail,
  sessionActionBusy,
  resetSessionState,
  loadSessionPage,
  loadSessionDetail,
  selectSession,
  createSession,
  removeSession,
  handleSessionCommand
} = useAssistantSessions({
  resolveBoundPayload: () => (currentBinding.value.bindable ? currentBinding.value.payload : null),
  beforeSwitch: () => abortStreamingIfNeeded(),
  isStreamingSession: (sessionId) => pendingTurn.value?.sessionId === sessionId,
  onDetailLoaded: (detail) => ingestToolCalls(detail.recentToolCalls),
  afterSelect: () => closeMobileSidebar()
})

const {
  draftMessage,
  sendingMessage,
  pendingTurn,
  liveAnnouncement,
  memoryMap,
  streamingMessageId,
  relatedToolCallMap,
  ingestToolCalls,
  resetStreamState,
  sendMessage,
  stopStreaming,
  abortStreamingIfNeeded
} = useAssistantStream({
  sessionDetail,
  activeSessionId,
  sessionPage,
  loadSessionPage,
  loadSessionDetail,
  createSession,
  shouldBindCurrentPage: () => currentBinding.value.bindable,
  requestScroll: (force) => conversationRef.value?.scrollToBottom(force)
})

const busySessionIds = computed(() => sessionPage.records.map((item) => item.id).filter((id) => sessionActionBusy(id)))

const quickPrompts = computed(() => {
  const contextType = String(sessionDetail.value?.currentContextType || '').toUpperCase()

  if (contextType === 'MATERIAL') {
    return ['帮我梳理这份资料的核心知识点', '根据这份资料生成一份复习提纲', '这份资料里最值得优先掌握的内容是什么']
  }

  if (contextType === 'PRACTICE_SESSION') {
    return ['帮我分析这次练习的薄弱点', '简答题为什么容易丢分', '根据这次练习给我一个复习顺序']
  }

  if (contextType === 'QUESTION_SET') {
    return ['这套题主要覆盖了哪些知识点', '这套题的难度分布怎么样', '刷完这套题后下一步怎么复习']
  }

  return ['帮我找到最近上传的学习资料', '如何把知识点整理成复习提纲', '怎样安排一轮错题复习']
})

const loadAiConfigNotice = async () => {
  if (aiConfigChecking.value) {
    return
  }
  aiConfigChecking.value = true
  try {
    const res = await getAiConfigApi()
    aiMockMode.value = Boolean(res.data?.data?.mockMode)
  } catch {
    aiMockMode.value = false
  } finally {
    aiConfigChecking.value = false
  }
}

const fillPrompt = (prompt: string) => {
  draftMessage.value = prompt
  panelRef.value?.querySelector<HTMLTextAreaElement>('textarea')?.focus()
}
</script>

<style scoped>
.assistant-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 12px 18px;
  border: 0;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  box-shadow: var(--shadow-lg);
  cursor: pointer;
}

.assistant-fab__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgb(255 255 255 / 16%);
  color: #fff;
}

.assistant-fab__label {
  font-size: 14px;
  font-weight: 700;
}

.assistant-overlay {
  position: fixed;
  inset: 0;
  z-index: 1800;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(27 42 32 / 32%);
  backdrop-filter: blur(4px);
}

.assistant-overlay-enter-active,
.assistant-overlay-leave-active {
  transition: opacity 0.24s ease;
}

.assistant-overlay-enter-from,
.assistant-overlay-leave-to {
  opacity: 0;
}

.assistant-mobile-scrim {
  display: none;
}

.assistant-panel {
  position: relative;
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  width: min(1380px, 100%);
  height: min(920px, calc(100dvh - 40px));
  max-height: calc(100dvh - 40px);
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--panel);
  box-shadow: 0 28px 80px rgb(27 42 32 / 18%);
  outline: none;
  overflow: hidden;
}

/* 子组件里的按钮也统一使用品牌色焦点环。 */
.assistant-panel :deep(button:focus-visible),
.assistant-panel :deep(summary:focus-visible),
.assistant-fab:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}

.assistant-main {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  min-width: 0;
  min-height: 0;
  height: 100%;
  padding: 22px 28px 18px;
  background: var(--panel);
  overflow: hidden;
}

.assistant-mock-notice {
  width: min(920px, 100%);
  margin: 0 auto 12px;
  padding: 10px 14px;
  border: 1px solid var(--accent-soft);
  border-left: 3px solid var(--accent);
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--text);
  line-height: 1.6;
}

.assistant-mock-notice strong {
  display: block;
  font-size: 13px;
}

.assistant-mock-notice span {
  display: block;
  margin-top: 2px;
  font-size: 12px;
}

@media (max-width: 900px) {
  .assistant-overlay {
    padding: 0;
    backdrop-filter: none;
  }

  .assistant-panel {
    grid-template-columns: 1fr;
    width: 100%;
    height: 100dvh;
    max-height: 100dvh;
    border: 0;
    border-radius: 0;
  }

  .assistant-mobile-scrim {
    position: absolute;
    inset: 0;
    z-index: 2;
    display: block;
    background: rgb(27 42 32 / 30%);
  }

  .assistant-main {
    padding: 0 18px calc(12px + env(safe-area-inset-bottom));
  }
}

@media (max-width: 640px) {
  .assistant-fab {
    right: 16px;
    bottom: 18px;
    padding: 10px;
    border-radius: 50%;
  }

  .assistant-fab__label {
    display: none;
  }

  .assistant-mock-notice {
    width: 100%;
    margin: 0 0 10px;
    padding: 9px 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-overlay-enter-active,
  .assistant-overlay-leave-active,
  .assistant-panel :deep(button) {
    transition: none;
  }
}
</style>
