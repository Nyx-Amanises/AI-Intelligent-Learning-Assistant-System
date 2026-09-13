<template>
  <button
    v-if="!visible && showLauncher"
    ref="launcherRef"
    type="button"
    class="assistant-fab"
    aria-label="打开 AI 学习助手"
    aria-haspopup="dialog"
    @click="openPanel"
  >
    <span class="assistant-fab__icon">
      <AppIcon name="assistant" :size="24" />
    </span>
    <span class="assistant-fab__label">AI 学习助手</span>
  </button>

  <Teleport to="body">
  <transition name="assistant-overlay">
    <div v-if="visible" class="assistant-overlay" @click.self="closePanel">
      <div
        v-if="mobileSidebarOpen"
        class="assistant-mobile-scrim"
        @click="closeMobileSidebar"
      />
      <div
        ref="panelRef"
        class="assistant-panel"
        role="dialog"
        aria-modal="true"
        aria-label="AI 学习助手"
        tabindex="-1"
        @keydown="handlePanelKeydown"
      >
        <aside
          ref="sidebarRef"
          class="assistant-sidebar"
          :class="{ 'assistant-sidebar--mobile-open': mobileSidebarOpen }"
          :inert="isCompactLayout && !mobileSidebarOpen"
          aria-label="学习助手会话"
        >
          <div class="assistant-sidebar__brand">
            <div class="assistant-sidebar__brand-row">
              <span class="assistant-sidebar__logo">
                <AppIcon name="copilot" :size="25" />
              </span>
              <div>
                <div class="assistant-sidebar__kicker">让知识，成为自己的</div>
                <h2>AI 学习助手</h2>
              </div>
              <button type="button" class="assistant-sidebar__close" aria-label="收起会话列表" @click="closeMobileSidebar">收起</button>
            </div>
            <p>{{ currentBinding.helperText }}</p>
          </div>

          <div class="assistant-context-card">
            <span class="assistant-context-card__label">对话范围</span>
            <strong>{{ currentBinding.label }}</strong>
            <em>提问时说明资料或题集的名称，便于找到学习内容。</em>
          </div>

          <div class="assistant-sidebar__actions">
            <button
              type="button"
              class="assistant-control assistant-control--primary"
              :disabled="creatingBlank"
              @click="createSession(false)"
            >
              {{ creatingBlank ? '创建中...' : '新对话' }}
            </button>
            <button
              type="button"
              class="assistant-control assistant-control--ghost"
              :disabled="sessionPageLoading"
              @click="loadSessionPage(true)"
            >
              {{ sessionPageLoading ? '刷新中...' : '刷新列表' }}
            </button>
          </div>

          <div class="assistant-session-header">
            <div>
              <span>对话</span>
              <strong>{{ sessionPage.total }}</strong>
            </div>
          </div>

          <div v-if="sessionPageLoading && !sessionPage.records.length" class="assistant-sidebar__empty">
            正在加载会话...
          </div>
          <div v-else-if="!sessionPage.records.length" class="assistant-sidebar__empty">
            还没有对话，先新建一个会话吧。
          </div>
          <div v-else class="assistant-session-list">
            <article
              v-for="item in sessionPage.records"
              :key="item.id"
              class="assistant-session-card"
              :class="{
                'assistant-session-card--active': activeSessionId === item.id,
                'assistant-session-card--pinned': isSessionPinned(item),
                'assistant-session-card--disabled': sendingMessage
              }"
            >
              <div class="assistant-session-card__top">
                <span
                  v-if="isSessionPinned(item)"
                  class="assistant-session-card__pin"
                  title="已置顶"
                >
                  <AppIcon name="pin" :size="13" />
                </span>
                <button
                  type="button"
                  class="assistant-session-card__select"
                  :disabled="sendingMessage"
                  :aria-current="activeSessionId === item.id ? 'true' : undefined"
                  :title="item.title || '新对话'"
                  @click="selectSession(item.id)"
                >{{ item.title || '新对话' }}</button>
                <el-dropdown
                  trigger="click"
                  popper-class="assistant-session-menu"
                  :disabled="sendingMessage || sessionActionBusy(item.id)"
                  @command="handleSessionCommandFromMenu(item, $event)"
                >
                  <button
                    type="button"
                    class="assistant-session-card__menu-button"
                    :disabled="sendingMessage || sessionActionBusy(item.id)"
                    aria-label="更多对话操作"
                    @click.stop
                  >
                    <AppIcon name="more-horizontal" :size="16" />
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">重命名</el-dropdown-item>
                      <el-dropdown-item :command="isSessionPinned(item) ? 'unpin' : 'pin'">
                        {{ isSessionPinned(item) ? '取消置顶' : '置顶聊天' }}
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided class="assistant-session-menu__danger">
                        删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </article>
          </div>
        </aside>

        <main class="assistant-main" :inert="isCompactLayout && mobileSidebarOpen">
          <header class="assistant-main__topbar">
            <button
              type="button"
              ref="mobileMenuRef"
              class="assistant-mobile-menu-button"
              aria-label="打开会话菜单"
              :aria-expanded="mobileSidebarOpen"
              @click="openMobileSidebar"
            >
              <span />
              <span />
              <span />
            </button>
            <div class="assistant-main__title-block">
              <span class="assistant-main__eyebrow">AI 学习助手</span>
              <h1>{{ sessionDetail ? (sessionDetail.title || '新对话') : '今天继续哪一块？' }}</h1>
            </div>
            <div class="assistant-main__actions">
              <button
                type="button"
                class="assistant-top-button assistant-top-button--ghost"
                :disabled="sessionPageLoading || sendingMessage"
                @click="loadSessionPage(true)"
              >
                {{ sessionPageLoading ? '刷新中...' : '刷新' }}
              </button>
              <button type="button" class="assistant-top-button assistant-close-button" aria-label="关闭 AI 学习助手" @click="closePanel">关闭</button>
            </div>
          </header>

          <div v-if="aiMockMode" class="assistant-mock-notice">
            <strong>{{ mockModeNoticeTitle }}</strong>
            <span>{{ mockModeNoticeText }}</span>
          </div>

          <section v-if="!sessionDetail" class="assistant-home">
            <div class="assistant-home__hero">
              <div class="assistant-home__greeting">你好，{{ greetingName }}</div>
              <h2>今天想学点什么？</h2>
              <p>一起读懂资料、梳理知识，把不明白的地方再学透一点。</p>
            </div>

            <div class="assistant-composer assistant-composer--hero">
              <textarea
                v-model="draftMessage"
                class="assistant-textarea"
                maxlength="4000"
                aria-label="向 AI 学习助手提问"
                placeholder="输入你的问题。回车发送，Shift + 回车换行。"
                @keydown="handleComposerKeydown"
              />
              <div class="assistant-composer__bottom">
                <div class="assistant-composer__meta">
                  <span>{{ currentBinding.label }}</span>
                  <span>{{ draftCount }}/4000</span>
                </div>
                <button
                  type="button"
                  class="assistant-send-button"
                  :disabled="sendingMessage || !draftMessage.trim()"
                  @click="sendMessage"
                >
                  {{ sendingMessage ? '发送中...' : '开始对话' }}
                </button>
              </div>
            </div>

            <div class="assistant-suggestion-row">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                type="button"
                class="assistant-suggestion-chip"
                @click="fillPrompt(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </section>

          <section v-else class="assistant-conversation">
            <div class="assistant-conversation__hero">
              <div class="assistant-conversation__meta">
                <span class="assistant-status-chip assistant-status-chip--brand">
                  {{ formatContextLabel(sessionDetail.currentContextType, sessionDetail.currentContextId, sessionDetail) }}
                </span>
                <span class="assistant-status-chip">消息 {{ sessionDetail.messages.length }}</span>
              </div>
              <p v-if="sessionDetail.lastMessageAt || sessionDetail.updatedAt || sessionDetail.createdAt">最近活跃 {{ formatDateTime(sessionDetail.lastMessageAt || sessionDetail.updatedAt || sessionDetail.createdAt) }}</p>
            </div>

            <div ref="messageStreamRef" class="assistant-thread" role="log" aria-label="对话消息" aria-live="polite" :aria-busy="sendingMessage">
              <div v-if="!sessionDetail.messages.length" class="assistant-thread__empty">
                <strong>这段对话已经准备好了</strong>
                <p>直接开始提问，或者点一条建议让我先帮你起个头。</p>
                <div class="assistant-suggestion-row assistant-suggestion-row--thread">
                  <button
                    v-for="prompt in quickPrompts"
                    :key="prompt"
                    type="button"
                    class="assistant-suggestion-chip"
                    @click="fillPrompt(prompt)"
                  >
                    {{ prompt }}
                  </button>
                </div>
              </div>

              <div
                v-for="(message, index) in sessionDetail.messages"
                :key="message.id"
                class="assistant-turn"
                :class="{
                  'assistant-turn--user': isUserMessage(message.role),
                  'assistant-turn--assistant': !isUserMessage(message.role)
                }"
              >
                <div class="assistant-turn__meta">
                  <span
                    class="assistant-turn__avatar"
                    :class="{
                      'assistant-turn__avatar--user': isUserMessage(message.role),
                      'assistant-turn__avatar--assistant': !isUserMessage(message.role)
                    }"
                  >
                    <AppIcon :name="isUserMessage(message.role) ? 'user' : 'assistant'" :size="16" />
                  </span>
                  <span class="assistant-turn__name">{{ isUserMessage(message.role) ? '你' : '学习助手' }}</span>
                  <span class="assistant-turn__time">{{ formatDateTime(message.createdAt) }}</span>
                </div>

                <div
                  v-if="isStreamingAssistantMessage(message.id) && !message.contentText"
                  class="assistant-thinking assistant-thinking--inline"
                >
                  <span class="assistant-thinking__dot" />
                  <span class="assistant-thinking__dot" />
                  <span class="assistant-thinking__dot" />
                  <span>正在整理上下文并开始生成...</span>
                </div>

                <div
                  v-else
                  class="assistant-turn__content"
                  :class="{
                    'assistant-turn__content--assistant': !isUserMessage(message.role),
                    'assistant-turn__content--streaming': isStreamingAssistantMessage(message.id)
                  }"
                >
                  {{ message.contentText || '' }}<span v-if="isStreamingAssistantMessage(message.id)" class="assistant-typing-cursor">|</span>
                </div>

                <details v-if="hasTraceContent(index)" class="assistant-turn__trace">
                  <summary>查看引用与轨迹</summary>

                  <div v-if="getRelatedToolCalls(index).length" class="assistant-trace-block">
                    <div class="assistant-trace-block__label">工具调用</div>
                    <div class="assistant-trace-chip-row">
                      <span
                        v-for="tool in getRelatedToolCalls(index)"
                        :key="tool.id"
                        class="assistant-trace-chip"
                        :class="toolStatusClass(tool.status)"
                      >
                        {{ tool.toolName || '未命名工具' }} · {{ tool.status || 'UNKNOWN' }}
                      </span>
                    </div>
                  </div>

                  <div v-if="getMemories(message.id).length" class="assistant-trace-block">
                    <div class="assistant-trace-block__label">参考记忆</div>
                    <div class="assistant-trace-chip-row">
                      <span
                        v-for="memory in getMemories(message.id)"
                        :key="memory.id"
                        class="assistant-trace-chip assistant-trace-chip--memory"
                      >
                        {{ memory.topicName || memory.memoryType || '会话记忆' }}
                      </span>
                    </div>
                  </div>

                  <div v-if="message.toolPlanJson" class="assistant-trace-block">
                    <div class="assistant-trace-block__label">工具规划</div>
                    <pre>{{ formatJson(message.toolPlanJson) }}</pre>
                  </div>

                  <div v-if="message.reasoningJson" class="assistant-trace-block">
                    <div class="assistant-trace-block__label">执行摘要</div>
                    <pre>{{ formatJson(message.reasoningJson) }}</pre>
                  </div>
                </details>
              </div>

            </div>

            <div class="assistant-composer assistant-composer--dock">
              <div class="assistant-composer__topline">
                <span v-if="sessionDetail.currentContextType">
                  {{ formatContextLabel(sessionDetail.currentContextType, sessionDetail.currentContextId, sessionDetail) }}
                </span>
                <button type="button" class="assistant-inline-danger" @click="removeSession(sessionDetail.id)">
                  删除会话
                </button>
              </div>

              <textarea
                v-model="draftMessage"
                class="assistant-textarea assistant-textarea--compact"
                maxlength="4000"
                aria-label="继续向 AI 学习助手提问"
                placeholder="继续追问、要求重写、要提纲、要习题或要复盘建议都可以。"
                @keydown="handleComposerKeydown"
              />

              <div class="assistant-composer__bottom">
                <div class="assistant-composer__meta">
                  <span>{{ draftCount }}/4000</span>
                </div>
                <button
                  type="button"
                  class="assistant-send-button"
                  :disabled="sendingMessage || !draftMessage.trim()"
                  @click="sendMessage"
                >
                  {{ sendingMessage ? '发送中...' : '发送' }}
                </button>
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  type AssistantChatReply,
  type AssistantMessage,
  type AssistantRelevantMemory,
  type AssistantSessionCreatePayload,
  type AssistantSessionDetail,
  type AssistantSessionPageItem,
  type AssistantStreamEvent,
  type AssistantStreamTracePayload,
  type AssistantToolCall,
  createAssistantSessionApi,
  deleteAssistantSessionApi,
  getAssistantSessionDetailApi,
  getAssistantSessionPageApi,
  renameAssistantSessionApi,
  streamAssistantMessageApi,
  updateAssistantSessionPinnedApi
} from '@/api/modules/assistant'
import { getAiConfigApi } from '@/api/modules/ai'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/AppIcon.vue'

interface CurrentPageBinding {
  bindable: boolean
  label: string
  helperText: string
  payload: AssistantSessionCreatePayload
}

interface PendingTurnState {
  sessionId: number
  optimisticUserId: number
  actualUserId?: number
  assistantMessageId: number
}

type AssistantSessionCommand = 'rename' | 'pin' | 'unpin' | 'delete'

const props = withDefaults(defineProps<{
  modelValue: boolean
  showLauncher?: boolean
}>(), {
  showLauncher: true
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const route = useRoute()
const userStore = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const sessionPage = reactive({
  current: 1,
  size: 100,
  total: 0,
  pages: 0,
  records: [] as AssistantSessionPageItem[]
})

const sessionPageLoading = ref(false)
const sessionDetailLoading = ref(false)
const creatingBlank = ref(false)
const creatingContext = ref(false)
const sendingMessage = ref(false)
const deletingSessionId = ref<number | null>(null)
const renamingSessionId = ref<number | null>(null)
const pinningSessionId = ref<number | null>(null)
const activeSessionId = ref<number | null>(null)
const sessionDetail = ref<AssistantSessionDetail | null>(null)
const draftMessage = ref('')
const messageStreamRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const sidebarRef = ref<HTMLElement | null>(null)
const launcherRef = ref<HTMLButtonElement | null>(null)
const mobileMenuRef = ref<HTMLButtonElement | null>(null)
const isCompactLayout = ref(false)
let compactLayoutQuery: MediaQueryList | null = null
let previousFocusedElement: HTMLElement | null = null
let previousBodyOverflow = ''
let bodyScrollLocked = false
const toolCallMap = ref<Record<number, AssistantToolCall[]>>({})
const memoryMap = ref<Record<number, AssistantRelevantMemory[]>>({})
const pendingTurn = ref<PendingTurnState | null>(null)
const aiMockMode = ref(false)
const aiConfigChecking = ref(false)
const mockModeNoticeTitle = '当前使用模拟回复'
const mockModeNoticeText = '当前回复由模拟模式提供。需要真实 AI 回答时，可在 AI 设置中关闭模拟模式。'
const draftCount = computed(() => draftMessage.value.length)
const totalSessionPages = computed(() =>
  Math.max(1, Math.ceil((sessionPage.total || 0) / Math.max(1, sessionPage.size || 1)))
)
const greetingName = computed(
  () => userStore.profile?.nickname || userStore.profile?.username || '同学'
)
const mobileSidebarOpen = ref(false)
let streamAbortController: AbortController | null = null

const resolveRouteLabel = (path: string) => {
  switch (path) {
    case '/dashboard':
      return '首页'
    case '/materials':
      return '资料管理'
    case '/summary':
      return 'AI 总结'
    case '/quiz':
      return 'AI 出题'
    case '/practice':
      return '练习记录'
    case '/ai-tasks':
      return '任务中心'
    case '/ai-config':
      return 'AI 配置'
    default:
      return '当前页面'
  }
}

const toNumeric = (value: unknown): number | undefined => {
  if (Array.isArray(value)) {
    return toNumeric(value[0])
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const resolveContextQueryId = (...keys: string[]) => {
  for (const key of keys) {
    const resolved = toNumeric(route.query[key])
    if (resolved) {
      return resolved
    }
  }
  return undefined
}

const toTimeValue = (value?: string) => {
  if (!value) {
    return 0
  }
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

const currentBinding = computed<CurrentPageBinding>(() => {
  return {
    bindable: false,
    label: '通用对话',
    helperText: '整理资料、解答问题，陪你把知识学扎实。',
    payload: {}
  }
})

const quickPrompts = computed(() => {
  const contextType = String(
    sessionDetail.value?.currentContextType ||
      ''
  ).toUpperCase()

  if (contextType === 'MATERIAL') {
    return [
      '帮我梳理这份资料的核心知识点',
      '根据这份资料生成一份复习提纲',
      '这份资料里最值得优先掌握的内容是什么'
    ]
  }

  if (contextType === 'PRACTICE_SESSION') {
    return [
      '帮我分析这次练习的薄弱点',
      '简答题为什么容易丢分',
      '根据这次练习给我一个复习顺序'
    ]
  }

  if (contextType === 'QUESTION_SET') {
    return [
      '这套题主要覆盖了哪些知识点',
      '这套题的难度分布怎么样',
      '刷完这套题后下一步怎么复习'
    ]
  }

  return [
    '帮我找到最近上传的学习资料',
    '如何把知识点整理成复习提纲',
    '怎样安排一轮错题复习'
  ]
})

const restoreBodyScroll = () => {
  if (!bodyScrollLocked) return
  document.body.style.overflow = previousBodyOverflow
  bodyScrollLocked = false
}

const syncPanelVisibility = async (value: boolean) => {
  if (value) {
    previousFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (!bodyScrollLocked) previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    bodyScrollLocked = true
    if (!sendingMessage.value) {
      resetToFreshSession()
      void loadAiConfigNotice()
      void loadSessionPage(false)
    }
    await nextTick()
    if (visible.value) panelRef.value?.focus({ preventScroll: true })
  } else {
    restoreBodyScroll()
    await nextTick()
    if (!visible.value) {
      const target = previousFocusedElement?.isConnected ? previousFocusedElement : launcherRef.value
      target?.focus({ preventScroll: true })
    }
  }
}

watch(visible, syncPanelVisibility)

const syncCompactLayout = () => {
  isCompactLayout.value = Boolean(compactLayoutQuery?.matches)
  if (!isCompactLayout.value) mobileSidebarOpen.value = false
}

const handlePanelKeydown = (event: KeyboardEvent) => {
  if (event.defaultPrevented || event.isComposing) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    if (mobileSidebarOpen.value) closeMobileSidebar()
    else closePanel()
    return
  }
  if (event.key !== 'Tab') return
  const scope = isCompactLayout.value && mobileSidebarOpen.value ? sidebarRef.value : panelRef.value
  if (!scope) return
  const focusable = Array.from(scope.querySelectorAll<HTMLElement>(
    'button:not([disabled]), a[href], input:not([disabled]), textarea:not([disabled]), select:not([disabled]), summary, [tabindex]:not([tabindex="-1"])'
  )).filter((element) => !element.closest('[inert]') && element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden')
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (!first || !last) {
    event.preventDefault()
    panelRef.value?.focus()
  } else if (!focusable.includes(active as HTMLElement)) {
    event.preventDefault()
    ;(event.shiftKey ? last : first).focus()
  } else if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(
  () => sessionDetail.value?.messages.length,
  async () => {
    if (!visible.value) {
      return
    }
    await nextTick()
    scrollToBottom()
  }
)

const isUserMessage = (role?: string) => String(role || '').toUpperCase() === 'USER'

const isSessionPinned = (session?: Pick<AssistantSessionPageItem | AssistantSessionDetail, 'pinned'> | null) =>
  Number(session?.pinned || 0) === 1

const sessionActionBusy = (sessionId: number) =>
  deletingSessionId.value === sessionId ||
  renamingSessionId.value === sessionId ||
  pinningSessionId.value === sessionId

const syncSessionDetail = (detail: AssistantSessionDetail) => {
  const normalizedDetail = normalizeSessionDetail(detail)
  if (sessionDetail.value?.id === normalizedDetail.id) {
    sessionDetail.value = {
      ...sessionDetail.value,
      ...normalizedDetail
    }
  }
  sessionPage.records = sessionPage.records.map((item) =>
    item.id === normalizedDetail.id
      ? {
          ...item,
          title: normalizedDetail.title,
          status: normalizedDetail.status,
          pinned: normalizedDetail.pinned,
          currentContextType: normalizedDetail.currentContextType,
          currentContextId: normalizedDetail.currentContextId,
          lastMessageAt: normalizedDetail.lastMessageAt,
          createdAt: normalizedDetail.createdAt
        }
      : item
  )
  return normalizedDetail
}

const openPanel = () => {
  mobileSidebarOpen.value = false
  visible.value = true
}

const closePanel = () => {
  mobileSidebarOpen.value = false
  visible.value = false
}

const resetToFreshSession = () => {
  mobileSidebarOpen.value = false
  activeSessionId.value = null
  sessionDetail.value = null
  draftMessage.value = ''
  pendingTurn.value = null
  toolCallMap.value = {}
  memoryMap.value = {}
}

const openMobileSidebar = () => {
  mobileSidebarOpen.value = true
  void nextTick(() => sidebarRef.value?.querySelector<HTMLButtonElement>('button')?.focus())
}

const closeMobileSidebar = () => {
  const wasOpen = mobileSidebarOpen.value
  mobileSidebarOpen.value = false
  if (wasOpen) void nextTick(() => mobileMenuRef.value?.focus())
}

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

const formatDateTime = (value?: string) => {
  if (!value) {
    return '刚刚'
  }
  return value.replace('T', ' ').slice(0, 16)
}

const formatSessionTime = (value?: string) => {
  if (!value) {
    return '刚刚'
  }
  const normalized = value.replace('T', ' ')
  const today = new Date().toISOString().slice(0, 10)
  if (normalized.startsWith(today)) {
    return normalized.slice(11, 16)
  }
  return normalized.slice(5, 16)
}

const formatContextLabel = (
  contextType?: string,
  contextId?: number,
  detail?: Partial<AssistantSessionDetail>
) => {
  const type = String(contextType || '').toUpperCase()
  if (type === 'MATERIAL') {
    return `资料 #${detail?.currentMaterialId || contextId || '--'}`
  }
  if (type === 'QUESTION_SET') {
    return `题集 #${detail?.currentQuestionSetId || contextId || '--'}`
  }
  if (type === 'PRACTICE_SESSION') {
    return `练习 #${detail?.currentPracticeSessionId || contextId || '--'}`
  }
  if (type === 'AI_TASK') {
    return `任务 #${contextId || '--'}`
  }
  return '通用会话'
}

const normalizeSessionDetail = (detail: AssistantSessionDetail) => ({
  ...detail,
  messages: detail.messages || [],
  recentToolCalls: detail.recentToolCalls || []
})

const mergeUniqueById = <T extends { id: number }>(current: T[], incoming: T[]) => {
  const merged = [...current]
  incoming.forEach((item) => {
    const index = merged.findIndex((currentItem) => currentItem.id === item.id)
    if (index >= 0) {
      merged[index] = item
    } else {
      merged.push(item)
    }
  })
  return merged
}

const ingestToolCalls = (toolCalls: AssistantToolCall[]) => {
  if (!toolCalls.length) {
    return
  }

  const nextMap = { ...toolCallMap.value }
  toolCalls.forEach((toolCall) => {
    const messageId = Number(toolCall.messageId || 0)
    if (!messageId) {
      return
    }
    const currentCalls = nextMap[messageId] ? [...nextMap[messageId]] : []
    const existingIndex = currentCalls.findIndex((item) => item.id === toolCall.id)
    if (existingIndex >= 0) {
      currentCalls[existingIndex] = toolCall
    } else {
      currentCalls.push(toolCall)
    }
    nextMap[messageId] = currentCalls.sort((a, b) => toTimeValue(a.createdAt) - toTimeValue(b.createdAt))
  })
  toolCallMap.value = nextMap
}

const getToolCalls = (messageId?: number) => {
  if (!messageId) {
    return []
  }
  return toolCallMap.value[messageId] || []
}

const getMemories = (messageId?: number) => {
  if (!messageId) {
    return []
  }
  return memoryMap.value[messageId] || []
}

const isStreamingAssistantMessage = (messageId?: number) =>
  Boolean(sendingMessage.value && pendingTurn.value?.assistantMessageId === messageId)

const getRelatedToolCalls = (index: number) => {
  const messages = sessionDetail.value?.messages || []
  const currentMessage = messages[index]
  if (!currentMessage || isUserMessage(currentMessage.role)) {
    return []
  }
  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    const candidate = messages[cursor]
    if (isUserMessage(candidate.role)) {
      return getToolCalls(candidate.id)
    }
  }
  return []
}

const hasTraceContent = (index: number) => {
  const messages = sessionDetail.value?.messages || []
  const currentMessage = messages[index]
  if (!currentMessage || isUserMessage(currentMessage.role)) {
    return false
  }
  return Boolean(
    getRelatedToolCalls(index).length ||
      getMemories(currentMessage.id).length ||
      currentMessage.toolPlanJson ||
      currentMessage.reasoningJson
  )
}

const toolStatusClass = (status?: string) => {
  switch (String(status || '').toUpperCase()) {
    case 'SUCCESS':
      return 'assistant-inline-chip--success'
    case 'FAILED':
      return 'assistant-inline-chip--danger'
    case 'RUNNING':
    case 'WAITING':
      return 'assistant-inline-chip--warning'
    default:
      return ''
  }
}

const formatJson = (value?: string) => {
  if (!value) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

const scrollToBottom = () => {
  const element = messageStreamRef.value
  if (!element) {
    return
  }
  element.scrollTop = element.scrollHeight
}

const fillPrompt = (prompt: string) => {
  draftMessage.value = prompt
  panelRef.value?.querySelector<HTMLTextAreaElement>('textarea')?.focus()
}

const handleComposerKeydown = (event: KeyboardEvent) => {
  if (event.isComposing || event.keyCode === 229) return
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void sendMessage()
  }
}

const buildLocalDateTime = () => {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

const updateSessionMessages = (updater: (messages: AssistantMessage[]) => AssistantMessage[]) => {
  if (!sessionDetail.value) {
    return
  }
  sessionDetail.value = {
    ...sessionDetail.value,
    messages: updater(sessionDetail.value.messages)
  }
}

const replaceMessage = (fromId: number, nextMessage: AssistantMessage) => {
  let replaced = false
  updateSessionMessages((messages) => {
    const nextMessages = messages.map((message) => {
      if (message.id !== fromId) {
        return message
      }
      replaced = true
      return {
        ...message,
        ...nextMessage
      }
    })
    return replaced ? nextMessages : [...nextMessages, nextMessage]
  })
}

const updateMessage = (messageId: number, updater: (message: AssistantMessage) => AssistantMessage) => {
  updateSessionMessages((messages) =>
    messages.map((message) => (message.id === messageId ? updater(message) : message))
  )
}

const removeMessagesById = (messageIds: number[]) => {
  const removableIds = new Set(messageIds)
  updateSessionMessages((messages) =>
    messages.filter((message) => !removableIds.has(message.id))
  )
}

const moveToolCallPreview = (fromMessageId: number, toMessageId: number) => {
  if (fromMessageId === toMessageId) {
    return
  }
  const previewToolCalls = toolCallMap.value[fromMessageId]
  if (!previewToolCalls?.length) {
    return
  }
  const nextMap = { ...toolCallMap.value }
  nextMap[toMessageId] = previewToolCalls
  delete nextMap[fromMessageId]
  toolCallMap.value = nextMap
}

const clearPendingPreviewArtifacts = (state: PendingTurnState | null) => {
  if (!state) {
    return
  }
  const nextToolCallMap = { ...toolCallMap.value }
  delete nextToolCallMap[state.optimisticUserId]
  if (state.actualUserId) {
    delete nextToolCallMap[state.actualUserId]
  }
  toolCallMap.value = nextToolCallMap

  const nextMemoryMap = { ...memoryMap.value }
  delete nextMemoryMap[state.assistantMessageId]
  memoryMap.value = nextMemoryMap
}

const buildOptimisticMessage = (
  id: number,
  role: 'USER' | 'ASSISTANT',
  contentText: string
): AssistantMessage => ({
  id,
  role,
  messageType: 'TEXT',
  contentText,
  createdAt: buildLocalDateTime()
})

const decoratePreviewToolCalls = (toolCalls: AssistantToolCall[], seed: number) =>
  toolCalls.map((toolCall, index) => {
    const rawId = Number((toolCall as Partial<AssistantToolCall>).id || 0)
    return {
      ...toolCall,
      id: rawId > 0 ? rawId : -(Math.abs(seed) * 1000 + index + 1)
    }
  })

const applyStreamSessionEvent = (event: Extract<AssistantStreamEvent, { type: 'session' }>) => {
  const currentPendingTurn = pendingTurn.value
  if (!currentPendingTurn || !sessionDetail.value || sessionDetail.value.id !== event.sessionId) {
    return
  }

  sessionDetail.value = {
    ...sessionDetail.value,
    title: event.sessionTitle || sessionDetail.value.title
  }
  activeSessionId.value = event.sessionId

  if (event.userMessage) {
    replaceMessage(currentPendingTurn.optimisticUserId, event.userMessage)
    moveToolCallPreview(currentPendingTurn.optimisticUserId, event.userMessage.id)
    pendingTurn.value = {
      ...currentPendingTurn,
      actualUserId: event.userMessage.id
    }
  }
}

const applyStreamTraceEvent = (tracePayload: AssistantStreamTracePayload) => {
  const currentPendingTurn = pendingTurn.value
  if (!currentPendingTurn) {
    return
  }

  updateMessage(currentPendingTurn.assistantMessageId, (message) => ({
    ...message,
    modelName: tracePayload.modelName || message.modelName,
    reasoningJson: tracePayload.reasoningJson || message.reasoningJson,
    toolPlanJson: tracePayload.toolPlanJson || message.toolPlanJson
  }))

  if (tracePayload.usedMemories?.length) {
    memoryMap.value = {
      ...memoryMap.value,
      [currentPendingTurn.assistantMessageId]: tracePayload.usedMemories
    }
  }

  if (tracePayload.toolCalls?.length) {
    const messageId = currentPendingTurn.actualUserId || currentPendingTurn.optimisticUserId
    toolCallMap.value = {
      ...toolCallMap.value,
      [messageId]: decoratePreviewToolCalls(tracePayload.toolCalls, currentPendingTurn.assistantMessageId)
    }
  }
}

const appendAssistantDelta = (delta: string) => {
  const currentPendingTurn = pendingTurn.value
  if (!currentPendingTurn || !delta) {
    return
  }
  updateMessage(currentPendingTurn.assistantMessageId, (message) => ({
    ...message,
    contentText: `${message.contentText || ''}${delta}`
  }))
  void nextTick(scrollToBottom)
}

const renderAssistantError = (message: string) => {
  const currentPendingTurn = pendingTurn.value
  if (!currentPendingTurn) {
    return
  }
  const errorText = `这次调用失败了：${message || '助手流式生成失败'}`
  updateMessage(currentPendingTurn.assistantMessageId, (assistantMessage) => ({
    ...assistantMessage,
    contentText: errorText
  }))
  void nextTick(scrollToBottom)
}

const changeSessionPage = (page: number) => {
  if (sendingMessage.value) {
    ElMessage.warning('当前回复还在生成中，先等这一轮完成吧')
    return
  }
  if (page === sessionPage.current || page < 1 || page > totalSessionPages.value) {
    return
  }
  sessionPage.current = page
  void loadSessionPage(true)
}

const loadSessionPage = async (selectFallback: boolean) => {
  sessionPageLoading.value = true
  try {
    const res = await getAssistantSessionPageApi({
      current: sessionPage.current,
      size: sessionPage.size
    })
    const data = res.data.data
    sessionPage.current = data.current || sessionPage.current
    sessionPage.size = data.size || sessionPage.size
    sessionPage.total = data.total || 0
    sessionPage.pages = data.pages || 0
    sessionPage.records = data.records || []

    if (!sessionPage.records.length) {
      if (selectFallback) {
        activeSessionId.value = null
        sessionDetail.value = null
      }
      return
    }

    const existsInPage = activeSessionId.value
      ? sessionPage.records.some((item) => item.id === activeSessionId.value)
      : false

    if (existsInPage) {
      if (!sessionDetail.value || sessionDetail.value.id !== activeSessionId.value) {
        await loadSessionDetail(activeSessionId.value as number)
      }
      return
    }

    if (selectFallback) {
      await selectSession(sessionPage.records[0].id)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载助手会话失败')
  } finally {
    sessionPageLoading.value = false
  }
}

const loadSessionDetail = async (sessionId: number) => {
  sessionDetailLoading.value = true
  try {
    const res = await getAssistantSessionDetailApi(sessionId, 30)
    const detail = normalizeSessionDetail(res.data.data as AssistantSessionDetail)
    activeSessionId.value = sessionId
    sessionDetail.value = detail
    ingestToolCalls(detail.recentToolCalls)
  } catch (error: any) {
    ElMessage.error(error.message || '加载助手会话详情失败')
  } finally {
    sessionDetailLoading.value = false
  }
}

const selectSession = async (sessionId: number) => {
  if (sendingMessage.value) {
    ElMessage.warning('当前回复还在生成中，先等这一轮完成吧')
    return
  }
  if (sessionDetail.value?.id === sessionId) {
    activeSessionId.value = sessionId
    closeMobileSidebar()
    return
  }
  await loadSessionDetail(sessionId)
  closeMobileSidebar()
}

const createSession = async (bindCurrentPage: boolean, silent = false, allowWhileSending = false) => {
  if (sendingMessage.value && !allowWhileSending) {
    ElMessage.warning('当前回复还在生成中，暂时不能新建会话')
    return null
  }
  const targetLoading = bindCurrentPage ? creatingContext : creatingBlank
  targetLoading.value = true
  try {
    const payload =
      bindCurrentPage && currentBinding.value.bindable ? currentBinding.value.payload : {}
    const res = await createAssistantSessionApi(payload)
    const detail = normalizeSessionDetail(res.data.data as AssistantSessionDetail)
    activeSessionId.value = detail.id
    sessionDetail.value = detail
    sessionPage.current = 1
    await loadSessionPage(false)
    closeMobileSidebar()
    if (!silent) {
      ElMessage.success(bindCurrentPage && currentBinding.value.bindable ? '已按当前页面创建会话' : '新会话已创建')
    }
    return detail
  } catch (error: any) {
    ElMessage.error(error.message || '创建助手会话失败')
    return null
  } finally {
    targetLoading.value = false
  }
}

const applyReply = (reply: AssistantChatReply) => {
  if (!sessionDetail.value || sessionDetail.value.id !== reply.sessionId) {
    sessionDetail.value = {
      id: reply.sessionId,
      title: reply.sessionTitle || '新对话',
      messages: [reply.userMessage, reply.assistantMessage],
      recentToolCalls: reply.toolCalls || []
    } as AssistantSessionDetail
  } else {
    sessionDetail.value = {
      ...sessionDetail.value,
      title: reply.sessionTitle || sessionDetail.value.title,
      messages: mergeUniqueById(sessionDetail.value.messages, [reply.userMessage, reply.assistantMessage]),
      recentToolCalls: mergeUniqueById(sessionDetail.value.recentToolCalls, reply.toolCalls || []),
      lastMessageAt: reply.assistantMessage.createdAt || sessionDetail.value.lastMessageAt
    }
  }

  ingestToolCalls(reply.toolCalls || [])

  if (reply.assistantMessage?.id && reply.usedMemories?.length) {
    memoryMap.value = {
      ...memoryMap.value,
      [reply.assistantMessage.id]: reply.usedMemories
    }
  }
  void nextTick(scrollToBottom)
}

const sendMessage = async () => {
  const contentText = draftMessage.value.trim()
  if (!contentText) {
    ElMessage.warning('先输入你的问题再发送')
    return
  }
  if (sendingMessage.value) {
    return
  }

  const previousDraft = draftMessage.value
  let shouldRestoreDraft = true
  let detail = sessionDetail.value

  try {
    if (!detail?.id) {
      detail = await createSession(currentBinding.value.bindable, true, true)
    }

    if (!detail?.id) {
      throw new Error('创建会话失败')
    }

    sendingMessage.value = true
    const optimisticUserId = -Date.now()
    const assistantMessageId = optimisticUserId - 1
    pendingTurn.value = {
      sessionId: detail.id,
      optimisticUserId,
      assistantMessageId
    }
    shouldRestoreDraft = false

    sessionDetail.value = {
      ...detail,
      messages: [
        ...detail.messages,
        buildOptimisticMessage(optimisticUserId, 'USER', contentText),
        buildOptimisticMessage(assistantMessageId, 'ASSISTANT', '')
      ],
      lastMessageAt: buildLocalDateTime()
    }

    draftMessage.value = ''
    void nextTick(scrollToBottom)
    streamAbortController?.abort()
    streamAbortController = new AbortController()

    let streamErrorMessage = ''
    let finalReply: AssistantChatReply | null = null

    await streamAssistantMessageApi({
      sessionId: detail.id,
      data: { contentText },
      signal: streamAbortController.signal,
      onEvent: (event) => {
        switch (event.type) {
          case 'session':
            applyStreamSessionEvent(event)
            break
          case 'trace':
            applyStreamTraceEvent(event)
            break
          case 'delta':
            appendAssistantDelta(event.delta)
            break
          case 'done':
            finalReply = event.reply
            break
          case 'error':
            streamErrorMessage = event.message || '助手流式生成失败'
            renderAssistantError(streamErrorMessage)
            break
        }
      }
    })

    if (streamErrorMessage) {
      pendingTurn.value = null
      await loadSessionPage(false)
      return
    }
    if (!finalReply) {
      throw new Error('助手没有返回完整结果')
    }

    const completedPendingTurn = pendingTurn.value
    clearPendingPreviewArtifacts(completedPendingTurn)
    removeMessagesById(
      [completedPendingTurn?.optimisticUserId, completedPendingTurn?.assistantMessageId].filter(
        (value): value is number => Number.isFinite(value)
      )
    )
    pendingTurn.value = null
    applyReply(finalReply)
    sessionPage.current = 1
    await loadSessionPage(false)
  } catch (error: any) {
    if (error?.name === 'AbortError') {
      return
    }

    if (shouldRestoreDraft) {
      draftMessage.value = previousDraft
    }

    const currentPendingTurn = pendingTurn.value
    if (currentPendingTurn) {
      clearPendingPreviewArtifacts(currentPendingTurn)
      removeMessagesById([currentPendingTurn.assistantMessageId])
      if (!currentPendingTurn.actualUserId) {
        removeMessagesById([currentPendingTurn.optimisticUserId])
      }
    }

    if (detail?.id) {
      try {
        await loadSessionDetail(detail.id)
      } catch {
        // 忽略详情同步失败，保留当前界面并直接提示错误。
      }
    }

    ElMessage.error(error.message || '发送消息失败')
  } finally {
    pendingTurn.value = null
    streamAbortController = null
    sendingMessage.value = false
  }
}

const renameSession = async (item: AssistantSessionPageItem) => {
  if (sendingMessage.value) {
    ElMessage.warning('当前回复还在生成中，暂时不能修改会话名称')
    return
  }

  let nextTitle = ''
  try {
    const result = await ElMessageBox.prompt('请输入新的对话名称', '重命名对话', {
      inputValue: item.title || '新对话',
      inputPlaceholder: '请输入新的对话名称',
      inputValidator: (value) => {
        const title = String(value || '').trim()
        if (!title) {
          return '会话名称不能为空'
        }
        if (title.length > 200) {
          return '会话名称不能超过 200 个字符'
        }
        return true
      },
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    nextTitle = String(result.value || '').trim()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error('打开重命名窗口失败')
    return
  }

  if (!nextTitle || nextTitle === (item.title || '').trim()) {
    return
  }

  renamingSessionId.value = item.id
  try {
    const res = await renameAssistantSessionApi(item.id, nextTitle)
    syncSessionDetail(res.data.data as AssistantSessionDetail)
    await loadSessionPage(false)
    ElMessage.success('会话名称已更新')
  } catch (error: any) {
    ElMessage.error(error.message || '修改会话名称失败')
  } finally {
    renamingSessionId.value = null
  }
}

const updateSessionPinned = async (item: AssistantSessionPageItem, pinned: boolean) => {
  if (sendingMessage.value) {
    ElMessage.warning('当前回复还在生成中，暂时不能修改置顶状态')
    return
  }

  pinningSessionId.value = item.id
  try {
    const res = await updateAssistantSessionPinnedApi(item.id, pinned)
    syncSessionDetail(res.data.data as AssistantSessionDetail)
    await loadSessionPage(false)
    ElMessage.success(pinned ? '已置顶聊天' : '已取消置顶')
  } catch (error: any) {
    ElMessage.error(error.message || '修改置顶状态失败')
  } finally {
    pinningSessionId.value = null
  }
}

const handleSessionCommand = (item: AssistantSessionPageItem, command: string | number | object) => {
  const action = String(command) as AssistantSessionCommand
  if (action === 'rename') {
    void renameSession(item)
    return
  }
  if (action === 'pin' || action === 'unpin') {
    void updateSessionPinned(item, action === 'pin')
    return
  }
  if (action === 'delete') {
    void removeSession(item.id)
  }
}

const handleSessionCommandFromMenu = (item: AssistantSessionPageItem, command: unknown) => {
  handleSessionCommand(item, command as string | number | object)
}

const removeSession = async (sessionId: number) => {
  if (sendingMessage.value) {
    ElMessage.warning('当前回复还在生成中，暂时不能删除会话')
    return
  }
  try {
    await ElMessageBox.confirm('删除后该会话中的聊天记录、工具调用和上下文都将一起移除，确定继续吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  deletingSessionId.value = sessionId
  try {
    await deleteAssistantSessionApi(sessionId)
    if (sessionPage.records.length === 1 && sessionPage.current > 1) {
      sessionPage.current -= 1
    }
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = null
      sessionDetail.value = null
    }
    await loadSessionPage(true)
    ElMessage.success('会话已删除')
  } catch (error: any) {
    ElMessage.error(error.message || '删除会话失败')
  } finally {
    deletingSessionId.value = null
  }
}

onMounted(() => {
  compactLayoutQuery = window.matchMedia('(max-width: 900px)')
  compactLayoutQuery.addEventListener('change', syncCompactLayout)
  syncCompactLayout()
  if (visible.value) void syncPanelVisibility(true)
})

onBeforeUnmount(() => {
  restoreBodyScroll()
  compactLayoutQuery?.removeEventListener('change', syncCompactLayout)
  streamAbortController?.abort()
  pendingTurn.value = null
})
</script>

<style scoped>
.assistant-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2400;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border: 0;
  border-radius: 999px;
  background: rgba(16, 23, 35, 0.94);
  color: #fff;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.22);
  cursor: pointer;
  overflow: hidden;
}

.assistant-fab__glow {
  position: absolute;
  inset: 1px;
  border-radius: inherit;
  background: linear-gradient(135deg, rgba(120, 166, 255, 0.26), rgba(179, 228, 255, 0.18));
  opacity: 0.92;
}

.assistant-fab__label,
.assistant-fab__hint,
.assistant-fab__icon {
  position: relative;
  z-index: 1;
}

.assistant-fab__icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  color: #dbeafe;
}

.assistant-fab__label {
  font-size: 14px;
  font-weight: 700;
}

.assistant-fab__hint {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.assistant-overlay {
  position: fixed;
  inset: 0;
  z-index: 2500;
  padding: 0;
  background: rgba(244, 247, 252, 0.96);
  backdrop-filter: blur(8px);
}

.assistant-mobile-scrim {
  display: none;
}

.assistant-panel {
  height: 100dvh;
  max-height: 100dvh;
  display: grid;
  grid-template-columns: 286px minmax(0, 1fr);
  overflow: hidden;
  background: #fff;
  box-shadow: none;
}

.assistant-sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  padding: 28px 18px 18px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--line);
  overflow: hidden;
}

.assistant-sidebar__brand h2,
.assistant-main__topbar h1,
.assistant-home__hero h2 {
  margin: 0;
  color: var(--text);
}

.assistant-sidebar__brand-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.assistant-sidebar__logo {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--brand), #48c78e);
  color: #fff;
  box-shadow: 0 14px 28px rgba(31, 122, 90, 0.18);
}

.assistant-sidebar__kicker,
.assistant-main__eyebrow {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.assistant-sidebar__brand p,
.assistant-home__hero p,
.assistant-context-card em,
.assistant-thread__empty p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 13px;
}

.assistant-context-card,
.assistant-session-card,
.assistant-home__composer,
.assistant-conversation__hero,
.assistant-thread,
.assistant-composer {
  background: transparent;
  box-shadow: none;
}

.assistant-context-card {
  padding: 0 0 14px;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
}

.assistant-context-card__label {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.assistant-context-card strong {
  display: block;
  margin-top: 12px;
  font-size: 16px;
  color: var(--text);
}

.assistant-sidebar__actions {
  display: grid;
  gap: 2px;
}

.assistant-control,
.assistant-top-button,
.assistant-page-button,
.assistant-send-button,
.assistant-inline-danger,
.assistant-session-card__delete,
.assistant-session-card__menu-button {
  border: 0;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.assistant-control {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
  text-align: left;
}

.assistant-control--primary,
.assistant-send-button {
  background: var(--brand-soft);
  color: var(--brand);
}

.assistant-control--ghost,
.assistant-top-button--ghost {
  background: transparent;
}

.assistant-control:disabled,
.assistant-top-button:disabled,
.assistant-page-button:disabled,
.assistant-send-button:disabled,
.assistant-session-card__delete:disabled,
.assistant-session-card__menu-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
}

.assistant-control:not(:disabled):hover,
.assistant-top-button:not(:disabled):hover,
.assistant-page-button:not(:disabled):hover,
.assistant-send-button:not(:disabled):hover {
  transform: none;
  box-shadow: none;
}

.assistant-session-header,
.assistant-session-card__top,
.assistant-session-card__bottom,
.assistant-main__topbar,
.assistant-main__actions,
.assistant-composer__bottom,
.assistant-composer__topline,
.assistant-conversation__hero,
.assistant-turn__meta,
.assistant-trace-chip-row,
.assistant-suggestion-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.assistant-session-header,
.assistant-session-card__bottom,
.assistant-main__topbar,
.assistant-composer__bottom,
.assistant-composer__topline,
.assistant-conversation__hero {
  justify-content: space-between;
}

.assistant-session-header span,
.assistant-session-header em,
.assistant-session-card__top span,
.assistant-session-card__bottom span,
.assistant-main__eyebrow,
.assistant-conversation__hero p,
.assistant-composer__meta,
.assistant-turn__time {
  color: var(--text-secondary);
}

.assistant-session-header strong {
  margin-left: 8px;
  font-size: 18px;
  color: var(--text);
}

.assistant-sidebar__empty {
  padding: 8px 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.75;
}

.assistant-session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 6px;
  scrollbar-gutter: stable;
}

.assistant-session-list::-webkit-scrollbar,
.assistant-thread::-webkit-scrollbar {
  width: 8px;
}

.assistant-session-list::-webkit-scrollbar-track,
.assistant-thread::-webkit-scrollbar-track {
  background: transparent;
}

.assistant-session-list::-webkit-scrollbar-thumb,
.assistant-thread::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(106, 122, 148, 0.28);
}

.assistant-session-list::-webkit-scrollbar-thumb:hover,
.assistant-thread::-webkit-scrollbar-thumb:hover {
  background: rgba(106, 122, 148, 0.46);
}

.assistant-session-card {
  padding: 8px 10px;
  border: 0;
  border-radius: 10px;
  cursor: pointer;
}

.assistant-session-card--disabled {
  opacity: 0.68;
  cursor: default;
}

.assistant-session-card--active {
  background: var(--brand-soft);
}

.assistant-session-card--pinned:not(.assistant-session-card--active) {
  background: rgba(255, 255, 255, 0.32);
}

.assistant-session-card__pin {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  color: var(--brand);
}

.assistant-session-card__top strong {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 700;
}

.assistant-session-card p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.assistant-session-card__delete,
.assistant-inline-danger {
  padding: 0;
  background: transparent;
  color: var(--red);
  font-size: 11px;
}

.assistant-session-card__menu-button {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
}

.assistant-session-card__menu-button:not(:disabled):hover,
.assistant-session-card__menu-button:not(:disabled):focus-visible {
  background: rgba(81, 98, 127, 0.1);
  color: var(--text);
}

:global(.assistant-session-menu__danger) {
  color: #dc2626;
}

.assistant-mobile-menu-button,
.assistant-mobile-avatar {
  display: none;
}

.assistant-top-button {
  padding: 8px 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.assistant-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100dvh;
  padding: 22px 28px 18px 28px;
  overflow: hidden;
  box-sizing: border-box;
}

.assistant-main__topbar h1 {
  margin-top: 6px;
  font-size: 20px;
  letter-spacing: -0.03em;
}

.assistant-mock-notice {
  width: min(920px, 100%);
  margin: 0 auto 12px;
  padding: 10px 14px;
  border: 1px solid rgba(245, 158, 11, 0.28);
  border-left: 3px solid #f59e0b;
  border-radius: 8px;
  background: #fffbeb;
  color: #7c4a03;
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

.assistant-home,
.assistant-conversation {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.assistant-home {
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 34px 28px 48px;
}

.assistant-home__hero {
  max-width: 760px;
  text-align: center;
}

.assistant-home__greeting {
  color: var(--text-secondary);
  font-size: 16px;
}

.assistant-home__hero h2 {
  margin-top: 6px;
  font-size: 42px;
  line-height: 1.12;
  letter-spacing: -0.04em;
}

.assistant-home__composer,
.assistant-composer {
  width: min(720px, 100%);
  padding: 16px 20px;
  border: 1px solid var(--line);
  border-radius: 32px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
}

.assistant-composer--dock {
  width: min(690px, 100%);
  flex: 0 0 auto;
  margin: 10px auto 0;
  padding: 12px 18px;
  border-radius: 28px;
}

.assistant-textarea {
  width: 100%;
  min-height: 92px;
  resize: none;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 16px;
  line-height: 1.7;
}

.assistant-textarea--compact {
  min-height: 46px;
  max-height: 96px;
  font-size: 15px;
  line-height: 1.55;
}

.assistant-textarea::placeholder {
  color: var(--muted);
}

.assistant-composer__meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
}

.assistant-send-button {
  padding: 10px 18px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: none;
}

.assistant-suggestion-row {
  max-width: 820px;
  flex-wrap: wrap;
  justify-content: center;
  gap: 18px;
}

.assistant-suggestion-row--thread {
  justify-content: flex-start;
}

.assistant-suggestion-chip {
  padding: 0;
  border: 0;
  border-bottom: 1px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
}

.assistant-suggestion-chip:hover {
  color: var(--brand);
  border-bottom-color: var(--brand);
}

.assistant-conversation__hero {
  width: min(920px, 100%);
  margin: 0 auto;
  padding: 0 0 14px;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
  box-shadow: none;
}

.assistant-conversation__meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.assistant-status-chip {
  display: inline-flex;
  align-items: center;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.assistant-status-chip--brand {
  color: var(--brand);
}

.assistant-thread {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  margin-top: 14px;
  padding: 0 0 16px;
  border: 0;
  border-radius: 0;
  background: transparent;
  scrollbar-gutter: stable;
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

.assistant-turn {
  width: min(920px, 100%);
  margin: 0 auto 28px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.assistant-turn--user {
  align-items: flex-end;
  margin-bottom: 20px;
}

.assistant-turn__meta {
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
  background: linear-gradient(135deg, #c7f2df, #dbeafe);
  color: var(--brand);
}

.assistant-turn__avatar--user {
  background: linear-gradient(135deg, #dbeafe, #f1f5f9);
  color: var(--brand);
}

.assistant-turn__content {
  width: 100%;
  margin-top: 8px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text);
  font-size: 15px;
  line-height: 1.95;
  white-space: pre-wrap;
  word-break: break-word;
}

.assistant-turn__content--assistant {
  color: var(--text);
}

.assistant-turn__content--streaming {
  color: var(--text);
}

.assistant-turn--user .assistant-turn__content {
  width: auto;
  max-width: min(640px, 72%);
  padding: 14px 18px;
  border-radius: 24px 24px 10px 24px;
  background: linear-gradient(135deg, rgba(233, 241, 255, 0.98), rgba(243, 248, 255, 0.98));
  box-shadow: 0 10px 24px rgba(148, 163, 184, 0.16);
  color: var(--text);
  font-weight: 600;
  text-align: left;
}

.assistant-turn__trace {
  width: 100%;
}

.assistant-turn__trace {
  margin-top: 10px;
  padding: 12px 0 0;
  border: 0;
  border-top: 1px solid var(--line);
  border-radius: 0;
  background: transparent;
}

.assistant-turn__trace summary {
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
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
  flex-wrap: wrap;
}

.assistant-trace-chip {
  display: inline-flex;
  align-items: center;
  padding: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
}

.assistant-inline-chip--success,
.assistant-trace-chip.assistant-inline-chip--success {
  background: rgba(34, 197, 94, 0.1);
}

.assistant-inline-chip--warning,
.assistant-trace-chip.assistant-inline-chip--warning {
  background: rgba(234, 179, 8, 0.14);
}

.assistant-inline-chip--danger,
.assistant-trace-chip.assistant-inline-chip--danger {
  background: rgba(239, 68, 68, 0.1);
}

.assistant-trace-chip--memory {
  color: var(--brand);
}

.assistant-trace-block pre {
  margin: 0;
  padding: 12px 0 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

.assistant-thinking {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-secondary);
  box-shadow: none;
}

.assistant-thinking--inline {
  width: fit-content;
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

.assistant-typing-cursor {
  display: inline-block;
  margin-left: 2px;
  color: var(--brand);
  animation: assistant-cursor 0.9s steps(1) infinite;
}

.assistant-overlay-enter-active,
.assistant-overlay-leave-active {
  transition: opacity 0.24s ease;
}

.assistant-overlay-enter-from,
.assistant-overlay-leave-to {
  opacity: 0;
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

@media (max-width: 1120px) {
  .assistant-panel {
    grid-template-columns: 264px minmax(0, 1fr);
  }

  .assistant-home__hero h2 {
    font-size: 38px;
  }
}

@media (max-width: 960px) {
  .assistant-panel {
    grid-template-columns: 1fr;
  }

  .assistant-sidebar {
    max-height: 42vh;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .assistant-main {
    padding: 18px;
  }

  .assistant-home__hero h2,
  .assistant-main__topbar h1 {
    font-size: 28px;
  }
}

@media (max-width: 640px) {
  .assistant-fab {
    right: 16px;
    bottom: 18px;
    padding: 12px;
    border-radius: 50%;
  }

  .assistant-fab__label,
  .assistant-fab__hint {
    display: none;
  }

  .assistant-overlay {
    background: var(--bg);
    backdrop-filter: none;
  }

  .assistant-panel {
    grid-template-columns: 1fr;
    background: var(--bg);
  }

  .assistant-mobile-scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 2501;
    background: rgba(15, 23, 42, 0.32);
  }

  .assistant-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 2502;
    width: min(84vw, 320px);
    height: 100dvh;
    max-height: none;
    padding: 22px 16px 16px;
    overflow-y: auto;
    border-right: 1px solid var(--line);
    border-bottom: 0;
    box-shadow: 22px 0 48px rgba(15, 23, 42, 0.18);
    transform: translateX(-105%);
    transition: transform 0.22s ease;
  }

  .assistant-sidebar--mobile-open {
    transform: translateX(0);
  }

  .assistant-sidebar__brand p,
  .assistant-context-card em {
    font-size: 12px;
  }

  .assistant-main {
    height: 100dvh;
    padding: 0 18px 12px;
    background: var(--bg);
  }

  .assistant-main__topbar {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) 44px;
    align-items: center;
    gap: 8px;
    min-height: 72px;
    padding: 8px 0;
    justify-content: initial;
  }

  .assistant-main__title-block {
    min-width: 0;
    text-align: center;
  }

  .assistant-main__eyebrow {
    display: none;
  }

  .assistant-main__topbar h1 {
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--text);
    font-size: 21px;
    line-height: 1.2;
    letter-spacing: 0;
  }

  .assistant-mock-notice {
    width: 100%;
    margin: 0 0 10px;
    padding: 9px 12px;
  }

  .assistant-mobile-menu-button {
    display: inline-grid;
    gap: 5px;
    width: 44px;
    height: 44px;
    padding: 10px 8px;
    border: 0;
    border-radius: 14px;
    background: transparent;
  }

  .assistant-mobile-menu-button span {
    display: block;
    height: 3px;
    border-radius: 999px;
    background: var(--text);
  }

  .assistant-main__actions {
    justify-content: flex-end;
    gap: 0;
  }

  .assistant-main__actions .assistant-top-button {
    display: none;
  }

  .assistant-mobile-avatar {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border: 4px solid transparent;
    border-radius: 50%;
    background:
      linear-gradient(#0f8f73, #0f8f73) padding-box,
      conic-gradient(#4285f4, #34a853, #fbbc05, #ea4335, #4285f4) border-box;
    color: #fff;
    font-size: 14px;
    font-weight: 800;
  }

  .assistant-home {
    align-items: stretch;
    justify-content: flex-start;
    gap: 22px;
    padding: 74px 4px 142px;
    overflow-y: auto;
  }

  .assistant-home__hero {
    max-width: none;
    text-align: left;
  }

  .assistant-home__greeting {
    color: var(--text);
    font-size: 24px;
    line-height: 1.25;
  }

  .assistant-home__hero h2 {
    margin-top: 4px;
    color: var(--text);
    font-size: 42px;
    line-height: 1.12;
    letter-spacing: 0;
  }

  .assistant-home__hero p {
    display: none;
  }

  .assistant-suggestion-row,
  .assistant-suggestion-row--thread {
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-start;
    gap: 12px;
  }

  .assistant-suggestion-chip {
    min-height: 52px;
    padding: 0 20px;
    border: 0;
    border-radius: 999px;
    background: #fff;
    box-shadow: 0 12px 24px rgba(15, 23, 42, 0.06);
    color: var(--text-secondary);
    font-size: 18px;
    font-weight: 600;
  }

  .assistant-thread,
  .assistant-home__composer,
  .assistant-composer {
    padding: 14px 16px;
  }

  .assistant-home__composer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 1;
    width: 100%;
    border-radius: 28px 28px 0 0;
    padding: 18px 18px calc(18px + env(safe-area-inset-bottom));
  }

  .assistant-composer--dock {
    width: 100%;
    margin: 8px 0 0;
    padding: 10px 14px;
    border-radius: 24px;
  }

  .assistant-composer__topline {
    display: none;
  }

  .assistant-composer__bottom {
    flex-direction: row;
    align-items: center;
    gap: 10px;
  }

  .assistant-composer__meta span:first-child {
    display: none;
  }

  .assistant-textarea {
    min-height: 42px;
    max-height: 86px;
    font-size: 15px;
    line-height: 1.55;
  }

  .assistant-textarea--compact {
    min-height: 36px;
    max-height: 68px;
  }

  .assistant-send-button {
    min-width: 70px;
    padding: 9px 15px;
  }

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

  .assistant-turn {
    margin-bottom: 22px;
  }

  .assistant-turn--user .assistant-turn__content {
    max-width: 86%;
    padding: 12px 15px;
    border-radius: 22px 22px 8px 22px;
  }

  .assistant-turn__content {
    font-size: 14px;
  }
}

/* Shared workspace colors and accessible modal controls. */
.assistant-fab { min-height: 52px; padding: 12px 18px; z-index: 1500; background: var(--brand); box-shadow: var(--shadow-lg); }
.assistant-fab__icon { color: #fff; }
.assistant-overlay { z-index: 1800; display: grid; place-items: center; padding: 20px; background: rgb(27 42 32 / 32%); backdrop-filter: blur(4px); }
.assistant-panel { position: relative; width: min(1380px, 100%); height: min(920px, calc(100dvh - 40px)); max-height: calc(100dvh - 40px); grid-template-columns: 270px minmax(0, 1fr); border: 1px solid var(--line); border-radius: 20px; background: var(--panel); box-shadow: 0 28px 80px rgb(27 42 32 / 18%); outline: none; }
.assistant-sidebar { height: 100%; max-height: none; padding: 24px 18px; border-right: 1px solid var(--line); border-bottom: 0; background: var(--bg-secondary); }
.assistant-sidebar__brand-row { gap: 10px; }
.assistant-sidebar__brand h2 { margin-top: 4px; font-size: 17px; line-height: 1.5; letter-spacing: -.3px; }
.assistant-sidebar__logo { width: 40px; height: 40px; flex: 0 0 40px; border-radius: 11px; background: var(--brand); box-shadow: none; }
.assistant-sidebar__kicker { font-size: 10px; letter-spacing: 0; }
.assistant-sidebar__close { display: none; }
.assistant-main { height: 100%; padding: 22px 28px 18px; background: var(--panel); }
.assistant-main__topbar { flex: 0 0 auto; margin-bottom: 22px; }
.assistant-main__title-block { min-width: 0; }
.assistant-main__topbar h1 { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 19px; line-height: 1.5; }
.assistant-main__eyebrow { color: var(--brand); font-size: 11px; letter-spacing: 0; }
.assistant-main__actions { flex: 0 0 auto; gap: 8px; }
.assistant-control { min-height: 44px; border-radius: 9px; }
.assistant-control--primary { border: 1px solid var(--line); background: var(--panel); color: var(--brand); }
.assistant-control:not(:disabled):hover, .assistant-top-button:not(:disabled):hover { background: var(--brand-light); }
.assistant-top-button { min-height: 44px; padding: 8px 12px; border: 1px solid var(--line); border-radius: 9px; color: var(--text-secondary); }
.assistant-top-button--ghost { border-color: transparent; }
.assistant-context-card em { display: block; font-style: normal; }
.assistant-session-card { padding: 3px 8px; }
.assistant-session-card__top { gap: 6px; }
.assistant-session-card__select { flex: 1; min-width: 0; min-height: 44px; overflow: hidden; padding: 0; border: 0; background: transparent; color: var(--text); font: inherit; font-size: 13px; font-weight: 500; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.assistant-session-card__select:disabled { cursor: default; }
.assistant-session-card__menu-button { width: 44px; height: 44px; }
.assistant-session-card:not(.assistant-session-card--disabled):hover { background: var(--panel); }
.assistant-session-card--active, .assistant-session-card--active:not(.assistant-session-card--disabled):hover { background: var(--brand-soft); }
.assistant-session-card--active .assistant-session-card__select { color: var(--brand); font-weight: 600; }
.assistant-home { padding: 26px 16px 48px; overflow-y: auto; }
.assistant-home > * { flex-shrink: 0; }
.assistant-home__hero h2 { font-size: clamp(28px, 3.3vw, 38px); line-height: 1.4; letter-spacing: -.8px; }
.assistant-home__hero p { display: block; max-width: 42ch; margin: 12px auto 0; font-size: 14px; }
.assistant-home__greeting { color: var(--brand); font-size: 15px; }
.assistant-composer { border-radius: 14px; background: var(--panel); box-shadow: var(--shadow-sm); transition: border-color .18s ease, box-shadow .18s ease; }
.assistant-composer:focus-within { border-color: var(--brand); box-shadow: 0 0 0 3px var(--brand-soft); }
.assistant-composer--dock { width: min(820px, 100%); border-radius: 14px; }
.assistant-send-button { min-height: 44px; border-radius: 9px; background: var(--brand); color: #fff; }
.assistant-send-button:not(:disabled):hover { background: var(--brand-hover); }
.assistant-suggestion-row { gap: 10px; }
.assistant-suggestion-chip { display: inline-flex; align-items: center; min-height: 44px; padding: 10px 14px; border: 1px solid var(--line); border-radius: 10px; background: var(--bg); color: var(--text-secondary); font-size: 13px; font-weight: 400; line-height: 1.6; text-align: left; box-shadow: none; transition: border-color .18s ease, color .18s ease; }
.assistant-suggestion-chip:hover { border-color: var(--brand); color: var(--brand); }
.assistant-inline-danger { min-height: 44px; font-size: 12px; }
.assistant-turn__avatar--assistant { background: var(--brand-soft); color: var(--brand); }
.assistant-turn__avatar--user { background: var(--bg-secondary); color: var(--text-secondary); }
.assistant-turn--user .assistant-turn__content { border: 1px solid var(--line); border-radius: 16px 16px 5px 16px; background: var(--brand-light); color: var(--text); font-weight: 400; box-shadow: none; }
.assistant-turn__trace summary { min-height: 36px; padding-top: 8px; }
.assistant-panel button:focus-visible, .assistant-panel summary:focus-visible, .assistant-fab:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }

@media (max-width: 900px) {
  .assistant-overlay { padding: 0; backdrop-filter: none; }
  .assistant-panel { width: 100%; height: 100dvh; max-height: 100dvh; grid-template-columns: 1fr; border: 0; border-radius: 0; }
  .assistant-sidebar { position: absolute; inset: 0 auto 0 0; z-index: 3; width: min(86vw, 320px); height: 100%; max-height: none; padding: 22px 16px; overflow-y: auto; border-right: 1px solid var(--line); box-shadow: 14px 0 36px rgb(27 42 32 / 12%); transform: translateX(-105%); visibility: hidden; transition: transform .2s ease, visibility .2s ease; }
  .assistant-sidebar--mobile-open { transform: translateX(0); visibility: visible; }
  .assistant-sidebar__close { display: inline-flex; align-items: center; justify-content: center; min-width: 44px; min-height: 44px; margin-left: auto; padding: 0 4px; border: 0; border-radius: 8px; background: transparent; color: var(--text-secondary); font-size: 12px; cursor: pointer; }
  .assistant-mobile-scrim { position: absolute; display: block; inset: 0; z-index: 2; background: rgb(27 42 32 / 30%); }
  .assistant-main { height: 100%; padding: 0 18px calc(12px + env(safe-area-inset-bottom)); background: var(--panel); }
  .assistant-main__topbar { display: grid; grid-template-columns: 44px minmax(0, 1fr) 56px; align-items: center; gap: 8px; min-height: 76px; margin-bottom: 8px; padding: 10px 0; }
  .assistant-main__title-block { text-align: center; }
  .assistant-main__eyebrow { display: block; font-size: 10px; line-height: 1.5; }
  .assistant-main__topbar h1 { margin: 3px 0 0; font-size: 17px; line-height: 1.4; }
  .assistant-main__actions { justify-content: flex-end; gap: 0; }
  .assistant-main__actions .assistant-top-button { display: none; }
  .assistant-main__actions .assistant-close-button { display: inline-flex; align-items: center; justify-content: center; min-width: 48px; padding: 6px 10px; font-size: 12px; }
  .assistant-mobile-menu-button { display: flex; flex-direction: column; justify-content: center; gap: 5px; width: 44px; height: 44px; padding: 11px 10px; border: 1px solid var(--line); border-radius: 10px; background: transparent; cursor: pointer; }
  .assistant-mobile-menu-button span { display: block; height: 2px; width: 100%; border-radius: 2px; background: var(--text-secondary); }
  .assistant-home { justify-content: flex-start; gap: 24px; padding: 38px 4px 20px; }
  .assistant-home__hero { text-align: center; }
  .assistant-home__greeting { color: var(--brand); font-size: 15px; }
  .assistant-home__hero h2 { margin-top: 8px; font-size: 32px; }
  .assistant-home__hero p { display: block; font-size: 13px; line-height: 1.8; }
}

@media (max-width: 640px) {
  .assistant-fab { padding: 10px; }
  .assistant-home { gap: 22px; padding: 24px 2px 16px; }
  .assistant-home__hero h2 { font-size: 30px; }
  .assistant-home__greeting { font-size: 14px; }
  .assistant-suggestion-row { width: 100%; gap: 8px; }
  .assistant-suggestion-chip { width: 100%; min-height: 44px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 9px; font-size: 13px; box-shadow: none; }
  .assistant-composer--hero { padding: 14px; border-radius: 14px; }
  .assistant-composer--hero .assistant-textarea { min-height: 90px; }
  .assistant-composer--dock { padding: 12px 14px; border-radius: 14px; }
  .assistant-textarea, .assistant-textarea--compact { font-size: 16px; }
  .assistant-composer__meta span:first-child { display: inline; }
  .assistant-turn__content { font-size: 15px; line-height: 1.85; }
  .assistant-turn--user .assistant-turn__content { max-width: 90%; border-radius: 14px 14px 4px 14px; }
}

@media (max-height: 640px) {
  .assistant-home { justify-content: flex-start; padding-top: 16px; padding-bottom: 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-thinking__dot, .assistant-typing-cursor { animation: none; }
  .assistant-overlay-enter-active, .assistant-overlay-leave-active, .assistant-sidebar, .assistant-composer, .assistant-suggestion-chip, .assistant-panel button { transition: none; }
}

</style>
