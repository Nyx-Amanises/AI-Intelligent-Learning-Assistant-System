<template>
  <div class="practice-history">
    <div v-if="loading" class="record-list" role="status" aria-label="正在加载练习记录">
      <div v-for="i in 4" :key="i" class="list-row record-row record-row--loading" aria-hidden="true">
        <span class="skeleton record-row__sk-lead" />
        <div class="record-row__main">
          <span class="skeleton record-row__sk-title" />
          <span class="skeleton record-row__sk-meta" />
        </div>
        <span class="skeleton record-row__sk-actions" />
      </div>
    </div>
    <EmptyState v-else-if="error" variant="error" title="练习记录没有加载出来" :description="error">
      <el-button type="primary" @click="emit('reload')">重新加载</el-button>
    </EmptyState>
    <template v-else-if="records.length">
      <ul class="record-list">
        <li v-for="item in records" :key="item.id" class="list-row record-row">
          <span class="practice-record__icon" aria-hidden="true"><AppIcon name="practice" :size="19" /></span>
          <div class="record-row__main">
            <h2 class="record-row__title">
              <button type="button" @click="emit('open', item.id)">{{ item.sessionName }}</button>
              <StatusPill
                v-if="!isSubmittedStatus(item.sessionStatus)"
                :tone="statusTone(item.sessionStatus)"
                :label="formatPracticeStatus(item.sessionStatus)"
              />
              <GenerationModeBadge :mode="item.generationMode" />
            </h2>
            <p class="practice-record__result">
              <span>{{ item.totalQuestions || 0 }} 道题</span>
              <template v-if="isSubmittedStatus(item.sessionStatus)">
                <span
                  >得分 <strong>{{ item.obtainedScore || 0 }}</strong></span
                >
                <span
                  >正确率 <strong>{{ Number(item.accuracyRate || 0).toFixed(0) }}%</strong></span
                >
              </template>
              <span v-else>尚未提交</span>
            </p>
            <p class="record-row__meta">
              <time v-if="item.submitTime" :datetime="item.submitTime" :title="formatDateTime(item.submitTime)"
                >{{ formatRelativeDay(item.submitTime) }}提交</time
              >
              <span v-else-if="isSubmittedStatus(item.sessionStatus)">提交时间未记录</span>
              <span v-if="item.questionIds?.length" class="record-row__chip" title="再练一次时，仍然只练本次选定的题目"
                >专项复习</span
              >
            </p>
          </div>
          <div class="record-row__actions" role="group" :aria-label="item.sessionName + '的操作'">
            <el-button size="small" type="primary" plain @click="emit('open', item.id)">{{
              isSubmittedStatus(item.sessionStatus) ? '查看结果' : '继续练习'
            }}</el-button>
            <el-dropdown trigger="click" @command="handleAction($event, item)">
              <IconMenuButton :label="item.sessionName + '的更多操作'" :loading="actionLoadingId === item.id" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">修改名称</el-dropdown-item>
                  <el-dropdown-item v-if="isSubmittedStatus(item.sessionStatus)" command="restart"
                    >再练一次</el-dropdown-item
                  >
                  <el-dropdown-item command="delete" divided class="menu-item--danger">删除记录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </li>
      </ul>
      <div class="workspace-pagination">
        <div class="workspace-pagination__meta">共 {{ total }} 次练习</div>
        <el-pagination
          :current-page="current"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          :pager-count="5"
          layout="sizes, prev, pager, next"
          :total="total"
          @update:current-page="emit('update:current', $event)"
          @update:page-size="emit('update:size', $event)"
          @current-change="emit('page-change')"
          @size-change="emit('page-change')"
        />
      </div>
    </template>
    <EmptyState
      v-else
      icon="practice"
      title="还没有练习记录"
      description="选一套题集开始作答。交卷后，成绩和答案会保存在这里。"
    >
      <el-button type="primary" @click="router.push('/quiz')">选择题集</el-button>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import StatusPill from '@/components/StatusPill.vue'
import GenerationModeBadge from '@/components/GenerationModeBadge.vue'
import { formatDateTime, formatRelativeDay } from '@/utils/format'
import { formatPracticeStatus, statusTone } from '@/utils/labels'
import { isSubmittedStatus, type PracticeHistoryItem } from './practiceHelpers'

withDefaults(
  defineProps<{
    records: PracticeHistoryItem[]
    total: number
    current: number
    size: number
    loading?: boolean
    error?: string
    actionLoadingId?: number | null
  }>(),
  { loading: false, error: '', actionLoadingId: null }
)

const emit = defineEmits<{
  open: [sessionId: number]
  rename: [item: PracticeHistoryItem]
  restart: [item: PracticeHistoryItem]
  delete: [item: PracticeHistoryItem]
  reload: []
  'page-change': []
  'update:current': [value: number]
  'update:size': [value: number]
}>()

const router = useRouter()
const handleAction = (command: string, item: PracticeHistoryItem) => {
  if (command === 'rename') emit('rename', item)
  if (command === 'restart') emit('restart', item)
  if (command === 'delete') emit('delete', item)
}
</script>

<style scoped>
.practice-history {
  gap: 0;
}
.practice-record__icon {
  display: grid;
  flex: 0 0 var(--record-lead);
  place-items: center;
  height: var(--record-lead);
  border-radius: var(--radius);
  background: var(--brand-light);
  color: var(--brand);
}
.practice-record__result {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.practice-record__result strong {
  color: var(--text);
  font-weight: 600;
}
@media (max-width: 760px) {
  .practice-history .record-list {
    display: grid;
    gap: 16px;
  }
  .practice-history .record-row {
    --record-lead: 36px;
    gap: 12px;
    padding: 20px;
    border: 0;
    border-radius: 18px;
    background: var(--panel);
  }
  .practice-record__icon {
    border-radius: 12px;
    background: var(--bg);
    color: var(--text-secondary);
  }
  .record-row__main {
    flex-basis: calc(100% - var(--record-lead) - 12px);
  }
  .record-row__title {
    gap: 8px;
    font-size: 18px;
    line-height: 1.5;
  }
  .practice-record__result {
    gap: 6px 14px;
    margin-top: 12px;
    font-size: 14px;
  }
  .practice-record__result strong {
    font-size: 17px;
  }
  .record-row__meta {
    margin-top: 10px;
    font-size: 12px;
  }
  .record-row__actions {
    justify-content: flex-end;
    gap: 8px;
    padding-left: 0;
  }
  .record-row__actions :deep(.el-button) {
    min-height: 48px;
    padding-inline: 18px;
    border-radius: 14px;
    font-size: 15px;
  }
  .workspace-pagination {
    margin-top: 20px;
    padding-inline: 0;
    border: 0;
  }
}
</style>
