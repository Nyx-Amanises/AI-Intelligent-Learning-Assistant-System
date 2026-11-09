<template>
  <section class="assistant-home">
    <div class="assistant-home__hero">
      <div class="assistant-home__greeting">
        <AssistantMark :size="28" />
        <span>你好，{{ greetingName }}</span>
      </div>
      <h2>今天想弄懂什么？</h2>
      <p>问知识点、查资料，也可以一起复盘错题。</p>
    </div>

    <AssistantComposer
      :model-value="draft"
      variant="hero"
      :sending="sending"
      :meta-label="bindingLabel"
      @update:model-value="emit('update:draft', $event)"
      @send="emit('send')"
    />

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
  </section>
</template>

<script setup lang="ts">
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantMark from '@/components/assistant/AssistantMark.vue'

defineProps<{
  greetingName: string
  bindingLabel: string
  quickPrompts: string[]
  draft: string
  sending: boolean
}>()

const emit = defineEmits<{
  (event: 'update:draft', value: string): void
  (event: 'send'): void
  (event: 'pick-prompt', prompt: string): void
}>()
</script>

<style scoped>
.assistant-home {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  min-height: 0;
  padding: 26px 16px 48px;
  overflow-x: hidden;
  overflow-y: auto;
}

.assistant-home > * {
  flex-shrink: 0;
}

.assistant-home__hero {
  width: min(720px, 100%);
  text-align: center;
}

.assistant-home__greeting {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  color: var(--brand);
  font-size: 15px;
  overflow-wrap: anywhere;
}

.assistant-home__greeting span {
  min-width: 0;
}

.assistant-home__hero h2 {
  margin: 6px 0 0;
  color: var(--text);
  font-size: clamp(25px, 2.5vw, 30px);
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 0.01em;
}

.assistant-home__hero p {
  display: block;
  max-width: 42ch;
  margin: 12px auto 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.assistant-suggestion-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 10px;
  max-width: 720px;
}

.assistant-suggestion-chip {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: var(--el-border-radius-base);
  background: var(--panel);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) ease,
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}

.assistant-suggestion-chip:hover {
  border-color: var(--brand);
  background: var(--brand-light);
  color: var(--brand);
}

@media (max-width: 900px) {
  .assistant-home {
    justify-content: flex-start;
    padding: 38px 4px 20px;
  }

  .assistant-home__hero h2 {
    margin-top: 8px;
    font-size: 28px;
  }

  .assistant-home__hero p {
    font-size: 13px;
    line-height: 1.8;
  }
}

@media (max-width: 760px) {
  .assistant-home {
    align-items: stretch;
    gap: 28px;
    padding: 36px 0 24px;
  }
  .assistant-home__hero {
    text-align: left;
  }
  .assistant-home__hero h2 {
    margin-top: 12px;
    font-size: 32px;
    line-height: 1.4;
  }
  .assistant-home__hero p {
    max-width: none;
    margin: 16px 0 0;
    font-size: 15px;
    line-height: 1.8;
  }
  .assistant-home__greeting {
    font-size: 14px;
    color: var(--muted);
  }
  .assistant-suggestion-row {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
    gap: 0;
  }
  .assistant-suggestion-chip {
    justify-content: space-between;
    gap: 14px;
    width: 100%;
    min-height: 56px;
    padding: 16px 0;
    border: 0;
    border-bottom: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
    color: var(--text-secondary);
    font-size: 14px;
  }
  .assistant-suggestion-chip::after {
    flex-shrink: 0;
    color: var(--brand);
    content: '↗';
    font-size: 18px;
  }
}
@media (max-width: 360px) {
  .assistant-home__hero h2 {
    font-size: 29px;
  }
}

@media (max-height: 640px) {
  .assistant-home {
    justify-content: flex-start;
    padding-top: 16px;
    padding-bottom: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-suggestion-chip {
    transition: none;
  }
}
</style>
