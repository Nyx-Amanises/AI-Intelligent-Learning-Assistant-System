<template>
  <section
    class="assistant-action-card"
    :class="`assistant-action-card--${displayStatus.toLowerCase()}`"
    aria-label="任务确认"
  >
    <header>
      <div>
        <span class="assistant-action-card__eyebrow">任务预览</span>
        <h3>{{ displayStatus === 'PENDING' ? '确认后开始生成' : statusLabel }}</h3>
      </div>
      <span class="assistant-action-card__status" role="status">{{ statusLabel }}</span>
    </header>
    <div class="assistant-action-card__material">
      <span>使用资料</span>
      <strong>{{ action.materialTitle || '未提供资料名称' }}</strong>
      <small v-if="action.materialId">资料 #{{ action.materialId }}</small>
    </div>
    <ol class="assistant-action-card__tasks">
      <li v-for="(task, index) in action.tasks" :key="index">
        <h4>{{ index + 1 }}. {{ taskLabel(task.taskType) }}</h4>
        <dl>
          <template v-if="task.taskType === 'SUMMARY'">
            <div>
              <dt>总结方式</dt>
              <dd>{{ SUMMARY_TYPE_LABELS[task.summaryType || ''] || task.summaryType || '服务端默认' }}</dd>
            </div>
            <div>
              <dt>保存笔记</dt>
              <dd>
                {{ task.saveAsNote === undefined ? '服务端默认' : task.saveAsNote ? '保存为笔记' : '仅生成总结' }}
              </dd>
            </div>
          </template>
          <template v-else-if="task.taskType === 'QUESTION_GENERATE'">
            <div>
              <dt>题目总数</dt>
              <dd>{{ task.questionCount ?? '未注明' }} 题</dd>
            </div>
            <div>
              <dt>题型分布</dt>
              <dd>
                单选 {{ task.singleCount ?? 0 }} · 多选 {{ task.multipleCount ?? 0 }} · 判断
                {{ task.judgeCount ?? 0 }} · 简答 {{ task.shortAnswerCount ?? 0 }}
              </dd>
            </div>
            <div>
              <dt>难度</dt>
              <dd>
                {{
                  task.difficultyLevel
                    ? `${formatDifficulty(task.difficultyLevel, '自定义')}（${task.difficultyLevel}/5）`
                    : '服务端默认'
                }}
              </dd>
            </div>
            <div class="assistant-action-card__wide">
              <dt>知识点范围</dt>
              <dd>{{ task.knowledgePoints?.length ? task.knowledgePoints.join('、') : '整份资料' }}</dd>
            </div>
          </template>
          <div>
            <dt>使用模型</dt>
            <dd>{{ task.modelName || '服务端默认模型' }}</dd>
          </div>
          <div v-if="task.taskType === 'SUMMARY'">
            <dt>生成多样性</dt>
            <dd>{{ task.temperature ?? '服务端默认' }}</dd>
          </div>
        </dl>
        <p v-if="task.adjustmentNote" class="assistant-action-card__note">配置说明：{{ task.adjustmentNote }}</p>
        <p v-if="task.requiresQuestionTypeConfirmation" class="assistant-action-card__note">请核对上面的题型分布。</p>
      </li>
    </ol>
    <p v-if="displayStatus === 'PENDING'" class="assistant-action-card__hint">
      {{
        action.expiresAt ? `请在 ${formatDateTime(action.expiresAt)} 前确认。` : ''
      }}需要修改时，直接在对话中说明新的要求。
    </p>
    <p v-else class="assistant-action-card__hint" role="status">{{ terminalMessage }}</p>
    <div v-if="error" class="assistant-action-card__error" role="alert">
      <p>{{ error }}</p>
      <button type="button" :disabled="unavailable" @click="emit('refresh')">重新加载确认状态</button>
    </div>
    <template v-if="displayStatus === 'PENDING'">
      <p v-if="sending" class="assistant-action-card__hint">助手正在更新方案，请等待回复完成。</p>
      <div class="assistant-action-card__actions">
        <button
          type="button"
          class="assistant-action-card__confirm"
          :disabled="unavailable || !action.tasks.length"
          @click="emit('decide', 'CONFIRM')"
        >
          {{ busy === 'CONFIRM' ? '正在确认…' : '确认并创建任务' }}
        </button>
        <button type="button" :disabled="unavailable" @click="emit('decide', 'CANCEL')">
          {{ busy === 'CANCEL' ? '正在取消…' : '取消本次任务' }}
        </button>
      </div>
    </template>
    <nav v-if="taskIds.length" class="assistant-action-card__links" aria-label="已创建的任务">
      <RouterLink
        v-for="taskId in taskIds"
        :key="taskId"
        :to="{ path: '/ai-tasks', query: { taskId } }"
        @click="emit('navigate')"
        >查看任务 #{{ taskId }} →</RouterLink
      >
    </nav>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { AssistantAction, AssistantActionDecision } from '@/api/modules/assistant'
import { formatDateTime, toTimeValue } from '@/utils/assistantFormat'
import { formatDifficulty, SUMMARY_TYPE_LABELS } from '@/utils/labels'

const props = defineProps<{
  action: AssistantAction
  busy: AssistantActionDecision | null
  loading: boolean
  sending: boolean
  error: string
}>()
const emit = defineEmits<{
  decide: [decision: AssistantActionDecision]
  navigate: []
  refresh: []
}>()
const now = ref(Date.now())
let expiryTimer: number | undefined
watch(
  () => props.action.expiresAt,
  (value) => {
    window.clearTimeout(expiryTimer)
    now.value = Date.now()
    const remaining = toTimeValue(value) - now.value
    if (remaining > 0)
      expiryTimer = window.setTimeout(
        () => {
          now.value = Date.now()
        },
        Math.min(remaining + 50, 2147483647)
      )
  },
  { immediate: true }
)
onBeforeUnmount(() => window.clearTimeout(expiryTimer))
const displayStatus = computed(() => {
  const status = String(props.action.status || '').toUpperCase()
  const expiry = toTimeValue(props.action.expiresAt)
  return status === 'PENDING' && expiry && expiry <= now.value ? 'EXPIRED' : status
})
const statusLabel = computed(
  () =>
    ({
      PENDING: '等待确认',
      CONFIRMED: '任务已创建',
      CANCELLED: '已取消',
      EXPIRED: '已过期',
      SUPERSEDED: '方案已更新'
    })[displayStatus.value] || '状态未知'
)
const terminalMessage = computed(() => {
  if (displayStatus.value === 'EXPIRED') return '这份预览已过期，请在对话中重新说明生成要求。'
  if (displayStatus.value === 'SUPERSEDED') return '这份预览已被新方案替代，请确认最新方案。'
  return (
    props.action.message ||
    (displayStatus.value === 'CONFIRMED'
      ? '任务已创建，可打开任务详情查看进度。'
      : displayStatus.value === 'CANCELLED'
        ? '已取消这次生成，之后可继续提出新的要求。'
        : '请刷新确认状态后再继续。')
  )
})
const unavailable = computed(() => Boolean(props.busy || props.loading || props.sending))
const taskIds = computed(() =>
  [...new Set(props.action.taskIds || [])].filter((id) => Number.isSafeInteger(id) && id > 0)
)
const taskLabel = (type: string) =>
  ({ SUMMARY: '生成学习总结', QUESTION_GENERATE: '生成练习题' })[type] || type || '未注明任务类型'
</script>

<style scoped>
.assistant-action-card {
  width: min(920px, 100%);
  margin: 20px auto 8px;
  padding: 20px;
  border: 1px solid var(--brand);
  border-radius: 16px;
  background: var(--panel);
  color: var(--text);
}
.assistant-action-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.assistant-action-card__eyebrow {
  color: var(--muted);
  font-size: 11px;
}
.assistant-action-card h3 {
  margin: 5px 0 0;
  font-size: 17px;
}
.assistant-action-card__status {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
}
.assistant-action-card__material {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  align-items: baseline;
  margin-top: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
  overflow-wrap: anywhere;
}
.assistant-action-card__material span,
.assistant-action-card__material small {
  color: var(--muted);
  font-size: 12px;
}
.assistant-action-card__material strong {
  font-size: 14px;
}
.assistant-action-card__tasks {
  display: grid;
  gap: 16px;
  list-style: none;
  margin: 16px 0;
  padding: 0;
}
.assistant-action-card__tasks li {
  min-width: 0;
  padding: 14px;
  border-radius: 10px;
  background: var(--bg-secondary);
}
.assistant-action-card h4 {
  margin: 0 0 12px;
  font-size: 14px;
}
.assistant-action-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
  margin: 0;
}
.assistant-action-card dt {
  margin-bottom: 3px;
  color: var(--muted);
  font-size: 11px;
}
.assistant-action-card dd {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.assistant-action-card__wide {
  grid-column: 1 / -1;
}
.assistant-action-card__hint,
.assistant-action-card__note,
.assistant-action-card__error {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--text-secondary);
}
.assistant-action-card__error {
  color: var(--red);
}
.assistant-action-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}
.assistant-action-card button {
  min-height: 44px;
  padding: 9px 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel);
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.assistant-action-card button.assistant-action-card__confirm {
  border-color: var(--brand);
  background: var(--brand);
  color: white;
}
.assistant-action-card button:disabled {
  cursor: default;
  opacity: 0.55;
}
.assistant-action-card button:focus-visible,
.assistant-action-card a:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}
.assistant-action-card__links {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  margin-top: 12px;
}
.assistant-action-card__links a {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  color: var(--brand);
  font-size: 13px;
}
.assistant-action-card--expired,
.assistant-action-card--cancelled,
.assistant-action-card--superseded {
  border-color: var(--line);
}
@media (max-width: 640px) {
  .assistant-action-card {
    padding: 16px 12px;
  }
  .assistant-action-card dl {
    grid-template-columns: minmax(0, 1fr);
  }
  .assistant-action-card__actions {
    flex-direction: column;
  }
}
</style>
