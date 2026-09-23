<template>
  <div class="practice-history">
    <div v-if="loading" class="practice-history__loading" aria-live="polite">
      <el-skeleton :rows="5" animated /><span>正在加载练习记录…</span>
    </div>
    <template v-else-if="records.length">
      <div class="practice-history__list">
        <article v-for="item in records" :key="item.id" class="practice-record">
          <div class="practice-record__main">
            <div class="practice-record__heading">
              <h2>{{ item.sessionName }}</h2>
              <StatusPill :tone="statusTone(item.sessionStatus)" :label="formatPracticeStatus(item.sessionStatus)" />
            </div>
            <p>{{ item.totalQuestions || 0 }} 道题<span>·</span>{{ describeSubmitTime(item) }}</p>
          </div>
          <div class="practice-record__stats">
            <div>
              <strong>{{
                isSubmittedStatus(item.sessionStatus) ? Number(item.accuracyRate || 0).toFixed(0) + '%' : '—'
              }}</strong
              ><span>正确率</span>
            </div>
            <div>
              <strong>{{ isSubmittedStatus(item.sessionStatus) ? item.obtainedScore || 0 : '—' }}</strong
              ><span>得分</span>
            </div>
          </div>
          <div class="practice-record__actions">
            <el-button type="primary" plain @click="emit('open', item.id)">{{
              isSubmittedStatus(item.sessionStatus) ? '查看结果' : '继续练习'
            }}</el-button>
            <el-dropdown trigger="click" @command="handleAction($event, item)">
              <IconMenuButton label="练习更多操作" :loading="actionLoadingId === item.id" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">修改名称</el-dropdown-item>
                  <el-dropdown-item v-if="isSubmittedStatus(item.sessionStatus)" command="restart"
                    >再练一次</el-dropdown-item
                  >
                  <el-dropdown-item command="delete" divided>删除记录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </article>
      </div>
      <div class="workspace-pagination">
        <div class="workspace-pagination__meta">共 {{ total }} 次练习</div>
        <el-pagination
          :current-page="current"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          :total="total"
          @update:current-page="emit('update:current', $event)"
          @update:page-size="emit('update:size', $event)"
          @current-change="emit('page-change')"
          @size-change="emit('page-change')"
        />
      </div>
    </template>
    <EmptyState v-else-if="error" variant="error" icon="refresh" title="练习记录暂时没能加载" :description="error">
      <el-button type="primary" @click="emit('reload')">重新加载</el-button>
    </EmptyState>
    <EmptyState
      v-else
      icon="practice"
      title="你的练习记录，从这里开始"
      description="选择一套题集完成练习，成绩与答案都会保存在这里。"
    >
      <el-button type="primary" @click="router.push('/quiz')">去选择题集</el-button>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import StatusPill from '@/components/StatusPill.vue'
import { formatDateTime } from '@/utils/format'
import { formatPracticeStatus, statusTone } from '@/utils/labels'
import { isSubmittedStatus, type PracticeHistoryItem } from './practiceHelpers'

/** 练习历史列表：记录卡片 + 分页 + 空态/错误态。 */
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
  {
    loading: false,
    error: '',
    actionLoadingId: null
  }
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

const describeSubmitTime = (item: PracticeHistoryItem) => {
  if (item.submitTime) return `提交于 ${formatDateTime(item.submitTime)}`
  return isSubmittedStatus(item.sessionStatus) ? '提交时间未记录' : '尚未提交'
}

const handleAction = (command: string, item: PracticeHistoryItem) => {
  if (command === 'rename') emit('rename', item)
  if (command === 'restart') emit('restart', item)
  if (command === 'delete') emit('delete', item)
}
</script>

<style scoped>
.practice-history__loading {
  display: grid;
  gap: 24px;
  padding: 24px;
  color: var(--muted);
  font-size: 13px;
}
.practice-history__list {
  display: grid;
}
.practice-record {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 170px auto;
  align-items: center;
  gap: 24px;
  padding: 24px 0;
  border-bottom: 1px solid var(--line);
}
.practice-record:first-child {
  padding-top: 4px;
}
.practice-record:last-child {
  border-bottom: 0;
}
.practice-record__main {
  min-width: 0;
}
.practice-record__heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.practice-record__heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.practice-record__main p {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 9px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.practice-record__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.practice-record__stats > div {
  display: grid;
  gap: 6px;
}
.practice-record__stats strong {
  color: var(--text);
  font-size: 20px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}
.practice-record__stats span {
  color: var(--muted);
  font-size: 12px;
}
.practice-record__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
@media (max-width: 1150px) {
  .practice-record {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 18px;
  }
  .practice-record__main {
    grid-column: 1 / -1;
  }
}
@media (max-width: 600px) {
  .practice-record__stats {
    gap: 14px;
  }
  .practice-record__stats strong {
    font-size: 19px;
  }
  .practice-record__actions {
    gap: 2px;
  }
}
</style>
