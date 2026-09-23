<template>
  <section class="assistant-home">
    <div class="assistant-home__hero">
      <div class="assistant-home__greeting">你好，{{ greetingName }}</div>
      <h2>今天想学点什么？</h2>
      <p>一起读懂资料、梳理知识，把不明白的地方再学透一点。</p>
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
  max-width: 760px;
  text-align: center;
}

.assistant-home__greeting {
  color: var(--brand);
  font-size: 15px;
}

.assistant-home__hero h2 {
  margin: 6px 0 0;
  color: var(--text);
  font-size: clamp(28px, 3.3vw, 38px);
  line-height: 1.4;
  letter-spacing: -0.8px;
}

.assistant-home__hero p {
  display: block;
  max-width: 42ch;
  margin: 12px auto 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.assistant-suggestion-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
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

@media (max-width: 900px) {
  .assistant-home {
    justify-content: flex-start;
    padding: 38px 4px 20px;
  }

  .assistant-home__hero h2 {
    margin-top: 8px;
    font-size: 32px;
  }

  .assistant-home__hero p {
    font-size: 13px;
    line-height: 1.8;
  }
}

@media (max-width: 640px) {
  .assistant-home {
    gap: 22px;
    padding: 24px 2px 16px;
  }

  .assistant-home__hero h2 {
    font-size: 30px;
  }

  .assistant-home__greeting {
    font-size: 14px;
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
