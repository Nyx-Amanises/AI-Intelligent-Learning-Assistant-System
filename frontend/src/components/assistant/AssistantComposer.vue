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
      :placeholder="variant === 'hero' ? '输入问题，也可以写下资料或题集的名称…' : '继续提问…'"
      @keydown="handleKeydown"
    />

    <div class="assistant-composer__bottom">
      <div class="assistant-composer__meta">
        <span v-if="metaLabel">{{ metaLabel }}</span>
        <span class="assistant-composer__shortcut">回车发送 · Shift + 回车换行</span>
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
        :disabled="disabled || sending || !modelValue.trim()"
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

const props = withDefaults(
  defineProps<{
    modelValue: string
    /** hero：首页大输入框；dock：会话底部输入条。 */
    variant?: 'hero' | 'dock'
    sending?: boolean
    disabled?: boolean
    /** 显示在字数前面的说明（首页用来展示对话范围）。 */
    metaLabel?: string
  }>(),
  {
    variant: 'dock',
    sending: false,
    disabled: false,
    metaLabel: ''
  }
)

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
    return props.sending ? '发送中…' : '发送'
  }
  return '发送'
})

/** 回车发送、Shift + 回车换行；输入法组合输入期间的回车不触发发送。 */
const handleKeydown = (event: KeyboardEvent) => {
  if (event.isComposing || event.keyCode === 229) return
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (props.disabled || props.sending || !props.modelValue.trim()) return
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
  border-radius: var(--radius-lg);
  background: var(--panel);
  box-shadow: var(--shadow-sm);
  transition:
    border-color var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
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
.assistant-composer__topline {
  color: var(--muted);
  font-size: 12px;
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
  gap: 4px 12px;
  min-width: 0;
  color: var(--muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.assistant-send-button {
  flex: 0 0 auto;
  min-height: 44px;
  padding: 10px 18px;
  border: 0;
  border-radius: var(--el-border-radius-base);
  background: var(--brand);
  color: var(--on-brand);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--duration-fast) ease;
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

@media (max-width: 760px) {
  .assistant-composer--hero {
    padding: 18px;
    border-color: transparent;
    border-radius: 18px;
    background: var(--bg);
    box-shadow: none;
  }
  .assistant-composer--dock {
    margin-top: 8px;
    padding: 14px 16px;
    border-color: transparent;
    border-radius: 18px;
    background: var(--bg);
    box-shadow: none;
  }
  .assistant-composer__topline {
    gap: 8px;
    flex-wrap: wrap;
  }
  .assistant-composer__shortcut {
    display: none;
  }
  .assistant-textarea {
    min-height: 112px;
    font-size: 16px;
    line-height: 1.8;
  }
  .assistant-textarea--compact {
    min-height: 44px;
    max-height: 18dvh;
  }
  .assistant-send-button {
    min-width: 88px;
    min-height: 48px;
    padding: 12px 20px;
    border-radius: 12px;
    font-size: 15px;
    font-weight: 600;
    background: var(--mobile-accent, var(--brand));
    color: var(--text);
  }
  .assistant-send-button:not(:disabled):hover {
    background: var(--mobile-accent, var(--brand));
    filter: brightness(0.97);
  }
  .assistant-send-button--stop {
    background: var(--panel);
    color: var(--text);
  }
  .assistant-send-button--stop:not(:disabled):hover {
    background: var(--red-soft);
    color: var(--red);
  }
}
@media (max-width: 760px) and (max-height: 600px) {
  .assistant-composer--dock {
    padding: 8px 12px;
  }
  .assistant-composer--dock .assistant-textarea {
    min-height: 38px;
  }
  .assistant-composer__topline {
    font-size: 11px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-composer,
  .assistant-send-button {
    transition: none;
  }
}
</style>
