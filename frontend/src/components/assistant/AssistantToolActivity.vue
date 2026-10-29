<template>
  <section class="assistant-tool-activity" aria-label="处理过程">
    <h3>
      处理过程 <span>{{ tools.length }} 项操作</span>
    </h3>
    <ol>
      <li v-for="tool in tools" :key="tool.id" :class="{ 'is-failed': toolFailed(tool) }">
        <div class="assistant-tool-activity__heading">
          <strong>{{ readableToolName(tool.toolName) }}</strong>
          <span>{{ readableToolStatus(tool.status) }}</span>
          <small v-if="toolDuration(tool)">{{ toolDuration(tool) }}</small>
        </div>
        <p v-if="toolQueryObject(tool)" class="assistant-tool-activity__object">{{ toolQueryObject(tool) }}</p>
        <p>{{ toolSummary(tool) }}</p>
        <nav v-if="toolPracticeRecords(tool).length" class="assistant-tool-activity__records" aria-label="相关练习记录">
          <RouterLink
            v-for="record in toolPracticeRecords(tool)"
            :key="record.id"
            :to="{ path: '/practice', query: { sessionId: record.id } }"
            @click="emit('openPractice')"
          >
            {{ record.label }} <span>查看练习 →</span>
          </RouterLink>
        </nav>
        <p v-if="tool.errorMessage && tool.summaryText" class="assistant-tool-activity__error">
          错误：{{ clipToolText(tool.errorMessage) }}
        </p>
        <details>
          <summary>查看操作详情</summary>
          <dl>
            <div>
              <dt>操作标识</dt>
              <dd>{{ tool.toolName || '未记录' }}</dd>
            </div>
            <div v-if="tool.summaryText && tool.summaryText.length > 320">
              <dt>完整摘要</dt>
              <dd>
                <pre>{{ toolDetailText(tool.summaryText) }}</pre>
              </dd>
            </div>
            <div>
              <dt>查询条件</dt>
              <dd>
                <pre>{{ toolDetailText(tool.toolArgsJson) }}</pre>
              </dd>
            </div>
            <div>
              <dt>返回结果</dt>
              <dd>
                <pre>{{ toolDetailText(tool.toolResultJson) }}</pre>
              </dd>
            </div>
            <div v-if="tool.errorMessage">
              <dt>错误说明</dt>
              <dd>
                <pre>{{ toolDetailText(tool.errorMessage) }}</pre>
              </dd>
            </div>
          </dl>
        </details>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import type { AssistantToolCall } from '@/api/modules/assistant'
import {
  clipToolText,
  readableToolName,
  readableToolStatus,
  toolDetailText,
  toolDuration,
  toolFailed,
  toolPracticeRecords,
  toolQueryObject,
  toolSummary
} from '@/utils/assistantToolPresentation'
defineProps<{ tools: AssistantToolCall[] }>()
const emit = defineEmits<{ openPractice: [] }>()
</script>

<style scoped>
.assistant-tool-activity {
  width: 100%;
  margin-top: 16px;
  border-top: 1px solid var(--line);
  padding-top: 14px;
}
.assistant-tool-activity h3 {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-secondary);
}
.assistant-tool-activity h3 span {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
}
.assistant-tool-activity ol {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.assistant-tool-activity li {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-secondary);
}
.assistant-tool-activity__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 12px;
  font-size: 12px;
}
.assistant-tool-activity__heading strong {
  color: var(--text);
  font-weight: 600;
}
.assistant-tool-activity__heading span {
  color: var(--brand);
}
.assistant-tool-activity__heading small {
  color: var(--muted);
  margin-left: auto;
}
.assistant-tool-activity p {
  margin: 9px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.assistant-tool-activity p.assistant-tool-activity__object {
  color: var(--muted);
  font-size: 11px;
}
.assistant-tool-activity .is-failed .assistant-tool-activity__heading span,
.assistant-tool-activity p.assistant-tool-activity__error {
  color: var(--red);
}
.assistant-tool-activity__records {
  display: grid;
  gap: 4px;
  margin-top: 10px;
}
.assistant-tool-activity__records a {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  min-height: 40px;
  color: var(--brand);
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.assistant-tool-activity__records a span {
  margin-left: auto;
  white-space: nowrap;
}
.assistant-tool-activity summary {
  min-height: 32px;
  padding-top: 10px;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}
.assistant-tool-activity summary:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}
.assistant-tool-activity dl {
  display: grid;
  gap: 14px;
  margin: 10px 0 0;
}
.assistant-tool-activity dt {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 5px;
}
.assistant-tool-activity dd {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.assistant-tool-activity pre {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 11px;
  line-height: 1.7;
}
</style>
