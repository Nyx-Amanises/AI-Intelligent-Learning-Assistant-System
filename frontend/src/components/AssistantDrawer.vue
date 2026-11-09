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
      <AssistantMark :size="26" />
    </span>
    <span class="assistant-fab__label">{{ ASSISTANT_NAME }}</span>
  </button>

  <Teleport to="body">
    <transition name="assistant-overlay">
      <div
        v-if="visible"
        class="assistant-overlay"
        :class="{ 'assistant-overlay--short': viewport.height < 540 }"
        :style="overlayStyle"
        @click.self="closePanel"
      >
        <AssistantFocusTrap
          :trapped="visible"
          :loop="true"
          :focus-trap-el="(isCompactLayout && mobileSidebarOpen ? sidebarRef : panelRef) || undefined"
          focus-start-el="container"
          @focus-after-released.prevent
          @release-requested="handlePanelKeydown"
        >
          <div
            id="global-assistant-panel"
            ref="panelRef"
            class="assistant-panel"
            role="dialog"
            aria-modal="true"
            :aria-label="ASSISTANT_NAME"
            tabindex="-1"
            @keydown="handlePanelKeydown"
          >
            <div v-if="mobileSidebarOpen" class="assistant-mobile-scrim" @click="closeMobileSidebar" />
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
                :title="sessionDetail ? sessionDetail.title || '新对话' : ASSISTANT_NAME"
                :loading="sessionPageLoading"
                :mobile-sidebar-open="mobileSidebarOpen"
                @open-menu="openMobileSidebar"
                @refresh="loadSessionPage(true)"
                @close="closePanel"
              />

              <div v-if="aiMockMode" class="assistant-mock-notice" role="status">
                <strong>演示模式</strong>
                <span>当前使用模拟回复。连接真实模型，可前往「AI 配置」切换使用方式。</span>
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
                :action="pendingAction"
                :action-loading="actionLoading"
                :action-error="actionError"
                :action-busy="actionBusy"
                @send="sendMessage"
                @stop="stopStreaming"
                @delete="removeSession(sessionDetail.id)"
                @pick-prompt="fillPrompt"
                @open-practice="closePanel"
                @decide-action="decideAction"
                @refresh-action="refreshAction"
              />
            </main>
          </div>
        </AssistantFocusTrap>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, useZIndex } from 'element-plus'
import AssistantFocusTrap from 'element-plus/es/components/focus-trap/index.mjs'
import { getAiConfigApi } from '@/api/modules/ai'
import { useUserStore } from '@/stores/user'
import AssistantMark from '@/components/assistant/AssistantMark.vue'
import AssistantSessionSidebar from '@/components/assistant/AssistantSessionSidebar.vue'
import AssistantTopbar from '@/components/assistant/AssistantTopbar.vue'
import AssistantHome from '@/components/assistant/AssistantHome.vue'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import { ASSISTANT_NAME } from '@/config/brand'
import { useAssistantPageBinding } from '@/composables/useAssistantPageBinding'
import { useAssistantSessions } from '@/composables/useAssistantSessions'
import { useAssistantStream } from '@/composables/useAssistantStream'
import { useAssistantActions } from '@/composables/useAssistantActions'
import { useModalShell } from '@/composables/useModalShell'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    showLauncher?: boolean
    launcherElement?: HTMLElement | null
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
const focusLauncherRef = computed(() => props.launcherElement || launcherRef.value)
const sidebarComponentRef = ref<InstanceType<typeof AssistantSessionSidebar> | null>(null)
const topbarRef = ref<InstanceType<typeof AssistantTopbar> | null>(null)
const conversationRef = ref<InstanceType<typeof AssistantConversation> | null>(null)
const sidebarRef = computed(() => sidebarComponentRef.value?.rootRef ?? null)
const mobileMenuRef = computed(() => topbarRef.value?.menuButtonRef ?? null)

const aiMockMode = ref(false)
const aiConfigChecking = ref(false)
const { nextZIndex } = useZIndex()
const overlayZIndex = ref(0)
const viewport = reactive({ height: 0, width: 0, top: 0, left: 0 })
const overlayStyle = computed(() => ({
  zIndex: overlayZIndex.value,
  top: `${viewport.top}px`,
  left: `${viewport.left}px`,
  width: `${viewport.width}px`,
  height: `${viewport.height}px`
}))

const syncViewport = () => {
  const visual = window.visualViewport
  viewport.height = visual?.height || window.innerHeight
  viewport.width = visual?.width || window.innerWidth
  viewport.top = visual?.offsetTop || 0
  viewport.left = visual?.offsetLeft || 0
}

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
  launcherRef: focusLauncherRef,
  mobileMenuRef,
  externalFocusTrap: true,
  onOpen: () => {
    overlayZIndex.value = nextZIndex()
    syncViewport()
    mobileSidebarOpen.value = false
    // 布局持续挂载，收起后保留会话、未发送草稿以及正在生成的回复。
    void loadAiConfigNotice()
    if (!sessionPageLoading.value) void loadSessionPage(false)
  }
})

const {
  sessionPage,
  sessionPageLoading,
  creatingBlank,
  activeSessionId,
  sessionDetail,
  sessionActionBusy,
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

const actionSessionId = computed(() => (visible.value ? sessionDetail.value?.id || null : null))
const {
  action: pendingAction,
  loading: actionLoading,
  error: actionError,
  busy: actionBusy,
  refresh: refreshAction,
  decide: decideAction
} = useAssistantActions({ sessionId: actionSessionId, sending: sendingMessage })

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

const openingPractice = ref(false)
const openPracticeAnalysis = async (practiceSessionId: number) => {
  if (openingPractice.value) return
  if (sendingMessage.value) {
    ElMessage.info('助手正在回复，请先停止生成，再分析这次练习。')
    openPanel()
    return
  }
  if (currentBinding.value.payload.practiceSessionId !== practiceSessionId) {
    ElMessage.warning('练习记录正在切换，请打开记录后重试。')
    return
  }
  openingPractice.value = true
  try {
    openPanel()
    await nextTick()
    if (currentBinding.value.payload.practiceSessionId !== practiceSessionId) return
    const detail = await createSession(true, true)
    if (detail?.currentPracticeSessionId === practiceSessionId) {
      fillPrompt('请分析这次练习的作答情况、失分原因，并给出下一步复习建议。')
    }
  } finally {
    openingPractice.value = false
  }
}

defineExpose({ openPracticeAnalysis })
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
  border-radius: var(--radius-lg);
  background: var(--brand);
  color: var(--on-brand);
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
  color: var(--on-brand);
}

.assistant-fab__label {
  font-size: 14px;
  font-weight: 500;
}

.assistant-overlay {
  position: fixed;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  box-sizing: border-box;
  padding: 16px;
  background: rgb(27 42 32 / 10%);
}

.assistant-overlay-enter-active,
.assistant-overlay-leave-active {
  transition: opacity var(--duration) ease;
}

.assistant-overlay-enter-from,
.assistant-overlay-leave-to {
  opacity: 0;
}
.assistant-overlay-enter-active .assistant-panel,
.assistant-overlay-leave-active .assistant-panel {
  transition: transform var(--duration) var(--ease-out);
}
.assistant-overlay-enter-from .assistant-panel,
.assistant-overlay-leave-to .assistant-panel {
  transform: translateY(8px);
}

.assistant-mobile-scrim {
  display: none;
}

.assistant-panel {
  position: relative;
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  width: min(900px, 100%);
  height: min(760px, 100%);
  max-height: 100%;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel);
  box-shadow: var(--shadow-xl);
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
  padding: 14px 20px;
  background: var(--panel);
  overflow: hidden;
}

.assistant-mock-notice {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  width: min(920px, 100%);
  margin: 0 auto 12px;
  padding: 10px 14px;
  border: 1px solid var(--accent-soft);
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--text);
  line-height: 1.6;
  flex-shrink: 0;
}

/* 问候与推荐可以滚动，输入区始终留在浮窗底部。 */
.assistant-main :deep(.assistant-home) {
  align-items: stretch;
  justify-content: flex-start;
  gap: 16px;
  padding: 12px 0 0;
  overflow: hidden;
}
.assistant-main :deep(.assistant-home__hero) {
  flex: 0 0 auto;
  text-align: left;
}
.assistant-main :deep(.assistant-home__hero h2) {
  margin-top: 6px;
  font-size: 25px;
}
.assistant-main :deep(.assistant-home__hero p) {
  margin: 8px 0 0;
  font-size: 14px;
}
.assistant-main :deep(.assistant-suggestion-row) {
  flex: 1 1 0;
  min-height: 0;
  align-content: flex-start;
  justify-content: flex-start;
  overflow-y: auto;
}
.assistant-main :deep(.assistant-home .assistant-composer) {
  order: 2;
  flex: 0 0 auto;
}
.assistant-main :deep(.assistant-composer--hero) {
  padding: 12px 14px;
}
.assistant-main :deep(.assistant-textarea) {
  min-height: 64px;
}
.assistant-main :deep(.assistant-textarea--compact) {
  min-height: 44px;
}
.assistant-main :deep(.assistant-top-button) {
  min-width: 44px;
}

.assistant-mock-notice strong {
  color: var(--accent);
  font-size: 13px;
  font-weight: 500;
}

.assistant-mock-notice span {
  font-size: 12px;
}

@media (max-width: 900px) {
  .assistant-overlay {
    padding: max(8px, env(safe-area-inset-top)) 8px max(8px, env(safe-area-inset-bottom));
  }

  .assistant-panel {
    grid-template-columns: 1fr;
    width: 100%;
    height: min(740px, 100%);
    max-height: 100%;
    border-radius: 20px;
  }

  .assistant-mobile-scrim {
    position: absolute;
    inset: 0;
    z-index: 2;
    display: block;
    background: rgb(27 42 32 / 30%);
  }

  .assistant-main {
    padding: 4px 16px 12px;
  }
  .assistant-main :deep(.assistant-topbar) {
    min-height: 52px;
    padding-block: 4px;
    margin-bottom: 8px;
  }
  .assistant-panel :deep(.assistant-sidebar) {
    width: min(88%, 320px);
    padding: 18px 16px;
  }
}

@media (max-width: 760px) {
  .assistant-main {
    padding: 4px 14px 12px;
  }
  .assistant-fab {
    right: 16px;
    bottom: 18px;
    padding: 10px;
    border-radius: 50%;
    background: var(--mobile-accent, var(--brand));
    color: var(--text);
  }
  .assistant-fab__icon {
    background: transparent;
    color: inherit;
  }

  .assistant-fab__label {
    display: none;
  }

  .assistant-mock-notice {
    width: 100%;
    margin: 0 0 10px;
    padding: 8px 10px;
    border: 0;
    border-radius: 12px;
    font-size: 13px;
  }
}
@media (max-width: 360px) {
  .assistant-main {
    padding-inline: 12px;
  }
}

.assistant-overlay--short .assistant-mock-notice,
.assistant-overlay--short :deep(.assistant-home__hero),
.assistant-overlay--short :deep(.assistant-conversation__hero) {
  display: none;
}
.assistant-overlay--short :deep(.assistant-home) {
  gap: 8px;
  padding-top: 0;
}
.assistant-overlay--short :deep(.assistant-composer) {
  padding: 8px 12px;
}
.assistant-overlay--short :deep(.assistant-textarea) {
  min-height: 44px;
  max-height: 70px;
}
.assistant-overlay--short :deep(.assistant-composer__topline) {
  display: none;
}
.assistant-overlay--short :deep(.assistant-topbar) {
  min-height: 44px;
  margin-bottom: 4px;
  padding-block: 0;
}

@media (prefers-reduced-motion: reduce) {
  .assistant-overlay-enter-active,
  .assistant-overlay-leave-active,
  .assistant-overlay-enter-active .assistant-panel,
  .assistant-overlay-leave-active .assistant-panel,
  .assistant-panel :deep(button) {
    transition: none;
  }
}
</style>
