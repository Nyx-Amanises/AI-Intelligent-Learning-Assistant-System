<template>
  <div class="assistant-composer" :class="`assistant-composer--${variant}`">
    <div v-if="$slots.topline" class="assistant-composer__topline">
      <slot name="topline" />
    </div>

    <textarea
      ref="textareaRef"
      v-model="text"
      class="assistant-textarea"
      :class="{ 'assistant-textarea--compact': variant === 'dock' }"
      maxlength="4000"
      :aria-label="variant === 'hero' ? `向${ASSISTANT_NAME}提问` : `继续向${ASSISTANT_NAME}提问`"
      :placeholder="variant === 'hero' ? '输入你的问题。回车发送，Shift + 回车换行。' : '继续追问、要求重写、要提纲、要习题或要复盘建议都可以。'"
      @keydown="handleKeydown"
    />

    <div class="assistant-composer__bottom">
      <div class="assistant-composer__meta">
        <span v-if="metaLabel">{{ metaLabel }}</span>
        <span>{{ modelValue.length }}/4000</span>
      </div>
      <button
        v-if="variant === 'dock' && sending"
        type="button"
        class="assistant-send-button assistant-send-button--stop"
        @click="emit('stop')"
      >
        <span class="assistant-send-button__stop-icon" aria-hidden="true" />
        停止生成
      </button>
      <button
        v-else
        type="button"
        class="assistant-send-button"
        :disabled="sending || !modelValue.trim()"
        @click="emit('send')"
      >
        {{ sendLabel }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ASSISTANT_NAME } from '@/config/brand'

const props = withDefaults(defineProps<{
  modelValue: string
  /** hero：首页大输入框；dock：会话底部输入条。 */
  variant?: 'hero' | 'dock'
  sending?: boolean
  /** 显示在字数前面的说明（首页用来展示对话范围）。 */
  metaLabel?: string
}>(), {
  variant: 'dock',
  sending: false,
  metaLabel: ''
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'send'): void
  (event: 'stop'): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)

const text = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value)
})

const sendLabel = computed(() => {
  if (props.variant === 'hero') {
    return props.sending ? '发送中...' : '开始对话'
  }
  return '发送'
})

/** 回车发送、Shift + 回车换行；输入法组合输入期间的回车不触发发送。 */
const handleKeydown = (event: KeyboardEvent) => {
  if (event.isComposing || event.keyCode === 229) return
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    emit('send')
  }
}

defineExpose({
  focus: () => textareaRef.value?.focus()
})
</script>

<style scoped>
.assistant-composer {
  width: min(720px, 100%);
  padding: 16px 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--panel);
  box-shadow: var(--shadow-sm);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.assistant-composer:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.assistant-composer--dock {
  flex: 0 0 auto;
  width: min(820px, 100%);
  margin: 10px auto 0;
  padding: 12px 18px;
}

.assistant-composer__topline,
.assistant-composer__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assistant-textarea {
  width: 100%;
  min-height: 92px;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 16px;
  line-height: 1.7;
  outline: none;
  resize: none;
}

.assistant-textarea--compact {
  min-height: 46px;
  font-size: 15px;
  line-height: 1.55;
}

.assistant-textarea::placeholder {
  color: var(--muted);
}

.assistant-composer__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--text-secondary);
  font-size: 12px;
}

.assistant-send-button {
  min-height: 44px;
  padding: 10px 18px;
  border: 0;
  border-radius: 9px;
  background: var(--brand);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease;
}

.assistant-send-button:not(:disabled):hover {
  background: var(--brand-hover);
}

.assistant-send-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.assistant-send-button--stop {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line);
  background: var(--panel);
  color: var(--text);
}

.assistant-send-button--stop:not(:disabled):hover {
  border-color: var(--red);
  background: var(--red-soft);
  color: var(--red);
}

.assistant-send-button__stop-icon {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: currentColor;
}

@media (max-width: 640px) {
  .assistant-composer--hero {
    padding: 14px;
  }

  .assistant-composer--dock {
    margin-top: 8px;
    padding: 12px 14px;
  }

  .assistant-composer__topline {
    display: none;
  }

  .assistant-textarea {
    min-height: 90px;
    font-size: 16px;
    line-height: 1.55;
  }

  .assistant-textarea--compact {
    min-height: 36px;
  }

  .assistant-send-button {
    min-width: 70px;
    padding: 9px 15px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-composer,
  .assistant-send-button {
    transition: none;
  }
}
</style>
